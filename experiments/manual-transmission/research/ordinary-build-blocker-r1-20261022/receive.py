#!/usr/bin/env python3
"""Receive an original failed artifact; preserve an untested draft without executing it."""
import hashlib
import io
import json
import subprocess
import tarfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
REPO = ROOT / "metamorph"
MAIN = "37a603617a03dfd2d81b39dae004f7f336a70dfc"
SOURCE = "a9ea02595bcdc681778b1e003e1b79d51737aeb2"
VALIDATION = "623db9bb9ce503152947ebee2f037111df419f4a"
ARCHIVE = "manual-ordinary-build-r1-39761794103.zip"
SHA256 = "e496371038f9531e239573c4b96b61e643bd6c473fbf057800df4159cbaa7df2"

def digest(data):
    return hashlib.sha256(data).hexdigest()

def git(*args):
    return subprocess.check_output(["git", "-C", str(REPO), *args], text=True).strip()

def save_json(name, value):
    (HERE / name).write_text(json.dumps(value, indent=2) + "\n")

raw = (ROOT / ARCHIVE).read_bytes()
assert len(raw) == 148577 and digest(raw) == SHA256
(HERE / ARCHIVE).write_bytes(raw)
with zipfile.ZipFile(io.BytesIO(raw)) as z:
    names = z.namelist()
    assert len(names) == len(set(names)) == 119 and z.testzip() is None
    manifest = dict(line.split("  ", 1)[::-1] for line in z.read("SHA256SUMS").decode().splitlines())
    assert set(manifest) == set(names) - {"SHA256SUMS"}
    assert all(digest(z.read(name)) == sha for name, sha in manifest.items())
    assert z.read("source-sha.txt").decode().strip() == SOURCE
    assert z.read("validation-sha.txt").decode().strip() == VALIDATION
    assert z.read("exit.txt").decode().strip() == "1"
    assert z.read("tracked-changes.txt") == b""
    summary = json.loads(z.read("SUMMARY.json"))
    totals = dict(rows=0, passed=0, failures=0, errors=0, skipped=0, classes=0)
    failures = []
    for name in names:
        if not name.startswith("junit/") or not name.endswith(".xml"):
            continue
        suite = ET.fromstring(z.read(name))
        cases = suite.findall("testcase")
        totals["classes"] += 1
        totals["rows"] += len(cases)
        for case in cases:
            category = "passed"
            for tag, field in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
                node = case.find(tag)
                if node is not None:
                    category = field
                    if tag in ("failure", "error"):
                        failures.append({"class": case.attrib["classname"], "name": case.attrib["name"], "message": node.attrib.get("message"), "xml": name})
                    break
            totals[category] += 1
    assert totals == summary["totals"] == dict(rows=1399, passed=1392, failures=4, errors=0, skipped=3, classes=110)
    (HERE / "SUMMARY.json").write_bytes(z.read("SUMMARY.json"))
    save_json("artifact-audit.json", {"run":39761794103, "attempt":1, "job":119767491442, "artifact_id":12419081339, "archive":ARCHIVE, "bytes":len(raw), "sha256":SHA256, "unique_members":len(names), "crc_clean":True, "verified_internal_payloads":len(manifest), "source":SOURCE, "validation":VALIDATION, "process_exit":1, "tracked_changes_empty":True, "xml_totals":totals, "failure_details":failures, "independent_review":False})

paths = ["oracle-assay", "mtg-sdk", "buildSrc", "gradle", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "AGENTS.md", ".agents/skills/verify/SKILL.md", "oracle-assay/src/test/kotlin/com/wingedsheep/assay/grammar/ManaValuesTest.kt", "oracle-assay/src/test/kotlin/com/wingedsheep/assay/grammar/SubtypesTest.kt", "experiments/manual-transmission/control/v0.7.decklist.txt"]
identity = []
for path in paths:
    before, after = (git("rev-parse", ref + ":" + path) for ref in (MAIN, SOURCE))
    assert before == after, path
    identity.append({"path":path, "main_object":before, "source_object":after, "identical":True})
assert git("diff", "--name-only", MAIN, SOURCE, "--", *paths) == ""
save_json("source-identity.json", {"main":MAIN, "source":SOURCE, "objects":identity, "baseline_rerun_performed":False, "conclusion":"Failing grammar module, its only production project dependency, build configuration, and tests are unchanged. This is source-identity evidence, not a baseline execution claim."})

draft_paths = ["docs/card-sdk-language-reference.md", "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/ReplacementEffect.kt", "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/effects/CopyExceptions.kt", "rules-engine/src/main/kotlin/com/wingedsheep/engine/core/ModalAndCloneContinuations.kt", "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/continuations/ModalAndCloneContinuationResumer.kt", "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/PermanentEntryReplacements.kt", "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt", "mtg-sdk/src/test/kotlin/com/wingedsheep/sdk/serialization/EntersAsCopyExceptionsSerializationTest.kt", "rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/EntersAsCopyExceptionsScenarioTest.kt", "experiments/manual-transmission/research/copy-entry-exceptions-r1-20261022/PROTOCOL.json"]
assert git("rev-parse", "HEAD") == SOURCE
draft_manifest = []
with tarfile.open(HERE / "UNTESTED-copy-entry-draft.tar.gz", "w:gz") as tar:
    for path in draft_paths:
        payload = (REPO / path).read_bytes()
        info = tarfile.TarInfo(path)
        info.size, info.mode, info.mtime = len(payload), 0o644, 0
        tar.addfile(info, io.BytesIO(payload))
        draft_manifest.append({"path":path, "bytes":len(payload), "sha256":digest(payload), "git_blob":hashlib.sha1(b"blob " + str(len(payload)).encode() + b"\0" + payload).hexdigest()})
assert next(x["git_blob"] for x in draft_manifest if x["path"].endswith("EntersAsCopyExceptionsScenarioTest.kt")) == "d35b6b89bb2e2abf9baab2a9edc585f581ce8cb3"
draft_raw = (HERE / "UNTESTED-copy-entry-draft.tar.gz").read_bytes()
parts = []
for index, offset in enumerate(range(0, len(draft_raw), 250000)):
    name = "UNTESTED-copy-entry-draft.tar.gz.part%02d" % index
    payload = draft_raw[offset:offset + 250000]
    (HERE / name).write_bytes(payload)
    parts.append({"name":name, "bytes":len(payload), "sha256":digest(payload)})
save_json("UNTESTED-draft-manifest.json", {"base_source":SOURCE, "preregistration":"2a2cd78fa163a0242bb8a468a47aea1694a6bb6d", "status":"HELD_UNTESTED_UNADOPTED", "compile_executed":False, "tests_executed":False, "card_added":False, "full_file_archive":"UNTESTED-copy-entry-draft.tar.gz", "archive_bytes":len(draft_raw), "archive_sha256":digest(draft_raw), "parts":parts, "files":draft_manifest})
(HERE / "SHA256SUMS").write_text("".join(digest(p.read_bytes()) + "  " + p.name + "\n" for p in sorted(HERE.iterdir()) if p.is_file() and p.name not in {"SHA256SUMS", "UNTESTED-copy-entry-draft.tar.gz"}))
print(json.dumps({"artifact_audit":"PASS", "source_identity":"PASS", "draft_preserved_files":len(draft_paths), "xml_totals":totals}))
