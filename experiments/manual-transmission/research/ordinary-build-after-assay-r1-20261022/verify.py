"""Verify the original build archive and recount JUnit without executing any game or build."""
from pathlib import Path
import hashlib
import io
import json
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter

root = Path(__file__).resolve().parent
record = json.loads((root / 'ARTIFACT.json').read_text())
chunks = []
for part in record['parts']:
    data = (root / part['name']).read_bytes()
    assert len(data) == part['bytes']
    assert hashlib.sha256(data).hexdigest() == part['sha256']
    chunks.append(data)
data = b''.join(chunks)
assert len(data) == record['size_in_bytes']
assert hashlib.sha256(data).hexdigest() == record['sha256']
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    names = archive.namelist()
    assert len(names) == len(set(names))
    assert archive.testzip() is None
    checked = []
    for row in archive.read('SHA256SUMS').decode().splitlines():
        digest, name = row.split(None, 1)
        assert hashlib.sha256(archive.read(name)).hexdigest() == digest
        checked.append(name)
    assert len(checked) == len(set(checked))
    assert set(checked) == set(names) - {'SHA256SUMS'}
    totals = Counter()
    modules = {}
    failed = []
    for name in names:
        if not name.endswith('.xml'):
            continue
        module = name.removeprefix('junit/').split('/build/')[0]
        count = modules.setdefault(module, Counter())
        suite = ET.fromstring(archive.read(name))
        cases = suite.findall('testcase')
        assert len(cases) == int(suite.get('tests'))
        for field, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
            assert len(suite.findall('.//' + tag)) == int(suite.get(field))
        for case in cases:
            status = ('failed' if case.find('failure') is not None else
                      'errors' if case.find('error') is not None else
                      'skipped' if case.find('skipped') is not None else 'passed')
            totals['total'] += 1
            totals[status] += 1
            count['total'] += 1
            count[status] += 1
            if status == 'failed':
                failed.append(case.get('classname'))
    assert totals == Counter(total=3645, passed=3600, failed=2, skipped=43)
    assert sorted(failed) == sorted([
        'com.wingedsheep.gym.PestControlTierOneMonsterTronRunnerSurfacePreflightTest',
        'com.wingedsheep.mtg.sets.FacadeBoundaryTest',
    ])
    assert len([n for n in names if n.endswith('.xml')]) == 435
    assert modules['oracle-assay'] == Counter(total=568, passed=568)
    assert 'rules-engine' not in modules
    assert not any(m.startswith('mtg-sets/') for m in modules)
    binding = json.loads(archive.read('source-binding.json'))
    assert binding['source'] == '6cf30d14d4be570bc1786e48079549cf850fa16a'
    assert binding['validation'] == '0317a91b0bdbbebf386d1d035936e1e26c67e627'
    assert binding['frozen_deck_blob'] == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
    assert binding['official_games'] == 0 and binding['official_seeds_generated'] is False
    assert archive.read('exit.txt').decode().strip() == '1'
    assert archive.read('tracked-changes.txt') == b''
    summary = json.loads(archive.read('SUMMARY.json'))
    for field, key in [('rows', 'total'), ('passed', 'passed'), ('failures', 'failed'),
                       ('errors', 'errors'), ('skipped', 'skipped')]:
        assert summary['totals'][field] == totals[key]
    result = {'archive_sha256': record['sha256'], 'manifest_files': len(checked),
              'xml_classes': 435, 'counts': dict(totals), 'modules': modules,
              'disposition': 'VERIFIED_FAILED_BUILD_COPY_ENTRY_HELD'}
    print(json.dumps(result, indent=2))
