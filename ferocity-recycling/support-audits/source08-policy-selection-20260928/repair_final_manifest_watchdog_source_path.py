#!/usr/bin/env python3
from __future__ import annotations
import argparse, copy, hashlib, json, pathlib, zipfile

OLD_ARTIFACT_SHA="6cc1ed84aa9fd6b17ddcdce7a1054bbc856124b16dd6b1ecce1fe9d04f9ceb80"
OLD_MANIFEST_SHA="86d6d0ed39eb098eb630865d22b75c2d25539b61eb70f8207ef526d64e2755dc"
SOURCE_SHA="803a03aae3f63ffb114d9a8b56d4bf108b2f5f71f051057e204220f5f52adb37"
OLD_PATH="ferocity-recycling/evidence/admission/D2-first-cell-current/watchdog/source/trial_watchdog.py"
NEW_PATH="watchdog/source/trial_watchdog.py"
PREFIX="build/reports/ferocity-final-development-manifest/materialized/"
OUT=pathlib.Path("build/reports/ferocity-final-manifest-watchdog-path-repair")
MAT=OUT/"materialized"

def sha(b:bytes)->str: return hashlib.sha256(b).hexdigest()
def fsha(p:pathlib.Path)->str:
    with p.open("rb") as f: return hashlib.file_digest(f,"sha256").hexdigest()
def canon(v)->bytes: return json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False).encode()

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--original",required=True); a=ap.parse_args()
    src=pathlib.Path(a.original)
    assert fsha(src)==OLD_ARTIFACT_SHA
    OUT.mkdir(parents=True,exist_ok=False); MAT.mkdir()
    with zipfile.ZipFile(src) as z:
        assert z.testzip() is None
        names=z.namelist()
        members=[n for n in names if n.startswith(PREFIX) and not n.endswith("/")]
        assert len(members)==28
        for n in members:
            rel=n[len(PREFIX):]
            p=MAT/rel; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(z.read(n))
    manifest_path=MAT/"ferocity-recycling/evidence/admission/D2-first-cell-current/manifest.json"
    old_b=manifest_path.read_bytes(); assert sha(old_b)==OLD_MANIFEST_SHA
    old=json.loads(old_b); assert canon(old)==old_b
    source_old=MAT/OLD_PATH
    assert fsha(source_old)==SOURCE_SHA
    author_path=MAT/"ferocity-recycling/evidence/admission/D2-first-cell-current/watchdog/AUTHOR_RECEIPT.json"
    author=json.loads(author_path.read_text())
    assert any(x["path"]==NEW_PATH and x["sha256"]==SOURCE_SHA for x in author["owned_files"])
    target=MAT/NEW_PATH; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(source_old.read_bytes())
    assert fsha(target)==SOURCE_SHA
    new=copy.deepcopy(old)
    assert new["watchdog"]["source"]["path"]==OLD_PATH
    assert new["watchdog"]["source"]["sha256"]==SOURCE_SHA
    new["watchdog"]["source"]["path"]=NEW_PATH
    check=copy.deepcopy(new); check["watchdog"]["source"]["path"]=OLD_PATH
    assert check==old
    new_b=canon(new); manifest_path.write_bytes(new_b)
    assert sha(new_b)!=OLD_MANIFEST_SHA
    rows=[]
    for p in sorted(x for x in MAT.rglob("*") if x.is_file()):
        rows.append({"path":p.relative_to(MAT).as_posix(),"bytes":p.stat().st_size,"sha256":fsha(p)})
    assert len(rows)==29
    receipt={
      "schema":"ferocity-final-manifest-watchdog-path-repair-receipt-v1",
      "status":"REPAIRED_CANONICAL_MANIFEST_CANDIDATE_FOR_VERIFY_ONLY_REVIEW",
      "source_artifact_sha256":OLD_ARTIFACT_SHA,
      "old_manifest_sha256":OLD_MANIFEST_SHA,
      "new_manifest_sha256":sha(new_b),
      "manifest_changes":[{"field":"watchdog.source.path","before":OLD_PATH,"after":NEW_PATH}],
      "materialized_members":rows,
      "watchdog_source_sha256":SOURCE_SHA,
      "java_started":False,"gradle_started":False,"entropy_read":False,"allocation_read":False,
      "ledger_created":False,"trial_claim_created":False,"game_initialized":False,"outcomes_exposed":0,
      "official_counters_delta":0
    }
    (OUT/"repair-receipt.json").write_bytes(canon(receipt))
    print(json.dumps({"newManifestSha256":sha(new_b),"materializedFiles":len(rows),"status":receipt["status"]},sort_keys=True))
if __name__=="__main__": main()
