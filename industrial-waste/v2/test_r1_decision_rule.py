"""Synthetic arithmetic fixtures only: no deck, corpus row contents or engine."""
import copy
import math
import unittest

from r1_decision_rule import CONTROL, DECKS, FAMILIES, METRICS, SCHEDULES, evaluate_complete_metrics


def synthetic_grid():
    return [dict(deck=deck, row=row, schedule=schedule, validity="VALID", terminal_status="TURN_CAP",
                 **dict.fromkeys(METRICS, False))
            for deck in DECKS for row in range(1, 65) for schedule in SCHEDULES]


def mark(records, deck, metric, first, count):
    selected = [record for record in records if record["deck"] == deck]
    for record in selected[first:first + count]:
        record[metric] = True


class R1NumericalRuleTest(unittest.TestCase):
    def test_valid_caps_are_failures_for_unachieved_metrics_and_zero_eligible_stops(self):
        result = evaluate_complete_metrics(synthetic_grid())
        self.assertTrue(result["all_candidates_fail_r1"])
        self.assertEqual(result["qualifies_for_prospective_recovery_gate"], [])
        self.assertFalse(result["authorizes_execution"])
        self.assertFalse(result["promotes_deck"])
        for comparison in result["comparisons"].values():
            self.assertEqual(comparison["metrics"]["conversion_by_t8"]["candidate_numerator"], 0)

    def test_exact_eight_improvements_and_four_extra_failures_pass(self):
        records = synthetic_grid()
        mark(records, FAMILIES[0], "loop_ready_by_t8", 0, 8)
        mark(records, FAMILIES[0], "colored_mana_failure", 0, 4)
        mark(records, FAMILIES[0], "total_mana_stranded_at_t4", 0, 4)
        result = evaluate_complete_metrics(records)
        self.assertEqual(result["qualifies_for_prospective_recovery_gate"], [FAMILIES[0]])

    def test_one_beyond_each_frozen_boundary_is_rejected(self):
        for field in ("loop_ready_by_t8", "colored_mana_failure", "total_mana_stranded_at_t4"):
            with self.subTest(field=field):
                records = synthetic_grid()
                mark(records, FAMILIES[0], "loop_ready_by_t8", 0, 7 if field == "loop_ready_by_t8" else 8)
                if field != "loop_ready_by_t8":
                    mark(records, FAMILIES[0], field, 0, 5)
                self.assertTrue(evaluate_complete_metrics(records)["all_candidates_fail_r1"])

    def test_net_paired_counts_include_both_improvements_and_regressions(self):
        records = synthetic_grid()
        mark(records, CONTROL, "conversion_by_t8", 0, 4)
        mark(records, FAMILIES[0], "conversion_by_t8", 4, 12)
        result = evaluate_complete_metrics(records)
        metric = result["comparisons"][FAMILIES[0]]["metrics"]["conversion_by_t8"]
        self.assertEqual((metric["paired_candidate_only"], metric["paired_control_only"], metric["net_count_delta"]), (12, 4, 8))
        self.assertTrue(result["comparisons"][FAMILIES[0]]["eligible"])

    def test_ranked_maximum_two_uses_conversion_then_loop_and_lexical_ties(self):
        records = synthetic_grid()
        for family in FAMILIES:
            mark(records, family, "conversion_by_t8", 0, 8)
        result = evaluate_complete_metrics(records)
        self.assertEqual(result["qualifies_for_prospective_recovery_gate"], ["COMPACT_LOOP", "LEAN_TRON_HYBRID"])
        mark(records, "RECURSIVE_EGGS", "conversion_by_t8", 8, 2)
        mark(records, "LEAN_TRON_HYBRID", "loop_ready_by_t8", 0, 12)
        result = evaluate_complete_metrics(records)
        self.assertEqual(result["qualifies_for_prospective_recovery_gate"], ["RECURSIVE_EGGS", "LEAN_TRON_HYBRID"])

    def test_failure_deltas_break_ties_in_the_predeclared_order(self):
        records = synthetic_grid()
        for family in FAMILIES:
            mark(records, family, "loop_ready_by_t8", 0, 8)
        mark(records, "COMPACT_LOOP", "colored_mana_failure", 0, 1)
        mark(records, "LEAN_TRON_HYBRID", "total_mana_stranded_at_t4", 0, 1)
        result = evaluate_complete_metrics(records)
        self.assertEqual(result["qualifies_for_prospective_recovery_gate"], ["RECURSIVE_EGGS", "LEAN_TRON_HYBRID"])

    def test_uncertainty_uses_64_row_clusters_instead_of_128_independent_members(self):
        records = synthetic_grid()
        mark(records, FAMILIES[0], "loop_ready_by_t8", 0, 64)
        metric = evaluate_complete_metrics(records)["comparisons"][FAMILIES[0]]["metrics"]["loop_ready_by_t8"]
        self.assertEqual(metric["row_cluster_differences"], [1.0] * 32 + [0.0] * 32)
        self.assertEqual(metric["mean_paired_difference"], 0.5)
        self.assertAlmostEqual(metric["cluster_standard_error"], math.sqrt(0.25 / 63))
        self.assertAlmostEqual(metric["normal_approximation_95_interval"][0], 0.5 - 1.96 * math.sqrt(0.25 / 63))

    def test_incomplete_duplicate_extra_or_unadmitted_allocations_fail_closed(self):
        base = synthetic_grid()
        cases = [base[:-1], base + [copy.deepcopy(base[0])], [copy.deepcopy(base[0])] + base[1:-1] + [copy.deepcopy(base[0])]]
        rogue = copy.deepcopy(base)
        rogue[0]["deck"] = "FOURTH_FAMILY"
        cases.append(rogue)
        for records in cases:
            with self.subTest(size=len(records)):
                with self.assertRaises(ValueError):
                    evaluate_complete_metrics(records)

    def test_invalid_unresolved_and_untyped_metrics_are_not_converted_into_losses(self):
        for field, value in [("validity", "QUARANTINED"), ("terminal_status", "EXCEPTION"),
                             ("terminal_status", "REJECTED_ACTION"), ("terminal_status", "UNRESOLVED_TELEMETRY"),
                             ("loop_ready_by_t8", None), ("loop_ready_by_t8", 1), ("row", True)]:
            with self.subTest(field=field, value=value):
                records = synthetic_grid()
                records[0][field] = value
                with self.assertRaises(ValueError):
                    evaluate_complete_metrics(records)


if __name__ == "__main__":
    unittest.main()
