#!/usr/bin/env python3
"""Fail-closed audit of a paired Capsize-interaction pilot artifact."""
from __future__ import annotations

import argparse
import hashlib
import re
from pathlib import Path

from audit_v09_capsize_interaction_output import loads_strict
from paired_capsize_interaction_contract import (
    CONTROL_SHA256,
    SCHEMA,
    validate_interaction_summary,
)


ARTIFACT_SCHEMA="izzet-v09-capsize-interaction-artifact-v1"
FILES={"interaction-summary.json","interaction-audit.txt",
       "interaction-runner.py","manifest.json"}
MANIFEST_KEYS={"schema","experimental_source_sha","workflow_runner_sha",
               "control_sha256","master_seed","samples","through","run_id",
               "run_attempt","summary_sha256","audit_sha256","runner_sha256"}


def digest(payload):
    return hashlib.sha256(payload).hexdigest()


def audit_files(files,expected_source,expected_workflow,expected_runner_sha256,
                expected_seed,expected_samples):
    if set(files)!=FILES or any(
            not isinstance(value,bytes) or not value for value in files.values()):
        raise ValueError("artifact file set mismatch or empty file")
    for name,value in (("expected_source",expected_source),
                       ("expected_workflow",expected_workflow)):
        if not isinstance(value,str) or not re.fullmatch(r"[0-9a-f]{40}",value):
            raise ValueError(f"{name} must be lowercase 40-hex")
    if not isinstance(expected_runner_sha256,str) or not re.fullmatch(
            r"[0-9a-f]{64}",expected_runner_sha256):
        raise ValueError("expected runner SHA256 must be lowercase 64-hex")

    summary=loads_strict(files["interaction-summary.json"].decode("utf-8"))
    validate_interaction_summary(
        summary,expected_source,expected_seed,expected_samples)
    expected_audit=(
        f"audit=pass\n"
        f"schema={SCHEMA}\n"
        f"source_sha={expected_source}\n"
        f"master_seed=0x{expected_seed:016X}\n"
        f"samples={expected_samples}\n"
        f"through=10\n").encode()
    if files["interaction-audit.txt"]!=expected_audit:
        raise ValueError("summary audit transcript mismatch")

    manifest=loads_strict(files["manifest.json"].decode("utf-8"))
    if not isinstance(manifest,dict) or set(manifest)!=MANIFEST_KEYS:
        raise ValueError("manifest schema mismatch")
    expected={
        "schema":ARTIFACT_SCHEMA,
        "experimental_source_sha":expected_source,
        "workflow_runner_sha":expected_workflow,
        "control_sha256":CONTROL_SHA256,
        "master_seed":f"0x{expected_seed:016X}",
        "samples":expected_samples,
        "through":10,
        "summary_sha256":digest(files["interaction-summary.json"]),
        "audit_sha256":digest(files["interaction-audit.txt"]),
        "runner_sha256":digest(files["interaction-runner.py"]),
    }
    for key,value in expected.items():
        if manifest.get(key)!=value:
            raise ValueError(f"manifest mismatch: {key}")
    if manifest["runner_sha256"]!=expected_runner_sha256:
        raise ValueError("frozen runner hash mismatch")
    if (not isinstance(manifest["run_id"],str) or
            not re.fullmatch(r"[1-9][0-9]*",manifest["run_id"])):
        raise ValueError("invalid run ID")
    if type(manifest["run_attempt"]) is not int or manifest["run_attempt"]<1:
        raise ValueError("invalid run attempt")
    return manifest


def main() -> None:
    parser=argparse.ArgumentParser()
    parser.add_argument("artifact",type=Path)
    parser.add_argument("--expected-source",required=True)
    parser.add_argument("--expected-workflow",required=True)
    parser.add_argument("--expected-runner-sha256",required=True)
    parser.add_argument("--expected-seed",required=True,type=lambda x:int(x,0))
    parser.add_argument("--expected-samples",required=True,type=int)
    args=parser.parse_args()
    if not args.artifact.is_dir():
        raise SystemExit("artifact path is not a directory")
    files={path.name:path.read_bytes() for path in args.artifact.iterdir()
           if path.is_file()}
    manifest=audit_files(
        files,args.expected_source,args.expected_workflow,
        args.expected_runner_sha256,args.expected_seed,args.expected_samples)
    print("artifact_audit=pass")
    print(f"run_id={manifest['run_id']}")
    print(f"run_attempt={manifest['run_attempt']}")


if __name__=="__main__":
    main()
