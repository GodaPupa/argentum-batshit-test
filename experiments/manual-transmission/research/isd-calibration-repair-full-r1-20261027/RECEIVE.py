"""Receive one original archive against externally pinned metadata, not workflow color."""
import hashlib,json,tempfile,zipfile
from pathlib import Path
from reports import P,stage
from receive_calibrated import receive
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
        for name in ['full-module','por','isd']:
            try:
                folder=Path(tmp)/name
                gates[name]=stage(folder) if name=='full-module' else receive(folder,name)
                if name!='full-module':
                    assert raw[name+'/'+name.upper()+'.golden.json']==raw['source/mtg-sets/src/test/resources/snapshots/cards/'+name.upper()+'.json']
            except Exception as e:errors.append(dict(stage=name,error=type(e).__name__+': '+str(e)))
    try:
        for path,blob in P['golden_blobs'].items():
            data=raw['source/'+path]
            assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==blob
        assert sha(raw['INHERITED_R1_RECEIPT.json'])==P['inherited_early']['receipt_sha256']
        assert all(P['bindings'][k]==v for k,v in P['inherited_early']['required_unchanged_bindings'].items())
        inherited=json.loads(raw['INHERITED_R1_RECEIPT.json'])
        assert inherited['state']==P['inherited_early']['original_state'] and inherited['source']==P['inherited_early']['original_source']
        assert inherited['run']==P['inherited_early']['run'] and inherited['artifact_sha256']==P['inherited_early']['artifact_sha256']
        assert set(inherited['gates'])==set(P['inherited_early']['stages'])
        gates['inherited_early']=dict(receipt=P['inherited_early']['receipt_commit'],stages=P['inherited_early']['stages'],bindings_unchanged=True,original_failure_preserved=True)
    except Exception as e:errors.append(dict(stage='inherited_early',error=type(e).__name__+': '+str(e)))
    try:
        repair_bytes=raw['INHERITED_REPAIR_RECEIPT.json']
        assert sha(repair_bytes)==P['inherited_repair']['receipt_sha256']
        repair=json.loads(repair_bytes)
        assert repair['state']=='RECEIVED_EARLY_ISD_REPAIR_ONLY_FULL_COMPATIBILITY_PENDING' and not repair['gate_errors']
        for key in ['source','run','artifact','artifact_sha256']:assert repair[key]==P['inherited_repair'][key]
        assert set(repair['gates'])==set(P['inherited_repair']['stages'])
        assert all(P['bindings'][k]==v for k,v in P['inherited_repair']['required_unchanged_bindings'].items())
        for path,blob in P['text_golden_blobs'].items():
            data=raw['source/'+path]
            assert hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==blob
            assert sha(data)==P['text_golden_hashes'][path]
            assert raw['full-module/projections/'+Path(path).name.split('.')[0].upper()+'.txt']==data
        gates['inherited_repair']=dict(receipt=P['inherited_repair']['receipt_commit'],stages=P['inherited_repair']['stages'],bindings_unchanged=True)
    except Exception as e:errors.append(dict(stage='inherited_repair_and_text_snapshots',error=type(e).__name__+': '+str(e)))
    try:
        I=json.loads(raw['protocol/INPUTS.json'])
        assert sha(raw['inputs/RAW_RESPONSES.json.gz'])==I['raw_pack_sha256']
        import gzip
        originals=json.loads(gzip.decompress(raw['inputs/RAW_RESPONSES.json.gz']))
        assert set(originals)==set(I['raw'])
        for name,body in originals.items():assert sha(body.encode())==I['raw'][name]['sha256']
        for code in ['por','isd']:assert sha(raw['inputs/'+code+'.json'])==I['sets'][code]['sha256']
        assert sha(raw['inputs/mtgish.lines.json'])==P['corpus']['sha256'] and len(raw['inputs/mtgish.lines.json'])==P['corpus']['bytes']
        assert raw['inputs/corpus-download-exit.txt']==b'0'
        assert json.loads(raw['FINAL_INPUTS.json'])==dict(corpus=P['corpus']['sha256'],caches={code:I['sets'][code]['sha256'] for code in ['por','isd']})
        assert json.loads(raw['FINAL_SOURCE_BINDING.json'])==dict(unchanged=True,bindings=P['bindings'],protected=P['protected'])
        assert int(raw['install/install-exit.txt'])==0 and b'BUILD SUCCESSFUL' in raw['install/install.log']
    except Exception as e:errors.append(dict(stage='inputs_and_final_binding',error=type(e).__name__+': '+str(e)))
    for file in ['FAILURE.txt','FINAL_SOURCE_FAILURE.txt']:
        if file in raw:errors.append(dict(stage=file,error=raw[file].decode()))
    receipt=dict(**pins,artifact=artifact['id'],artifact_bytes=len((ROOT/'original.zip').read_bytes()),artifact_sha256=sha((ROOT/'original.zip').read_bytes()),manifest_entries=len(manifest),gates=gates,gate_errors=errors,feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    receipt['state']='RECEIVED_BOUNDED_GENERATOR_AND_CALIBRATION_NOT_CARD_ADMISSION' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
    (ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps({k:receipt[k] for k in ['state','artifact','manifest_entries','gate_errors']}))
if __name__=='__main__':audit()
