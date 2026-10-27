"""Strict diagnostic receiving. Expected baseline failures are not feature acceptance."""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())

def reports(raw, variant):
    assert len(raw)==1
    root=ET.fromstring(next(iter(raw.values())))
    rows=root.findall('testcase')
    assert len(rows)==4 and root.get('tests')=='4'
    assert root.get('errors')=='0' and root.get('skipped')=='0'
    assert sorted(r.get('name') for r in rows)==sorted(P['cases'])
    assert all(r.get('classname')==P['class'] for r in rows)
    expected=P['witnesses'][variant]
    failed={r.get('name'):r.find('failure') for r in rows if r.find('failure') is not None}
    assert set(failed)==set(expected) and int(root.get('failures'))==len(expected)
    assert not any(r.find('error') is not None or r.find('skipped') is not None for r in rows)
    for name,f in failed.items():
        assert f.get('type')=='org.opentest4j.AssertionFailedError'
        frame=re.search(r'ActivatedPhyrexianPaymentDiagnosticTest\.kt:(\d+)',f.text or '')
        assert frame and int(frame.group(1))==expected[name]['line'],name
        for token in expected[name]['contains']:
            assert token in (f.get('message','')+'\n'+(f.text or '')),(name,token)
    return dict(cases=[r.get('name') for r in rows],passed=4-len(failed),failures=len(failed),errors=0,skips=0,witnesses=expected)

def stage(folder):
    folder=Path(folder);name=folder.name
    rc=int((folder/'exit.txt').read_text());log=(folder/'output.log').read_text()
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    if name.startswith('compile-'):
        assert rc==0 and 'BUILD SUCCESSFUL' in log
        assert re.search(r'^> Task :rules-engine:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
        assert not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
        assert not list(folder.rglob('TEST-*.xml'))
        return dict(compiled=True,behavioral_tests=0)
    assert rc!=0
    assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
    return reports({p.name:p.read_bytes() for p in folder.glob('junit/TEST-*.xml')},'mutant' if name=='mutant' else 'baseline')
