#!/usr/bin/env python3
"""Preserve exact existing Izzet regression diagnostics; never admit gameplay."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import traceback
import xml.etree.ElementTree as ET

MARKER = "IZZET_DIAGNOSTIC_TRACE "
ANCHOR = "        fun record(entry: String) = stream?.update(entry.toByteArray(Charsets.UTF_8))"
REPLACEMENT = """        fun record(entry: String) {
            stream?.update(entry.toByteArray(Charsets.UTF_8))
            if (recordActionStream) {
                println("IZZET_DIAGNOSTIC_TRACE " + java.util.Base64.getEncoder().encodeToString(entry.toByteArray(Charsets.UTF_8)))
            }
        }"""


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def extract_trace(xml_path: Path, output: Path) -> dict:
    suite = ET.fromstring(xml_path.read_bytes())
    chunks = []
    for node in suite.iter("system-out"):
        for line in (node.text or "").splitlines():
            if MARKER in line:
                encoded = line.split(MARKER, 1)[1].strip()
                chunks.append(base64.b64decode(encoded, validate=True))
    result = {"entries": len(chunks), "complete": False}
    if not chunks:
        return result
    raw = b"".join(chunks)
    (output / "baseline-hash-input.bin").write_bytes(raw)
    result.update(bytes=len(raw), sha256=sha(raw))
    numbered = chunks[:-1]
    expected = list(range(1, len(numbered) + 1))
    actual = []
    for entry in numbered:
        match = re.match(rb"[AD]([0-9]+)\|", entry)
        actual.append(int(match.group(1)) if match else None)
    result["numbered_entries"] = len(numbered)
    result["complete"] = (
        actual == expected and chunks[-1].startswith(b"END|")
        and all(c.endswith(b"\n") and c.count(b"\n") == 1 for c in chunks)
    )
    result["end_record"] = chunks[-1].decode("utf-8")
    return result


def run_stage(root: Path, output: Path, stage: dict) -> dict:
    name = stage["class"].rsplit(".", 1)[1]
    target = output / name
    target.mkdir()
    result = {"class": stage["class"], "expected_cases": stage["case_names"],
              "official_games": 0, "execution_admission": False}
    command = ["just", "test-class", name, "--rerun", "--max-workers=1",
               "-PkotlinCompileParallelism=1", "-Pkotlin.compiler.execution.strategy=in-process",
               "-Dorg.gradle.jvmargs=-Xmx4g", "--info", "--stacktrace"]
    result["command"] = command
    print("DIAGNOSTIC_STAGE_START " + name, flush=True)
    with (target / "gradle.log").open("wb") as log:
        process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        assert process.stdout is not None
        for line in process.stdout:
            log.write(line)
            log.flush()
            sys.stdout.buffer.write(line)
            sys.stdout.buffer.flush()
        result["exit"] = process.wait()
    log_text = (target / "gradle.log").read_text(errors="replace")
    task = ":" + stage["module"].replace("/", ":") + ":test"
    result["fresh_executor_started"] = bool(re.search(r"Gradle Test Executor [0-9]+ started executing tests", log_text))
    result["target_task_not_executed"] = bool(re.search(
        re.escape(task) + r" (?:NO-SOURCE|UP-TO-DATE|FROM-CACHE|SKIPPED)(?:\s|$)", log_text))
    reports = root / stage["module"] / "build/test-results/test"
    if reports.is_dir():
        shutil.copytree(reports, target / "raw-test-results")
    xmls = sorted((target / "raw-test-results").glob("TEST-*.xml"))
    result["xml_files"] = [{"path": p.name, "sha256": sha(p.read_bytes())} for p in xmls]
    rows = []
    for path in xmls:
        suite = ET.fromstring(path.read_bytes())
        cases = suite.findall("testcase")
        rows.append({"class": suite.attrib["name"], "cases": [c.attrib["name"] for c in cases],
                     "failures": sum(c.find("failure") is not None for c in cases),
                     "errors": sum(c.find("error") is not None for c in cases),
                     "skips": sum(c.find("skipped") is not None for c in cases),
                     "declared": {k: int(suite.attrib[k]) for k in ("tests", "failures", "errors", "skipped")}})
    result["suites"] = rows
    result["case_inventory_complete"] = (
        len(rows) == 1 and rows[0]["class"] == stage["class"]
        and len(rows[0]["cases"]) == len(set(rows[0]["cases"])) == stage["cases"]
        and set(rows[0]["cases"]) == set(stage["case_names"])
        and rows[0]["declared"] == {"tests": stage["cases"], "failures": rows[0]["failures"],
                                     "errors": rows[0]["errors"], "skipped": rows[0]["skips"]}
    )
    if name == "FrozenBaselineTest" and len(xmls) == 1:
        result["baseline_trace"] = extract_trace(xmls[0], target)
    result["diagnostic_material_complete"] = (
        result["case_inventory_complete"] and result["fresh_executor_started"]
        and not result["target_task_not_executed"]
        and (name != "FrozenBaselineTest" or result.get("baseline_trace", {}).get("complete", False))
    )
    result["original_tests_passed"] = (
        result["exit"] == 0 and result["case_inventory_complete"]
        and all(r["failures"] == r["errors"] == r["skips"] == 0 for r in rows)
    )
    write_json(target / "stage-receipt.json", result)
    print("DIAGNOSTIC_STAGE_END " + json.dumps(result), flush=True)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--scope", type=Path, required=True)
    parser.add_argument("--case", choices=["accepted-base", "candidate"], required=True)
    parser.add_argument("--control-source", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root, output = args.source_root.resolve(), args.output.resolve()
    assert output != root and root not in output.parents, "Evidence must be outside tested checkout"
    output.mkdir(parents=True, exist_ok=False)
    scope_raw = args.scope.read_bytes()
    scope = json.loads(scope_raw)
    selected = scope["sources"][args.case]
    receipt = {"schema": "izzet-c47e-diagnostic-attempt-v1", "case": args.case,
               "scope_sha256": sha(scope_raw), "official_games": 0, "official_vector_access": False,
               "new_experimental_allocations": 0, "execution_admission": False, "stages": []}
    exit_code = 2
    try:
        control_root = Path(__file__).resolve().parents[3]
        receipt["control_source"] = git(control_root, "rev-parse", "HEAD")
        assert receipt["control_source"] == args.control_source == os.environ["GITHUB_SHA"]
        receipt["control_tree"] = git(control_root, "rev-parse", "HEAD^{tree}")
        receipt["workflow"] = {key: os.environ.get(key) for key in (
            "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_EVENT_NAME", "GITHUB_REF", "GITHUB_WORKFLOW")}
        receipt["source"] = git(root, "rev-parse", "HEAD")
        receipt["tree"] = git(root, "rev-parse", "HEAD^{tree}")
        receipt["initial_status"] = git(root, "status", "--porcelain")
        receipt["source_sha256"] = {p: sha((root / p).read_bytes()) for p in selected["pins"]}
        write_json(output / "source-binding.json", receipt)
        assert receipt["source"] == selected["source"] and receipt["tree"] == selected["tree"]
        assert receipt["initial_status"] == "" and receipt["source_sha256"] == selected["pins"]
        assert os.environ.get("GRADLE_LOCK_SLOTS") == "1"
        for stage in selected["stages"]:
            assert not (root / stage["module"] / "build/test-results/test").exists(), "Pre-existing XML rejected"
        for path in selected["pins"]:
            dest = output / "original-source" / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes((root / path).read_bytes())
        instrumented = root / scope["permitted_instrumented_path"]
        original = instrumented.read_text()
        assert original.count(ANCHOR) == 1 and MARKER not in original
        instrumented.write_text(original.replace(ANCHOR, REPLACEMENT))
        receipt["instrumented_sha256"] = sha(instrumented.read_bytes())
        (output / "instrumented-TableGameRunner.kt").write_bytes(instrumented.read_bytes())
        (output / "instrumentation.patch").write_text(git(root, "diff", "--", scope["permitted_instrumented_path"]) + "\n")
        assert git(root, "diff", "--name-only").splitlines() == [scope["permitted_instrumented_path"]]
        for stage in selected["stages"]:
            try:
                receipt["stages"].append(run_stage(root, output, stage))
            except Exception as exc:
                # Keep already copied logs/XML and continue the other declared original classes.
                receipt["stages"].append({"class": stage["class"], "diagnostic_error": repr(exc)})
                traceback.print_exc()
            write_json(output / "attempt-receipt.json", receipt)
        receipt["final_diff_paths"] = git(root, "diff", "--name-only").splitlines()
        assert receipt["final_diff_paths"] == [scope["permitted_instrumented_path"]]
        assert sha(instrumented.read_bytes()) == receipt["instrumented_sha256"]
        for path, digest in selected["pins"].items():
            if path != scope["permitted_instrumented_path"]:
                assert sha((root / path).read_bytes()) == digest, path
        complete = all(s.get("diagnostic_material_complete", False) for s in receipt["stages"])
        passed = all(s.get("original_tests_passed", False) for s in receipt["stages"])
        receipt["disposition"] = ("DIAGNOSTIC_INCOMPLETE" if not complete else
                                  "ORIGINAL_TESTS_PASS_DIAGNOSTIC_ONLY" if passed else
                                  "DIAGNOSTIC_COMPLETE_ORIGINAL_TEST_FAILURES_PRESERVED")
        exit_code = 0 if complete and passed else 1
    except Exception as exc:
        receipt["disposition"] = "DIAGNOSTIC_PREFLIGHT_OR_INTEGRITY_FAILURE"
        receipt["error"] = repr(exc)
        traceback.print_exc()
    finally:
        receipt["exit"] = exit_code
        write_json(output / "attempt-receipt.json", receipt)
        files = {str(p.relative_to(output)): {"bytes": p.stat().st_size, "sha256": sha(p.read_bytes())}
                 for p in sorted(output.rglob("*")) if p.is_file() and p.name != "artifact-files.json"}
        write_json(output / "artifact-files.json", files)
        print("DIAGNOSTIC_ATTEMPT " + json.dumps(receipt), flush=True)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
