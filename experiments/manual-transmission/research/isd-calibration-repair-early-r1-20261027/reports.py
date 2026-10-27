"""Fail closed on original JUnit identities, multiplicity and executable mutant assertion frames."""
import json,re
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())
def junit(raw,variant='normal',full=False):
    expected={(w['test_class'],w['name']):w['frame'] for w in P['mutations'].get(variant,{}).get('witnesses',[])}
    paths=[n for n in raw if n.endswith('.xml')];assert len(paths)==(23 if full else 2),paths
    identities=[];failed=[];skipped=[]
    for path in paths:
        root=ET.fromstring(raw[path]);assert root.tag=='testsuite'
        rows=root.findall('testcase');assert int(root.get('tests'))==len(rows)
        errors=sum(len(r.findall('error')) for r in rows);skips=sum(len(r.findall('skipped')) for r in rows)
        failures=sum(len(r.findall('failure')) for r in rows)
        assert errors==int(root.get('errors'))==0
        assert skips==int(root.get('skipped'))
        assert failures==int(root.get('failures'))
        for row in rows:
            key=(row.get('classname'),row.get('name'));identities.append(key)
            if row.find('skipped') is not None:skipped.append(key)
            assert not (row.find('skipped') is not None and row.find('failure') is not None)
            assert len(row.findall('failure'))<=1
            failure=row.find('failure')
            if failure is not None:
                assert key in expected,key
                assert failure.get('type')==P['assertion_type'],failure.attrib
                frame=re.search(re.escape(key[0].rsplit('.',1)[1])+r'\.kt:(\d+)',failure.text or '')
                assert frame and int(frame.group(1))==expected[key],(key,failure.text)
                failed.append(key)
    want=P['full_module_cases'] if full else P['targeted_cases']
    assert Counter(identities)==Counter(map(tuple,want)),(identities,want)
    assert Counter(failed)==Counter(expected.keys())
    assert Counter(skipped)==Counter(map(tuple,P['full_module_baseline_skips'] if full else []))
    return dict(tests=len(identities),failures=len(failed),passed=len(identities)-len(failed)-len(skipped),errors=0,skips=len(skipped),skipped_identities=skipped,identities=identities,witnesses=failed)
def stage(folder):
    folder=Path(folder);name=folder.name;rc=int((folder/'exit.txt').read_text());log=(folder/'output.log').read_text()
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    paths=list(folder.glob('junit/*.xml'))
    if name.startswith('compile-'):
        assert rc==0 and 'BUILD SUCCESSFUL' in log
        assert re.search(r'^> Task :mtgish-tooling:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
        assert not re.search(r'^> Task :mtgish-tooling:test(?: |$)',log,re.M) and not paths
        return dict(compile=True,tests=0)
    expected=name in P['mutations'];assert (rc!=0)==expected
    assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==([':mtgish-tooling:test'] if expected else [])
    if not expected:assert 'BUILD SUCCESSFUL' in log
    return junit({p.name:p.read_bytes() for p in paths},name,name=='full-module')
