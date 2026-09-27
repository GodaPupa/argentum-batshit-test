#!/usr/bin/env python3
"""Run preserved deterministic banks on one immutable Izzet receiving source."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

SOURCE = "60d2d6f6412d1f4e9238ca0c84755c34cd31f8d5"
TREE = "1a903dc4862d32fdd029e73a916cc9659678db4d"
ROOT = Path(os.environ["GITHUB_WORKSPACE"])
CHECKOUT = ROOT / "source"
CONTROL = ROOT / "control"
OUT = ROOT / "output"
CONTROL_PATHS = [".github/workflows/izzet-shared-receiving.yml",
                 "izzet-receiving-qualification.py", "receiving-gate.json"]


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_snapshot(gate):
    result = {"head": git(CHECKOUT, "rev-parse", "HEAD"),
              "tree": git(CHECKOUT, "rev-parse", "HEAD^{tree}"),
              "status": git(CHECKOUT, "status", "--porcelain"),
              "authority_sha256": {}, "test_source_git_blobs": {}}
    assert result["head"] == gate["source_head"] == SOURCE
    assert result["tree"] == gate["source_tree"] == TREE
    assert not result["status"], "Tracked/untracked source changed"
    for path, expected in gate["preserved_authority_sha256"].items():
        actual = sha(CHECKOUT / path)
        result["authority_sha256"][path] = actual
        assert actual == expected, "Preserved authority changed: " + path
    for bank in gate["banks"]:
        path = bank["test_source"]
        actual = git(CHECKOUT, "hash-object", path)
        result["test_source_git_blobs"][path] = actual
        assert actual == bank["test_source_git_blob"], "Test source changed: " + path
    return result


def collect_xml(path, bank, started, finished):
    raw = path.read_bytes()
    suite = ET.fromstring(raw)
    cases = suite.findall("testcase")
    names = [case.attrib["name"] for case in cases]
    assert suite.tag == "testsuite" and suite.attrib["name"] == bank["class"]
    assert len(cases) == int(suite.attrib["tests"]) == bank["expected_cases"]
    assert len(names) == len(set(names)), "Duplicate test identity"
    assert sorted(names) == sorted(bank["case_names"]), "Original case identity drift"
    assert all(case.attrib.get("classname") == bank["class"] for case in cases)
    assert not any(int(suite.attrib.get(key, 0)) for key in ("failures", "errors", "skipped"))
    assert not any(suite.findall(".//" + key) for key in ("failure", "error", "skipped"))
    # Gradle output directories are required empty before this command; also reject stale/future XML.
    assert started - 2_000_000_000 <= path.stat().st_mtime_ns <= finished + 2_000_000_000
    return {"class": bank["class"], "actual_cases": len(cases), "case_names": names,
            "xml_sha256": hashlib.sha256(raw).hexdigest()}


def run_bank(bank, index):
    folder = OUT / f"{index:02d}-{bank['stage']}"
    folder.mkdir()
    xml_dir = CHECKOUT / bank["module"] / "build/test-results/test"
    # Only generated reports in this freshly created, owned qualification checkout are removed.
    for path in xml_dir.glob("TEST-*.xml"):
        path.unlink()
    assert not list(xml_dir.glob("TEST-*.xml"))
    command = ["just", "test-class", bank["class"].rsplit(".", 1)[1],
               "--rerun", "--no-build-cache", "-DupdateSnapshots=false", "--info", "--stacktrace",
               "--max-workers=1", "-PkotlinCompileParallelism=1",
               "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx4g"]
    row = {"stage": bank["stage"], "scope": bank["scope"], "class": bank["class"],
           "module": bank["module"], "expected_cases": bank["expected_cases"],
           "command": command, "status": "INCOMPLETE", "started_ns": time.time_ns()}
    (folder / "command.json").write_text(json.dumps(row, indent=2) + "\n")
    try:
        with (folder / "command.log").open("wb") as log:
            result = subprocess.run(command, cwd=CHECKOUT, stdout=log, stderr=subprocess.STDOUT)
        row.update(exit_status=result.returncode, finished_ns=time.time_ns())
        (folder / "exit-status.txt").write_text(str(result.returncode) + "\n")
        paths = sorted(xml_dir.glob("TEST-*.xml"))
        # Preserve every produced XML before checking any failure, suite or count.
        for path in paths:
            shutil.copyfile(path, folder / path.name)
        assert result.returncode == 0, "Qualification command failed"
        assert len(paths) == 1 and paths[0].name == "TEST-" + bank["class"] + ".xml", "Suite mismatch"
        row["actual"] = collect_xml(paths[0], bank, row["started_ns"], row["finished_ns"])
        task = ":" + bank["module"].replace("/", ":") + ":test"
        lines = (folder / "command.log").read_text().splitlines()
        assert any(line.strip() == "> Task " + task for line in lines), "No fresh required test task"
        assert not any(line.strip() == "> Task " + task + " " + suffix
                       for line in lines for suffix in ("FROM-CACHE", "UP-TO-DATE", "NO-SOURCE", "SKIPPED"))
        assert any("Gradle Test Executor" in line and "started executing tests" in line for line in lines)
        assert not git(CHECKOUT, "status", "--porcelain"), "Source changed during bank"
        row["status"] = "PASS_PRESERVED_CASE_IDENTITIES"
    except Exception as error:
        row["status"] = "FAILED_PRESERVED_FOR_REVIEW"
        row["error"] = type(error).__name__ + ": " + str(error)
    finally:
        (folder / "command.json").write_text(json.dumps(row, indent=2) + "\n")
    return row


def main():
    OUT.mkdir(exist_ok=False)
    gate_path = CONTROL / "receiving-gate.json"
    gate = json.loads(gate_path.read_text())
    manifest = {"schema": "izzet-shared-exact-receiving-audit-v1", "status": "INCOMPLETE",
                "source_head": SOURCE, "source_tree": TREE,
                "control_head": git(CONTROL, "rev-parse", "HEAD"),
                "run_id": os.environ["GITHUB_RUN_ID"], "attempt": os.environ["GITHUB_RUN_ATTEMPT"],
                "event": os.environ["GITHUB_EVENT_NAME"], "stages": [], "errors": [],
                "new_pilot_cases": 0, "official_games": 0, "official_seeds": 0,
                "gameplay_authorized": False, "full_runtime_accepted": False}
    try:
        assert os.environ["GITHUB_REPOSITORY"] == "GodaPupa/argentum-batshit-test"
        assert os.environ["GITHUB_RUN_ATTEMPT"] == "1" and os.environ["GITHUB_EVENT_NAME"] == "push"
        assert manifest["control_head"] == os.environ["GITHUB_SHA"]
        assert not git(CONTROL, "status", "--porcelain")
        assert sorted(git(CONTROL, "ls-files").splitlines()) == sorted(CONTROL_PATHS)
        assert gate["ready_for_execution"] is True and gate["source_review"]
        manifest["control_files_sha256"] = {p: sha(CONTROL / p) for p in CONTROL_PATHS}
        for path in CONTROL_PATHS:
            target = OUT / "control" / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(CONTROL / path, target)
        identities = [(bank["class"], name) for bank in gate["banks"] for name in bank["case_names"]]
        assert len(gate["banks"]) == 87 and len(identities) == len(set(identities)) == 973
        manifest["source_before"] = source_snapshot(gate)
        manifest["dependency_files_sha256"] = {p: sha(CHECKOUT / p) for p in gate["dependency_files"]}
        (OUT / "source-before.json").write_text(json.dumps(manifest["source_before"], indent=2) + "\n")
        (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
        for index, bank in enumerate(gate["banks"], 1):
            assert source_snapshot(gate) == manifest["source_before"], "Source drift before stage"
            manifest["current_stage"] = bank["stage"]
            (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
            manifest["stages"].append(run_bank(bank, index))
            (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
        manifest["source_after"] = source_snapshot(gate)
        (OUT / "source-after.json").write_text(json.dumps(manifest["source_after"], indent=2) + "\n")
        assert manifest["source_before"] == manifest["source_after"]
        assert manifest["control_files_sha256"] == {p: sha(CONTROL / p) for p in CONTROL_PATHS}
        assert manifest["dependency_files_sha256"] == {p: sha(CHECKOUT / p) for p in gate["dependency_files"]}
        assert all(row["status"] == "PASS_PRESERVED_CASE_IDENTITIES" for row in manifest["stages"]), "One or more preserved classes failed; see raw artifacts"
        assert sum(row["actual"]["actual_cases"] for row in manifest["stages"]) == 973
        manifest["status"] = "PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW"
    except Exception as error:
        manifest["errors"].append(type(error).__name__ + ": " + str(error))
        raise
    finally:
        (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
