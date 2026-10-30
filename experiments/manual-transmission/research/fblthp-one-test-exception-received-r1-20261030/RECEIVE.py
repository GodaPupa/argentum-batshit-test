"""Receive the pinned original once; strict partial evidence survives every failed gate."""
from pathlib import Path,PurePosixPath
import json,hashlib,zipfile,sys,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def envelope(P,run,jobs,arts,raw,proof):
 assert run['id']==P['run'] and run['head_sha']==P['validation'] and run['head_branch']==P['branch']
 assert run['run_attempt']==run['run_number']==1 and run['event']=='push' and run['status']=='completed'
 assert run['workflow_id']==P['workflow_id'] and run['path']==P['workflow']
 assert jobs['total_count']==len(jobs['jobs'])==1;j=jobs['jobs'][0]
 assert j['id']==P['job'] and j['run_id']==run['id'] and j['head_sha']==P['validation'] and j['status']=='completed' and j['run_attempt']==1
 assert arts['total_count']==len(arts['artifacts'])==1;a=arts['artifacts'][0]
 assert a['name']==P['artifact_name'] and not a['expired'] and a['workflow_run']['id']==run['id'] and a['workflow_run']['head_sha']==P['validation']
 assert len(raw)==a['size_in_bytes'] and 'sha256:'+sha(raw)==a['digest']
 for k,parent in [('source',P['base']),('validation',P['source'])]:
  c=proof[k];assert c['sha']==P[k] and c['tree']['sha']==P['tree' if k=='source' else 'validation_tree'] and [p['sha'] for p in c['parents']]==[parent]
 assert proof['main']['sha']==P['main']
 assert sorted(f['filename'] for f in proof['source_delta']['files'])==sorted(P['source_delta'])
 assert [f['filename'] for f in proof['validation_delta']['files']]==[P['workflow']]
 return j,a
def unpack(raw):
 import io
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  assert z.testzip() is None;names=z.namelist();assert len(names)==len(set(names))
  assert all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts for n in names)
  data={n:z.read(n) for n in names if not n.endswith('/')}
 m=json.loads(data['MANIFEST.json']);assert set(m)==set(data)-{'MANIFEST.json'}
 for n,h in m.items():assert sha(data[n])==h,n
 return data,m
