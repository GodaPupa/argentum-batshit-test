"""Synthetic log parser checks; no engine, original artifact or fixture execution."""
import unittest
from task_evidence import single_fresh_test_execution

TASK = ":gym:test"
START = "Gradle Test Executor 1 started executing tests.\n"
END = "Gradle Test Executor 1 finished executing tests.\n"
OK = "> Task :gym:test\n" + START + END + "BUILD SUCCESSFUL in 1s\n"


class TaskEvidenceTest(unittest.TestCase):
    def reject(self, log):
        with self.assertRaises(ValueError):
            single_fresh_test_execution(log, TASK)

    def test_single_display(self):
        self.assertEqual(single_fresh_test_execution(OK, TASK)["fresh_executor_count"], 1)

    def test_duplicate_display_one_executor(self):
        self.assertEqual(single_fresh_test_execution(OK.replace(END, "> Task :gym:test\n" + END), TASK)["display_count"], 2)

    def test_cached_task_rejected(self):
        self.reject(OK.replace(TASK, TASK + " FROM-CACHE"))

    def test_missing_executor_rejected(self):
        self.reject(OK.replace(START, ""))

    def test_two_executions_rejected_even_with_reused_id(self):
        self.reject(OK.replace(END, END + START + END))

    def test_unmatched_executor_rejected(self):
        self.reject(OK.replace(END, END.replace("1", "2")))

    def test_reversed_lifecycle_rejected(self):
        self.reject(OK.replace(START + END, END + START))
        self.reject("BUILD SUCCESSFUL in 1s\n" + OK.replace("BUILD SUCCESSFUL in 1s\n", ""))

    def test_other_test_task_rejected(self):
        self.reject(OK.replace(END, END + "> Task :rules-engine:test\n"))

    def test_failed_or_multiple_command_rejected(self):
        self.reject(OK.replace("BUILD SUCCESSFUL", "BUILD FAILED"))
        self.reject(OK + "BUILD SUCCESSFUL in 1s\n")


if __name__ == "__main__":
    unittest.main()
