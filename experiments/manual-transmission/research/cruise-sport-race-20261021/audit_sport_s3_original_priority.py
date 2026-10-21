#!/usr/bin/env python3
"""SPORT S3: source-pinned, exhaustive, read-only audit of original R5 priority.

This inventories actual ORIGINAL accepted actions and the ORIGINAL scored options;
it cannot tell whether an unchosen option would have survived opposing responses.
There is NO GameInitializer, action replay, simulation, policy or card modification.
"""
from __future__ import annotations
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import zipfile

ROOT = Path("experiments/manual-transmission/research/cruise-sport-race-20261021")
INPUT = Path(os.environ.get("MANUAL_S3_INPUT", "sport-s3-originals"))
OUTPUT = Path(os.environ.get("MANUAL_S3_OUTPUT", "sport-s3-evidence"))
SEATS = {f"manual-mirror-{i}" for i in range(4)}
PROFILE = "manual-transmission-r5-survival-opt-in"
CAST = "CastSpell"
ACTIVATE = "ActivateAbility"
PACT = "Pact of Negation"

def check(yes, reason):
    if not yes:
        raise AssertionError(reason)

def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def jsonl(data):
    return [json.loads(x) for x in data.decode().splitlines() if x.strip()]

def original(spec):
    fn = INPUT / f"{spec['cohort']}-{spec['arm']}-{spec['seed']}-original.zip"
    check(fn.is_file(), f"Original archive missing: {fn}")
    check(sha256(fn) == spec["sha256"], f"Original ZIP SHA-256 mismatch: {fn}")
    outer = "precision-r1-stage1-output/" if spec["cohort"] == "R1" else "precision-r6-prospective-output/"
    prefix = outer + spec["arm"] + "-seed-" + spec["seed"] + "/"
    with zipfile.ZipFile(fn) as z:
        def one(path):
            check(z.namelist().count(path) == 1, f"Archive member duplicate/missing: {path}")
            return z.read(path)
        check(one(outer + "source-sha.txt").decode().strip() == spec["source_sha"], "Original source SHA drift")
        summary = json.loads(one(prefix + "summary.json"))
        bind = json.loads(one(prefix + "r1-arm-binding.json"))
        initialization = json.loads(one(prefix + "initialization.json"))
        initial = json.loads(one(prefix + "initial-state.json"))
        pairs = jsonl(one(prefix + "actions.jsonl"))
        decisions = jsonl(one(prefix + "decisions.jsonl"))
    check(str(summary["seed"]) == spec["seed"] and summary["arm"] == spec["arm"], "Seed/arm mismatch")
    check(summary["profile"] == PROFILE and bind["profile"] == PROFILE, "R5 policy not source-bound")
    check(summary["status"] == "COMPLETE" and summary["gameOver"] is True
          and summary["error"] is None and summary["prefixOnly"] is False,
          "Incomplete original full game")
    check(summary["accepted"] == summary["submitted"] and summary["accepted"] >= 64,
          "Missing or rejected original action")
    check(initialization["openingPolicy"] ==
          "Four seats keep seed-derived initial seven, no smoother or mulligans",
          "Original initial policy changed")
    check(set(initial["turnOrder"]) == SEATS, "Original four seats changed")
    check(len(pairs) == 2 * summary["accepted"], "Original accepted stream length mismatch")
    submitted, results = pairs[::2], pairs[1::2]
    for i, (a, b) in enumerate(zip(submitted, results), 1):
        check(a["record"] == "submit" and b["record"] == "result"
              and a["index"] == b["index"] == i and b["error"] is None,
              f"Original submitted/accepted action mismatch: {spec} {i}")
    check(results[-1]["gameOver"] is True and results[-1]["winner"] == summary["winner"],
          "Original game termination mismatch")
    return summary, submitted, results, decisions

