#!/usr/bin/env python3
"""Bind and retain the fixed Monster actor diagnostic bank; never authorize a pilot or game."""
from __future__ import annotations

import fnmatch
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[4]
PREFIX = "docs/experiments/pest-control/monster-actor-component"
BANK = f"{PREFIX}/receiving-bank.json"
FREEZE = f"{PREFIX}/receiving-freeze.json"
BANK_SHA256 = "b28545f7c40f06f5bb904ed797a05cfde0279e2af0ba84d752066c1f282987c4"
REPORT = ROOT / "build/reports/pest-monster-actor-receiving"
CONTROLS = [BANK, FREEZE, f"{PREFIX}/source-candidate.json",
            f"{PREFIX}/collect-receiving.py", ".github/workflows/pest-monster-actor-receiving.yml",
            f"{PREFIX}/evidence/prospective-source-review/source-candidate.json",
            f"{PREFIX}/evidence/prospective-source-review/receiving-bank.json",
            f"{PREFIX}/evidence/prospective-source-review/PestMonsterTronActorDecisions.kt.txt"]


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def write(name: str, value: object) -> None:
    REPORT.mkdir(parents=True, exist_ok=True)
    (REPORT / name).write_text(json.dumps(value, indent=2) + "\n")


def bank() -> dict:
    data = (ROOT / BANK).read_bytes()
    assert digest(data) == BANK_SHA256, "Receiving bank changed"
    value = json.loads(data)
    assert value["required_actual_cases"] == 72
    assert sum(item["expected_cases"] for item in value["banks"]) == 72
    assert len(value["banks"]) == 7 and len(value["new_cases"]) == 10
    assert value["gameplay_authorized"] is False
    return value


def source_snapshot(value: dict) -> dict:
    head = git("rev-parse", "HEAD")
    assert head == os.environ["GITHUB_SHA"], "Requested source differs from actual HEAD"
    assert not git("status", "--porcelain"), "Source checkout is dirty"
    freeze = json.loads((ROOT / FREEZE).read_text())
    assert freeze["reviewed_bank_sha256"] == BANK_SHA256
    assert freeze["disposition"] == "ACCEPT_FOR_72_CASE_RECEIVING_DIAGNOSTIC_ONLY"
    assert freeze["independent_reviewer"] and freeze["independent_reviewer"] != "/root/pest"
    assert freeze["gameplay_authorized"] is False
    assert freeze["actor_choice_equivalence_accepted"] is False
    assert freeze["gate_review"]["ready_for_execution"] is True, "Independent gate review remains pending"
    assert freeze["gate_review"]["reviewer"] and freeze["gate_review"]["reviewer"] != "/root/pest"
    hashes = {}
    for path, expected in value["source_sha256"].items():
        data = (ROOT / path).read_bytes()
        assert digest(data) == expected, f"Frozen source differs: {path}"
        hashes[path] = expected
    for path in CONTROLS:
        data = (ROOT / path).read_bytes()
        committed = subprocess.check_output(["git", "show", f"{head}:{path}"], cwd=ROOT)
        assert data == committed, f"Control file differs from exact Git source: {path}"
        hashes[path] = digest(data)
    assert hashes[f"{PREFIX}/source-candidate.json"] == value["source_candidate_manifest_sha256"]
    # scripts/test-class intentionally selects exactly these two modules with this first glob.
    matches = [path for path in git("ls-files").splitlines()
               if path.endswith(".kt") and fnmatch.fnmatch(Path(path).stem, "PestMonsterTron*Test")]
    expected = {item["source_path"] for item in value["banks"]
                if fnmatch.fnmatch(Path(item["source_path"]).stem, "PestMonsterTron*Test")}
    assert set(matches) == expected and len(matches) == 2, "First test selector changed"
    return {"head": head, "tree": git("rev-parse", "HEAD^{tree}"),
            "requested_source": os.environ["GITHUB_SHA"], "bank_sha256": BANK_SHA256,
            "source_sha256": hashes}


def bind() -> None:
    value = bank()
    snapshot = source_snapshot(value)
    old_xml = [str(p.relative_to(ROOT)) for module in ("ai", "gym")
               for p in (ROOT / module / "build/test-results/test").glob("TEST-*.xml")]
    assert not old_xml, f"Pre-existing test evidence is not admissible: {old_xml}"
    write("source-before.json", snapshot)


