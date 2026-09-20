import tempfile
import unittest
from pathlib import Path

from validate_inputs import parse_deck, validate_project


ROOT = Path(__file__).resolve().parents[1]


class IndustrialWasteInputTest(unittest.TestCase):
    def test_submitted_control_is_preserved_as_60_plus_15(self):
        main, side = parse_deck(ROOT / "control/industrial-waste-v1.0-submitted.dck")
        self.assertEqual(sum(main.values()), 60)
        self.assertEqual(sum(side.values()), 15)

    def test_each_challenger_is_60_plus_15(self):
        for path in sorted((ROOT / "challengers").glob("*.dck")):
            with self.subTest(path=path.name):
                main, side = parse_deck(path)
                self.assertEqual(sum(main.values()), 60)
                self.assertEqual(sum(side.values()), 15)

    def test_project_validation_passes(self):
        validate_project(ROOT)

    def test_duplicate_card_line_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.dck"
            path.write_text("[main]\n1 Forest\n1 Forest\n[sideboard]\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "duplicate card line"):
                parse_deck(path)


if __name__ == "__main__":
    unittest.main()
