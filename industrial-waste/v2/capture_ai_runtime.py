#!/usr/bin/env python3
"""Bind the existing Industrial qualification worker to its observed compiled runtime.

This runs only the already declared deterministic AI case bank. It refuses the official R1
environment and does not construct, admit, restore or launch an official allocation.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from observed_test_runtime import archive_classpath, record_classpath

BANK = Path("industrial-waste/v2/runtime-ai-case-bank.json")
INPUT_PATHS = [
    "ai/src", "rules-engine/src", "mtg-sdk/src", "buildSrc/src", "mtg-sets/src",
    ":(glob)mtg-sets/*/src/**", ":(glob)**/*.gradle.kts", ":(glob)**/gradle.properties",
    "gradle", "gradlew", "justfile", "scripts/test-class", "scripts/gradle-locked",
    "tools/observed_test_runtime.py", "industrial-waste/v2/capture_ai_runtime.py", str(BANK),
    "industrial-waste/v2/frozen-responder-equivalence-v1.json",
    "industrial-waste/control/industrial-waste-v1.0-submitted.dck",
    "industrial-waste/v2/candidates/compact-loop.dck",
    "industrial-waste/v2/candidates/recursive-eggs.dck",
    "industrial-waste/v2/candidates/lean-tron-hybrid.dck",
    ".github/workflows/industrial-waste-v2-r1-runtime-composition.yml",
]
COMMAND = [
    "just", "test-class", "IndustrialWasteV2AllocationRunnerTest",
    "--tests", "*IndustrialWasteV2*Test", "--tests", "*IndustrialWasteAdvisorModuleTest",
    "--tests", "*FrozenBaselineTest", "--rerun", "--no-build-cache", "--info", "--stacktrace",
    "--max-workers=1", "-PkotlinCompileParallelism=1",
    "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx4g",
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def git(*args):
    return subprocess.check_output(["git", "-C", str(ROOT), *args])


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def compiled_inputs():
    """Hash every declared compiled input and require its exact checked-in blob bytes."""
    index = {}
    for entry in git("ls-files", "--stage", "-z", "--", *INPUT_PATHS).split(b"\0"):
        if not entry:
            continue
        metadata, filename = entry.split(b"\t", 1)
        mode, blob, stage = metadata.decode().split()
        if stage != "0" or mode not in {"100644", "100755"}:
            raise ValueError("Unsupported or unmerged compiled input")
        index[filename.decode()] = (mode, blob)
    untracked = git("ls-files", "--others", "--exclude-standard", "-z", "--", *INPUT_PATHS)
    if untracked:
        raise ValueError("Untracked compiled inputs are not admitted")
    result = {}
    for name, (mode, blob) in sorted(index.items()):
        path = ROOT / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("Missing or aliased compiled input: " + name)
        data = path.read_bytes()
        actual_blob = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        if actual_blob != blob:
            raise ValueError("Compiled source differs from its Git index: " + name)
        result[name] = {"sha256": sha(data), "bytes": len(data), "git_mode": mode, "git_blob": blob}
    if not result:
        raise ValueError("No compiled input identities")
    return result


def collect_cases(attempt, bank, started, finished):
    expected = {row["class"]: row for row in bank["cases"]}
    actual = {}
    folder = attempt / "junit"
    folder.mkdir()
    paths = sorted((ROOT / "ai/build/test-results/test").glob("TEST-*.xml"))
    for path in paths:
        shutil.copyfile(path, folder / path.name)
    for path in paths:
        raw = path.read_bytes()
        suite = ET.fromstring(raw)
        name = suite.attrib["name"]
        if suite.tag != "testsuite" or name not in expected or name in actual:
            raise ValueError("Unexpected or duplicate actual suite: " + name)
        stamp = dt.datetime.fromisoformat(suite.attrib["timestamp"].replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            stamp = stamp.replace(tzinfo=dt.timezone.utc)
        if not started <= stamp.timestamp() <= finished or not started <= path.stat().st_mtime <= finished:
            raise ValueError("JUnit is outside the actual command interval: " + name)
        cases = suite.findall("testcase")
        names = [case.attrib["name"] for case in cases]
        if len(names) != len(set(names)) or set(names) != set(expected[name]["cases"]):
            raise ValueError("Actual case identities differ from the frozen bank: " + name)
        if any(case.attrib.get("classname") != name for case in cases):
            raise ValueError("Case class differs from its suite")
        skipped = [case.attrib["name"] for case in cases if case.find("skipped") is not None]
        if sorted(skipped) != sorted(expected[name]["allowed_skips"]):
            raise ValueError("Only the original explicit official-execution refusal may be skipped")
        if any(case.find(tag) is not None for case in cases for tag in ("failure", "error")):
            raise ValueError("Actual failed or errored case: " + name)
        if int(suite.attrib["tests"]) != len(cases) or int(suite.attrib.get("skipped", 0)) != len(skipped):
            raise ValueError("JUnit case counters differ from actual nodes")
        if any(int(suite.attrib.get(tag, 0)) for tag in ("failures", "errors")):
            raise ValueError("JUnit suite reports a failure or error")
        actual[name] = {"cases": names, "skipped": skipped, "xml_sha256": sha(raw)}
    if set(actual) != set(expected):
        raise ValueError("A frozen AI suite did not execute")
    total = sum(len(row["cases"]) for row in actual.values())
    passed = total - sum(len(row["skipped"]) for row in actual.values())
    if total != bank["expected_total"] or passed != bank["expected_passed"]:
        raise ValueError("The complete existing AI case bank is required")
    return actual


def run(attempt, gradle):
    if os.environ.get("IW_V2_R1_PREPARED_DIRECTORY"):
        raise ValueError("Official R1 execution environment is forbidden")
    head = git("rev-parse", "HEAD").decode().strip()
    if head != os.environ["GITHUB_SHA"]:
        raise ValueError("Actual checkout differs from the workflow source")
    if git("diff", "--cached", "--name-only").strip():
        raise ValueError("The source index differs from HEAD")
    before = compiled_inputs()
    bank_raw = (ROOT / BANK).read_bytes()
    bank = json.loads(bank_raw)
    attempt = attempt.resolve()
    attempt.mkdir(parents=True, exist_ok=False)
    write_json(attempt / "compiled-inputs-before.json", before)
    shutil.copyfile(ROOT / BANK, attempt / "frozen-case-bank.json")
    stale = attempt / "rejected-stale-junit"
    stale.mkdir()
    for path in (ROOT / "ai/build/test-results/test").glob("TEST-*.xml"):
        shutil.move(path, stale / path.name)
    started = now()
    receipt = {"schema": "industrial-ai-worker-capture-v1", "status": "RUNNING",
               "source_head": head, "source_tree": git("rev-parse", "HEAD^{tree}").decode().strip(),
               "started_at_utc": started, "command": COMMAND, "bank_sha256": sha(bank_raw),
               "compiled_inputs_unchanged": False, "stages": [], "official_games": 0,
               "official_execution_authorized": False}
    write_json(attempt / "receipt.json", receipt)
    try:
        with (attempt / "qualification.log").open("x") as log:
            result = subprocess.run(COMMAND, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        finished = now()
        receipt["command_finished_at_utc"] = finished
        receipt["command_exit_status"] = result.returncode
        receipt["stages"] = [{"module": "ai", "started_at_utc": started, "finished_at_utc": finished}]
        after = compiled_inputs()
        write_json(attempt / "compiled-inputs-after.json", after)
        if git("diff", "--cached", "--name-only", "HEAD").strip():
            raise ValueError("The source index differs from HEAD after the actual command")
        receipt["compiled_inputs_unchanged"] = before == after
        receipt["cases"] = collect_cases(attempt, bank, dt.datetime.fromisoformat(started).timestamp(),
                                          dt.datetime.fromisoformat(finished).timestamp())
        log = (attempt / "qualification.log").read_text()
        if "> Task :ai:test\n" not in log and "> Task :ai:test\r\n" not in log:
            raise ValueError("No fresh actual AI test-task marker")
        if result.returncode or not receipt["compiled_inputs_unchanged"]:
            raise ValueError("Actual qualification failed or compiled inputs changed")
        if head != git("rev-parse", "HEAD").decode().strip() or (ROOT / BANK).read_bytes() != bank_raw:
            raise ValueError("Qualification source or fixed bank changed")
        registry_path = ROOT / "industrial-waste/v2/runtime-evidence/compiled-card-registry.json"
        registry_raw = registry_path.read_bytes()
        registry = json.loads(registry_raw)
        if registry["schema"] != "industrial-compiled-card-registry-observation-v1" or registry["source_head"] != head:
            raise ValueError("Compiled registry does not bind the actual qualification source")
        names = registry["frozen_card_names"]
        tokens = registry["registered_token_names"]
        definitions = registry["definitions"]
        if len(names) != len(set(names)) or len(names) != 34 or len(tokens) != len(set(tokens)):
            raise ValueError("Compiled registry identity sets are malformed")
        if [row["name"] for row in definitions] != sorted(set(names) | set(tokens)):
            raise ValueError("Compiled registry definitions do not cover the declared identity sets")
        for row in definitions:
            if sha(row["raw_definition_json"].encode()) != row["raw_definition_sha256"]:
                raise ValueError("Raw compiled definition digest differs")
            if json.loads(row["raw_definition_json"])["name"] != row["name"]:
                raise ValueError("Raw compiled definition name differs")
        if not dt.datetime.fromisoformat(started).timestamp() <= registry_path.stat().st_mtime <= dt.datetime.fromisoformat(finished).timestamp():
            raise ValueError("Compiled registry observation is outside the actual worker interval")
        shutil.copyfile(registry_path, attempt / "compiled-card-registry.json")
        receipt["compiled_registry"] = {"sha256": sha(registry_raw), "frozen_identities": len(names),
                                        "registered_token_names": tokens, "definitions": len(definitions)}
        workers = re.findall(r"Starting process 'Gradle Test Executor [^']+'[^\n]* Command: (\S+) ([^\n]+)", log)
        if len(workers) != 1:
            raise ValueError("Expected one actual logged AI test-worker executable")
        java = Path(workers[0][0]).resolve(strict=True)
        if not Path(workers[0][0]).is_absolute() or java.name != "java" or java.parent.name != "bin":
            raise ValueError("Actual worker command does not identify an absolute Java executable")
        argument_files = re.findall(r"@(\S*gradle-worker-classpath\S+)", workers[0][1])
        if len(argument_files) != 1:
            raise ValueError("Actual worker command does not identify one classpath argument file")
        worker_arguments = Path(argument_files[0]).read_bytes()
        receipt["actual_worker"] = {"java_path": str(java), "java_sha256": sha(java.read_bytes()),
                                     "argument_file_sha256": sha(worker_arguments)}
        version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
        receipt["java"] = {"sha256": sha(java.read_bytes()), "version_output": version.stdout + version.stderr}
        receipt["status"] = "PASS"
    except Exception as error:
        receipt["status"] = "FAILED_OR_INCOMPLETE"
        receipt["error"] = {"type": type(error).__name__, "message": str(error)}
    finally:
        receipt["finished_at_utc"] = now()
        write_json(attempt / "receipt.json", receipt)
    if receipt["status"] != "PASS":
        return 1
    if record_classpath(ROOT, attempt, gradle, schema="industrial-post-run-runtime-classpath-observation-v1",
                        recorder_source=Path(__file__)):
        return 1
    observation = json.loads((attempt / "runtime-classpath-observation/manifest.json").read_text())
    stages = observation["stages"]
    if len(stages) != 1 or len(stages[0]["matching_workers"]) != 1:
        raise ValueError("Observed classpath cannot be assigned to the actual single AI worker")
    if stages[0]["matching_workers"][0]["argument_file_sha256"] != receipt["actual_worker"]["argument_file_sha256"]:
        raise ValueError("Observed classpath argument bytes differ from the actual logged worker")
    if git("diff", "--cached", "--name-only", "HEAD").strip() or compiled_inputs() != before:
        raise ValueError("The qualified inputs or index changed before runtime archiving")
    archive_classpath(ROOT, attempt, gradle, expected_module="ai", output_name="observed-ai-runtime",
                     schema="industrial-observed-ai-runtime-archive-v1", archiver_source=Path(__file__),
                     java_executable=receipt["actual_worker"]["java_path"])
    archived = json.loads((attempt / "observed-ai-runtime/runtime-archive-receipt.json").read_text())
    if archived["java_executable"]["sha256"] != receipt["actual_worker"]["java_sha256"]:
        raise ValueError("Actual logged worker executable changed before runtime archive binding")
    if git("diff", "--cached", "--name-only", "HEAD").strip() or compiled_inputs() != before:
        raise ValueError("The qualified inputs or index changed during runtime archiving")
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--attempt", required=True, type=Path)
    parser.add_argument("--gradle-home", type=Path, default=Path.home() / ".gradle")
    args = parser.parse_args()
    raise SystemExit(run(args.attempt, args.gradle_home))
