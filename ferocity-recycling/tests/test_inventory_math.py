"""Cross-check exact combinatorics with exhaustive tiny decks, not a gameplay test."""
import importlib.util
import itertools
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("project", Path(__file__).parents[1] / "tools/project.py")
project = importlib.util.module_from_spec(spec)
spec.loader.exec_module(project)


class ExactInventoryArithmeticTest(unittest.TestCase):
    def test_hypergeometric_matches_exhaustive_subsets(self):
        for n in range(2, 10):
            for k in range(n + 1):
                for draws in range(1, n + 1):
                    hands = list(itertools.combinations(range(n), draws))
                    exact = sum(any(i < k for i in hand) for hand in hands) / len(hands)
                    self.assertAlmostEqual(project.hg(n, k, draws), exact, places=14)

    def test_disjoint_required_and_forbidden_sets(self):
        hands = list(itertools.combinations(range(9), 4))
        exact = sum(any(i < 2 for i in hand) and not any(2 <= i < 5 for i in hand) for hand in hands) / len(hands)
        formula = (project.choose(9-3, 4) - project.choose(9-3-2, 4)) / project.choose(9, 4)
        self.assertAlmostEqual(exact, formula, places=14)

    def test_joint_presence_inclusion_exclusion(self):
        hands = list(itertools.combinations(range(10), 4))
        exact = sum(any(i < 2 for i in hand) and any(2 <= i < 5 for i in hand) for hand in hands) / len(hands)
        formula = 1-(project.choose(8,4)+project.choose(7,4)-project.choose(5,4))/project.choose(10,4)
        self.assertAlmostEqual(exact, formula, places=14)


if __name__ == "__main__":
    unittest.main()
