#!/usr/bin/env python3
"""Reproduce the archived comparison; this normalization is never used by an engine or test."""

import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parent
receipt = json.loads((root / "receipt.json").read_text())
streams = []
for archive, stream_name in (
    ("accepted-main.zip", "accepted-main-action-stream.txt"),
    ("failed-shared-receiver.zip", "failed-shared-receiver-action-stream.txt"),
):
    raw = (root / archive).read_bytes()
    expected = receipt["artifacts"][archive]
    assert len(raw) == expected["bytes"]
    assert hashlib.sha256(raw).hexdigest() == expected["sha256"]
    with ZipFile(root / archive) as zipped:
        assert zipped.testzip() is None
        stream = zipped.read("action-stream.txt")
    assert stream == (root / stream_name).read_bytes()
    streams.append(stream.decode().splitlines(keepends=True))
old, new = streams
assert len(old) == len(new) == 413
reconstructed = old.copy()
changed = set()
for index, declaration in enumerate(old):
    if "|DeclareBlockers(" not in declaration:
        continue
    assert declaration == new[index]
    before = [line.split("|", 1) for line in old[index + 1:index + 3]]
    after = [line.split("|", 1) for line in new[index + 1:index + 3]]
    assert len(before) == len(after) == 2
    assert all("|DECLARE_BLOCKERS|PassPriority(" in "|" + row[1] for row in before + after)
    assert [row[0] for row in before] == [row[0] for row in after]
    assert before[0][1] == after[1][1] and before[1][1] == after[0][1]
    defender = declaration.split("|")[1]
    assert before[0][1].split("|")[0] == defender
    assert after[0][1].split("|")[0] != defender
    reconstructed[index + 1] = before[0][0] + "|" + before[1][1]
    reconstructed[index + 2] = before[1][0] + "|" + before[0][1]
    changed.update((index + 1, index + 2))
assert len(changed) == 14
assert changed == {i for i, (a, b) in enumerate(zip(old, new)) if a != b}
assert reconstructed == new
assert hashlib.sha256("".join(old).encode()).hexdigest()[:16] == receipt["historical_hash"]
assert hashlib.sha256("".join(new).encode()).hexdigest()[:16] == receipt["proposed_successor_hash"]
assert old[-1] == new[-1]
print("Exact proof: seven post-block pass-pair reversals, 14 changed and 399 identical records.")
