#!/usr/bin/env python3
"""Paired fixed-event Capsize estimands and fail-closed summary contract."""
from __future__ import annotations

import copy
import re


SCHEMA="izzet-v09-capsize-interaction-paired-v1"
DERIVATION="sha256-domain-counter-u64be"
CONTROL_SHA256="726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
METRICS=(
    "response_window_capsize_present","hostile_permanent_one_shot",
    "hostile_permanent_buyback","guildmage_self_rescue",
    "opposing_commander_to_hand","opposing_commander_to_command",
    "combo_lethal","lethal_by_now","commander_battlefield",
)
PAIR_KEYS=("both","control_only","policy_only","neither","delta")
POLICY_EVENT_KEYS=("capsize_tutor_used","capsize_tutor_found",
                   "capsize_scroll_used","capsize_drift_used")
EVENT_MODEL={
    "observation_window":"after deterministic turn actions and mana spending",
    "hostile_permanent":"legal permanent; no ward; no opponent response",
    "opposing_commander_destinations":["hand","command"],
    "countered":{"resolves":False,"buyback_retained":False},
    "target_illegal":{"resolves":False,"buyback_retained":False},
    "interpretation":"fixed-event readiness; not tempo, survival, or win rate",
}
TOP_KEYS={"schema","source_sha","control_sha256","master_seed","samples",
          "through","derivation","event_model","turns"}
TURN_KEYS={"turn","n","capsize_tutor_events_by_now",
           "capsize_ever_tutored_by_now","merchant_scroll_events_by_now",
           "drift_events_by_now","metrics"}


def _count(value,name):
    if not isinstance(value,int) or isinstance(value,bool) or value<0:
        raise ValueError(f"{name} must be a nonnegative integer")
    return value


def _u64(value,name):
    if not isinstance(value,int) or isinstance(value,bool) or not 0<=value<2**64:
        raise ValueError(f"{name} must be an unsigned 64-bit integer")
    return value


def _paired_cell(control,policy):
    if type(control) is not bool or type(policy) is not bool:
        raise ValueError("paired outcomes must be Boolean")
    return ("both" if control and policy else
            "control_only" if control else
            "policy_only" if policy else "neither")


def aggregate_paired_interaction_games(
        pairs,source_sha,master_seed,samples,derive_seed,through=10):
    if not isinstance(source_sha,str) or not re.fullmatch(r"[0-9a-f]{40}",source_sha):
        raise ValueError("source_sha must be lowercase 40-hex")
    if not isinstance(samples,int) or isinstance(samples,bool) or samples<1:
        raise ValueError("samples must be a positive integer")
    _u64(master_seed,"master_seed")
    if not isinstance(through,int) or isinstance(through,bool) or through!=10:
        raise ValueError("through must equal 10")
    turns=[]
    for turn in range(1,through+1):
        turns.append({
            "turn":turn,"n":0,"capsize_tutor_events_by_now":0,
            "capsize_ever_tutored_by_now":0,
            "merchant_scroll_events_by_now":0,"drift_events_by_now":0,
            "metrics":{metric:{"both":0,"control_only":0,"policy_only":0,
                                "neither":0,"delta":0}
                       for metric in METRICS},
        })
    seen=0
    child_seeds=set()
    for expected_index,pair in enumerate(pairs):
        if not isinstance(pair,tuple) or len(pair)!=4:
            raise ValueError("paired iterator item must be a four-tuple")
        game_index,child_seed,control_rows,policy_rows=pair
        if game_index!=expected_index or game_index>=samples:
            raise ValueError("paired game index mismatch")
        if child_seed!=derive_seed(master_seed,game_index) or child_seed in child_seeds:
            raise ValueError("paired child seed mismatch or duplicate")
        child_seeds.add(child_seed)
        if len(control_rows)!=through or len(policy_rows)!=through:
            raise ValueError("paired trajectory length mismatch")
        events_by_now=scroll_events=drift_events=0
        ever_tutored=False
        for offset,(control,policy) in enumerate(zip(control_rows,policy_rows),1):
            if control.get("turn")!=offset or policy.get("turn")!=offset:
                raise ValueError("paired turn order mismatch")
            if any(key in control for key in POLICY_EVENT_KEYS):
                raise ValueError("control row contains policy event telemetry")
            events=[policy.get(key) for key in POLICY_EVENT_KEYS]
            if any(type(value) is not bool for value in events):
                raise ValueError("policy event telemetry must be Boolean")
            used,found,used_scroll,used_drift=events
            if not (used==found and int(used)==int(used_scroll)+int(used_drift)):
                raise ValueError("policy event identity violation")
            events_by_now+=int(used)
            scroll_events+=int(used_scroll)
            drift_events+=int(used_drift)
            ever_tutored=ever_tutored or used
            out=turns[offset-1]
            out["n"]+=1
            out["capsize_tutor_events_by_now"]+=events_by_now
            out["capsize_ever_tutored_by_now"]+=int(ever_tutored)
            out["merchant_scroll_events_by_now"]+=scroll_events
            out["drift_events_by_now"]+=drift_events
            for metric in METRICS:
                cell=_paired_cell(control.get(metric),policy.get(metric))
                out["metrics"][metric][cell]+=1
        seen+=1
    if seen!=samples:
        raise ValueError(f"paired sample count mismatch expected={samples} actual={seen}")
    for row in turns:
        for metric in METRICS:
            cells=row["metrics"][metric]
            cells["delta"]=cells["policy_only"]-cells["control_only"]
    summary={
        "schema":SCHEMA,"source_sha":source_sha,"control_sha256":CONTROL_SHA256,
        "master_seed":f"0x{master_seed:016X}","samples":samples,"through":through,
        "derivation":DERIVATION,"event_model":copy.deepcopy(EVENT_MODEL),"turns":turns,
    }
    validate_interaction_summary(summary,source_sha,master_seed,samples)
    return summary


