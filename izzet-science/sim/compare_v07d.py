#!/usr/bin/env python3
"""Fail-closed comparator for the frozen v0.7-D snow-basic gate."""
from __future__ import annotations

import argparse
from pathlib import Path

SNOW_FIELDS={"avg_snow_permanents","avg_skred_damage","skred_live","skred_3plus"}


def parse_fields(line: str) -> dict[str,float]:
    tokens=line.split()
    start=2 if tokens[0]=="turn" else 3
    fields={}
    for i in range(start,len(tokens),2):
        fields[tokens[i]]=float(tokens[i+1])
    return fields


def legacy_projection(line: str) -> str:
    if not line.startswith("turn "):
        return line
    tokens=line.split()
    kept=tokens[:2]
    for i in range(2,len(tokens),2):
        if tokens[i] not in SNOW_FIELDS:
            kept.extend(tokens[i:i+2])
    return " ".join(kept)


def load(path: Path) -> list[str]:
    lines=path.read_text().splitlines()
    if len([x for x in lines if x.startswith("turn ")])!=10:
        raise ValueError(f"{path}: expected ten turn rows")
    if len([x for x in lines if x.startswith("interaction turn ")])!=10:
        raise ValueError(f"{path}: expected ten interaction rows")
    return lines


def main() -> None:
    ap=argparse.ArgumentParser()
    ap.add_argument("control",type=Path)
    ap.add_argument("challenger",type=Path)
    args=ap.parse_args()
    control=load(args.control)
    challenger=load(args.challenger)
    if [legacy_projection(x) for x in control] != [legacy_projection(x) for x in challenger]:
        raise SystemExit("FAIL: pre-v0.7-D telemetry differs")

    control_t10=parse_fields(next(x for x in control if x.startswith("turn 10 ")))
    challenger_t10=parse_fields(next(x for x in challenger if x.startswith("turn 10 ")))
    delta_3plus=challenger_t10["skred_3plus"]-control_t10["skred_3plus"]
    if delta_3plus < 0.05:
        raise SystemExit(f"FAIL: T10 skred_3plus delta {delta_3plus:.4f} < 0.0500")
    for field in ("avg_skred_damage","skred_live"):
        if challenger_t10[field] <= control_t10[field]:
            raise SystemExit(f"FAIL: T10 {field} did not strictly improve")
    print("legacy_telemetry_exact=true")
    print(f"t10_skred_3plus_control={control_t10['skred_3plus']:.4f}")
    print(f"t10_skred_3plus_challenger={challenger_t10['skred_3plus']:.4f}")
    print(f"t10_skred_3plus_delta={delta_3plus:.4f}")
    print(f"t10_avg_skred_damage_control={control_t10['avg_skred_damage']:.4f}")
    print(f"t10_avg_skred_damage_challenger={challenger_t10['avg_skred_damage']:.4f}")
    print(f"t10_skred_live_control={control_t10['skred_live']:.4f}")
    print(f"t10_skred_live_challenger={challenger_t10['skred_live']:.4f}")


if __name__=="__main__":
    main()
