"""Filesystem integrity fixtures; no official identifiers, seeds, or gameplay."""
import concurrent.futures
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "durability", Path(__file__).resolve().parents[1] / "evidence_durability.py")
durability = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(durability)


class DurabilityTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name) / "attempt.jsonl"

    def start(self):
        return durability.create_journal(self.path, {"stage": "BEFORE_INITIALIZATION"}, key="attempt")

    def test_create_is_exclusive_and_preserves_existing_bytes(self):
        expected = durability.create_json_once(self.path, {"authorized": False})
        before = self.path.read_bytes()
        self.assertEqual(expected, hashlib.sha256(before).hexdigest())
        with self.assertRaises(FileExistsError):
            durability.create_json_once(self.path, {"authorized": True})
        self.assertEqual(before, self.path.read_bytes())

    def test_append_and_verify_preserve_payload_and_order(self):
        tail = self.start()
        tail = durability.append_journal(self.path, {"stage": "INITIALIZED"}, key="allocation:1:init", expected_tail=tail)
        records = durability.verify_journal(self.path, expected_tail=tail)
        self.assertEqual([r["stage"] for r in records], ["BEFORE_INITIALIZATION", "INITIALIZED"])
        self.assertEqual([r["journal_sequence"] for r in records], [1, 2])

    def test_competing_writers_cannot_both_advance_same_tail(self):
        tail = self.start()
        def append(index):
            try:
                return durability.append_journal(self.path, {"worker": index}, key=f"worker:{index}", expected_tail=tail)
            except durability.EvidenceError:
                return None
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            outcomes = list(pool.map(append, [1, 2]))
        winner = [v for v in outcomes if v is not None]
        self.assertEqual(len(winner), 1)
        self.assertEqual(len(durability.verify_journal(self.path, expected_tail=winner[0])), 2)

    def test_duplicate_allocation_key_rejected(self):
        tail = self.start()
        before = self.path.read_bytes()
        with self.assertRaises(durability.EvidenceError):
            durability.append_journal(self.path, {"stage": "RETRY"}, key="attempt", expected_tail=tail)
        self.assertEqual(before, self.path.read_bytes())

    def test_truncation_rejected_against_external_tail(self):
        first = self.start()
        original = self.path.read_bytes()
        tail = durability.append_journal(self.path, {"stage": "INITIALIZED"}, key="init", expected_tail=first)
        self.path.write_bytes(original)
        with self.assertRaises(durability.EvidenceError):
            durability.verify_journal(self.path, expected_tail=tail)

    def test_incomplete_write_is_preserved_and_cannot_be_reused(self):
        first = self.start()
        with self.path.open("ab") as output:
            output.write(b'{"partial":')
        before = self.path.read_bytes()
        with self.assertRaises(durability.EvidenceError):
            durability.append_journal(self.path, {}, key="retry", expected_tail=first)
        self.assertEqual(before, self.path.read_bytes())

    def test_failed_initial_fsync_keeps_exclusive_claim(self):
        with patch.object(durability.os, "fsync", side_effect=OSError("fixture disk failure")):
            with self.assertRaises(OSError):
                durability.create_json_once(self.path, {"attempt": 1})
        self.assertTrue(self.path.exists())
        with self.assertRaises(FileExistsError):
            durability.create_json_once(self.path, {"attempt": 2})

    def test_reordered_or_modified_record_rejected(self):
        tail = self.start()
        raw = self.path.read_bytes()
        self.path.write_bytes(raw.replace(b"BEFORE_INITIALIZATION", b"AFTER_INITIALIZATION"))
        with self.assertRaises(durability.EvidenceError):
            durability.verify_journal(self.path, expected_tail=tail)

    def test_symlink_not_followed(self):
        target = self.path.with_name("target")
        target.write_text("protected")
        self.path.symlink_to(target)
        with self.assertRaises(OSError):
            durability.create_json_once(self.path, {})
        with self.assertRaises(OSError):
            durability.append_journal(self.path, {}, key="attempt", expected_tail="fixture")
        self.assertEqual(target.read_text(), "protected")

    def test_reserved_fields_and_nonfinite_values_rejected(self):
        with self.assertRaises(durability.EvidenceError):
            durability.create_journal(self.path, {"journal_sequence": 9}, key="fixture")
        with self.assertRaises(ValueError):
            durability.create_json_once(self.path, {"value": float("nan")})
        self.assertFalse(self.path.exists())

    def test_replacement_after_lock_cannot_report_success(self):
        tail = self.start()
        initial = self.path.read_bytes()
        replacement = self.path.with_name("replacement")
        replacement.write_bytes(initial)
        real_write = durability._write_all
        def replace_then_write(fd, raw):
            os.replace(replacement, self.path)
            real_write(fd, raw)
        with patch.object(durability, "_write_all", side_effect=replace_then_write):
            with self.assertRaises(durability.EvidenceError):
                durability.append_journal(self.path, {"stage": "INIT"}, key="init", expected_tail=tail)
        self.assertEqual(self.path.read_bytes(), initial)

    def test_symlinked_parent_rejected(self):
        alias = self.path.parent / "alias"
        alias.symlink_to(self.path.parent, target_is_directory=True)
        with self.assertRaises(durability.EvidenceError):
            durability.create_json_once(alias / "attempt.json", {"attempt": 1})
        self.assertFalse((self.path.parent / "attempt.json").exists())


if __name__ == "__main__":
    unittest.main()
