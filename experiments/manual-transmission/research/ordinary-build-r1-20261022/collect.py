"""Preserve whole-build JUnit results without conflating skips with passes."""
from pathlib import Path
import hashlib, json, os, shutil, subprocess
import xml.etree.ElementTree as ET

out = Path('ordinary-evidence')
out.mkdir(exist_ok=True)
modules = {}
for path in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml')):
    if 'ordinary-evidence' in path.parts:
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
    for key, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
        actual = len(suite.findall('.//' + tag))
        assert actual == int(suite.get(key)), (path, key)
        result[key] += actual
    for case in cases:
        identity = {'class': case.get('classname'), 'name': case.get('name'), 'xml': str(path)}
        if case.find('skipped') is not None:
            result['skips'].append(identity)
        elif case.find('failure') is not None or case.find('error') is not None:
            result['failed_cases'].append(identity)
        else:
            result['passed'] += 1

summary = {'validation_commit': os.getenv('GITHUB_SHA'), 'modules': modules,
           'totals': {k: sum(m[k] for m in modules.values()) for k in ['rows','passed','failures','errors','skipped','classes']},
           'official_results': 0, 'competitive_admission': False}
(out/'SUMMARY.json').write_text(json.dumps(summary,indent=2)+'\n')
tracked = subprocess.check_output(['git','status','--porcelain','--untracked-files=no'], text=True)
(out/'tracked-changes.txt').write_text(tracked)
files = sorted(p for p in out.rglob('*') if p.is_file() and p.name != 'SHA256SUMS')
(out/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(out))+'\n' for p in files))
print(json.dumps(summary,indent=2))
assert modules, 'No JUnit results; build incomplete'
assert not tracked, 'Build modified tracked source or goldens'
assert summary['totals']['failures'] == summary['totals']['errors'] == 0
assert (out/'exit.txt').read_text().strip() == '0', 'Build process failed'
