import json,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
D=Path(__file__).resolve().parent
P=json.loads((D/'PROTOCOL.json').read_text())
def read_report(folder):
    report=json.loads((folder/'report.json').read_text())
    assert not report.get('errors'), 'top-level browser errors'
    cases={}
    def walk(suites):
        for suite in suites:
            for spec in suite.get('specs',[]):
                assert spec['title'] not in cases,'duplicate browser identity'
                tests=spec['tests'];assert len(tests)==1,'project multiplicity'
                results=tests[0]['results'];assert len(results)==1,'retry or missing result'
                r=results[0];assert r['status'] in ['passed','failed'],'skip/timeout/interruption is not evidence'
                cases[spec['title']]=dict(status=r['status'],errors=r.get('errors',[]),attachments=r.get('attachments',[]))
            walk(suite.get('suites',[]))
    walk(report['suites']);return cases
def browser_gate(root,stage):
    cases=read_report(root/stage)
    mutant=next((m for m in P['mutations'] if m['name']==stage),None)
    expected=([mutant['witness'],P['control']] if mutant else P['browser_cases'])
    assert set(cases)==set(expected),(stage,'identities',list(cases))
    failed=[]
    for title,c in cases.items():
        expected_failure=mutant is not None and title==mutant['witness']
        assert c['status']==('failed' if expected_failure else 'passed'),(stage,title,c['status'])
        if expected_failure:
            failed.append(title)
            errors=json.dumps(c['errors']);assert mutant['marker'] in errors,(stage,'missing assertion witness',errors)
            assert 'expect' in errors.lower(), 'failure is not an assertion'
        else:assert not c['errors'],(stage,title,'unexpected errors')
        names=[a['name'] for a in c['attachments']]
        for name in ['decision-wire.json','owner-observed.json','owner-dom.html','owner-screen.png','seat-0-trace','seat-1-trace']:
            assert names.count(name)==1,(stage,title,'missing/multiple attachment',name)
            attachment=next(a for a in c['attachments'] if a['name']==name)
            marker='manual-public-occurrence-ui-r4-evidence/'
            original=attachment['path'];assert marker in original
            relative=original.split(marker,1)[1]
            assert '..' not in Path(relative).parts and not relative.startswith('/')
            file=root/relative;assert file.is_file() and file.stat().st_size>0
            if name.endswith('-trace'):
                with zipfile.ZipFile(file) as trace:
                    assert trace.testzip() is None
                    assert any(n.endswith('.trace') for n in trace.namelist())
            if name=='owner-screen.png':assert file.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
    assert int((root/stage/'exit.txt').read_text())==(1 if mutant else 0),stage
    return dict(cases=len(cases),passes=len(cases)-len(failed),failures=failed,skips=0,retries=0)
def audit(root,only=None):
    root=Path(root)
    if only:return {only:browser_gate(root,only)}
    for stage in P['compile_stages']:
        assert (root/stage/'exit.txt').read_text().strip()=='0',stage
        assert (root/stage/'output.log').stat().st_size>0,stage
    cases=[]
    for file in (root/'fixture-tests/junit').glob('TEST-*.xml'):
        doc=ET.parse(file).getroot()
        for tc in doc.iter('testcase'):
            assert not any(tc.find(x) is not None for x in ['failure','error','skipped']),tc.attrib
            cases.append([tc.get('classname'),tc.get('name')])
    assert sorted(cases)==sorted(P['fixture_cases']),cases
    assert (root/'fixture-tests/exit.txt').read_text().strip()=='0'
    return {'compile_stages':len(P['compile_stages']),'fixture_cases':cases,
        'browser':{stage:browser_gate(root,stage) for stage in ['normal',*[m['name'] for m in P['mutations']],'restored']}}
