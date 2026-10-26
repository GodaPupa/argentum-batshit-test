"""Strict raw-report gates; no summary or successful workflow implies acceptance."""
import json,re
import xml.etree.ElementTree as ET
from pathlib import Path
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())

def server(raw, mutant=False):
    paths=[k for k in raw if k.endswith('.xml')]
    assert len(paths)==1, paths
    root=ET.fromstring(raw[paths[0]])
    rows=root.findall('testcase')
    assert root.get('tests')=='4' and root.get('errors')=='0' and root.get('skipped')=='0'
    names=[r.get('name') for r in rows]
    assert sorted(names)==sorted(P['server_cases'])
    assert all(r.get('classname')=='com.wingedsheep.gameserver.session.FixedRecipientChoiceTransportTest' for r in rows)
    failures={r.get('name'):r.find('failure') for r in rows if r.find('failure') is not None}
    expected=P['server_witnesses'] if mutant else {}
    assert set(failures)==set(expected) and int(root.get('failures'))==len(expected)
    assert not any(r.find('error') is not None or r.find('skipped') is not None for r in rows)
    for name,f in failures.items():
        assert f.get('type')==P['assertion_type']
        frame=re.search(r'FixedRecipientChoiceTransportTest\.kt:(\d+)',f.text or '')
        assert frame and int(frame.group(1))==expected[name]
    return dict(cases=names,passed=4-len(failures),failures=len(failures),errors=0,skips=0)

def client(data, variant='normal'):
    report=json.loads(data)
    assert report['numTotalTests']==P['client_count'] and report['numPendingTests']==0 and report['numTodoTests']==0
    suites=report['testResults']; assert len(suites)==1
    rows=suites[0]['assertionResults']
    assert len(rows)==P['client_count'] and sorted(r['title'] for r in rows)==P['client_cases']
    assert all(r['status'] in ['passed','failed'] for r in rows)
    assert all(r['fullName']==P['client_full_names'][r['title']] for r in rows)
    expected=P.get('client_'+variant+'_witnesses',{}) if variant!='normal' else {}
    failed={r['title']:r for r in rows if r['status']=='failed'}
    assert set(failed)==set(expected)
    assert report['numFailedTests']==len(expected) and report['numPassedTests']==P['client_count']-len(expected)
    assert bool(report['success']) == (not expected)
    for name,r in failed.items():
        messages=r['failureMessages']; assert len(messages)==1
        assert 'AssertionError' in messages[0]
        frame=re.search(r'liveInteraction\.test\.ts:(\d+)',messages[0])
        assert frame and int(frame.group(1))==expected[name]
    return dict(cases=[r['title'] for r in rows],passed=P['client_count']-len(expected),failures=len(expected),errors=0,skips=0)

def stage(folder):
    folder=Path(folder); name=folder.name
    rc=int((folder/'exit.txt').read_text()); log=(folder/'output.log').read_text()
    assert not re.search(r'^e: |Compilation error|Unresolved reference|error TS\d+',log,re.M)
    if name.startswith('compile-'):
        assert rc==0
        if 'server' in name:
            assert 'BUILD SUCCESSFUL' in log
            assert re.search(r'^> Task :game-server:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
            assert not re.search(r'^> Task :game-server:test(?: |$)',log,re.M)
        assert not list(folder.rglob('TEST-*.xml')) and not (folder/'vitest.json').exists()
        return dict(compile=True,tests=0)
    if 'server' in name:
        mutant=name=='server-mutant'
        assert (rc!=0)==mutant
        assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==([':game-server:test'] if mutant else [])
        return server({p.name:p.read_bytes() for p in folder.glob('junit/*.xml')},mutant)
    variant=name.removeprefix('client-') if name in ['client-index','client-stale','client-legacy','client-group'] else 'normal'
    assert (rc!=0)==(variant!='normal')
    return client((folder/'vitest.json').read_bytes(),variant)
