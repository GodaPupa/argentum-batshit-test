#!/usr/bin/env python3
"""Independent 800-slot current-main registry inventory/receipt validation.

This is NOT a card-semantics test or a gameplay-admission gate. Never initializes
the frozen decks, generates seeds, reads private game state or changes pilots.
"""
from __future__ import annotations
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT=Path("experiments/manual-transmission-phase2")
REPORT_ROOT=Path(os.environ.get("MT_P2_GATE_OUTPUT","phase2-live-evidence"))
INPUT=Path(os.environ.get("MT_P2_AUDIT_INPUT","phase2-live-evidence/source/registry-requests.json"))
REGISTRY=Path(os.environ.get("MT_P2_AUDIT_OUTPUT","phase2-live-evidence/registry"))
JUNIT=Path("gym/build/test-results/test/TEST-com.wingedsheep.gym.ManualTransmissionPhaseTwoRegistryAuditTest.xml")
DECKS=("manual-transmission-v07","blue-farm","rogsi","kinnan","sisay","magda","hashaton","shorikai")

def require(yes,message):
    if not yes:raise AssertionError(message)

def read_json(path):
    return json.loads(path.read_text(encoding="utf8"))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    protocol=read_json(ROOT/"PHASE2_20261021_LIVE_REGISTRY_GATE.json")
    official=read_json(ROOT/"protocol-r1.json")
    admission=read_json(ROOT/"opponent-admission-r1.json")
    request=read_json(INPUT)
    actual=read_json(REGISTRY/"registry-report.json")
    bootstrap=read_json(REPORT_ROOT/"source/bootstrap-report.json")
    require(protocol["status"]=="PREDECLARED_BEFORE_ANY_20261021_LIVE_REGISTRY_EXECUTION","Protocol not preregistered")
    require(protocol["mode"]=="read_only_seed_free_no_actual_game","Mode changed")
    require(official["protocol_id"]==admission["protocol_id"],"Mixed official and opponent programs")
    require(official["gameplay_authorized"] is False and official["seed_generation_authorized"] is False,
            "Unexpected broad game authority in an inventory")
    require(official["official_counters"]==dict(seeds_generated=0,attempts=0,games_initialized=0,actions=0,outcomes=0),
            "Official historic pre-admission counters unexpectedly differ")
    require(official["primary"]["total_games"]==864 and official["capability_pilot"]["games_per_pod"]==12,
            "Original sample schedule altered")
    require(sum(x["decks"].count("manual-transmission-v07") for x in official["pod_templates"])==3,
            "Manual not present in every pod")
    require(request["schema"]=="mt-phase2-registry-requests-v1" and
            request["phase1_commit"]==protocol["original_phase1_sha"],"Wrong registry input origin")
    require(bootstrap["status"]=="INVENTORY_ONLY_NOT_CEDH_QUALIFICATION" and
            bootstrap["phase1_preserved"] is True and
            bootstrap["execution_allowed"] is False and
            bootstrap["requested_physical_cards"]==800 and
            bootstrap["registry_request_sha256"]==sha(INPUT),"Bootstrap provenance incomplete")
    require(actual["schema"]=="mt-phase2-live-registry-audit-v1" and
            actual["status"]=="INVENTORY_ONLY_NOT_RULES_OR_PILOT_QUALIFICATION" and
            actual["execution_allowed"] is False and
            actual["actual_deck_games_initialized"]==0 and actual["official_outcomes"]==0,
            "Runtime inventory falsely claims a game or admission")
    require(JUNIT.is_file(),"Expected new focused Kotlin registry execution XML absent")
    junit=ET.parse(JUNIT).getroot()
    tests=junit.findall(".//testcase")
    require(len(tests)==1 and
            not junit.findall(".//failure") and
            not junit.findall(".//error") and
            not junit.findall(".//skipped") and
            all(int(junit.attrib.get(x,"0"))==0 for x in ("failures","errors","skipped")),
            "Registry audit XML was not exactly one fresh clean case")
    wanted=request["decks"]
    have=actual["decks"]
    require(len(wanted)==8 and len(have)==8,"Eight real decks mandatory")
    require([x["id"] for x in wanted]==list(DECKS) and
            [x["deck_id"] for x in have]==list(DECKS),"Deck ordering or identity substitution")
    info=[]
    all_missing=Counter()
    seen_export=0
    for expected,observed in zip(wanted,have):
        name=expected["id"]
        want=expected["commanders"]+expected["mainboard"]
        require(len(want)==100 and observed["physical_cards"]==100 and
                len(observed["cards"])==100 and
                observed["commander_count"]==len(expected["commanders"]),
                f"Real deck/partner count changed: {name}")
        missing=[]
        resolved=0
        for k,(requested,card) in enumerate(zip(want,observed["cards"])):
            require(card["index"]==k and card["requested_name"]==requested,
                    f"Inventory card identity substituted: {name}[{k}]")
            require(card["rules_qualified"] is False,
                    f"Registry inventory falsely marks mechanics qualified: {name}[{k}]")
            if card["registry_resolved"]:
                require(card["resolved_name"] is not None and card["resolved_name"]!="",
                        f"Resolved card lacks actual name: {name}[{k}]")
                file=REGISTRY/(name+"-"+str(k)+".json")
                require(file.is_file() and file.stat().st_size>30,
                        f"Missing serialized actual CardDefinition: {name}[{k}]")
                # Parsed actual definition, not a fabricated placeholder count.
                definition=read_json(file)
                require(isinstance(definition,dict) and isinstance(definition.get("name"),str),
                        f"Invalid qualified-identity JSON: {name}[{k}]")
                resolved+=1
                seen_export+=1
            else:
                require(card["resolved_name"] is None,
                        f"Unresolved card pretended to have a definition: {name}[{k}]")
                missing.append(requested)
                all_missing[requested]+=1
        info.append({"deck":name,"commanders":len(expected["commanders"]),
                     "registered":resolved,"total":100,"unresolved":100-resolved,
                     "unresolved_cards":missing})
    require(sum(x["registered"]+x["unresolved"] for x in info)==800,
            "Physical card accounting incomplete")
    require(len({x["deck"] for x in info})==8,"Duplicate deck identity")
    gap_count=sum(x["unresolved"] for x in info)
    require(gap_count+seen_export==800,"Serialized original registration count mismatch")
    status="HOLD_REGISTRY_COVERAGE" if gap_count else "HOLD_SEMANTIC_AND_PILOT_ADMISSION"
    totals={"requested_slots":800,"registered_slots":seen_export,
            "unregistered_slots":gap_count,
            "unregistered_unique_names":len(all_missing),
            "manual_registered":info[0]["registered"],
            "manual_unregistered":info[0]["unresolved"],
            "opponent_registered":sum(x["registered"] for x in info[1:]),
            "opponent_unregistered":sum(x["unresolved"] for x in info[1:])}
    result={
      "schema":"ARGENTUM_MANUAL_PHASE2_CANONICAL_MAIN_REGISTRY_GATE_RESULT_V1",
      "status":status,
      "scope":"ACTUAL_CURRENT_MAIN_KOTLIN_REGISTRY_ONLY",
      "qualified_current_runtime_source_sha":os.environ.get("GITHUB_SHA","UNSPECIFIED"),
      "frozen_phase1_source_sha":protocol["original_phase1_sha"],
      "bootstrap_input_sha256":sha(INPUT),
      "actual_registry_json_sha256":sha(REGISTRY/"registry-report.json"),
      "actual_junit_xml_sha256":sha(JUNIT),
      "protocol_source_sha256":sha(ROOT/"PHASE2_20261021_LIVE_REGISTRY_GATE.json"),
      "decks":info,"totals":totals,
      "most_frequent_unregistered": [{"card":n,"slots":v} for n,v in all_missing.most_common(35)],
      "mechanics_qualification":False,
      "opponent_pilot_qualification":False,
      "three_gear_pilot_qualification":False,
      "whole_game_telemetry_replay_journal_qualification":False,
      "official_capability_game_authorized":False,
      "primary_game_authorized":False,
      "actual_games_initialized":0,
      "official_results":0,
      "precision_reserved_games_untouched":2,
      "next_gate":"Audit exact mechanics, three true gear pilots and seven exact opponent pilots; then priority/hidden-boundary, end-to-end replay, durable claim, applicable rules and independent receipt before scheduling any 36/864 game.",
      "method_limits":[
        "Catalog lookup is necessary, not sufficient, for executable full card semantics.",
        "No initial state was created; no opponent action or competitive match was sampled.",
        "Missing card definition is a blocker, never a game outcome.",
        "Official Phase 2 program retains its earlier zero counter state until separate admission."
      ]
    }
    REPORT_ROOT.mkdir(parents=True,exist_ok=True)
    (REPORT_ROOT/"LIVE_REGISTRY_GATE.json").write_text(json.dumps(result,indent=2,ensure_ascii=False)+"\n",encoding="utf8")
    lines=["# Manual Transmission Phase 2 — canonical main live 800-slot registry",
      "",f"**Disposition: {status}.** Registry-only component; NO gameplay admission.",
      "",
      "| Frozen exact 100 | Registered | Missing |","|---|---:|---:|"]
    for d in info:
        lines.append(f"| {d['deck']} | {d['registered']}/100 | {d['unresolved']} |")
    lines+=["",f"Total {seen_export}/800 physically registered, {gap_count} unresolved positions.",
      f"Unresolved unique names: {len(all_missing)}.",
      "","**CRITICAL:** Registration is not mechanic/play/pilot qualification. Even a",
      "100%-registered roster cannot enter official 36/864 trials until the",
      "additional separately independently reviewed gates are satisfied.",
      "No seeds, action attempts or games initialized; two original Precision games remain reserved.",
      ""]
    if all_missing:
        lines+=["## Unregistered card identities and affected slots",""]
        for card,num in all_missing.most_common():
            lines.append(f"- {card}: {num} positions")
    (REPORT_ROOT/"REPORT.md").write_text("\n".join(lines)+"\n",encoding="utf8")
    print("MANUAL_PHASE2_CURRENT_MAIN_REGISTRY_GATE "+json.dumps({"status":status,**totals},sort_keys=True))

if __name__=="__main__":
    main()
