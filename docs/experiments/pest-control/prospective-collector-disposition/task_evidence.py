"""Prospective log evidence only. Never reclassifies a consumed attempt."""
import re


def single_fresh_test_execution(log: str, task: str) -> dict:
    """Allow repeated Gradle progress display, require one actual test executor.

    This contract is limited to one test task and one worker. Callers must also
    verify immutable source, command exit, exact raw XML and execution authority.
    """
    if not re.fullmatch(r":[A-Za-z0-9_:-]+:test", task):
        raise ValueError("Expected one absolute Gradle test task")
    display = re.findall(r"^> Task (\S+)(.*)$", log, re.MULTILINE)
    tests = [(name, suffix.strip()) for name, suffix in display if name.endswith(":test")]
    if not tests or any(name != task or suffix for name, suffix in tests):
        raise ValueError("Missing, nonfresh, failed or additional test task")
    starts = re.findall(r"^Gradle Test Executor (\d+) started executing tests\.$", log, re.MULTILINE)
    ends = re.findall(r"^Gradle Test Executor (\d+) finished executing tests\.$", log, re.MULTILINE)
    if len(starts) != 1 or ends != starts:
        raise ValueError("Expected exactly one paired actual test executor")
    start_pos = log.index(f"Gradle Test Executor {starts[0]} started executing tests.")
    end_pos = log.index(f"Gradle Test Executor {ends[0]} finished executing tests.")
    if start_pos >= end_pos:
        raise ValueError("Executor completion precedes its start")
    if len(re.findall(r"^BUILD SUCCESSFUL\b", log, re.MULTILINE)) != 1:
        raise ValueError("Expected exactly one successful command")
    if re.search(r"^BUILD FAILED\b", log, re.MULTILINE):
        raise ValueError("Failed command present")
    if re.search(r"^BUILD SUCCESSFUL\b", log, re.MULTILINE).start() <= end_pos:
        raise ValueError("Command success precedes executor completion")
    return {"task": task, "display_count": len(tests), "executor_id": starts[0],
            "fresh_executor_count": 1, "evidence_only": True}
