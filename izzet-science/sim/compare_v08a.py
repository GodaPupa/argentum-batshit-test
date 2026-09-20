#!/usr/bin/env python3
"""Fail-closed comparator for the frozen v0.8-A Mizzium Skin gate."""
from __future__ import annotations

import argparse
from pathlib import Path

NEW_FIELDS={"ability_removal_guaranteed","ability_removal_loss"}


def parse_fields(line: str) -> dict[str,float]:
    tokens=line.split()
    start=2 if tokens[0]=="turn" else 3
    return {tokens[i]:float(tokens[i+1]) for i in range(start,len(tokens),2)}


def legacy_projection(line: str) -> str:
    if not line.startswith("interaction turn "):
        return line
    tokens=line.split()
    kept=tokens[:3]
    for i in range(3,len(tokens),2):
        if tokens[i] not in NEW_FIELDS:
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
        raise SystemExit("FAIL: pre-v0.8-A telemetry differs")

    for label,lines in (("control",control),("challenger",challenger)):
        lethal_by_turn={
            int(line.split()[1]):parse_fields(line)["combo_lethal"]
            for line in lines if line.startswith("turn ")
        }
        for line in (x for x in lines if x.startswith("interaction turn ")):
            turn=int(line.split()[2])
            fields=parse_fields(line)
            ability=fields["ability_removal_guaranteed"]
            lethal=lethal_by_turn[turn]
            if not 0 <= ability <= lethal:
                raise SystemExit(f"FAIL: {label} ability metric is not a lethal subset")
            if abs(fields["ability_removal_loss"]-(lethal-ability)) > 1e-12:
                raise SystemExit(f"FAIL: {label} ability loss is inconsistent")

    control_t10=parse_fields(next(x for x in control if x.startswith("interaction turn 10 ")))
    challenger_t10=parse_fields(next(x for x in challenger if x.startswith("interaction turn 10 ")))
    delta=(challenger_t10["ability_removal_guaranteed"]-
           control_t10["ability_removal_guaranteed"])
    if delta < 0.003:
        raise SystemExit(f"FAIL: T10 ability-removal delta {delta:.4f} < 0.0030")
    print("legacy_telemetry_exact=true")
    print(f"t10_ability_removal_control={control_t10['ability_removal_guaranteed']:.4f}")
    print(f"t10_ability_removal_challenger={challenger_t10['ability_removal_guaranteed']:.4f}")
    print(f"t10_ability_removal_delta={delta:.4f}")


if __name__=="__main__":
    main()
