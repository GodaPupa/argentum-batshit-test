#!/usr/bin/env python3
"""Verify and summarize preserved Round 2 games. Never convert unfinished games to losses."""
import collections
import hashlib
import json
from pathlib import Path
import re
import statistics
import sys

root = Path(sys.argv[1])
games = []
for path in sorted(root.rglob('summary.json')):
    summary = json.loads(path.read_text())
    if 'arm' not in summary:
        continue
    assert summary['seed'] in range(202610190801, 202610190806)
    assert summary['candidateSeat'] in range(4)
    profiles = ['current'] * 4
    if summary['arm'] == 'candidate': profiles[summary['candidateSeat']] = 'manual-animar-development-r1'
    assert summary['profilesBySeat'] == profiles
    folder = path.parent
    rows = [json.loads(line) for line in (folder / 'actions.jsonl').read_text().splitlines()]
    submissions = [r for r in rows if r['record'] == 'submit']
    results = [r for r in rows if r['record'] == 'result']
    assert [r['index'] for r in submissions] == list(range(1, len(submissions) + 1))
    assert [r['index'] for r in results] == list(range(1, len(results) + 1))
    assert len(submissions) == summary['submitted']
    assert sum(r['error'] is None for r in results) == summary['accepted']
    if summary['status'] == 'COMPLETE':
        assert len(submissions) == len(results) == summary['accepted']
        assert summary['gameOver'] and summary['error'] is None
        final = json.loads((folder / 'final-state.json').read_text())
        assert final['gameOver'] and final.get('winnerId') == summary['winner']
        assert results[-1]['gameOver'] and results[-1]['winner'] == summary['winner']
        casts = {k: 0 for k in summary['commanderCasts']}
        for result in results:
            for event in result['events']:
                if event.startswith('SpellCastEvent(') and 'cardName=Animar, Soul of Elements,' in event:
                    casts[re.search(r'casterId=([^,]+),', event)[1]] += 1
        assert casts == summary['commanderCasts']
        assert final['turnNumber'] == summary['playerTurnNumber']
        for player, life in summary['life'].items():
            actual_life = final['entities'][player]['com.wingedsheep.engine.state.components.identity.LifeTotalComponent']['life']
            assert actual_life == life, (folder, player, actual_life, life)
        assert results[-1]['life'] == [summary['life'][p] for p in summary['life']]
        for player, n in summary['turnsByPlayer'].items():
            actual = final['entities'][player]['com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent'].get('count', 0)
            assert actual == n
    insights = [json.loads(line) for line in (folder / 'decisions.jsonl').read_text().splitlines()]
    adjusted = [d for d in insights if any('ManualAnimarDevelopmentAdvisor' in (o.get('note') or '') for o in d['options'])]
    if summary['arm'] == 'control':
        assert not adjusted, 'Candidate advisor activated in reference control'
    else:
        assert all(d['playerId'] == f"manual-mirror-{summary['candidateSeat']}" for d in adjusted)
    audit = json.loads((folder / 'registry-audit.json').read_text())
    assert not audit['issues'] and audit['cardCount'] == 100
    assert audit['deckSha256'] == '2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1'
    assert sum(c['quantity'] for c in audit['cards']) == 100
    if (folder / 'initial-state.json').exists():
        initial = json.loads((folder / 'initial-state.json').read_text())
        assert initial['format']['type'] == 'com.wingedsheep.sdk.core.Format.Commander'
        zones = {(initial['zones'][i]['ownerId'], initial['zones'][i]['zoneType']): initial['zones'][i+1] for i in range(0, len(initial['zones']), 2)}
        expected = collections.Counter({c['name'].replace('’', "'"): c['quantity'] for c in audit['cards']})
        for seat in range(4):
            player = f'manual-mirror-{seat}'; command = zones[(player, 'Command')]
            assert len(command) == 1 and len(zones[(player, 'Library')]) == 92 and len(zones[(player, 'Hand')]) == 7
            ids = command + zones[(player, 'Library')] + zones[(player, 'Hand')]
            assert len(set(ids)) == 100
            assert collections.Counter(initial['entities'][id]['com.wingedsheep.engine.state.components.identity.CardComponent']['name'].replace('’', "'") for id in ids) == expected
            assert initial['entities'][command[0]]['com.wingedsheep.engine.state.components.identity.CardComponent']['name'] == 'Animar, Soul of Elements'
            assert initial['entities'][player]['com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent']['commanderIds'] == command
            assert initial['entities'][player]['com.wingedsheep.engine.state.components.identity.LifeTotalComponent']['life'] == 40
        commander_ids = {initial['zones'][i+1][0] for i in range(0, len(initial['zones']), 2) if initial['zones'][i]['zoneType'] == 'Command'}
        actual_casts = {p: 0 for p in summary['commanderCasts']}
        for result in results:
            for event in result['events']:
                if not event.startswith('SpellCastEvent('): continue
                physical_id = re.search(r'spellEntityId=([^,]+),', event)[1]
                if 'cardName=Animar, Soul of Elements,' in event: assert physical_id in commander_ids
                if physical_id in commander_ids: actual_casts[re.search(r'casterId=([^,]+),', event)[1]] += 1
        assert actual_casts == summary['commanderCasts']


    # Observable finite sequence marker, not an inferred intention or infinite-loop proof.
    statue_casts = collections.Counter()
    statue_self_returns = 0
    ballista_casts = []
    accepted_indices = {r['index'] for r in results if r['error'] is None}
    for row in submissions:
        if row['index'] not in accepted_indices:
            continue
        action = json.loads(row['action'])
        if action['type'] == 'CastSpell' and row.get('sourceName') == 'Ancestral Statue':
            statue_casts[(row['actor'], row['turn'], action['cardId'])] += 1
        if action['type'] == 'CastSpell' and row.get('sourceName') == 'Walking Ballista':
            ballista_casts.append({'index': row['index'], 'actor': row['actor'], 'x': action.get('xValue', 0)})
    for row in results:
        statue_self_returns += sum(e.startswith('ZoneChangeEvent(') and 'entityName=Ancestral Statue,' in e and 'fromZone=BATTLEFIELD, toZone=HAND,' in e for e in row['events'])
    summary['traceFolder'] = str(folder.relative_to(root))
    summary['initialStateSha256'] = hashlib.sha256((folder / 'initial-state.json').read_bytes()).hexdigest() if (folder / 'initial-state.json').exists() else None
    summary['actionsSha256'] = hashlib.sha256((folder / 'actions.jsonl').read_bytes()).hexdigest()
    summary['advisorAdjustedWindows'] = len(adjusted)
    summary['advisorChosenWindows'] = sum(any(o.get('chosen') and 'ManualAnimarDevelopmentAdvisor' in (o.get('note') or '') for o in d['options']) for d in adjusted)
    changes = []
    creature_names = set()
    if (folder / 'initial-state.json').exists():
        for entity in json.loads((folder / 'initial-state.json').read_text())['entities'].values():
            card = entity.get('com.wingedsheep.engine.state.components.identity.CardComponent', {})
            if 'Creature' in card.get('typeLine', ''): creature_names.add(card.get('name'))
    for d in adjusted:
        chosen = next((o for o in d['options'] if o.get('chosen') and 'ManualAnimarDevelopmentAdvisor' in (o.get('note') or '')), None)
        if not chosen: continue
        other = max((o['score'] for o in d['options'] if o is not chosen), default=float('-inf'))
        if chosen.get('rawScore', chosen['score']) >= other: continue
        matches = [r for r in submissions if r['turn'] == d['turnNumber'] and r['actor'] == d['playerId'] and json.loads(r['action']) == chosen['action']]
        index = matches[0]['index'] if len(matches) == 1 else None
        followups = [r for r in submissions if index is not None and r['index'] > index and r['turn'] == d['turnNumber'] and r['actor'] == d['playerId'] and r['index'] in accepted_indices and json.loads(r['action'])['type'] == 'CastSpell' and r.get('sourceName') in creature_names]
        changes.append({'turn': d['turnNumber'], 'player': d['playerId'], 'actionIndex': index, 'rawScore': chosen['rawScore'], 'adjustedScore': chosen['score'], 'bestAlternativeScore': other, 'subsequentSameTurnCreatureCasts': [{'index': r['index'], 'card': r['sourceName']} for r in followups]})
    summary['advisorChangedRankingWindows'] = len(changes)
    summary['advisorChangedRankingDetails'] = changes
    summary['repeatedSameTurnStatueCastGroups'] = sum(n >= 2 for n in statue_casts.values())
    summary['statueBattlefieldToHandReturns'] = statue_self_returns
    summary['statueReturnDefinition'] = 'Observed Statue battlefield-to-hand zone transitions; does not establish its own ETB caused the return'
    summary['ballistaCasts'] = ballista_casts
    summary['ballistaActivations'] = sum(r['index'] in accepted_indices and r.get('sourceName') == 'Walking Ballista' and json.loads(r['action'])['type'] == 'ActivateAbility' for r in submissions)
    summary['eliminations'] = [{'index': r['index'], 'event': e} for r in results for e in r['events'] if e.startswith('PlayerLostEvent(')]
    games.append(summary)

