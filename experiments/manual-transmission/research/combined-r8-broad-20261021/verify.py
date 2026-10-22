"""Read-only reconstruction and verification of R8 original CI evidence; starts no games."""
from pathlib import Path
import hashlib
import io
import json
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
EXPECTED_SKIP = {'classname': 'com.wingedsheep.ai.arena.ArenaBudgetScalingTest',
                 'name': 'budget scaling: strength is monotone in the decision budget'}


def audit_zip(data, role):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate ZIP members'
        assert archive.testzip() is None, 'ZIP CRC mismatch'
        hashed = set()
        for name in names:
            if name.endswith('SHA256SUMS'):
                prefix = name[:-len('SHA256SUMS')]
                for line in archive.read(name).decode().splitlines():
                    digest, member = line.split(maxsplit=1)
                    member = prefix + member
                    assert hashlib.sha256(archive.read(member)).hexdigest() == digest, member
                    assert member not in hashed, member
                    hashed.add(member)
        assert hashed == {name for name in names if not name.endswith('SHA256SUMS')}, 'Incomplete internal manifest'
        totals = dict(rows=0, passed=0, failures=0, errors=0, skipped=0, classes=0)
        exclusions = []
        for name in names:
            if '/TEST-' not in name or not name.endswith('.xml'):
                continue
            suite = ET.fromstring(archive.read(name))
            cases = suite.findall('.//testcase')
            assert int(suite.get('tests')) == len(cases), name
            totals['classes'] += 1
            totals['rows'] += len(cases)
            for key, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
                count = len(suite.findall('.//' + tag))
                assert int(suite.get(key)) == count, (name, key)
                totals[key] += count
            for case in cases:
                if case.find('skipped') is not None:
                    exclusions.append({key: case.get(key) for key in ('classname', 'name')})
        assert totals['rows'] and totals['classes'], 'Missing JUnit evidence'
        assert not totals['failures'] and not totals['errors'], totals
        assert exclusions == ([EXPECTED_SKIP] if role == 'ai' else []), exclusions
        totals['passed'] = totals['rows'] - totals['failures'] - totals['errors'] - totals['skipped']
        if role != 'assay':
            exit_members = [n for n in names if n.endswith('exit.txt')]
            assert len(exit_members) == 1 and archive.read(exit_members[0]).strip() == b'0'
        else:
            delta = json.loads(archive.read('snapshot-delta.json'))
            assert not delta['modified'] and not delta['removed']
            assert delta['added'] == delta['expected_additions'] and len(delta['added']) == 28
            def cards(prefix):
                result = {}
                for member in names:
                    if member.startswith(prefix) and member.endswith('.json'):
                        for section in re.split(r'^// ', archive.read(member).decode(), flags=re.M)[1:]:
                            name, body = section.split('\n', 1)
                            key = (Path(member).name, name)
                            assert key not in result
                            result[key] = json.loads(body)
                return result
            before = cards('before/')
            generated = cards('generated/')
            assert sorted(set(generated) - set(before)) == [tuple(row) for row in delta['added']]
            assert all(generated[key] == before[key] for key in set(generated) & set(before))
            after_files = {key[0] for key in generated}
            assert not {key for key in before if key[0] in after_files} - set(generated)
            differential = archive.read('differential-ths.log').decode()
            assert re.search(r'DIVERGENT — read every one\s+0', differential)
        return dict(**totals, exclusions=exclusions, unique_members=len(names), internal_hashes=len(hashed))


def main():
    index = json.loads((ROOT / 'ORIGINALS.json').read_text())
    assert index['run'] == 39722000171
    assert index['validation_commit'] == '58591e802eb59e49d790db5f683dfe8fc6b31df7'
    results = []
    for record in index['artifacts']:
        parts = []
        for part in record['ordered_parts']:
            content = (ROOT / part['path']).read_bytes()
            assert len(content) == part['bytes']
            assert hashlib.sha256(content).hexdigest() == part['sha256']
            parts.append(content)
        original = b''.join(parts)
        assert len(original) == record['bytes']
        assert hashlib.sha256(original).hexdigest() == record['sha256']
        actual = audit_zip(original, record['role'])
        assert actual == record['audit'], record['name']
        results.append(dict(name=record['name'], **actual))
    print(json.dumps({'status': 'ORIGINALS_VERIFIED', 'artifacts': results}, indent=2))


if __name__ == '__main__':
    main()
