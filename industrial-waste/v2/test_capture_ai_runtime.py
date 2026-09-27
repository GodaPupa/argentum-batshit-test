"""Synthetic metadata controls only: no JVM, game, seed or experimental corpus execution."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("industrial_capture", ROOT / "industrial-waste/v2/capture_ai_runtime.py")
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)
sys.path.insert(0, str(ROOT / "tools"))
import observed_test_runtime as shared


class RuntimeCaptureMetadataTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.results = self.root / "ai/build/test-results/test"
        self.results.mkdir(parents=True)
        self.attempt = self.root / "attempt"
        self.attempt.mkdir()
        self.started, self.finished = time.time() - 10, time.time() + 10
        self.bank = {"cases": [{"class": "metadata.Control", "cases": ["one"], "allowed_skips": []}],
                     "expected_total": 1, "expected_passed": 1}
        self.addCleanup(patch.stopall)
        patch.object(capture, "ROOT", self.root).start()

    def xml(self, *, name="metadata.Control", body=None, timestamp=None):
        body = body or '<testcase name="one" classname="metadata.Control"/>'
        timestamp = timestamp or capture.now()
        path = self.results / "TEST-metadata.Control.xml"
        path.write_text(f'<testsuite name="{name}" tests="1" failures="0" errors="0" skipped="0" '
                        f'timestamp="{timestamp}">{body}</testsuite>')
        return path

    def collect(self):
        return capture.collect_cases(self.attempt, self.bank, self.started, self.finished)

    def test_exact_identity_and_freshness_are_accepted_as_metadata_only(self):
        self.xml()
        self.assertEqual(self.collect()["metadata.Control"]["cases"], ["one"])

    def test_unknown_suite_is_rejected_with_raw_xml_preserved(self):
        self.xml(name="metadata.Other")
        with self.assertRaisesRegex(ValueError, "Unexpected"):
            self.collect()
        self.assertTrue((self.attempt / "junit/TEST-metadata.Control.xml").exists())

    def test_duplicate_actual_case_is_rejected(self):
        self.xml(body='<testcase name="one" classname="metadata.Control"/>' * 2)
        with self.assertRaisesRegex(ValueError, "identities"):
            self.collect()

    def test_failure_node_is_rejected_even_when_suite_counters_claim_zero(self):
        self.xml(body='<testcase name="one" classname="metadata.Control"><failure/></testcase>')
        with self.assertRaisesRegex(ValueError, "failed"):
            self.collect()

    def test_unapproved_skip_is_rejected(self):
        self.xml(body='<testcase name="one" classname="metadata.Control"><skipped/></testcase>')
        with self.assertRaisesRegex(ValueError, "official-execution refusal"):
            self.collect()

    def test_stale_xml_is_rejected(self):
        path = self.xml()
        os.utime(path, (self.started - 60, self.started - 60))
        with self.assertRaisesRegex(ValueError, "interval"):
            self.collect()

    def test_compiled_inputs_require_exact_tracked_blob_and_refuse_untracked_inputs(self):
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        path = self.root / "ai/src/main/kotlin/Control.kt"
        path.parent.mkdir(parents=True)
        path.write_text("synthetic metadata fixture\n")
        subprocess.run(["git", "-C", str(self.root), "add", "ai/src"], check=True)
        self.assertIn("ai/src/main/kotlin/Control.kt", capture.compiled_inputs())
        path.write_text("modified bytes\n")
        with self.assertRaisesRegex(ValueError, "Git index"):
            capture.compiled_inputs()
        path.write_text("synthetic metadata fixture\n")
        (path.parent / "Untracked.kt").write_text("untracked\n")
        with self.assertRaisesRegex(ValueError, "Untracked"):
            capture.compiled_inputs()

    def test_generic_archive_preserves_ai_order_and_rejects_ambiguous_worker(self):
        # These are inert byte fixtures and an explicit synthetic receipt, never a Gradle claim.
        import tarfile
        import hashlib
        classes = self.root / "ai/build/classes/kotlin/test"
        classes.mkdir(parents=True)
        (classes / "Control.class").write_bytes(b"inert test bytes")
        gradle = self.root / "gradle-home"
        (gradle / ".tmp").mkdir(parents=True)
        jar = gradle / "fixture.jar"
        jar.write_bytes(b"inert jar bytes")
        args = gradle / ".tmp/gradle-worker-classpath-unit"
        args.write_text("-cp\n" + os.pathsep.join(map(str, [classes, jar])) + "\n")
        java = self.root / "synthetic-java/bin/java"
        java.parent.mkdir(parents=True)
        java.write_bytes(b"never executed")
        patch.dict(os.environ, {"JAVA_HOME": str(java.parents[1])}).start()
        patch.object(shared.subprocess, "check_output", return_value="synthetic-source\n").start()
        receipt = {"status": "PASS", "source_head": "synthetic-source", "compiled_inputs_unchanged": True,
                   "stages": [{"module": "ai", "started_at_utc": "2000-01-01T00:00:00+00:00",
                               "finished_at_utc": "2099-01-01T00:00:00+00:00"}]}
        (self.attempt / "receipt.json").write_text(json.dumps(receipt))
        self.assertEqual(shared.record_classpath(self.root, self.attempt, gradle,
                         schema="synthetic-observation", recorder_source=Path(__file__)), 0)
        shared.archive_classpath(self.root, self.attempt, gradle, expected_module="ai",
                                 output_name="synthetic-runtime", schema="synthetic-archive",
                                 archiver_source=Path(__file__), java_executable=java)
        archive = self.attempt / "synthetic-runtime/runtime-files.tar.gz"
        with tarfile.open(archive) as tar:
            self.assertEqual(tar.getnames(), ["classpath/000/Control.class", "classpath/001/runtime"])
            self.assertEqual(tar.extractfile("classpath/000/Control.class").read(), b"inert test bytes")
            self.assertEqual(tar.extractfile("classpath/001/runtime").read(), b"inert jar bytes")
        recorded = json.loads((self.attempt / "synthetic-runtime/runtime-archive-receipt.json").read_text())
        self.assertEqual(recorded["archive"]["sha256"], hashlib.sha256(archive.read_bytes()).hexdigest())
        self.assertEqual(recorded["java_executable"]["sha256"], hashlib.sha256(java.read_bytes()).hexdigest())
        observation_path = self.attempt / "runtime-classpath-observation/manifest.json"
        observation = json.loads(observation_path.read_text())
        observation["stages"][0]["matching_workers"] *= 2
        observation_path.write_text(json.dumps(observation))
        with self.assertRaisesRegex(ValueError, "Ambiguous"):
            shared.archive_classpath(self.root, self.attempt, gradle, expected_module="ai",
                                     output_name="rejected-ambiguous", schema="synthetic-archive",
                                     archiver_source=Path(__file__))


if __name__ == "__main__":
    unittest.main()
