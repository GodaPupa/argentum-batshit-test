"""Read-only audit of already-completed source08 priority evidence."""
from pathlib import Path
import datetime as dt
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).parent
HEAD = "3a4f99a7653839506e96d19e6639f58d9e8c5ced"
TREE = "56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6"
def sha(data): return hashlib.sha256(data).hexdigest()
def instant(value): return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
def read_text(name):
    path = ROOT / name
    if path.exists(): return path.read_text()
    return gzip.decompress((ROOT / (name + ".gz")).read_bytes()).decode()

live = json.loads((ROOT / "live-inputs.json").read_text())
run, jobs, metadata = live["run"], live["jobs"]["jobs"], live["artifacts"]["artifacts"]
assert run["id"] == 36268324434 and run["head_sha"] == HEAD
assert run["run_attempt"] == 1 and run["event"] == "pull_request"
assert run["status"] == "completed" and run["conclusion"] == "success"
assert len(jobs) == len(metadata) == 1
assert jobs[0]["id"] == 108477324533 and jobs[0]["head_sha"] == HEAD
assert all(step["conclusion"] == "success" for step in jobs[0]["steps"])
artifact = metadata[0]
raw = (ROOT / "original-artifact-10915032554.zip").read_bytes()
assert artifact["id"] == 10915032554 and len(raw) == artifact["size_in_bytes"] == 344773
assert "sha256:" + sha(raw) == artifact["digest"]
sources = json.loads(read_text("exact-git-source-inputs.json"))
sources.update(json.loads(read_text("exact-git-source-extra.json")))
log = read_text("decoded-job-108477324533.log")
invocations = list(re.finditer(r"(?m)^(\S+) test-class: (\S+) -> (\S+)$", log))
assert len(invocations) == 27
assert HEAD in log and "HEAD is now at 3a4f99a765" in log
assert len(re.findall(r"Gradle Test Executor \d+ started executing tests\.", log)) == 27
assert len(re.findall(r"Gradle Test Executor \d+ finished executing tests\.", log)) == 27
assert len(re.findall(r"BUILD SUCCESSFUL in", log)) == 27
assert not re.findall(r"(?m)> Task \S+:test (?:FAILED|NO-SOURCE|SKIPPED|UP-TO-DATE|FROM-CACHE)\s*$", log)

