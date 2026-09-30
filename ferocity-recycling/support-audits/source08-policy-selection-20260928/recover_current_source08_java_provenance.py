#!/usr/bin/env python3
import hashlib, json, pathlib, re, tempfile, zipfile

root = pathlib.Path(__file__).resolve().parents[3]
evidence = root / "ferocity-recycling/evidence/source08-takeover"
manifest_path = evidence / "publication-manifest.json"
manifest = json.loads(manifest_path.read_text())
artifact = next(x for x in manifest["originals"] if x["id"] == 10914678223)

expected_sha = "9849c7aade4ffe015fd5ab859ae57e4ba3c4e5461514bbc72bc6ffa355be0816"
expected_bytes = 95124680
assert artifact["sha256"] == expected_sha
assert artifact["bytes"] == expected_bytes
assert len(artifact["parts"]) == 12

def git_blob_sha1(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode()
    return hashlib.sha1(header + data).hexdigest()

part_rows = []
whole = hashlib.sha256()
written = 0

with tempfile.NamedTemporaryFile(prefix="ferocity-source08-gym-", suffix=".zip", delete=False) as tmp:
    tmp_path = pathlib.Path(tmp.name)
    for part in sorted(artifact["parts"], key=lambda x: x["offset"]):
        p = evidence / part["path"]
        data = p.read_bytes()
        assert len(data) == part["bytes"], (part["path"], len(data), part["bytes"])
        assert written == part["offset"], (part["path"], written, part["offset"])
        sha = hashlib.sha256(data).hexdigest()
        blob = git_blob_sha1(data)
        assert sha == part["sha256"], (part["path"], sha, part["sha256"])
        assert blob == part["git_blob_sha1"], (part["path"], blob, part["git_blob_sha1"])
        tmp.write(data)
        whole.update(data)
        part_rows.append({
            "path": part["path"],
            "offset": part["offset"],
            "bytes": len(data),
            "sha256": sha,
            "git_blob_sha1": blob,
        })
        written += len(data)

try:
    assert written == expected_bytes, (written, expected_bytes)
    actual_sha = whole.hexdigest()
    assert actual_sha == expected_sha, (actual_sha, expected_sha)

    key_pattern = re.compile(r"(java|jdk|temurin|toolchain|executable|distribution|runtime|classpath)", re.I)
    allowed_member = re.compile(
        r"(receipt|runtime|qualification|provenance|source|environment|manifest|classpath|java|toolchain|argument)",
        re.I,
    )
    forbidden_member = re.compile(r"(seed|entropy|allocation|journal|outcome|game[-_ ]?result|trial[-_ ]?record)", re.I)
    matches = []
    scanned = []

    def add(member, kind, locator, value, member_sha256):
        text = value if isinstance(value, str) else json.dumps(value, sort_keys=True)
        if key_pattern.search(locator) or key_pattern.search(text):
            matches.append({
                "member": member,
                "kind": kind,
                "locator": locator,
                "value": value,
                "member_sha256": member_sha256,
            })

    def walk_json(member, value, member_sha256, path=()):
        if isinstance(value, dict):
            for k, v in value.items():
                p = path + (str(k),)
                if not isinstance(v, (dict, list)):
                    add(member, "json-value", ".".join(p), v, member_sha256)
                walk_json(member, v, member_sha256, p)
        elif isinstance(value, list):
            for i, v in enumerate(value):
                walk_json(member, v, member_sha256, path + (str(i),))

    with zipfile.ZipFile(tmp_path) as zf:
        bad = zf.testzip()
        assert bad is None, bad
        for info in zf.infolist():
            if info.is_dir():
                continue
            name = info.filename
            if forbidden_member.search(name) or not allowed_member.search(name):
                continue
            suffix = pathlib.PurePosixPath(name).suffix.lower()
            if suffix not in {".json", ".txt", ".log", ".md", ".properties", ".tsv"}:
                continue
            data = zf.read(name)
            member_sha = hashlib.sha256(data).hexdigest()
            scanned.append({"member": name, "bytes": len(data), "sha256": member_sha})
            if suffix == ".json":
                try:
                    walk_json(name, json.loads(data.decode("utf-8")), member_sha)
                except (UnicodeDecodeError, json.JSONDecodeError):
                    pass
            try:
                for i, line in enumerate(data.decode("utf-8").splitlines(), 1):
                    if key_pattern.search(line):
                        add(name, "text-line", f"line:{i}", line, member_sha)
            except UnicodeDecodeError:
                pass

    matches.sort(key=lambda x: (x["member"], x["kind"], x["locator"], json.dumps(x["value"], sort_keys=True)))
    scanned.sort(key=lambda x: x["member"])
    out = root / "build/reports/ferocity-current-java-provenance"
    out.mkdir(parents=True, exist_ok=True)
    result = {
        "schema": "ferocity-current-source08-java-provenance-candidates-v2",
        "publication_manifest_sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        "artifact_id": artifact["id"],
        "artifact_bytes": written,
        "artifact_sha256": actual_sha,
        "part_count": len(part_rows),
        "parts": part_rows,
        "zip_crc": "clean",
        "scanned_members": scanned,
        "scanned_member_count": len(scanned),
        "matches": matches,
        "match_count": len(matches),
        "java_started": False,
        "gradle_started": False,
        "exporter_started": False,
        "seed_or_entropy_read": False,
        "allocation_read": False,
        "game_initialized": False,
        "disposition": "RECORDED_PROVENANCE_CANDIDATES_ONLY",
    }
    (out / "provenance-candidates.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps({
        "artifact_id": artifact["id"],
        "artifact_sha256": actual_sha,
        "part_count": len(part_rows),
        "scanned_member_count": len(scanned),
        "match_count": len(matches),
    }, sort_keys=True))
finally:
    tmp_path.unlink(missing_ok=True)
