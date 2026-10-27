"""Receive one original archive against externally pinned metadata, not workflow color."""
import hashlib,json,tempfile,zipfile
from pathlib import Path
from audit import P,audit as receive_browser
ROOT=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
def audit():
    pins=json.loads((ROOT/'PINS.json').read_text());m=json.loads((ROOT/'metadata.json').read_text())
    run=m['run'];jobs=m['jobs']['jobs'];artifact=m['artifact']
    assert run['id']==pins['run'] and run['head_sha']==pins['validation']
    assert run['workflow_id']==pins['workflow_id'] and run['path']==pins['workflow']
    assert run['run_attempt']==run['run_number']==1 and run['status']=='completed' and run['event']=='push'
    assert run['head_branch']==pins['branch'] and len(jobs)==1
    job=jobs[0];assert job['id']==pins['job'] and job['run_id']==pins['run'] and job['run_attempt']==1 and job['status']=='completed'
    assert artifact['name']==pins['artifact_name'] and not artifact['expired']
    assert artifact['workflow_run']['id']==pins['run'] and artifact['workflow_run']['head_sha']==pins['validation']
    for key,parent in [('source',P['base']),('validation',pins['source'])]:
        obj=m[key];assert obj['sha']==pins[key] and obj['tree']['sha']==pins['tree' if key=='source' else 'validation_tree']
        assert [p['sha'] for p in obj['parents']]==[parent]
    assert m['main']['sha']==P['main']
    assert {f['filename'] for f in m['source_delta']['files']}==set(pins['source_delta'])
    assert [f['filename'] for f in m['validation_delta']['files']]==[pins['workflow']]
    data=(ROOT/'original.zip').read_bytes()
    assert len(data)==artifact['size_in_bytes'] and 'sha256:'+sha(data)==artifact['digest']
    with zipfile.ZipFile(ROOT/'original.zip') as z:
        assert z.testzip() is None
        names=[n for n in z.namelist() if not n.endswith('/')]
        assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
        raw={n:z.read(n) for n in names}
    manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'}
    assert all(sha(raw[k])==v for k,v in manifest.items())
    b=json.loads(raw['BINDING.json'])
    for key in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[key]==pins[key]
    assert b['run']==str(pins['run']) and b['attempt']==1
    assert hashlib.sha1(b'blob '+str(len(raw['WORKFLOW.yml'])).encode()+b'\0'+raw['WORKFLOW.yml']).hexdigest()==pins['workflow_blob']
    for path,digest in pins['protocol_files'].items():assert sha(raw['protocol/'+path])==digest
    assert json.loads(raw['protocol/PROTOCOL.json'])==P
    for path,digest in {**P['bindings'],**P['protected']}.items():assert sha(raw['source/'+path])==digest,path
    assert b['base']==P['base'] and sorted(b['source_delta'])==sorted(P['source_delta'])
    gates={};errors=[]
    with tempfile.TemporaryDirectory() as tmp:
        for k,v in raw.items():
            path=Path(tmp)/k;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(v)
        try:gates['actual_ui']=receive_browser(Path(tmp))
        except Exception as e:errors.append(dict(stage='actual_ui',error=type(e).__name__+': '+str(e)))
    try:
        for mutant in P['mutations']:
            original=raw['source/'+mutant['path']].decode()
            assert original.count(mutant['old'])==1
            expected=original.replace(mutant['old'],mutant['new']).encode()
            assert raw['mutants/'+mutant['name']+'/'+mutant['path']]==expected
        assert json.loads(raw['FINAL_SOURCE_BINDING.json'])==dict(unchanged=True,bindings=P['bindings'],protected=P['protected'])
        assert json.loads(raw['RESTORATION.json'])=={'restored':True}
        gates['source_forms_restoration']=True
    except Exception as e:errors.append(dict(stage='source_forms_restoration',error=type(e).__name__+': '+str(e)))
    for file in ['FAILURE.txt','RESTORATION_FAILURE.json']:
        if file in raw:errors.append(dict(stage=file,error=raw[file].decode()))
    receipt=dict(**pins,artifact=artifact['id'],artifact_bytes=len(data),artifact_sha256=sha(data),manifest_entries=len(manifest),gates=gates,gate_errors=errors,feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    receipt['state']='RECEIVED_ACTUAL_UI_ONLY_PUBLIC_MATRIX_AND_FINAL_COMPATIBILITY_PENDING' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
    (ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps({k:receipt[k] for k in ['state','artifact','manifest_entries','gate_errors']}))
if __name__=='__main__':audit()
