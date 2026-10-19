#!/usr/bin/env python3
"""Compare verified summaries; hashes bind gameplay independently of branch heads."""
import json
import sys

previous, current = (json.load(open(p)) for p in sys.argv[1:3])
key = lambda g: (g['seed'], g['candidateSeat'], g['arm'])
old = {key(g): g for g in previous['games']}
rows = []
for game in current['games']:
    before = old[key(game)]
    assert before['initialStateSha256'] == game['initialStateSha256']
    same = before['actionsSha256'] == game['actionsSha256']
    if game['arm'] == 'control':
        assert same, 'CURRENT baseline changed'
    rows.append({
        'seed': game['seed'], 'candidateSeat': game['candidateSeat'], 'arm': game['arm'],
        'sameInitialState': True, 'sameActions': same,
        'previousStatus': before['status'], 'status': game['status'],
        'previousWinner': before['winner'], 'winner': game['winner'],
        'previousAccepted': before['accepted'], 'accepted': game['accepted'],
        'previousActionsSha256': before['actionsSha256'], 'actionsSha256': game['actionsSha256'],
    })
print(json.dumps({'comparisons': rows, 'sameActions': sum(r['sameActions'] for r in rows),
                  'changedGames': [r for r in rows if not r['sameActions']]}, indent=2))
