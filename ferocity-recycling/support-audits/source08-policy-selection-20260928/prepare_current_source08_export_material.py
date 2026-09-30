#!/usr/bin/env python3
"""Restore exact accepted source08 runtime/JDK bytes and emit export material. Never execute Java."""
from __future__ import annotations
import hashlib, json, os, pathlib, shutil, tarfile, tempfile, urllib.request, zipfile

SOURCE_COMMIT = "3a4f99a7653839506e96d19e6639f58d9e8c5ced"
ORIGINAL_SHA = "9849c7aade4ffe015fd5ab859ae57e4ba3c4e5461514bbc72bc6ffa355be0816"
ORIGINAL_BYTES = 95124680
RUNTIME_RECEIPT_SHA = "1dd2e960d9de93ad59a2439a14ef5345faa6bc572b9e8db3d3b886e18f8b1d49"
RUNTIME_ARCHIVE_SHA = "0e9ea0c6c3f2e29230148d330b6b7961aeba1fa44985876b90f68bc8ca7e28d5"
COMPILED_INPUTS_SHA = "36cfd36a923f6883ae7b55925957055ae65389ae41a17eafb2ad48fdf4a14908"
COMPILED_CANONICAL_SHA = "663af924957ed0d67af8cd594a8abcd6c1e404ea8181acda3fcbac28b6961fd3"
JDK_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
JDK_BYTES = 207473347
JDK_SHA = "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94"
JAVA_SHA = "2a207f5e7d075afa01d97f8048389a64432a44c4a5af0f5e77d6e286ec5f401d"

root = pathlib.Path(__file__).resolve().parents[3]
evidence = root / "ferocity-recycling/evidence/source08-takeover"
budget_path = root / "ferocity-recycling/support-audits/source08-policy-selection-20260928/CURRENT_SOURCE08_EXPORT_PLAN_ASSEMBLY_BUDGET.json"
budget = json.loads(budget_path.read_text())
assert budget["source08"]["commit"] == SOURCE_COMMIT
assert budget["runtime"]["original_artifact_sha256"] == ORIGINAL_SHA
assert budget["java"]["asset_sha256"] == JDK_SHA

