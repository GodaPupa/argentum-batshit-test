#!/usr/bin/env python3
"""Inspect immutable published fixed-process bytes; never launch a runtime."""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile

REPOSITORY = "GodaPupa/argentum-batshit-test"
SOURCE = "3a4f99a7653839506e96d19e6639f58d9e8c5ced"
OUTER_PATH = "ferocity-recycling/evidence/build/integrated-bundle-publication-v4-2/integrated-v4-2-bundle-04-part-01.zip"
OUTER_BYTES = 3077462
OUTER_GIT_BLOB = "a2d9f217df8112094bdb49f1319d6ae7d0f4d7f6"
OUTER_SHA256 = "b99335efcfc04767cce9ea34b074b76a73f1ce47050787f9c89fc90763c66e11"
INNER_PATH = "ferocity-recycling/evidence/build/integrated-v4-2-bundle-04/fixed-process-artifacts.zip"
INNER_BYTES = 223731
INNER_SHA256 = "004da1b12dde092b96623b31531f10d8291ad16fdba3c7d001c74d03fd6410de"
TARGET_SHA256 = "fa4cbc67fc5482d298ddf7e92968349b6eba584c4328c851d2bda86ce0277c5a"
SOURCE_FILES = [
    ".github/workflows/ferocity-historical-gift-retrieval.yml",
    "ferocity-recycling/tools/retrieve-historical-gift-template.py",
]


def sha256(data):
    return hashlib.sha256(data).hexdigest()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("The fixed public Git source must not redirect")


def inspect(data):
    rows = []
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        infos = archive.infolist()
        assert len(infos) <= 1000
        assert len({info.filename for info in infos}) == len(infos)
        assert sum(info.file_size for info in infos) <= 64 * 1024 * 1024
        assert all(info.file_size <= 16 * 1024 * 1024 for info in infos)
        for info in infos:
            # In-memory ZIP reads verify CRC. No member path is used for extraction.
            member = archive.read(info)
            rows.append({"path": info.filename, "bytes": len(member), "sha256": sha256(member)})
    return rows


def main():
    output = Path("output")
    output.mkdir(exist_ok=False)
    manifest = {
        "schema": "ferocity-historical-fixed-template-retrieval-v1",
        "status": "INCOMPLETE",
        "repository": REPOSITORY,
        "original_source": SOURCE,
        "original_path": OUTER_PATH,
        "original_git_blob": OUTER_GIT_BLOB,
        "original_sha256": OUTER_SHA256,
        "target_journal_sha256": TARGET_SHA256,
        "retrieval_run_id": os.environ["GITHUB_RUN_ID"],
        "retrieval_run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "retrieval_source": os.environ["EXPECTED_SOURCE"],
        "scope": "Read only one existing immutable Git archive and its fixed-process ZIP; no resource probe, engine, build, browser, seed allocation, or gameplay.",
        "resource_probe_commands": 0,
        "new_entropy_draws": 0,
        "new_game_initializations": 0,
    }
    try:
        assert os.environ["GITHUB_REPOSITORY"] == REPOSITORY
        assert os.environ["GITHUB_RUN_ATTEMPT"] == "1"
        head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        assert head == os.environ["EXPECTED_SOURCE"]
        tracked = subprocess.check_output(["git", "ls-files"], text=True).splitlines()
        assert sorted(tracked) == sorted(SOURCE_FILES)
        pins = {path: sha256(Path(path).read_bytes()) for path in SOURCE_FILES}
        manifest["retrieval_source_files"] = pins
        url = f"https://raw.githubusercontent.com/{REPOSITORY}/{SOURCE}/{OUTER_PATH}"
        request = urllib.request.Request(url, method="GET")
        with urllib.request.build_opener(NoRedirect).open(request, timeout=60) as response:
            assert response.status == 200 and response.geturl() == url
            data = response.read(OUTER_BYTES + 1)
        assert len(data) == OUTER_BYTES and sha256(data) == OUTER_SHA256
        blob = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
        assert blob == OUTER_GIT_BLOB
        (output / "original-published-archive.zip").write_bytes(data)
        manifest["outer_members"] = inspect(data)
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            inner = archive.read(INNER_PATH)
        assert len(inner) == INNER_BYTES and sha256(inner) == INNER_SHA256
        (output / "original-fixed-process-artifacts.zip").write_bytes(inner)
        manifest["inner_sha256"] = sha256(inner)
        manifest["inner_members"] = inspect(inner)
        manifest["target_matches"] = [row for row in manifest["inner_members"] if row["sha256"] == TARGET_SHA256]
        assert pins == {path: sha256(Path(path).read_bytes()) for path in SOURCE_FILES}
        assert head == subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        manifest["status"] = "EXISTING_FIXED_ARCHIVE_VERIFIED_AND_INSPECTED"
    except Exception as error:
        manifest["failure_type"] = type(error).__name__
        raise
    finally:
        (output / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
