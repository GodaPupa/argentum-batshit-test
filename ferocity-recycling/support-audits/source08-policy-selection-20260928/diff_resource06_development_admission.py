#!/usr/bin/env python3
import difflib, hashlib, json, pathlib, re, zipfile

archive=pathlib.Path("ferocity-recycling/evidence/build/publication-M1-source-audits/05-resource-boundary-06.zip")
member="ferocity-recycling/runtime-audits/development-admission/resource-boundary-06/FerocityDevelopmentAdmission.kt.before"
current=pathlib.Path("gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityDevelopmentAdmission.kt")

with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    old=z.read(member).decode("utf-8")
new=current.read_text()

def sha(s): return hashlib.sha256(s.encode()).hexdigest()
def meaningful(lines):
    out=[]
    in_block=False
    for raw in lines:
        s=raw.strip()
        if in_block:
            if "*/" in s: in_block=False
            continue
        if s.startswith("/*"):
            if "*/" not in s: in_block=True
            continue
        if not s or s.startswith("//") or s.startswith("*"): continue
        out.append(re.sub(r"\s+"," ",s))
    return out

old_lines=old.splitlines()
new_lines=new.splitlines()
old_exec=meaningful(old_lines)
new_exec=meaningful(new_lines)
ops=[]
for tag,i1,i2,j1,j2 in difflib.SequenceMatcher(a=old_exec,b=new_exec,autojunk=False).get_opcodes():
    if tag!="equal":
        ops.append({"tag":tag,"old":old_exec[i1:i2],"new":new_exec[j1:j2]})
diff=list(difflib.unified_diff(old_lines,new_lines,fromfile="Resource06/FerocityDevelopmentAdmission.kt.before",tofile="source08/FerocityDevelopmentAdmission.kt",lineterm=""))
out={
 "schema":"ferocity-resource06-development-admission-semantic-diff-v1",
 "archive_member_sha256":sha(old),
 "current_sha256":sha(new),
 "byte_identical":old==new,
 "old_lines":len(old_lines),
 "new_lines":len(new_lines),
 "unified_diff_lines":len(diff),
 "meaningful_old_lines":len(old_exec),
 "meaningful_new_lines":len(new_exec),
 "meaningful_change_hunks":len(ops),
 "meaningful_changes":ops,
 "unified_diff":diff,
 "classification":"EXECUTABLE_TEXT_IDENTICAL" if old_exec==new_exec else "EXECUTABLE_TEXT_DIFFERS",
 "jvm_started":False,
 "seed_or_entropy_files_read":False,
 "game_initialized":False,
 "official_counters_delta":0
}
print(json.dumps(out,indent=2,sort_keys=True))
