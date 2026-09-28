#!/usr/bin/env python3
import json,pathlib,subprocess
EXPECTED_SOURCE="4f020ea2406e9bd6a87ec389685b48f15df4c5a3"
REQUIRED=["fun decideMulligan(","hasPayableEarlyDevelopmentLine(","guaranteedSecondLandAccess(","override fun chooseBottomCards(","coloredMismatch < 3 || castableEarly >= 2","keptHandSize <= 5"]
def main():
 source=pathlib.Path("ai/src/main/kotlin/com/wingedsheep/ai/engine/EngineAiPlayerController.kt").read_text()
 assert subprocess.check_output(["git","hash-object","ai/src/main/kotlin/com/wingedsheep/ai/engine/EngineAiPlayerController.kt"],text=True).strip()==EXPECTED_SOURCE
 for token in REQUIRED: assert token in source,token
 inv=json.loads(pathlib.Path("docs/experiments/pest-control/tier-one-monster-london-raw-predicate-inventory-20260928.json").read_text())
 assert len(inv["predicate_axes"])==10
 out=pathlib.Path("build/reports/pest-london-predicate-inventory");out.mkdir(parents=True,exist_ok=True)
 (out/"result.json").write_text(json.dumps({"source_blob":EXPECTED_SOURCE,"axes":[x["id"] for x in inv["predicate_axes"]],"result":"STATIC_INVENTORY_RECONCILED","behavioral_cases":0,"official_counters":0},indent=2)+"\n")
 print("PEST_LONDON_RAW_PREDICATE_INVENTORY_PASS")
if __name__=="__main__":main()
