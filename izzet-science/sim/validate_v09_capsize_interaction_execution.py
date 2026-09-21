#!/usr/bin/env python3
"""Seed-free validation of the paired interaction runner and artifact contract."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path

from audit_v09_capsize_interaction_artifact import (
    ARTIFACT_SCHEMA,
    audit_files,
    digest,
)
from paired_capsize_interaction_contract import (
    CONTROL_SHA256,
    SCHEMA,
    aggregate_paired_interaction_games,
)
from validate_v09_capsize_interaction_contract import row


ROOT=Path(__file__).resolve().parents[2]
HARNESS_PATH=ROOT/"izzet-science/sim/mana_harness.py"
RUNNER=ROOT/"izzet-science/sim/run_v09_capsize_interaction_paired_pilot.py"
RETIRED_RUNNERS=(
    ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot.py",
    ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot_v2.py",
    ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot_v3.py",
)
CONTROL=ROOT/"izzet-science/v0.7-control.md"
RUNNER_SHA256="0c8b902dd014c6b7c92392dc59c19b64932fa49a276938a90cea0393fcd793aa"
SOURCE="a"*40
WORKFLOW="b"*40
MASTER=1
SAMPLES=2


def load_harness():
    spec=importlib.util.spec_from_file_location("mana_harness",HARNESS_PATH)
    if spec is None or spec.loader is None:
        raise SystemExit("unable to load harness")
    harness=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    return harness


def valid_files(harness):
    pairs=[]
    for game in range(SAMPLES):
        control=[row(turn,game,False) for turn in range(1,11)]
        policy=[row(turn,game,True) for turn in range(1,11)]
        pairs.append((game,harness.derive_paired_game_seed(MASTER,game),control,policy))
    summary=aggregate_paired_interaction_games(
        pairs,SOURCE,MASTER,SAMPLES,harness.derive_paired_game_seed)
    summary_bytes=(json.dumps(summary,indent=2)+"\n").encode()
    audit_bytes=(
        f"audit=pass\nschema={SCHEMA}\nsource_sha={SOURCE}\n"
        f"master_seed=0x{MASTER:016X}\nsamples={SAMPLES}\nthrough=10\n").encode()
    runner_bytes=RUNNER.read_bytes()
    manifest={
        "schema":ARTIFACT_SCHEMA,"experimental_source_sha":SOURCE,
        "workflow_runner_sha":WORKFLOW,"control_sha256":CONTROL_SHA256,
        "master_seed":f"0x{MASTER:016X}","samples":SAMPLES,"through":10,
        "run_id":"123456","run_attempt":1,
        "summary_sha256":digest(summary_bytes),"audit_sha256":digest(audit_bytes),
        "runner_sha256":digest(runner_bytes),
    }
    return {
        "interaction-summary.json":summary_bytes,
        "interaction-audit.txt":audit_bytes,
        "interaction-runner.py":runner_bytes,
        "manifest.json":(json.dumps(manifest,indent=2)+"\n").encode(),
    }


def reject(files):
    try:
        audit_files(files,SOURCE,WORKFLOW,RUNNER_SHA256,MASTER,SAMPLES)
    except (ValueError,UnicodeError,KeyError,TypeError):
        return
    raise AssertionError("accepted adversarial interaction artifact")


def mutate_manifest(files,key,value):
    changed=copy.deepcopy(files)
    manifest=json.loads(changed["manifest.json"])
    manifest[key]=value
    changed["manifest.json"]=(json.dumps(manifest,indent=2)+"\n").encode()
    return changed


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    if hashlib.sha256(RUNNER.read_bytes()).hexdigest()!=RUNNER_SHA256:
        raise SystemExit("frozen interaction runner hash mismatch")
    runner_bytes=RUNNER.read_bytes()
    if b"RETIRED=False" not in runner_bytes or b"MASTER_SEED=0x1A22E7013" not in runner_bytes:
        raise SystemExit("interaction runner execution identity mismatch")
    if any(b"RETIRED=True" not in runner.read_bytes() for runner in RETIRED_RUNNERS):
        raise SystemExit("an exposed prior paired runner is not retired")

    harness=load_harness()
    harness.paired_rng_isolation_regressions()
    harness.capsize_tutor_policy_regressions()
    harness.capsize_reacquisition_regressions()
    files=valid_files(harness)
    manifest=audit_files(files,SOURCE,WORKFLOW,RUNNER_SHA256,MASTER,SAMPLES)
    assert manifest["run_id"]=="123456"

    missing=copy.deepcopy(files); missing.pop("interaction-audit.txt"); reject(missing)
    empty=copy.deepcopy(files); empty["interaction-summary.json"]=b""; reject(empty)
    transcript=copy.deepcopy(files); transcript["interaction-audit.txt"]+=b"extra\n"; reject(transcript)
    runner=copy.deepcopy(files); runner["interaction-runner.py"]+=b"\n"; reject(runner)
    reject(mutate_manifest(files,"experimental_source_sha","c"*40))
    reject(mutate_manifest(files,"summary_sha256","0"*64))
    reject(mutate_manifest(files,"run_id","0"))
    reject(mutate_manifest(files,"run_attempt",True))
    reject(mutate_manifest(files,"runner_sha256","0"*64))
    duplicate=copy.deepcopy(files)
    duplicate["manifest.json"]=b'{"schema":"x","schema":"y"}'
    reject(duplicate)

    bad_summary=copy.deepcopy(files)
    summary=json.loads(bad_summary["interaction-summary.json"])
    summary["event_model"]["countered"]["buyback_retained"]=True
    bad_summary["interaction-summary.json"]=(json.dumps(summary,indent=2)+"\n").encode()
    reject(bad_summary)

    print(f"control_sha256={CONTROL_SHA256}")
    print(f"runner_sha256={RUNNER_SHA256}")
    print(f"summary_schema={SCHEMA}")
    print(f"artifact_schema={ARTIFACT_SCHEMA}")
    print("phase=commander-independent-readiness-18-interaction-execution-freeze")
    print("pilot_pairs=10000")
    print("master_seed=0x00000001A22E7013")
    print("adversarial_artifact_rejections=11")
    print("sampled_games=0")
    print("seed_status=assigned_unconsumed")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE18_PREFLIGHT_VALIDATED")


if __name__=="__main__":
    main()
