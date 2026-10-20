#!/usr/bin/env python3
"""Read-only R22 telemetry from original ZIPs; no games, policies or deck changes."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import zipfile

BASE = Path(__file__).resolve().parents[3]
EXPECTED = {
 ("202610191621", "control"): ("control-202610191621.zip","d6a88192418856f1e5d10f6e50410dd9dafaba209c1637c4a639bbd51f7d0175"),
 ("202610191621", "challenger"): ("challenger-202610191621.zip","659afd207d381645bd1b0363b3a9c0eb7ac5e95c3e3f3e4da3d7abdd092887ce"),
 ("202610191622", "control"): ("control-202610191622.zip","418d9d30cef71a4d1c025d9986294f55c8be8d295555cbbaa3fc3a27c5d2ad22"),
 ("202610191622", "challenger"): ("challenger-202610191622.zip","368f5d44d15c4869278d8c370e7e3f8fc37f8f45cfd81e2420ecde4daa95aece"),
}
PROFILE = "manual-transmission-r5-survival-opt-in"
WINNERS = {"202610191621":"manual-mirror-0","202610191622":"manual-mirror-3"}
ROLE_NAMES = {
 "Cruise-example-value-casts": {"Elvish Visionary","Wall of Blossoms","Wood Elves","Farhaven Elf","Beast Whisperer","Soul of the Harvest","Coiling Oracle","Aether Channeler"},
 "Sport-example-toolbox-casts": {"Foundation Breaker","Amphin Mutineer","Bane of Progress","Tishana’s Tidebinder","World Breaker","Pongify","Rapid Hybridization","Reality Shift","Chaos Warp"},
 "Race-example-combo-casts": {"Ancestral Statue","Walking Ballista","Purphoros, God of the Forge","Imperial Recruiter","Fauna Shaman","Chord of Calling"},
}

def ensure(ok,msg):
 if not ok: raise ValueError(msg)

def classify_action_names(submits):
 counts={k:Counter() for k in ROLE_NAMES}
 for row in submits:
  action=json.loads(row["action"])
  if action.get("type") != "CastSpell": continue
  name=row.get("sourceName")
  for role,names in ROLE_NAMES.items():
   if name in names: counts[role][name]+=1
 return {role:{"total_casts":sum(c.values()),"names":dict(sorted(c.items()))} for role,c in counts.items()}

def first_divergence(left,right):
 for a,b in zip(left,right):
  if (a["actor"],a.get("sourceName"),a["action"]) != (b["actor"],b.get("sourceName"),b["action"]):
   return a["index"]
 return None if len(left)==len(right) else min(len(left),len(right))+1

def validate_summary(s,seed,arm):
 ensure(s.get("status")=="COMPLETE" and s.get("gameOver") is True,"censored or incomplete game")
 ensure(s.get("error") is None and s.get("prefixOnly") is False,"error or prefix-only game")
 ensure(s.get("seed")==int(seed) and s.get("arm")==arm,"seed/arm mismatch")
 ensure(s.get("accepted")==s.get("submitted") and s["accepted"]>=64,"accepted/submitted mismatch")
 ensure(s.get("winner")==WINNERS[seed],"original winner mismatch")
 ensure(s.get("profile")==PROFILE and s.get("profilesBySeat")==[PROFILE]*4,"unexpected profile")
 for field in ("commanderCasts","firstCommanderPersonalTurn","legalCommanderOpportunityDecisions","legalCommanderDeclinedDecisions"):
  ensure(isinstance(s.get(field),dict),"missing telemetry field "+field)

def load_game(root,seed,arm):
 name,wanted=EXPECTED[(seed,arm)]
 data=(root/name).read_bytes()
 ensure(hashlib.sha256(data).hexdigest()==wanted,"original ZIP checksum mismatch: "+name)
 with zipfile.ZipFile(root/name) as z:
  ensure(z.testzip() is None,"corrupted original ZIP")
  matches=[x for x in z.namelist() if x.endswith("/summary.json") and Path(x).parent.name==f"{arm}-seed-{seed}"]
  ensure(len(matches)==1,"missing full original summary")
  prefix=matches[0][:-len("summary.json")]
  summary=json.loads(z.read(prefix+"summary.json"))
  binding=json.loads(z.read(prefix+"r22-arm-binding.json"))
  actions=[json.loads(l) for l in z.read(prefix+"actions.jsonl").decode().splitlines() if l.strip()]
 validate_summary(summary,seed,arm)
 ensure(binding.get("oneCardSwapVerified") is True and binding.get("matchedSlotPreflightVerified") is True,"bad arm binding")
 ensure(binding.get("automaticAdoption") is False and binding.get("r7CombinedAdvisorAbsent") is True,"unaccepted policy")
 ensure(binding.get("profile")==PROFILE,"binding profile mismatch")
 submits=[a for a in actions if a.get("record")=="submit"]
 results=[a for a in actions if a.get("record")=="result"]
 ids=list(range(1,summary["accepted"]+1))
 ensure([r["index"] for r in submits]==ids and [r["index"] for r in results]==ids,"missing action records")
 ensure(all(r.get("error") is None for r in results),"engine error in accepted result")
 return {"summary":summary,"submits":submits,"digest":wanted,"seed":seed,"arm":arm}

def audit(originals):
 games={k:load_game(originals,*k) for k in EXPECTED}
 rows=[]
 for (seed,arm),g in games.items():
  s=g["summary"]
  rows.append({
   "seed":seed,"arm":arm,"zip_sha256":g["digest"],"winner":s["winner"],
   "terminal_global_turn":s["playerTurnNumber"],"accepted_actions":s["accepted"],
   "first_commander_personal_turn_by_seat":s["firstCommanderPersonalTurn"],
   "commander_casts_by_seat":s["commanderCasts"],
   "legal_commander_opportunities_by_seat":s["legalCommanderOpportunityDecisions"],
   "legal_commander_declines_by_seat":s["legalCommanderDeclinedDecisions"],
   "scored_commander_opportunities_by_seat":s["scoredCommanderOpportunityDecisions"],
   "scored_commander_declines_by_seat":s["scoredCommanderDeclinedDecisions"],
   "observed_role_example_casts_NOT_policy_gears":classify_action_names(g["submits"]),
   "wan_cast_count":s["wanCastCount"],"wan_cast_x_values":s["wanCastXs"],
   "observed_failed_draw_events":s["observedFailedDraws"],
   "combo_relevant_action_count_not_wins":s["comboRelevantActionCount"],
  })
 pairs=[]
 for seed in WINNERS:
  ctrl,wan=games[(seed,"control")],games[(seed,"challenger")]
  diff=first_divergence(ctrl["submits"],wan["submits"])
  ensure(diff==(None if seed=="202610191621" else 1892),"unexpected original first action divergence")
  pairs.append({"seed":seed,"first_action_divergence":diff,"same_winner":ctrl["summary"]["winner"]==wan["summary"]["winner"]})
 return {"status":"PASS_R22_READ_ONLY_DESCRIPTIVE_TELEMETRY",
  "original_run_id":39435937911,"original_source_sha":"10675d8dc3133294c035606f656f9e4d12d50939",
  "original_completed_games_inspected":4,"unique_gameplay_seeds":2,"games_initialized_or_replayed":0,
  "tested_policy":"R5 only; NOT qualified Cruise/Sport/Race live policies",
  "new_defect_proven":False,"causal_upgrade_proven":False,"win_rate_claim_authorized":False,
  "card_changes":0,"CURRENT_changes":0,"promotions":0,
  "per_game":rows,"pairs":pairs,
  "next":"Do not infer flawed scoring from declines. Require exact-state new defect witness or live three-gear capability."}

def main():
 p=argparse.ArgumentParser()
 p.add_argument("--originals",type=Path,required=True)
 p.add_argument("--output",type=Path,required=True)
 a=p.parse_args()
 report=audit(a.originals)
 a.output.parent.mkdir(parents=True,exist_ok=True)
 a.output.write_text(json.dumps(report,sort_keys=True,indent=2)+"\n")
 print("R2_ORIGINAL_FOUR_GAMES_PASS new_games=0 promotions=0")
 for x in report["per_game"]:
  print("R2_GAME",x["seed"],x["arm"],"actions",x["accepted_actions"],
   "first_animar",json.dumps(x["first_commander_personal_turn_by_seat"],sort_keys=True),
   "legal_declines",json.dumps(x["legal_commander_declines_by_seat"],sort_keys=True),
   "role_casts",json.dumps({k:v["total_casts"] for k,v in x["observed_role_example_casts_NOT_policy_gears"].items()},sort_keys=True))
 for p in report["pairs"]: print("R2_PAIR",p)
if __name__=="__main__": main()
