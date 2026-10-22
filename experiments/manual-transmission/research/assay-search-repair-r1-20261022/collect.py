"""Receive each source-pinned repair job; preserve failures before asserting expectations."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import xml.etree.ElementTree as ET

stage = os.environ['STAGE']
out = Path('repair-evidence')
out.mkdir(exist_ok=True)
modules = {}
failed = []
for path in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml')):
    if 'repair-evidence' in path.parts:
        continue
    module = str(path).split('/build/test-results/test/', 1)[0]
    target = out / 'junit' / path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
    suite = ET.fromstring(path.read_bytes())
    cases = suite.findall('.//testcase')
    assert len(cases) == int(suite.get('tests'))
    result = modules.setdefault(module, dict(rows=0, passed=0, failures=0, errors=0, skipped=0, classes=0, skips=[], failed_cases=[]))
    result['rows'] += len(cases)
    result['classes'] += 1
    for key, tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
        actual = len(suite.findall('.//' + tag))
        assert actual == int(suite.get(key)), (path,key)
        result[key] += actual
    for case in cases:
        identity = {'class':case.get('classname'), 'name':case.get('name'), 'xml':str(path)}
        if case.find('skipped') is not None:
            result['skips'].append(identity)
        elif case.find('failure') is not None or case.find('error') is not None:
            result['failed_cases'].append(identity)
            failed.append(identity['class'].rsplit('.',1)[-1] + ':' + identity['name'])
        else:
            result['passed'] += 1
totals = {k:sum(m[k] for m in modules.values()) for k in ['rows','passed','failures','errors','skipped','classes']}
summary = {'stage':stage, 'validation':os.getenv('GITHUB_SHA'), 'source':os.getenv('SOURCE'), 'modules':modules, 'totals':totals, 'official_results':0, 'competitive_admission':False}
(out/'SUMMARY.json').write_text(json.dumps(summary,indent=2)+'\n')
tracked = subprocess.check_output(['git','status','--porcelain','--untracked-files=no'],text=True)
(out/'tracked-changes.txt').write_text(tracked)
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(out))+'\n' for p in sorted(out.rglob('*')) if p.is_file() and p.name != 'SHA256SUMS'))
print(json.dumps(summary,indent=2))
assert modules, 'No original test XML'
assert totals['errors'] == 0
if stage == 'negative-control':
    expected = {
        'ManaValuesTest:the searches and returns that named this band read as whole lines',
        'ManaValuesTest:the pronoun form of the search is a variant, not a second reading',
        'SubtypesTest:a basic land type in card position is the land type noun, and only that',
        'SubtypesTest:a non-land subtype in card position still reads ungated',
        'LibrarySearchTest:every singular destination and reveal row prints the authorized search recipe',
        'LibrarySearchTest:a counted search preserves its count and subtype in both directions',
        'LibrarySearchTest:a two-filter search reads both authorized scopes and only the final shuffle',
    }
    assert set(failed) == expected and len(failed) == 7, failed
    assert totals['skipped'] == 0
    assert tracked.strip() == 'M oracle-assay/src/main/kotlin/com/wingedsheep/assay/grammar/Library.kt'
    assert (out/'exit.txt').read_text().strip() != '0'
else:
    assert not tracked
    assert totals['failures'] == 0
    assert (out/'exit.txt').read_text().strip() == '0'
    if stage == 'assay':
        assert totals['rows'] == totals['passed'] == 568 and totals['skipped'] == 0
        assert (out/'gate-exit.txt').read_text().strip() == '0'
        assert (out/'differential-exit.txt').read_text().strip() == '0'
