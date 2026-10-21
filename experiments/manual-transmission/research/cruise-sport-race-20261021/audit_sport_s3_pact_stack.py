#!/usr/bin/env python3
"""Source-only archive audit: original Pact options vs original live stack spells.

No GameInitializer, no action submission, no hidden opposing cards, and no
counterfactual future. Pact option exposure is not a strategic need to counter.
"""
from __future__ import annotations
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import re
import zipfile

ROOT=Path("experiments/manual-transmission/research/cruise-sport-race-20261021")
INPUT=Path(os.environ.get("MANUAL_S3B_INPUT","sport-s3b-originals"))
OUTPUT=Path(os.environ.get("MANUAL_S3B_OUTPUT","sport-s3b-evidence"))
SEATS={f"manual-mirror-{i}" for i in range(4)}
PROFILE="manual-transmission-r5-survival-opt-in"
SPELL_CAST=re.compile(r"^SpellCastEvent\(spellEntityId=([^,]+), cardName=(.*?), casterId=([^,]+),")
LEFT_STACK=re.compile(r"^ZoneChangeEvent\(entityId=([^,]+), entityName=.*?, fromZone=STACK, toZone=([^,]+),")
RESOLVED=re.compile(r"^ResolvedEvent\(entityId=([^,]+), name=")

def guard(v, msg):
    if not v: raise AssertionError(msg)

def sha256(file):
    h=hashlib.sha256()
    with file.open("rb") as f:
        for buf in iter(lambda:f.read(1048576),b""):
            h.update(buf)
    return h.hexdigest()

def jsonlines(content):
    return [json.loads(s) for s in content.decode().splitlines() if s.strip()]

def original(spec):
    file=INPUT/f"{spec['cohort']}-{spec['arm']}-{spec['seed']}-original.zip"
    guard(file.is_file() and sha256(file)==spec["sha256"],f"Missing/changed original ZIP: {file}")
    outer="precision-r1-stage1-output/" if spec["cohort"]=="R1" else "precision-r6-prospective-output/"
    pre=outer+spec["arm"]+"-seed-"+spec["seed"]+"/"
    with zipfile.ZipFile(file) as z:
        def get(suffix):
            name=pre+suffix
            guard(z.namelist().count(name)==1,"Missing/ambiguous member: "+name)
            return z.read(name)
        guard(z.read(outer+"source-sha.txt").decode().strip()==spec["source_sha"],"Original source mismatch")
        summary=json.loads(get("summary.json"))
        binding=json.loads(get("r1-arm-binding.json"))
        opening=json.loads(get("initialization.json"))
        initial=json.loads(get("initial-state.json"))
        acts=jsonlines(get("actions.jsonl"))
        decisions=jsonlines(get("decisions.jsonl"))
    guard(str(summary["seed"])==spec["seed"] and summary["arm"]==spec["arm"],"Seed mismatch")
    guard(summary["status"]=="COMPLETE" and summary["gameOver"] is True and
          summary["error"] is None and summary["prefixOnly"] is False,"Not a completed original")
    guard(summary["accepted"]==summary["submitted"] and summary["accepted"]>=64,
          "Rejected original action")
    guard(summary["profile"]==PROFILE and binding["profile"]==PROFILE,"Original R5 pilot changed")
    guard(opening["openingPolicy"]==
          "Four seats keep seed-derived initial seven, no smoother or mulligans",
          "Original initial seven policy changed")
    guard(set(initial["turnOrder"])==SEATS,"Missing original mirror seats")
    guard(len(acts)==summary["accepted"]*2,"Original pair count invalid")
    submits,results=acts[::2],acts[1::2]
    for i,(a,b) in enumerate(zip(submits,results),1):
        guard(a["record"]=="submit" and b["record"]=="result" and a["index"]==b["index"]==i and
              b["error"] is None,"Original action/result gap")
    guard(results[-1]["gameOver"] is True and results[-1]["winner"]==summary["winner"],
          "Original terminal evidence changed")
    return summary,submits,results,decisions

def priority_map(submits,decisions):
    priority=[a for a in submits if a["pendingDecision"] is None]
    cursor=0
    out={}
    for d in decisions:
        if d["kind"]!="PRIORITY": continue
        guard(type(d["onOwnTurn"]) is bool and d["playerId"] in SEATS,
              "Original priority identity invalid")
        chosen=[x for x in d["options"] if x.get("chosen") is True]
        guard(len(chosen)==1,"Ambiguous chosen option")
        pick=chosen[0]
        hit=None
        for k in range(cursor,len(priority)):
            a=priority[k]
            if a["turn"]>d["turnNumber"]+1:break
            if (a["actor"]==d["playerId"] and a["turn"]==d["turnNumber"] and
                a["step"]==d["step"] and json.loads(a["action"])==pick["action"]):
                hit=a
                cursor=k+1
                break
        guard(hit is not None,"Priority cannot join to actual accepted original action")
        guard(hit["index"] not in out,"Duplicate original action mapped")
        out[hit["index"]]=d
    guard(len(out)==sum(d["kind"]=="PRIORITY" for d in decisions),"Original priority join incomplete")
    return out

