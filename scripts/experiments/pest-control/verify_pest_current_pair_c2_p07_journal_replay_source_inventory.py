#!/usr/bin/env python3
import json, pathlib, subprocess, sys

PARENT="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"

def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()

def die(msg):
    raise SystemExit(msg)

def main():
    if len(sys.argv) != 2:
        die("usage: verifier MANIFEST")
    d=json.loads(pathlib.Path(sys.argv[1]).read_text())
    if d.get("schema")!="pest-current-pair-c2-p07-journal-replay-source-inventory-v1": die("schema")
    if d.get("authority")!="SOURCE_ONLY_NO_OFFICIAL_CLAIM_NO_C2_ADMISSION_NO_GAMEPLAY": die("authority")
    if git("rev-parse","HEAD^")!=PARENT: die("candidate parent")
    expected=sorted([
      ".github/workflows/pest-c2-p07-journal-replay-source-inventory-20261005.yml",
      "docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_P07_JOURNAL_REPLAY_SOURCE_INVENTORY_20261005.json",
      "scripts/experiments/pest-control/verify_pest_current_pair_c2_p07_journal_replay_source_inventory.py",
    ])
    actual=sorted(x for x in git("diff","--name-only",PARENT+"..HEAD").splitlines() if x)
    if actual!=expected: die(f"unexpected delta {actual}")
    for path,blob in d["protected_blobs"].items():
        if git("rev-parse","HEAD:"+path)!=blob: die("protected blob drift "+path)

    phase=pathlib.Path("gym/src/main/kotlin/com/wingedsheep/gym/pest/PestPhaseBComparisonJournal.kt").read_text()
    op=pathlib.Path("gym/src/main/kotlin/com/wingedsheep/gym/matchup/PestControlTierOneMonsterTronOperationalStack.kt").read_text()
    old=json.loads(pathlib.Path("docs/experiments/pest-control/goldfish-sample-1-fresh-regression-replay-accepted.json").read_text())

    for marker in ('append("INTENT"', 'append("RESULT"', 'append("EXCEPTION"', '"PEST_MONSTER_PHASE_B_KEEP_BOTTOM_ONLY"'):
        if marker not in phase: die("phase journal marker missing "+marker)
    for marker in ("ATTEMPT_DURABLY_RECORDED","INITIALIZATION_ENTERED","RECORD_DURABLY_WRITTEN","MonsterTronActionEvidencePhase.INTENT","MonsterTronActionEvidencePhase.RESULT"):
        if marker not in op: die("operational durability marker missing "+marker)
    if old.get("deckVersion")!="Pest Control v1.0": die("historical replay identity")
    if old.get("agentProfile")!="production-candidate-expiring": die("historical replay profile")
    if len(old.get("seeds",[]))!=30 or len(old.get("games",[]))!=30: die("historical replay count")
    if d["historical_replay_disposition"]["disposition"]!="INCOMPATIBLE_WITH_EXACT_CURRENT_PEST_MONSTER_C2_PAIR_REPLAY":
        die("historical replay must remain incompatible")
    if d["c2p07"]["gate_status"].startswith("SATISFIED"): die("C2P07 overpromotion")
    if d.get("official_counters")!={"allocations":0,"claims":0,"games":0,"outcomes":0}: die("official counters")
    print(json.dumps({
      "schema":"pest-current-pair-c2-p07-journal-replay-source-inventory-result-v1",
      "status":"PASS",
      "head":git("rev-parse","HEAD"),
      "tree":git("rev-parse","HEAD^{tree}"),
      "historical_replay_seed_count":30,
      "historical_replay_game_count":30,
      "historical_replay_disposition":"INCOMPATIBLE_PREDECESSOR_ONLY",
      "c2p07":d["c2p07"]["gate_status"],
      "official_counters_delta":0,
    },sort_keys=True))
if __name__=="__main__":
    main()
