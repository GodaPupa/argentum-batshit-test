"""Reassemble the unchanged retained Actions ZIP and verify every byte range."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parent
manifest = json.loads((root / "artifact-parts.json").read_text())
target = root / manifest["original_name"]
whole = hashlib.sha256()
offset = 0
with target.open("xb") as output:
    for part in manifest["parts"]:
        path = root / part["path"]
        if path.parent != root or part["offset"] != offset:
            raise ValueError("Part path or offset differs from the manifest")
        part_hash = hashlib.sha256()
        count = 0
        with path.open("rb") as source:
            while data := source.read(1024 * 1024):
                part_hash.update(data)
                whole.update(data)
                output.write(data)
                count += len(data)
        if count != part["bytes"] or part_hash.hexdigest() != part["sha256"]:
            raise ValueError("Part byte count or SHA256 differs: " + part["path"])
        offset += count
if offset != manifest["original_bytes"] or whole.hexdigest() != manifest["original_sha256"]:
    raise ValueError("Reassembled ZIP differs from the original artifact")
print(target.name, offset, whole.hexdigest())
