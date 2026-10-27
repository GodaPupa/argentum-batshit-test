"""Receive the original Assay continuation, reusing exactly bound R2 reports."""
from pathlib import Path
import json,zipfile,tempfile,hashlib,subprocess,os,gzip,base64
H=Path(__file__).resolve().parent;P=json.loads((H/'PROTOCOL.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
def receive():
 pins=json.loads((H/'PINS.json').read_text());m=json.loads((H/'metadata.json').read_text());run=m['run'];jobs=m['jobs']['jobs'];a=m['artifact']
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
 assert all(f['filename'].startswith('experiments/manual-transmission/research/') for f in m['runtime_delta']['files'])
 assert len(m['runtime_delta']['files'])==pins['runtime_delta_files']
 data=(H/'original.zip').read_bytes();assert len(data)==a['size_in_bytes'] and 'sha256:'+sha(data)==a['digest']
 with zipfile.ZipFile(H/'original.zip') as z:
  assert z.testzip() is None;names=[n for n in z.namelist() if not n.endswith('/')]
  assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names);raw={n:z.read(n) for n in names}
 manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'} and all(sha(raw[k])==v for k,v in manifest.items())
 b=json.loads(raw['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[k]==pins[k]
 assert b['run']==str(pins['run']) and b['attempt']==b['run_number']==1
 assert b['runtime_source']==P['runtime_source'] and b['runtime_tree']==P['runtime_tree']
 wf=raw['WORKFLOW.yml'];assert hashlib.sha1(b'blob '+str(len(wf)).encode()+b'\0'+wf).hexdigest()==pins['workflow_blob']
 for n,h in pins['protocol_files'].items():assert sha(raw['protocol/'+n])==h
 assert json.loads(raw['protocol/PROTOCOL.json'])==P
 for label in ['initial','final']:
  assert raw[label+'/TRACKED.diff']==b''
  for path,h in {**P['bindings'],**P['protected']}.items():assert sha(raw[label+'/source/'+path])==h,(label,path)
 for n,h in P['prior_receipt_bindings'].items():assert sha(raw['prior-receipt/'+n])==h,n
 archives=json.loads(raw['prior-receipt/ARCHIVES.json'])
 def unpack(name):
  x=archives[name];data=gzip.decompress(base64.b64decode(b''.join(raw['prior-receipt/'+n] for n in x['parts'])))
  assert len(data)==x['decoded_bytes'] and sha(data)==x['decoded_sha256'];return json.loads(data)
 original=unpack('VERIFIED_RECEIPT');ordinary=unpack('ORDINARY_SUPPLEMENT')
 assert original['source']==P['runtime_source'] and original['state']=='ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
 assert original['artifact']==P['prior_artifact']['id'] and original['artifact_sha256']==P['prior_artifact']['sha256'] and original['manifest_entries']==9585
 assert original['gates']['registration']==P['reuse_passed']['registration']
 for stage in ['recording','actor','focused','broad']:
  assert original['gates'][stage]['passed']==P['reuse_passed'][stage] and original['gates'][stage]['skips']==0
 assert original['gates']['client']==dict(passed=64,skips=0)
 assert ordinary['artifact_sha256']==original['artifact_sha256'] and ordinary['gate']['rows']==18918 and ordinary['gate']['passed']==18875 and ordinary['gate']['skips']==43
 assert len(json.loads(raw['prior-receipt/INVENTORY_CORRECTION.json'])['mapping'])==4
 errors=[];gates={}
 with tempfile.TemporaryDirectory() as td:
  root=Path(td)
  for n,body in raw.items():p=root/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(body)
  result=subprocess.run(['python3',str(root/'protocol/check_assay.py')],env=dict(os.environ,RECORD=str(root/'protocol'),EVIDENCE=str(root)),capture_output=True,text=True)
  if result.returncode:errors.append(dict(stage='assay',error=result.stdout+result.stderr))
  else:gates['assay']=json.loads((root/'assay/RECEIVING.json').read_text())
 if 'FAILURE.txt' in raw:errors.append(dict(stage='collector',error=raw['FAILURE.txt'].decode()))
 state='RECEIVED_FINAL_COMPATIBILITY_ONLY_SPELLSKITE_MULTIPLAYER_PENDING' if not errors and run['conclusion']==job['conclusion']=='success' else 'ORIGINAL_FAILURE_RECEIVED_NOT_ACCEPTED'
 receipt=dict(pins,state=state,artifact=a['id'],artifact_bytes=len(data),artifact_sha256=sha(data),manifest_entries=len(manifest),gates=gates,gate_errors=errors,reused=P['reuse_passed'],reused_source=P['runtime_source'],reused_receipt=P['prior_receipt'],original_failed_receipt_preserved=True,feature_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
 (H/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(dict(state=state,errors=errors)))
if __name__=='__main__':receive()
