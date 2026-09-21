#!/usr/bin/env python3
"""Seed-free Phase-8 validation for paired estimands and output auditing."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
from pathlib import Path

from audit_v09_capsize_paired_output import loads_strict
from paired_capsize_contract import aggregate_paired_games,validate_summary


ROOT=Path(__file__).resolve().parents[2]
HARNESS_PATH=ROOT/"izzet-science/sim/mana_harness.py"
CONTROL=ROOT/"izzet-science/v0.7-control.md"
CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
SOURCE="a"*40
MASTER=1
SAMPLES=2


def load_harness():
    spec=importlib.util.spec_from_file_location("mana_harness",HARNESS_PATH)
    if spec is None or spec.loader is None:
        raise SystemExit("unable to load harness")
    harness=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(harness)
    return harness


def row(turn,game,policy):
    combo_pair=turn>=(5 if game else 6)
    lethal=turn>=(7 if game else 8)
    out={
        "turn":turn,
        "capsize_present":bool(policy and game==0 and turn>=3),
        "capsize_buyback":bool(policy and game==0 and turn>=6),
        "combo_pair":combo_pair,
        "combo_lethal":lethal,
        "lethal_by_now":lethal,
        "commander_battlefield":turn>=(3 if policy and game==0 else 2),
    }
    if policy:
        event=game==0 and turn==2
        out.update({"capsize_tutor_used":event,"capsize_tutor_found":event,
                    "capsize_scroll_used":event,"capsize_drift_used":False})
    return out


def expect_rejection(summary,mutator):
    bad=copy.deepcopy(summary); mutator(bad)
    try:
        validate_summary(bad,SOURCE,MASTER,SAMPLES)
    except (ValueError,KeyError,TypeError):
        return
    raise AssertionError("accepted adversarial paired summary")


def main() -> None:
    if hashlib.sha256(CONTROL.read_bytes()).hexdigest()!=CONTROL_SHA256:
        raise SystemExit("accepted control hash mismatch")
    harness=load_harness()
    _,cards=harness.parse_deck(CONTROL)
    if len(cards)!=99:
        raise SystemExit(f"control main-deck identity mismatch: {len(cards)} cards")
    harness.paired_rng_isolation_regressions()
    pairs=[]
    for game in range(SAMPLES):
        control=[row(turn,game,False) for turn in range(1,11)]
        policy=[row(turn,game,True) for turn in range(1,11)]
        pairs.append((game,harness.derive_paired_game_seed(MASTER,game),control,policy))
    summary=aggregate_paired_games(
        pairs,SOURCE,MASTER,SAMPLES,harness.derive_paired_game_seed)
    assert validate_summary(summary,SOURCE,MASTER,SAMPLES)
    assert summary["turns"][2]["metrics"]["capsize_present"]["policy_only"]==1
    assert summary["turns"][1]["metrics"]["commander_battlefield"]["control_only"]==1

    mutators=(
        lambda s:s.pop("schema"),
        lambda s:s.__setitem__("source_sha","b"*40),
        lambda s:s["turns"].pop(),
        lambda s:s["turns"][0].__setitem__("n",True),
        lambda s:s["turns"][0]["metrics"]["combo_pair"].__setitem__("neither",99),
        lambda s:s["turns"][0]["metrics"]["combo_pair"].__setitem__("delta",1),
        lambda s:s["turns"][1].__setitem__("capsize_tutored_by_now",2),
        lambda s:s["turns"][2].__setitem__("capsize_tutored_by_now",0),
        lambda s:s["turns"][0]["metrics"]["capsize_buyback"].update(
            {"both":0,"control_only":0,"policy_only":1,"neither":1,"delta":1}),
    )
    for mutator in mutators: expect_rejection(summary,mutator)

    lethal_bad=copy.deepcopy(summary)
    lethal_bad["turns"][3]["metrics"]["lethal_by_now"].update(
        {"both":2,"control_only":0,"policy_only":0,"neither":0,"delta":0})
    lethal_bad["turns"][4]["metrics"]["lethal_by_now"].update(
        {"both":0,"control_only":0,"policy_only":0,"neither":2,"delta":0})
    try:
        validate_summary(lethal_bad,SOURCE,MASTER,SAMPLES)
    except ValueError:
        pass
    else:
        raise AssertionError("accepted decreasing cumulative lethal")

    assert loads_strict(json.dumps(summary))==summary
    for payload in ('{"schema":"x","schema":"y"}','{"value":NaN}'):
        try:
            loads_strict(payload)
        except ValueError:
            pass
        else:
            raise AssertionError("accepted noncanonical JSON")

    print(f"control_sha256={CONTROL_SHA256}")
    print("phase=commander-independent-readiness-8-paired-output-contract")
    print("paired_metrics="+",".join(summary["turns"][0]["metrics"]))
    print("adversarial_rejections=12")
    print("sampled_games=0")
    print("seeds_consumed=0")
    print("pilot_authorized=0")
    print("outcome_claims=0")
    print("disposition=V09_PHASE8_SEED_FREE_VALIDATED")


if __name__=="__main__":
    main()
