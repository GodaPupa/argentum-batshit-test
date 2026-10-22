"""Prepare or receive the original registry-only test, without initializing games."""
from pathlib import Path
import hashlib, json, os, shutil, sys
import xml.etree.ElementTree as ET

record = Path(os.environ['RECORD'])
out = Path('counter-metamorph-evidence/registry-inventory')
protocol = json.loads((record / 'PROTOCOL.json').read_text())['registry_inventory']
out.mkdir(parents=True, exist_ok=True)
if sys.argv[1] == 'prepare':
    source = out / 'source'
    source.mkdir(exist_ok=True)
    for name, expected in protocol['frozen_source_files'].items():
        body = (record / ('FROZEN_' + name)).read_bytes()
        assert len(body) == expected['bytes'] and hashlib.sha256(body).hexdigest() == expected['sha256']
        (source / name).write_bytes(body)
    request = json.loads((source / 'registry-requests.json').read_text())
    assert len(request['decks']) == 8
    assert all(len(d['commanders']) + len(d['mainboard']) == 100 for d in request['decks'])
    print('Original frozen800-slot request restored; no game initialized')
elif sys.argv[1] == 'receive':
    report = Path('gym/build/test-results/test/TEST-com.wingedsheep.gym.ManualTransmissionPhaseTwoRegistryAuditTest.xml')
    original = report.read_bytes()
    (out / report.name).write_bytes(original)
    suite = ET.fromstring(original)
    assert int(suite.get('tests')) == 1 and len(suite.findall('.//testcase')) == 1
    assert all(int(suite.get(key)) == 0 for key in ['failures', 'errors', 'skipped'])
    assert not suite.findall('.//failure') and not suite.findall('.//error') and not suite.findall('.//skipped')
    assert (out / 'exit.txt').read_text().strip() == '0'
    actual = json.loads((out / 'registry/registry-report.json').read_text())
    request = json.loads((out / 'source/registry-requests.json').read_text())
    assert actual['actual_deck_games_initialized'] == actual['official_outcomes'] == 0
    assert actual['execution_allowed'] is False
    assert len(actual['decks']) == 8
    locations = []
    for expected, deck in zip(request['decks'], actual['decks']):
        assert expected['id'] == deck['deck_id']
        names = expected['commanders'] + expected['mainboard']
        assert len(names) == len(deck['cards']) == deck['physical_cards'] == 100
        for index, (name, card) in enumerate(zip(names, deck['cards'])):
            assert card['index'] == index and card['requested_name'] == name
            assert card['rules_qualified'] is False
            if name == 'Phyrexian Metamorph':
                assert card['registry_resolved'] and card['resolved_name'] == name
                definition = json.loads((out / 'registry' / f"{deck['deck_id']}-{index}.json").read_text())
                assert definition['name'] == name
                locations.append(deck['deck_id'])
    assert locations == protocol['required_card_locations']
    (out / 'SOURCE_BOUND_RECEIPT.json').write_text(json.dumps({
        'source': os.environ['SOURCE'], 'validation': os.environ['GITHUB_SHA'],
        'status': 'INVENTORY_ONLY_NOT_RULES_OR_PILOT_QUALIFICATION',
        'original_input_artifact': protocol['original_input_artifact'],
        'input_sha256': protocol['frozen_source_files']['registry-requests.json']['sha256'],
        'junit_sha256': hashlib.sha256(original).hexdigest(), 'fresh_cases': 1,
        'metamorph_registered_in': locations, 'physical_slots': 800,
        'actual_games_initialized': 0, 'official_seeds': 0, 'precision_reserved': 2,
    }, indent=2) + '\n')
else:
    raise ValueError('Expected prepare or receive')
