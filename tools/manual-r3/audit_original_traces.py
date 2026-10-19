#!/usr/bin/env python3
"""Extract every Statue cycle and exact Encore rejection from immutable r2 ZIPs."""
import hashlib
import json
import re
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
out = root / 'gameplay/manual-r3/original-audit'
out.mkdir(parents=True, exist_ok=True)
for seat, lane in [(1, 'statue'), (3, 'encore')]:
    archive = root / f'gameplay/manual-continuation-r2/evidence/cells/manual-continuation-seed-0-seat-{seat}-39207390012.zip'
    with zipfile.ZipFile(archive) as z:
        member = next(n for n in z.namelist() if n.endswith('/candidate/actions.jsonl'))
        rows = [json.loads(line) for line in z.read(member).splitlines()]
        summary = json.loads(z.read(member.replace('actions.jsonl', 'summary.json')))
    submits = {r['index']: r for r in rows if r['record'] == 'submit'}
    results = {r['index']: r for r in rows if r['record'] == 'result'}
    receipt = {'evidenceCommit': 'a3a315bdd21d083099f05d89e45c3122498f8662',
               'testedSource': '72749eed76c44de163ba5d91575de0d008e87391',
               'archive': str(archive.relative_to(root)),
               'archiveSha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'summary': summary}
    if lane == 'statue':
        casts = [s for s in submits.values() if s.get('sourceName') == 'Ancestral Statue']
        cycles = []
        for s in casts:
            i = s['index']; end = i + 14
            window = [r for j, r in results.items() if i <= j < end]
            returns = [r for r in window if any(e.startswith('ZoneChangeEvent(entityId=e341,') and 'fromZone=BATTLEFIELD, toZone=HAND,' in e for e in r['events'])]
            spent = [int(m[1]) for e in results[i]['events'] if (m := re.search(r'totalManaSpent=(\d+)', e))]
            counters = [r['index'] for r in window if any(e.startswith('CountersAddedEvent(entityId=e300,') for e in r['events'])]
            cycles.append({'castIndex': i, 'turn': s['turn'], 'actor': s['actor'], 'manaSpent': spent,
                           'counterEventIndices': counters, 'returnIndices': [r['index'] for r in returns],
                           'returnSelections': [json.loads(submits[r['index']]['action']) for r in returns],
                           'lifeVectors': list({tuple(r['life']) for r in window})})
        assert len(casts) == 214 and sum(len(c['returnIndices']) for c in cycles) == 213
        assert all(c['turn'] == 32 and c['actor'] == 'manual-mirror-3' for c in cycles)
        assert all(v == (36, 40, 38, 38) for c in cycles for v in c['lifeVectors'])
        receipt.update(cycles=cycles, casts=len(casts), returns=213,
                       incompleteLastCast=cycles[-1]['castIndex'],
                       payoffObservation='Animar counters increase; no observed life progress; final cast unfinished at resource cap')
    else:
        i = 3562
        assert results[i]['error'] == 'Amphin Mutineer must attack its designated defender this turn if able'
        receipt.update(rejectedSubmission=submits[i], rejection=results[i],
                       lastAcceptedIndex=i-1, engineRecordedEvents=[])
    (out / f'{lane}.json').write_text(json.dumps(receipt, indent=2)+'\n')
print('Verified all 214 casts/213 returns and exact rejected attack 3562')
