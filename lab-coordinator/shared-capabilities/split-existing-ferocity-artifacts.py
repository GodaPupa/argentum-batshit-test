#!/usr/bin/env python3
"""Copy two existing Actions ZIPs into independently verifiable transport chunks.

This script never builds, tests, initializes an engine, allocates a seed, or
executes a game. Original run artifacts remain the evidence authority.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

REPOSITORY = "GodaPupa/argentum-batshit-test"
HEAD = "d097c9eff09dd402dfacb644351bfb9d62f35aff"
BRANCH = "ferocity-recycling/qualification-review"
CHUNK_BYTES = 24 * 1024 * 1024
BANK = {
    10911949741: {
        "run_id": 36260766617,
        "name": "ferocity-combat-browser-36260766617-1",
        "bytes": 80849874,
        "sha256": "75fa18e454d96e65ff5a6c33930864b18bb0ec1701de1a1453742556dc97c968",
    },
    10912261986: {
        "run_id": 36260771329,
        "name": "ferocity-recycling-gym-36260771329-1",
        "bytes": 95091910,
        "sha256": "2b09d576f10efdcf836100665fc2660dc6320d555ac3a42caa850de3210fd1ca",
    },
}


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        while data := stream.read(1024 * 1024):
            h.update(data)
    return h.hexdigest()


def api(suffix):
    # The official GitHub CLI supplies authentication and follows the authorized
    # artifact redirect. Tokens and signed redirects are never written to output.
    result = subprocess.run(
        ["gh", "api", "--method", "GET", f"repos/{REPOSITORY}/{suffix}"],
        check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60,
    )
    return json.loads(result.stdout)


def main():
    artifact_id = int(sys.argv[1])
    expected = BANK[artifact_id]
    output = Path("output") / str(artifact_id)
    output.mkdir(parents=True, exist_ok=False)
    manifest_path = output / "manifest.json"
    manifest = {
        "schema": "ferocity-existing-artifact-byte-transport-v1",
        "status": "INCOMPLETE",
        "repository": REPOSITORY,
        "artifact_id": artifact_id,
        "expected_original": expected,
        "evidence_source_head": HEAD,
        "evidence_source_branch": BRANCH,
        "retrieval_run_id": os.environ["GITHUB_RUN_ID"],
        "retrieval_run_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
        "retrieval_source_head": os.environ["EXPECTED_SOURCE"],
        "scope": "Transport of already-generated original ZIP bytes only; no qualification or gameplay execution.",
        "chunks": [],
    }
    try:
        assert os.environ["GITHUB_REPOSITORY"] == REPOSITORY
        assert os.environ["GITHUB_RUN_ATTEMPT"] == "1"
        actual_source = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        assert actual_source == os.environ["EXPECTED_SOURCE"]
        source_files = [
            ".github/workflows/ferocity-existing-artifact-transfer.yml",
            "lab-coordinator/shared-capabilities/split-existing-ferocity-artifacts.py",
        ]
        tracked = subprocess.check_output(["git", "ls-files"], text=True).splitlines()
        assert sorted(tracked) == sorted(source_files), "Retrieval source must contain only its two reviewed files"
        manifest["retrieval_source_files"] = {p: digest(Path(p)) for p in source_files}

        metadata = api(f"actions/artifacts/{artifact_id}")
        run = api(f"actions/runs/{expected['run_id']}")
        assert metadata["id"] == artifact_id and not metadata["expired"]
        assert metadata["name"] == expected["name"]
        assert metadata["size_in_bytes"] == expected["bytes"]
        assert metadata["digest"] == "sha256:" + expected["sha256"]
        for item in [metadata["workflow_run"], run]:
            assert item["id"] == expected["run_id"]
            assert item["head_sha"] == HEAD and item["head_branch"] == BRANCH
        assert run["run_attempt"] == 1
        assert run["status"] == "completed" and run["conclusion"] == "success"
        manifest["verified_original_metadata"] = {
            key: metadata[key] for key in ["id", "name", "size_in_bytes", "digest", "expired", "created_at"]
        }
        manifest["verified_original_run"] = {
            key: run[key] for key in ["id", "head_sha", "head_branch", "run_attempt", "status", "conclusion"]
        }

        original = output / "original.zip"
        with original.open("xb") as stream:
            subprocess.run(
                ["gh", "api", "--method", "GET", f"repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"],
                check=True, stdout=stream, stderr=subprocess.PIPE, timeout=180,
            )
        assert original.stat().st_size == expected["bytes"]
        assert digest(original) == expected["sha256"]
        with zipfile.ZipFile(original) as archive:
            assert archive.testzip() is None
        offset = 0
        combined = hashlib.sha256()
        with original.open("rb") as stream:
            for index in range(4):
                data = stream.read(CHUNK_BYTES)
                assert data and len(data) < 32 * 1024 * 1024
                part = output / f"part-{index:02d}.bin"
                with part.open("xb") as target:
                    target.write(data)
                combined.update(data)
                manifest["chunks"].append({
                    "index": index, "file": part.name, "offset": offset,
                    "bytes": len(data), "sha256": digest(part),
                })
                offset += len(data)
            assert stream.read(1) == b"", "The fixed four chunks must contain the complete original ZIP"
        assert offset == expected["bytes"]
        assert combined.hexdigest() == expected["sha256"]
        assert digest(original) == expected["sha256"]
        assert actual_source == subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        assert manifest["retrieval_source_files"] == {p: digest(Path(p)) for p in source_files}
        manifest["status"] = "VERIFIED_ORIGINAL_ZIP_SPLIT"
        manifest["verified_reassembly_sha256"] = combined.hexdigest()
    except Exception as error:
        manifest["failure_type"] = type(error).__name__
        # Do not record exception text from authenticated network subprocesses.
        raise
    finally:
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
