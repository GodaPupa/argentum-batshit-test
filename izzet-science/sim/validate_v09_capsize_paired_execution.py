#!/usr/bin/env python3
"""Seed-free Phase-9 validation of runner identity and artifact provenance."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path

from audit_v09_capsize_paired_artifact import ARTIFACT_SCHEMA,audit_files,digest
from paired_capsize_contract import CONTROL_SHA256,SCHEMA,aggregate_paired_games
from validate_paired_capsize_contract import row


ROOT=Path(__file__).resolve().parents[2]
HARNESS_PATH=ROOT/"izzet-science/sim/mana_harness.py"
RUNNER=ROOT/"izzet-science/sim/run_v09_capsize_paired_pilot.py"
CONTROL=ROOT/"izzet-science/v0.7-control.md"
RUNNER_SHA256="1dce15e3555f937964ae571140ca43e763f52747af56c2be71d53a7b7bcebb36"
SOURCE="b"*40
WORKFLOW="c"*40
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
    summary=aggregate_paired_games(
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
    return {"paired-summary.json":summary_bytes,"paired-audit.txt":audit_bytes,
            "paired-runner.py":runner_bytes,
            "manifest.json":(json.dumps(manifest,indent=2)+"\n").encode()}


def reject(files):
    try:
        audit_files(files,SOURCE,WORKFLOW,RUNNER_SHA256,MASTER,SAMPLES)
    except (ValueError,UnicodeError,KeyError,TypeError):
        return
    raise AssertionError("accepted adversarial artifact")


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
        raise SystemExit("frozen paired runner hash mismatch")
    harness=load_harness()
    harness.paired_rng_isolation_regressions()
    files=valid_files(harness)
    manifest=audit_files(files,SOURCE,WORKFLOW,RUNNER_SHA256,MASTER,SAMPLES)
    assert manifest["run_id"]=="123456"

    missing=copy.deepcopy(files); missing.pop("paired-audit.txt"); reject(missing)
    empty=copy.deepcopy(files); empty["paired-summary.json"]=b""; reject(empty)
    transcript=copy.deepcopy(files); transcript["paired-audit.txt"]+=b"extra\n"; reject(transcript)
    runner=copy.deepcopy(files); runner["paired-runner.py"]+=b"\n"; reject(runner)
    reject(mutate_manifest(files,"experimental_source_sha","d"*40))
    reject(mutate_manifest(files,"summary_sha256","0"*64))
    reject(mutate_manifest(files,"run_id","0"))
    reject(mutate_manifest(files,"run_attempt",True))
    reject(mutate_manifest(files,"runner_sha256","0"*64))
    duplicate=copy.deepcopy(files)
    duplicate["manifest.json"]=b'{"schema":"x","schema":"y"}'
    reject(duplicate)

    print(f"control_sha256={CONTROL_SHA256}")
    print(f"runner_sha256={RUNNER_SHA256}")
    print("phase=commander-independent-readiness-9-paired-execution-freeze")
    print("pilot_pairs=10000")
    print("master_seed=0x00000001A22E7010")
    print("adversarial_artifact_rejections=10")
    print("sampled_games=0")
    print("seed_status=assigned_unconsumed")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE9_PREFLIGHT_VALIDATED")


if __name__=="__main__":
    main()
