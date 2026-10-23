"""Recount card and frozen inventory evidence without trusting runner summaries."""
import hashlib, json, re
import xml.etree.ElementTree as ET


def receive_card_and_inventory(root, z, names, protocol, source, validation):
    gaps, card, inventory = [], {}, {}
    sha = lambda body: hashlib.sha256(body).hexdigest()
    read = lambda name: json.loads(z.read(name))
    review = json.loads((root / 'SOURCE_REVIEW.json').read_text())
    prefix = 'card/source-review/'
    if prefix+'RECEIPT.json' not in names:
        gaps.append('Fresh primary card-source receipt absent')
    else:
        receipt = read(prefix+'RECEIPT.json')
        card['primary_source_receipt'] = receipt
        assert receipt['source'] == source
        if receipt['status'] != 'PASS':
            gaps.append('Fresh primary card-source gate did not pass')
        else:
            for request in receipt['requests']:
                assert request['status'] == 200
                if request['method'] == 'GET':
                    body = z.read(prefix+request['file'])
                    assert len(body) == request['bytes'] and sha(body) == request['sha256']
            actual = read(prefix+'card.json')
            for field, expected in review['verified_fields'].items():
                value = actual['image_uris']['normal'] if field == 'image_uri' else actual[field]
                assert value == expected, field
            for field in ['id', 'oracle_id']:
                assert actual[field] == review['primary_source'][field]
            assert actual['colors'] == ['U'] and actual['set'] == 'nph'
            rulings = read(prefix+'rulings.json')
            old = json.loads((root / 'nph-phyrexian-metamorph-rulings.json').read_text())
            fields = ['oracle_id', 'source', 'published_at', 'comment']
            normalize = lambda data: [[row[k] for k in fields] for row in data['data']]
            assert not rulings['has_more'] and len(rulings['data']) == 8
            assert normalize(rulings) == normalize(old)
            printings = []
            pages = [request for request in receipt['requests'] if request.get('file', '').startswith('printings-')]
            assert pages
            for index, request in enumerate(pages):
                page = read(prefix+request['file'])
                assert bool(page['has_more']) == (index < len(pages)-1)
                if page['has_more']:
                    assert page['next_page'] == pages[index+1]['url']
                printings.extend(page['data'])
            eligible = [row for row in printings if row['set_type'] not in {'promo','token','art_series','memorabilia'}]
            earliest = min(eligible, key=lambda row:(row['released_at'], row['set']))
            assert earliest['set'] == 'nph' and earliest['collector_number'] == '42'
            assert any(r['method'] == 'HEAD' and r['url'] == actual['image_uris']['normal'] for r in receipt['requests'])
            assert receipt['card_sha256'] == review['draft_sha256']
            card['fields_and_rulings_reverified'] = True
    for name in ['printing-exit.txt', 'nph-differential-exit.txt']:
        path = 'card/'+name
        if path not in names:
            gaps.append('Missing card gate: '+name)
        else:
            card[name] = int(z.read(path).strip())
            if card[name] != 0:
                gaps.append('Nonzero card gate: '+name)
    if 'card/printing-cache.json' not in names:
        gaps.append('Fresh printing cache missing')
    else:
        card['printing_cache_sha256'] = sha(z.read('card/printing-cache.json'))
    for recipe in ['card-assay-parse', 'card-assay-explain']:
        log, exit_path = 'card/'+recipe+'.log', 'card/'+recipe+'-exit.txt'
        if log not in names or exit_path not in names:
            gaps.append('Card Assay evidence missing: '+recipe)
        else:
            text = z.read(log).decode()
            card[recipe] = {'exit': int(z.read(exit_path).strip()), 'log_sha256': sha(z.read(log)),
                            'text': text, 'interpretation': 'Requires explicit reading; parser decline is a retained limitation, never engine or competitive evidence.'}
    if 'card/nph-differential.log' in names:
        text = z.read('card/nph-differential.log').decode()
        card['nph_differential'] = {'sha256': sha(z.read('card/nph-differential.log')), 'text': text,
            'divergent_cards': re.findall(r'^  (.+)  \[([A-Z0-9]+)\]$', text, re.M)}
        if card['nph_differential']['divergent_cards']:
            gaps.append('NPH differential requires explicit semantic classification')
    expected_golden = protocol['source_manifest']['mtg-sets/src/test/resources/snapshots/cards/NPH.json']['sha256']
    if 'card/NPH.candidate.json' not in names:
        gaps.append('Candidate NPH bytes missing')
    else:
        current = z.read('card/NPH.candidate.json')
        assert sha(current) == expected_golden
        baseline = (root / 'NPH.baseline.json').read_bytes()
        assert sha(baseline) == protocol['source_manifest']['mtg-sets/src/test/resources/snapshots/cards/NPH.json']['baseline_sha256']
        def blocks(body):
            headers = list(re.finditer(rb'^// ([^\n]+)\n', body, re.M))
            return {header.group(1).decode(): body[header.start():headers[i+1].start() if i+1 < len(headers) else len(body)]
                    for i, header in enumerate(headers)}
        old_blocks, new_blocks = blocks(baseline), blocks(current)
        assert len(old_blocks) == 9 and len(new_blocks) == 10
        assert set(new_blocks)-set(old_blocks) == {'Phyrexian Metamorph'}
        assert all(new_blocks[name] == body for name, body in old_blocks.items())
        card['candidate_nph_sha256'] = expected_golden
        card['original_nph_blocks_byte_identical'] = 9
    mismatches = sorted(n for n in names if n.startswith('card/snapshots-actual/') and n.endswith('.json'))
    card['actual_snapshot_mismatches'] = mismatches
    if mismatches:
        gaps.append('Actual exporter snapshot mismatch artifacts require review')

    prefix = 'registry-inventory/'
    if prefix+'SOURCE_BOUND_RECEIPT.json' not in names:
        gaps.append('Source-bound frozen registry inventory missing')
        return card, inventory, gaps
    received = read(prefix+'SOURCE_BOUND_RECEIPT.json')
    assert received['source'] == source and received['validation'] == validation
    assert received['actual_games_initialized'] == received['official_seeds'] == 0
    assert received['precision_reserved'] == 2 and received['fresh_cases'] == 1
    inventory['source_bound_receipt'] = received
    for name, expected in protocol['registry_inventory']['frozen_source_files'].items():
        body = z.read(prefix+'source/'+name)
        assert len(body) == expected['bytes'] and sha(body) == expected['sha256']
    request = read(prefix+'source/registry-requests.json')
    actual = read(prefix+'registry/registry-report.json')
    assert actual['actual_deck_games_initialized'] == actual['official_outcomes'] == 0
    assert actual['execution_allowed'] is False
    xml = z.read(prefix+'TEST-com.wingedsheep.gym.ManualTransmissionPhaseTwoRegistryAuditTest.xml')
    suite = ET.fromstring(xml)
    assert int(suite.get('tests')) == len(suite.findall('.//testcase')) == 1
    assert all(int(suite.get(k)) == 0 for k in ['failures','errors','skipped'])
    assert not any(suite.findall('.//'+tag) for tag in ['failure','error','skipped'])
    assert sha(xml) == received['junit_sha256']
    assert int(z.read(prefix+'exit.txt').strip()) == 0
    decks, locations = [], []
    assert len(request['decks']) == len(actual['decks']) == 8
    for wanted, have in zip(request['decks'], actual['decks']):
        assert wanted['id'] == have['deck_id']
        wanted_names = wanted['commanders'] + wanted['mainboard']
        assert len(wanted_names) == len(have['cards']) == have['physical_cards'] == 100
        resolved, missing = 0, []
        for index, (name, row) in enumerate(zip(wanted_names, have['cards'])):
            assert row['requested_name'] == name and row['index'] == index
            assert row['rules_qualified'] is False
            if row['registry_resolved']:
                definition = read(prefix+'registry/'+have['deck_id']+'-'+str(index)+'.json')
                assert definition['name'] == row['resolved_name']
                resolved += 1
            else:
                assert row['resolved_name'] is None
                missing.append(name)
            if name == 'Phyrexian Metamorph':
                assert row['registry_resolved'] and row['resolved_name'] == name
                locations.append(have['deck_id'])
        decks.append({'deck': have['deck_id'], 'registered': resolved, 'missing': missing})
    assert locations == protocol['registry_inventory']['required_card_locations']
    assert received['metamorph_registered_in'] == locations
    inventory['decks'] = decks
    inventory['registered_slots'] = sum(d['registered'] for d in decks)
    inventory['manual_registered'] = decks[0]['registered']
    inventory['opponent_registered'] = sum(d['registered'] for d in decks[1:])
    inventory['physical_slots'] = 800
    path = prefix+'COMPOSED_R1_LIVE_REGISTRY_GATE.json'
    if path not in names:
        gaps.append('Original composed registry audit missing')
    else:
        original = read(path)
        assert original['qualified_candidate_source_sha'] == validation
        assert original['totals']['registered_slots'] == inventory['registered_slots']
        assert original['totals']['manual_registered'] == inventory['manual_registered']
        assert original['totals']['opponent_registered'] == inventory['opponent_registered']
        assert original['actual_games_initialized'] == original['official_results'] == 0
        assert original['precision_reserved_games_untouched'] == 2
        assert original['mechanics_qualification'] is False
        inventory['original_gate'] = original
    inventory['status'] = 'REGISTRATION_ONLY_NO_RULES_PILOT_OR_GAMEPLAY_ADMISSION'
    return card, inventory, gaps
