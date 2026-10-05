#!/usr/bin/env python3
import json, pathlib, re, subprocess, sys

PARENT="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
TREE="e7c37f620c8cefa0352742e38635ca196e887637"

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def die(msg):
    raise SystemExit(msg)

def linked_map(path, marker):
    text=pathlib.Path(path).read_text()
    start=text.index(marker)
    tail=text[start:]
    end=tail.index("\n)")
    block=tail[:end]
    rows=re.findall(r'"([^"]+)"\s+to\s+(\d+)', block)
    out={}
    for name,count in rows:
        if name in out: die(f"duplicate deck row {name}")
        out[name]=int(count)
    return out

def main():
    if len(sys.argv)!=2: die("usage: verifier MANIFEST")
    p=pathlib.Path(sys.argv[1]); d=json.loads(p.read_text())
    if d.get("schema")!="pest-current-pair-c2-p03-p04-source-rebind-v1": die("schema")
    if d.get("status")!="CURRENT_CARD_SOURCE_REBOUND__RUNTIME_REACHABILITY_VARIANTS_OPEN": die("status")
    if d.get("authority")!="SOURCE_INVENTORY_ONLY_NO_C2_ADMISSION_NO_GAMEPLAY": die("authority")
    if d.get("source_parent")!={"commit":PARENT,"tree":TREE}: die("source parent")
    if git("rev-parse","HEAD^")!=PARENT: die("candidate parent")
    expected_delta=sorted([
      ".github/workflows/pest-current-pair-c2-p03-p04-source-rebind-20261005.yml",
      "docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_P03_P04_SOURCE_REBIND_20261005.json",
      "scripts/experiments/pest-control/verify_pest_current_pair_c2_p03_p04_source_rebind.py",
    ])
    actual=sorted(x for x in git("diff","--name-only",f"{PARENT}..HEAD").splitlines() if x)
    if actual!=expected_delta: die(f"unexpected delta {actual}")

    decks={x["seat_policy"]:x for x in d["decks"]}
    pest=linked_map(pathlib.Path("gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlPreboardContract.kt"),"private val PEST_MAIN = linkedMapOf(")
    monster=linked_map(pathlib.Path("gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronAdmission.kt"),"private val MONSTER_TRON_MAIN = linkedMapOf(")
    for key,source in (("pest",pest),("monster",monster)):
        inv={row["name"]:row["count"] for row in decks[key]["cards"]}
        if inv!=source: die(f"{key} exact main inventory mismatch")

    nonbasic=0
    for deck in d["decks"]:
        for row in deck["cards"]:
            blob=row.get("git_blob")
            path=row.get("definition_path")
            if blob is None:
                if path!="basic land registry": die(f"null nonbasic source {row['name']}")
                continue
            nonbasic+=1
            if git("rev-parse",f"HEAD:{path}")!=blob:
                die(f"definition drift {deck['seat_policy']} {row['name']}")

    if nonbasic!=33: die(f"expected 33 nonbasic rows, got {nonbasic}")
    for path,blob in d["pilot_and_projection_source"].items():
        if git("rev-parse",f"HEAD:{path}")!=blob: die(f"pilot/projection drift {path}")
    for path,blob in d["current_monster_policy_source"].items():
        if git("rev-parse",f"HEAD:{path}")!=blob: die(f"monster policy drift {path}")

    drift=d["rebound_summary"]["observation_adapter_drift"]
    if drift["historical"]==drift["current"]: die("ObservationAdapter drift must remain explicit")
    if d["lane_disposition"]["C2P03"].startswith("SATISFIED") or d["lane_disposition"]["C2P04"].startswith("SATISFIED"):
        die("P03/P04 may not be promoted by a source inventory")

    expected_actions={"ActivateAbility","CastSpell","DeclareAttackers","DeclareBlockers","PassPriority","PlayLand"}
    if set(d["current_projection_surface"]["priority_action_classes"])!=expected_actions: die("action surface")
    expected_pending={
      "AssignDamageDecision","BatchYesNoDecision","BudgetModalDecision","ChooseColorDecision",
      "ChooseModeDecision","ChooseNumberDecision","ChooseOptionDecision","ChooseReplacementDecision",
      "ChooseTargetsDecision","CombatResolutionDecision","DistributeDecision","OrderObjectsDecision",
      "ReorderLibraryDecision","SearchLibraryDecision","SelectCardsDecision","SelectManaSourcesDecision",
      "SplitPilesDecision","YesNoDecision"
    }
    if set(d["current_projection_surface"]["pending_decision_classes"])!=expected_pending: die("pending surface")
    if d.get("official_counters")!={"allocations":0,"claims":0,"games":0,"outcomes":0}: die("counters")

    print(json.dumps({
      "schema":"pest-current-pair-c2-p03-p04-source-rebind-result-v1",
      "status":"PASS",
      "head":git("rev-parse","HEAD"),
      "tree":git("rev-parse","HEAD^{tree}"),
      "pest_cards":len(pest),
      "monster_cards":len(monster),
      "non_basic_definition_rows_rebound":nonbasic,
      "observation_adapter_current":d["pilot_and_projection_source"]["gym/src/main/kotlin/com/wingedsheep/gym/actorinput/ObservationAdapter.kt"],
      "c2p03":d["lane_disposition"]["C2P03"],
      "c2p04":d["lane_disposition"]["C2P04"],
      "official_counters_delta":0,
    },sort_keys=True))

if __name__=="__main__":
    main()
