"""Preserve original reports before exact source-bound qualification assertions."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import sys
sys.dont_write_bytecode = True

stage = os.environ['STAGE']
assert stage in ('negative', 'focused', 'broad', 'ordinary')
root = Path(os.environ['RECORD'])
protocol = json.loads((root / 'PROTOCOL.json').read_text())
out = Path('paused-token-r3-evidence') / stage
out.mkdir(parents=True, exist_ok=True)
modules, cases_by_class, identities, failures = {}, {}, set(), []
reports = [path for path in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml'))
           if 'paused-token-r3-evidence' not in path.parts]
for path in reports:
    target = out / 'junit' / path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
for path in reports:
    suite = ET.fromstring(path.read_bytes())
    cases = suite.findall('.//testcase')
    assert len(cases) == int(suite.get('tests')), (path, 'row count')
    module = str(path).split('/build/test-results/test/', 1)[0]
    result = modules.setdefault(module, dict(rows=0, passed=0, failures=0, errors=0,
        skipped=0, classes=0, skips=[], failed_cases=[]))
    result['rows'] += len(cases)
    result['classes'] += 1
    for key, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
        actual = sum(case.find(tag) is not None for case in cases)
        assert actual == int(suite.get(key)), (path, key)
        result[key] += actual
    for case in cases:
        full = case.get('classname') + '::' + case.get('name')
        assert (module, full) not in identities, (module, full)
        identities.add((module, full))
        cls = case.get('classname').rsplit('.', 1)[-1]
        cases_by_class.setdefault(cls, []).append(full)
        identity = dict(classname=case.get('classname'), name=case.get('name'), xml=str(path))
        if case.find('skipped') is not None:
            result['skips'].append(identity)
        elif case.find('failure') is not None or case.find('error') is not None:
            failed = case.find('failure')
            if failed is None:
                failed = case.find('error')
            detail = dict(identity, type=failed.get('type'), message=failed.get('message'), detail=failed.text)
            result['failed_cases'].append(detail)
            failures.append(detail)
        else:
            result['passed'] += 1
totals = {key: sum(module[key] for module in modules.values()) for key in
          ['rows', 'passed', 'failures', 'errors', 'skipped', 'classes']}
tracked = subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], text=True)
(out / 'tracked-changes.txt').write_text(tracked)
log = (out / 'gradle.log').read_text(errors='replace') if (out / 'gradle.log').exists() else ''
task_outcomes = [{'task': match.group(1), 'outcome': match.group(2) or 'EXECUTED'} for match in
    re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$', log, re.M)]
summary = dict(stage=stage, source=os.environ['SOURCE'], source_tree=os.environ['SOURCE_TREE'],
    validation=os.getenv('GITHUB_SHA'), run=os.getenv('GITHUB_RUN_ID'),
    attempt=os.getenv('GITHUB_RUN_ATTEMPT'), modules=modules, totals=totals,
    cases_by_class={cls: sorted(values) for cls, values in cases_by_class.items()},
    gradle_task_outcomes=task_outcomes, official_results=0, official_seeds=0,
    precision_reserved=2, competitive_admission=False)
(out / 'SUMMARY.json').write_text(json.dumps(summary, indent=2) + '\n')
(out / 'SHA256SUMS').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' +
    str(path.relative_to(out)) + '\n' for path in sorted(out.rglob('*'))
    if path.is_file() and path.name != 'SHA256SUMS'))
print(json.dumps({'stage': stage, 'totals': totals, 'modules': list(modules)}, indent=2))
assert protocol['status'] == 'SOURCE_FROZEN_EXECUTION_REQUIRES_ACTIVITY_AND_WORKFLOW_PINS'
rc = int((out / 'exit.txt').read_text())
eras = ['1993-1999', '2000-2002', '2003-2007', '2008-2016', '2017-2022', '2023', '2024', '2025', '2026']
broad_modules = {'rules-engine'} | {'mtg-sets/' + era + '/tests' for era in eras}
expected_cases = json.loads((root / 'EXPECTED_CASES.json').read_text())
if stage == 'negative':
    changed = sorted(subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines())
    assert changed == sorted(protocol['baseline_restore_paths']), changed
    assert totals == protocol['expected_negative'], totals
    assert set(modules) == {'rules-engine'}
    expected = {case['classname'] + '::' + case['name']: case for case in protocol['cases']}
    observed = {full: 'pass' for values in cases_by_class.values() for full in values}
    for failed in failures:
        full = failed['classname'] + '::' + failed['name']
        assert failed['type'] == 'org.opentest4j.AssertionFailedError', (full, failed['type'])
        spec = expected[full]
        first = re.search(re.escape(spec['classname'].rsplit('.', 1)[-1]) + r'\.kt:(\d+)', failed['detail'] or '')
        assert first and int(first.group(1)) == spec['expected_first_failure_line'], (full, first.group(1) if first else None)
        observed[full] = 'assertion_failure'
    assert observed == {full: spec['predicted_baseline'] for full, spec in expected.items()}, observed
    assert rc != 0
else:
    assert not tracked
    assert rc == 0 and modules and totals['failures'] == totals['errors'] == 0
    required_cases = {cls: values for cls, values in expected_cases.items()
        if stage != 'broad' or cls not in {'CardDefinitionSnapshotTest', 'CardLintTest', 'EntersAsCopyExceptionsSerializationTest'}}
    for cls, values in required_cases.items():
        assert sorted(cases_by_class.get(cls, [])) == values, (stage, cls, 'identity drift')
    if stage == 'focused':
        assert set(cases_by_class) == set(expected_cases), set(cases_by_class) ^ set(expected_cases)
        assert set(modules) == {'rules-engine', 'mtg-sdk', 'mtg-sets', 'mtg-sets/2023/tests', 'mtg-sets/2024/tests', 'mtg-sets/2025/tests'}
        assert totals['passed'] == protocol['qualification']['focused_passed'] and totals['skipped'] == 0
    elif stage == 'broad':
        assert set(modules) == broad_modules, set(modules)
        assert modules['rules-engine']['passed'] == protocol['qualification']['engine_passed']
        assert totals['passed'] == protocol['qualification']['broad_required_passed'] and totals['skipped'] == 0
    else:
        required_modules = broad_modules | {'ai', 'game-server', 'gym', 'gym-server', 'gym-trainer',
            'mtg-sdk', 'mtg-search', 'mtg-sets', 'mtgish-tooling', 'oracle-assay'}
        assert set(modules) == required_modules, set(modules)
        assert modules['oracle-assay']['passed'] == 568 and modules['mtg-sdk']['passed'] == 529
        assert modules['rules-engine']['passed'] == protocol['qualification']['engine_passed']
        assert totals['passed'] == protocol['qualification']['ordinary_passed'] and totals['skipped'] == 43
        skips = sorted(s['classname'] + '::' + s['name'] for module in modules.values() for s in module['skips'])
        assert skips == sorted(json.loads((root / 'BASELINE_SKIPS.json').read_text())), skips
# Missing/old report deletion forces these test tasks to execute; no cached/up-to-date test evidence.
task_map = {item['task']: item['outcome'] for item in task_outcomes}
for module in modules:
    task = ':' + module.replace('/', ':') + ':test'
    assert task_map.get(task) in ({'EXECUTED', 'FAILED'} if stage == 'negative' else {'EXECUTED'}), (task, task_map.get(task))
if stage == 'negative':
    # This is a composite receiving gate, never an invented single-run seventy-case result.
    from reuse_baseline import audit_reuse
    reuse = audit_reuse(write=False)
    reused = reuse['reused_totals']
    composite = {key: totals[key] + reused[key] for key in totals}
    assert composite == protocol['expected_composite_baseline'], composite
    fresh_identities = {case['classname'] + '::' + case['name'] for case in protocol['cases']}
    assert not fresh_identities & set(reuse['reused_cases'])
    assert fresh_identities | set(reuse['reused_cases']) == {
        case['classname'] + '::' + case['name'] for case in protocol['composite_baseline_cases']}
    combined = dict(status='R2_COMPOSITE_BASELINE_GATE_PASSED', source=os.environ['SOURCE'],
        source_tree=os.environ['SOURCE_TREE'], validation=os.getenv('GITHUB_SHA'),
        fresh_run=os.getenv('GITHUB_RUN_ID'), fresh_attempt=os.getenv('GITHUB_RUN_ATTEMPT'),
        fresh14=totals, reused56=reused, composite70=composite, reuse=reuse,
        r1_status='FAILED_UNADOPTED', one_execution=False, official_games=0, official_seeds=0,
        external_independent_review=False, competitive_admission=False)
    (out / 'COMPOSITE_BASELINE.json').write_text(json.dumps(combined, indent=2) + '\n')
    (out / 'SHA256SUMS').write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' +
        str(path.relative_to(out)) + '\n' for path in sorted(out.rglob('*'))
        if path.is_file() and path.name != 'SHA256SUMS'))
