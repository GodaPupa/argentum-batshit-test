#!/usr/bin/env python3
"""Observe two unchanged AI regression classes on explicitly pinned engine sources.

The temporary test-runner overlay copies the exact bytes already passed to its action-stream
digest. It changes no policy, fixture, action, seed, assertion, or digest input. This script is
diagnostic evidence collection, never gameplay admission or permission to re-bless a baseline.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET


RUNNER = "ai/src/test/kotlin/com/wingedsheep/ai/arena/TableGameRunner.kt"
RUNNER_SHA256 = "388c9a3879699de63a1bf899a689c5d46ed434429f89d6a8c8aa98bf0b856e2e"
SOURCES = {
    "9ca83110f5907e66a0c289f8205ed1e24575535c": "accepted-main",
    "b35d4778dada69476da0f67549be7e70424c18bd": "failed-shared-receiver",
}
EXPECTED_CLASSES = {
    "com.wingedsheep.ai.arena.FrozenBaselineTest": 1,
    "com.wingedsheep.ai.puzzles.PuzzleSuiteTest": 4,
}
OLD = "        fun record(entry: String) = stream?.update(entry.toByteArray(Charsets.UTF_8))"
NEW = """        fun record(entry: String) {
            stream?.update(entry.toByteArray(Charsets.UTF_8))
            if (recordActionStream) {
                System.getenv("ARGENTUM_AI_ACTION_TRACE")?.let { path ->
                    java.io.File(path).appendText(entry, Charsets.UTF_8)
                }
            }
        }"""


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n")


def problem(case, tag):
    child = case.find(tag)
    return None if child is None else "\n".join((child.attrib.get("message", ""), child.text or ""))


def instrument(source, report):
    assert source in SOURCES
    assert git("rev-parse", "HEAD") == source
    assert git("status", "--porcelain") == ""
    control = Path(__file__).resolve().parents[2]
    control_head = git("-C", str(control), "rev-parse", "HEAD")
    assert control_head == os.environ["GITHUB_SHA"]
    original = Path(RUNNER).read_bytes()
    assert sha(original) == RUNNER_SHA256
    text = original.decode()
    assert text.count(OLD) == 1
    paths = git(
        "ls-files", "ai/src/main", "ai/src/test/kotlin/com/wingedsheep/ai/arena",
        "ai/src/test/kotlin/com/wingedsheep/ai/puzzles",
    ).splitlines()
    hashes = {path: sha(Path(path).read_bytes()) for path in paths}
    report.mkdir(parents=True, exist_ok=False)
    Path(RUNNER).write_text(text.replace(OLD, NEW))
    write_json(report / "source-binding.json", {
        "schema": "shared-ai-regression-diagnostic-source-v1",
        "source_head": source,
        "source_tree": git("rev-parse", "HEAD^{tree}"),
        "source_role": SOURCES[source],
        "diagnostic_control_head": control_head,
        "diagnostic_control_tree": git("-C", str(control), "rev-parse", "HEAD^{tree}"),
        "collector_sha256": sha(Path(__file__).read_bytes()),
        "original_files_sha256": hashes,
        "only_temporary_overlay": RUNNER,
        "overlay_sha256": sha(Path(RUNNER).read_bytes()),
        "unchanged_contracts": [
            "LEGACY_V0 and PRODUCTION profiles and policy source",
            "FrozenBaselineTest deck, fixed seed, sample, GOLDEN_HASH and assertions",
            "All PuzzleCatalog positions, PUZZLE_SEED and KNOWN_FAILURES",
            "The original digest byte stream and all game actions",
        ],
        "official_gameplay_authorized": False,
    })
    (report / "runner-instrumentation.patch").write_text(git("diff", "--", RUNNER) + "\n")


def collect(source, report):
    binding = json.loads((report / "source-binding.json").read_text())
    assert source == binding["source_head"] == git("rev-parse", "HEAD")
    assert binding["source_tree"] == git("rev-parse", "HEAD^{tree}")
    assert git("diff", "--name-only").splitlines() == [RUNNER]
    for path, digest in binding["original_files_sha256"].items():
        expected = binding["overlay_sha256"] if path == RUNNER else digest
        assert sha(Path(path).read_bytes()) == expected, path
    cases_by_class = {}
    for name, count in EXPECTED_CLASSES.items():
        path = Path("ai/build/test-results/test/TEST-" + name + ".xml")
        raw = path.read_bytes()
        root = ET.fromstring(raw)
        cases = root.findall("testcase")
        assert root.attrib["name"] == name and len(cases) == count
        assert len({case.attrib["name"] for case in cases}) == count
        assert all(case.attrib["classname"] == name for case in cases)
        assert not any(case.find("skipped") is not None for case in cases)
        shutil.copyfile(path, report / path.name)
        cases_by_class[name] = {
            "actual_cases": count,
            "xml_sha256": sha(raw),
            "cases": [{
                "name": case.attrib["name"],
                "failure": problem(case, "failure"),
                "error": problem(case, "error"),
            } for case in cases],
        }
    trace = (report / "action-stream.txt").read_bytes()
    assert trace.splitlines()[-1].startswith(b"END|turns=")
    trace_hash = sha(trace)[:16]
    frozen = cases_by_class["com.wingedsheep.ai.arena.FrozenBaselineTest"]["cases"][0]
    if frozen["failure"]:
        reported = re.search(r"Actual hash: ([0-9a-f]{16})", frozen["failure"])
        assert reported and reported[1] == trace_hash
    else:
        assert frozen["error"] is None
        fixture = Path("ai/src/test/kotlin/com/wingedsheep/ai/arena/FrozenBaselineTest.kt").read_text()
        golden = re.search(r'private const val GOLDEN_HASH = "([0-9a-f]{16})"', fixture)
        assert golden and golden[1] == trace_hash
    write_json(report / "diagnostic.json", {
        "schema": "shared-ai-regression-diagnostic-v1",
        "status": "EXISTING_TEST_RESULTS_RETAINED_FOR_CAUSAL_REVIEW",
        "source_head": source,
        "source_tree": binding["source_tree"],
        "diagnostic_control_head": binding["diagnostic_control_head"],
        "actual_cases": 5,
        "classes": cases_by_class,
        "action_stream_sha256": sha(trace),
        "action_stream_hash_matches_original_digest": True,
        "fixture_replays": 1,
        "frozen_deck_seed_profile_golden_changed": False,
        "puzzle_positions_or_known_failures_changed": False,
        "combined_runtime_accepted": False,
        "official_games": 0,
        "official_outcomes_exposed": 0,
        "gameplay_authorized": False,
    })


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("instrument", "collect"))
    parser.add_argument("--source", required=True)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    {"instrument": instrument, "collect": collect}[args.mode](args.source, args.report)
