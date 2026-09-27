"""Seed-free rejection fixtures for the exact construction boundary."""
from pathlib import Path
import json
import shutil
import tempfile
import unittest

from validate_r0 import parse_deck, validate

REPO = Path(__file__).resolve().parents[2]


class R0BoundaryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        shutil.copytree(REPO / "industrial-waste", self.repo / "industrial-waste")
        self.base = self.repo / "industrial-waste/v2"

    def tearDown(self):
        self.temp.cleanup()

    def test_freeze_is_valid_without_any_gameplay(self):
        report = validate(self.repo)
        self.assertEqual(report["gameplay_initialized_by_this_validator"], 0)
        self.assertFalse(report["r1_execution_ready"])

    def test_unfrozen_fourth_candidate_is_rejected(self):
        shutil.copyfile(self.base / "candidates/compact-loop.dck", self.base / "candidates/fourth.dck")
        with self.assertRaisesRegex(ValueError, "candidate set changed"):
            validate(self.repo)

    def test_historical_control_byte_drift_is_rejected(self):
        p = self.repo / "industrial-waste/control/industrial-waste-v1.0-submitted.dck"
        p.write_text(p.read_text() + "\n")
        with self.assertRaisesRegex(ValueError, "historical comparator changed"):
            validate(self.repo)

    def test_ordering_drift_is_rejected(self):
        p = self.base / "r1-ordering-corpus.json"
        data = json.loads(p.read_text())
        data["rows"][0]["initial_orderings"][0].reverse()
        p.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "frozen identity drift"):
            validate(self.repo)

    def test_four_copy_limit_includes_the_sideboard(self):
        p = self.base / "candidates/compact-loop.dck"
        p.write_text(p.read_text().replace("2 Ancient Grudge", "2 Ashnod's Altar"))
        with self.assertRaisesRegex(ValueError, "more than four copies across"):
            parse_deck(p)


if __name__ == "__main__":
    unittest.main()
