#!/usr/bin/env python3
"""Retain one exact five-case Monster search receiving attempt; grant no game authority."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
PREFIX = "docs/experiments/pest-control/monster-search-receiving"
BANK_PATH = f"{PREFIX}/bank.json"
FREEZE_PATH = f"{PREFIX}/freeze.json"
BANK_SHA256 = "4795fae179accc2e5764657ef669834609d126b90ef332bd50cd9ca62e38e578"
WORKFLOW = ".github/workflows/pest-monster-search-actor-receiving.yml"
REPORT = ROOT / "build/reports/pest-monster-search-actor-receiving"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def write(name: str, data: object) -> None:
    REPORT.mkdir(parents=True, exist_ok=True)
    (REPORT / name).write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")


def load_bank() -> dict:
    raw = (ROOT / BANK_PATH).read_bytes()
    assert digest(raw) == BANK_SHA256, "Five-case bank bytes changed"
    bank = json.loads(raw)
    assert bank["status"] == "FROZEN_PROSPECTIVE_RECEIVING_ONLY"
    assert bank["case_count"] == 5 and len(bank["case_names"]) == 5
    assert len(set(bank["case_names"])) == 5
    assert all(name.startswith(f"MS{i:02d} ") for i, name in enumerate(bank["case_names"], 1))
    assert bank["games_authorized"] is False and bank["official_seeds_drawn"] == 0
    return bank


def source_snapshot(bank: dict) -> dict:
    head = git("rev-parse", "HEAD")
    assert head == os.environ["GITHUB_SHA"], "Requested source differs from HEAD"
    assert not git("status", "--porcelain", "--untracked-files=all"), "Source checkout is dirty"
    freeze = json.loads((ROOT / FREEZE_PATH).read_text())
    assert freeze["bank_sha256"] == BANK_SHA256
    assert freeze["status"] == "ACCEPT_FOR_EXACT_MS01_MS05_RECEIVING_ONLY", "Independent source review pending"
    assert freeze["source_review"]["accepted"] is True
    assert freeze["gate_review"]["accepted"] is True
    assert freeze["source_review"]["reviewer"] not in (None, "", "/root/pest_gate")
    assert freeze["gate_review"]["reviewer"] not in (None, "", "/root/pest_gate")
    assert re.fullmatch(r"[0-9a-f]{40}", freeze["source_review"]["review_ref"])
    assert re.fullmatch(r"[0-9a-f]{40}", freeze["gate_review"]["review_ref"])
    assert freeze["source_review"]["source_sha256"] == bank["source_sha256"]
    assert freeze["gate_review"]["workflow_sha256"] == digest((ROOT / WORKFLOW).read_bytes())
    assert freeze["gate_review"]["collector_sha256"] == digest(Path(__file__).read_bytes())
    candidate = freeze["source_review"]["candidate_commit"]
    assert re.fullmatch(r"[0-9a-f]{40}", candidate)

    hashes = {}
    controls = list(bank["source_sha256"]) + [BANK_PATH, FREEZE_PATH, WORKFLOW, f"{PREFIX}/collect.py"]
    for path in controls:
        raw = (ROOT / path).read_bytes()
        assert raw == subprocess.check_output(["git", "show", f"{head}:{path}"], cwd=ROOT), (
            f"Local control/source differs from Git: {path}")
        value = digest(raw)
        if path in bank["source_sha256"]:
            assert value == bank["source_sha256"][path], f"Frozen source changed: {path}"
        hashes[path] = value

    test_path = bank["test_source_path"]
    assert list(path for path in git("ls-files").splitlines()
                if path.endswith("/PestMonsterTronAuthorizedSearchReceivingTest.kt")) == [test_path]
    names = re.findall(r'\btest\("([^"]+)"\)', (ROOT / test_path).read_text())
    assert names == bank["case_names"], "Static class names differ from five-case bank"
    return {"head": head, "tree": git("rev-parse", "HEAD^{tree}"), "bank_sha256": BANK_SHA256,
            "source_sha256": hashes, "test_class": bank["test_class"], "case_names": names}


def bind() -> int:
    try:
        bank = load_bank()
        source = source_snapshot(bank)
        existing = list((ROOT / "gym/build/test-results/test").glob("TEST-*.xml"))
        assert not existing, f"Pre-existing gym XML is not admissible: {existing}"
        write("source-before.json", source)
        return 0
    except Exception as exc:
        write("bind-failure.json", {"status": "INCOMPLETE_NO_TEST_EXECUTION_AUTHORITY",
                                    "error": f"{type(exc).__name__}: {exc}", "games": 0})
        return 1


def collect() -> int:
    errors: list[str] = []
    receipt = {"schema": "pest-monster-search-actor-receiving-actual-v1",
               "status": "INCOMPLETE_RECEIVING", "errors": errors, "raw_cases": [],
               "gameplay_authorized": False, "pair_pilot_accepted": False,
               "c2_accepted": False, "a2_accepted": False, "official_games": 0,
               "official_seeds_drawn": 0, "historical_games_quarantined": 81}
    try:
        bank = load_bank()
        before = json.loads((REPORT / "source-before.json").read_text())
        after = source_snapshot(bank)
        write("source-after.json", after)
        assert before == after, "Source changed across the bounded test"
        receipt["source"] = before
        rc = (REPORT / "test-exit-code.txt").read_text().strip()
        receipt["test_exit_code"] = rc
        if rc != "0":
            errors.append(f"Test command exited {rc}")
        log_path = REPORT / "tests.log"
        log = log_path.read_bytes()
        receipt["test_log_sha256"] = digest(log)
        task_lines = [line for line in log.decode("utf-8", errors="replace").splitlines()
                      if re.match(r"^> Task :gym:test(?:\s|$)", line)]
        receipt["gym_test_task_lines"] = task_lines
        if len(task_lines) != 1 or any(marker in task_lines[0] for marker in
                                       ("FROM-CACHE", "UP-TO-DATE", "SKIPPED", "NO-SOURCE")):
            errors.append("Raw log does not prove one fresh :gym:test execution")
        expected = ROOT / "gym/build/test-results/test" / f'TEST-{bank["test_class"]}.xml'
        xml_paths = set((ROOT / "gym/build/test-results/test").glob("TEST-*.xml"))
        if xml_paths != {expected}:
            errors.append("Expected exactly one gym XML; found: " +
                          repr(sorted(str(path.relative_to(ROOT)) for path in xml_paths)))
        if expected.exists():
            raw = expected.read_bytes()
            receipt["xml_path"] = str(expected.relative_to(ROOT))
            receipt["xml_sha256"] = digest(raw)
            suite = ET.fromstring(raw)
            cases = suite.findall("testcase")
            if suite.attrib.get("name") != bank["test_class"] or len(cases) != 5 or (
                int(suite.attrib.get("tests", -1)) != 5):
                errors.append("Class or actual XML count differs from the five-case bank")
            for key in ("failures", "errors", "skipped"):
                if int(suite.attrib.get(key, 0)) != 0:
                    errors.append(f"XML {key}={suite.attrib[key]}")
            names = [case.attrib.get("name", "") for case in cases]
            if len(names) != len(set(names)) or set(names) != set(bank["case_names"]):
                errors.append("Actual XML case names differ from bank")
            for case in cases:
                outcomes = [{"kind": node.tag, "attributes": node.attrib, "text": node.text or ""}
                            for node in case if node.tag in ("failure", "error", "skipped")]
                if case.attrib.get("classname") != bank["test_class"] or outcomes:
                    errors.append(f"Unexpected class or nonpass case: {case.attrib.get('name')}")
                receipt["raw_cases"].append({"name": case.attrib.get("name"), "outcomes": outcomes})
        else:
            errors.append("Expected raw XML missing")
    except Exception as exc:
        errors.append(f"{type(exc).__name__}: {exc}")
    if not errors:
        receipt["status"] = "EXACT_FIVE_CASES_OBSERVED_AWAIT_INDEPENDENT_ARTIFACT_REVIEW"
    write("audit.json", receipt)
    return 1 if errors else 0


if __name__ == "__main__":
    if sys.argv[1:] == ["bind"]:
        sys.exit(bind())
    if sys.argv[1:] == ["collect"]:
        sys.exit(collect())
    raise SystemExit("Usage: collect.py bind|collect")
