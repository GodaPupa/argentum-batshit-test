from __future__ import annotations

import importlib.util
import unittest
from copy import deepcopy
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("r1_artifact_contract.py")
SPEC = importlib.util.spec_from_file_location("r1_artifact_contract", MODULE_PATH)
assert SPEC and SPEC.loader
contract = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(contract)

ZERO = "0" * 64
ONE = "1" * 64
TWO = "2" * 64
THREE = "3" * 64
FOUR = "4" * 64
FIVE = "5" * 64


def plan():
    allocations = []
    decks = contract.DECKS
    schedules = contract.SCHEDULES
    index = 0
    for row in range(1, 65):
        for schedule in schedules:
            for deck in decks:
                index += 1
                allocations.append({
                    "allocation_id": f"IW_V2_R1_{index:04d}",
                    "allocation_index": index,
                    "row": row,
                    "schedule": schedule,
                    "deck_id": deck,
                    "deck_sha256": ZERO,
                })
    return {"protocol_id": contract.PROTOCOL_ID, "allocations": allocations}


def record(expected, terminal="TURN_CAP", validity="VALID"):
    metrics = {name: False for name in contract.METRICS}
    invalid_reason = None
    failure = None
    if validity == "INVALID":
        metrics = {name: None for name in contract.METRICS}
        invalid_reason = "preserved failure"
        failure = FIVE
    return {
        "schema": contract.SCHEMA,
        "protocol_id": contract.PROTOCOL_ID,
        "allocation_id": expected["allocation_id"],
        "allocation_index": expected["allocation_index"],
        "row": expected["row"],
        "schedule": expected["schedule"],
        "deck": expected["deck_id"],
        "deck_sha256": expected["deck_sha256"],
        "provenance": {
            "source_commit": "a" * 40,
            "allocation_plan_sha256": ZERO,
            "runtime_identity_receipt_sha256": ONE,
            "runner_binding_sha256": TWO,
            "artifact_contract_sha256": THREE,
            "metric_projection_sha256": FOUR,
        },
        "execution": {
            "terminal_status": terminal,
            "submitted_actions": 200,
            "accepted_actions": 200,
            "own_turns_started": 8,
            "own_turns_completed": 8,
        },
        "evidence": {
            "action_transcript_sha256": ZERO,
            "raw_telemetry_sha256": ONE,
            "checkpoint_telemetry_sha256": TWO,
            "initial_ordering_sha256": THREE,
            "failure_bytes_sha256": failure,
        },
        "metric_projection": {
            "validity": validity,
            **metrics,
            "invalid_reason": invalid_reason,
        },
    }


class ArtifactContractTests(unittest.TestCase):
    def test_complete_fixed_512_grid_passes(self):
        p = plan()
        records = [record(item) for item in p["allocations"]]
        result = contract.validate_complete_corpus(records, p)
        self.assertEqual(result["allocation_artifacts"], 512)
        self.assertEqual(result["valid_artifacts"], 512)
        self.assertEqual(result["invalid_artifacts"], 0)
        self.assertTrue(result["decision_rule_admission_ready"])
        self.assertFalse(result["authorizes_execution"])

    def test_incomplete_or_duplicate_corpus_fails_closed(self):
        p = plan()
        records = [record(item) for item in p["allocations"]]
        with self.assertRaisesRegex(ValueError, "exactly 512"):
            contract.validate_complete_corpus(records[:-1], p)
        duplicate = records[:-1] + [deepcopy(records[0])]
        with self.assertRaisesRegex(ValueError, "duplicate"):
            contract.validate_complete_corpus(duplicate, p)

    def test_record_must_match_frozen_allocation_geometry(self):
        p = plan()
        r = record(p["allocations"][0])
        r["row"] = 2
        with self.assertRaisesRegex(ValueError, "row"):
            contract.validate_record(r, p["allocations"][0])

    def test_valid_evidence_requires_all_four_boolean_metrics(self):
        p = plan()
        r = record(p["allocations"][0])
        r["metric_projection"]["conversion_by_t8"] = None
        with self.assertRaisesRegex(ValueError, "four resolved Boolean"):
            contract.validate_record(r, p["allocations"][0])

    def test_invalid_attempt_preserves_failure_bytes_and_exposes_no_metrics(self):
        p = plan()
        r = record(p["allocations"][0], terminal="EXCEPTION", validity="INVALID")
        contract.validate_record(r, p["allocations"][0])
        r["metric_projection"]["loop_ready_by_t8"] = False
        with self.assertRaisesRegex(ValueError, "must not expose"):
            contract.validate_record(r, p["allocations"][0])
        r = record(p["allocations"][0], terminal="REJECTED_ACTION", validity="INVALID")
        r["evidence"]["failure_bytes_sha256"] = None
        with self.assertRaisesRegex(ValueError, "preserve failure bytes"):
            contract.validate_record(r, p["allocations"][0])

    def test_caps_cannot_be_overrun(self):
        p = plan()
        r = record(p["allocations"][0])
        r["execution"]["submitted_actions"] = 4001
        with self.assertRaisesRegex(ValueError, "action cap"):
            contract.validate_record(r, p["allocations"][0])

    def test_descriptor_cannot_authorize_execution_or_promotion(self):
        d = contract.descriptor()
        self.assertEqual(d["required_allocation_count"], 512)
        self.assertFalse(d["claim_creation_authorized"])
        self.assertFalse(d["initialization_admitted"])
        self.assertFalse(d["execution_authorized"])
        self.assertFalse(d["candidate_promotion_authorized"])
        self.assertEqual(
            d["closes_binding_when_formally_accepted"],
            "official_r1_artifact_schema_and_completeness_contract",
        )


if __name__ == "__main__":
    unittest.main()
