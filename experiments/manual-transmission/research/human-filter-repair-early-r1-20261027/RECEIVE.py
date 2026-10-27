"""Receive one original archive against externally pinned metadata, not workflow color."""
import hashlib,json,tempfile,zipfile
from pathlib import Path
from reports import P,stage
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
    assert b['run']==str(pins['run']) and b['attempt']==b['run_number']==1
    assert hashlib.sha1(b'blob '+str(len(raw['WORKFLOW.yml'])).encode()+b'\0'+raw['WORKFLOW.yml']).hexdigest()==pins['workflow_blob']
    for path,digest in pins['protocol_files'].items():assert sha(raw['protocol/'+path])==digest
    assert json.loads(raw['protocol/PROTOCOL.json'])==P
    for path,digest in {**P['bindings'],**P['protected']}.items():assert sha(raw['source/'+path])==digest,path
    assert b['base']==P['base'] and sorted(b['source_delta'])==sorted(P['source_delta'])
    stages=['compile-'+v for v in ['normal',*P['mutations']]]+['normal',*P['mutations'],'restored']
    gates={};errors=[]
    with tempfile.TemporaryDirectory() as tmp:
        for name in stages:
            try:
                folder=Path(tmp)/name;folder.mkdir()
                for k,v in raw.items():
                    if k.startswith(name+'/'):
                        path=Path(tmp)/k;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(v)
                gates[name]=stage(folder)
                variant=name.removeprefix('compile-')
                for path in P['bindings']:
                    expected=raw['source/'+path]
                    mutation=P['mutations'].get(variant)
                    if mutation and path==mutation['path']:
                        for r in mutation['replacements']:
                            assert expected.count(r['anchor'].encode())==1
                            expected=expected.replace(r['anchor'].encode(),r['replacement'].encode())
                    assert raw[name+'/source/'+path]==expected,(name,path)
                if variant not in P['mutations']:assert raw[name+'/SOURCE.diff']==b''
            except Exception as e:errors.append(dict(stage=name,error=type(e).__name__+': '+str(e)))
    try:
        restoration=json.loads(raw['RESTORATION.json'])
        assert restoration==dict(restored=True,bindings=P['bindings'],protected=P['protected'])
        assert raw['final-restored/SOURCE.diff']==b''
        for path,digest in P['bindings'].items():assert sha(raw['final-restored/source/'+path])==digest
    except Exception as e:errors.append(dict(stage='restoration',error=type(e).__name__+': '+str(e)))
    if 'FAILURE.txt' in raw:errors.append(dict(stage='collector',error=raw['FAILURE.txt'].decode()))
    projections={}
    try:
        for code,blob in P['text_golden_blobs'].items():
            original=raw['original-goldens/'+code+'.txt']
            assert hashlib.sha1(b'blob '+str(len(original)).encode()+b'\0'+original).hexdigest()==blob
            normal=raw['normal/projections/'+code+'.txt'];restored=raw['restored/projections/'+code+'.txt']
            assert normal==restored
            projections[code]=dict(original_sha256=sha(original),projected_sha256=sha(normal),changed=normal!=original)
        for name in ['normal',*P['mutations'],'restored']:
            assert sorted(k.split('/')[-1].removesuffix('.txt') for k in raw if k.startswith(name+'/projections/'))==P['projection_sets']
    except Exception as e:errors.append(dict(stage='projections',error=type(e).__name__+': '+str(e)))
    receipt=dict(projections=projections,**pins,artifact=artifact['id'],artifact_bytes=len((ROOT/'original.zip').read_bytes()),artifact_sha256=sha((ROOT/'original.zip').read_bytes()),manifest_entries=len(manifest),gates=gates,gate_errors=errors,feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    receipt['state']='RECEIVED_EARLY_HUMAN_FILTER_REPAIR_ONLY_FULL_COMPATIBILITY_PENDING' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
    (ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps({k:receipt[k] for k in ['state','artifact','manifest_entries','gate_errors']}))
if __name__=='__main__':audit()
