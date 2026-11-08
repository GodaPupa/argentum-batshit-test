"""Read original JUnit bytes; reject missing/duplicate/skipped/unrelated negative evidence."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

def rows(directory):
    result = []
    for path in sorted(Path(directory).rglob('TEST-*.xml')):
        root = ET.parse(path).getroot()
        assert root.tag == 'testsuite', path
        cases = list(root.iter('testcase'))
        assert int(root.get('tests')) == len(cases), path
        for plural, child in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
            assert int(root.get(plural)) == sum(bool(t.findall(child)) for t in cases), (path, plural)
        for t in cases:
            result.append(dict(classname=t.get('classname'), name=t.get('name'),
                               failures=[dict(f.attrib, text=f.text or '') for f in t.findall('failure')],
                               errors=[dict(f.attrib, text=f.text or '') for f in t.findall('error')],
                               skips=[dict(f.attrib, text=f.text or '') for f in t.findall('skipped')]))
    return result

def verify(report_rows, protocol, exit_code, mutant=None):
    expected = Counter((protocol['class'], name) for name in protocol['cases'])
    assert Counter((r['classname'], r['name']) for r in report_rows) == expected, 'Exact identities and multiplicities'
    assert all(not r['errors'] and not r['skips'] for r in report_rows), 'No errors or skips'
    observed = Counter(r['name'] for r in report_rows if r['failures'])
    expected_failures = Counter(mutant['failed_identities']) if mutant else Counter()
    assert observed == expected_failures, ('Exact negative failures', observed, expected_failures)
    assert (exit_code != 0) == bool(expected_failures), 'Exit must agree with reports'
    for r in report_rows:
        if not r['failures']:
            continue
        assert len(r['failures']) == 1, r['name']
        witness = mutant['witnesses'][r['name']]
        failure = r['failures'][0]
        assert failure['type'] == witness['assertion_type'], r['name']
        assert re.search(re.escape(witness['file']) + ':' + str(witness['line']) + r'\b', failure['text']), r['name']
        message = failure.get('message', '')
        assert re.search(r'expected.*?\b' + witness['expected'] + r'\b', message, re.I | re.S), message
        assert re.search(r'but.*?\b' + witness['actual'] + r'\b', message, re.I | re.S), message
    return dict(rows=len(report_rows), failures=sum(observed.values()), skips=0, verified=True)
