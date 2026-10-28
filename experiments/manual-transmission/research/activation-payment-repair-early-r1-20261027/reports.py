from pathlib import Path
import json,re,collections,xml.etree.ElementTree as ET
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())

def reports(raw, variant):
    expected_classes={P['control_class']:P['classes'][P['control_class']]} if variant in P['mutants'] else P['classes']
    assert len(raw)==len(expected_classes)
    expected=collections.Counter((c,n) for c,names in expected_classes.items() for n in names)
    rows=[];summary_failures=0
    for data in raw.values():
        root=ET.fromstring(data);cases=root.findall('testcase');rows+=cases
        assert int(root.get('tests'))==len(cases)
        assert root.get('errors')=='0' and root.get('skipped')=='0'
        summary_failures+=int(root.get('failures'))
    assert collections.Counter((r.get('classname'),r.get('name')) for r in rows)==expected
    assert not any(r.find('error') is not None or r.find('skipped') is not None for r in rows)
    witnesses=P['mutants'][variant]['witnesses'] if variant in P['mutants'] else {}
    failed={r.get('name'):r.find('failure') for r in rows if r.find('failure') is not None}
    assert set(failed)==set(witnesses),(variant,sorted(failed),sorted(witnesses))
    assert summary_failures==len(failed)
    for name,f in failed.items():
        w=witnesses[name]
        assert f.get('type')=='org.opentest4j.AssertionFailedError',(name,f.get('type'))
        frame=re.search(r'ActivatedPhyrexianPaymentControlTest\.kt:(\d+)',f.text or '')
        assert frame and int(frame.group(1))==w['line'],(name,frame.group(1) if frame else None,w['line'])
        for token in w['contains']:assert token in (f.get('message','')+'\n'+(f.text or '')),(name,token)
    return dict(cases=len(rows),passed=len(rows)-len(failed),failures=len(failed),errors=0,skips=0,witnesses=witnesses)

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
    if name in P['mutants']:
        assert rc!=0
        assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
    else:
        assert rc==0 and 'BUILD SUCCESSFUL' in log
        assert not re.findall(r'^> Task (\S+) FAILED$',log,re.M)
    return reports({p.name:p.read_bytes() for p in folder.glob('junit/TEST-*.xml')},name)
