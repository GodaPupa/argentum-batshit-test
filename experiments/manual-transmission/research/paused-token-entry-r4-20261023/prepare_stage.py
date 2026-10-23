"""Preserve then clear previous-stage JUnit XML; no stale result may enter a new gate."""
from pathlib import Path
import hashlib
import json
import os
import shutil

stage = os.environ['STAGE']
assert stage in ('focused', 'broad', 'ordinary')
out = Path('paused-token-r4-evidence') / stage
out.mkdir(parents=True, exist_ok=True)
archive = out / 'preexisting-junit'
records = []
for path in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml')):
    if 'paused-token-r4-evidence' in path.parts:
        continue
    payload = path.read_bytes()
    target = archive / path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
    records.append(dict(path=str(path), bytes=len(payload), sha256=hashlib.sha256(payload).hexdigest()))
    path.unlink()
(out / 'PREEXISTING_REPORTS.json').write_text(json.dumps(records, indent=2) + '\n')
print('Preserved and cleared', len(records), 'prior JUnit reports before', stage)
