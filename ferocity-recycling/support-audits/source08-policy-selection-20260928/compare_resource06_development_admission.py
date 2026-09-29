#!/usr/bin/env python3
import hashlib, json, pathlib, zipfile

archive=pathlib.Path("ferocity-recycling/evidence/build/publication-M1-source-audits/05-resource-boundary-06.zip")
member="ferocity-recycling/runtime-audits/development-admission/resource-boundary-06/FerocityDevelopmentAdmission.kt.before"
current=pathlib.Path("gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityDevelopmentAdmission.kt")
assert archive.is_file() and current.is_file()

def sha256(b): return hashlib.sha256(b).hexdigest()
def git_blob(b):
    return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()

with zipfile.ZipFile(archive) as z:
    bad=z.testzip()
    assert bad is None, bad
    names=z.namelist()
    assert member in names, names
    archived=z.read(member)

live=current.read_bytes()
out={
 "schema":"ferocity-resource06-development-admission-direct-byte-comparison-v2",
 "archive_git_blob":"a0fb48835413d80149331a7775e17820435cad7b",
 "archive_member":member,
 "archive_member_bytes":len(archived),
 "archive_member_git_blob":git_blob(archived),
 "archive_member_sha256":sha256(archived),
 "current_path":str(current),
 "current_bytes":len(live),
 "current_git_blob":git_blob(live),
 "current_sha256":sha256(live),
 "byte_identical":archived==live,
 "prior_claimed_archive_member_git_blob":"df6b3e0f6ae5e3c0885d51a1ea4a54854760cbc9",
 "prior_claimed_archive_member_sha256":"ed75e5d32a9eccf90a0379d62716a0a7b695b7196a82f32972df88a03b8b881f",
 "expected_current_git_blob":"c64afaf0dac73fe4a73e47a58ababa60a5888064",
 "expected_current_sha256":"ed75e5d32a9eccf90a0379d62716a0a7b695b7196a82f32972df88a03b8b881f",
 "jvm_started":False,"seed_or_entropy_files_read":False,"game_initialized":False,"official_counters_delta":0
}
assert out["current_git_blob"]==out["expected_current_git_blob"]
assert out["current_sha256"]==out["expected_current_sha256"]
print(json.dumps(out,indent=2,sort_keys=True))
