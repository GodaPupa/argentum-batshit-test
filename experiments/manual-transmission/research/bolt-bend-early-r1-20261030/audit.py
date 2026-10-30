from pathlib import Path
from collections import Counter
import json,re,xml.etree.ElementTree as ET
P=json.loads((Path(__file__).parent/'PROTOCOL.json').read_text())
def verify(folder,form='normal'):
    folder=Path(folder);w=P['witnesses'].get(form,{})
    assert form in ['normal','restored'] or form in P['witnesses']
    rc=int((folder/'exit.txt').read_text());log=(folder/'output.log').read_text()
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    assert (rc!=0)==bool(w)
    assert ('BUILD FAILED' if w else 'BUILD SUCCESSFUL') in log
    assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==([':rules-engine:test'] if w else [])
    files=list((folder/'junit').rglob('TEST-*.xml'));classes={c['class'] for c in P['cases'].values()}
    assert len(files)==len(classes)==2
    observed=[];failures=[]
    for path in files:
        root=ET.fromstring(path.read_bytes());cls=root.get('name');assert cls in classes
        assert str(path.relative_to(folder/'junit'))=='rules-engine/build/test-results/test/TEST-'+cls+'.xml'
        rows=root.findall('testcase');expected=[(id,c) for id,c in P['cases'].items() if c['class']==cls]
        assert [int(root.get(k)) for k in ['tests','failures','errors','skipped']]==[len(expected),sum(id in w for id,c in expected),0,0]
        for row in rows:
            key=(row.get('classname'),row.get('name'));observed.append(key)
            matches=[(id,c) for id,c in expected if key==(c['class'],c['name'])];assert len(matches)==1
            id,c=matches[0];fs=row.findall('failure');assert row.find('error') is None and row.find('skipped') is None
            if id in w:
                assert len(fs)==1;f=fs[0];assert f.get('type')=='org.opentest4j.AssertionFailedError'
                assert w[id]['message'] in f.get('message','') and w[id]['message'] in (f.text or '')
                frames=re.findall(re.escape(c['file'])+r':(\d+)',f.text or '')
                assert frames and int(frames[0])==w[id]['line']
                failures.append({'id':id,'line':int(frames[0]),'message':f.get('message')})
            else:assert not fs
    assert Counter(observed)==Counter((c['class'],c['name']) for c in P['cases'].values())
    return {'form':form,'rows':16,'failures':failures,'passing_controls':16-len(w),'skips':0}
