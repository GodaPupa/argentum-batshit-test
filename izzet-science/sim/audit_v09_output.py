#!/usr/bin/env python3
"""Fail-closed completeness and invariant audit for the v0.9 Phase-1 pilot."""
from __future__ import annotations

import argparse
import math
from pathlib import Path


EXPECTED_SEED="0x1a22e700e"
EXPECTED_SAMPLES=10000


def fields(line: str, prefix_words: int) -> dict[str,float]:
    tokens=line.split()[prefix_words:]
    if len(tokens)%2:
        raise ValueError(f"odd telemetry field count: {line}")
    parsed={}
    for key,value in zip(tokens[::2],tokens[1::2]):
        number=float(value)
        if not math.isfinite(number):
            raise ValueError(f"non-finite {key}: {value}")
        parsed[key]=number
    return parsed


def audit(text: str) -> dict[str,float]:
    lines=[line.strip() for line in text.splitlines() if line.strip()]
    header=next((line for line in lines if line.startswith("seed ")),None)
    if header!=f"seed {EXPECTED_SEED} samples {EXPECTED_SAMPLES}":
        raise ValueError(f"seed/sample header mismatch: {header!r}")
    standard=[line for line in lines if line.startswith("turn ")]
    backup=[line for line in lines if line.startswith("backup turn ")]
    if len(standard)!=10 or len(backup)!=10:
        raise ValueError(f"incomplete turns standard={len(standard)} backup={len(backup)}")
    if [int(line.split()[1]) for line in standard]!=list(range(1,11)):
        raise ValueError("standard turn sequence mismatch")
    if [int(line.split()[2]) for line in backup]!=list(range(1,11)):
        raise ValueError("backup turn sequence mismatch")
    last={}
    required={"mystic_present","mystic_castable","rolling_present","rolling_live",
              "rolling_5plus","avg_rolling_x","torch_present","torch_live",
              "torch_5plus","avg_torch_x","capsize_present","capsize_buyback"}
    for line in backup:
        turn=int(line.split()[2]); data=fields(line,3)
        if set(data)!=required:
            raise ValueError(f"backup schema mismatch turn={turn}")
        for key in required-{"avg_rolling_x","avg_torch_x"}:
            if not 0<=data[key]<=1:
                raise ValueError(f"rate out of range turn={turn} key={key}")
        for ready,present in (("mystic_castable","mystic_present"),
                              ("rolling_live","rolling_present"),
                              ("rolling_5plus","rolling_live"),
                              ("torch_live","torch_present"),
                              ("torch_5plus","torch_live"),
                              ("capsize_buyback","capsize_present")):
            if data[ready]>data[present]+1e-12:
                raise ValueError(f"subset violation turn={turn} {ready}>{present}")
        if data["avg_rolling_x"]<0 or data["avg_torch_x"]<0:
            raise ValueError(f"negative X capacity turn={turn}")
        last=data
    return last


def self_test() -> None:
    rate=("mystic_present 0.1 mystic_castable 0.05 rolling_present 0.1 "
          "rolling_live 0.08 rolling_5plus 0.02 avg_rolling_x 0.3 "
          "torch_present 0.1 torch_live 0.08 torch_5plus 0.02 avg_torch_x 0.3 "
          "capsize_present 0.1 capsize_buyback 0.03")
    text=f"seed {EXPECTED_SEED} samples {EXPECTED_SAMPLES}\n"
    text+="\n".join(f"turn {turn} placeholder 0" for turn in range(1,11))+"\n"
    text+="\n".join(f"backup turn {turn} {rate}" for turn in range(1,11))+"\n"
    assert audit(text)["mystic_castable"]==0.05


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
    for key in sorted(last):
        print(f"t10_{key}={last[key]}")


if __name__=="__main__":
    main()
