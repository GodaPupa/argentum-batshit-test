"""Four fixed resource-boundary cases. No engine, game initialization or entropy is used."""
from __future__ import annotations

import dataclasses
import importlib.util
import json
import os
from pathlib import Path
import sys
import types
import unittest
from unittest import mock

MODULE_PATH = Path(__file__).resolve().parents[1] / "trial_watchdog.py"
SPEC = importlib.util.spec_from_file_location("ferocity_resource_watchdog", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
watchdog = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = watchdog
SPEC.loader.exec_module(watchdog)
LIMIT = 128 * 1024 * 1024
FREE = 768 * 1024 * 1024
PREFIX = b'fixed-resource-prefix; not a game journal\n'
CHILD = '''import json, os, pathlib, resource, sys
mode, counter, journal = sys.argv[1:]
limit = 128 * 1024 * 1024
assert resource.getrlimit(resource.RLIMIT_FSIZE) == (limit, limit)
with open(counter, "xb", buffering=0) as out:
    out.write(b"one-launch\\n")
    os.fsync(out.fileno())
try:
    resource.setrlimit(resource.RLIMIT_FSIZE, (limit + 1, limit + 1))
except (ValueError, OSError):
    pass
else:
    raise AssertionError("Child raised its hard file limit")
print(json.dumps({"soft": limit, "hard": limit, "gameplay": 0}), flush=True)
if mode == "cap":
    with open(journal, "xb", buffering=0) as out:
        out.write(b'fixed-resource-prefix; not a game journal\\n')
        # A sparse extent exercises the actual OS byte-offset limit without wasting 128 MiB
        # of physical storage. The original prefix and entire sparse file are retained.
        out.seek(limit - 1)
        out.write(b"!")
        os.fsync(out.fileno())
        out.write(b"must-fail")
    raise AssertionError("File grew beyond the hard limit")
'''


class TrialWatchdogResourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.root = Path(os.environ["FEROCITY_WATCHDOG_RESOURCE_FIXTURE_ROOT"]).resolve()
        cls.root.mkdir(parents=True, exist_ok=False)

    def setUp(self) -> None:
        self.work = self.root / self._testMethodName
        self.work.mkdir()
        self.child = self.work / "resource_child.py"
        self.child.write_text(CHILD)
        self.counter = self.work / "launches.txt"
        self.journal = self.work / "resource-prefix.jsonl"
        self.output = self.work / "supervisor"
        self.executable = Path(sys.executable).resolve()

    def spec(self, mode: str = "normal") -> watchdog.WatchdogSpec:
        return watchdog.WatchdogSpec(
            schema_version=1, run_id="fixed-resource",
            argv=(str(self.executable), str(self.child), mode, str(self.counter), str(self.journal)),
            cwd=str(self.work), wall_seconds=5.0, term_grace_seconds=0.3,
            journal_path=str(self.journal),
            pinned_files={str(self.executable): watchdog.file_digest(self.executable),
                          str(self.child): watchdog.file_digest(self.child)},
            supervisor_sha256=watchdog.file_digest(MODULE_PATH),
            file_size_limit_bytes=LIMIT, minimum_free_bytes=FREE,
        )

    def rows(self) -> list[dict]:
        directory = self.output / "fixed-resource"
        previous = watchdog.digest((directory / "claim.json").read_bytes())
        rows = [json.loads(line) for line in (directory / "events.jsonl").read_bytes().splitlines()]
        for index, row in enumerate(rows):
            self.assertEqual((row["index"], row["previous_sha256"]), (index, previous))
            unsigned = {key: value for key, value in row.items() if key != "sha256"}
            self.assertEqual(row["sha256"], watchdog.digest(watchdog.canonical(unsigned)))
            previous = row["sha256"]
        return rows

    def test_wp01_exact_limit_is_inherited_and_cannot_be_raised(self) -> None:
        result = watchdog.supervise(self.spec(), self.output)
        self.assertEqual(result["classification"], "EXIT_ZERO_REQUIRES_ENGINE_REPLAY")
        self.assertIsNone(result["game_outcome"])
        self.assertEqual(result["new_gameplay_games"], 0)
        self.assertEqual(self.counter.read_bytes(), b"one-launch\n")
        stdout = json.loads((self.output / "fixed-resource" / "stdout.log").read_text())
        self.assertEqual(stdout, {"soft": LIMIT, "hard": LIMIT, "gameplay": 0})
        preflight = [r for r in self.rows() if r["kind"] == "RESOURCE_PREFLIGHT"]
        self.assertEqual(len(preflight), 1)
        self.assertTrue(all(row["available_bytes"] >= FREE and row["sufficient"]
                            for row in preflight[0]["data"]["filesystems"]))

    def test_wp02_over_limit_write_preserves_the_prefix_and_never_becomes_a_game(self) -> None:
        result = watchdog.supervise(self.spec("cap"), self.output)
        self.assertEqual(result["classification"], "EXIT_NONZERO_UNRESOLVED")
        self.assertNotEqual(result["returncode"], 0)
        self.assertIsNone(result["game_outcome"])
        self.assertEqual(result["new_gameplay_games"], 0)
        self.assertEqual(self.counter.read_bytes(), b"one-launch\n")
        self.assertEqual(self.journal.stat().st_size, LIMIT)
        with self.journal.open("rb") as stream:
            self.assertEqual(stream.read(len(PREFIX)), PREFIX)
            stream.seek(LIMIT - 1)
            self.assertEqual(stream.read(), b"!")
        self.assertEqual(result["journal"]["classification"], "UNRESOLVED_OVER_INSPECTION_LIMIT")
        self.assertFalse(result["journal"]["engine_verified"])
        self.assertEqual(sum(r["kind"] == "CHILD_STARTED" for r in self.rows()), 1)
        with self.assertRaises(FileExistsError):
            watchdog.supervise(self.spec("cap"), self.output)
        self.assertEqual(self.counter.read_bytes(), b"one-launch\n")

    def test_wp03_insufficient_space_is_retained_before_any_child_launch(self) -> None:
        with mock.patch.object(watchdog.os, "statvfs", return_value=types.SimpleNamespace(f_bavail=0, f_frsize=4096)):
            with self.assertRaisesRegex(OSError, "Insufficient evidence space"):
                watchdog.supervise(self.spec(), self.output)
        self.assertFalse(self.counter.exists())
        self.assertFalse(self.journal.exists())
        rows = self.rows()
        self.assertEqual([row["kind"] for row in rows], ["RESOURCE_PREFLIGHT", "SUPERVISOR_FAILURE"])
        self.assertIsNone(rows[-1]["data"]["game_outcome"])
        self.assertIsNone(rows[-1]["data"]["pid"])
        self.assertTrue((self.output / "fixed-resource" / "claim.json").is_file())

    def test_wp04_unbounded_or_changed_resource_configuration_is_refused(self) -> None:
        valid = dataclasses.asdict(self.spec())
        for changes in (
            {"file_size_limit_bytes": None}, {"minimum_free_bytes": None},
            {"file_size_limit_bytes": LIMIT + 1}, {"file_size_limit_bytes": 0},
            {"file_size_limit_bytes": True}, {"file_size_limit_bytes": "unlimited"},
            {"minimum_free_bytes": FREE - 1}, {"minimum_free_bytes": True},
        ):
            with self.assertRaises(ValueError):
                watchdog.WatchdogSpec.parse(dict(valid, **changes))
        self.assertFalse(self.counter.exists())
        self.assertFalse(self.output.exists())


if __name__ == "__main__":
    unittest.main()
