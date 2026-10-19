#!/usr/bin/env python3
"""Offline archive/source binding verification; does not rerun games."""
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[2]
receipts = json.loads((Path(__file__).parent / "artifact-receipts.json").read_text())
for receipt in receipts:
    archive = root / receipt["localPath"]
    data = archive.read_bytes()
    assert len(data) == receipt["size_in_bytes"], archive
    assert "sha256:" + hashlib.sha256(data).hexdigest() == receipt["digest"], archive
    with ZipFile(archive) as z:
        sources = [n for n in z.namelist() if n.endswith("source-sha.txt")]
        assert len(sources) == 1, (archive, sources)
        assert z.read(sources[0]).decode().strip() == receipt["workflow_run"]["head_sha"], archive
    print(f"VERIFIED {archive.name}: {receipt['workflow_run']['head_sha']}")
