"""Reconstruct and independently verify the original source-bound build artifact; no execution."""
from pathlib import Path
import hashlib
import io
import json
import sys
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
meta = json.loads((root / 'ARTIFACT.json').read_text())
parts = []
for part in meta['parts']:
    data = (root / part['name']).read_bytes()
    assert len(data) == part['bytes']
    assert hashlib.sha256(data).hexdigest() == part['sha256'], part['name']
    parts.append(data)
data = b''.join(parts)
assert len(data) == meta['size_in_bytes']
assert hashlib.sha256(data).hexdigest() == meta['digest'].removeprefix('sha256:')
z = zipfile.ZipFile(io.BytesIO(data))
assert z.testzip() is None
names = set(z.namelist())
assert len(names) == len(z.namelist())
outer = {}
for row in z.read('SHA256SUMS').decode().splitlines():
    sha, name = row.split('  ', 1)
    assert name not in outer
    assert hashlib.sha256(z.read(name)).hexdigest() == sha, name
    outer[name] = sha
assert names == set(outer) | {'SHA256SUMS'}
binding = json.loads(z.read('source-binding.json'))
assert binding['source'] == 'ebb1e2892c258dde060b27113aa113fba65dcd66'
assert binding['validation'] == '9a35cf2813f9f4ff24823e00a3b5d0b5fe01f45d'
assert binding['source_tree'] == 'e1e478f3ab5f2077b16ae979606e94ef4b773063'
assert binding['goldens_verified'] == 23
assert len(binding['bindings']) == 13
assert binding['official_games'] == binding['official_seeds'] == 0
assert binding['precision_reserved'] == 2
assert binding['bindings']['experiments/manual-transmission/control/v0.7.decklist.txt']['git_blob'] == 'c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
r1 = '.github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml'
assert binding['bindings'][r1]['sha256'] == '814cf66d558fa38eec5cac53458588824a8268419339b3b0f7391e6e97f244fb'
required_classes = {'FacadeBoundaryTest', 'CardDefinitionSnapshotTest', 'CardLintTest',
    'PestControlTierOneMonsterTronRunnerSurfacePreflightTest', 'PestControlTierOneMonsterTronR1SurfacePinTest',
    'ArcaneDenialScenarioTest', 'PactOfNegationScenarioTest', 'PactOfNegationUnpaidScenarioTest'}
all_stages = {}
for stage in ('targeted', 'ordinary'):
    assert z.read(stage + '/exit.txt').strip() == b'0'
    assert z.read(stage + '/tracked-changes.txt') == b''
    inner = {}
    for row in z.read(stage + '/SHA256SUMS').decode().splitlines():
        sha, rel = row.split('  ', 1)
        name = stage + '/' + rel
        assert name not in inner
        assert hashlib.sha256(z.read(name)).hexdigest() == sha, name
        inner[name] = sha
    assert {n for n in names if n.startswith(stage + '/')} == set(inner) | {stage + '/SHA256SUMS'}
    modules, classes = {}, {}
    for name in sorted(n for n in names if n.startswith(stage + '/junit/') and n.endswith('.xml')):
        module = name.removeprefix(stage + '/junit/').split('/build/test-results/test/')[0]
        suite = ET.fromstring(z.read(name))
        cases = suite.findall('.//testcase')
        assert len(cases) == int(suite.get('tests'))
        row = modules.setdefault(module, dict(rows=0, passed=0, failures=0, errors=0, skipped=0, classes=0, skips=[], failed_cases=[]))
        row['classes'] += 1
        row['rows'] += len(cases)
        for key, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
            actual = sum(case.find(tag) is not None for case in cases)
            assert actual == int(suite.get(key))
            row[key] += actual
        for case in cases:
            classname = case.get('classname')
            short = classname.rsplit('.', 1)[-1]
            identity = {'class': classname, 'name': case.get('name'), 'xml': name}
            classes.setdefault(short, []).append(identity)
            if case.find('skipped') is not None:
                row['skips'].append(identity)
            elif case.find('error') is not None or case.find('failure') is not None:
                row['failed_cases'].append(identity)
            else:
                row['passed'] += 1
    totals = {k: sum(row[k] for row in modules.values()) for k in ('rows', 'passed', 'failures', 'errors', 'skipped', 'classes')}
    summary = json.loads(z.read(stage + '/SUMMARY.json'))
    assert summary['source'] == binding['source'] and summary['validation'] == binding['validation']
    assert totals == summary['totals']
    assert modules.keys() == summary['modules'].keys()
    for module, row in modules.items():
        for key in totals:
            assert row[key] == summary['modules'][module][key], (stage, module, key)
    assert totals['failures'] == totals['errors'] == 0
    assert required_classes <= classes.keys()
    assert len(classes['PestControlTierOneMonsterTronR1SurfacePinTest']) == 4
    assert len(classes['PestControlTierOneMonsterTronRunnerSurfacePreflightTest']) == 4
    assert len(classes['ArcaneDenialScenarioTest']) == 3
    assert len(classes['PactOfNegationScenarioTest']) == 3
    assert len(classes['PactOfNegationUnpaidScenarioTest']) == 1
    if stage == 'targeted':
        assert totals['skipped'] == 0
    else:
        required_modules = {'ai', 'game-server', 'gym', 'gym-server', 'gym-trainer', 'mtg-sdk',
            'mtg-search', 'mtg-sets', 'mtgish-tooling', 'oracle-assay', 'rules-engine'}
        required_modules.update('mtg-sets/' + era + '/tests' for era in
            ['1993-1999', '2000-2002', '2003-2007', '2008-2016', '2017-2022', '2023', '2024', '2025', '2026'])
        assert required_modules <= modules.keys()
        assert modules['oracle-assay']['passed'] == 568
        skipped = {item['class'].rsplit('.', 1)[-1] for item in modules['gym']['skips']}
        assert {'PestControlTierOneMonsterTronOfficialExecutionRunnerTest',
                'PestControlV2OfficialSeedFreezeGeneratorTest'} <= skipped
    all_stages[stage] = {'totals': totals, 'modules': modules, 'required_cases': {k: classes[k] for k in sorted(required_classes)}}
audit = {'artifact_id': meta['id'], 'zip_sha256': hashlib.sha256(data).hexdigest(), 'zip_bytes': len(data),
    'outer_manifest_entries': len(outer), 'source_binding': binding, 'stages': all_stages,
    'official_results': 0, 'competitive_admission': False, 'status': 'ORDINARY_BUILD_PASSED'}
if '--write' in sys.argv:
    (root / 'INDEPENDENT_AUDIT.json').write_text(json.dumps(audit, indent=2) + '\n')
else:
    assert json.loads((root / 'INDEPENDENT_AUDIT.json').read_text()) == audit
print(json.dumps({'status': audit['status'], 'artifact': meta['id'],
    'totals': {stage: d['totals'] for stage, d in all_stages.items()}}))
