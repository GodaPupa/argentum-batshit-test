#!/usr/bin/env python3
"""Fail-closed output audit for the v0.9 Phase-4 tutor-opportunity pilot."""
from __future__ import annotations

import argparse
import math
from pathlib import Path


EXPECTED_SEED="0x1a22e700f"
EXPECTED_SAMPLES=10000
BACKUP_FIELDS={
    "mystic_present","mystic_castable","rolling_present","rolling_live",
    "rolling_5plus","avg_rolling_x","torch_present","torch_live",
    "torch_5plus","avg_torch_x","capsize_present","capsize_buyback",
}
TUTOR_FIELDS={
    f"{prefix}_tutor_{suffix}"
    for prefix in ("mystic","rolling","torch","capsize")
    for suffix in ("targetable","payable","uncontested")
}


def fields(line: str, prefix_words: int) -> dict[str,float]:
    tokens=line.split()[prefix_words:]
    if len(tokens)%2:
        raise ValueError(f"odd telemetry field count: {line}")
    parsed={}
    for key,value in zip(tokens[::2],tokens[1::2]):
        if key in parsed:
            raise ValueError(f"duplicate telemetry field: {key}")
        number=float(value)
        if not math.isfinite(number):
            raise ValueError(f"non-finite {key}: {value}")
        parsed[key]=number
    return parsed


def audit(text: str) -> dict[str,dict[str,float]]:
    lines=[line.strip() for line in text.splitlines() if line.strip()]
    header=next((line for line in lines if line.startswith("seed ")),None)
    if header!=f"seed {EXPECTED_SEED} samples {EXPECTED_SAMPLES}":
        raise ValueError(f"seed/sample header mismatch: {header!r}")
    standard=[line for line in lines if line.startswith("turn ")]
    backup=[line for line in lines if line.startswith("backup turn ")]
    tutors=[line for line in lines if line.startswith("backup_tutors turn ")]
    if (len(standard),len(backup),len(tutors))!=(10,10,10):
        raise ValueError(
            f"incomplete turns standard={len(standard)} backup={len(backup)} tutors={len(tutors)}")
    for rows,index in ((standard,1),(backup,2),(tutors,2)):
        if [int(line.split()[index]) for line in rows]!=list(range(1,11)):
            raise ValueError("turn sequence mismatch")

    last_backup={}
    for line in backup:
        turn=int(line.split()[2]); data=fields(line,3)
        if set(data)!=BACKUP_FIELDS:
            raise ValueError(f"backup schema mismatch turn={turn}")
        for key in BACKUP_FIELDS-{"avg_rolling_x","avg_torch_x"}:
            if not 0<=data[key]<=1:
                raise ValueError(f"backup rate out of range turn={turn} key={key}")
        for ready,present in (("mystic_castable","mystic_present"),
                              ("rolling_live","rolling_present"),
                              ("rolling_5plus","rolling_live"),
                              ("torch_live","torch_present"),
                              ("torch_5plus","torch_live"),
                              ("capsize_buyback","capsize_present")):
            if data[ready]>data[present]+1e-12:
                raise ValueError(f"backup subset violation turn={turn} {ready}>{present}")
        if data["avg_rolling_x"]<0 or data["avg_torch_x"]<0:
            raise ValueError(f"negative X capacity turn={turn}")
        last_backup=data

    last_tutors={}
    for line in tutors:
        turn=int(line.split()[2]); data=fields(line,3)
        if set(data)!=TUTOR_FIELDS:
            raise ValueError(f"tutor schema mismatch turn={turn}")
        if any(not 0<=value<=1 for value in data.values()):
            raise ValueError(f"tutor rate out of range turn={turn}")
        for prefix in ("mystic","rolling","torch","capsize"):
            targetable=data[f"{prefix}_tutor_targetable"]
            payable=data[f"{prefix}_tutor_payable"]
            uncontested=data[f"{prefix}_tutor_uncontested"]
            if uncontested>payable+1e-12 or payable>targetable+1e-12:
                raise ValueError(f"tutor subset violation turn={turn} target={prefix}")
        if any(data[f"mystic_tutor_{suffix}"]!=0
               for suffix in ("targetable","payable","uncontested")):
            raise ValueError(f"impossible Mystic tutor connectivity turn={turn}")
        last_tutors=data
    return {"backup":last_backup,"tutors":last_tutors}


def expect_failure(text: str) -> None:
    try:
        audit(text)
    except ValueError:
        return
    raise AssertionError("auditor accepted invalid fixture")


def self_test() -> None:
    backup=("mystic_present 0.1 mystic_castable 0.05 rolling_present 0.1 "
            "rolling_live 0.08 rolling_5plus 0.02 avg_rolling_x 0.3 "
            "torch_present 0.1 torch_live 0.08 torch_5plus 0.02 avg_torch_x 0.3 "
            "capsize_present 0.1 capsize_buyback 0.03")
    tutor=("mystic_tutor_targetable 0 mystic_tutor_payable 0 mystic_tutor_uncontested 0 "
           "rolling_tutor_targetable 0.1 rolling_tutor_payable 0.08 rolling_tutor_uncontested 0.04 "
           "torch_tutor_targetable 0.1 torch_tutor_payable 0.08 torch_tutor_uncontested 0.04 "
           "capsize_tutor_targetable 0.12 capsize_tutor_payable 0.1 capsize_tutor_uncontested 0.1")
    header=f"seed {EXPECTED_SEED} samples {EXPECTED_SAMPLES}\n"
    standard="\n".join(f"turn {turn} placeholder 0" for turn in range(1,11))+"\n"
    backup_rows="\n".join(f"backup turn {turn} {backup}" for turn in range(1,11))+"\n"
    tutor_rows="\n".join(f"backup_tutors turn {turn} {tutor}" for turn in range(1,11))+"\n"
    valid=header+standard+backup_rows+tutor_rows
    result=audit(valid)
    assert result["tutors"]["capsize_tutor_uncontested"]==0.1
    expect_failure(valid.replace("samples 10000","samples 9999",1))
    expect_failure(valid.replace("mystic_tutor_targetable 0","mystic_tutor_targetable 0.01",1))
    expect_failure(valid.replace("rolling_tutor_uncontested 0.04",
                                 "rolling_tutor_uncontested 0.09",1))
    expect_failure(valid.replace("backup_tutors turn 10","omitted turn 10",1))


def main() -> None:
    parser=argparse.ArgumentParser()
    parser.add_argument("output",nargs="?",type=Path)
    parser.add_argument("--self-test",action="store_true")
    args=parser.parse_args()
    if args.self_test:
        self_test()
        print("audit_self_test=pass")
        return
    if args.output is None:
        raise SystemExit("output path required")
    last=audit(args.output.read_text())
    print("output_audit=pass")
    for group in ("backup","tutors"):
        for key in sorted(last[group]):
            print(f"t10_{key}={last[group][key]}")


if __name__=="__main__":
    main()
