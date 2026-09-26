from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("r1_allocation_plan.py")
SPEC = importlib.util.spec_from_file_location("r1_allocation_plan", MODULE_PATH)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)


class R1AllocationPlanTests(unittest.TestCase):
    def test_main_deck_parser_rejects_non_60_card_deck(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "bad.dck"
            path.write_text("[main]\n1 Forest\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "exactly 60"):
                module._parse_main_deck(path)

    def test_filter_requires_exact_frozen_deck_copy_set(self):
        labels = ["Forest#1", "Forest#2", "Swamp#1"]
        with self.assertRaisesRegex(ValueError, "60-card"):
            module._filtered_ordering([0, 1, 2], labels, {"Forest#1"})

    def test_live_plan_is_exactly_512_and_fail_closed(self):
        root = Path(__file__).resolve().parents[2]
        result = module.compile_plan(root, "0" * 40)
        self.assertEqual(result["status"], "QUALIFIED_SEED_FREE_EXACT_512_ALLOCATION_PLAN_ONLY")
        self.assertEqual(len(result["allocations"]), 512)
        self.assertEqual(result["allocations"][0]["allocation_id"], "IW_V2_R1_0001")
        self.assertEqual(result["allocations"][-1]["allocation_id"], "IW_V2_R1_0512")
        self.assertEqual(
            result["execution_order"]["minor"],
            [
                "IMMUTABLE_SUBMITTED_V1_0",
                "COMPACT_LOOP",
                "RECURSIVE_EGGS",
                "LEAN_TRON_HYBRID",
            ],
        )
        for allocation in result["allocations"]:
            self.assertEqual(allocation["initial_ordering_count"], 4)
            self.assertEqual(allocation["filtered_cards_per_ordering"], 60)
            self.assertEqual(len(allocation["initial_ordering_sha256"]), 4)
        self.assertEqual(
            result["official_counters"],
            {
                "allocations_initialized": 0,
                "actions_submitted": 0,
                "comparative_outcomes_exposed": 0,
            },
        )
        self.assertFalse(result["claim_creation_authorized"])
        self.assertFalse(result["initialization_admitted"])
        self.assertFalse(result["execution_authorized"])
        self.assertFalse(result["closes_official_r1_corpus_runner_binding"])


if __name__ == "__main__":
    unittest.main()
