"""Excluded deterministic durability fixtures; no frozen allocation or seed access."""
import base64
import json
import multiprocessing
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import attempt_journal as component
from attempt_journal import AttemptJournal, audit_attempt
from evidence_durability import append_journal, verify_journal
from metrics_contract import ContractError
from test_import_engine_trace import fixture_trace
from test_metrics_contract import bindings


def snapshot(trace=None):
    trace = fixture_trace() if trace is None else trace
    return {key: trace[key] for key in ("initialState", "initialStateSha256")}


def trace_bytes(trace=None):
    return json.dumps(fixture_trace() if trace is None else trace, ensure_ascii=False).encode("utf-8")


def competing_claim(directory, queue):
    try:
        AttemptJournal.create(Path(directory), bindings())
        queue.put("CLAIMED")
    except FileExistsError:
        queue.put("ALREADY_CONSUMED")


class AttemptJournalTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def start(self, b=None):
        return AttemptJournal.create(self.root, bindings() if b is None else b)

    def finish(self, trace=None, b=None):
        trace = fixture_trace() if trace is None else trace
        attempt = self.start(b)
        attempt.initialize(lambda: snapshot(trace))
        return attempt, attempt.finalize(trace_bytes(trace))

    def test_initialization_callback_runs_only_after_fsynced_intent(self):
        real_fsync = os.fsync
        with patch("evidence_durability.os.fsync", wraps=real_fsync) as sync:
            attempt = self.start()
            def initialize():
                rows = verify_journal(attempt.journal_path, expected_tail=attempt.tail)
                self.assertEqual([r["kind"] for r in rows],
                    ["ATTEMPT_BEFORE_INITIALIZATION", "INITIALIZATION_ATTEMPT"])
                self.assertGreaterEqual(sync.call_count, 3)
                return snapshot()
            attempt.initialize(initialize)
        self.assertEqual(len(verify_journal(attempt.journal_path, expected_tail=attempt.tail)), 3)

    def test_two_processes_cannot_claim_the_same_member(self):
        ctx = multiprocessing.get_context("fork")
        queue = ctx.Queue()
        processes = [ctx.Process(target=competing_claim, args=(str(self.root), queue)) for _ in range(2)]
        for process in processes:
            process.start()
        for process in processes:
            process.join(timeout=10)
            self.assertEqual(process.exitcode, 0)
        self.assertEqual(sorted(queue.get(timeout=2) for _ in processes), ["ALREADY_CONSUMED", "CLAIMED"])
        queue.close()

    def test_different_source_pilot_or_input_hash_cannot_retry_same_member(self):
        attempt = self.start()
        original = attempt.journal_path.read_bytes()
        for field in ("engine_sha", "pilot_sha256_by_seat", "input_manifest_sha256"):
            b = bindings()
            b[field] = (["c" * 64] * 4 if field == "pilot_sha256_by_seat" else
                        "c" * (40 if field == "engine_sha" else 64))
            with self.subTest(field=field), self.assertRaises(FileExistsError):
                self.start(b)
        self.assertEqual(attempt.journal_path.read_bytes(), original)

    def test_predeclared_distinct_gears_keep_separate_member_identities(self):
        paths = []
        for gear in ("Cruise", "Sport", "Race"):
            b = bindings()
            b["gear"] = gear
            paths.append(self.start(b).journal_path)
        self.assertEqual(len(set(paths)), 3)

    def test_failed_intent_write_does_not_invoke_initializer_or_permit_retry(self):
        attempt = self.start()
        callback = unittest.mock.Mock(return_value=snapshot())
        with patch.object(component, "append_journal", side_effect=OSError("fixture disk failure")):
            with self.assertRaises(OSError):
                attempt.initialize(callback)
        callback.assert_not_called()
        with self.assertRaisesRegex(ContractError, "consumed"):
            attempt.initialize(callback)
        with self.assertRaises(FileExistsError):
            self.start()

    def test_initializer_exception_preserves_invalid_attempt_and_cannot_be_retried(self):
        attempt = self.start()
        def broken():
            raise RuntimeError("EXCLUDED_FIXTURE_INITIALIZER_FAILURE")
        with self.assertRaises(RuntimeError):
            attempt.initialize(broken)
        rows = verify_journal(attempt.journal_path, expected_tail=attempt.tail)
        self.assertEqual(rows[-1]["kind"], "INTEGRITY_FAILURE")
        self.assertEqual(rows[-1]["outcome_status"], "INVALID")
        with self.assertRaises(ContractError):
            attempt.initialize(snapshot)
        self.assertEqual(list(self.root.glob("*-final.json")), [])

    def test_callers_cannot_mutate_recorded_bindings_or_initial_state(self):
        b = bindings()
        attempt = self.start(b)
        b["gear"] = "Race"
        original = snapshot()
        returned = attempt.initialize(lambda: original)
        original["initialState"]["changed"] = True
        returned["initialState"]["changed_again"] = True
        result = attempt.finalize(trace_bytes())
        self.assertEqual(result["outcome_status"], "UNRESOLVED")

    def test_final_bundle_rederives_metrics_and_cannot_authorize_gameplay_or_replay(self):
        attempt, result = self.finish()
        self.assertEqual(audit_attempt(self.root, result["attempt_key"], result["receipt_sha256"]), result)
        self.assertEqual(result["outcome_status"], "UNRESOLVED")
        self.assertTrue(result["engine_replay_required"])
        self.assertFalse(result["execution_allowed"])
        self.assertEqual(result["missing_mandatory_integrity"], [])
        with self.assertRaises(ContractError):
            attempt.finalize(trace_bytes())
        with self.assertRaises(FileExistsError):
            self.start()

    def test_initial_state_or_engine_digest_substitution_consumes_finalization(self):
        for field in ("initialState", "initialStateSha256"):
            with self.subTest(field=field), tempfile.TemporaryDirectory(dir=self.root) as folder:
                attempt = AttemptJournal.create(Path(folder), bindings())
                attempt.initialize(snapshot)
                trace = fixture_trace()
                trace[field] = {"other": True} if field == "initialState" else "f" * 64
                with self.assertRaises(ContractError):
                    attempt.finalize(trace_bytes(trace))
                with self.assertRaises(ContractError):
                    attempt.finalize(trace_bytes())
                rows = verify_journal(attempt.journal_path, expected_tail=attempt.tail)
                self.assertEqual(rows[-1]["phase"], "FINALIZATION")
                self.assertEqual(rows[-1]["outcome_status"], "INVALID")

    def test_final_evidence_cannot_overwrite_a_preexisting_trace(self):
        attempt = self.start()
        attempt.initialize(snapshot)
        path = attempt.journal_path.with_name(attempt.journal_path.stem + "-trace.json")
        path.write_text("EXCLUDED_PREEXISTING_BYTES")
        with self.assertRaises(FileExistsError):
            attempt.finalize(trace_bytes())
        self.assertEqual(path.read_text(), "EXCLUDED_PREEXISTING_BYTES")
        self.assertEqual(list(self.root.glob("*-final.json")), [])

    def test_modified_initial_trace_metric_or_receipt_bytes_fail_audit(self):
        _, result = self.finish()
        for role in ("initial", "trace", "record", "final"):
            path = self.root / (result["attempt_key"] + "-" + role + ".json")
            before = path.read_bytes()
            path.write_bytes(before + b" ")
            with self.subTest(role=role), self.assertRaises(ContractError):
                audit_attempt(self.root, result["attempt_key"], result["receipt_sha256"])
            path.write_bytes(before)

    def test_final_journal_truncation_or_extra_record_fails_external_receipt_binding(self):
        attempt, result = self.finish()
        before = attempt.journal_path.read_bytes()
        attempt.journal_path.write_bytes(before.splitlines(keepends=True)[0])
        with self.assertRaises(component.verify_journal.__globals__["EvidenceError"]):
            audit_attempt(self.root, result["attempt_key"], result["receipt_sha256"])
        attempt.journal_path.write_bytes(before)
        append_journal(attempt.journal_path, {"kind": "UNDECLARED_EXTRA"},
            key="EXCLUDED_EXTRA", expected_tail=attempt.tail)
        with self.assertRaises(component.verify_journal.__globals__["EvidenceError"]):
            audit_attempt(self.root, result["attempt_key"], result["receipt_sha256"])

    def test_invalid_engine_attempt_remains_invalid_in_final_receipt(self):
        trace = fixture_trace()
        step = trace["steps"][0]
        step.update(accepted=False, afterStateSha256=step["beforeStateSha256"],
            error="EXCLUDED_REJECTED_ACTION", observations=[{"kind": "INTEGRITY_FAILURE",
            "data": {"reason": "EXCLUDED_REJECTED_ACTION"}}])
        trace["stopObservation"] = None
        _, result = self.finish(trace)
        self.assertEqual(result["outcome_status"], "INVALID")
        self.assertFalse(result["execution_allowed"])

    def test_symlinked_directory_cannot_alias_attempt_store(self):
        alias = self.root / "alias"
        alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ContractError):
            AttemptJournal.create(alias, bindings())

    def test_original_private_replay_bytes_preserve_json_order_spacing_and_unicode(self):
        trace = fixture_trace()
        trace["initialState"] = {"z_before_a": "\u03b1", "a_after_z": True}
        raw = json.dumps(trace, indent=3, ensure_ascii=False).encode("utf-8") + b"\n"
        attempt = self.start()
        attempt.initialize(lambda: snapshot(trace))
        result = attempt.finalize(raw)
        envelope = json.loads((self.root / (result["attempt_key"] + "-trace.json")).read_bytes())
        self.assertEqual(base64.b64decode(envelope["payload"]), raw)
        self.assertEqual(component._decode_trace(envelope)[1], raw)

    def test_parsed_trace_or_duplicate_json_fields_cannot_replace_original_bytes(self):
        for raw in (fixture_trace(), b'{"schema":"first","schema":"second"}'):
            with self.subTest(raw_type=type(raw).__name__), tempfile.TemporaryDirectory(dir=self.root) as folder:
                attempt = AttemptJournal.create(Path(folder), bindings())
                attempt.initialize(snapshot)
                with self.assertRaises(ContractError):
                    attempt.finalize(raw)

    @unittest.skipUnless(os.getenv("MT_P2_ENGINE_TRACE_OUTPUT"), "Real-engine fixture output not supplied")
    def test_actual_engine_trace_is_bound_to_durable_snapshot_and_rederived_metrics(self):
        path = Path(os.environ["MT_P2_ENGINE_TRACE_OUTPUT"]) / "excluded-fixture-engine-trace.json"
        raw = path.read_bytes()
        trace = json.loads(raw)
        b = bindings()
        b["player_ids"] = trace["playerIds"]
        attempt = self.start(b)
        attempt.initialize(lambda: snapshot(trace))
        result = attempt.finalize(raw)
        self.assertEqual(result["outcome_status"], "WIN")
        self.assertFalse(result["execution_allowed"])
        self.assertTrue(result["engine_replay_required"])
        # This bridges an independently exercised engine trace; it does not assert
        # the Kotlin initializer was called by the Python callback in this fixture.
        self.assertEqual(audit_attempt(self.root, result["attempt_key"], result["receipt_sha256"]), result)


if __name__ == "__main__":
    unittest.main()