with zipfile.ZipFile(ROOT / "original-artifact-10915032554.zip") as archive:
    assert archive.testzip() is None
    names = archive.namelist()
    assert len(names) == len(set(names)) == 113
    manifest_raw = archive.read("source-manifest.json")
    manifest = json.loads(manifest_raw)
    before = json.loads(archive.read("binding-attempt.json"))
    provenance = json.loads(archive.read("source-provenance.json"))
    collected = json.loads(archive.read("actual-case-audit.json"))
    assert collected["head"] == HEAD and collected["tree"] == TREE
    assert collected["status"] == "PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW" and collected["errors"] == []
    for value in [before, provenance, collected]: assert value["manifest_sha256"] == sha(manifest_raw)
    assert before["candidate_head"] == before["expected_head"] == provenance["candidate_head"] == HEAD
    assert before["github_run_id"] == provenance["github_run_id"] == str(run["id"])
    assert before["github_run_attempt"] == provenance["github_run_attempt"] == "1"
    pins = manifest["source_files_sha256"]
    assert pins == before["expected_source_files_sha256"] == before["observed_source_files_sha256"] == provenance["source_files_sha256"]
    assert len(pins) == 210
    verified = {}
    for path, expected in pins.items():
        data = sources[path]["content"].encode()
        assert sha(data) == expected, path
        git_blob = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
        assert git_blob == sources[path]["sha"], path
        verified[path] = {"sha256": expected, "git_blob_sha1": git_blob, "bytes": len(data)}
    for item in manifest["preserved_manifests"]:
        assert sha(sources[item["path"]]["content"].encode()) == item["sha256"]
    rules = archive.read("effective-rules.txt")
    assert len(rules) == manifest["rules"]["byte_length"] and sha(rules) == manifest["rules"]["sha256"]
    original = json.loads(sources[manifest["stage_count_authority"]["path"]]["content"])
    original_rows = original["xml"]
    assert sorted((x["stage"], x["class"], x["expected_cases"]) for x in manifest["stages"]) == sorted((x["path"].split("/")[1], x["class"], x["cases"]) for x in original_rows)
    assert {n.split("/")[1] for n in names if n.startswith("tests/")} == {x["stage"] for x in manifest["stages"]}
    xml_rows, seen = [], set()
    for i, stage in enumerate(manifest["stages"]):
        call = invocations[i]
        assert call.group(2) == stage["class"].split(".")[-1]
        assert call.group(3) == ":" + stage["module"].replace("/", ":")
        end = invocations[i + 1].start() if i + 1 < len(invocations) else len(log)
        segment = log[call.start():end]
        assert segment.count("started executing tests.") == segment.count("finished executing tests.") == 1
        assert segment.count("BUILD SUCCESSFUL in") == 1
        xml_name = f"tests/{stage['stage']}/TEST-{stage['class']}.xml"
        xml = archive.read(xml_name); suite = ET.fromstring(xml)
        cases = suite.findall("testcase")
        assert suite.attrib["name"] == stage["class"]
        assert int(suite.attrib["tests"]) == len(cases) == stage["expected_cases"]
        assert all(int(suite.attrib.get(k, 0)) == 0 for k in ["failures", "errors", "skipped"])
        assert all(case.find(k) is None for case in cases for k in ["failure", "error", "skipped"])
        invocation_end = invocations[i + 1].group(1) if i + 1 < len(invocations) else jobs[0]["completed_at"]
        assert instant(call.group(1)) <= instant(suite.attrib["timestamp"]) <= instant(invocation_end)
        assert archive.read(f"tests/{stage['stage']}/exit-status.txt").strip() == b"0"
        for case in cases:
            key = (case.attrib["classname"], case.attrib["name"])
            assert key[0] == stage["class"] and key not in seen
            seen.add(key)
        collected_stage = collected["stages"][i]
        assert collected_stage["xml_sha256"] == sha(xml)
        assert collected_stage["case_names"] == [case.attrib["name"] for case in cases]
        xml_rows.append({**stage, "actual_cases": len(cases), "xml_sha256": sha(xml), "case_names": [case.attrib["name"] for case in cases], "invocation_started": call.group(1), "xml_timestamp": suite.attrib["timestamp"], "fresh_worker_pairs": 1, "build_successes": 1, "exit_status": 0})
    assert len(seen) == collected["actual_cases"] == 220
    member_map = {name: {"bytes": len(archive.read(name)), "sha256": sha(archive.read(name))} for name in names}

report = {"schema": "ferocity-source08-priority-raw-artifact-audit-v1", "status": "PASS_BOUNDED_PRIORITY_COMPONENT_PENDING_NONAUTHOR_REVIEW", "head": HEAD, "tree": TREE, "run_id": run["id"], "job_id": jobs[0]["id"], "run_attempt": 1, "event": run["event"], "artifact_id": artifact["id"], "original_artifact_bytes": len(raw), "original_artifact_sha256": sha(raw), "archive_crc_valid": True, "original_members": member_map, "manifest_sha256": sha(manifest_raw), "exact_source_pins_verified": verified, "preserved_authorities_verified": manifest["preserved_manifests"], "stages": xml_rows, "actual_unique_cases": len(seen), "failures": 0, "errors": 0, "skipped": 0, "decoded_log_utf8_sha256": sha(log.encode()), "decoded_log_utf8_bytes": len(log.encode()), "post_source_check": "The exact pinned collector rehashes all210 source files after allstages and records PASS with emptyerrors; no independent post-run source map exists in the originalarchive.", "scope": "Completed original27-stage/220-case shared-priority regression on exactFerocity receiving3a4f99 only. No fullruntime/admission acceptance or binary-equivalence claim to dedicatedFerocity gym runtime.", "new_test_executions": 0, "official_gameplay_authorized": False, "new_games": 0, "new_entropy": 0}
(ROOT / "priority-artifact-audit.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
with gzip.GzipFile(filename=str(ROOT / "decoded-job-108477324533.log.gz"), mode="wb", mtime=0) as target:
    target.write(log.encode())
print(json.dumps({"source_pins": len(verified), "stages": len(xml_rows), "cases": len(seen), "archive_members": len(member_map), "status": report["status"]}))