def receive():
 P=json.loads((R/'PINS.json').read_text())
 run,jobs,arts,proof=[json.loads((R/n).read_text()) for n in ['RUN.json','JOBS.json','ARTIFACTS.json','GIT_PROOF.json']]
 raw=(R/'original.zip').read_bytes();j,a=envelope(P,run,jobs,arts,raw,proof);data,m=unpack(raw)
 for n,h in P['protocol_blobs'].items():assert blob(data['protocol/'+n])==h,n
 assert set(n.removeprefix('protocol/') for n in data if n.startswith('protocol/'))==set(P['protocol_blobs'])
 assert blob(data['WORKFLOW.yml'])==P['workflow_blob']
 b=json.loads(data['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[k]==P[k],k
 assert int(b['run'])==run['id'] and b['attempt']==b['run_number']==1 and b['web_client_tree']==P['web_client_tree']
 q=json.loads(data['protocol/PROTOCOL.json'])
 assert q['base']==P['base'] and q['main']==P['main']
 for n,h in P['approved_blobs'].items():assert blob(data['approved/'+n])==h,n
 assert sha(data['approved/ORIGINAL_BECOMES_TARGET_TEST.kt'])==q['original_test_sha256']
 assert sha(data['approved/PROPOSED_BECOMES_TARGET_TEST.kt'])==q['proposed_test_sha256']
 # Only after complete transport/identity verification extract original evidence, exactly once.
 out=R/'extracted';assert not out.exists(),'Do not receive twice'
 for n,v in data.items():p=out/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(v)
 sys.path.insert(0,str(out/'protocol'));import audit,report_audit
 result=dict(state='ORIGINAL_RECEIVED_GATES_PENDING',source=P['source'],validation=P['validation'],run=run['id'],job=j['id'],attempt=1,artifact=a['id'],bytes=len(raw),sha256=sha(raw),manifest_entries=len(m),feature_admitted=False,card_admitted=False,gates={},gate_errors=[])
 def gate(name,f):
  try:result['gates'][name]=f()
  except Exception as e:result['gate_errors'].append({'gate':name,'error':repr(e)})
 def binding(phase):
  for n,h in q['bindings'].items():assert sha(data[phase+'/source/'+n])==h,(phase,n)
  for n,h in q['git_bindings'].items():assert blob(data[phase+'/source/'+n])==h,(phase,n)
  assert data[phase+'/TRACKED.diff']==b''
  assert data[phase+'/source/'+q['test_path']]==data['approved/PROPOSED_BECOMES_TARGET_TEST.kt']
  return {'sha256_bindings':len(q['bindings']),'git_bindings':len(q['git_bindings']),'tracked_clean':True}
 for phase in ['initial','compiled-restored','mutants-restored','final']:gate(phase,lambda phase=phase:binding(phase))
 def form(name):
  v=q['mutants'][name];old=data['initial/source/'+v['path']].decode();assert old.count(v['old'])==v['occurrences']
  wanted=old.replace(v['old'],v['new']).encode();assert data['forms/'+name+'/'+v['path']]==wanted and sha(wanted)==v['sha256']
  return {'sha256':sha(wanted)}
 for name in q['mutants']:gate('form-'+name,lambda name=name:form(name))
 for name in ['compile-normal',*('compile-'+n for n in q['mutants'])]:gate(name,lambda name=name:report_audit.compile_gate(out/name))
 for name in ['focused-normal','focused-restored']:gate(name,lambda name=name:report_audit.reports(out/name,q['focused_cases']))
 for name,v in q['mutants'].items():gate(name,lambda name=name,v=v:report_audit.reports(out/name,q['axes_cases'],v['witnesses']))
 inv=audit.inventory(out/'initial/source')
 assert sha(json.dumps(inv,sort_keys=True,separators=(',',':')).encode())==q['inventory_sha256']
 assert inv==json.loads(data['INVENTORY.json'])
 for name in ['cards','ordinary']:gate(name,lambda name=name:audit.junit(out/name,inv[name]))
 contract=json.loads(data['initial/source/'+q['client_contract']]);gate('client',lambda:audit.client(out/'client',contract))
 # Re-run executable auditor controls from immutable protocol; these are not behavioral evidence.
 import subprocess
 for script in ['check_audit.py','check_reports.py']:
  def control(script=script):
   got=json.loads(subprocess.check_output(['python3',str(out/'protocol'/script)]));assert got==json.loads(data[script+'.json']);return got
  gate(script,control)
 observed={}
 for n,v in data.items():
  if '/junit/' in n and n.endswith('.xml'):
   rt=ET.fromstring(v);observed[n]=[{'class':t.get('classname'),'name':t.get('name'),'skips':len(t.findall('skipped')),'failures':[{'type':f.get('type'),'message':f.get('message'),'text':f.text} for f in t.findall('failure')],'errors':[{'type':f.get('type'),'message':f.get('message'),'text':f.text} for f in t.findall('error')]} for t in rt.iter('testcase')]
 (R/'OBSERVED_REPORTS.json').write_text(json.dumps(observed,indent=2)+'\n')
 if 'FAILURE.txt' in data:result['gate_errors'].append({'gate':'collector','error':data['FAILURE.txt'].decode()})
 def completion():
  assert run['conclusion']==j['conclusion']=='success'
  assert json.loads(data['RESULT.json'])=={'state':'ONE_TEST_DEPENDENT_CLIENT_PASSED_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False}
  return {'workflow_success':True,'collector_complete':True}
 gate('completion',completion)
 result['state']='ONE_TEST_DEPENDENT_CLIENT_ORIGINAL_VERIFIED_NOT_CARD_ADMISSION' if not result['gate_errors'] else 'FAILED_ORIGINAL_RECEIVED_NO_ADMISSION'
 (R/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps({k:v for k,v in result.items() if k!='gates'}))
if __name__=='__main__':receive()