def main():
    protocol = json.loads((ROOT / "SPORT_S3_ORIGINAL_PRIORITY_INVENTORY_PROTOCOL.json").read_text())
    manifest = json.loads((ROOT / "CRUISE_R2_ZONE_MANIFEST.json").read_text())
    check(protocol["schema"] == "ARGENTUM_MANUAL_SPORT_S3_REACTIVE_PRIORITY_INVENTORY_R1"
          and protocol["status"] == "PREDECLARED_BEFORE_S3_ARCHIVE_INVENTORY_EXECUTION",
          "SPORT S3 prospective protocol missing")
    check(manifest["schema"] == "MANUAL_CRUISE_R2_ORIGINAL_ZONE_AUDIT_V1", "Original R2 manifest invalid")
    check(manifest["budget"] == dict(used=10, max=12, reserved=2, new_games=0), "Original game budget altered")
    check(len(manifest["archives"]) == 10, "Missing original ten-game universe")
    watch = protocol["card_watchlist_by_purpose"]
    categories = {name: purpose for purpose, names in watch.items() for name in names}
    check(sum(map(len, watch.values())) == len(categories), "Overlapping watchlist classes")
    deck = Path("experiments/manual-transmission/control/v0.7.decklist.txt").read_text()
    fixed_cards = {line[2:] for line in deck.splitlines() if line.startswith("1 ")}
    check(set(categories) <= fixed_cards, f"Watched cards not in original v0.7: {set(categories)-fixed_cards}")
    check(protocol["boundaries"]["full_game_budget"] == dict(used=10, max=12, reserved=2, new=0),
          "Game budget permission changed")
    check(protocol["parent_s2_evidence"]["card_mechanics_run"] == 39677142827, "Wrong S2 integration")
    check(protocol["originals_expected"]["matched_priority"] == 7827, "Predeclared original sample drift")

    global_stats = Counter()
    card_stats = defaultdict(Counter)
    arm_stats = defaultdict(Counter)
    seat_stats = defaultdict(Counter)
    candidate_rows = []
    run_rows = []
    for spec in manifest["archives"]:
        summary, submits, results, decisions = original(spec)
        global_stats["original_games"] += 1
        global_stats["original_accepted_actions"] += summary["accepted"]
        arm_stats[spec["arm"]]["accepted_actions"] += summary["accepted"]
        pact_casts = sum(1 for a in submits
                         if a.get("sourceName") == PACT
                         and json.loads(a["action"]).get("type") == CAST)
        global_stats["historical_pact_casts"] += pact_casts
        priority = [a for a in submits if a["pendingDecision"] is None]
        cursor = 0
        matched = 0
        total_off = 0
        for d in decisions:
            if d["kind"] != "PRIORITY":
                continue
            check(type(d["onOwnTurn"]) is bool, "Historical own-turn evidence untyped")
            check(d["playerId"] in SEATS, "Decision maker not an original seat")
            chosen = [o for o in d["options"] if o.get("chosen") is True]
            check(len(chosen) == 1, "Original priority chooser ambiguous")
            picked = chosen[0]
            hit = None
            for k in range(cursor, len(priority)):
                a = priority[k]
                if a["turn"] > d["turnNumber"] + 1:
                    break
                if a["actor"] == d["playerId"] and a["turn"] == d["turnNumber"] and \
                        a["step"] == d["step"] and json.loads(a["action"]) == picked["action"]:
                    hit = a
                    cursor = k + 1
                    break
            check(hit is not None, f"Priority decision failed original accepted-action join: {spec}")
            matched += 1
            global_stats["matched_priority_decisions"] += 1
            is_off = not d["onOwnTurn"]
            global_stats["offturn_priority" if is_off else "ownturn_priority"] += 1
            arm_stats[spec["arm"]]["offturn_priority" if is_off else "ownturn_priority"] += 1
            seat_stats[d["playerId"]]["offturn_priority" if is_off else "ownturn_priority"] += 1
            if is_off:
                total_off += 1
            offered = {}
            for option in d["options"]:
                name = option.get("cardName")
                typ = option.get("actionType")
                if name in categories and typ in (CAST, ACTIVATE):
                    offered.setdefault(name, set()).add(typ)
            if not offered:
                if is_off:
                    global_stats["offturn_without_watched_offer"] += 1
                continue
            global_stats["offturn_with_watched_offer" if is_off else "ownturn_with_watched_offer"] += 1
            if is_off:
                arm_stats[spec["arm"]]["offturn_with_watched_offer"] += 1
            picked_name = picked.get("cardName")
            picked_type = picked.get("actionType")
            chosen_watched = picked_name in offered and picked_type in offered.get(picked_name, set())
            if is_off and chosen_watched:
                global_stats["offturn_chose_watched_action"] += 1
            elif is_off:
                global_stats["offturn_declined_watched_options"] += 1
            for card, types in offered.items():
                card_stats[card]["offturn_offered" if is_off else "ownturn_offered"] += 1
                if is_off and chosen_watched and card == picked_name:
                    card_stats[card]["offturn_chosen"] += 1
                elif is_off:
                    card_stats[card]["offturn_not_chosen"] += 1
                candidate_rows.append(dict(
                    cohort=spec["cohort"], seed=spec["seed"], arm=spec["arm"],
                    player=d["playerId"], action_index=hit["index"],
                    turn=hit["turn"], step=hit["step"], off_turn=is_off,
                    watched_card=card, offered_action_types=sorted(types),
                    chosen_watched_card=bool(chosen_watched and card == picked_name),
                    chosen_action_type=picked_type,
                    chosen_card=picked_name if picked_name in categories else None,
                    chosen_original_label=d.get("chosenLabel"),
                    original_action_accepted=True,
                    opportunity_qualification="ORIGINAL_SCORE_OPTION_NOT_COUNTERFACTUAL",
                ))
        check(matched == sum(d["kind"] == "PRIORITY" for d in decisions), "Missing priority evidence")
        run_rows.append(dict(cohort=spec["cohort"],seed=spec["seed"],arm=spec["arm"],
            original_zip_sha256=spec["sha256"],source_sha=spec["source_sha"],
            accepted=summary["accepted"],aligned_priority=matched,offturn_priority=total_off,
            actual_pact_casts=pact_casts))
    check(global_stats["original_games"] == protocol["originals_expected"]["games"], "Game count drift")
    check(global_stats["original_accepted_actions"] == protocol["originals_expected"]["actions"], "Action count drift")
    check(global_stats["matched_priority_decisions"] == protocol["originals_expected"]["matched_priority"],
          "Matched priority count drift")
    check(global_stats["historical_pact_casts"] == protocol["originals_expected"]["pact_casts"],
          "Pact cast count contradicts independent S1")
    check(global_stats["offturn_priority"] + global_stats["ownturn_priority"] == 7827,
          "Priority decision accounting gap")
    check(global_stats["offturn_with_watched_offer"] +
          global_stats["offturn_without_watched_offer"] == global_stats["offturn_priority"],
          "Offturn category accounting gap")
    check(global_stats["offturn_declined_watched_options"] +
          global_stats["offturn_chose_watched_action"] == global_stats["offturn_with_watched_offer"],
          "Offturn chosen-vs-declined accounting gap")

    # Stable source-only selection: never rank by winner, payoff or candidate model.
    candidate_rows.sort(key=lambda r: (0 if r["cohort"] == "R1" else 1,
        r["seed"], 0 if r["arm"]=="control" else 1, r["action_index"],
        r["player"], r["watched_card"]))
    first_by_card = {}
    for card in categories:
        first_by_card[card] = next((row for row in candidate_rows
                                   if row["off_turn"] and row["watched_card"] == card), None)
    outputs = dict(
        schema="ARGENTUM_MANUAL_SPORT_S3_ALL_ORIGINAL_PRIORITY_SOURCE_REPORT_V1",
        qualification="SOURCE_ONLY_REACTIVE_OFFER_INVENTORY_NOT_STRATEGY_VERDICT",
        branch_sha=os.environ.get("GITHUB_SHA","UNSPECIFIED"),
        observation_universe="TEN_ORIGINAL_FOUR_SEAT_R1_R6_DEPENDENT_MIRROR_GAMES",
        counts=dict(global_stats),
        original_games=run_rows,
        off_turn_card_stats={card:dict(card_stats[card]) for card in categories},
        by_arm={k:dict(v) for k,v in sorted(arm_stats.items())},
        by_seat={k:dict(v) for k,v in sorted(seat_stats.items())},
        earliest_offturn_by_card=first_by_card,
        all_watched_offer_rows=candidate_rows,
        historical_pact_next_own_upkeep_paid_from_separate_s1="13/13",
        no_hidden_opponent_cards_used=True,
        source_only_no_replay=True,
        no_new_games=True,
        remaining_precision_games=2,
        method_limits=[
            "An offered action in a historical scorer is not an independently re-executed legal action.",
            "Off-turn does not prove an opposing threat or spell on stack.",
            "Declining an offered Pact or removal card is NOT automatically a policy mistake.",
            "All original seats ran the same R5 AI policy; no diverse competitive opponent pilot.",
            "Source-only options and observed choices cannot prove the winning value of alternatives.",
            "Historical samples are dependent nonblind original games, not fresh competition."
        ]
    )
    OUTPUT.mkdir(exist_ok=True, parents=True)
    (OUTPUT / "SPORT_S3_ALL_ORIGINAL_PRIORITY.json").write_text(
        json.dumps(outputs,indent=2,ensure_ascii=False)+"\n")
    lines = [
      "# SPORT S3 — all original R5 reactive priority decisions",
      "",
      "**Read-only ten matched original games. No new plays, outcome predictions, deck or profile changes.**",
      "",
      "| Historical measurement | Observed |",
      "|---|---:|",
    ]
    for key in ("original_games","original_accepted_actions","matched_priority_decisions",
                "offturn_priority","offturn_with_watched_offer","offturn_chose_watched_action",
                "offturn_declined_watched_options","historical_pact_casts"):
        lines.append(f"| {key} | {global_stats[key]} |")
    lines.extend(["", "| Watched card | Offturn offered | Chosen | Not chosen |",
                  "|---|---:|---:|---:|"])
    for card in sorted(categories):
        c=card_stats[card]
        lines.append(f"| {card} | {c['offturn_offered']} | {c['offturn_chosen']} | {c['offturn_not_chosen']} |")
    lines.extend(["", "**Interpretation:** These are observed eligible *scored options*, not proof",
       "that using them was necessary, legal on replay, survival-improving or a missed win.",
       "All four seats were the same R5 mirror pilot. SPORT policy remains unchanged.",
       "The 13 actual Pact casts were checked separately by S1 and paid at next upkeep.",
       "Two reserved full-game slots remain unspent. SPORT S3 is not a competitive rating.",
       ""])
    (OUTPUT / "REPORT.md").write_text("\n".join(lines))
    print("MANUAL_SPORT_S3_INVENTORY_SOURCE_GREEN "+
          json.dumps(dict(global_stats),sort_keys=True)+
          " WATCHED_ROWS="+str(len(candidate_rows))+" NO_NEW_GAMES")

if __name__ == "__main__":
    main()