report = root / "build/reports/ferocity-export-plan-assembly"
report.mkdir(parents=True, exist_ok=True)
runner_temp = pathlib.Path(os.environ["RUNNER_TEMP"]).resolve()
restore_root = runner_temp / f"ferocity-source08-runtime-{os.environ.get('GITHUB_RUN_ID','local')}"
jdk_parent = runner_temp / f"ferocity-source08-jdk-{os.environ.get('GITHUB_RUN_ID','local')}"
export_output = runner_temp / f"ferocity-first-cell-export-output-{os.environ.get('GITHUB_RUN_ID','local')}"
for p in (restore_root, jdk_parent, export_output):
    assert not p.exists(), p

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def file_sha(path: pathlib.Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def canonical(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()

def git_blob(data: bytes) -> str:
    return hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()

def safe_rel(name: str) -> pathlib.PurePosixPath:
    p = pathlib.PurePosixPath(name)
    assert not p.is_absolute() and p.parts and ".." not in p.parts and "." not in p.parts
    return p

# Reassemble exact source08 gym artifact from published repository parts.
publication = json.loads((evidence / "publication-manifest.json").read_text())
artifact = next(row for row in publication["originals"] if row["id"] == 10914678223)
assert artifact["sha256"] == ORIGINAL_SHA and artifact["bytes"] == ORIGINAL_BYTES and len(artifact["parts"]) == 12
original_path = runner_temp / f"ferocity-source08-gym-{os.environ.get('GITHUB_RUN_ID','local')}.zip"
whole = hashlib.sha256()
written = 0
part_receipts = []
with original_path.open("xb") as out:
    for part in sorted(artifact["parts"], key=lambda r: r["offset"]):
        data = (evidence / part["path"]).read_bytes()
        assert written == part["offset"]
        assert len(data) == part["bytes"]
        assert sha(data) == part["sha256"]
        assert git_blob(data) == part["git_blob_sha1"]
        out.write(data); whole.update(data); written += len(data)
        part_receipts.append({"path": part["path"], "offset": part["offset"], "bytes": len(data),
                              "sha256": sha(data), "git_blob_sha1": git_blob(data)})
assert written == ORIGINAL_BYTES and whole.hexdigest() == ORIGINAL_SHA

try:
    with zipfile.ZipFile(original_path) as zf:
        assert zf.testzip() is None
        receipt_bytes = zf.read("qualification/observed-gym-runtime/runtime-archive-receipt.json")
        runtime_bytes = zf.read("qualification/observed-gym-runtime/runtime-files.tar.gz")
        compiled_bytes = zf.read("qualification/compiled-inputs-before.json")
    assert sha(receipt_bytes) == RUNTIME_RECEIPT_SHA
    assert sha(runtime_bytes) == RUNTIME_ARCHIVE_SHA
    assert sha(compiled_bytes) == COMPILED_INPUTS_SHA
    runtime_receipt = json.loads(receipt_bytes)
    compiled = json.loads(compiled_bytes)
    assert sha(canonical(compiled)) == COMPILED_CANONICAL_SHA
    assert runtime_receipt["source_head"] == SOURCE_COMMIT
    assert runtime_receipt["archive"]["sha256"] == RUNTIME_ARCHIVE_SHA
    assert len(runtime_receipt["ordered_classpath"]) == 56
    assert len(runtime_receipt["archive"]["members"]) == 2159

    compiled_rel = "build/reports/ferocity-export-plan-assembly/compiled-inputs-source08.json"
    compiled_path = root / compiled_rel
    compiled_path.write_bytes(compiled_bytes)

    # Restore runtime archive, checking every member against the accepted receipt.
    restore_root.mkdir()
    expected_members = {row["path"]: row for row in runtime_receipt["archive"]["members"]}
    observed_members = {}
    runtime_tmp = runner_temp / f"ferocity-source08-runtime-{os.environ.get('GITHUB_RUN_ID','local')}.tar.gz"
    runtime_tmp.write_bytes(runtime_bytes)
    try:
        with tarfile.open(runtime_tmp, "r:gz") as tf:
            members = tf.getmembers()
            assert len(members) == len(expected_members)
            for member in members:
                assert member.isfile() and not member.issym() and not member.islnk()
                rel = safe_rel(member.name)
                expected = expected_members[member.name]
                stream = tf.extractfile(member)
                assert stream is not None
                data = stream.read()
                assert len(data) == expected["bytes"] and sha(data) == expected["sha256"]
                dest = restore_root.joinpath(*rel.parts)
                dest.parent.mkdir(parents=True, exist_ok=True)
                with dest.open("xb") as out:
                    out.write(data)
                observed_members[member.name] = {"bytes": len(data), "sha256": sha(data)}
    finally:
        runtime_tmp.unlink(missing_ok=True)
    assert set(observed_members) == set(expected_members)

    # Reconstruct exact FerocityRuntimePathPin-compatible ordered classpath rows.
    by_index = {}
    for row in runtime_receipt["archive"]["members"]:
        by_index.setdefault(row["classpath_index"], []).append(row)
    class_path = []
    for index, entry in enumerate(runtime_receipt["ordered_classpath"]):
        rows = sorted(by_index.get(index, []), key=lambda r: r["path"])
        prefix = f"classpath/{index:03d}/"
        if entry["type"] == "file":
            assert len(rows) == 1 and rows[0]["sha256"] == entry["sha256"] and rows[0]["bytes"] == entry["bytes"]
            path = restore_root / rows[0]["path"]
            assert path.is_file() and file_sha(path) == entry["sha256"]
            class_path.append({"path": str(path.resolve()), "directory": False, "sha256": entry["sha256"], "files": {}})
        else:
            assert entry["type"] == "directory"
            files = {}
            declared = {r["path"]: r for r in entry["members"]}
            for row in rows:
                assert row["path"].startswith(prefix)
                relative = row["path"][len(prefix):]
                assert relative in declared
                assert row["sha256"] == declared[relative]["sha256"] and row["bytes"] == declared[relative]["bytes"]
                files[relative] = row["sha256"]
            assert set(files) == set(declared)
            path = restore_root / f"classpath/{index:03d}"
            assert path.is_dir()
            class_path.append({"path": str(path.resolve()), "directory": True,
                               "sha256": sha(canonical(files)), "files": dict(sorted(files.items()))})
    assert len(class_path) == 56
    class_path_path = report / "class-path.json"
    class_path_path.write_bytes(canonical(class_path))
    class_path_sha = file_sha(class_path_path)

    # Restore the independently accepted exact Temurin distribution without running it.
    jdk_archive = runner_temp / f"temurin-21.0.12.1-1-{os.environ.get('GITHUB_RUN_ID','local')}.tar.gz"
    h = hashlib.sha256(); count = 0
    request = urllib.request.Request(JDK_URL, headers={"User-Agent": "Argentum-Ferocity-Export-Plan/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response, jdk_archive.open("xb") as out:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk: break
            out.write(chunk); h.update(chunk); count += len(chunk)
    assert count == JDK_BYTES and h.hexdigest() == JDK_SHA
    jdk_parent.mkdir()
    try:
        import posixpath
        with tarfile.open(jdk_archive, "r:gz") as tf:
            for member in tf.getmembers():
                member_path = safe_rel(member.name)
                if member.issym() or member.islnk():
                    target = pathlib.PurePosixPath(member.linkname)
                    assert not target.is_absolute()
                    normalized = pathlib.PurePosixPath(
                        posixpath.normpath(str(member_path.parent / target))
                    )
                    assert not normalized.is_absolute() and ".." not in normalized.parts
            tf.extractall(jdk_parent, filter="data")
    finally:
        jdk_archive.unlink(missing_ok=True)
    java = (jdk_parent / "jdk-21.0.12.1+1/bin/java").resolve(strict=True)
    assert java.is_file() and file_sha(java) == JAVA_SHA

    # Verify exact source08 policy/protocol inputs before material assembly.
    for policy in budget["policies"].values():
        for relative, expected in policy["files"].items():
            assert compiled.get(relative) == expected
            assert file_sha(root / relative) == expected
    assert compiled.get(budget["serializer"]["path"]) == budget["serializer"]["sha256"]
    assert file_sha(root / budget["serializer"]["path"]) == budget["serializer"]["sha256"]
    for relative, expected in budget["protocol_files"].items():
        assert file_sha(root / relative) == expected

    material = {
        "schemaVersion": 1,
        "scope": "FEROCITY_FIRST_CELL_EXPORT_MATERIAL",
        "repositoryPath": str(root.resolve()),
        "sourceCommit": SOURCE_COMMIT,
        "compiledInputs": {"path": compiled_rel, "sha256": COMPILED_INPUTS_SHA},
        "classPathFile": {"path": str(class_path_path.resolve()), "sha256": class_path_sha},
        "javaExecutable": {"path": str(java), "sha256": JAVA_SHA},
        "policies": budget["policies"],
        "protocolFiles": budget["protocol_files"],
        "dependencies": {},
        "exportOutput": str(export_output),
    }
    material_path = report / "material.json"
    material_path.write_bytes(canonical(material))
    material_sha = file_sha(material_path)

    restore_receipt = {
        "schema": "ferocity-source08-export-material-restore-v1",
        "sourceCommit": SOURCE_COMMIT,
        "originalArtifact": {"id": 10914678223, "bytes": written, "sha256": ORIGINAL_SHA, "parts": part_receipts},
        "runtime": {"receiptSha256": RUNTIME_RECEIPT_SHA, "archiveSha256": RUNTIME_ARCHIVE_SHA,
                    "classpathEntries": len(class_path), "files": len(observed_members),
                    "classPathFileSha256": class_path_sha},
        "compiledInputs": {"fileSha256": COMPILED_INPUTS_SHA, "canonicalSha256": COMPILED_CANONICAL_SHA},
        "java": {"distributionArchiveSha256": JDK_SHA, "javaExecutableSha256": JAVA_SHA,
                 "javaExecutable": str(java), "architecture": "x86_64"},
        "materialSha256": material_sha,
        "javaStarted": False, "gradleStarted": False, "exporterStarted": False,
        "seedOrEntropyRead": False, "allocationRead": False, "gameInitialized": False,
    }
    (report / "restore-receipt.json").write_text(json.dumps(restore_receipt, indent=2, sort_keys=True) + "\n")
    (report / "material-sha256.txt").write_text(material_sha + "\n")
    print(json.dumps({"material": str(material_path), "materialSha256": material_sha,
                      "classPathEntries": len(class_path), "runtimeFiles": len(observed_members),
                      "javaExecutable": str(java), "javaStarted": False}, sort_keys=True))
finally:
    original_path.unlink(missing_ok=True)
