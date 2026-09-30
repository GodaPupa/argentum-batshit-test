#!/usr/bin/env python3
import hashlib, json, pathlib, re, zipfile

root = pathlib.Path(__file__).resolve().parents[3]
archive = root / "ferocity-recycling/evidence/source08-takeover/priority08/original-artifact-10915032554.zip"
expected = "9849c7aade4ffe015fd5ab859ae57e4ba3c4e5461514bbc72bc6ffa355be0816"
raw = archive.read_bytes()
actual = hashlib.sha256(raw).hexdigest()
assert actual == expected, (actual, expected)

pattern = re.compile(r"(java|jdk|temurin|toolchain|executable|distribution|runtime)", re.I)
matches = []

def add(member, kind, locator, value, member_sha256):
    text = value if isinstance(value, str) else json.dumps(value, sort_keys=True)
    if pattern.search(locator) or pattern.search(text):
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
            add(member, "json-key", ".".join(p), v if not isinstance(v, (dict, list)) else type(v).__name__, member_sha256)
            walk_json(member, v, member_sha256, p)
    elif isinstance(value, list):
        for i, v in enumerate(value):
            walk_json(member, v, member_sha256, path + (str(i),))
    else:
        add(member, "json-value", ".".join(path), value, member_sha256)

with zipfile.ZipFile(archive) as zf:
    bad = zf.testzip()
    assert bad is None, bad
    for info in zf.infolist():
        if info.is_dir():
            continue
        data = zf.read(info.filename)
        member_sha = hashlib.sha256(data).hexdigest()
        suffix = pathlib.PurePosixPath(info.filename).suffix.lower()
        if suffix == ".json":
            try:
                walk_json(info.filename, json.loads(data.decode("utf-8")), member_sha)
            except Exception:
                pass
        if suffix in {".txt", ".log", ".md", ".json", ".properties"}:
            try:
                for i, line in enumerate(data.decode("utf-8", errors="strict").splitlines(), 1):
                    if pattern.search(line):
                        add(info.filename, "text-line", f"line:{i}", line, member_sha)
            except UnicodeDecodeError:
                pass

matches.sort(key=lambda x: (x["member"], x["kind"], x["locator"], json.dumps(x["value"], sort_keys=True)))
out = root / "build/reports/ferocity-current-java-provenance"
out.mkdir(parents=True, exist_ok=True)
result = {
    "schema": "ferocity-current-source08-java-provenance-candidates-v1",
    "archive_sha256": actual,
    "zip_crc": "clean",
    "matches": matches,
    "match_count": len(matches),
    "java_started": False,
    "gradle_started": False,
    "exporter_started": False,
    "seed_or_entropy_read": False,
    "game_initialized": False,
}
(out / "provenance-candidates.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
print(json.dumps({"match_count": len(matches), "archive_sha256": actual}, sort_keys=True))
