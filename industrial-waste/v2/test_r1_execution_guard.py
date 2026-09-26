import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from r1_execution_guard import (
    AUTHORIZATION_STATUS,
    CLAIM_SCHEMA,
    JOURNAL_SCHEMA,
    PROTOCOL_ID,
    RUNTIME_STATUS,
    validate_preinitialization_guard,
)

HEAD = "1" * 40


class R1ExecutionGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.runtime = self.root / "runtime.json"
        self.authorization = self.root / "authorization.json"
        self.claim = self.root / "claim.json"
        self.journal = self.root / "journal.jsonl"
        self._write_json(self.runtime, {"protocol_id": PROTOCOL_ID, "status": RUNTIME_STATUS})
        self._write_json(self.authorization, {"protocol_id": PROTOCOL_ID, "status": AUTHORIZATION_STATUS})
        self._write_claim()
        self._write_journal()

    def tearDown(self):
        self.tmp.cleanup()

    @staticmethod
    def _write_json(path, value):
        path.write_text(json.dumps(value, sort_keys=True) + "\n", encoding="utf-8")

    @staticmethod
    def _digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def _write_claim(self, **overrides):
        value = {
            "schema": CLAIM_SCHEMA,
            "status": "ACTIVE_PREINITIALIZATION",
            "protocol_id": PROTOCOL_ID,
            "claim_id": "r1-attempt-1",
            "expected_branch_head": HEAD,
            "created_before_initialization": True,
            "runtime_binding": {"path": "runtime.json", "sha256": self._digest(self.runtime)},
            "execution_authorization": {"path": "authorization.json", "sha256": self._digest(self.authorization)},
        }
        value.update(overrides)
        self._write_json(self.claim, value)

    def _write_journal(self, records=None):
        records = records or [{
            "schema": JOURNAL_SCHEMA,
            "record_type": "ATTEMPT_DECLARED",
            "protocol_id": PROTOCOL_ID,
            "claim_id": "r1-attempt-1",
            "expected_branch_head": HEAD,
            "attempt_number": 1,
            "stage": "BEFORE_INITIALIZATION",
            "initialized": False,
            "allocations_initialized": 0,
            "actions_submitted": 0,
            "outcomes_exposed": 0,
        }]
        self.journal.write_text("".join(json.dumps(r, sort_keys=True) + "\n" for r in records), encoding="utf-8")

    def validate(self):
        return validate_preinitialization_guard(self.root, self.claim, self.journal, HEAD)

    def test_exact_bound_claim_and_journal_admit_only_first_initialization_guard(self):
        result = self.validate()
        self.assertEqual(result["status"], "READY_FOR_FIRST_INITIALIZATION_GUARD_ONLY")
        self.assertEqual(result["official_allocations_initialized"], 0)
        self.assertEqual(result["official_actions_submitted"], 0)
        self.assertEqual(result["official_outcomes_exposed"], 0)
        self.assertFalse(result["authorizes_extra_sampling"])
        self.assertFalse(result["promotes_candidate"])

    def test_head_mismatch_fails_closed(self):
        self._write_claim(expected_branch_head="2" * 40)
        with self.assertRaisesRegex(ValueError, "branch HEAD mismatch"):
            self.validate()

    def test_bound_receipt_byte_drift_fails_closed(self):
        self.runtime.write_text(self.runtime.read_text() + " ", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "digest mismatch"):
            self.validate()

    def test_missing_or_wrong_authorization_fails_closed(self):
        self._write_json(self.authorization, {"protocol_id": PROTOCOL_ID, "status": "READY_NOT_AUTHORIZED"})
        self._write_claim()
        with self.assertRaisesRegex(ValueError, "not accepted for R1 execution"):
            self.validate()

    def test_initialization_in_journal_before_admission_fails_closed(self):
        first = json.loads(self.journal.read_text().splitlines()[0])
        second = dict(first, record_type="INITIALIZED", initialized=True, allocations_initialized=1)
        self._write_journal([first, second])
        with self.assertRaisesRegex(ValueError, "initialization before admission"):
            self.validate()

    def test_duplicate_attempt_declaration_fails_closed(self):
        first = json.loads(self.journal.read_text().splitlines()[0])
        self._write_journal([first, dict(first)])
        with self.assertRaisesRegex(ValueError, "exactly one durable attempt declaration"):
            self.validate()

    def test_bound_path_cannot_escape_repository_root(self):
        self._write_claim(runtime_binding={"path": "../runtime.json", "sha256": "0" * 64})
        with self.assertRaisesRegex(ValueError, "escapes repository root"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
