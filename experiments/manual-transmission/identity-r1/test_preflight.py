#!/usr/bin/env python3
"""Deterministic, seed-free guards. Never constructs a game or changes a deck."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

SCRIPT = Path(__file__).with_name("preflight.py")
spec = importlib.util.spec_from_file_location("manual_identity_r1_preflight", SCRIPT)
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


class IdentityGuardTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = SCRIPT.resolve().parents[3]
        cls.deck = (cls.root / "experiments/manual-transmission/control/v0.7.decklist.txt").read_text()
        cls.elasticity = json.loads((cls.root / "experiments/manual-transmission/protocols/same-hardware-elasticity-r1.json").read_text())
        cls.race = json.loads((cls.root / "experiments/manual-transmission/protocols/final-race-policy-r1.json").read_text())
        cls.conclusion = (cls.root / "experiments/manual-transmission/results/FINAL_CONCLUSION_2026_09_24.md").read_text()

    def test_actual_frozen_source_and_policy_pass(self):
        report = audit.audit(self.root)
        self.assertEqual(report["status"], "PASS_READ_ONLY_STRUCTURAL_PREFLIGHT")
        self.assertEqual(report["structure"]["card_count"], 100)
        self.assertFalse(report["gameplay_executed"])
        self.assertFalse(report["hardware_promotion_authorized"])

    def test_loss_of_combat_payoff_rejected_even_with_100_cards(self):
        mutant = self.deck.replace("1 Decimator of the Provinces\n", "1 Bloom Tender\n")
        self.assertNotEqual(mutant, self.deck)
        with self.assertRaisesRegex(ValueError, "Cruise/value-and-fair-floor"):
            audit.check_deck_structure(mutant)

    def test_loss_of_toolbox_answer_rejected_even_with_100_cards(self):
        mutant = self.deck.replace("1 World Breaker\n", "1 Worldly Tutor\n")
        self.assertNotEqual(mutant, self.deck)
        with self.assertRaisesRegex(ValueError, "Sport/adaptive-interaction"):
            audit.check_deck_structure(mutant)

    def test_loss_of_combo_outlet_rejected_even_with_100_cards(self):
        mutant = self.deck.replace("1 Walking Ballista\n", "1 Mystic Remora\n")
        self.assertNotEqual(mutant, self.deck)
        with self.assertRaisesRegex(ValueError, "Race/deterministic-payoffs"):
            audit.check_deck_structure(mutant)

    def test_race_overlay_leak_into_cruise_rejected(self):
        changed = copy.deepcopy(self.elasticity)
        changed["mode_definitions"]["Cruise-R1"]["opponent_overlays"] = ["R3-HT"]
        with self.assertRaisesRegex(ValueError, "Cruise"):
            audit.check_policy(changed, self.race, self.conclusion)

    def test_missing_race_opponent_axis_rejected(self):
        changed = copy.deepcopy(self.elasticity)
        changed["mode_definitions"]["Race-R1"]["opponent_overlays"].remove("R3-M")
        with self.assertRaisesRegex(ValueError, "Race overlays"):
            audit.check_policy(changed, self.race, self.conclusion)

    def test_no_undocumented_card_change_authorization(self):
        changed = copy.deepcopy(self.elasticity)
        changed["hardware"]["card_changes_authorized"] = True
        with self.assertRaisesRegex(ValueError, "hardware edit"):
            audit.check_policy(changed, self.race, self.conclusion)

    def test_source_blob_mismatch_fails_closed(self):
        path = "experiments/manual-transmission/control/v0.7.decklist.txt"
        with self.assertRaisesRegex(ValueError, "pinned blob mismatch"):
            audit.check_blob(self.root, path, "0" * 40)

    def test_basic_opening_seven_is_probability_not_win_rate(self):
        p1 = audit.opening_seven_probability(99, 32, 0, 1)
        p2 = audit.opening_seven_probability(99, 32, 2, 4)
        self.assertTrue(0.0 < p1 < 1.0)
        self.assertTrue(0.0 < p2 < 1.0)
        self.assertTrue(p1 + p2 < 1.0)


if __name__ == "__main__":
    unittest.main()
