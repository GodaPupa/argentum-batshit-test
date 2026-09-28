#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,pathlib,subprocess
REF="3a4f99a7653839506e96d19e6639f58d9e8c5ced"
EXPECTED={
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactControlPilot.kt":"3f92f97465ae3c39ce4d31b57a8f4215a802412510f251857059d4101c377251",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactCombatPlanner.kt":"72ba6cd82c049bc9bc04b499785f343e2e67d392c2570d48f7198b85b37cf193",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactControlPolicyTest.kt":"4cbe939d64505a4a31b87a5357e76a57914d5ac7867fe268ea37338bee29ada5",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/RedMadnessPilot.kt":"bfafcdf95f35035462cec6959dcb554fbc9b4fd17083252027bb68aeca8c843e",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/RedMadnessPilotScenarioTest.kt":"df97c95e45612875f180dd12007f2cb1ece17d99fb49b0b50674f313d8d63632",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ActorPublicCards.kt":"78aca8e15df0914ae64c422b4f7a9df0d20da3c92e769e7c1be55fa3d3dda2f2",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ActorChoiceSupport.kt":"46336b46aded04033164b11d490ccb6ad455f6942f96b9486dc8074a6f81cc57",
"gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FirstCellCanonicalFixturePool.kt":"e720343eaf0f11c6f460bae139ae3cfcb4efa4829eddecdc5ccc8095b095afc2",
}
def git(*a): return subprocess.check_output(["git",*a],text=False).strip()
def main():
 out=pathlib.Path("build/reports/ferocity-policy-byte-binding");out.mkdir(parents=True,exist_ok=True)
 subprocess.run(["git","fetch","--no-tags","origin",REF],check=True,stdout=subprocess.DEVNULL)
 actual={}
 for p,h in EXPECTED.items():
  data=subprocess.check_output(["git","show",f"{REF}:{p}"])
  got=hashlib.sha256(data).hexdigest();actual[p]=got
  # Differences are the subject of this changed-source diagnostic; record all of them.
  actual[p]=got
 result={"schema":"ferocity-source08-policy-byte-binding-v1","source08":REF,"expected":EXPECTED,"actual":actual,
 "matches":{p:(actual[p]==h) for p,h in EXPECTED.items()},
 "differences":{p:{"archive":h,"source08":actual[p]} for p,h in EXPECTED.items() if actual[p]!=h},
 "artifact_policy":{"pilot_archive":EXPECTED["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactControlPilot.kt"],"pilot_source08":actual["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactControlPilot.kt"],"bank_archive":EXPECTED["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactControlPolicyTest.kt"],"bank_source08":actual["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/ArtifactControlPolicyTest.kt"]},
 "red_policy":{"pilot_archive":EXPECTED["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/RedMadnessPilot.kt"],"pilot_source08":actual["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/RedMadnessPilot.kt"],"bank_archive":EXPECTED["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/RedMadnessPilotScenarioTest.kt"],"bank_source08":actual["gym/src/test/kotlin/com/wingedsheep/gym/ferocity/RedMadnessPilotScenarioTest.kt"]},
 "new_jvms":0,"new_entropy":0,"new_games":0,"new_calibration_commands":0,"result":"DIAGNOSTIC_COMPLETE_REQUIRES_INDEPENDENT_DISPOSITION"}
 (out/"result.json").write_text(json.dumps(result,indent=2)+"\n")
 print(json.dumps({"differences":result["differences"],"matched":sum(result["matches"].values()),"total":len(EXPECTED)},indent=2))
if __name__=="__main__":main()