def main():
    protocol=json.loads((ROOT/"SPORT_S3_PACT_STACK_ATTRIBUTION_PROTOCOL.json").read_text())
    manifest=json.loads((ROOT/"CRUISE_R2_ZONE_MANIFEST.json").read_text())
    guard(protocol["schema"]=="ARGENTUM_MANUAL_SPORT_S3_PACT_ORIGINAL_STACK_ATTRIBUTION_R1"
          and protocol["status"]=="FROZEN_BEFORE_PACT_STACK_ATTRIBUTION_EXECUTION",
          "No prospective S3B source-bound protocol")
    guard(protocol["safety"]["original_budget_used"]==10 and
          protocol["safety"]["remaining_reserved"]==2 and
          protocol["safety"]["no_new_full_games"] is True,"Full-game budget drift")
    guard(manifest["original_game_count"]==10 and len(manifest["archives"])==10
          and manifest["original_accepted_actions"]==33440 and
          manifest["original_priority_decisions_matched"]==7827,
          "Original immutable source universe changed")
    stats=Counter()
    target_counts=defaultdict(Counter)
    rows=[]
    original_per_game=[]
    for spec in manifest["archives"]:
        summary,submits,results,decisions=original(spec)
        joined=priority_map(submits,decisions)
        stats["games"]+=1
        stats["accepted_actions"]+=summary["accepted"]
        stats["matched_priorities"]+=len(joined)
        public_stack={}
        offers_this_game=0
        casts_this_game=0
        for a,b in zip(submits,results):
            index=a["index"]
            d=joined.get(index)
            if d is not None and d["onOwnTurn"] is False:
                options=[o for o in d["options"] if o.get("cardName")=="Pact of Negation" and
                         o.get("actionType")=="CastSpell"]
                if options:
                    offers_this_game+=1
                    stats["pact_offturn_windows"]+=1
                    chosen=[o for o in options if o.get("chosen") is True]
                    guard(len(chosen)<=1,"Multiple historical chosen Pacts in one decision")
                    stats["pact_chosen_windows" if chosen else "pact_not_chosen_windows"]+=1
                    unique_targets=set()
                    for o in options:
                        targets=o.get("action",{}).get("targets",[])
                        guard(len(targets)==1 and targets[0].get("type")=="Spell" and
                              isinstance(targets[0].get("spellEntityId"),str),
                              f"Pact option lacks exact stack-spell target {spec} {index}")
                        entity=targets[0]["spellEntityId"]
                        guard(entity in public_stack,
                              f"Pact option target lacks live prior public SpellCastEvent: {spec} action={index} target={entity}")
                        info=public_stack[entity]
                        guard(info["cast_index"]<index and info["caster"] in SEATS,
                              "Pact targets a nonexistent/future or unknown-caster spell")
                        key=(entity,info["cast_index"])
                        guard(key not in unique_targets,"Duplicate Pact target option in single decision")
                        unique_targets.add(key)
                        is_chosen=o.get("chosen") is True
                        is_own=info["caster"]==d["playerId"]
                        target_counts[info["card"]]["offers"]+=1
                        if is_chosen:target_counts[info["card"]]["chosen"]+=1
                        else:target_counts[info["card"]]["declined"]+=1
                        if is_own:stats["offered_pact_own_spell_options"]+=1
                        else:stats["offered_pact_other_seat_spell_options"]+=1
                        rows.append({
                          "cohort":spec["cohort"],"arm":spec["arm"],"seed":spec["seed"],
                          "offering_seat":d["playerId"],"original_action_index":index,
                          "step":a["step"],"turn":a["turn"],
                          "target_spell_entity":entity,
                          "target_spell":info["card"],"target_caster_seat":info["caster"],
                          "target_original_cast_index":info["cast_index"],
                          "same_seat_target":is_own,
                          "actually_chosen_pact":is_chosen,
                          "original_option_score":o.get("score"),
                          "original_option_note":o.get("note"),
                          "original_action_accepted":True
                        })
            action=json.loads(a["action"])
            if a.get("sourceName")=="Pact of Negation" and action.get("type")=="CastSpell":
                casts_this_game+=1
                stats["actual_pact_casts"]+=1
            # Modify only a local public-stack inventory, never replay or alter game state.
            for event in b["events"]:
                found=SPELL_CAST.match(event)
                if found:
                    entity,card,caster=found.groups()
                    public_stack[entity]={"card":card,"caster":caster,"cast_index":index}
                found=LEFT_STACK.match(event)
                if found:public_stack.pop(found.group(1),None)
                found=RESOLVED.match(event)
                if found:public_stack.pop(found.group(1),None)
        original_per_game.append({
          "cohort":spec["cohort"],"arm":spec["arm"],"seed":spec["seed"],
          "source_sha":spec["source_sha"],"original_zip_sha256":spec["sha256"],
          "aligned_priority":len(joined),"accepted_actions":summary["accepted"],
          "offturn_pact_windows":offers_this_game,"actual_pact_casts":casts_this_game
        })
    guard(stats["games"]==10 and stats["accepted_actions"]==33440 and stats["matched_priorities"]==7827,
          "Original sampled universe incomplete")
    guard(stats["pact_offturn_windows"]==150 and stats["pact_chosen_windows"]==13 and
          stats["pact_not_chosen_windows"]==137 and stats["actual_pact_casts"]==13,
          "Pact S3A attribution counts failed to reconcile")
    guard(sum(c["offers"] for c in target_counts.values())==len(rows),"Pact option row accounting mismatch")

    rows.sort(key=lambda x:(0 if x["cohort"]=="R1" else 1,x["seed"],
                            0 if x["arm"]=="control" else 1,x["original_action_index"],
                            x["offering_seat"],x["target_spell"]))
    unique_for_seat={(r["cohort"],r["seed"],r["arm"],r["offering_seat"],
                      r["target_original_cast_index"],r["target_spell_entity"]) for r in rows}
    unique_spell={(r["cohort"],r["seed"],r["arm"],
                   r["target_original_cast_index"],r["target_spell_entity"]) for r in rows}
    stats["pact_target_offers"]=len(rows)
    stats["unique_seat_target_spell_instances"]=len(unique_for_seat)
    stats["unique_target_spell_instances_across_seats"]=len(unique_spell)
    stats["repeated_target_offer_rows"]=len(rows)-len(unique_for_seat)
    first_by_target={name:next((r for r in rows if r["target_spell"]==name),None)
                     for name in sorted(target_counts)}
    pact_selected=[r for r in rows if r["actually_chosen_pact"]]
    guard(len(pact_selected)==13,"Thirteen original Pact selections must bind live spells")
    stats["actual_pact_self_counter_selected"]=sum(r["same_seat_target"] for r in pact_selected)
    stats["actual_pact_other_seat_counter_selected"]=13-stats["actual_pact_self_counter_selected"]
    report={
      "schema":"ARGENTUM_MANUAL_SPORT_S3_PACT_REAL_STACK_ATTRIBUTION_REPORT_R1",
      "disposition":"SOURCE_ONLY_NO_POLICY_VERDICT",
      "source_sha":os.environ.get("GITHUB_SHA","UNSPECIFIED"),
      "counts":dict(stats),"per_target_card":{name:dict(c) for name,c in sorted(target_counts.items())},
      "first_offer_by_target_spell":first_by_target,
      "original_games":original_per_game,
      "all_original_pact_options_with_public_stack_witness":rows,
      "selected_actual_pact_target_instances":pact_selected,
      "s1_independent_actual_next_upkeep_paid":"13/13",
      "source_scope":"R1/R6 mirrored R5; no independent cEDH opponents",
      "conclusion":"CLASSIFICATION_ONLY_NO_MISPLAY_OR_WINRATE_CAUSALITY",
      "limitations":[
        "Pact being offered to target an actual spell does not show that spell was dangerous.",
        "150 is an option-window count; one unresolved spell can be offered repeatedly to one or several seats.",
        "The chosen or declined original R5 option is not an independently replayed alternative.",
        "No hidden opponent hand/library, future opponent action, strategic lethal, or card swap is inferred.",
        "The source original games were dependent self-mirrors and previously analyzed.",
        "Pact's 13 original paid debts do not establish it is always safe to cast."
      ],
      "new_full_games":0,"reserved_original_games":2
    }
    OUTPUT.mkdir(exist_ok=True,parents=True)
    (OUTPUT/"SPORT_S3_PACT_REAL_STACK_ATTRIBUTION.json").write_text(
      json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    lines=["# SPORT S3B — Pact against real unresolved original stack spells",
           "",
           "**Read-only retrospective original source. No extra games, opponent predictions or policy changes.**",
           "",
           "| Metric | Source observation |","|---|---:|"]
    for name in ("pact_offturn_windows","pact_chosen_windows","pact_not_chosen_windows",
                 "pact_target_offers","unique_seat_target_spell_instances",
                 "unique_target_spell_instances_across_seats","repeated_target_offer_rows",
                 "offered_pact_own_spell_options","offered_pact_other_seat_spell_options",
                 "actual_pact_self_counter_selected","actual_pact_other_seat_counter_selected"):
        lines.append(f"| {name} | {stats[name]} |")
    lines.extend(["","| Actual stack target card | Option offers | Pacts chosen | Unchosen |",
                  "|---|---:|---:|---:|"])
    for card in sorted(target_counts):
        c=target_counts[card]
        lines.append(f"| {card} | {c['offers']} | {c['chosen']} | {c['declined']} |")
    lines.extend(["","**Scientific limitation:** Not choosing an offered counter is not a missed win.",
                  "Deduplicated target offers still do not prove threat severity or a better",
                  "decision under the opponent's real future responses. All original seats",
                  "used R5; the SPORT pilot stays unchanged. No full games were consumed.",""])
    (OUTPUT/"REPORT.md").write_text("\n".join(lines))
    print("MANUAL_SPORT_S3B_PACT_REAL_STACK_GREEN "+
          json.dumps(dict(stats),sort_keys=True)+" NO_NEW_GAMES")

if __name__=="__main__":
    main()
