"""Read-only card-source intake. No cards, games, policies, seeds or goldens are changed."""
import hashlib
import json
from pathlib import Path
import subprocess
import time
from urllib.parse import urlencode
from urllib.request import Request, urlopen

HERE = Path(__file__).resolve().parent
OUT = Path('tail-source-evidence')
ASSAY = 'oracle-assay/build/install/oracle-assay/bin/oracle-assay'
FIDELITY = 'mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling'


def fetch(url, dest):
    time.sleep(0.15)
    req = Request(url, headers={'User-Agent': 'Argentum-Manual-Source-Qualification/1.0',
                                'Accept': 'application/json'})
    with urlopen(req, timeout=60) as response:
        body = response.read()
    dest.write_bytes(body)
    return json.loads(body)


def run(command, dest):
    with dest.open('wb') as out:
        result = subprocess.run(command, stdout=out, stderr=subprocess.STDOUT, timeout=300)
    return {'command': command, 'exit': result.returncode, 'output': str(dest)}


def main():
    protocol = json.loads((HERE / 'PROTOCOL.json').read_text())
    OUT.mkdir(exist_ok=True)
    report = []
    for index, card in enumerate(protocol['cards']):
        name, set_code = card['name'], card['expected_canonical_set']
        directory = OUT / f'{index + 1:02d}-{set_code}'
        directory.mkdir(exist_ok=True)
        row = {'name': name, 'requested_set': set_code, 'commands': [], 'qualified_for_games': False}
        report.append(row)
        try:
            for label, command in [
                ('parse', [ASSAY, 'parse', name]),
                ('explain', [ASSAY, 'explain', name]),
                ('fidelity', [FIDELITY, 'fidelity', '--emit', name, '--set', set_code.upper()]),
            ]:
                row['commands'].append(run(command, directory / (label + '.log')))
            exact = fetch('https://api.scryfall.com/cards/named?' + urlencode({'exact': name, 'set': set_code}),
                          directory / 'scryfall-card.json')
            assert exact['set'].lower() == set_code.lower()
            assert name in [exact['name']] + [face['name'] for face in exact.get('card_faces', [])]
            fetch(exact['rulings_uri'], directory / 'scryfall-rulings.json')
            printings_url = 'https://api.scryfall.com/cards/search?' + urlencode({
                'q': '!"' + exact['name'] + '"', 'unique': 'prints', 'order': 'released', 'dir': 'asc'})
            all_printings = []
            page_number = 0
            while printings_url:
                page_number += 1
                page = fetch(printings_url, directory / f'scryfall-printings-{page_number:02d}.json')
                all_printings.extend(page['data'])
                printings_url = page.get('next_page') if page.get('has_more') else None
            eligible = [p for p in all_printings if not p.get('promo') and
                        p.get('set_type') not in {'promo', 'token', 'art_series', 'memorabilia'}]
            assert eligible, 'No eligible canonical printing'
            earliest = eligible[0]
            row['earliest_printing'] = {k: earliest.get(k) for k in
                                        ['name', 'set', 'set_name', 'set_type', 'released_at', 'collector_number', 'id']}
            row['canonical_matches'] = earliest['set'].lower() == set_code.lower()
            image_urls = ([exact['image_uris']['normal']] if 'image_uris' in exact else
                          [face['image_uris']['normal'] for face in exact['card_faces']])
            row['images'] = []
            for url in image_urls:
                time.sleep(0.15)
                with urlopen(Request(url, method='HEAD', headers={'User-Agent': 'Argentum-Manual-Source-Qualification/1.0'}), timeout=60) as response:
                    row['images'].append({'url': url, 'status': response.status})
            row['source_received'] = True
        except Exception as error:
            row['source_received'] = False
            row['error'] = type(error).__name__ + ': ' + str(error)
        (OUT / 'REPORT.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps({'name': name, 'source_received': row['source_received'],
                          'canonical_matches': row.get('canonical_matches'),
                          'command_exits': [r['exit'] for r in row['commands']],
                          'error': row.get('error')}), flush=True)
    assert all(r['source_received'] and r['canonical_matches'] for r in report), 'Source intake incomplete; originals retained'
    assert all(c['exit'] in (0, 1) for r in report for c in r['commands']), 'Unexpected CLI failure; inspect originals'


if __name__ == '__main__':
    main()
