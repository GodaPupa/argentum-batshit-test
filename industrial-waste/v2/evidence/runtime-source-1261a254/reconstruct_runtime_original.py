"""Reassemble retained source1261 Actions ZIP; never execute its contents."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parent
manifest = json.loads((root / "runtime-publication-parts.json").read_text())
prefix = "industrial-waste/v2/evidence/runtime-source-1261a254/"
target = root / "runtime-original.zip"
whole = hashlib.sha256()
offset = 0
with target.open("xb") as output:
    for index, part in enumerate(manifest["parts"]):
        name = f"runtime-original.zip.part-{index:03d}"
        if part["path"] != prefix + name or part["index"] != index or part["offset"] != offset:
            raise ValueError("Unexpected part identity, path, order or offset")
        part_hash = hashlib.sha256()
        blob_hash = hashlib.sha1(f"blob {part['bytes']}\0".encode())
        count = 0
        with (root / name).open("rb") as source:
            while data := source.read(1024 * 1024):
                part_hash.update(data)
                blob_hash.update(data)
                whole.update(data)
                output.write(data)
                count += len(data)
        if count != part["bytes"] or part_hash.hexdigest() != part["sha256"] or blob_hash.hexdigest() != part["git_blob"]:
            raise ValueError("Part bytes or digest differ: " + name)
        offset += count
if offset != manifest["original_bytes"] or whole.hexdigest() != manifest["original_sha256"]:
    raise ValueError("Reassembled bytes differ from the original artifact")
with zipfile.ZipFile(target) as archive:
    if archive.testzip() is not None:
        raise ValueError("Original ZIP member CRC failed")
print(target.name, offset, whole.hexdigest())
