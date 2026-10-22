"""Reconcile actual JUnit results; never starts a game or infers a win rate."""
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent


def collect(directory, module, broad=False):
    directory = Path(directory)
    rows = []
    for path in sorted(directory.rglob('TEST-*.xml')):
        root = ET.parse(path).getroot()
        cases = root.findall('.//testcase')
        rows.append(dict(name=root.get('name').split('.')[-1], tests=len(cases),
                         failures=len(root.findall('.//failure')),
                         errors=len(root.findall('.//error')),
                         skipped=len(root.findall('.//skipped')),
                         unique=len({(c.get('classname'), c.get('name')) for c in cases}),
                         xml=str(path.relative_to(directory))))
    report = dict(module=module, scope='broad' if broad else 'focused', rows=rows,
                  totals={k: sum(r[k] for r in rows)
                          for k in ('tests', 'failures', 'errors', 'skipped')})
    (directory/'RESULTS.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps({k: v for k, v in report.items() if k != 'rows'}))
    assert rows, 'No JUnit execution evidence'
    assert all(r['tests'] == r['unique'] for r in rows), 'Duplicate case identities'
    assert not any(r['failures'] or r['errors'] or r['skipped'] for r in rows), 'Nonpassing cases retained'
    if not broad:
        expected = json.loads((HERE/'EXPECTED_CASES.json').read_text())[module]
        assert len(rows) == len(expected), 'Missing/extra class'
        assert {r['name']: r['tests'] for r in rows} == expected, 'Class/count mismatch'


def hash_directory(directory):
    directory = Path(directory)
    files = sorted(p for p in directory.rglob('*') if p.is_file() and p.name != 'SHA256SUMS')
    (directory/'SHA256SUMS').write_text(''.join(
        hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(directory))+'\n'
        for p in files))


if __name__ == '__main__':
    if sys.argv[1] == 'hash':
        hash_directory(sys.argv[2])
    else:
        collect(sys.argv[2], sys.argv[3], broad=sys.argv[1] == 'broad')
