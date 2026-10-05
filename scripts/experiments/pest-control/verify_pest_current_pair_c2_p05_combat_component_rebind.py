#!/usr/bin/env python3
import hashlib, json, pathlib, subprocess, sys

PARENT="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
TREE="e7c37f620c8cefa0352742e38635ca196e887637"

def git(*args): return subprocess.check_output(["git",*args],text=True).strip()
def sha(path): return hashlib.sha256(pathlib.Path(path).read_bytes()).hexdigest()
def die(msg): raise SystemExit(msg)

def main():
    if len(sys.argv)!=2: die("usage: verifier MANIFEST")
    d=json.loads(pathlib.Path(sys.argv[1]).read_text())
    if d.get("schema")!="pest-current-pair-c2-p05-combat-component-rebind-v1": die("schema")
    if d.get("authority")!="SOURCE_REBIND_ONLY_NO_C2_ADMISSION_NO_GAMEPLAY": die("authority")
    if d.get("source_parent")!={"commit":PARENT,"tree":TREE}: die("parent")
    if git("rev-parse","HEAD^")!=PARENT: die("candidate parent")
    expected=sorted([
      ".github/workflows/pest-current-pair-c2-p05-combat-component-rebind-20261005.yml",
      "docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_P05_COMBAT_COMPONENT_REBIND_20261005.json",
      "scripts/experiments/pest-control/verify_pest_current_pair_c2_p05_combat_component_rebind.py",
    ])
    actual=sorted(x for x in git("diff","--name-only",f"{PARENT}..HEAD").splitlines() if x)
    if actual!=expected: die(f"delta {actual}")

    r=d["receiving"]; sr=d["independent_source_review"]; ar=d["independent_artifact_review"]
    for item in (r,sr,ar):
        if git("rev-parse",f"HEAD:{item['path']}")!=item["blob"]: die(f"record drift {item['path']}")
    receiving=json.loads(pathlib.Path(r["path"]).read_text())
    source_review=json.loads(pathlib.Path(sr["path"]).read_text())
    artifact_review=json.loads(pathlib.Path(ar["path"]).read_text())
    if source_review.get("disposition")!=sr["disposition"]: die("source review disposition")
    if artifact_review.get("verdict")!=ar["verdict"]: die("artifact verdict")
    for key in ("run_id","artifact_id","actual_cases","failures","errors","skipped"):
        if artifact_review.get(key)!=ar[key]: die(f"artifact binding {key}")
    if artifact_review.get("archive_sha256")!=ar["archive_sha256"]: die("archive sha")
    if artifact_review.get("same_reviewed_tree") is not True: die("review tree")
    if receiving.get("actual_required_cases")!=32 or receiving.get("gameplay_authorized") is not False or receiving.get("official_games")!=0:
        die("receiving scope")

    pins=artifact_review.get("all_source_sha256",{})
    if len(pins)<30: die("insufficient accepted source pins")
    for path,expected_sha in pins.items():
        p=pathlib.Path(path)
        if not p.is_file(): die(f"missing current pin {path}")
        actual_sha=sha(p)
        if actual_sha!=expected_sha: die(f"current byte drift {path}: {actual_sha} != {expected_sha}")

    if d["c2p05"]["gate_status"].startswith("SATISFIED"): die("whole C2P05 may not be promoted")
    if d.get("official_counters")!={"allocations":0,"claims":0,"games":0,"outcomes":0}: die("counters")
    print(json.dumps({
      "schema":"pest-current-pair-c2-p05-combat-component-rebind-result-v1",
      "status":"PASS",
      "head":git("rev-parse","HEAD"),
      "tree":git("rev-parse","HEAD^{tree}"),
      "accepted_source_pins_rebound":len(pins),
      "accepted_cases":32,
      "component_status":d["c2p05"]["component_status"],
      "c2p05_gate_status":d["c2p05"]["gate_status"],
      "official_counters_delta":0
    },sort_keys=True))
if __name__=="__main__": main()
