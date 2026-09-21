#!/usr/bin/env python3
"""Execute the sole frozen v0.9 paired Capsize-interaction pilot."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

import mana_harness as harness
from paired_capsize_interaction_contract import aggregate_paired_interaction_games


CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
MASTER_SEED=0x1A22E7013
SAMPLES=10000
THROUGH=10
RETIRED=False
RETIRED_REASON="Phase-18 pilot has not executed"


def main() -> None:
    if RETIRED:
        raise SystemExit(RETIRED_REASON)
    parser=argparse.ArgumentParser()
    parser.add_argument("--deck",type=Path,default=Path("izzet-science/v0.7-control.md"))
    parser.add_argument("--source-sha",required=True)
    parser.add_argument("--output",type=Path,required=True)
    args=parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}",args.source_sha):
        raise SystemExit("source SHA must be lowercase 40-hex")
    if hashlib.sha256(args.deck.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    _,cards=harness.parse_deck(args.deck)
    if len(cards)!=99:
        raise SystemExit(f"control main-deck identity mismatch: {len(cards)} cards")
    pairs=harness.iter_paired_capsize_policy_games(
        cards,SAMPLES,MASTER_SEED,THROUGH,False,True,False,True)
    summary=aggregate_paired_interaction_games(
        pairs,args.source_sha,MASTER_SEED,SAMPLES,
        harness.derive_paired_game_seed,THROUGH)
    args.output.write_text(json.dumps(summary,indent=2)+"\n",encoding="utf-8")


if __name__=="__main__":
    main()
