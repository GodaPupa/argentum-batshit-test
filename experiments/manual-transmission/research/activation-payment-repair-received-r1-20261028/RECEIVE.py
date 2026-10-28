"""Receive original bounded payment repair; green is not acceptance."""
from pathlib import Path
import json,zipfile,tempfile,hashlib,subprocess,os,sys
H=Path(__file__).resolve().parent;P=json.loads((H/'PROTOCOL.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def receive():
    pins=json.loads((H/'PINS.json').read_text());m=json.loads((H/'metadata.json').read_text());run=m['run'];jobs=m['jobs']['jobs'];a=m['artifact']
    assert run['id']==pins['run'] and run['head_sha']==pins['validation'] and run['workflow_id']==pins['workflow_id'] and run['path']==pins['workflow']
    assert run['head_branch']==pins['branch'] and run['run_attempt']==run['run_number']==1 and run['event']=='push' and run['status']=='completed'
    assert len(jobs)==1;job=jobs[0]
    assert job['id']==pins['job'] and job['run_id']==pins['run'] and job['run_attempt']==1 and job['status']=='completed'
    assert a['name']==pins['artifact_name'] and not a['expired'] and a['workflow_run']['id']==pins['run'] and a['workflow_run']['head_sha']==pins['validation']
    for key,parent in [('source',P['base']),('validation',pins['source'])]:
        assert m[key]['sha']==pins[key] and m[key]['tree']['sha']==pins['tree' if key=='source' else 'validation_tree']
        assert [p['sha'] for p in m[key]['parents']]==[parent]
    assert m['main']['sha']==P['main']
    assert sorted(f['filename'] for f in m['source_delta']['files'])==sorted(P['source_delta'])
    assert [f['filename'] for f in m['validation_delta']['files']]==[pins['workflow']]
    data=(H/'original.zip').read_bytes()
    assert len(data)==a['size_in_bytes'] and 'sha256:'+sha(data)==a['digest']
    with zipfile.ZipFile(H/'original.zip') as z:
        assert z.testzip() is None;names=[n for n in z.namelist() if not n.endswith('/')]
        assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
        raw={n:z.read(n) for n in names}
    manifest=json.loads(raw['MANIFEST.json'])
    assert set(manifest)==set(raw)-{'MANIFEST.json'} and all(sha(raw[k])==v for k,v in manifest.items())
    b=json.loads(raw['BINDING.json'])
    for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[k]==pins[k]
    assert b['run']==str(pins['run']) and b['attempt']==b['run_number']==1
    assert blob(raw['WORKFLOW.yml'])==pins['workflow_blob']
    for n,h in pins['protocol_files'].items():assert sha(raw['protocol/'+n])==h
    assert json.loads(raw['protocol/PROTOCOL.json'])==P
    for label in ['initial','pre-behavior','final']:
        assert raw[label+'/TRACKED.diff']==b''
        for path,h in {**P['bindings'],**P['protected']}.items():assert sha(raw[label+'/source/'+path])==h,(label,path)
        for path,h in P['blob_bindings'].items():assert blob(raw[label+'/source/'+path])==h,(label,path)
    normal=raw['initial/source/'+P['handler']]
    forms={'normal':normal}
    for name,m in P['mutants'].items():
        body=normal
        for c in m['changes']:
            assert body.count(c['old'].encode())==c['count']
            body=body.replace(c['old'].encode(),c['new'].encode())
        assert sha(body)==m['sha256'];forms[name]=body
    errors=[];gates={}
    with tempfile.TemporaryDirectory() as td:
        root=Path(td)
        for n,body in raw.items():p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(body)
        sys.path.insert(0,str(root/'protocol'))
        from reports import stage
        for name in P['stages']:
            try:
                variant=name.removeprefix('compile-') if name.startswith('compile-') else name
                assert raw[name+'/FORM.kt']==forms.get(variant,normal)
                gates[name]=stage(root/name)
            except Exception as e:errors.append(dict(stage=name,error=type(e).__name__+': '+str(e)))
    try:
        timing={name:json.loads(raw[name+'/TIMING.json']) for name in P['stages']}
        assert all(v['started_ns']<v['ended_ns'] for v in timing.values())
        assert max(timing[n]['ended_ns'] for n in P['stages'] if n.startswith('compile-')) < min(timing[n]['started_ns'] for n in P['stages'] if not n.startswith('compile-'))
        assert all(timing[a]['ended_ns']<=timing[b]['started_ns'] for a,b in zip(P['stages'],P['stages'][1:]))
    except Exception as e:errors.append(dict(stage='order',error=type(e).__name__+': '+str(e)))
    if 'FAILURE.txt' in raw:errors.append(dict(stage='collector',error=raw['FAILURE.txt'].decode()))
    state='RECEIVED_EARLY_PAYMENT_REPAIR_FULL_COMPATIBILITY_PENDING' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
    receipt=dict(pins,state=state,artifact=a['id'],artifact_bytes=len(data),artifact_sha256=sha(data),manifest_entries=len(manifest),gates=gates,gate_errors=errors,feature_acceptance=False,full_compatibility=False,card_registration=False,official_games=0,official_seeds=0,precision_reserved=2)
    (H/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(dict(state=state,errors=errors)))
if __name__=='__main__':receive()
