#!/usr/bin/env python3
"""Check preserved artifacts and actual engine trace receipts; never infer missing wins."""
import collections
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[2]
base = root / 'gameplay/manual-r3'
receipts = json.loads((base / 'ARTIFACT_RECEIPTS.json').read_text())
verified = []
for receipt in receipts:
    path = root / receipt['file']
    assert path.stat().st_size == receipt['size']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == receipt['sha256']
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        def read(suffix):
            matches = [n for n in names if n.endswith(suffix)]
            assert len(matches) == 1, matches
            return z.read(matches[0])
        assert read('/source-sha.txt').decode().strip() == receipt['source']
        preserved = [n for n in names if n.endswith('/preserved-inputs.sha256')]
        if preserved:
            rows = dict((line.split()[1], line.split()[0]) for line in z.read(preserved[0]).decode().splitlines())
            assert rows['experiments/manual-transmission/control/v0.7.decklist.txt'] == '2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1'
            assert rows['ai/src/main/kotlin/com/wingedsheep/ai/engine/advisor/modules/ManualAnimarDevelopmentAdvisorModule.kt'] == '62ee5847e8f76d5fe4fc3ebe4f115681eecca1805c9e42c237f34915e2522650'
        tests = [ET.fromstring(z.read(n)) for n in names if n.endswith('.xml') and '/TEST-' in n]
        row = dict(receipt, tests=sum(int(t.attrib['tests']) for t in tests),
                   testFailures=sum(int(t.attrib['failures'])+int(t.attrib.get('errors', 0)) for t in tests))
        if receipt['stage'] != 'smoke':
            verified.append(row)
            continue
        summary = json.loads(read('/summary.json'))
        initial = json.loads(read('/initial-state.json'))
        final = json.loads(read('/final-state.json'))
        actions = [json.loads(l) for l in read('/actions.jsonl').splitlines()]
        assert final['gameOver'] == (summary['status'] == 'COMPLETE')
        assert final.get('winnerId') == summary['winner']
        life = {p: final['entities'][p]['com.wingedsheep.engine.state.components.identity.LifeTotalComponent']['life'] for p in final['turnOrder']}
        assert life == summary['life']
        if receipt['lane'] == 'statue':
            submits = [a for a in actions if a['record'] == 'submit']
            results = [a for a in actions if a['record'] == 'result']
            assert [a['index'] for a in submits] == list(range(1, summary['submitted']+1))
            assert len(results) == summary['submitted']
            assert sum(a['error'] is None for a in results) == summary['accepted']
            assert all(a['error'] is None for a in results)
            audit = json.loads(read('/registry-audit.json'))
            assert not audit['issues'] and audit['cardCount'] == 100
            assert audit['deckSha256'] == '2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1'
            assert initial['format']['type'] == 'com.wingedsheep.sdk.core.Format.Commander'
            zones = {(initial['zones'][i]['ownerId'], initial['zones'][i]['zoneType']): initial['zones'][i+1] for i in range(0, len(initial['zones']), 2)}
            expected = collections.Counter({c['name'].replace('’', "'"): c['quantity'] for c in audit['cards']})
            for p in initial['turnOrder']:
                command, library, hand = (zones[p, zone] for zone in ['Command', 'Library', 'Hand'])
                assert (len(command), len(library), len(hand)) == (1, 92, 7)
                assert initial['entities'][command[0]]['com.wingedsheep.engine.state.components.identity.CardComponent']['name'] == 'Animar, Soul of Elements'
                assert initial['entities'][p]['com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent']['commanderIds'] == command
                assert initial['entities'][p]['com.wingedsheep.engine.state.components.identity.LifeTotalComponent']['life'] == 40
                assert collections.Counter(initial['entities'][i]['com.wingedsheep.engine.state.components.identity.CardComponent']['name'].replace('’', "'") for i in command+library+hand) == expected
            old_path = root / 'gameplay/manual-continuation-r2/evidence/cells/manual-continuation-seed-0-seat-1-39207390012.zip'
            with zipfile.ZipFile(old_path) as old:
                prefix = 'continuation-output/games/seed-0-seat-1/candidate/'
                assert old.read(prefix+'initial-state.json') == read('/initial-state.json')
                original = [json.loads(l) for l in old.read(prefix+'actions.jsonl').splitlines() if json.loads(l)['record'] == 'submit']
            differences = [a['index'] for a, b in zip(submits, original) if a['action'] != b['action']]
            assert differences[0] == 1345
            assert json.loads(submits[1344]['action'])['response']['selectedCards'] == ['e304']
            assert len([a for a in submits if a['sourceName'] == 'Ancestral Statue']) == 1
            assert not any('entityName=Ancestral Statue,' in e and 'fromZone=BATTLEFIELD, toZone=HAND,' in e for a in results for e in a['events'])
            row['sameOriginalInitialState'] = True
            row['firstChangedAction'] = 1345
            row['statueCasts'] = 1
            row['statueBattlefieldToHandReturns'] = 0
        else:
            assert [a['index'] for a in actions] == list(range(3562, 3562+len(actions)))
            assert sum(a['error'] is None for a in actions) == summary['continuationAccepted']
            if summary['status'] == 'ENGINE_REJECTION':
                assert actions[-1]['error'] == summary['error']
            else:
                assert all(a['error'] is None for a in actions)
            first = json.loads(actions[0]['action'])
            assert first['attackers']['e756'] == 'manual-mirror-1'
            assert first['attackers']['e757'] == 'manual-mirror-3'
            row['archivedAcceptedPrefix'] = 3561
            old_path = root / 'gameplay/manual-continuation-r2/evidence/cells/manual-continuation-seed-0-seat-3-39207390012.zip'
            with zipfile.ZipFile(old_path) as old:
                prefix = 'continuation-output/games/seed-0-seat-3/candidate/'
                assert json.loads(old.read(prefix+'final-state.json')) == initial
                old_results = [json.loads(l) for l in old.read(prefix+'actions.jsonl').splitlines() if json.loads(l)['record'] == 'result' and json.loads(l)['index'] <= 3561]
                assert len(old_results) == 3561 and all(a['error'] is None for a in old_results)
                prefix_casts = {p: 0 for p in initial['turnOrder']}
                for a in old_results:
                    for event in a['events']:
                        if event.startswith('SpellCastEvent(') and 'cardName=Animar, Soul of Elements,' in event:
                            prefix_casts[re.search(r'casterId=([^,]+)', event)[1]] += 1
            row['sameOriginalCheckpoint'] = True
        event_rows = results if receipt['lane'] == 'statue' else actions
        casts = {p: 0 for p in initial['turnOrder']}
        for a in event_rows:
            for event in a['events']:
                if event.startswith('SpellCastEvent(') and 'cardName=Animar, Soul of Elements,' in event:
                    casts[re.search(r'casterId=([^,]+)', event)[1]] += 1
        if 'commanderCasts' in summary:
            assert casts == summary['commanderCasts']
        row['commanderCastsInTrace'] = casts
        if receipt['lane'] == 'encore':
            row['prefixCommanderCasts'] = prefix_casts
            row['wholeBranchedGameCommanderCasts'] = {p: prefix_casts[p]+casts[p] for p in casts}
            row['wholeBranchedGameAccepted'] = 3561 + summary['continuationAccepted']
        row['finalPersonalTurns'] = {p: final['entities'][p].get('com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent', {}).get('count', 0) for p in final['turnOrder']}
        row.update(summary=summary, actionsSha256=hashlib.sha256(read('/actions.jsonl')).hexdigest(),
                   decisionsSha256=hashlib.sha256(read('/decisions.jsonl')).hexdigest(), verifiedFinalLife=life)
        verified.append(row)
print(json.dumps(verified, indent=2))
