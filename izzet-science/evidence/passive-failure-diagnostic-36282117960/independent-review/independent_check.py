"""Read original diagnostic evidence only. No engine, subprocess or network execution."""
import base64, datetime, hashlib, json, pathlib, re, zipfile
import xml.etree.ElementTree as ET

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
PACKAGE = ROOT / 'izzet-takeover/shared-receiving/prospective-failure-diagnostic/publication/izzet-science/evidence/passive-failure-diagnostic-36282117960'
ORIGINAL = ROOT / 'attachments/731e4675-260a-4563-8338-94a4dc6b1452/izzet-six-case-diagnostic-original-10919123143.zip'
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
sources = {}
for r in json.loads((HERE / 'live-source.json').read_text()):
    assert r['status'] == 'fulfilled'
    v = r['value']; sources[v['scope'], v['path']] = v['response']['structuredContent']['content'].encode()
metadata = {}
for r in json.loads((HERE / 'live-metadata.json').read_text()):
    assert r['status'] == 'fulfilled'
    v = r['value']; metadata[v['endpoint']] = json.loads(v['response']['structuredContent']['content'])
raw = ORIGINAL.read_bytes()
assert raw == (PACKAGE / 'original-artifact.zip').read_bytes()
assert len(raw) == 2687709 and sha(raw) == '8bfcb2881786acbc6c93ef0bb8e639baa9c86a37f187189977eea11b24c90a6b'
author = json.loads((PACKAGE / 'original-author-audit.json').read_text())
assert sha((PACKAGE / 'original-author-audit.json').read_bytes()) == '990538ec9e5a70cd93c672c8e18728bda7148a5c5ae39f4c101187c6f1e97e14'
report = {'schema':'pest-independent-izzet-six-diagnostic-checks-v1','reviewer':'actual /root/pest','original_sha256':sha(raw),'package_files':{p.name:{'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())} for p in sorted(PACKAGE.iterdir()) if p.is_file()},'stages':[]}
with zipfile.ZipFile(ORIGINAL) as z:
    names = z.namelist()
    assert len(names) == len(set(names)) == 87 and z.testzip() is None
    members = {n:{'bytes':len(z.read(n)), 'sha256':sha(z.read(n))} for n in names}
    assert members == author['members']
    report['member_count'] = len(names)
    report['uncompressed_bytes'] = sum(v['bytes'] for v in members.values())
    gate = json.loads(z.read('control/scope.json')); audit = json.loads(z.read('audit.json'))
    before = json.loads(z.read('source-before.json')); after = json.loads(z.read('source-restored.json')); during = json.loads(z.read('instrumented-source.json'))
    assert before == after == audit['source_before'] == audit['source_restored'] and before['status'] == ''
    assert during == audit['instrumented_source']
    for key in ['head','tree']: assert before[key] == during[key] == gate['source_' + key]
    controls = {e['path']:e for e in json.loads((HERE/'live-control-tree.json').read_text())['tree'] if e['type']=='blob'}
    assert set(controls) == {'run.py','scope.json','.github/workflows/izzet-receiving-failure-diagnostic.yml'}
    for path, entry in controls.items():
        b = sources['control',path]
        assert b == z.read('control/'+path) and sha(b) == audit['control_sha256'][path]
        assert blob(b) == entry['sha'] and len(b) == entry['size']
    assert metadata['git/commits/'+audit['control_head']]['tree']['sha'] == audit['control_tree'] == 'a9a5fd284f73f6879437eed344e8578510e0d52d'
    assert metadata['git/commits/'+before['head']]['tree']['sha'] == before['tree'] == '4f91346617cd0d4e29e09ab8405c141883cf1820'
    assert sha(gate['source_review']['record'].encode()) == gate['source_review']['sha256']
    report['independent_source_hashes'] = {}
    for path, digest in before['file_sha256'].items():
        b = sources['source',path]
        assert sha(b) == digest
        report['independent_source_hashes'][path] = {'sha256':sha(b),'git_blob':blob(b)}
    for row in gate['instrumentation']:
        p = row['path']; original = sources['source',p]; instrumented = z.read('instrumentation/'+p)
        assert original == z.read('instrumentation/'+p+'.original')
        assert blob(original) == row['original_git_blob'] and sha(original) == row['original_sha256']
        old,new = row['replacement_old'].encode(),row['replacement_new'].encode()
        assert original.count(old) == instrumented.count(new) == 1
        assert original.replace(old,new) == instrumented and instrumented.replace(new,old) == original
        assert sha(instrumented) == row['instrumented_sha256'] == during['file_sha256'][p]
    changed = {p for p in before['file_sha256'] if before['file_sha256'][p] != during['file_sha256'][p]}
    assert changed == {r['path'] for r in gate['instrumentation']}
    assert {line.strip()[2:] for line in during['status'].splitlines()} == changed
    for row in gate['unchanged_tests']:
        b = sources['source',row['path']]
        assert blob(b) == row['git_blob'] and sha(b) == row['sha256']
    run = metadata['actions/runs/36282117960']
    assert (run['status'],run['conclusion'],run['event'],run['run_attempt'],run['head_sha']) == ('completed','failure','push',1,audit['control_head'])
    jobs = metadata['actions/runs/36282117960/jobs?per_page=100']
    artifacts = metadata['actions/runs/36282117960/artifacts?per_page=100']
    assert jobs['total_count'] == len(jobs['jobs']) == artifacts['total_count'] == len(artifacts['artifacts']) == 1
    job = jobs['jobs'][0]; artifact = artifacts['artifacts'][0]
    assert job['conclusion']=='failure' and artifact['id']==10919123143 and artifact['size_in_bytes']==len(raw)
    if artifact.get('digest'): assert artifact['digest'] == 'sha256:'+sha(raw)
    report['live_run']={k:run[k] for k in ['id','head_sha','event','run_attempt','status','conclusion','created_at','updated_at']}
    report['live_job']={k:job[k] for k in ['id','started_at','completed_at','conclusion']}
    totals={'tests':0,'failures':0,'errors':0,'skipped':0}
    decisions=[]
    for i, bank in enumerate(gate['existing_class_bank'],1):
        folder=f'{i:02d}-{bank["stage"]}'
        command=json.loads(z.read(folder+'/command.json')); rawlog=z.read(folder+'/command.log'); log=rawlog.decode(); lines=log.splitlines()
        assert command == audit['stages'][i-1] and command['exit_status']==1 and z.read(folder+'/exit-status.txt')==b'1\n'
        assert command['technical_limit'] is None and command['output_complete'] and command['discarded_observed_pipe_bytes']==0 and command['stdout_retained_bytes']==len(rawlog)
        task='> Task :'+bank['module'].replace('/',':')+':test'
        assert lines.count(task+' FAILED')==1
        assert not any(task+' '+suffix in lines for suffix in ['FROM-CACHE','UP-TO-DATE','NO-SOURCE','SKIPPED'])
        assert len(re.findall(r'Gradle Test Executor \d+ started executing tests\.',log))==len(re.findall(r'Gradle Test Executor \d+ finished executing tests\.',log))==1
        assert len(re.findall(r'^BUILD FAILED in ',log,re.M))==1
        rawxml=z.read(folder+'/TEST-'+bank['class']+'.xml'); suite=ET.fromstring(rawxml); cases=suite.findall('testcase')
        assert suite.attrib['name']==bank['class']
        identities=[(c.attrib['classname'],c.attrib['name']) for c in cases]
        assert len(cases)==len(set(identities))==int(suite.attrib['tests'])==bank['expected_cases']
        assert {n for c,n in identities}==set(bank['case_names']) and {c for c,n in identities}=={bank['class']}
        timestamp=datetime.datetime.fromisoformat(suite.attrib['timestamp']).timestamp()*1e9
        assert command['started_ns']-2e9<=timestamp<=command['finished_ns']+2e9
        xmlret=command['xml_retention']; assert len(xmlret)==1 and xmlret[0]['complete'] and xmlret[0]['observed_source_bytes']==xmlret[0]['retained_bytes']==len(rawxml) and xmlret[0]['sha256']==sha(rawxml)
        results={c.attrib['name']:('failure' if c.find('failure') is not None else 'pass') for c in cases}
        for k in totals:
            actual=len(cases) if k=='tests' else sum(c.find(k[:-1] if k!='skipped' else k) is not None for c in cases)
            assert actual==int(suite.attrib[k]);totals[k]+=actual
        traces=[]; records=[]
        for node in suite.iter('system-out'):
            for line in (node.text or '').splitlines():
                for prefix,dst in [('IZZET_DIAGNOSTIC_TRACE ',traces),('IZZET_DECISION_DIAGNOSTIC ',records)]:
                    if line.startswith(prefix):dst.append(base64.b64decode(line[len(prefix):],validate=True))
        if i==1:
            assert len(traces)==413 and not records
            stream=b''.join(traces)
            assert stream==z.read(folder+'/baseline-hash-input.bin')==(PACKAGE/'candidate-stream.txt').read_bytes()
            assert [int(re.match(rb'A(\d+)\|',t).group(1)) for t in traces[:-1]]==list(range(1,413))
            assert all(t.endswith(b'\n') and t.count(b'\n')==1 for t in traces)
            base=(PACKAGE/'accepted-base-stream.txt').read_bytes();old=base.splitlines(keepends=True)
            assert len(old)==413 and sha(base)=='47e993c61a57ebbd30d4a58c18aa84da119347836c0116eae053be61746014d8'
            diff=[j for j in range(413) if old[j]!=traces[j]];assert len(diff)==14
            for a,b in zip(diff[::2],diff[1::2]):
                assert b==a+1 and b'DeclareBlockers(' in traces[a-1]
                assert old[a].split(b'|',1)[1]==traces[b].split(b'|',1)[1] and old[b].split(b'|',1)[1]==traces[a].split(b'|',1)[1]
                assert all(b'|DECLARE_BLOCKERS|PassPriority(' in t for t in (old[a],old[b],traces[a],traces[b]))
            assert traces[-1]==b'END|turns=20|winner=1|life=-8/16\n'
            report['baseline']={'entries':413,'bytes':len(stream),'sha256':sha(stream),'changed_entries':[i+1 for i in diff],'end':traces[-1].decode()}
        else:
            assert len(records)==(45 if i==2 else 18) and not traces
            logged=[]
            for line in lines:
                marker='IZZET_DECISION_DIAGNOSTIC '
                if marker in line:logged.append(base64.b64decode(line.split(marker,1)[1].strip(),validate=True))
            assert records==logged
            for j,b in enumerate(records,1):
                assert b==z.read(folder+f'/action-{j:03d}.txt')
                text=b.decode(); assert all(k in text for k in ['\nbefore=','\nresultState=','\nstoredAfter='])
                if text.splitlines()[3]!='afterPendingType=null':decisions.append({'class':bank['class'],'record':j,'type':text.splitlines()[3],'sha256':sha(b)})
        report['stages'].append({'class':bank['class'],'cases':results,'task':task+' FAILED','fresh_workers':1,'command_exit':1,'xml_sha256':sha(rawxml),'log_sha256':sha(rawlog),'decision_records':len(records),'trace_records':len(traces),'xml_timestamp':suite.attrib['timestamp']})
    assert totals=={'tests':6,'failures':4,'errors':0,'skipped':0}
    assert [(r['record'],r['type'].split('.')[-1]) for r in decisions]==[(32,'ChooseOptionDecision'),(9,'ChooseTargetsDecision'),(18,'ChooseTargetsDecision')]
    arc=z.read('02-historical-regression-ArcaneDenialScenarioTest/action-032.txt').decode().split('\nstoredAfter=',1)[1]
    assert 'turnNumber=2, activePlayerId=e1, phase=BEGINNING, step=UPKEEP' in arc and 'answer=TriggerOrderingContinuation(' in arc and arc.count('triggerContext=TriggerContext(')==2
    assert arc.count('damageAmount=null, step=UPKEEP')==2 and 'at the beginning of each End Step' in arc
    for j in [9,18]:
        wall=z.read(f'03-historical-regression-MnemonicWallScenarioTest/action-{j:03d}.txt').decode().split('\nstoredAfter=',1)[1]
        assert 'answer=TriggeredAbilityContinuation(' in wall and 'effect=GatedEffect(gate=MayDecide(' in wall
        assert 'minTargets=1, maxTargets=1' in wall and re.search(r'legalTargets=\{0=\[[a-f0-9-]+\]\}, canCancel=false',wall)
    assert audit['errors']==[] and audit['diagnostic_material_complete'] and audit['exit_status']==1
    report['totals']=totals;report['pending_decisions']=decisions
    report['source_restoration']='Exact clean before/after maps; both original helper bytes independently fetched and exact reversible patches verified.'
    report['no_runtime_execution_by_review']=True
(HERE/'independent-checks.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'totals':totals,'members':87,'fresh_commands':3,'decision_records':63,'trace_entries':413,'checks_sha256':sha((HERE/'independent-checks.json').read_bytes())}))
