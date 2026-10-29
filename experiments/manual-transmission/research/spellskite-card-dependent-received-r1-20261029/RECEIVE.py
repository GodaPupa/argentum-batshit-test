"""Receive the original artifact, not its green workflow label."""
from pathlib import Path
import json,zipfile,tempfile,hashlib
from audit import P,sha,inventory,junit
from snapshot_check import compile_gate,snapshot_insertion
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
  assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names)
  raw={n:z.read(n) for n in names}
 manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'} and all(sha(raw[k])==v for k,v in manifest.items())
 b=json.loads(raw['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[k]==pins[k]
 assert b['run']==str(pins['run']) and b['attempt']==b['run_number']==1
 wf=raw['WORKFLOW.yml'];assert hashlib.sha1(b'blob '+str(len(wf)).encode()+b'\0'+wf).hexdigest()==pins['workflow_blob']
 for n,h in pins['protocol_files'].items():assert sha(raw['protocol/'+n])==h
 assert json.loads(raw['protocol/PROTOCOL.json'])==P
 for label in ['initial','final']:
  assert raw[label+'/TRACKED.diff']==b''
  for path,h in {**P['protected'],**P['bindings'],**P['new_bindings']}.items():assert sha(raw[label+'/source/'+path])==h,(label,path)
 for name,mutation in P['mutants'].items():
  k='forms/'+name+'/'+mutation['path']
  if k in raw:
   assert sha(raw[k])==mutation['sha256']
   assert raw[k]==raw['initial/source/'+mutation['path']].replace(mutation['old'].encode(),mutation['new'].encode())
 assert sha(raw['EARLY_RECEIPT.json'])==P['early_receipt_sha256']
 errors=[];gates={}
 with tempfile.TemporaryDirectory() as td:
  root=Path(td)
  for name,body in raw.items():f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(body)
  I=inventory(Path(__file__).resolve().parents[4]) if False else None
  # Inventories are derived from immutable accepted source inputs copied into the archive.
  I=inventory(root)
  calls=[('compile-normal',lambda:compile_gate(root/'compile-normal'))]
  calls += [(n,lambda n=n:junit(root/n,I[n])) for n in P['stages']]
  for name,fn in calls:
   try:gates[name]=fn()
   except Exception as e:errors.append({'stage':name,'error':type(e).__name__+': '+str(e)})
  try:
   assert sha(raw['protocol/NPH.original.json'])==P['original_nph_sha256']
   gates['insertion']=snapshot_insertion(raw['protocol/NPH.original.json'],raw['final/source/mtg-sets/src/test/resources/snapshots/cards/NPH.json'])
   assert gates['insertion']==json.loads(raw['INSERTION.json'])
   assert sha(raw['final/source/mtg-sets/src/test/resources/snapshots/cards/CLB.json'])==pins['original_clb_sha256']
  except Exception as e:errors.append({'stage':'snapshot candidate','error':type(e).__name__+': '+str(e)})
 if 'FAILURE.txt' in raw:errors.append({'stage':'collector','error':raw['FAILURE.txt'].decode()})
 state='ORIGINAL_DEPENDENT_REGRESSIONS_RECEIVED_METADATA_ASSAY_ADMISSION_PENDING' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
 receipt=dict(pins,state=state,artifact=a['id'],artifact_bytes=len(data),artifact_sha256=sha(data),manifest_entries=len(manifest),gates=gates,gate_errors=errors,protected_bindings=len(P['protected']),card_admission=False,multiplayer_admission=False,official_games=0,official_seeds=0,precision_reserved=2)
 (HERE/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'state':state,'errors':errors}))
if __name__=='__main__':receive()
