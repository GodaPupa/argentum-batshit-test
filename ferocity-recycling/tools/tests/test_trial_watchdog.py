"""Nine fixed real-process accounting fixtures; all files retained under the required evidence root."""
from __future__ import annotations

import dataclasses
import importlib.util
import json
import os
from pathlib import Path
import signal
import sys
import time
import unittest
from unittest import mock

MODULE_PATH = Path(__file__).resolve().parents[1] / "trial_watchdog.py"
SPEC = importlib.util.spec_from_file_location("ferocity_trial_watchdog", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
watchdog = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = watchdog
SPEC.loader.exec_module(watchdog)

CHILD = '''import json, os, pathlib, signal, subprocess, sys, time
mode, claim_name, counter_name, journal_name, pid_name = sys.argv[1:]
claim = pathlib.Path(claim_name)
assert claim.is_file(), "Supervisor claim was not durable before child initialization"
assert json.loads(claim.read_text())["scope"] == "PROCESS_SUPERVISION_ONLY"
with open(counter_name, "ab", buffering=0) as out:
    out.write(b"one-launch\\n")
    os.fsync(out.fileno())
print("fixed dummy child", mode, flush=True)
if mode == "normal":
    sys.exit(0)
if mode == "nonzero":
    sys.exit(17)
if mode == "ignore":
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
if mode == "intent":
    raw = b'{"index":0,"previousSha256":"dummy-fixture-only","record":{"type":"INTENT","submission":1},"sha256":"unverified-dummy-prefix"}\\n'
    with open(journal_name, "xb", buffering=0) as out:
        out.write(raw)
        os.fsync(out.fileno())
if mode == "descendant":
    ready = pid_name + ".ready"
    code = "import pathlib,signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); pathlib.Path(" + repr(ready) + ").write_text('ready'); time.sleep(600)"
    child = subprocess.Popen([sys.executable, "-c", code])
    pathlib.Path(pid_name).write_text(str(child.pid))
    deadline = time.monotonic() + 4
    while not pathlib.Path(ready).exists():
        if time.monotonic() >= deadline:
            raise RuntimeError("Fixed child did not initialize")
        time.sleep(0.005)
    sys.exit(0)
while True:
    time.sleep(0.05)
'''


class TrialWatchdogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.root = Path(os.environ["FEROCITY_WATCHDOG_FIXTURE_ROOT"]).resolve()
        cls.root.mkdir(parents=True, exist_ok=False)

    def setUp(self) -> None:
        self.work = self.root / self._testMethodName
        self.work.mkdir()
        self.child = self.work / "dummy_child.py"
        self.child.write_text(CHILD)
        self.output = self.work / "supervisor"
        self.counter = self.work / "launches.txt"
        self.journal = self.work / "dummy-journal.jsonl"
        self.pidfile = self.work / "descendant.pid"
        self.executable = Path(sys.executable).resolve()

    def spec(self, mode: str, *, wall: float = 5.0) -> watchdog.WatchdogSpec:
        return watchdog.WatchdogSpec(
            schema_version=1, run_id="fixed-run",
            argv=(str(self.executable), str(self.child), mode,
                  str(self.output / "fixed-run" / "claim.json"), str(self.counter), str(self.journal), str(self.pidfile)),
            cwd=str(self.work), wall_seconds=wall, term_grace_seconds=0.3,
            journal_path=str(self.journal) if mode == "intent" else None,
            pinned_files={str(self.executable): watchdog.file_digest(self.executable), str(self.child): watchdog.file_digest(self.child)},
            supervisor_sha256=watchdog.file_digest(MODULE_PATH), inspection_limit_bytes=1024 * 1024,
        )

    def rows(self) -> list[dict]:
        run = self.output / "fixed-run"
        claim = (run / "claim.json").read_bytes()
        previous = watchdog.digest(claim)
        rows = [json.loads(line) for line in (run / "events.jsonl").read_bytes().splitlines()]
        for index, row in enumerate(rows):
            self.assertEqual(row["index"], index)
            self.assertEqual(row["previous_sha256"], previous)
            unsigned = {key: value for key, value in row.items() if key != "sha256"}
            self.assertEqual(row["sha256"], watchdog.digest(watchdog.canonical(unsigned)))
            previous = row["sha256"]
        return rows

    def assert_no_outcome(self, result: dict) -> None:
        self.assertIsNone(result["game_outcome"])
        self.assertEqual(result["new_gameplay_games"], 0)
        self.assertFalse(result["journal"]["engine_verified"])
        self.assertTrue(result["input_pins_unchanged"])
        self.assertEqual(self.counter.read_bytes(), b"one-launch\n")
        self.assertEqual(sum(row["kind"] == "CHILD_STARTED" for row in self.rows()), 1)

    def test_normal_exit_records_exact_command_versions_pins_and_durable_claim(self) -> None:
        spec = self.spec("normal")
        result = watchdog.supervise(spec, self.output)
        self.assertEqual(result["classification"], "EXIT_ZERO_REQUIRES_ENGINE_REPLAY")
        self.assertEqual(result["returncode"], 0)
        self.assertEqual(result["signals"], [])
        self.assert_no_outcome(result)
        claim = json.loads((self.output / "fixed-run" / "claim.json").read_bytes())
        self.assertEqual(claim["spec"]["argv"], list(spec.argv))
        self.assertEqual(claim["supervisor_source_sha256"], spec.supervisor_sha256)
        self.assertEqual(claim["child_executable_sha256"], watchdog.file_digest(self.executable))
        self.assertEqual(claim["python_version"], sys.version)
        self.assertEqual(claim["spec"]["wall_seconds"], 5.0)
        self.assertEqual(result["stdout"]["sha256"], watchdog.digest(b"fixed dummy child normal\n"))

    def test_nonzero_exit_is_unresolved_and_never_retried(self) -> None:
        result = watchdog.supervise(self.spec("nonzero"), self.output)
        self.assertEqual(result["classification"], "EXIT_NONZERO_UNRESOLVED")
        self.assertEqual(result["returncode"], 17)
        self.assert_no_outcome(result)

    def test_wall_timeout_terminates_the_owned_process_group(self) -> None:
        result = watchdog.supervise(self.spec("term", wall=1.0), self.output)
        self.assertEqual(result["classification"], "TIMEOUT_TERMINATED")
        self.assertEqual(result["returncode"], -signal.SIGTERM)
        self.assertEqual(result["signals"], ["SIGTERM"])
        self.assertTrue(result["process_group_absent_after_cleanup"])
        self.assert_no_outcome(result)

    def test_term_ignoring_child_receives_kill_after_fixed_grace(self) -> None:
        result = watchdog.supervise(self.spec("ignore", wall=1.0), self.output)
        self.assertEqual(result["classification"], "TIMEOUT_KILLED")
        self.assertEqual(result["returncode"], -signal.SIGKILL)
        self.assertEqual(result["signals"], ["SIGTERM", "SIGKILL"])
        self.assertTrue(result["process_group_absent_after_cleanup"])
        self.assert_no_outcome(result)

    def test_timeout_preserves_unmatched_intent_bytes_without_claiming_engine_verification(self) -> None:
        result = watchdog.supervise(self.spec("intent", wall=1.0), self.output)
        expected = b'{"index":0,"previousSha256":"dummy-fixture-only","record":{"type":"INTENT","submission":1},"sha256":"unverified-dummy-prefix"}\n'
        self.assertEqual(self.journal.read_bytes(), expected)
        self.assertEqual(result["journal"]["sha256"], watchdog.digest(expected))
        self.assertEqual(result["journal"]["classification"], "UNRESOLVED_OBSERVED_UNMATCHED_INTENT")
        self.assertEqual(result["journal"]["unmatched_submission"], 1)
        self.assert_no_outcome(result)
        self.assertEqual(self.journal.read_bytes(), expected)

    def test_duplicate_run_identity_fails_before_another_launch_and_preserves_files(self) -> None:
        spec = self.spec("normal")
        watchdog.supervise(spec, self.output)
        before = {str(path): path.read_bytes() for path in (self.output / "fixed-run").iterdir() if path.is_file()}
        with self.assertRaises(FileExistsError):
            watchdog.supervise(spec, self.output)
        self.assertEqual(self.counter.read_bytes(), b"one-launch\n")
        self.assertEqual(before, {str(path): path.read_bytes() for path in (self.output / "fixed-run").iterdir() if path.is_file()})

    def test_changed_pins_or_unknown_schema_fail_before_claim_or_launch(self) -> None:
        valid = self.spec("normal")
        with self.assertRaisesRegex(ValueError, "Input pin mismatch"):
            watchdog.supervise(dataclasses.replace(valid, pinned_files={str(self.executable): "0" * 64}), self.output)
        with self.assertRaisesRegex(ValueError, "Unsupported watchdog schema"):
            watchdog.supervise(dataclasses.replace(valid, schema_version=2), self.output)
        data = dataclasses.asdict(valid)
        data["unknown_future_field"] = True
        with self.assertRaisesRegex(ValueError, "Unsupported watchdog spec fields"):
            watchdog.WatchdogSpec.parse(data)
        self.assertFalse(self.output.exists())
        self.assertFalse(self.counter.exists())

    def test_normal_leader_exit_with_descendant_still_requires_cleanup_and_is_unresolved(self) -> None:
        result = watchdog.supervise(self.spec("descendant"), self.output)
        self.assertEqual(result["classification"], "LEADER_EXITED_WITH_DESCENDANTS_UNRESOLVED")
        self.assertEqual(result["returncode"], 0)
        self.assertTrue(result["descendants_after_leader_exit"])
        self.assertEqual(result["signals"], ["SIGTERM", "SIGKILL"])
        self.assert_no_outcome(result)
        # A container's init may retain an adopted zombie briefly; it cannot keep executing.
        pid = int(self.pidfile.read_text())
        process = Path(f"/proc/{pid}/stat")
        if process.exists():
            self.assertEqual(process.read_text().split(")", 1)[1].split()[0], "Z")

    def test_cancellation_after_os_spawn_before_popen_assignment_cleans_owned_child_and_preserves_intent(self) -> None:
        original_popen = watchdog.subprocess.Popen
        created = []
        spec = self.spec("intent")
        expected = b'{"index":0,"previousSha256":"dummy-fixture-only","record":{"type":"INTENT","submission":1},"sha256":"unverified-dummy-prefix"}\n'

        def launch_then_interrupt(*args, **kwargs):
            if not args or list(args[0]) != list(spec.argv):
                return original_popen(*args, **kwargs)
            child = original_popen(*args, **kwargs)
            created.append(child)
            # Exercise the exact ownership seam, rather than a race against process scheduling:
            # the real child has initialized, but supervise() has not received its Popen handle.
            deadline = time.monotonic() + 3.0
            while not self.journal.exists() or self.journal.read_bytes() != expected:
                if child.poll() is not None or time.monotonic() >= deadline:
                    child.kill()
                    child.wait(timeout=0.3)
                    self.fail("Fixed child did not write its intent before injected cancellation")
                time.sleep(0.005)
            os.kill(os.getpid(), signal.SIGTERM)
            return child

        with mock.patch.object(watchdog.subprocess, "Popen", side_effect=launch_then_interrupt):
            with self.assertRaisesRegex(watchdog.SupervisorInterrupted, "during child creation"):
                watchdog.supervise(spec, self.output)
        self.assertEqual(len(created), 1)
        self.assertEqual(created[0].returncode, -signal.SIGTERM)
        self.assertEqual(self.counter.read_bytes(), b"one-launch\n")
        self.assertEqual(self.journal.read_bytes(), expected)
        rows = self.rows()
        self.assertEqual(sum(row["kind"] == "LAUNCH_INTENT" for row in rows), 1)
        self.assertFalse(any(row["kind"] == "PROCESS_RESULT" for row in rows))
        failure = rows[-1]
        self.assertEqual(failure["kind"], "SUPERVISOR_FAILURE")
        self.assertEqual(failure["data"]["pid"], created[0].pid)
        self.assertEqual(failure["data"]["signals"], ["SIGTERM"])
        self.assertIsNone(failure["data"]["game_outcome"])
        with self.assertRaises(ProcessLookupError):
            os.killpg(created[0].pid, 0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
