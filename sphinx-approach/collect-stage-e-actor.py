#!/usr/bin/env python3
"""Bind one exact receiving source and audit actual XML for the prospective 136 checks."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/reports/sphinx-stage-e-actor"
BUDGET = "sphinx-approach/STAGE_E_ACTOR_FIXTURE_BUDGET.json"
SCOPE = "sphinx-approach/STAGE_E_ACTOR_RECEIVING_SCOPE.json"
BUDGET_SHA256 = "6f162994a176e83431939e509d7611132192fbc969891fc04ff1372eee831cc7"
SCOPE_SHA256 = "1602ca99f7be5f80e486e7353994be8e4aba95049b17ff55261beb18372a15c8"
BANKS = {
    "com.wingedsheep.gym.actorinput.ActorObservationBoundaryTest": 28,
    "com.wingedsheep.gym.actorinput.ActorStackSourceObservationTest": 12,
    "com.wingedsheep.gym.actorinput.ActorTriggerOrderObservationTest": 6,
    "com.wingedsheep.gym.actorinput.ActorPriorityObservationTest": 4,
    "com.wingedsheep.gym.sphinx.SphinxStageEPilotComponentTest": 56,
    "com.wingedsheep.gym.actorinput.ActorSpellPaymentProjectionTest": 10,
    "com.wingedsheep.gym.sphinx.SphinxStageEActorAdapterTest": 20,
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def snapshot():
    requested = os.environ["GITHUB_SHA"]
    head, tree = git("rev-parse", "HEAD"), git("rev-parse", "HEAD^{tree}")
    if head != requested or git("status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Requested source differs from the clean checked-out source")
    if sha(ROOT / BUDGET) != BUDGET_SHA256 or sha(ROOT / SCOPE) != SCOPE_SHA256:
        raise ValueError("Prospective bank or reviewed source inventory changed")
    budget = json.loads((ROOT / BUDGET).read_text())
    scope = json.loads((ROOT / SCOPE).read_text())
    pins = {}
    for bank in (budget["protected_source_sha256"], budget["fixture_source_sha256"],
                 scope["receiving_source_sha256"]):
        for path, expected in bank.items():
            if path in pins and pins[path] != expected:
                raise ValueError(f"Conflicting source pin: {path}")
            actual = sha(ROOT / path)
            if actual != expected:
                raise ValueError(f"Reviewed source changed: {path}")
            pins[path] = actual
    if budget["required_actual_total"] != sum(BANKS.values()) or sum(BANKS.values()) != 136:
        raise ValueError("Receiving bank geometry changed")
    return {"head": head, "tree": tree, "requested_source": requested,
            "budget_sha256": BUDGET_SHA256, "scope_sha256": SCOPE_SHA256,
            "source_sha256": pins}


def write(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, indent=2) + "\n")


def collect():
    errors, banks = [], []
    before = json.loads((OUT / "source-before.json").read_text())
    try:
        after = snapshot()
        write("source-after.json", after)
        if after != before:
            errors.append("Source changed during execution")
    except Exception as error:
        errors.append(str(error))
    exit_file = OUT / "test-exit-code.txt"
    exit_code = exit_file.read_text().strip() if exit_file.exists() else "MISSING"
    if exit_code != "0":
        errors.append(f"Actual test command exit code was {exit_code}")
    all_cases = set()
    for classname, expected in BANKS.items():
        matches = list((ROOT / "gym/build/test-results/test").glob(f"TEST-{classname}.xml"))
        if len(matches) != 1:
            errors.append(f"Missing or repeated XML bank: {classname}")
            continue
        path = matches[0]
        try:
            suite = ET.parse(path).getroot()
            cases = list(suite.iter("testcase"))
            names = [case.attrib["name"] for case in cases]
            rejected = [case.attrib.get("name") for case in cases
                        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped"))]
            if len(cases) != expected or len(set(names)) != expected:
                errors.append(f"Wrong actual unique case count: {classname}")
            if int(suite.attrib.get("tests", "-1")) != len(cases):
                errors.append(f"Declared versus actual XML count differs: {classname}")
            if any(case.attrib.get("classname") != classname for case in cases):
                errors.append(f"Wrong actual case class: {classname}")
            if rejected or any(int(suite.attrib.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
                errors.append(f"Failed, errored or skipped cases: {classname}")
            keys = {(classname, name) for name in names}
            if all_cases & keys:
                errors.append(f"Repeated case identity: {classname}")
            all_cases |= keys
            if classname.endswith("SphinxStageEActorAdapterTest"):
                identities = [row["id"] for row in json.loads((ROOT / BUDGET).read_text())["frozen_input_identities"]]
                for identity in identities:
                    for number in range(1, 6):
                        if sum(name.startswith(f"AR{number} {identity} ") for name in names) != 1:
                            errors.append(f"Unequal/missing adapter cell AR{number}/{identity}")
            if classname.endswith("ActorSpellPaymentProjectionTest"):
                for number in range(1, 11):
                    if sum(name.startswith(f"SP{number:02d} ") for name in names) != 1:
                        errors.append(f"Missing shared projection check SP{number:02d}")
            banks.append({"class": classname, "expected": expected, "actual": len(cases),
                          "xml": str(path.relative_to(ROOT)), "xml_sha256": sha(path),
                          "case_names": names, "failed_errored_or_skipped": rejected})
        except Exception as error:
            errors.append(f"Invalid XML {classname}: {error}")
    if len(all_cases) != 136:
        errors.append(f"Expected 136 actual distinct checks; observed {len(all_cases)}")
    write("audit.json", {"source": before, "test_exit_code": exit_code, "banks": banks,
          "actual_distinct_cases": len(all_cases), "errors": errors,
          "disposition": "AWAIT_NON_AUTHOR_ARTIFACT_REVIEW" if not errors else "INCOMPLETE_QUALIFICATION",
          "component_accepted": False, "full_receiving_accepted": False,
          "whole_pilot_accepted": False, "official_games": 0, "gameplay_authorized": False})
    if errors:
        raise SystemExit("; ".join(errors))
    print("136 actual checks audited; non-author artifact review and remaining admission required")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("bind", "collect"):
        raise SystemExit("usage: collect-stage-e-actor.py bind|collect")
    if sys.argv[1] == "bind":
        write("source-before.json", snapshot())
    else:
        collect()
