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
    manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)-set(raw)==set(['source/.github/workflows/pest-control-tier-one-monster-tron-official-smoke.yml', 'source/.github/workflows/pest-control-tier-one-monster-tron-one-shot-construction.yml', 'source/.github/workflows/pest-control-tier-one-monster-tron-r1-official-smoke.yml']);assert not set(raw)-set(manifest)-{'MANIFEST.json'}
    assert all(sha(raw[k])==v for k,v in manifest.items() if k in raw)
    b=json.loads(raw['BINDING.json'])
    for key in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[key]==pins[key]
    assert b['run']==str(pins['run']) and b['attempt']==b['run_number']==1
    assert hashlib.sha1(b'blob '+str(len(raw['WORKFLOW.yml'])).encode()+b'\0'+raw['WORKFLOW.yml']).hexdigest()==pins['workflow_blob']
    for path,digest in pins['protocol_files'].items():assert sha(raw['protocol/'+path])==digest
    assert json.loads(raw['protocol/PROTOCOL.json'])==P
    for path,digest in {**P['bindings'],**P['protected']}.items():
        if 'source/'+path in raw:assert sha(raw['source/'+path])==digest,path
    assert not any(k.endswith('.xml') or k.endswith('vitest.json') for k in raw)
    passed={}
    with tempfile.TemporaryDirectory() as tmp:
        for name in ['compile-server-normal','compile-client-normal','compile-server-server-mutant']:
            folder=Path(tmp)/name;folder.mkdir()
            for k,v in raw.items():
                if k.startswith(name+'/'):
                    p=Path(tmp)/k;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(v)
            passed[name]=stage(folder)
    failed='compile-client-client-index'
    assert int(raw[failed+'/exit.txt'])!=0
    log=raw[failed+'/output.log'].decode()
    expected="src/store/slices/gameplaySlice.ts(293,38): error TS6133: 'optionIndex' is declared but its value is never read."
    assert expected in log and log.count('error TS')==1
    for name in list(passed)+[failed]:
        for path in P['bindings']:
            expected_bytes=raw['source/'+path]
            if name.endswith('server-mutant') and path.endswith('PendingDecisionRouting.kt'):
                expected_bytes=expected_bytes.replace(b'is ChooseOptionDecision -> copy(id = id)',b'is ChooseOptionDecision -> copy(id = id, optionCardIds = null)')
            if name.endswith('client-index') and path.endswith('gameplaySlice.ts'):
                text=expected_bytes.decode();start=text.index('submitOptionDecision: (decisionId, optionIndex) => {');end=text.index('submitReplacementDecision:',start)
                body=text[start:end];assert body.count('        optionIndex,')==1
                expected_bytes=(text[:start]+body.replace('        optionIndex,','        optionIndex: 0,')+text[end:]).encode()
            assert raw[name+'/source/'+path]==expected_bytes
    receipt=dict(state='ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED',source=pins['source'],validation=pins['validation'],run=pins['run'],job=pins['job'],attempt=1,artifact=artifact['id'],artifact_bytes=len(data),artifact_sha256=sha(data),manifest_entries=len(manifest),verified_present_entries=len(raw)-1,missing_manifest_entries=sorted(set(manifest)-set(raw)),passed_compilations=passed,compiler_diagnostic=expected,behavioral_tests=0,restoration='Collector finally restore present; no archived final restoration capture, not accepted',feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
    (ROOT/'FAILURE_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n')
    print(json.dumps(receipt,indent=2))
if __name__=='__main__':audit()
