#!/usr/bin/env python3
"""Reassemble the unchanged PR206 archive after validating every retained part.

Only reads named sibling parts and creates one new output. It never overwrites
an existing file or executes the preserved source/tests/artifacts.
"""
import argparse
import hashlib
import json
from pathlib import Path

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    manifest = json.loads((root / "archive-parts.json").read_text())
    assert manifest["schema"] == "manual-pr206-lossless-archive-parts-v1"
    archive = manifest["archive"]
    assert Path(archive).name == archive and archive not in ("", ".", "..")
    digest = hashlib.sha256()
    offset = 0
    paths = []
    for row in manifest["parts"]:
        name = row["file"]
        assert Path(name).name == name and name not in ("", ".", "..")
        assert row["offset"] == offset
        assert 0 < row["bytes"] <= manifest["chunk_max_bytes"]
        path = root / name
        data = path.read_bytes()
        assert len(data) == row["bytes"]
        assert hashlib.sha256(data).hexdigest() == row["sha256"]
        paths.append((path, row))
        digest.update(data)
        offset += len(data)
    assert offset == manifest["bytes"]
    assert digest.hexdigest() == manifest["sha256"]
    output = args.output or (root / archive)
    written = 0
    final_digest = hashlib.sha256()
    with output.open("xb") as stream:
        for path, row in paths:
            data = path.read_bytes()
            assert len(data) == row["bytes"]
            assert hashlib.sha256(data).hexdigest() == row["sha256"]
            stream.write(data)
            written += len(data)
            final_digest.update(data)
    assert written == manifest["bytes"]
    assert final_digest.hexdigest() == manifest["sha256"]
    print(json.dumps({"output": str(output), "bytes": written,
                      "sha256": final_digest.hexdigest()}))

if __name__ == "__main__":
    main()
