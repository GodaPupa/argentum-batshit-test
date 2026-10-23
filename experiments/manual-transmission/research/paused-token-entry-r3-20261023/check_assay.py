"""Require touchstone metrics and unchanged original differential blocks; no normalization."""
from pathlib import Path
import hashlib
import json
import os
import re

record = Path(os.environ['RECORD'])
root = Path('paused-token-r3-evidence/assay')
root.mkdir(parents=True, exist_ok=True)
report = {'status': 'INCOMPLETE', 'remaining_gates': [], 'metrics': {}}
for name in ['build-exit.txt', 'gate-exit.txt', 'differential-exit.txt', 'corpus-capture-exit.txt']:
    if not (root / name).exists() or int((root / name).read_text()) != 0:
        report['remaining_gates'].append(name + ' missing or nonzero')
gate = (root / 'gate.log').read_text() if (root / 'gate.log').exists() else ''
for label in ['Cards assayed', 'Ambiguous — distinct readings', 'Print mismatch',
              'Normalization not invertible', 'Full inverse not reproduced', 'Redundant readings (same model)']:
    values = [line.strip()[len(label):].strip() for line in gate.splitlines() if line.strip().startswith(label)]
    if len(values) != 1:
        report['remaining_gates'].append('Missing or duplicate metric: ' + label)
        continue
    match = re.match(r'[\s:]*([0-9]+)', values[0])
    value = int(match.group(1)) if match else None
    report['metrics'][label] = value
    if value != (2000 if label == 'Cards assayed' else 0):
        report['remaining_gates'].append('Unexpected metric: ' + label)
corpus = root / 'oracle-bulk-corpus.jsonl.gz'
if corpus.exists():
    data = corpus.read_bytes()
    report['corpus'] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
    if report['corpus']['sha256'] != (root / 'oracle-bulk-sha256.txt').read_text().split()[0]:
        report['remaining_gates'].append('Raw corpus SHA mismatch')
    if len(data) != int((root / 'oracle-bulk-bytes.txt').read_text().split()[0]):
        report['remaining_gates'].append('Raw corpus length mismatch')
else:
    report['remaining_gates'].append('Full raw corpus missing')
def blocks(text):
    headers = list(re.finditer(r'^  (.+)  \[([A-Z0-9]+)\]$', text, re.M))
    result = {}
    for index, header in enumerate(headers):
        name = header.group(1) + ' [' + header.group(2) + ']'
        assert name not in result
        result[name] = text[header.end():headers[index + 1].start() if index + 1 < len(headers) else len(text)].strip()
    return result
if (root / 'differential.log').exists():
    baseline = (record / 'BASELINE_DIFFERENTIAL.log').read_bytes()
    current = (root / 'differential.log').read_bytes()
    old, new = blocks(baseline.decode()), blocks(current.decode())
    report['differential'] = dict(baseline_artifact_id=12469559629,
        baseline_report_sha256=hashlib.sha256(baseline).hexdigest(),
        current_report_sha256=hashlib.sha256(current).hexdigest(),
        baseline_count=len(old), current_count=len(new), added=sorted(new.keys() - old.keys()),
        removed=sorted(old.keys() - new.keys()), changed=sorted(key for key in old.keys() & new.keys() if old[key] != new[key]),
        original_blocks_byte_identical=(old == new), unresolved_original_cards=sorted(old))
    if old != new or len(new) != 51:
        report['remaining_gates'].append('Original51 differential blocks changed; explicit review required')
else:
    report['remaining_gates'].append('Differential report missing')
if not report['remaining_gates']:
    report['status'] = 'TOUCHSTONE_PASS_ORIGINAL_51_DIVERGENCES_RETAINED'
(root / 'RECEIVING.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
assert not report['remaining_gates'], report['remaining_gates']
