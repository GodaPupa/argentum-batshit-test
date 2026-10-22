"""Preserve every original JUnit result before enforcing the stage gate."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET

stage = os.environ['STAGE']
out = Path('copied-choice-evidence') / stage
out.mkdir(parents=True, exist_ok=True)
modules, classes = {}, set()
for path in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml')):
    if 'copied-choice-evidence' in path.parts:
        continue
    target = out / 'junit' / path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
    suite = ET.fromstring(path.read_bytes())
    cases = suite.findall('.//testcase')
    assert len(cases) == int(suite.get('tests'))
    module = str(path).split('/build/test-results/test/', 1)[0]
    result = modules.setdefault(module, dict(rows=0, passed=0, failures=0, errors=0,
                                             skipped=0, classes=0, skips=[], failed_cases=[]))
    result['rows'] += len(cases)
    result['classes'] += 1
    for key, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
        actual = len(suite.findall('.//' + tag))
        assert actual == int(suite.get(key)), (path, key)
        result[key] += actual
    for case in cases:
        classes.add(case.get('classname').rsplit('.', 1)[-1])
        identity = dict(classname=case.get('classname'), name=case.get('name'), xml=str(path))
        if case.find('skipped') is not None:
            result['skips'].append(identity)
        elif case.find('failure') is not None or case.find('error') is not None:
            result['failed_cases'].append(identity)
        else:
            result['passed'] += 1
totals = {k: sum(m[k] for m in modules.values()) for k in
          ['rows', 'passed', 'failures', 'errors', 'skipped', 'classes']}
summary = {'stage': stage, 'source': os.environ['SOURCE'], 'validation': os.getenv('GITHUB_SHA'),
           'modules': modules, 'totals': totals, 'official_results': 0, 'competitive_admission': False}
(out / 'SUMMARY.json').write_text(json.dumps(summary, indent=2) + '\n')
tracked = subprocess.check_output(['git', 'status', '--porcelain', '--untracked-files=no'], text=True)
(out / 'tracked-changes.txt').write_text(tracked)
(out / 'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' +
    str(p.relative_to(out)) + '\n' for p in sorted(out.rglob('*')) if p.is_file() and p.name != 'SHA256SUMS'))
print(json.dumps({'stage': stage, 'totals': totals, 'modules': list(modules)}, indent=2))
if stage == 'negative':
    expected_paths = sorted(path for path in json.loads(Path(os.environ['RECORD'], 'PROTOCOL.json').read_text())['source_manifest'] if '/src/main/' in path)
    changed = sorted(subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines())
    assert changed == expected_paths, changed
    assert totals == dict(rows=7, passed=0, failures=7, errors=0, skipped=0, classes=1), totals
    assert classes == {'CopiedAsEntersChoiceScenarioTest'}
    assert (out / 'exit.txt').read_text().strip() != '0'
else:
    assert not tracked
    assert modules and totals['failures'] == totals['errors'] == totals['skipped'] == 0
    assert (out / 'exit.txt').read_text().strip() == '0'
    assert {'CopiedAsEntersChoiceScenarioTest', 'EntersAsCopyExceptionsScenarioTest'} <= classes
    if stage == 'focused':
        assert {'EntersAsCopyExceptionsSerializationTest', 'CardDefinitionSnapshotTest', 'CardLintTest'} <= classes
        assert totals['passed'] == 362
    else:
        assert stage == 'broad'
        required_modules = {'rules-engine'} | {'mtg-sets/' + era + '/tests' for era in
            ['1993-1999', '2000-2002', '2003-2007', '2008-2016', '2017-2022', '2023', '2024', '2025', '2026']}
        assert required_modules <= modules.keys()
        assert modules['rules-engine']['passed'] == 3771
        assert sum(modules[m]['passed'] for m in required_modules) == 14546
