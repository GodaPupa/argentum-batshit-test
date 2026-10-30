"""Strict original-defect reproduction audit; never feature or card admission."""
from pathlib import Path
from collections import Counter
import json,re,xml.etree.ElementTree as ET
P=json.loads((Path(__file__).parent/'PROTOCOL.json').read_text())
def verify(folder):
    folder=Path(folder)
    assert int((folder/'exit.txt').read_text())!=0
    log=(folder/'output.log').read_text()
    assert 'BUILD FAILED' in log
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
    files=list((folder/'junit').rglob('TEST-*.xml'))
    assert len(files)==1
    assert str(files[0].relative_to(folder/'junit'))=='rules-engine/build/test-results/test/TEST-'+P['class']+'.xml'
    root=ET.fromstring(files[0].read_bytes());rows=root.findall('testcase')
    assert root.get('name')==P['class']
    assert [int(root.get(k)) for k in ['tests','failures','errors','skipped']]==[5,2,0,0]
    assert Counter((t.get('classname'),t.get('name')) for t in rows)==Counter((P['class'],x) for x in P['cases'])
    failures=[]
    for row in rows:
        name=row.get('name');f=row.find('failure')
        assert row.find('error') is None and row.find('skipped') is None
        if name in P['witnesses']:
            assert f is not None and len(row.findall('failure'))==1
            assert f.get('type')=='org.opentest4j.AssertionFailedError'
            expected='expected:<"{R}"> but was:<"{3}{R}">'
            assert expected in f.get('message','') and expected in (f.text or '')
            frames=re.findall(r'BoltBendCostPredicateTest\.kt:(\d+)',f.text or '')
            assert frames and int(frames[0])==P['witnesses'][name]
            failures.append({'name':name,'line':int(frames[0]),'type':f.get('type'),'message':f.get('message')})
        else:assert f is None
    return {'state':'ORIGINAL_DEFECT_REPRODUCED_RECEIVING_PENDING','rows':5,'assertion_failures':failures,'passing_controls':3,'skips':0,'feature_admitted':False,'card_admitted':False}
