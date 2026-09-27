"""Read-only audit of the sole six-case diagnostic artifact; no engine execution."""
import base64, hashlib, json, pathlib, re, zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[3]
HERE = pathlib.Path(__file__).resolve().parent
ZIP = ROOT / 'attachments/731e4675-260a-4563-8338-94a4dc6b1452/izzet-six-case-diagnostic-original-10919123143.zip'
if (HERE / 'original-artifact.zip').exists(): ZIP = HERE / 'original-artifact.zip'
sha = lambda raw: hashlib.sha256(raw).hexdigest()
blob = lambda raw: hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()

def balanced(text, marker):
    start = text.index(marker)
    depth = 0
    for i in range(start, len(text)):
        if text[i] == '(': depth += 1
        if text[i] == ')':
            depth -= 1
            if depth == 0: return text[start:i+1]
    raise AssertionError('Unbalanced diagnostic representation')

def audit():
    raw = ZIP.read_bytes()
    assert len(raw) == 2687709 and sha(raw) == '8bfcb2881786acbc6c93ef0bb8e639baa9c86a37f187189977eea11b24c90a6b'
    report = {'schema':'izzet-six-case-original-author-audit-v1','artifact_id':10919123143,'run':36282117960,'attempt':1,'archive_bytes':len(raw),'archive_sha256':sha(raw),'members':{},'stages':[]}
    with zipfile.ZipFile(ZIP) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == 87
        for n in z.namelist():
            b=z.read(n); report['members'][n]={'bytes':len(b),'sha256':sha(b)}
        gate=json.loads(z.read('control/scope.json')); collector=json.loads(z.read('audit.json'))
        report['source']={k:gate[k] for k in ['source_head','source_tree']}
        report['control']={k:collector[k] for k in ['control_head','control_tree']}
        for n,digest in {'control/run.py':'91d913cd8eb0ab7c8a7b9104f1bcfee0bb5958fa875937272bf63fc67cc49d94','control/scope.json':'710ae4da7aa8e110c08aaa5bc16df636b53000791525cd6fb791e79b03dea3aa','control/.github/workflows/izzet-receiving-failure-diagnostic.yml':'456e03ae92a32ae0f2a23a94679c5d0b983de8335442a8c3e0609abb7c16447c'}.items(): assert sha(z.read(n))==digest
        before=json.loads(z.read('source-before.json')); after=json.loads(z.read('source-restored.json')); instrumented=json.loads(z.read('instrumented-source.json'))
        assert before==after and before['status']=='' and before['head']==gate['source_head'] and before['tree']==gate['source_tree']
        for row in gate['instrumentation']:
            a=z.read('instrumentation/'+row['path']+'.original'); b=z.read('instrumentation/'+row['path'])
            assert sha(a)==row['original_sha256'] and blob(a)==row['original_git_blob'] and sha(b)==row['instrumented_sha256']
            assert a.decode().count(row['replacement_old'])==1
            assert a.decode().replace(row['replacement_old'],row['replacement_new']).encode()==b
            assert instrumented['file_sha256'][row['path']]==sha(b)
        live=json.loads((HERE/'final-audit-live-tests.json').read_text()); test_sources={}
        for r in live:
            assert r['status']=='fulfilled'; v=r['value']; raw_source=v['result']['structuredContent']['content'].encode(); test_sources[v['path']]=raw_source
        for row in gate['unchanged_tests']:
            b=test_sources[row['path']]; assert sha(b)==row['sha256'] and blob(b)==row['git_blob']==next(x['test_source_git_blob'] for x in gate['existing_class_bank'] if x['test_source']==row['path'])
            assert before['file_sha256'][row['path']]==instrumented['file_sha256'][row['path']]==sha(b)
        for p,d in gate['preserved_authority_sha256'].items(): assert before['file_sha256'][p]==instrumented['file_sha256'][p]==d
        report['restoration']='EXACT_CLEAN_SOURCE_BEFORE_EQUALS_AFTER'; report['unchanged_test_sources']=[{'path':p,'sha256':sha(b),'git_blob':blob(b)} for p,b in test_sources.items()]
        for i,bank in enumerate(gate['existing_class_bank'],1):
            folder=f'{i:02d}-historical-regression-{bank["class"].rsplit(".",1)[-1]}'
            xmlname=folder+'/TEST-'+bank['class']+'.xml'; root=ET.fromstring(z.read(xmlname)); cases=root.findall('testcase')
            assert root.attrib['name']==bank['class'] and len(cases)==bank['expected_cases']==int(root.attrib['tests'])
            assert sorted(c.attrib['name'] for c in cases)==sorted(bank['case_names'])
            counts={k:sum(c.find(k) is not None for c in cases) for k in ['failure','error','skipped']}
            assert all(counts[k]==int(root.attrib[k+'s' if k!='skipped' else k]) for k in counts)
            assert counts['error']==counts['skipped']==0
            log=z.read(folder+'/command.log').decode(); command=json.loads(z.read(folder+'/command.json')); stage=collector['stages'][i-1]
            assert z.read(folder+'/exit-status.txt').strip()==b'1' and stage['exit_status']==1 and stage['technical_limit'] is None and stage['output_complete'] is True and stage['discarded_observed_pipe_bytes']==0
            assert len(log.encode())==stage['stdout_retained_bytes'] and 'BUILD FAILED' in log
            assert len(re.findall(r'Gradle Test Executor \d+ started executing tests\.',log))==len(re.findall(r'Gradle Test Executor \d+ finished executing tests\.',log))==1
            assert all(flag in stage['command'] for flag in ['--rerun','--no-build-cache','--no-daemon','-DupdateSnapshots=false'])
            assert f'> Task :{bank["module"].replace("/",":")}:test' in log
            traces=[]; records=[]
            for node in root.iter('system-out'):
                for line in (node.text or '').splitlines():
                    if line.startswith('IZZET_DIAGNOSTIC_TRACE '): traces.append(base64.b64decode(line.split(' ',1)[1],validate=True))
                    if line.startswith('IZZET_DECISION_DIAGNOSTIC '): records.append(base64.b64decode(line.split(' ',1)[1],validate=True))
            row={'class':bank['class'],'cases':[{'name':c.attrib['name'],'result':'FAIL' if c.find('failure') is not None else 'PASS','failure_message':c.find('failure').attrib.get('message') if c.find('failure') is not None else None} for c in cases],'counts':counts,'fresh_executor_pairs':1,'exit':1,'stdout_bytes':len(log.encode()),'xml_bytes':len(z.read(xmlname)),'xml_sha256':sha(z.read(xmlname)),'elapsed_seconds':stage['elapsed_seconds'],'technical_limit':None}
            if traces:
                stream=b''.join(traces); assert len(traces)==413 and len(stream)==26613 and not records
                assert stream==z.read(folder+'/baseline-hash-input.bin')
                assert [int(re.match(rb'A([0-9]+)\|',t).group(1)) for t in traces[:-1]]==list(range(1,413))
                history=HERE if (HERE/'accepted-base-stream.txt').exists() else ROOT/'izzet-takeover/diagnostic-evidence-publish/izzet-science/evidence/c47e-diagnostic-36271994873'
                old=(history/'candidate-stream.txt').read_bytes()
                base=(history/'accepted-base-stream.txt').read_bytes()
                assert stream==old
                lines=base.splitlines(keepends=True); changes=[j for j,(a,b) in enumerate(zip(lines,traces)) if a!=b]; assert len(lines)==len(traces)==413 and len(changes)==14
                pairs=[]
                for j,k in zip(changes[::2],changes[1::2]):
                    assert k==j+1 and b'DeclareBlockers' in traces[j-1]
                    assert lines[j].split(b'|',1)[1]==traces[k].split(b'|',1)[1] and lines[k].split(b'|',1)[1]==traces[j].split(b'|',1)[1]
                    assert all(b'PassPriority' in x for x in [lines[j],lines[k],traces[j],traces[k]])
                    pairs.append({'entries':[j+1,k+1],'preceding':traces[j-1].decode().strip(),'base':[lines[j].decode().strip(),lines[k].decode().strip()],'current':[traces[j].decode().strip(),traces[k].decode().strip()]})
                row['baseline']={'entries':413,'bytes':26613,'sha256':sha(stream),'equals_preserved_c47_bytes':True,'accepted_base_sha256':sha(base),'changed_entries':14,'pairs':pairs,'end':traces[-1].decode()}
            else:
                assert len(records)==(45 if i==2 else 18)
                row['decoded_record_count']=len(records); row['pending_records']=[]
                context=None; observed=[]
                for line in log.splitlines():
                    if ' STANDARD_OUT' in line and ' > ' in line: context=line.split(' > ',1)[1].removesuffix(' STANDARD_OUT')
                    marker='IZZET_DECISION_DIAGNOSTIC '
                    if marker in line:
                        b=base64.b64decode(line.split(marker,1)[1].strip(),validate=True); observed.append((context,b))
                assert [b for _,b in observed]==records
                for j,b in enumerate(records,1):
                    assert b==z.read(folder+f'/action-{j:03d}.txt')
                    text=b.decode(); lines=text.splitlines(); pending=lines[3].split('=',1)[1]
                    if pending!='null':
                        marker=pending.rsplit('.',1)[-1]+'('; stored=text.split('\nstoredAfter=',1)[1]; question=balanced(stored,marker)
                        assert 'success=false paused=true error=null' in text and 'beforePendingType=null' in text
                        row['pending_records'].append({'record':j,'case':observed[j-1][0],'member':folder+f'/action-{j:03d}.txt','sha256':sha(b),'action':lines[0],'result':lines[1],'actual_pending_type':pending,'question':question,'continuation_type':'TriggerOrderingContinuation' if 'answer=TriggerOrderingContinuation(' in stored else 'TriggeredAbilityContinuation'})
            report['stages'].append(row)
        assert collector['diagnostic_material_complete'] is True and collector['errors']==[]
        report['total']={'cases':6,'pass':2,'failure':4,'error':0,'skipped':0,'commands':3,'command_exit_codes':[1,1,1],'new_cases':0}
        report['resource_integrity']={'uncompressed_member_bytes':sum(r['bytes'] for r in report['members'].values()),'collector_errors':[],'complete':True,'raw_xml_and_stdout_untruncated':True}
        report['limits']=['Source-author preservation audit, not independent admission.','Original assertions remain failures; no source, golden, policy, deck or seed changes.','Observed pending question contents establish where these fixtures stop; subsequent resolution is not executed by this audit.','Equal complete streams concern one fixed software fixture, not general gameplay or policy equivalence.','No official experimental allocations or exposed outcomes.']
    return report

if __name__=='__main__':
    result=audit(); (HERE/'original-author-audit.json').write_text(json.dumps(result,indent=2)+'\n'); print(json.dumps(result['total']))
