"""Original compile-only artifact receiving. Never grants behavioral/card acceptance."""
import json,hashlib,zipfile
from pathlib import Path
D=Path(__file__).resolve().parent;P=json.loads((D/'PROTOCOL.json').read_text());Q=json.loads((D/'PINS.json').read_text());M=json.loads((D/'metadata.json').read_text());H=lambda b:hashlib.sha256(b).hexdigest()
r=M['run'];a=M['artifact'];jobs=M['jobs']['jobs'];assert r['id']==Q['run'] and r['head_sha']==Q['validation'] and r['head_branch']==Q['branch'];assert r['workflow_id']==Q['workflow_id'] and r['path']==Q['workflow'];assert r['event']=='push' and r['run_number']==r['run_attempt']==1 and r['status']=='completed'
assert len(jobs)==1 and jobs[0]['id']==Q['job'] and jobs[0]['run_id']==Q['run'] and jobs[0]['run_attempt']==1 and jobs[0]['status']=='completed'
assert a['name']==Q['artifact_name'] and not a['expired'];assert a['workflow_run']['id']==Q['run'] and a['workflow_run']['head_sha']==Q['validation']
for k,parent in [('source',P['base']),('validation',Q['source'])]:
 c=M[k];assert c['sha']==Q[k] and c['tree']['sha']==Q['tree' if k=='source' else 'validation_tree'];assert [p['sha'] for p in c['parents']]==[parent]
assert M['main']['sha']==P['main'];assert sorted(f['filename'] for f in M['source_delta']['files'])==sorted(P['source_delta']);assert [f['filename'] for f in M['validation_delta']['files']]==[Q['workflow']]
b=(D/'original.zip').read_bytes();assert len(b)==a['size_in_bytes'] and 'sha256:'+H(b)==a['digest']
with zipfile.ZipFile(D/'original.zip') as z:
 assert z.testzip() is None;names=[n for n in z.namelist() if not n.endswith('/')];assert len(names)==len(set(names)) and all(not n.startswith('/') and '..' not in Path(n).parts for n in names);raw={n:z.read(n) for n in names}
manifest=json.loads(raw['MANIFEST.json']);assert set(manifest)==set(raw)-{'MANIFEST.json'};assert all(H(raw[n])==v for n,v in manifest.items())
binding=json.loads(raw['BINDING.json'])
for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert binding[k]==Q[k]
assert binding['run']==str(Q['run']) and binding['attempt']==binding['run_number']==1
wf=raw['WORKFLOW.yml'];assert hashlib.sha1(b'blob '+str(len(wf)).encode()+b'\0'+wf).hexdigest()==Q['workflow_blob']
assert json.loads(raw['protocol/PROTOCOL.json'])==P
for n,d in Q['protocol_files'].items():assert H(raw['protocol/'+n])==d
for p,d in {**P['protected'],**P['bindings']}.items():assert H(raw['source/'+p])==d,p

import tempfile
from audit import audit
errors=[];stages={};normal=raw['compile-normal/FORM.kt'];assert H(normal)==P['normal_sha256'];last=0
with tempfile.TemporaryDirectory() as td:
 for stage in P['stages']:
  if stage+'/RESULT.json' not in raw:errors.append({'stage':stage,'error':'missing stage result; not evidence'});continue
  result=json.loads(raw[stage+'/RESULT.json']);form=raw[stage+'/FORM.kt'];expected=normal
  variant=stage.removeprefix('compile-')
  if variant in P['mutants']:
   m=P['mutants'][variant];assert normal.count(m['old'].encode())==m['count'];expected=m['new'].encode().join(normal.rsplit(m['old'].encode(),1));assert H(expected)==m['sha256']
  assert form==expected and result['form_sha256']==H(form);assert result['started_ns']>=last and result['ended_ns']>=result['started_ns'];last=result['ended_ns']
  d=Path(td)/stage;d.mkdir()
  for name,value in raw.items():
   if name.startswith(stage+'/'):
    p=Path(td)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(value)
  try:stages[stage]=audit(d,stage)
  except (AssertionError,ValueError,KeyError) as e:errors.append({'stage':stage,'error':'original gate rejected: '+repr(e),'exit':result['exit']})
assert json.loads(raw['RESTORATION.json'])=={'restored':True}
if 'FAILURE.txt' in raw:errors.append({'stage':'runner','error':raw['FAILURE.txt'].decode()})
if not errors:
 assert json.loads(raw['GATES.json'])==stages
 assert json.loads(raw['RESULT.json'])=={'state':'BEHAVIORAL_GATES_COMPLETE_ORIGINAL_RECEIVING_REQUIRED'}
receipt=dict(**Q,state='ORIGINAL_BEHAVIORAL_GATES_RECEIVED_DEPENDENT_ADMISSION_PENDING' if not errors and r['conclusion']==jobs[0]['conclusion']=='success' else 'ORIGINAL_BEHAVIORAL_FAILURE_RECEIVED',artifact=a['id'],artifact_bytes=len(b),artifact_sha256=H(b),manifest_entries=len(manifest),stages=stages,gate_errors=errors,card_acceptance=False,official_games=0,official_seeds=0,precision_reserved=2)
(D/'VERIFIED_RECEIPT.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({k:receipt[k] for k in ['state','manifest_entries','gate_errors']}))