def validate_interaction_summary(summary,expected_source,expected_seed,expected_samples):
    if not isinstance(summary,dict) or set(summary)!=TOP_KEYS:
        raise ValueError("summary top-level schema mismatch")
    if summary["schema"]!=SCHEMA or summary["derivation"]!=DERIVATION:
        raise ValueError("summary identity mismatch")
    if summary["event_model"]!=EVENT_MODEL:
        raise ValueError("fixed-event model mismatch")
    if (not isinstance(expected_source,str) or
            summary["source_sha"]!=expected_source or
            not re.fullmatch(r"[0-9a-f]{40}",expected_source)):
        raise ValueError("experimental source mismatch")
    if summary["control_sha256"]!=CONTROL_SHA256:
        raise ValueError("accepted control hash mismatch")
    _u64(expected_seed,"expected_seed")
    if summary["master_seed"]!=f"0x{expected_seed:016X}":
        raise ValueError("master seed mismatch or noncanonical encoding")
    if type(expected_samples) is not int or expected_samples<1:
        raise ValueError("expected sample count must be a positive integer")
    if _count(summary["samples"],"samples")!=expected_samples:
        raise ValueError("sample count mismatch")
    if type(summary["through"]) is not int or summary["through"]!=10:
        raise ValueError("turn horizon mismatch")
    if not isinstance(summary["turns"],list) or len(summary["turns"])!=10:
        raise ValueError("turn horizon mismatch")
    previous_events=(0,0,0,0)
    previous_lethal={"control":0,"policy":0}
    for expected_turn,row in enumerate(summary["turns"],1):
        if (not isinstance(row,dict) or set(row)!=TURN_KEYS or
                _count(row["turn"],"turn")!=expected_turn):
            raise ValueError("turn row schema or order mismatch")
        n=_count(row["n"],"n")
        if n!=expected_samples:
            raise ValueError("turn sample count mismatch")
        events=tuple(_count(row[key],key) for key in
                     ("capsize_tutor_events_by_now","capsize_ever_tutored_by_now",
                      "merchant_scroll_events_by_now","drift_events_by_now"))
        total,ever,scroll,drift=events
        if total!=scroll+drift or ever>n or ever>total or total>n*expected_turn:
            raise ValueError("cumulative tutor identity violation")
        if any(now<before for now,before in zip(events,previous_events)):
            raise ValueError("cumulative tutor count decreased")
        previous_events=events
        if not isinstance(row["metrics"],dict) or set(row["metrics"])!=set(METRICS):
            raise ValueError("paired metric schema mismatch")
        arm_counts={}
        for metric in METRICS:
            cells=row["metrics"][metric]
            if not isinstance(cells,dict) or tuple(cells)!=PAIR_KEYS:
                raise ValueError(f"paired cells schema mismatch metric={metric}")
            both=_count(cells["both"],"both")
            control=_count(cells["control_only"],"control_only")
            policy=_count(cells["policy_only"],"policy_only")
            neither=_count(cells["neither"],"neither")
            delta=cells["delta"]
            if type(delta) is not int:
                raise ValueError("delta must be an integer")
            if both+control+policy+neither!=n or delta!=policy-control:
                raise ValueError(f"paired partition violation metric={metric}")
            arm_counts[metric]=(both+control,both+policy)
        one=row["metrics"]["hostile_permanent_one_shot"]
        if (row["metrics"]["opposing_commander_to_hand"]!=one or
                row["metrics"]["opposing_commander_to_command"]!=one):
            raise ValueError("commander destination branches diverged")
        for arm,index in (("control",0),("policy",1)):
            if (arm_counts["hostile_permanent_one_shot"][index]>
                    arm_counts["response_window_capsize_present"][index]):
                raise ValueError(f"one-shot readiness subset violation arm={arm}")
            if arm_counts["hostile_permanent_buyback"][index]>arm_counts["hostile_permanent_one_shot"][index]:
                raise ValueError(f"buyback readiness subset violation arm={arm}")
            if (arm_counts["guildmage_self_rescue"][index]>
                    min(arm_counts["hostile_permanent_one_shot"][index],
                        arm_counts["commander_battlefield"][index])):
                raise ValueError(f"self-rescue subset violation arm={arm}")
            lethal=arm_counts["lethal_by_now"][index]
            if lethal<previous_lethal[arm]:
                raise ValueError(f"cumulative lethal decreased arm={arm}")
            previous_lethal[arm]=lethal
    return True
