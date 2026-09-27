"""Twenty-one filesystem/process fixtures and one separate tiny actual-JVM probe.

The unittest bank uses fake procfs and never starts a JVM. The explicit
--actual-jvm-probe mode compiles one probe class, which starts the read-only
observer as its direct child. Neither mode loads the engine or creates claims.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import parent_jvm_observer as observer


def digest(data):
    return hashlib.sha256(data).hexdigest()


class ParentJvmObserverTest(unittest.TestCase):
    def setUp(self):
        retained = os.environ.get("PARENT_JVM_OBSERVER_FIXTURE_ROOT")
        if retained:
            self.root = Path(retained).resolve(strict=True) / self._testMethodName
            self.root.mkdir(exist_ok=False)
        else:
            self.temp = tempfile.TemporaryDirectory()
            self.addCleanup(self.temp.cleanup)
            self.root = Path(self.temp.name)
        self.repo = self.root / "repository"
        self.gradle = self.root / "gradle"
        self.proc = self.root / "proc"
        self.pid = 321
        self.java = self.root / "jdk/bin/java"
        self.java.parent.mkdir(parents=True)
        self.java.write_bytes(b"software-fixture-only-not-an-executable-JVM")
        self.classes = self.repo / "build/classes"
        self.classes.mkdir(parents=True)
        (self.classes / "A.class").write_bytes(b"software-fixture-class-bytes")
        self.jar = self.gradle / "caches/library.jar"
        self.jar.parent.mkdir(parents=True)
        self.jar.write_bytes(b"software-fixture-library-bytes")
        self.arg = self.gradle / ".tmp/gradle-worker-classpath-test"
        self.arg.parent.mkdir()
        self.arg.write_text("-cp\n" + os.pathsep.join(map(str, [self.classes, self.jar])) + "\n")
        self.node = self.proc / str(self.pid)
        self.node.mkdir(parents=True)
        (self.node / "exe").symlink_to(self.java)
        self.write_stat("100")
        self.command = (str(self.java) + "\0@" + str(self.arg) + "\0fixture.Main\0").encode()
        (self.node / "cmdline").write_bytes(self.command)
        boot = self.proc / "sys/kernel/random/boot_id"
        boot.parent.mkdir(parents=True)
        boot.write_text("12345678-1234-1234-1234-123456789abc\n")
        self.expected = {
            "schema": "parent-jvm-classpath-observation-input-v1",
            "roots": {"repository": str(self.repo), "gradle_home": str(self.gradle)},
            "java_sha256": digest(self.java.read_bytes()),
            "cmdline_sha256": digest(self.command),
            "argument_file_sha256": digest(self.arg.read_bytes()),
            "ordered_classpath": [
                {"kind": "repository", "path": "build/classes", "type": "directory",
                 "members": [{"path": "A.class", "sha256": digest((self.classes / "A.class").read_bytes()), "bytes": len((self.classes / "A.class").read_bytes())}]},
                {"kind": "gradle_home", "path": "caches/library.jar", "type": "file",
                 "sha256": digest(self.jar.read_bytes()), "bytes": len(self.jar.read_bytes())},
            ],
            "bounds": {"max_seconds": 5, "max_metadata_bytes": 65536, "max_file_bytes": 8 * 1024 * 1024,
                       "max_total_bytes": 64 * 1024 * 1024, "max_entries": 1000,
                       "max_classpath_entries": 16, "max_journal_bytes": 2 * 1024 * 1024},
        }
        self.input = self.root / "input.json"
        self.output = self.root / "observation"

    def write_stat(self, start):
        (self.node / "stat").write_text(f"{self.pid} (java) R " + " ".join(["0"] * 18 + [start] + ["0"] * 6) + "\n")

    def set_arguments(self, text):
        self.arg.write_text(text)
        self.expected["argument_file_sha256"] = digest(self.arg.read_bytes())

    def run_observation(self, **kwargs):
        self.input.write_text(json.dumps(self.expected))
        return observer.observe(self.input, self.output, _proc=self.proc, _parent=lambda: self.pid, **kwargs)

    def assert_failed(self, result, code=None):
        self.assertEqual(result["status"], "FAILED_OBSERVATION")
        self.assertFalse(result["admission"])
        self.assertEqual(result["claims_created"], 0)
        self.assertTrue((self.output / "intent.json").is_file())
        self.assertEqual(json.loads((self.output / "result.json").read_text()), result)
        if code:
            self.assertEqual(result["error"]["code"], code)

    def test_matching_two_snapshots_preserve_scope_and_observations(self):
        result = self.run_observation()
        self.assertEqual(result["status"], "MATCHED_TWO_SNAPSHOTS_NOT_ADMISSION")
        self.assertFalse(result["admission"])
        self.assertEqual(result["engine_initializations"], 0)
        records = [json.loads(line) for line in (self.output / "observations.jsonl").read_text().splitlines()]
        self.assertEqual(sum(x["kind"] == "parent" for x in records), 2)
        self.assertEqual(sum(x["kind"] == "classpath_entry" for x in records), 4)
        self.assertEqual(records[1]["identity"], self.expected["ordered_classpath"][0])

    def test_changed_class_bytes_fail(self):
        (self.classes / "A.class").write_bytes(b"different bytes")
        self.assert_failed(self.run_observation(), "ORDERED_CLASSPATH_MISMATCH")

    def test_reordered_entries_fail_even_with_current_argument_digest(self):
        self.set_arguments("-cp\n" + os.pathsep.join(map(str, [self.jar, self.classes])) + "\n")
        self.assert_failed(self.run_observation(), "ORDERED_CLASSPATH_MISMATCH")

    def test_missing_entry_preserves_partial_parent_observation(self):
        self.jar.unlink()
        self.assert_failed(self.run_observation())
        self.assertIn('"kind":"parent"', (self.output / "observations.jsonl").read_text())

    def test_duplicate_entry_fails(self):
        self.set_arguments("-cp\n" + os.pathsep.join(map(str, [self.jar, self.jar])) + "\n")
        self.assert_failed(self.run_observation(), "DUPLICATE_CLASSPATH_ENTRY")

    def test_entry_outside_declared_roots_fails(self):
        outside = self.root / "outside.jar"
        outside.write_bytes(self.jar.read_bytes())
        self.set_arguments("-cp\n" + str(outside) + "\n")
        self.assert_failed(self.run_observation(), "PATH_OUTSIDE_OR_AMBIGUOUS_ROOT")

    def test_directory_symlink_escape_fails(self):
        outside = self.root / "outside.class"
        outside.write_bytes(b"same-looking bytes")
        (self.classes / "linked.class").symlink_to(outside)
        self.assert_failed(self.run_observation(), "SYMLINK_IN_CLASSPATH_DIRECTORY")

    def test_non_java_parent_fails(self):
        other = self.java.with_name("python")
        other.write_bytes(self.java.read_bytes())
        (self.node / "exe").unlink()
        (self.node / "exe").symlink_to(other)
        self.assert_failed(self.run_observation(), "PARENT_IS_NOT_CANONICAL_JAVA")

    def test_direct_parent_replacement_fails(self):
        self.input.write_text(json.dumps(self.expected))
        parents = iter([self.pid, self.pid + 1])
        result = observer.observe(self.input, self.output, _proc=self.proc, _parent=lambda: next(parents))
        self.assert_failed(result, "DIRECT_PARENT_REPLACED")

    def test_same_pid_new_process_lifetime_fails(self):
        original = observer.snapshot
        calls = 0
        def replace_after_first(*args):
            nonlocal calls
            value = original(*args)
            calls += 1
            if calls == 1:
                self.write_stat("101")
            return value
        with patch.object(observer, "snapshot", side_effect=replace_after_first):
            self.assert_failed(self.run_observation(), "SNAPSHOTS_OR_PARENT_DIFFER")

    def test_malformed_argument_file_fails(self):
        self.set_arguments("-cp\n" + str(self.jar) + "\n-unexpected\n")
        self.assert_failed(self.run_observation(), "MALFORMED_WORKER_ARGUMENT_FILE")

    def test_mutation_between_snapshots_fails_and_keeps_first_complete_marker(self):
        original = observer.snapshot
        calls = 0
        def mutate_after_first(*args):
            nonlocal calls
            value = original(*args)
            calls += 1
            if calls == 1:
                self.jar.write_bytes(b"mutated between snapshots")
            return value
        with patch.object(observer, "snapshot", side_effect=mutate_after_first):
            self.assert_failed(self.run_observation(), "ORDERED_CLASSPATH_MISMATCH")
        self.assertIn("first_snapshot_complete", (self.output / "observations.jsonl").read_text())

    def test_missing_finite_bounds_fails_before_parent_read(self):
        del self.expected["bounds"]
        self.assert_failed(self.run_observation(), "MISSING_OR_UNKNOWN_BOUNDS")
        self.assertFalse((self.output / "observations.jsonl").exists())

    def test_byte_budget_is_enforced(self):
        self.expected["bounds"]["max_total_bytes"] = 1
        self.assert_failed(self.run_observation(), "TOTAL_BYTE_BOUND_EXCEEDED")

    def test_cooperative_clock_budget_is_enforced(self):
        ticks = iter([0.0, 6.0])
        self.assert_failed(self.run_observation(_clock=lambda: next(ticks)), "TIME_BOUND_EXCEEDED")

    def test_missing_input_retains_intent_and_error(self):
        result = observer.observe(self.input, self.output, _proc=self.proc, _parent=lambda: self.pid)
        self.assert_failed(result)
        self.assertEqual(result["error"]["type"], "FileNotFoundError")

    def test_fifo_input_fails_without_blocking_and_retains_intent(self):
        os.mkfifo(self.input)
        (self.root / "fifo-fixture.json").write_text(json.dumps({"path": self.input.name, "mode": self.input.lstat().st_mode,
                                                              "scope": "Fresh empty software FIFO; no bytes to archive."}))
        try:
            result = observer.observe(self.input, self.output, _proc=self.proc, _parent=lambda: self.pid)
        finally:
            self.input.unlink()
        self.assert_failed(result, "NONREGULAR_INPUT")
        self.assertFalse((self.output / "observations.jsonl").exists())

    def test_existing_attempt_is_never_overwritten(self):
        self.output.mkdir()
        prior = self.output / "prior-failure.json"
        prior.write_bytes(b"preserve these original bytes")
        with self.assertRaises(FileExistsError):
            self.run_observation()
        self.assertEqual(prior.read_bytes(), b"preserve these original bytes")
        self.assertEqual(list(self.output.iterdir()), [prior])

    def test_actual_java_byte_mismatch_fails(self):
        self.java.write_bytes(b"different executable bytes")
        self.assert_failed(self.run_observation(), "JAVA_BYTE_MISMATCH")

    def test_parent_command_mismatch_fails(self):
        (self.node / "cmdline").write_bytes(self.command + b"different-option\0")
        self.assert_failed(self.run_observation(), "PARENT_COMMAND_MISMATCH")

    def test_journal_byte_budget_fails_without_losing_result(self):
        self.expected["bounds"]["max_journal_bytes"] = 1
        self.assert_failed(self.run_observation(), "JOURNAL_BYTE_BOUND_EXCEEDED")
        self.assertEqual((self.output / "observations.jsonl").read_bytes(), b"")


PROBE_SOURCE = """import java.util.concurrent.TimeUnit;
public final class ParentJvmObserverProbe {
  public static void main(String[] args) throws Exception {
    if (args.length != 4) throw new IllegalArgumentException("four arguments required");
    Process child = new ProcessBuilder(args[3], args[0], args[1], args[2])
        .inheritIO().start();
    if (!child.waitFor(20, TimeUnit.SECONDS)) {
      child.destroyForcibly();
      child.waitFor(5, TimeUnit.SECONDS);
      throw new IllegalStateException("observer child exceeded 20 seconds");
    }
    System.exit(child.exitValue());
  }
}
"""


def actual_jvm_probe():
    """One separate software probe, with every input/log retained on failure."""
    retained = os.environ.get("PARENT_JVM_OBSERVER_FIXTURE_ROOT")
    if not retained:
        raise ValueError("PARENT_JVM_OBSERVER_FIXTURE_ROOT is required for the actual probe")
    root = Path(retained).resolve(strict=True) / "actual-parent-probe"
    root.mkdir(exist_ok=False)
    report = {"schema": "parent-jvm-observer-actual-probe-v1", "status": "INCOMPLETE",
              "admission": False, "engine_initializations": 0, "claims_created": 0}
    observer.write_exclusive(root / "intent.json", observer.encoded(report))
    try:
        # Unrecorded injected Java options would make the planned launch ambiguous.
        if any(os.environ.get(k) for k in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")):
            raise ValueError("injected Java options are outside this finite probe")
        java_name, javac_name = shutil.which("java"), shutil.which("javac")
        if not java_name or not javac_name:
            raise ValueError("actual probe requires java and javac; absence is failure, not skip")
        java, javac = Path(java_name).resolve(strict=True), Path(javac_name).resolve(strict=True)
        repo, gradle = root / "repository", root / "gradle"
        classes = repo / "build/classes"
        classes.mkdir(parents=True)
        gradle.mkdir()
        source = repo / "ParentJvmObserverProbe.java"
        source.write_text(PROBE_SOURCE)
        input_path, output = root / "input.json", root / "observation"
        observer_path = Path(observer.__file__).resolve(strict=True)
        argument = gradle / "worker-classpath-probe"
        argument.write_text("-cp\n" + str(classes) + "\n")

        def command(name, argv, seconds):
            observer.write_exclusive(root / (name + "-command.json"), observer.encoded({"argv": argv, "timeout_seconds": seconds}))
            with (root / (name + ".stdout")).open("xb") as out, (root / (name + ".stderr")).open("xb") as err:
                process = subprocess.Popen(argv, stdout=out, stderr=err, start_new_session=True)
                timed_out = False
                deadline = time.monotonic() + seconds
                try:
                    # Leave the direct child unreaped until its owned session is
                    # terminated, so its PID cannot be recycled before killpg.
                    while os.waitid(os.P_PID, process.pid, os.WEXITED | os.WNOHANG | os.WNOWAIT) is None:
                        if time.monotonic() >= deadline:
                            timed_out = True
                            break
                        time.sleep(0.01)
                finally:
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
                    returncode = process.wait(timeout=5)
                value = {"returncode": returncode, "timeout": timed_out,
                         "owned_session_termination_requested": True, "direct_child_reaped": True}
            observer.write_exclusive(root / (name + "-exit.json"), observer.encoded(value))
            if timed_out:
                raise subprocess.TimeoutExpired(argv, seconds)
            if returncode:
                raise ValueError(name + " returned nonzero; raw logs retained")

        command("compile", [str(javac), "-d", str(classes), str(source)], 20)
        compiled = classes / "ParentJvmObserverProbe.class"
        members = list(classes.rglob("*"))
        if members != [compiled] or not compiled.is_file():
            raise ValueError("unexpected compiled probe inventory")
        argv = [str(java), "@" + str(argument), "ParentJvmObserverProbe",
                str(observer_path), str(input_path), str(output), str(Path(sys.executable).resolve(strict=True))]
        class_bytes = compiled.read_bytes()
        expected = {
            "schema": "parent-jvm-classpath-observation-input-v1",
            "roots": {"repository": str(repo), "gradle_home": str(gradle)},
            "java_sha256": digest(java.read_bytes()),
            "cmdline_sha256": digest(("\0".join(argv) + "\0").encode()),
            "argument_file_sha256": digest(argument.read_bytes()),
            "ordered_classpath": [{"kind": "repository", "path": "build/classes", "type": "directory",
                                   "members": [{"path": compiled.name, "sha256": digest(class_bytes), "bytes": len(class_bytes)}]}],
            "bounds": {"max_seconds": 5, "max_metadata_bytes": 65536, "max_file_bytes": 32 * 1024 * 1024,
                       "max_total_bytes": 128 * 1024 * 1024, "max_entries": 100,
                       "max_classpath_entries": 4, "max_journal_bytes": 2 * 1024 * 1024},
        }
        observer.write_exclusive(input_path, observer.encoded(expected))
        report.update(java_sha256=expected["java_sha256"], javac_sha256=digest(javac.read_bytes()),
                      observer_sha256=digest(observer_path.read_bytes()), probe_source_sha256=digest(source.read_bytes()))
        command("launch", argv, 25)
        result = json.loads((output / "result.json").read_text())
        if result["status"] != "MATCHED_TWO_SNAPSHOTS_NOT_ADMISSION" or result["admission"] is not False:
            raise ValueError("actual parent observation did not match")
        if result["claims_created"] != 0 or result["engine_initializations"] != 0:
            raise ValueError("unexpected scope counters")
        report.update(status="PASS_READ_ONLY_ACTUAL_PARENT_PROBE", observation_sha256=digest((output / "result.json").read_bytes()))
    except Exception as error:
        report.update(status="FAILED_PROBE", error={"type": type(error).__name__, "message": str(error)})
    finally:
        observer.write_exclusive(root / "result.json", observer.encoded(report))
    print(json.dumps(report, sort_keys=True))
    return 0 if report["status"] == "PASS_READ_ONLY_ACTUAL_PARENT_PROBE" else 1


if __name__ == "__main__":
    if sys.argv[1:] == ["--actual-jvm-probe"]:
        raise SystemExit(actual_jvm_probe())
    unittest.main()
