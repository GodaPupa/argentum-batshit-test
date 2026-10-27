"""Strict raw original JUnit identities, executable assertion witnesses and compilation gates."""
import json,re
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())
def junit(raw,variant='normal',full=False):
    expected=P['mutations'].get(variant,{}).get('witnesses',{})
    paths=[n for n in raw if n.endswith('.xml')];assert len(paths)==1,paths
    identities=[];failed=[];summed=Counter()
    for path in paths:
        root=ET.fromstring(raw[path]);assert root.tag=='testsuite'
        rows=root.findall('testcase');assert int(root.get('tests'))==len(rows)
        errors=sum(len(r.findall('error')) for r in rows);skips=sum(len(r.findall('skipped')) for r in rows)
        failures=sum(len(r.findall('failure')) for r in rows)
        assert errors==skips==int(root.get('errors'))==int(root.get('skipped'))==0
        assert int(root.get('failures'))==failures
        summed.update(tests=len(rows),failures=failures)
        for r in rows:
            key=(r.get('classname'),r.get('name'));identities.append(key)
            assert len(r.findall('failure'))<=1
            f=r.find('failure')
            if f is not None:
                assert key[0]==P['test_class'] and key[1] in expected,key
                assert f.get('type')==P['assertion_type'],f.attrib
                frame=re.search(r'PublicOccurrenceMatrixTest\.kt:(\d+)',f.text or '')
                assert frame and int(frame.group(1))==expected[key[1]],(key,f.text)
                assert P['mutations'][variant]['marker'] in (f.text or ''), (key,'missing marker')
                failed.append(key[1])
    want=[[P['test_class'],n] for n in P['mutations'].get(variant,{}).get('cases',P['cases'])]
    assert Counter(identities)==Counter(map(tuple,want)),(identities,want)
    assert Counter(failed)==Counter(expected.keys())
    return dict(tests=len(identities),failures=len(failed),passed=len(identities)-len(failed),errors=0,skips=0,identities=identities,witnesses=failed)
def stage(folder):
    folder=Path(folder);name=folder.name;rc=int((folder/'exit.txt').read_text());log=(folder/'output.log').read_text()
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    paths=list(folder.glob('junit/*.xml'))
    if name.startswith('compile-'):
        assert rc==0 and 'BUILD SUCCESSFUL' in log
        assert re.search(r'^> Task :rules-engine:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
        assert not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M) and not paths
        return dict(compile=True,tests=0)
    expected=name in P['mutations'];assert (rc!=0)==expected
    assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==([':rules-engine:test'] if expected else [])
    if not expected:assert 'BUILD SUCCESSFUL' in log
    return junit({p.name:p.read_bytes() for p in paths},name,name=='full-module')
