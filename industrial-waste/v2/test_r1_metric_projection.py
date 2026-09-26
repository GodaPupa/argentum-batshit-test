import copy
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("iw_r1_metric_projection", Path(__file__).with_name("r1_metric_projection.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def fixture():
    status = {
        "status": "TURN_CAP", "submittedActions": 800, "acceptedActions": 800,
        "ownTurnsStarted": 8, "ownTurnsCompleted": 8, "engineGameOver": False, "diagnostic": None,
    }
    events = {
        "acceptedTransitions": 800, "demonstratedLoopReadyTurn": None, "demonstratedLoopCycles": [],
        "actualLethalTurn": None, "certifiedFutureConversionTurn": None, "deterministicConversionTurn": None,
    }
    checkpoints = [{
        "ownTurn": turn, "cards": [], "relevantActivatedAbilities": [], "relevantGraveyardSpells": [],
        "activatedAbilityCoverageComplete": True, "coloredManaFailure": False,
        "totalManaStranded": False, "unresolved": False,
    } for turn in range(1, 9)]
    return status, events, checkpoints


class MetricProjectionTests(unittest.TestCase):
    def test_valid_turn_cap_counts_absent_events_as_false(self):
        result = module.project_metrics(*fixture())
        self.assertEqual(result, {"validity": "VALID", "invalid_reason": None, **{key: False for key in module.METRICS}})

    def test_color_failure_in_activation_counts_without_stranding_a_hand_copy(self):
        status, events, checkpoints = fixture()
        checkpoints[3]["relevantActivatedAbilities"] = [{
            "originalCopy": "Blood Fountain#1", "status": "UNAVAILABLE_COLORED_PAYMENT",
        }]
        checkpoints[3]["coloredManaFailure"] = True
        result = module.project_metrics(status, events, checkpoints)
        self.assertTrue(result["colored_mana_failure"])
        self.assertFalse(result["total_mana_stranded_at_t4"])

    def test_only_t4_hand_stranding_is_the_frozen_mana_penalty(self):
        for turn, expected in ((3, False), (4, True), (5, False)):
            status, events, checkpoints = fixture()
            checkpoints[turn - 1]["cards"] = [{"originalCopy": "Myr Retriever#1", "status": "INSUFFICIENT_TOTAL_MANA"}]
            checkpoints[turn - 1]["totalManaStranded"] = True
            self.assertEqual(module.project_metrics(status, events, checkpoints)["total_mana_stranded_at_t4"], expected)

    def test_flashback_affects_color_failure_without_becoming_hand_stranding(self):
        for card_status, expected in (("UNAVAILABLE_COLORED_PAYMENT", True), ("INSUFFICIENT_TOTAL_MANA", False)):
            status, events, checkpoints = fixture()
            checkpoints[3]["relevantGraveyardSpells"] = [{"originalCopy": "Eviscerator's Insight#1", "status": card_status}]
            checkpoints[3]["coloredManaFailure"] = expected
            result = module.project_metrics(status, events, checkpoints)
            self.assertEqual(result["colored_mana_failure"], expected)
            self.assertFalse(result["total_mana_stranded_at_t4"])
        checkpoints[3]["cards"] = checkpoints[3]["relevantGraveyardSpells"]
        with self.assertRaisesRegex(ValueError, "identities are missing or duplicated"):
            module.project_metrics(status, events, checkpoints)

    def test_missing_graveyard_spell_ledger_is_incomplete(self):
        status, events, checkpoints = fixture()
        del checkpoints[0]["relevantGraveyardSpells"]
        with self.assertRaisesRegex(ValueError, "graveyard-spell ledgers"):
            module.project_metrics(status, events, checkpoints)

    def test_real_loop_requires_ordered_same_copy_evidence_and_neutral_resources(self):
        status, events, checkpoints = fixture()
        events["demonstratedLoopReadyTurn"] = 3
        events["demonstratedLoopCycles"] = [{
            "ownTurn": 3, "sacrificeTransition": 201, "returnTransition": 203,
            "castTransition": 204, "entryTransition": 206,
            "sacrificedRetriever": "Myr Retriever#1", "returnedRetriever": "Myr Retriever#2",
            "manaBefore": [0] * 6, "manaAfter": [0] * 6,
        }]
        self.assertTrue(module.project_metrics(status, events, checkpoints)["loop_ready_by_t8"])
        for field, value in (("returnTransition", 201), ("returnedRetriever", "Myr Retriever#1"), ("manaAfter", [1] * 6)):
            changed = copy.deepcopy(events)
            changed["demonstratedLoopCycles"][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                module.project_metrics(status, changed, checkpoints)

    def test_inventory_claim_cannot_become_a_demonstrated_loop(self):
        status, events, checkpoints = fixture()
        events["demonstratedLoopReadyTurn"] = 2
        with self.assertRaisesRegex(ValueError, "not backed"):
            module.project_metrics(status, events, checkpoints)

    def test_invalid_attempt_exposes_no_rule_metrics(self):
        for terminal in module.INVALID_TERMINALS:
            status, events, checkpoints = fixture()
            status.update(status=terminal, diagnostic="preserved failure", submittedActions=801)
            result = module.project_metrics(status, events, checkpoints)
            self.assertEqual(result["validity"], "INVALID")
            self.assertTrue(all(result[key] is None for key in module.METRICS))

    def test_action_cap_before_t4_does_not_invent_a_checkpoint(self):
        status, events, checkpoints = fixture()
        status.update(status="ACTION_CAP", submittedActions=4000, acceptedActions=4000, ownTurnsStarted=2, ownTurnsCompleted=1)
        events["acceptedTransitions"] = 4000
        result = module.project_metrics(status, events, checkpoints[:2])
        self.assertEqual(result["validity"], "VALID")
        self.assertFalse(result["total_mana_stranded_at_t4"])

    def test_missing_duplicate_or_reordered_completed_checkpoint_fails_closed(self):
        status, events, checkpoints = fixture()
        for changed in (checkpoints[:3] + checkpoints[4:], checkpoints + [checkpoints[0]], list(reversed(checkpoints))):
            with self.assertRaises(ValueError):
                module.project_metrics(status, events, changed)

    def test_unqualified_activated_surface_or_inconsistent_summary_fails_closed(self):
        for field, value in (("activatedAbilityCoverageComplete", False), ("coloredManaFailure", True), ("unresolved", True)):
            status, events, checkpoints = fixture()
            checkpoints[3][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                module.project_metrics(status, events, checkpoints)

    def test_actual_lethal_requires_real_engine_terminal(self):
        status, events, checkpoints = fixture()
        events.update(actualLethalTurn=8, deterministicConversionTurn=8)
        with self.assertRaises(ValueError):
            module.project_metrics(status, events, checkpoints)
        status.update(status="REAL_TERMINAL", engineGameOver=True)
        self.assertTrue(module.project_metrics(status, events, checkpoints)["conversion_by_t8"])

    def test_malformed_counters_bool_or_missing_accepted_transition_fails_closed(self):
        for field, value in (("submittedActions", 4001), ("ownTurnsStarted", True), ("acceptedActions", 799)):
            status, events, checkpoints = fixture()
            status[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                module.project_metrics(status, events, checkpoints)
        status, events, checkpoints = fixture()
        events["acceptedTransitions"] -= 1
        with self.assertRaises(ValueError):
            module.project_metrics(status, events, checkpoints)

    def test_missing_nullable_metric_fields_are_incomplete_not_negative_outcomes(self):
        for field in ("demonstratedLoopReadyTurn", "actualLethalTurn", "certifiedFutureConversionTurn",
                "deterministicConversionTurn", "demonstratedLoopCycles", "acceptedTransitions"):
            status, events, checkpoints = fixture()
            del events[field]
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, "fields are missing"):
                module.project_metrics(status, events, checkpoints)

    def test_true_is_not_one_accepted_transition(self):
        status, events, _ = fixture()
        status.update(status="REAL_TERMINAL", submittedActions=1, acceptedActions=1,
            ownTurnsStarted=1, ownTurnsCompleted=0, engineGameOver=True)
        events["acceptedTransitions"] = True
        with self.assertRaisesRegex(ValueError, "acceptedTransitions must be an integer"):
            module.project_metrics(status, events, [])


if __name__ == "__main__":
    unittest.main()