def collect() -> int:
    errors = []
    suites = []
    actual_names = set()
    diagnostic = []
    result = {"schema": "pest-monster-actor-receiving-actual-v1", "banks": suites,
              "errors": errors, "actual_distinct_cases": 0,
              "actor_choice_equivalence_accepted": False, "whole_pilot_accepted": False,
              "combined_runtime_accepted": False, "c2_accepted": False, "a2_accepted": False,
              "official_seeds_generated": 0, "official_games": 0,
              "official_outcomes_exposed": 0, "historical_games_quarantined": 81,
              "gameplay_authorized": False}
    try:
        value = bank()
        before = json.loads((REPORT / "source-before.json").read_text())
        after = source_snapshot(value)
        write("source-after.json", after)
        result["source"] = before
        assert before == after, "Source binding changed during execution"
        rc = (REPORT / "test-exit-code.txt").read_text().strip()
        result["test_exit_code"] = rc
        if rc != "0":
            errors.append(f"Actual test command exit code: {rc}")
        actual_xml = {p for module in ("ai", "gym")
                      for p in (ROOT / module / "build/test-results/test").glob("TEST-*.xml")}
        expected_xml = {ROOT / item["module"] / "build/test-results/test" / f'TEST-{item["class"]}.xml'
                        for item in value["banks"]}
        if actual_xml - expected_xml:
            errors.append("Unexpected XML banks: " + repr(sorted(str(p.relative_to(ROOT)) for p in actual_xml - expected_xml)))
        for item in value["banks"]:
            path = ROOT / item["module"] / "build/test-results/test" / f'TEST-{item["class"]}.xml'
            if not path.exists():
                errors.append(f'Missing actual XML: {item["class"]}')
                continue
            suite = ET.parse(path).getroot()
            cases = suite.findall("testcase")
            row = {"class": item["class"], "xml_path": str(path.relative_to(ROOT)),
                   "xml_sha256": digest(path.read_bytes()), "cases": []}
            suites.append(row)
            if suite.attrib.get("name") != item["class"] or int(suite.attrib.get("tests", -1)) != len(cases) or len(cases) != item["expected_cases"]:
                errors.append(f'Actual class/count mismatch: {item["class"]}')
            for key in ("failures", "errors", "skipped"):
                if int(suite.attrib.get(key, 0)) != 0:
                    errors.append(f'{item["class"]}: {key}={suite.attrib[key]}')
            for case in cases:
                name = case.attrib.get("name", "")
                identity = (item["class"], name)
                if case.attrib.get("classname") != item["class"] or not name or identity in actual_names:
                    errors.append(f"Invalid or duplicate actual case: {identity}")
                actual_names.add(identity)
                outcomes = [{"kind": node.tag, "attributes": node.attrib, "text": node.text or ""}
                            for node in case if node.tag in ("failure", "error", "skipped")]
                if outcomes:
                    errors.append(f"Actual unsuccessful case retained: {identity}")
                row["cases"].append({"name": name, "outcomes": outcomes})
            if item["class"].endswith(".PestMonsterTronActorDecisionsTest"):
                if {case.attrib.get("name") for case in cases} != set(value["new_cases"]):
                    errors.append("The fixed MC01-MC10 identities changed")
                stdout = "\n".join(node.text or "" for node in suite.findall(".//system-out"))
                diagnostic = re.findall(r"PEST_MONSTER_ORDER_DIAGNOSTIC raw=(.*?) canonical=(.*?) "
                                        r"disposition=REQUIRES_PROSPECTIVE_PROTOCOL_DISPOSITION no_response_submitted=true", stdout)
        result["actual_distinct_cases"] = len(actual_names)
        result["order_diagnostic"] = [{"raw_name": raw, "canonical_name": canonical}
                                      for raw, canonical in diagnostic]
        if len(actual_names) != 72:
            errors.append(f"Expected 72 actual distinct cases, observed {len(actual_names)}")
        if len(diagnostic) != 1 or diagnostic[0][0] == diagnostic[0][1]:
            errors.append("Required distinguishing unresolved-order diagnostic missing or ambiguous")
    except Exception as exc:
        errors.append(f"{type(exc).__name__}: {exc}")
    result["disposition"] = ("INCOMPLETE_RECEIVING_DIAGNOSTIC" if errors else
        "FIXED_72_CASE_DIAGNOSTIC_COMPLETE_AWAIT_NON_AUTHOR_REVIEW_POLICY_ORDER_UNRESOLVED")
    write("audit.json", result)
    return 1 if errors else 0


if __name__ == "__main__":
    if sys.argv[1:] == ["bind"]:
        bind()
    elif sys.argv[1:] == ["collect"]:
        sys.exit(collect())
    else:
        raise SystemExit("Usage: collect-receiving.py bind|collect")
