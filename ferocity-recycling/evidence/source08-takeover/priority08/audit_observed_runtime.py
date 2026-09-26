"""Audit retained source08 runtime archive bytes without extracting or executing them."""
from pathlib import Path
import hashlib
import json
import tarfile
import zipfile

ROOT = Path(__file__).parent
ZIP = ROOT.parent / "original-10914678223.zip"
HEAD = "3a4f99a7653839506e96d19e6639f58d9e8c5ced"
def sha(data): return hashlib.sha256(data).hexdigest()
def digest(stream):
    h, count = hashlib.sha256(), 0
    while block := stream.read(1024 * 1024):
        h.update(block); count += len(block)
    return count, h.hexdigest()

with ZIP.open("rb") as stream:
    assert digest(stream) == (95124680, "9849c7aade4ffe015fd5ab859ae57e4ba3c4e5461514bbc72bc6ffa355be0816")
with zipfile.ZipFile(ZIP) as z:
    receipt_raw = z.read("qualification/observed-gym-runtime/runtime-archive-receipt.json")
    observation_raw = z.read("qualification/runtime-classpath-observation/manifest.json")
    qualification_raw = z.read("qualification/receipt.json")
    before_raw = z.read("qualification/compiled-inputs-before.json")
    after_raw = z.read("qualification/compiled-inputs-after.json")
    receipt, observation, qualification = map(json.loads, [receipt_raw, observation_raw, qualification_raw])
    assert receipt["source_head"] == qualification["source_head"] == HEAD
    assert sha(qualification_raw) == receipt["qualification_receipt_sha256"] == observation["receipt_sha256"]
    assert sha(observation_raw) == receipt["observation_sha256"]
    assert before_raw == after_raw
    assert sha(before_raw) == qualification["compiled_inputs_before_sha256"] == qualification["compiled_inputs_after_sha256"]
    assert not qualification["source_is_dirty"]
    assert not qualification["tracked_compiled_input_changes"] and not qualification["untracked_compiled_inputs_sha256"]
    assert len(observation["stages"]) == 1 and len(observation["stages"][0]["matching_workers"]) == 1
    worker = observation["stages"][0]["matching_workers"][0]
    assert worker["argument_file_sha256"] == receipt["actual_worker_argument_file_sha256"]
    assert worker["classpath"] == receipt["ordered_classpath"]
    ordered = receipt["ordered_classpath"]
    assert len(ordered) == 56
    expected = {}
    for i, entry in enumerate(ordered):
        prefix = f"classpath/{i:03d}/"
        if entry["type"] == "file":
            expected[prefix + "runtime"] = {"path": prefix + "runtime", "classpath_index": i, "sha256": entry["sha256"], "bytes": entry["bytes"]}
        else:
            assert entry["type"] == "directory"
            for member in entry["members"]:
                path = prefix + member["path"]
                assert path not in expected
                expected[path] = {"path": path, "classpath_index": i, "sha256": member["sha256"], "bytes": member["bytes"]}
    archive = receipt["archive"]
    assert len(archive["members"]) == len(expected) == 2159
    assert {m["path"]: m for m in archive["members"]} == expected
    archive_path = "qualification/observed-gym-runtime/" + archive["path"]
    with z.open(archive_path) as stream:
        assert digest(stream) == (archive["bytes"], archive["sha256"])
    actual = {}
    with z.open(archive_path) as stream, tarfile.open(fileobj=stream, mode="r|gz") as tar:
        for member in tar:
            assert member.isfile(), (member.name, member.type)
            assert member.name in expected and member.name not in actual
            count, hashed = digest(tar.extractfile(member))
            pin = expected[member.name]
            assert count == member.size == pin["bytes"] and hashed == pin["sha256"], member.name
            actual[member.name] = {"bytes": count, "sha256": hashed}
    assert actual.keys() == expected.keys()

report = {
    "schema": "ferocity-source08-observed-runtime-bytes-audit-v1",
    "status": "PASS_ARCHIVED_BYTE_BINDING_PENDING_NONAUTHOR_REVIEW",
    "source_head": HEAD, "source_tree": qualification["source_tree"],
    "original_artifact_id": 10914678223,
    "original_zip_bytes": 95124680,
    "original_zip_sha256": "9849c7aade4ffe015fd5ab859ae57e4ba3c4e5461514bbc72bc6ffa355be0816",
    "runtime_archive": {k: archive[k] for k in ["path", "bytes", "sha256"]},
    "runtime_receipt_sha256": sha(receipt_raw),
    "runtime_observation_sha256": sha(observation_raw),
    "qualification_receipt_sha256": sha(qualification_raw),
    "compiled_inputs_before_after_sha256": sha(before_raw),
    "compiled_input_count": len(json.loads(before_raw)),
    "actual_worker_argument_file_sha256": worker["argument_file_sha256"],
    "recorded_java_executable": receipt["java_executable"],
    "ordered_classpath_entries": len(ordered), "actual_archive_files": len(actual),
    "actual_archive_payload_bytes": sum(x["bytes"] for x in actual.values()),
    "ordered_classpath": ordered,
    "scope": "Every retained archive file matched the recorded ordered classpath and observed qualification identity. No runtime was restored, loaded or executed. Java identity is recorded in the receipt, not an executable included in this runtime archive.",
    "not_established": ["complete source08 admission", "resource calibration capacity or authority", "offline card bundle export", "semantic replay", "binary equivalence with standard priority CI", "gameplay execution permission"],
    "engine_initializations": 0, "new_jvms": 0, "new_resource_commands": 0,
    "new_tests": 0, "new_entropy": 0, "new_games": 0
}
(ROOT / "observed-runtime-byte-audit.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
print(json.dumps({k: report[k] for k in ["status", "ordered_classpath_entries", "actual_archive_files", "actual_archive_payload_bytes", "compiled_input_count"]}))
