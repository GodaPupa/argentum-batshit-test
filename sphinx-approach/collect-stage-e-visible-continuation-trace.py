#!/usr/bin/env python3
"""Bind and retain one excluded initialized Sphinx continuation diagnostic attempt."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/reports/sphinx-stage-e-visible-continuation-trace"
EXPECTED_BLOBS = {
    "sphinx-approach/STAGE_E_VISIBLE_CHOICE_SOURCE_SCOPE.json": "41a0a63842d8989366511fee3c1d611c1ce9c78c",
    "sphinx-approach/STAGE_E_VISIBLE_CHOICE_TRACE_PROPOSAL.json": "7b6361688d2dc2b1c34b014b79182475abc3d445",
    "sphinx-approach/STAGE_E_VISIBLE_CONTINUATION_TRACE_PROPOSAL.json": "c74bc882d38d4929044a7542bd8cf9cd7d262c6c",
    "gym/src/main/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEVisibleChoice.kt": "c769710269303809a64f01ad06aa3487d2af1182",
    "gym/src/main/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEInitializedSeat.kt": "f420e5b6e144ffc2692e9b146a44dbf461679f5d",
    "gym/src/test/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEVisibleChoiceTraceTest.kt": "0ee71be94915f92837cc786c1643eb6336022dea",
    "gym/src/test/kotlin/com/wingedsheep/gym/sphinx/SphinxStageEVisibleContinuationTraceTest.kt": "69c655a3f0c61cd48dcbcf07f39deab68200c6cc",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ActorChoiceSupport.kt": "9b37061e1515e0927ddafe0906381f7281a95ef1",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ActorPublicCards.kt": "f69cedd6c6d7aa841fb1917a842febd13adbba46",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ObservationAdapter.kt": "3e41a512f91358f763a194527258979755bab7b6",
    "gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ActorActionMenu.kt": "91d25d450c60124bc6db6e9a9fbeba0a5f15ca58",
}
DECKS = {
    "reconstructed-v01": "274521097cc731ac5f5aa9b7645a20b13d4546fd73b17dbcd14af0b0a25c4486",
    "reconstructed-hybrid": "f4cf2c64bb07847bd013e9984480f5b0fa76ac3a40dc5a47894a3609e9cb3b2e",
    "closest-no-approach-v01": "548998c77f5f688d793d36cdb9f21ae8b3b63963856875c4b84d82c271196a83",
    "serpico-terror-benchmark": "6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62",
}
CLASS = "com.wingedsheep.gym.sphinx.SphinxStageEVisibleContinuationTraceTest"
CASES = [
    f"U{number} {identity} {description}"
    for identity in ("reconstructed-v01", "reconstructed-hybrid")
    for number, description in (
        (1, "Approach May to exact-five graveyard payment and Sphinx search"),
        (2, "Snap untaps currently tapped own lands after public target bounce"),
    )
] + [
    f"U{number} {identity} {description}"
    for identity in ("closest-no-approach-v01", "serpico-terror-benchmark")
    for number, description in (
        (3, "Brainstorm selects two then reorders current top cards"),
        (4, "Preordain bottoms and reorders using only current look metadata"),
        (5, "Ponder reorder exposes later unqualified shuffle May"),
        (6, "Lórien typecycling search continues into current own hand"),
    )
]


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def sha(path):
    return hashlib.sha256((ROOT / path).read_bytes()).hexdigest()


def write(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def snapshot():
    head = git("rev-parse", "HEAD")
    tree = git("rev-parse", "HEAD^{tree}")
    if head != os.environ["SPHINX_TRACE_SHA"] or git("status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Trace must run on exact clean requested source")
    blobs = {}
    for path, expected in EXPECTED_BLOBS.items():
        actual = git("rev-parse", f"HEAD:{path}")
        if actual != expected:
            raise ValueError(f"Trace source blob changed: {path}")
        blobs[path] = actual
    decks = {}
    for identity, expected in DECKS.items():
        path = f"sphinx-approach/decks/{identity}.csv"
        actual = sha(path)
        if actual != expected:
            raise ValueError(f"Frozen deck rows changed: {path}")
        decks[path] = actual
    proposal = json.loads((ROOT / "sphinx-approach/STAGE_E_VISIBLE_CONTINUATION_TRACE_PROPOSAL.json").read_text())
    if proposal["trace_cases"] != CASES or proposal["attempt_record"]["continuation_traces_attempted"] != 0:
        raise ValueError("Reviewed trace case inventory changed")
    return {"head": head, "tree": tree, "requested_sha": os.environ["SPHINX_TRACE_SHA"],
            "blobs": blobs, "decks_sha256": decks}


def collect():
    errors = []
    before_path = OUT / "source-before.json"
    before = json.loads(before_path.read_text()) if before_path.exists() else {"error": "Missing pre-attempt bind"}
    if "error" in before:
        errors.append(before["error"])
    try:
        after = snapshot()
        write("source-after.json", after)
        if after != before:
            errors.append("Source identity changed during diagnostic")
    except Exception as failure:
        errors.append(str(failure))
    code_path = OUT / "test-exit-code.txt"
    code = code_path.read_text().strip() if code_path.exists() else "MISSING"
    if code != "0":
        errors.append(f"Test command exit code: {code}")
    xml = ROOT / "gym/build/test-results/test" / f"TEST-{CLASS}.xml"
    observed = []
    if not xml.exists():
        errors.append("Missing raw initialized continuation XML")
    else:
        try:
            suite = ET.parse(xml).getroot()
            cases = list(suite.iter("testcase"))
            observed = [case.attrib["name"] for case in cases]
            if len(cases) != len(CASES) or sorted(observed) != sorted(CASES) or len(set(observed)) != len(CASES):
                errors.append("Actual initialized trace case inventory differs")
            if int(suite.attrib.get("tests", "-1")) != len(cases):
                errors.append("Declared versus actual trace case count differs")
            if any(case.attrib.get("classname") != CLASS for case in cases):
                errors.append("Trace XML class differs")
            if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")) or any(
                int(suite.attrib.get(tag, "0")) != 0 for tag in ("failures", "errors", "skipped")):
                errors.append("Failed, errored or skipped initialized trace cases")
        except Exception as failure:
            errors.append(f"Invalid trace XML: {failure}")
    log = OUT / "tests.log"
    if not log.exists():
        errors.append("Missing raw test process log")
    else:
        log_text = log.read_text(errors="replace")
        if "> Task :gym:test" not in log_text or any(token in log_text for token in (
                ":gym:test FROM-CACHE", ":gym:test UP-TO-DATE", ":gym:test NO-SOURCE")):
            errors.append("Target gym test task was not freshly executed")
    write("audit.json", {"source": before, "test_exit_code": code,
          "xml": str(xml.relative_to(ROOT)), "xml_sha256": sha(str(xml.relative_to(ROOT))) if xml.exists() else None,
          "case_names": observed, "errors": errors,
          "disposition": "AWAIT_INDEPENDENT_DIAGNOSTIC_REVIEW" if not errors else "INCOMPLETE_DIAGNOSTIC",
          "pilot_bank_accepted": False, "whole_pilot_accepted": False,
          "official_stage_e_seeds": 0, "official_stage_e_games": 0})
    if errors:
        raise SystemExit("; ".join(errors))
    print("Twelve initialized continuation traces retained; no pilot qualification")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("bind", "collect"):
        raise SystemExit("usage: collect-stage-e-visible-continuation-trace.py bind|collect")
    if sys.argv[1] == "bind":
        try:
            xml = ROOT / "gym/build/test-results/test" / f"TEST-{CLASS}.xml"
            if xml.exists():
                raise ValueError("Preexisting continuation XML would contaminate first attempt")
            write("source-before.json", snapshot())
        except Exception as failure:
            write("bind-error.json", {"error": str(failure), "official_stage_e_games": 0})
            raise
    else:
        collect()
