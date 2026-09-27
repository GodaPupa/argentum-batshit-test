"""Prospective exact Sphinx receiving bindings; never a gameplay authority."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import subprocess

BINDING = "sphinx-approach/STAGE_E_CANONICAL_RECEIVING_BINDING.json"
BINDING_SHA256 = "1316e5dc9ad26db03e655d4fd5aa96427309eb04700d929e6433e64786f50682"
QUALIFIED_SOURCE = "a0c5b995c2829c0b4069562b1f1eede8a820dd97"
QUALIFIED_TREE = "1ee5590bacf2f6507fff6487be9d3e20df8b5e40"


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


def _fingerprint(value: object) -> str:
    raw = json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(raw).hexdigest()


def _bind(group: str, original_pins: dict[str, str], root: Path) -> tuple[dict[str, str], dict]:
    """Verify immutable donor scope and apply only explicitly reviewed receiving pins."""
    root = root.resolve()
    if _sha(root / BINDING) != BINDING_SHA256:
        raise ValueError("Receiving binding descriptor changed")
    binding = json.loads((root / BINDING).read_bytes())
    if binding["schema"] != "sphinx-canonical-receiving-binding/v1":
        raise ValueError("Unexpected receiving schema")
    if binding["ready_for_qualification"] is not True:
        raise ValueError("Prospective receiving binding awaits nonauthor source review")
    if binding["qualified_source"] != QUALIFIED_SOURCE or binding["qualified_tree"] != QUALIFIED_TREE:
        raise ValueError("Qualified runtime identity changed")
    if binding["official_games"] != 0 or binding["gameplay_authorized"] is not False:
        raise ValueError("Receiving qualification cannot grant gameplay authority")
    review = binding["independent_source_review"]
    if not review or _sha(root / review["path"]) != review["sha256"]:
        raise ValueError("Independent receiving source review missing or changed")
    head = _git(root, "rev-parse", "HEAD")
    tree = _git(root, "rev-parse", "HEAD^{tree}")
    if _git(root, "rev-parse", QUALIFIED_SOURCE + "^{tree}") != QUALIFIED_TREE:
        raise ValueError("Qualified ancestor tree changed")
    subprocess.run(["git", "merge-base", "--is-ancestor", QUALIFIED_SOURCE, head], cwd=root, check=True)
    changed = set(_git(root, "diff", "--name-only", QUALIFIED_SOURCE, head).splitlines())
    if not changed <= set(binding["allowed_qualification_paths"]):
        raise ValueError("Runtime, test, protocol or unrelated source changed: " + repr(sorted(changed)))
    if _git(root, "status", "--porcelain", "--untracked-files=all"):
        raise ValueError("Receiving checkout is not clean")
    for path, digest in binding["preserved_authority_sha256"].items():
        if _sha(root / path) != digest:
            raise ValueError("Original authority bytes changed: " + path)
    rule = binding["groups"][group]
    if _fingerprint(original_pins) != rule["original_pins_sha256"]:
        raise ValueError("Original source scope changed: " + group)
    effective = dict(original_pins)
    for item in rule["explicit_overrides"]:
        path = item["path"]
        if path not in effective or effective[path] != item["original_sha256"]:
            raise ValueError("Override does not match an original binding: " + path)
        effective[path] = item["receiving_sha256"]
    for path, expected in effective.items():
        if _sha(root / path) != expected:
            raise ValueError("Exact receiving source differs: " + path)
    controls = {path: _sha(root / path) for path in binding["qualification_control_paths"]}
    controls[review["path"]] = _sha(root / review["path"])
    for path, digest in controls.items():
        if path in effective and effective[path] != digest:
            raise ValueError("Qualification control conflicts with effective scope: " + path)
        effective[path] = digest
    proof = {
        "qualified_source": QUALIFIED_SOURCE,
        "qualified_tree": QUALIFIED_TREE,
        "head": head,
        "tree": tree,
        "group": group,
        "original_pins_sha256": rule["original_pins_sha256"],
        "original_pin_count": len(original_pins),
        "explicit_overrides": rule["explicit_overrides"],
        "binding_sha256": BINDING_SHA256,
        "qualification_only_changed_paths": sorted(changed),
        "qualification_controls_sha256": controls,
        "official_games": 0,
        "gameplay_authorized": False,
    }
    return effective, proof


REPORTS = {
    "priority_220": "engine-priority-after-resolution",
    "token_20": "attacking-token-defenders",
    "shared_actor_50": "shared-actor-input",
    "pest_postboard_b": "pest-postboard-support-batch-b",
    "sphinx_actor_136": "sphinx-stage-e-actor",
    "sphinx_seat_16": "sphinx-initialized-seat",
}
ORIGINALS = {
    "priority_220": ["lab-coordinator/shared-capabilities/priority-receiving-sources.json"],
    "token_20": ["lab-coordinator/shared-capabilities/attacking-token-defender-receiving.json"],
    "shared_actor_50": ["lab-coordinator/shared-capabilities/actor-input-extraction.json"],
    "pest_postboard_b": ["docs/experiments/pest-control/tier-one-postboard-support-batch-b-shared-receiving-sources.json"],
    "sphinx_actor_136": ["sphinx-approach/STAGE_E_ACTOR_FIXTURE_BUDGET.json", "sphinx-approach/STAGE_E_ACTOR_RECEIVING_SCOPE.json"],
    "sphinx_seat_16": ["sphinx-approach/STAGE_E_INITIALIZED_SEAT_FIXTURE_BUDGET.json", "sphinx-approach/STAGE_E_INITIALIZED_SEAT_RECEIVING.json"],
}


def bind(group: str, original_pins: dict[str, str], root: Path) -> tuple[dict[str, str], dict]:
    """Preserve every attempted binding, including rejected source, before asserting it."""
    root = root.resolve()
    parent = root / "build/reports" / REPORTS[group] / "receiving-binding-attempts"
    parent.mkdir(parents=True, exist_ok=True)
    index = 1
    while (parent / f"attempt-{index:03d}").exists():
        index += 1
    out = parent / f"attempt-{index:03d}"
    out.mkdir()
    attempt = {
        "group": group,
        "status": "BINDING_ATTEMPT_NOT_ACCEPTANCE",
        "run_id": os.environ.get("GITHUB_RUN_ID"),
        "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "github_sha": os.environ.get("GITHUB_SHA"),
        "requested_source": os.environ.get("EXPECTED_SOURCE") or os.environ.get("EXPECTED_HEAD") or os.environ.get("GITHUB_SHA"),
        "original_expected_pins": original_pins,
        "observed_source_sha256": {},
        "preserved_files_sha256": {},
        "official_games": 0,
        "gameplay_authorized": False,
    }
    record = out / "attempt.json"
    try:
        for path in [BINDING, "sphinx-approach/canonical_receiving_binding.py", *ORIGINALS[group]]:
            source = root / path
            if source.is_file():
                raw = source.read_bytes()
                dest = out / "source" / path
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(raw)
                attempt["preserved_files_sha256"][path] = hashlib.sha256(raw).hexdigest()
            else:
                attempt["preserved_files_sha256"][path] = None
        attempt["head"] = _git(root, "rev-parse", "HEAD")
        attempt["tree"] = _git(root, "rev-parse", "HEAD^{tree}")
        attempt["checkout_status"] = _git(root, "status", "--porcelain", "--untracked-files=all")
        attempt["observed_source_sha256"] = {
            path: _sha(root / path) if (root / path).is_file() else None for path in original_pins
        }
        record.write_text(json.dumps(attempt, indent=2) + "\n")
        effective, proof = _bind(group, original_pins, root)
        attempt["status"] = "EXACT_SOURCE_BOUND_QUALIFICATION_RESULTS_PENDING"
        attempt["binding"] = proof
        record.write_text(json.dumps(attempt, indent=2) + "\n")
        return effective, proof
    except Exception as exc:
        attempt["status"] = "REJECTED_BINDING_NO_QUALIFICATION"
        attempt["exception"] = {"type": type(exc).__name__, "message": str(exc)}
        record.write_text(json.dumps(attempt, indent=2) + "\n")
        raise
