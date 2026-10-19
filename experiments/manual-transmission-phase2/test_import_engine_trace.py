"""Invented wire fixtures, plus an optional real-engine exported fixture integration."""
from copy import deepcopy
import json
import os
from pathlib import Path
import unittest

from import_engine_trace import import_trace, TRACE_SCHEMA
from metrics_contract import ContractError, audit_record
from test_metrics_contract import bindings


def fixture_trace():
    b = bindings()
    clock = {"kind": "WINDOW", "data": {"turn": 0, "personal_rounds": {p: 0 for p in b["player_ids"]}}}
    action = {"type": "TakeMulligan", "playerId": "manual"}
    return {"schema": TRACE_SCHEMA, "engineSourceSha": b["engine_sha"], "manualSeat": 0,
        "playerIds": b["player_ids"], "initialState": {"excluded_fixture_only": True},
        "initialStateSha256": "c" * 64, "initialObservations": [clock],
        "steps": [{"sequence": 1, "action": action, "beforeStateSha256": "c" * 64,
            "afterStateSha256": "d" * 64, "accepted": True, "error": None, "engineEvents": [], "failurePhase": None,
            "observations": [
                {"kind": "ACTION_ACCEPTED", "data": {"player_id": "manual", "action": action,
                                                       "engine_trace_ref": "engine-step:1"}},
                {"kind": "MULLIGAN_TAKEN", "data": {"player_id": "manual", "free": True}}, clock,
            ]}],
        "stopObservation": {"kind": "RESOURCE_CAP", "data": {"reason": "EXCLUDED_FIXTURE_END"}},
        "executionAuthorizedByThisComponent": False}


class EngineTraceImportTests(unittest.TestCase):
    def test_typed_engine_trace_enters_existing_22_metric_contract_without_self_authorization(self):
        record = import_trace(bindings(), fixture_trace())
        audit = audit_record(record)
        self.assertEqual(audit["missing_mandatory_integrity"], [])
        self.assertFalse(audit["execution_allowed"])
        self.assertEqual(record["metrics"]["mulligans"]["value"]["manual"], 1)
        self.assertEqual(record["metrics"]["winner_or_rules_draw"]["value"]["status"], "UNRESOLVED")
        self.assertIsNone(record["metrics"]["loss_reason_with_evidence"]["value"])
        self.assertIsNone(record["metrics"]["successful_and_failed_win_attempts"]["value"])
        self.assertEqual(record["events"][1]["data"]["engine_adapter_schema"], TRACE_SCHEMA)

    def test_engine_source_roster_or_authority_cannot_be_substituted(self):
        for field, value in (("engineSourceSha", "0" * 40), ("manualSeat", True),
                             ("playerIds", list(reversed(bindings()["player_ids"]))),
                             ("executionAuthorizedByThisComponent", True)):
            trace = fixture_trace()
            trace[field] = value
            with self.subTest(field=field), self.assertRaises(ContractError):
                import_trace(bindings(), trace)

    def test_changed_action_actor_payload_sequence_and_disconnected_state_are_rejected(self):
        for corruption in ("actor", "action", "sequence", "state", "accepted_flag"):
            trace = fixture_trace()
            step = trace["steps"][0]
            if corruption == "actor":
                step["observations"][0]["data"]["player_id"] = "kinnan"
            elif corruption == "action":
                step["observations"][0]["data"]["action"] = {"type": "PassPriority", "playerId": "manual"}
            elif corruption == "sequence":
                step["sequence"] = 2
            elif corruption == "state":
                step["beforeStateSha256"] = "0" * 64
            else:
                step["accepted"] = 1
            with self.subTest(corruption=corruption), self.assertRaises(ContractError):
                import_trace(bindings(), trace)

    def test_rejected_attempt_retained_without_counting_action_or_producing_loss(self):
        trace = fixture_trace()
        step = trace["steps"][0]
        step.update(accepted=False, error="fixture rejected action", afterStateSha256=step["beforeStateSha256"],
            observations=[{"kind": "INTEGRITY_FAILURE", "data": {"reason": "ENGINE_REJECTED_ACTION: fixture rejected action",
                                                                    "engine_trace_ref": "engine-step:1"}}])
        trace["stopObservation"] = None
        record = import_trace(bindings(), trace)
        self.assertEqual(record["metrics"]["ordered_accepted_actions"]["value"], [])
        self.assertEqual(audit_record(record)["outcome_status"], "INVALID")
        for change in ("state", "accepted"):
            corrupted = deepcopy(trace)
            if change == "state":
                corrupted["steps"][0]["afterStateSha256"] = "d" * 64
            else:
                corrupted["steps"][0]["observations"] = fixture_trace()["steps"][0]["observations"]
            with self.subTest(change=change), self.assertRaises(ContractError):
                import_trace(bindings(), corrupted)

    def test_missing_terminal_duplicate_terminal_and_synthetic_draw_from_cap_rejected(self):
        for stop in (None, {"kind": "RULES_DRAW", "data": {"rule_basis": "synthetic cap"}},
                     {"kind": "GAME_WON", "data": {"winner": "manual"}}):
            trace = fixture_trace()
            trace["stopObservation"] = stop
            with self.subTest(stop=stop), self.assertRaises(ContractError):
                import_trace(bindings(), trace)
        trace = fixture_trace()
        trace["steps"][0]["observations"].append(trace["stopObservation"])
        with self.assertRaises(ContractError):
            import_trace(bindings(), trace)

    def test_engine_and_collector_exceptions_preserve_invalid_attempt_without_completeness_claim(self):
        for phase in ("ENGINE", "COLLECTOR"):
            trace = fixture_trace()
            step = trace["steps"][0]
            accepted = step["observations"][0]
            step.update(failurePhase=phase, error="fixture injected failure", observations=[
                {"kind": "INTEGRITY_FAILURE", "data": {"reason": f"{phase}_EXCEPTION: fixture injected failure"}}])
            if phase == "ENGINE":
                step.update(accepted=None, afterStateSha256=None, engineEvents=None)
            else:
                step["observations"].insert(0, accepted)
            trace["stopObservation"] = None
            record = import_trace(bindings(), trace)
            audit = audit_record(record)
            self.assertEqual(audit["outcome_status"], "INVALID")
            self.assertTrue(audit["missing_mandatory_integrity"])
            self.assertIsNone(record["metrics"]["ordered_accepted_actions"]["value"])
            self.assertIsNone(record["metrics"]["loss_reason_with_evidence"]["value"])

    @unittest.skipUnless(os.getenv("MT_P2_ENGINE_TRACE_OUTPUT"), "Real-engine fixture output not supplied")
    def test_actual_serialized_engine_trace_imports_and_passes_independent_record_audit(self):
        path = Path(os.environ["MT_P2_ENGINE_TRACE_OUTPUT"]) / "excluded-fixture-engine-trace.json"
        self.assertTrue(path.is_file(), "Kotlin real-engine export gate did not produce the required artifact")
        trace = json.loads(path.read_bytes())
        b = bindings()
        b["player_ids"] = trace["playerIds"]
        record = import_trace(b, trace)
        audit = audit_record(record)
        self.assertEqual(audit["outcome_status"], "WIN")
        self.assertEqual(audit["missing_mandatory_integrity"], [])
        self.assertEqual(record["metrics"]["elimination_order"]["value"], trace["playerIds"][:3])
        self.assertEqual(len(record["metrics"]["ordered_accepted_actions"]["value"]), 3)
        self.assertFalse(audit["execution_allowed"])


if __name__ == "__main__":
    unittest.main()
