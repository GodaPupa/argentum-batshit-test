from pathlib import Path
import hashlib,json,zipfile
from audit import audit
H=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def receive():
 p=json.loads((H/'PINS.json').read_text());m=json.loads((H/'metadata.json').read_text());P=json.loads((H/'PROTOCOL.json').read_text());r=m['run'];a=m['artifact'];jobs=m['jobs']['jobs']
 for k,pk in [('id','run'),('head_sha','validation'),('head_branch','branch'),('path','workflow'),('workflow_id','workflow_id')]:assert r[k]==p[pk]
 assert r['run_attempt']==r['run_number']==1 and r['event']=='push' and r['status']=='completed'
 assert len(jobs)==1 and jobs[0]['id']==p['job'] and jobs[0]['run_id']==r['id'] and jobs[0]['run_attempt']==1 and jobs[0]['status']=='completed'
 assert a['name']==p['artifact_name'] and not a['expired'] and a['workflow_run']['head_sha']==p['validation'] and a['workflow_run']['id']==r['id']
 for k,parent in [('source',P['base']),('validation',p['source'])]:assert m[k]['sha']==p[k] and m[k]['tree']['sha']==p['tree' if k=='source' else 'validation_tree'] and [x['sha'] for x in m[k]['parents']]==[parent]
 assert m['main']['sha']==P['main']
 assert sorted(f['filename'] for f in m['source_delta']['files'])==sorted(P['source_delta']) and [f['filename'] for f in m['validation_delta']['files']]==[p['workflow']]
 b=(H/'original.zip').read_bytes();assert len(b)==a['size_in_bytes'] and a['digest']=='sha256:'+sha(b)
 with zipfile.ZipFile(H/'original.zip') as z:
  assert z.testzip() is None;names=[n for n in z.namelist() if not n.endswith('/')];assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names);raw={n:z.read(n) for n in names}
 manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'} and all(sha(raw[n])==h for n,h in manifest.items())
 binding=json.loads(raw['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert binding[k]==p[k]
 assert binding['run']==str(r['id']) and binding['attempt']==binding['run_number']==1 and blob(raw['WORKFLOW.yml'])==p['workflow_blob']
 for n,h in p['protocol_files'].items():assert sha(raw['protocol/'+n])==h
 assert json.loads(raw['protocol/PROTOCOL.json'])==P
 errors=[];result={}
 try:
  for label in ['initial','final']:
   assert raw[label+'/TRACKED.diff']==b''
   for path,h in P['bindings'].items():assert sha(raw[label+'/source/'+path])==h,(label,path)
  for stage in ['compile-normal','registry']:
   assert int(raw[stage+'/exit.txt'])==0 and b'BUILD SUCCESSFUL' in raw[stage+'/stdout.log']
  result=audit(json.loads(raw['protocol/REQUEST.json']),raw)
  assert result==json.loads(raw['COLLECTOR_INVENTORY.json'])
  assert 'FAILURE.txt' not in raw and r['conclusion']==jobs[0]['conclusion']=='success'
 except Exception as e:errors.append(repr(e))
 out=dict(p,state='ORIGINAL_REGISTRY_INVENTORY_RECEIVED' if not errors else 'ORIGINAL_INVENTORY_FAILURE_RECEIVED',artifact=a['id'],artifact_bytes=len(b),artifact_sha256=sha(b),manifest_entries=len(manifest),gate_errors=errors,inventory=result,card_admission=False,multiplayer_admission=False,official_games=0,official_seeds=0,precision_reserved=2)
 (H/'VERIFIED_RECEIPT.json').write_text(json.dumps(out,indent=2)+'\n');print(json.dumps({'state':out['state'],'errors':errors,'resolved':result.get('resolved')}))
if __name__=='__main__':receive()
