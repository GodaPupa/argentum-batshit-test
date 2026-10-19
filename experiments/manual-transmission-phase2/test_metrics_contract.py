"""Deterministic invented observation fixtures; no engine, seed or sampled game."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from metrics_contract import (
    ContractError, COLLECTIONS, COUNTERS, DEFINITIONS, HARDWARE_BYTES, PROTOCOL, REQUIRED_METRICS,
    Recorder, audit_record, write_record_new,
)


def bindings():
    return {"protocol_id": PROTOCOL, "allocation_id": "EXCLUDED_FIXTURE_ONLY",
            "pod_id": "P01", "gear": "Cruise", "manual_seat": 0,
            "player_ids": ["manual", "bluefarm", "rogsi", "kinnan"],
            "deck_sha256_by_seat": [HARDWARE_BYTES, "1" * 64, "2" * 64, "3" * 64],
            "pilot_sha256_by_seat": ["4" * 64, "5" * 64, "6" * 64, "7" * 64],
            "engine_sha": "8" * 40, "rules_sha256": "9" * 64,
            "input_manifest_sha256": "a" * 64, "gear_definition_sha256": "b" * 64}


def recorder(complete=(), complete_lists=True):
    result = Recorder(bindings(), set(complete) | (COLLECTIONS if complete_lists else set()))
    result.append("WINDOW", {"turn": 0, "personal_rounds": {p: 0 for p in bindings()["player_ids"]}})
    return result


def finish_cap(value):
    value.append("RESOURCE_CAP", {"reason": "EXCLUDED_FIXTURE_ACTION_CAP"})
    return value.finish()


def rehash(record):
    body = {k: v for k, v in record.items() if k != "record_sha256"}
    raw = (json.dumps(body, sort_keys=True, separators=(",", ":"), allow_nan=False) + "\n").encode()
    record["record_sha256"] = hashlib.sha256(raw).hexdigest()


class PhaseTwoMetricsContractTests(unittest.TestCase):
    def test_exact_protocol_metric_keys_and_definitions_have_no_missing_contract_field(self):
        protocol = json.loads((Path(__file__).with_name("protocol-r1.json")).read_text())
        self.assertEqual(tuple(protocol["required_telemetry"]), REQUIRED_METRICS)
        self.assertEqual(set(DEFINITIONS), set(REQUIRED_METRICS))
        self.assertEqual(protocol["primary"]["total_games"], 864)
        self.assertFalse(protocol["gameplay_authorized"])

    def test_cap_and_timeout_are_unresolved_and_cannot_claim_winner_draw_or_loss(self):
        for kind in ("RESOURCE_CAP", "TIMEOUT"):
            with self.subTest(kind=kind):
                r = recorder()
                r.append(kind, {"reason": "fixture limit"})
                record = r.finish()
                outcome = record["metrics"]["winner_or_rules_draw"]["value"]
                self.assertEqual(outcome["status"], "UNRESOLVED")
                self.assertIsNone(outcome["winner"])
                self.assertFalse(outcome["rules_draw"])
                self.assertIsNone(record["metrics"]["loss_reason_with_evidence"]["value"])
                self.assertFalse(audit_record(record)["execution_allowed"])
                for bad in ({"reason": "cap", "winner": "kinnan"}, {"reason": "cap", "rules_draw": True}):
                    with self.assertRaises(ContractError):
                        recorder().append(kind, bad)

    def test_missing_collector_is_null_with_reason_even_when_some_events_are_present(self):
        r = recorder()
        r.append("MULLIGAN_TAKEN", {"player_id": "manual", "free": True})
        record = finish_cap(r)
        for name in COUNTERS:
            measure = record["metrics"][name]
            self.assertIsNone(measure["value"])
            self.assertTrue(measure["unavailable_reason"])
        audit_record(record)

    def test_explicit_complete_collector_can_observe_real_zero_and_counts_free_mulligan(self):
        r = recorder(COUNTERS)
        r.append("MULLIGAN_TAKEN", {"player_id": "manual", "free": True})
        r.append("MULLIGAN_TAKEN", {"player_id": "manual", "free": False})
        result = finish_cap(r)
        self.assertEqual(result["metrics"]["mulligans"]["value"]["manual"], 2)
        self.assertEqual(result["metrics"]["mulligans"]["value"]["kinnan"], 0)
        self.assertEqual(result["metrics"]["animar_removals"]["value"], 0)
        audit_record(result)

    def test_paired_commanders_are_counted_by_identity_without_collapsing_recasts(self):
        r = recorder(["commander_casts_by_identity"])
        for name in ("Tymna the Weaver", "Kraum, Ludevic's Opus", "Tymna the Weaver"):
            r.append("COMMANDER_CAST", {"player_id": "bluefarm", "commander_identity": name})
        result = finish_cap(r)
        self.assertEqual(result["metrics"]["commander_casts_by_identity"]["value"]["bluefarm"],
                         {"Tymna the Weaver": 2, "Kraum, Ludevic's Opus": 1})
        audit_record(result)

    def test_animar_removal_requires_manual_owner_and_real_departure_identity(self):
        valid = {"player_id": "manual", "commander_identity": "Animar, Soul of Elements", "destination": "COMMAND"}
        for wrong in ({**valid, "player_id": "kinnan"}, {**valid, "commander_identity": "Kinnan, Bonder Prodigy"}):
            with self.assertRaises(ContractError):
                recorder(COUNTERS).append("ANIMAR_REMOVED", wrong)
        r = recorder(COUNTERS)
        r.append("ANIMAR_REMOVED", valid)
        self.assertEqual(finish_cap(r)["metrics"]["animar_removals"]["value"], 1)

    def test_actual_observations_require_existing_evidence_and_cannot_overwrite_derived_outcome(self):
        r = recorder()
        with self.assertRaises(ContractError):
            r.observe_metric("interaction_used", [], ["future-event"])
        with self.assertRaises(ContractError):
            r.observe_metric("winner_or_rules_draw", {"winner": "manual"}, ["e00000001"])
        ref = r.append("ACTION_ACCEPTED", {"player_id": "manual", "action": {"kind": "PassPriority"}, "engine_trace_ref": "fixture-trace:1"})
        with self.assertRaises(ContractError):
            r.observe_metric("gear_decisions", {"gear": "Cruise", "selected_action_ref": ref}, [ref])
        r.mark_unavailable("gear_decisions", "Exact gear collector has not been qualified")
        result = finish_cap(r)
        self.assertEqual(result["metrics"]["ordered_accepted_actions"]["evidence_refs"], ["collector:ordered_accepted_actions", ref])
        audit_record(result)

    def test_unqualified_complex_metrics_cannot_admit_arbitrary_json_with_unrelated_refs(self):
        r = recorder()
        for metric, value in (("mana_stranded", -500), ("successful_and_failed_win_attempts", {"arbitrary": True}),
                              ("loss_reason_with_evidence", "unsupported card swap diagnosis")):
            with self.subTest(metric=metric), self.assertRaises(ContractError):
                r.observe_metric(metric, value, ["e00000001"])
        record = finish_cap(r)
        for metric in ("mana_stranded", "successful_and_failed_win_attempts", "loss_reason_with_evidence"):
            self.assertIsNone(record["metrics"][metric]["value"])
            self.assertTrue(record["metrics"][metric]["unavailable_reason"])
        audit_record(record)

    def test_incomplete_action_and_elimination_streams_are_null_and_block_integrity_admission(self):
        r = recorder(complete_lists=False)
        record = finish_cap(r)
        for metric in COLLECTIONS:
            self.assertIsNone(record["metrics"][metric]["value"])
            self.assertTrue(record["metrics"][metric]["unavailable_reason"])
        self.assertEqual(set(audit_record(record)["missing_mandatory_integrity"]), COLLECTIONS)

    def test_eliminated_players_cannot_win_act_or_cast_in_same_ledger(self):
        r = recorder()
        r.append("PLAYER_ELIMINATED", {"player_id": "manual", "reason": "fixture elimination"})
        attempts = (("GAME_WON", {"winner": "manual", "engine_trace_ref": "fixture"}),
                    ("ACTION_ACCEPTED", {"player_id": "manual", "action": {"kind": "PassPriority"}, "engine_trace_ref": "fixture"}),
                    ("COMMANDER_CAST", {"player_id": "manual", "commander_identity": "Animar, Soul of Elements"}))
        for kind, data in attempts:
            with self.subTest(kind=kind), self.assertRaises(ContractError):
                r.append(kind, data)

    def test_rules_defined_draw_and_integrity_failure_remain_distinct(self):
        r = recorder()
        r.append("RULES_DRAW", {"rule_basis": "fixture mandatory loop", "engine_trace_ref": "fixture:draw"})
        self.assertEqual(audit_record(r.finish())["outcome_status"], "RULES_DRAW")
        r = recorder()
        r.append("INTEGRITY_FAILURE", {"reason": "fixture failed engine transition"})
        self.assertEqual(audit_record(r.finish())["outcome_status"], "INVALID")

    def test_turns_are_not_derived_by_division_and_cannot_move_backwards(self):
        r = recorder()
        counts = {"manual": 2, "bluefarm": 1, "rogsi": 1, "kinnan": 0}
        r.append("WINDOW", {"turn": 4, "personal_rounds": counts})
        with self.assertRaises(ContractError):
            r.append("WINDOW", {"turn": 5, "personal_rounds": {**counts, "manual": 1}})
        self.assertEqual(finish_cap(r)["metrics"]["turn_and_personal_round"]["value"]["personal_rounds"], counts)

    def test_elimination_order_is_unique_and_later_observations_cannot_follow_terminal(self):
        r = recorder()
        r.append("PLAYER_ELIMINATED", {"player_id": "rogsi", "reason": "fixture state-based loss"})
        with self.assertRaises(ContractError):
            r.append("PLAYER_ELIMINATED", {"player_id": "rogsi", "reason": "duplicate"})
        result = finish_cap(r)
        self.assertEqual(result["metrics"]["elimination_order"]["value"], ["rogsi"])
        with self.assertRaises(ContractError):
            r.append("OBSERVATION", {"anything": "later"})
        with self.assertRaises(ContractError):
            r.finish()

    def test_rehashed_artifact_still_rejects_wrong_derived_metric_or_ledger_reordering(self):
        r = recorder(COUNTERS)
        r.append("MULLIGAN_TAKEN", {"player_id": "manual", "free": True})
        original = finish_cap(r)
        for change in ("counter", "winner", "order", "clock", "missing_metric", "authority"):
            value = deepcopy(original)
            if change == "counter":
                value["metrics"]["mulligans"]["value"]["manual"] = 0
            elif change == "winner":
                value["metrics"]["winner_or_rules_draw"]["value"]["winner"] = "manual"
            elif change == "order":
                value["events"][0], value["events"][1] = value["events"][1], value["events"][0]
            elif change == "clock":
                value["events"][0]["data"]["turn"] = 9
            elif change == "missing_metric":
                del value["metrics"]["mana_stranded"]
            else:
                value["execution_authorized_by_this_component"] = True
            rehash(value)
            with self.subTest(change=change), self.assertRaises(ContractError):
                audit_record(value)

    def test_wrong_hardware_unknown_actor_boolean_seat_and_nonfinite_json_rejected(self):
        for field, bad in (("manual_seat", True), ("gear", "Race-but-weaker"),
                           ("deck_sha256_by_seat", ["6c28f062" + "0" * 56, "1" * 64, "2" * 64, "3" * 64])):
            value = bindings()
            value[field] = bad
            with self.assertRaises(ContractError):
                Recorder(value)
        with self.assertRaises(ContractError):
            recorder().append("ACTION_ACCEPTED", {"player_id": "unknown", "action": {"a": 1}, "engine_trace_ref": "fixture"})
        with self.assertRaises(ContractError):
            recorder().append("OBSERVATION", {"fake": float("nan")})

    def test_export_never_overwrites_a_previous_record(self):
        result = finish_cap(recorder())
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.json"
            write_record_new(path, result)
            self.assertEqual(json.loads(path.read_bytes()), result)
            original = path.read_bytes()
            with self.assertRaises(FileExistsError):
                write_record_new(path, result)
            self.assertEqual(path.read_bytes(), original)

    def test_export_writes_exact_audited_bytes_despite_later_caller_mutation(self):
        result = finish_cap(recorder())
        before = deepcopy(result)
        original_open = Path.open
        def mutate_then_open(path, *args, **kwargs):
            result["metrics"]["winner_or_rules_draw"]["value"]["winner"] = "manual"
            return original_open(path, *args, **kwargs)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "fixture.json"
            with patch.object(Path, "open", mutate_then_open):
                write_record_new(path, result)
            exported = json.loads(path.read_bytes())
            self.assertEqual(exported, before)
            audit_record(exported)


if __name__ == "__main__":
    unittest.main()
