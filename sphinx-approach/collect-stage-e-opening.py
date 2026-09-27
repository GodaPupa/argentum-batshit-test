#!/usr/bin/env python3
"""Audit the excluded, source-bound Sphinx opening receiving bank; no gameplay."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/reports/sphinx-stage-e-opening"
PROPOSAL = "sphinx-approach/STAGE_E_OPENING_ACTOR_FIXTURE_PROPOSAL.json"
EXPECTED_BLOBS = {
    PROPOSAL: "1f699dee4f572928e9c2957f4dc808353ed4d956",
    "sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json": "160eda5fe3adbf35dfb770ef1fc305ee25ab48a9",
    "gym/src/main/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEOpeningActor.kt": "8be1831af807c77e0e45807eb248c1b99b172d28",
    "gym/src/main/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEOpeningRouter.kt": "753595605db51df86ebf18c605b6ebab19b451ac",
    "gym/src/main/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEInitializedSeat.kt": "67d65a5d3d7ca7bea2f585b372481358336e99db",
    "gym/src/test/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEOpeningActorTest.kt": "a5c93ad4bf19ea310ea53d5d42e618eb7ea54125",
    "gym/src/test/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEInitializedSeatTest.kt": "0a46c7e1886ab8fa1d09fc97e23c3165e2648e27",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ActorInputEligibility.kt": "7c0aa20561c5ac90c244106fad790e6ae22cc64e",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ActorActionMenu.kt": "91d25d450c60124bc6db6e9a9fbeba0a5f15ca58",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ObservationAdapter.kt": "3e41a512f91358f763a194527258979755bab7b6",
}
DECK_SHA256 = {
    "reconstructed-v01": "274521097cc731ac5f5aa9b7645a20b13d4546fd73b17dbcd14af0b0a25c4486",
    "reconstructed-hybrid": "f4cf2c64bb07847bd013e9984480f5b0fa76ac3a40dc5a47894a3609e9cb3b2e",
    "closest-no-approach-v01": "548998c77f5f688d793d36cdb9f21ae8b3b63963856875c4b84d82c271196a83",
    "serpico-terror-benchmark": "6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62",
}
OPENING = "com.wingedsheep.gym.sphinx.SphinxStageEOpeningActorTest"
INITIALIZED = "com.wingedsheep.gym.sphinx.SphinxStageEInitializedSeatTest"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def sha256(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def snapshot():
    head = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    if head != os.environ["GITHUB_SHA"] or git("status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Requested qualification source differs from clean checked-out HEAD")
    blobs = {}
    for path, expected in EXPECTED_BLOBS.items():
        actual = git("rev-parse", f"HEAD:{path}")
        if actual != expected:
            raise ValueError(f"Reviewed source blob changed: {path}")
        blobs[path] = actual
    decks = {}
    for identity, expected in DECK_SHA256.items():
        path = f"sphinx-approach/decks/{identity}.csv"
        actual = sha256(path)
        if actual != expected:
            raise ValueError(f"Frozen deck rows changed: {path}")
        decks[path] = actual
    proposal = json.loads((ROOT / PROPOSAL).read_text())
    if (proposal["equal_budget_for_review"]["exact_case_ids_per_identity"] !=
        [f"O{i}" for i in range(1, 9)] or
        proposal["equal_budget_for_review"]["proposed_distinct_cases"] != 32 or
        proposal["attempt_record"]["fixture_attempts"] != 0 or
        proposal["source_head"] != "b70072b1e6dc8aa5494617c5b62788a7758e6c46" or
        proposal["source_tree"] != "db3e94f707627fc90e4eae0ff99260f500597f85"):
        raise ValueError("Prospective O1-O8 x four proposal changed")
    return {"head": head, "tree": tree, "requested_sha": os.environ["GITHUB_SHA"],
            "blobs": blobs, "decks_sha256": decks}


def write(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def collect():
    errors = []
    before_file = OUT / "source-before.json"
    if before_file.exists():
        before = json.loads(before_file.read_text())
    else:
        before = {"error": "Missing bound pre-attempt source"}
        errors.append("Missing bound pre-attempt source")
    try:
        after = snapshot()
        write("source-after.json", after)
        if after != before:
            errors.append("Source changed during fixture execution")
    except Exception as failure:
        errors.append(str(failure))
    exit_file = OUT / "test-exit-code.txt"
    exit_code = exit_file.read_text().strip() if exit_file.exists() else "MISSING"
    if exit_code != "0":
        errors.append(f"Test command exit code: {exit_code}")
    all_cases = set()
    banks = []
    for classname, count in ((OPENING, 32), (INITIALIZED, 16)):
        path = ROOT / "gym/build/test-results/test" / f"TEST-{classname}.xml"
        if not path.exists():
            errors.append(f"Missing raw XML: {classname}")
            continue
        try:
            suite = ET.parse(path).getroot()
            cases = list(suite.iter("testcase"))
            names = [case.attrib["name"] for case in cases]
            rejected = [case.attrib.get("name") for case in cases if any(
                case.find(tag) is not None for tag in ("failure", "error", "skipped"))]
            if len(cases) != count or len(set(names)) != count:
                errors.append(f"Wrong actual unique case count: {classname}")
            if int(suite.attrib.get("tests", "-1")) != len(cases):
                errors.append(f"Declared versus actual count differs: {classname}")
            if any(case.attrib.get("classname") != classname for case in cases):
                errors.append(f"Wrong actual class: {classname}")
            if rejected or any(int(suite.attrib.get(k, "0")) != 0 for k in ("failures", "errors", "skipped")):
                errors.append(f"Failed, errored or skipped cases: {classname}")
            if classname == OPENING:
                for identity in DECK_SHA256:
                    for number in range(1, 9):
                        if sum(name.startswith(f"O{number} {identity} ") for name in names) != 1:
                            errors.append(f"Missing or repeated O{number}/{identity}")
            else:
                for identity in DECK_SHA256:
                    for seat in range(2):
                        for number in range(1, 3):
                            if sum(name.startswith(f"IB{number} {identity} seat{seat} ") for name in names) != 1:
                                errors.append(f"Missing or repeated IB{number}/{identity}/seat{seat}")
            keys = {(classname, name) for name in names}
            if all_cases & keys:
                errors.append(f"Duplicate case identities: {classname}")
            all_cases |= keys
            banks.append({"class": classname, "actual": len(cases),
                          "xml": str(path.relative_to(ROOT)), "xml_sha256": sha256(str(path.relative_to(ROOT))),
                          "case_names": names, "failed_errored_or_skipped": rejected})
        except Exception as failure:
            errors.append(f"Invalid raw XML {classname}: {failure}")
    if len(all_cases) != 48:
        errors.append(f"Expected 48 actual distinct opening plus preserved seat cases; observed {len(all_cases)}")
    log = OUT / "tests.log"
    if not log.exists():
        errors.append("Missing test process log")
    else:
        contents = log.read_text(errors="replace")
        if any(token in contents for token in (
            ":gym:test FROM-CACHE", ":gym:test UP-TO-DATE", ":gym:test NO-SOURCE")):
            errors.append("Target gym test task was not freshly executed")
    write("audit.json", {"source": before, "test_exit_code": exit_code, "banks": banks,
          "actual_distinct_cases": len(all_cases), "errors": errors,
          "disposition": "AWAIT_NON_AUTHOR_ARTIFACT_REVIEW" if not errors else "INCOMPLETE_QUALIFICATION",
          "opening_accepted": False, "whole_pilot_accepted": False,
          "official_stage_e_seeds": 0, "official_stage_e_games": 0})
    if errors:
        raise SystemExit("; ".join(errors))
    print("32 new plus 16 preserved opening checks audited; independent artifact review required")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("bind", "collect"):
        raise SystemExit("usage: collect-stage-e-opening.py bind|collect")
    if sys.argv[1] == "bind":
        try:
            write("source-before.json", snapshot())
        except Exception as failure:
            write("bind-error.json", {"error": str(failure), "official_stage_e_games": 0})
            raise
    else:
        collect()
