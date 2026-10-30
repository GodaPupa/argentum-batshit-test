"""Strict raw-report checks adapted from the accepted Bolt Bend receiving auditor."""
from collections import Counter
from pathlib import Path
import hashlib,json,re,xml.etree.ElementTree as ET
def verify_export(raw):
    assert raw['exit.txt'].strip()==b'0'
    log=raw['output.log'].decode();assert 'BUILD SUCCESSFUL' in log
    assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
    reports={k:v for k,v in raw.items() if k.startswith('junit/') and k.endswith('.xml')}
    assert len(reports)==1
    path,report=next(iter(reports.items()))
    cls='com.wingedsheep.mtg.sets.OpalPalaceSnapshotExportTest'
    assert path=='junit/mtg-sets/build/test-results/test/TEST-'+cls+'.xml'
    root=ET.fromstring(report);assert root.get('name')==cls
    assert [int(root.get(k)) for k in ['tests','failures','errors','skipped']]==[1,0,0,0]
    rows=root.findall('testcase');assert len(rows)==1
    row=rows[0];assert row.get('classname')==cls and row.get('name')=='Opal Palace canonical discovery and lossless snapshot candidate'
    assert not any(row.find(k) is not None for k in ['failure','error','skipped'])
    return dict(cases=1,failures=0,skips=0,snapshot_admitted=False)
def verify(raw,contract,form):
    negative=form in contract['witnesses']
    assert negative or form in ['normal','restored']
    witnesses=contract['witnesses'].get(form,{})
    selected=list(witnesses)+contract['controls'] if negative else list(contract['cases'])
    cases={key:contract['cases'][key] for key in selected}
    rc=int(raw['exit.txt']);log=raw['output.log'].decode()
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    assert (rc!=0)==negative
    assert ('BUILD FAILED' if negative else 'BUILD SUCCESSFUL') in log
    assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==([':mtg-sets:2008-2016:tests:test'] if negative else [])
    classes={c['classname'] for c in cases.values()}
    reports={k:v for k,v in raw.items() if k.startswith('junit/') and k.endswith('.xml')}
    assert len(reports)==len(classes)
    identities=[];found=[];seenclasses=[]
    for path,report in reports.items():
        root=ET.fromstring(report);cls=root.get('name');assert cls in classes;seenclasses.append(cls)
        expected={key:c for key,c in cases.items() if c['classname']==cls}
        module=next(iter(expected.values()))['module'].lstrip(':').replace(':','/')
        assert path=='junit/'+module+'/build/test-results/test/TEST-'+cls+'.xml'
        rows=root.findall('testcase')
        assert [int(root.get(k)) for k in ['tests','failures','errors','skipped']]==[len(expected),sum(key in witnesses for key in expected),0,0]
        assert len(rows)==len(expected)
        for row in rows:
            key=row.get('classname','')+'::'+row.get('name','');identities.append(key)
            assert key in expected and row.find('error') is None and row.find('skipped') is None
            failure=row.findall('failure')
            if key not in witnesses:assert not failure;continue
            assert len(failure)==1
            f=failure[0];w=witnesses[key];case=cases[key]
            assert f.get('type')=='org.opentest4j.AssertionFailedError'
            assert w['message'] in f.get('message','') and w['message'] in (f.text or '')
            frames=re.findall(re.escape(case['file'])+r':(\d+)',f.text or '')
            assert frames and int(frames[0])==w['line']
            found.append(dict(identity=key,line=int(frames[0]),message=f.get('message')))
    assert Counter(seenclasses)==Counter(classes)
    assert Counter(identities)==Counter(selected)
    assert len(found)==len(witnesses)
    return dict(form=form,rows=len(identities),failures=found,passing_controls=len(identities)-len(found),skips=0)
if __name__=='__main__':
    import sys
    folder=Path(sys.argv[1]);contract=json.loads(Path(sys.argv[2]).read_text())
    print(json.dumps(verify({str(p.relative_to(folder)):p.read_bytes() for p in folder.rglob('*') if p.is_file()},contract,sys.argv[3]),indent=2))
