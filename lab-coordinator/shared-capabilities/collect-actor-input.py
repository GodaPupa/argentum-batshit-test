#!/usr/bin/env python3
"""Source-bound collection for unchanged shared actor fixtures; never an experimental runner."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / "build/reports/shared-actor-input"
EXTRACTION = "lab-coordinator/shared-capabilities/actor-input-extraction.json"
WORKFLOW = ".github/workflows/shared-actor-input-qualification.yml"
COLLECTOR = "lab-coordinator/shared-capabilities/collect-actor-input.py"
EXPECTED = {
    "ActorObservationBoundaryTest": 28,
    "ActorStackSourceObservationTest": 12,
    "ActorTriggerOrderObservationTest": 6,
    "ActorPriorityObservationTest": 4,
}


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, indent=2) + "\n")


def file_digests(paths: list[str]) -> dict[str, str]:
    return {path: digest((ROOT / path).read_bytes()) for path in paths}


def bind() -> None:
    REPORT.mkdir(parents=True, exist_ok=True)
    extraction = json.loads((ROOT / EXTRACTION).read_text())
    paths = sorted({EXTRACTION, WORKFLOW, COLLECTOR, "justfile", "scripts/test-class", "scripts/gradle-locked"}
        | {row["receiving_path"] for row in extraction["source_records"]}
        | set(extraction["local_integration_files_sha256"]))
    binding = {
        "schema": "shared-actor-input-binding-v1",
        "requested_head": os.environ["GITHUB_SHA"],
        "actual_head": git("rev-parse", "HEAD"),
        "tree": git("rev-parse", "HEAD^{tree}"),
        "checkout_status": git("status", "--porcelain"),
        "event_name": os.environ.get("GITHUB_EVENT_NAME"),
        "run_id": os.environ["GITHUB_RUN_ID"],
        "run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "files_sha256": file_digests(paths),
    }
    write_json(REPORT / "source-binding.json", binding)
    assert binding["actual_head"] == binding["requested_head"], binding
    assert not binding["checkout_status"], binding
    assert extraction["required_cases"] == sum(EXPECTED.values()) == 50
    for row in extraction["source_records"]:
        assert binding["files_sha256"][row["receiving_path"]] == row["receiving_sha256"], row
    for path, sha in extraction["local_integration_files_sha256"].items():
        assert binding["files_sha256"][path] == sha, path
    assert extraction["actor_component_accepted"] is False
    assert extraction["pilot_qualified"] is False and extraction["execution_authorized"] is False
    assert extraction["gameplay_games"] == 0 and not extraction["project_gameplay_evidence_transferred"]
    write_json(REPORT / "extraction.json", extraction)


def collect() -> None:
    binding = json.loads((REPORT / "source-binding.json").read_text())
    extraction = json.loads((ROOT / EXTRACTION).read_text())
    banks = {}
    xml_files = {}
    for class_name, expected in EXPECTED.items():
        paths = list((ROOT / "gym/build/test-results/test").glob(f"TEST-*.{class_name}.xml"))
        assert len(paths) == 1, (class_name, paths)
        suite = ET.parse(paths[0]).getroot()
        cases = suite.findall("testcase")
        assert len(cases) == int(suite.attrib["tests"]) == expected, (class_name, suite.attrib)
        assert all(int(suite.attrib.get(key, 0)) == 0 for key in ("failures", "errors", "skipped")), suite.attrib
        assert all(not any(case.find(tag) is not None for tag in ("failure", "error", "skipped")) for case in cases)
        names = [case.attrib["name"] for case in cases]
        assert len(set(names)) == len(names), (class_name, names)
        assert all(case.attrib["classname"] == f"com.wingedsheep.gym.actorinput.{class_name}" for case in cases)
        banks[class_name] = names
        xml_files[str(paths[0].relative_to(ROOT))] = digest(paths[0].read_bytes())
    # Receiving files are pinned to the exact original fixture bodies in the extraction manifest.
    assert sum(map(len, banks.values())) == 50
    assert file_digests(list(binding["files_sha256"])) == binding["files_sha256"]
    assert git("rev-parse", "HEAD") == binding["actual_head"]
    assert git("rev-parse", "HEAD^{tree}") == binding["tree"]
    assert not git("status", "--porcelain")
    assert extraction["required_cases"] == 50
    write_json(REPORT / "manifest.json", {
        "schema": "shared-actor-input-qualification-v1",
        "status": "TARGETED_ACTOR_PROJECTION_CHECKS_PASSED_RECEIVING_ACCEPTANCE_PENDING",
        "source_head": binding["actual_head"],
        "source_tree": binding["tree"],
        "run_id": binding["run_id"], "run_attempt": binding["run_attempt"],
        "actual_cases": 50, "failures": 0, "errors": 0, "skipped": 0,
        "banks": banks, "xml_sha256": xml_files,
        "files_sha256": binding["files_sha256"],
        "actor_component_accepted": False, "complete_pilot_qualified": False,
        "optional_duel_choice_helpers_qualified": False,
        "receiving_full_ci_accepted": False, "independent_artifact_review_accepted": False,
        "project_gameplay_evidence_transferred": False, "execution_authorized": False,
        "randomized_development_games": 0, "official_seeds": 0,
        "official_games": 0, "official_outcomes_exposed": 0,
    })


if __name__ == "__main__":
    assert len(sys.argv) == 2 and sys.argv[1] in {"bind", "collect"}
    {"bind": bind, "collect": collect}[sys.argv[1]]()
