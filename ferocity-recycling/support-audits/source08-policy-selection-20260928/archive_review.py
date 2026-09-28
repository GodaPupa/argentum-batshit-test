#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,pathlib,subprocess,sys,zipfile
ARCHIVE_BLOB="6703d6913a321771563ff200ca99cd65f7c04cd0"
EVIDENCE_REF="ferocity-recycling/source08-evidence-20260926"
ARCHIVE_PATH="ferocity-recycling/evidence/build/publication-M1-source-audits/08-historical-first-cell-policy.zip"
OUT=pathlib.Path("build/reports/ferocity-policy-archive-review")
def git(*args):
    return subprocess.check_output(["git",*args],text=True).strip()
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    subprocess.run(["git","fetch","--no-tags","origin",f"refs/heads/{EVIDENCE_REF}:refs/remotes/origin/{EVIDENCE_REF}"],check=True,stdout=subprocess.DEVNULL)
    ref=f"origin/{EVIDENCE_REF}"
    actual=git("rev-parse",f"{ref}:{ARCHIVE_PATH}")
    if actual!=ARCHIVE_BLOB: raise SystemExit(f"archive blob mismatch {actual}")
    raw=subprocess.check_output(["git","show",f"{ref}:{ARCHIVE_PATH}"])
    archive=OUT/"historical-first-cell-policy.zip"
    archive.write_bytes(raw)
    rows=[]
    with zipfile.ZipFile(archive) as z:
        bad=z.testzip()
        if bad: raise SystemExit(f"CRC failure {bad}")
        names=z.namelist()
        if len(names)!=len(set(names)): raise SystemExit("duplicate zip member")
        for name in names:
            p=pathlib.PurePosixPath(name)
            if p.is_absolute() or ".." in p.parts: raise SystemExit(f"unsafe member {name}")
            info=z.getinfo(name); data=z.read(name)
            row={"path":name,"bytes":len(data),"sha256":hashlib.sha256(data).hexdigest(),"is_dir":info.is_dir()}
            if not info.is_dir() and len(data)<=2_000_000:
                try:
                    text=data.decode("utf-8")
                    row["utf8"]=True
                    low=(name+"\n"+text[:10000]).lower()
                    row["policy_candidate"]=("policy" in low or "prob" in low or "map" in low)
                    if name.lower().endswith(".json"):
                        try:
                            obj=json.loads(text); row["json_type"]=type(obj).__name__
                            if isinstance(obj,dict): row["json_keys"]=sorted(obj.keys())
                        except Exception as e: row["json_error"]=str(e)
                except UnicodeDecodeError:
                    row["utf8"]=False
            rows.append(row)
    manifest={
      "schema":"ferocity-historical-first-cell-policy-archive-inventory-v1",
      "archive_blob":ARCHIVE_BLOB,
      "archive_sha256":hashlib.sha256(raw).hexdigest(),
      "archive_bytes":len(raw),
      "member_count":len(rows),
      "members":rows,
      "policy_candidates":[r for r in rows if r.get("policy_candidate")],
      "executed_archive_members":False,
      "new_jvms":0,"new_entropy":0,"new_games":0,"new_calibration_commands":0,
      "result":"INVENTORY_READY_FOR_INDEPENDENT_CONTENT_ADOPTION"
    }
    (OUT/"inventory.json").write_text(json.dumps(manifest,indent=2)+"\n")
    print(json.dumps({"member_count":len(rows),"policy_candidates":[r["path"] for r in manifest["policy_candidates"]]},indent=2))
if __name__=="__main__": main()
