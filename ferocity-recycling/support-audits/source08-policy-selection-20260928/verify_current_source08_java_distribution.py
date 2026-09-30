#!/usr/bin/env python3
import hashlib, json, pathlib, tarfile, tempfile, urllib.request

ASSET_API = "https://api.github.com/repos/adoptium/temurin21-binaries/releases/assets/521122372"
ASSET_NAME = "OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
ASSET_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"
ASSET_SIZE = 207473347
ASSET_SHA256 = "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94"
JAVA_SHA256 = "2a207f5e7d075afa01d97f8048389a64432a44c4a5af0f5e77d6e286ec5f401d"

root = pathlib.Path(__file__).resolve().parents[3]
out = root / "build/reports/ferocity-current-java-distribution"
out.mkdir(parents=True, exist_ok=True)

def request(url, accept):
    return urllib.request.Request(
        url,
        headers={
            "User-Agent": "Argentum-Ferocity-Java-Provenance/1.0",
            "Accept": accept,
        },
    )

with urllib.request.urlopen(request(ASSET_API, "application/vnd.github+json"), timeout=60) as response:
    metadata_raw = response.read()
metadata = json.loads(metadata_raw)
assert metadata["id"] == 521122372
assert metadata["name"] == ASSET_NAME
assert metadata["size"] == ASSET_SIZE
assert metadata["digest"] == "sha256:" + ASSET_SHA256
assert metadata["browser_download_url"] == ASSET_URL

archive_hash = hashlib.sha256()
archive_bytes = 0
with tempfile.NamedTemporaryFile(prefix="ferocity-temurin-", suffix=".tar.gz", delete=False) as tmp:
    archive_path = pathlib.Path(tmp.name)
    with urllib.request.urlopen(request(ASSET_URL, "application/octet-stream"), timeout=120) as response:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            tmp.write(chunk)
            archive_hash.update(chunk)
            archive_bytes += len(chunk)

try:
    observed_archive_sha = archive_hash.hexdigest()
    assert archive_bytes == ASSET_SIZE, (archive_bytes, ASSET_SIZE)
    assert observed_archive_sha == ASSET_SHA256, (observed_archive_sha, ASSET_SHA256)

    with tarfile.open(archive_path, mode="r:gz") as tf:
        java_members = [m for m in tf.getmembers() if m.isfile() and m.name.endswith("/bin/java")]
        assert len(java_members) == 1, [m.name for m in java_members]
        java_member = java_members[0]
        java_stream = tf.extractfile(java_member)
        assert java_stream is not None
        java_hash = hashlib.sha256()
        java_bytes = 0
        while True:
            chunk = java_stream.read(1024 * 1024)
            if not chunk:
                break
            java_hash.update(chunk)
            java_bytes += len(chunk)
        observed_java_sha = java_hash.hexdigest()
        assert observed_java_sha == JAVA_SHA256, (observed_java_sha, JAVA_SHA256)

        release_members = [m for m in tf.getmembers() if m.isfile() and m.name.endswith("/release")]
        assert len(release_members) == 1, [m.name for m in release_members]
        release_member = release_members[0]
        release_stream = tf.extractfile(release_member)
        assert release_stream is not None
        release_bytes = release_stream.read()
        release_sha = hashlib.sha256(release_bytes).hexdigest()
        release_text = release_bytes.decode("utf-8")
        release_fields = {}
        for line in release_text.splitlines():
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            release_fields[key] = value.strip().strip('"')
        assert release_fields.get("IMPLEMENTOR") == "Eclipse Adoptium"
        assert release_fields.get("JAVA_VERSION") == "21.0.12.1"
        assert release_fields.get("OS_ARCH") in {"amd64", "x86_64"}

    result = {
        "schema": "ferocity-current-source08-java-distribution-verification-v1",
        "github_release_asset": {
            "id": metadata["id"],
            "name": metadata["name"],
            "size": metadata["size"],
            "digest": metadata["digest"],
            "browser_download_url": metadata["browser_download_url"],
            "updated_at": metadata.get("updated_at"),
        },
        "archive": {
            "bytes": archive_bytes,
            "sha256": observed_archive_sha,
        },
        "java_member": {
            "path": java_member.name,
            "bytes": java_bytes,
            "sha256": observed_java_sha,
        },
        "release_member": {
            "path": release_member.name,
            "sha256": release_sha,
            "fields": {
                "IMPLEMENTOR": release_fields.get("IMPLEMENTOR"),
                "JAVA_VERSION": release_fields.get("JAVA_VERSION"),
                "OS_ARCH": release_fields.get("OS_ARCH"),
                "OS_NAME": release_fields.get("OS_NAME"),
                "IMPLEMENTOR_VERSION": release_fields.get("IMPLEMENTOR_VERSION"),
                "JAVA_RUNTIME_VERSION": release_fields.get("JAVA_RUNTIME_VERSION"),
            },
        },
        "java_started": False,
        "gradle_started": False,
        "exporter_started": False,
        "seed_or_entropy_read": False,
        "allocation_read": False,
        "game_initialized": False,
        "decision_candidate": "EXACT_DISTRIBUTION_AND_JAVA_BYTES_MATCH_RECORDED_SOURCE08",
    }
    (out / "java-distribution-verification.json").write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    (out / "release-asset-metadata.json").write_bytes(metadata_raw)
    print(json.dumps({
        "archive_sha256": observed_archive_sha,
        "java_sha256": observed_java_sha,
        "os_arch": release_fields.get("OS_ARCH"),
        "java_version": release_fields.get("JAVA_VERSION"),
    }, sort_keys=True))
finally:
    archive_path.unlink(missing_ok=True)
