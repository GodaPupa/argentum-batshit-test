#!/usr/bin/env python3
"""Verify internal consistency of a real mirror artifact; never infer game completion."""
import collections
import hashlib
import json
from pathlib import Path
import sys
import re

root = Path(sys.argv[1])
summary = json.loads((root / 'summary.json').read_text())
rows = [json.loads(line) for line in (root / 'actions.jsonl').read_text().splitlines()]
submits = [r for r in rows if r['record'] == 'submit']
results = [r for r in rows if r['record'] == 'result']
assert [r['index'] for r in submits] == list(range(1, len(submits) + 1))
assert [r['index'] for r in results] == list(range(1, len(results) + 1))
assert summary['submitted'] == len(submits)
assert summary['accepted'] == sum(r['error'] is None for r in results)
errors = [r for r in results if r['error'] is not None]
if summary['status'] == 'COMPLETE':
    final = json.loads((root / 'final-state.json').read_text())
    assert summary['gameOver'] and final['gameOver']
    assert final.get('winnerId') == summary['winner']
    assert len(submits) == len(results) == summary['accepted']
    assert not errors
    expected_casts = {k: 0 for k in summary['commanderCasts']}
    for row in results:
        for event in row['events']:
            if event.startswith('SpellCastEvent(') and 'cardName=Animar, Soul of Elements,' in event:
                caster = re.search(r'casterId=([^,]+),', event)[1]
                expected_casts[caster] += 1
    assert expected_casts == summary['commanderCasts']
    for player, turns in summary['turnsByPlayer'].items():
        component = final['entities'][player]['com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent']
        assert component['count'] == turns
    assert results[-1]['gameOver'] and results[-1]['winner'] == summary['winner']
audit = json.loads((root / 'registry-audit.json').read_text())
assert audit['cardCount'] == 100 and not audit['issues']
assert all(c['registered'] for c in audit['cards'])
assert sum(c['quantity'] for c in audit['cards']) == 100
out = {
    'consistency_verified': True,
    'summary': summary,
    'action_types': dict(collections.Counter(json.loads(r['action'])['type'] for r in submits)),
    'errors': errors,
    'artifact_file_sha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in sorted(root.iterdir()) if p.is_file()},
}
print(json.dumps(out, indent=2))