cells = collections.defaultdict(dict)
for g in games:
    key = (g['seed'], g['candidateSeat'])
    assert g['arm'] not in cells[key], f'Duplicate {key} {g["arm"]}'
    cells[key][g['arm']] = g
for key, arms in cells.items():
    if len(arms) == 2 and all(g['initialStateSha256'] is not None for g in arms.values()):
        assert arms['candidate']['initialStateSha256'] == arms['control']['initialStateSha256'], key

stats = []
for seat in range(4):
    for arm in ['control', 'candidate']:
        subset = [g for g in games if g['candidateSeat'] == seat and g['arm'] == arm]
        completed = [g for g in subset if g['status'] == 'COMPLETE']
        id = f'manual-mirror-{seat}'
        times = [g['firstCommanderPersonalTurn'][id] for g in subset if id in g.get('firstCommanderPersonalTurn', {})]
        stats.append({'seat': seat + 1, 'arm': arm, 'scheduledCellsObserved': len(subset),
            'completed': len(completed), 'wins': sum(g['winner'] == id for g in completed),
            'commanderCasts': sum(g['commanderCasts'].get(id, 0) for g in subset),
            'legalCommanderOpportunities': sum(g['legalCommanderOpportunityDecisions'].get(id, 0) for g in subset),
            'legalCommanderDeclines': sum(g['legalCommanderDeclinedDecisions'].get(id, 0) for g in subset),
            'deploymentGames': len(times), 'meanPersonalTurnFirstCast': statistics.mean(times) if times else None,
            'meanPlayerTurns': statistics.mean(g['playerTurnNumber'] for g in completed) if completed else None,
            'failures': [g['error'] or g['status'] for g in subset if g['status'] != 'COMPLETE']})

out = {'gamesObserved': len(games), 'matchedCells': len(cells),
       'full40AttemptScheduleObserved': len(games) == 40 and set(cells) == {(seed, seat) for seed in range(202610190801, 202610190806) for seat in range(4)} and all(len(a) == 2 for a in cells.values()),
       'completedGames': sum(g['status'] == 'COMPLETE' for g in games), 'incompleteGames': sum(g['status'] != 'COMPLETE' for g in games), 'perSeat': stats, 'games': games}
print(json.dumps(out, indent=2))

