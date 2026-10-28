"""Original archive receiving against externally pinned immutable metadata."""
from pathlib import Path
import json,zipfile,tempfile,subprocess,os,hashlib
from audit import P,sha,inventory,junit,client
HERE=Path(__file__).resolve().parent

def receive():
 pins=json.loads((HERE/'PINS.json').read_text());m=json.loads((HERE/'metadata.json').read_text());run=m['run'];jobs=m['jobs']['jobs'];a=m['artifact']
 assert run['id']==pins['run'] and run['head_sha']==pins['validation'] and run['workflow_id']==pins['workflow_id'] and run['path']==pins['workflow']
 assert run['head_branch']==pins['branch'] and run['run_attempt']==run['run_number']==1 and run['event']=='push' and run['status']=='completed'
 assert len(jobs)==1;job=jobs[0];assert job['id']==pins['job'] and job['run_id']==pins['run'] and job['run_attempt']==1 and job['status']=='completed'
 assert a['name']==pins['artifact_name'] and not a['expired'] and a['workflow_run']['id']==pins['run'] and a['workflow_run']['head_sha']==pins['validation']
 for key,parent in [('source',P['base']),('validation',pins['source'])]:
  assert m[key]['sha']==pins[key] and m[key]['tree']['sha']==pins['tree' if key=='source' else 'validation_tree']
  assert [p['sha'] for p in m[key]['parents']]==[parent]
 assert m['main']['sha']==P['main']
 assert sorted(f['filename'] for f in m['source_delta']['files'])==sorted(P['source_delta'])
 assert [f['filename'] for f in m['validation_delta']['files']]==[pins['workflow']]
 data=(HERE/'original.zip').read_bytes();assert len(data)==a['size_in_bytes'] and 'sha256:'+sha(data)==a['digest']
 with zipfile.ZipFile(HERE/'original.zip') as z:
  assert z.testzip() is None;names=[n for n in z.namelist() if not n.endswith('/')]
  assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names);raw={n:z.read(n) for n in names}
 manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'} and all(sha(raw[k])==v for k,v in manifest.items())
 b=json.loads(raw['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[k]==pins[k]
 assert b['run']==str(pins['run']) and b['attempt']==b['run_number']==1
 wf=raw['WORKFLOW.yml'];assert hashlib.sha1(b'blob '+str(len(wf)).encode()+b'\0'+wf).hexdigest()==pins['workflow_blob']
 for n,h in pins['protocol_files'].items():assert sha(raw['protocol/'+n])==h
 assert json.loads(raw['protocol/PROTOCOL.json'])==P
 errors=[];gates={}
 for label in ['initial','final']:
  assert sha(raw[label+'/EARLY_RECEIPT.json'])==P['early_receipt_sha256']
  assert json.loads(raw[label+'/EARLY_RECEIPT.json'])['state']=='ORIGINAL_BEHAVIORAL_GATES_RECEIVED_DEPENDENT_ADMISSION_PENDING'
  assert raw[label+'/TRACKED.diff']==b''
  for p,h in {**P['bindings'],**P['protected']}.items():assert sha(raw[label+'/source/'+p])==h,(label,p)
 with tempfile.TemporaryDirectory() as td:
  root=Path(td)
  for n,body in raw.items():p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(body)
  I=inventory(root)
  for stage in P['stages']:
   try:gates[stage]=junit(root/stage,I[stage])
   except Exception as e:errors.append(dict(stage=stage,error=type(e).__name__+': '+str(e)))
  try:gates['client']=client(root/'client',json.loads((root/P['inventory']/'CLIENT64_CONTRACT.json').read_text()))
  except Exception as e:errors.append(dict(stage='client',error=str(e)))
 if 'FAILURE.txt' in raw:errors.append(dict(stage='collector',error=raw['FAILURE.txt'].decode()))
 state='RECEIVED_SPELLSKITE_TEST_SOURCE_COMPATIBILITY_CARD_MULTIPLAYER_PENDING' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
 receipt=dict(pins, state=state,artifact=a['id'],artifact_bytes=len(data),artifact_sha256=sha(data),manifest_entries=len(manifest),gates=gates,gate_errors=errors,feature_acceptance=False,early_and_assay_reuse=P['reuse'],card_registration=False,official_games=0,official_seeds=0,precision_reserved=2)
 (HERE/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(dict(state=state,errors=errors)))
if __name__=='__main__':receive()
