"""Retain fresh primary card data before checking the reviewed Metamorph draft."""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import os
import urllib.request

record = Path(os.environ['RECORD'])
out = Path('counter-metamorph-evidence/card/source-review')
out.mkdir(parents=True, exist_ok=True)
review = json.loads((record / 'SOURCE_REVIEW.json').read_text())
protocol = json.loads((record / 'PROTOCOL.json').read_text())
receipt = {'started_at': datetime.now(timezone.utc).isoformat(),
           'source': os.environ['SOURCE'], 'status': 'INCOMPLETE', 'requests': []}


def request(url, name=None, method='GET'):
    entry = {'url': url, 'method': method}
    receipt['requests'].append(entry)
    req = urllib.request.Request(url, method=method, headers={
        'User-Agent': 'Argentum-Manual-Transmission-Source-Review/1.0',
        'Accept': 'application/json' if method == 'GET' else '*/*'})
    with urllib.request.urlopen(req, timeout=60) as response:
        entry.update(status=response.status, final_url=response.url)
        if method == 'HEAD':
            assert response.status == 200
            return
        body = response.read()
    (out / name).write_bytes(body)
    entry.update(file=name, bytes=len(body), sha256=hashlib.sha256(body).hexdigest())
    assert entry['status'] == 200
    return json.loads(body)


try:
    card = request(review['primary_source']['files'][0]['url'], 'card.json')
    for field, expected in review['verified_fields'].items():
        actual = card['image_uris']['normal'] if field == 'image_uri' else card[field]
        assert actual == expected, (field, expected, actual)
    for field in ['id', 'oracle_id']:
        assert card[field] == review['primary_source'][field], field
    assert card['set'] == 'nph' and card['colors'] == ['U']
    fresh_rulings = request(card['rulings_uri'], 'rulings.json')
    old_rulings = json.loads((record / 'nph-phyrexian-metamorph-rulings.json').read_text())
    fields = ('oracle_id', 'source', 'published_at', 'comment')
    normalize = lambda data: [tuple(row[key] for key in fields) for row in data['data']]
    assert not fresh_rulings['has_more'] and len(fresh_rulings['data']) == 8
    assert normalize(fresh_rulings) == normalize(old_rulings)
    printings, page, url = [], 0, card['prints_search_uri']
    while url:
        data = request(url, f'printings-{page}.json')
        printings.extend(data['data'])
        url = data['next_page'] if data['has_more'] else None
        page += 1
    eligible = [row for row in printings if row['set_type'] not in
                {'promo', 'token', 'art_series', 'memorabilia'}]
    earliest = min(eligible, key=lambda row: (row['released_at'], row['set']))
    assert earliest['set'] == 'nph', earliest
    assert earliest['collector_number'] == '42', earliest
    request(card['image_uris']['normal'], method='HEAD')
    card_path = next(path for path in protocol['source_manifest']
                     if path.endswith('/PhyrexianMetamorph.kt'))
    assert hashlib.sha256(Path(card_path).read_bytes()).hexdigest() == review['draft_sha256']
    receipt.update(status='PASS', fields_checked=list(review['verified_fields']),
                   rulings_checked=8, printings_received=len(printings), printing_pages=page,
                   canonical={'set': earliest['set'], 'collector_number': earliest['collector_number'],
                              'id': earliest['id'], 'released_at': earliest['released_at']},
                   card_path=card_path, card_sha256=review['draft_sha256'])
except Exception as error:
    receipt.update(status='FAIL', error=repr(error))
    raise
finally:
    receipt['finished_at'] = datetime.now(timezone.utc).isoformat()
    (out / 'RECEIPT.json').write_text(json.dumps(receipt, indent=2) + '\n')
