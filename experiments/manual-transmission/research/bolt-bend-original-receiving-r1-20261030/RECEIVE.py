"""Receive original Bolt Bend reproduction once; no runtime repair or card admission."""
from pathlib import Path,PurePosixPath
import json,hashlib,zipfile,gzip,re,subprocess,sys
H=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def receive():
 p=json.loads((H/'PINS.json').read_text());m=json.loads((H/'GITHUB_METADATA.json').read_text());r=m['run'];jobs=m['jobs'];arts=m['artifacts'];proof=m['git']
 assert r['id']==p['run'] and r['head_sha']==p['validation'] and r['head_branch']==p['branch'] and r['path']==p['workflow'] and r['workflow_id']==p['workflow_id']
 assert r['run_attempt']==r['run_number']==1 and r['event']=='push' and r['status']=='completed'
 assert jobs['total_count']==len(jobs['jobs'])==1;j=jobs['jobs'][0]
 assert j['id']==p['job'] and j['run_id']==r['id'] and j['head_sha']==p['validation'] and j['run_attempt']==1 and j['status']=='completed'
 assert arts['total_count']==len(arts['artifacts'])==1;a=arts['artifacts'][0]
 assert a['name']==p['artifact_name'] and not a['expired'] and a['workflow_run']['head_sha']==p['validation'] and a['workflow_run']['id']==r['id']
 raw=(H/'original.zip').read_bytes();assert len(raw)==a['size_in_bytes'] and 'sha256:'+sha(raw)==a['digest']
 with zipfile.ZipFile(H/'original.zip') as z:
  assert z.testzip() is None;names=z.namelist();assert len(names)==len(set(names)) and all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts for n in names)
  data={n:z.read(n) for n in names if not n.endswith('/')}
 manifest=json.loads(data['MANIFEST.json']);assert set(manifest)==set(data)-{'MANIFEST.json'}
 for n,h in manifest.items():assert sha(data[n])==h,n
 for n,h in p['protocol_blobs'].items():assert blob(data['protocol/'+n])==h,n
 assert set(n[9:] for n in data if n.startswith('protocol/'))==set(p['protocol_blobs'])
 P=json.loads(data['protocol/PROTOCOL.json']);assert P['state']=='ORIGINAL_ONLY_REPRODUCTION_NO_REPAIR'
 for k,parent in [('source',P['base']),('validation',p['source'])]:
  c=proof[k];assert c['sha']==p[k] and c['tree']['sha']==p['tree' if k=='source' else 'validation_tree'] and [v['sha'] for v in c['parents']]==[parent]
 assert proof['main']['sha']==P['main']
 assert sorted(f['filename'] for f in proof['source_delta']['files'])==sorted(P['source_delta'])
 assert [f['filename'] for f in proof['validation_delta']['files']]==[p['workflow']]
 assert blob(data['WORKFLOW.yml'])==p['workflow_blob'];binding=json.loads(data['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert binding[k]==p[k]
 assert int(binding['run'])==r['id'] and binding['attempt']==binding['run_number']==1
 out=H/'extracted';assert not out.exists(),'Original must not be received twice'
 for n,v in data.items():f=out/n;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(v)
 result=dict(state='ORIGINAL_RECEIVED_CLASSIFICATION_PENDING',source=p['source'],validation=p['validation'],run=r['id'],job=j['id'],attempt=1,artifact=a['id'],bytes=len(raw),sha256=sha(raw),manifest_entries=len(manifest),gates={},gate_errors=[],feature_admitted=False,card_admitted=False)
 def gate(name,f):
  try:result['gates'][name]=f()
  except Exception as e:result['gate_errors'].append({'gate':name,'error':repr(e)})
 def bound(label):
  assert data[label+'/TRACKED.diff']==b''
  for path,h in P['bindings'].items():assert sha(data[label+'/source/'+path])==h,path
  for path,h in P['git_bindings'].items():assert blob(data[label+'/source/'+path])==h,path
  trees=json.loads(data[label+'/UNCHANGED_TREES.json']);assert trees==p['unchanged_trees'] and set(trees)==set(P['unchanged_trees'])
  return {'sha256_bindings':len(P['bindings']),'git_bindings':len(P['git_bindings']),'unchanged_trees':trees}
 for label in ['initial','final']:gate(label,lambda label=label:bound(label))
 def compiled():
  assert int(data['compile/exit.txt'])==0
  log=data['compile/output.log'];assert b'BUILD SUCCESSFUL' in log
  assert not re.search(rb'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
  assert not any(n.startswith('compile/junit/') for n in data)
  return {'normal_compilation':True,'output_sha256':sha(log)}
 gate('compile',compiled)
 def reproduce():
  sys.path.insert(0,str(out/'protocol'))
  from audit import verify
  result=verify(out/'reproduce');assert result==json.loads(data['reproduce/SUMMARY.json'])
  return result
 gate('original_reproduction',reproduce)
 def controls():
  got=json.loads(subprocess.check_output(['python3',str(out/'protocol/check_audit.py')]))
  assert got['count']==len(got['synthetic_rejections'])==11
  return got
 gate('synthetic_auditor_controls',controls)
 def completion():
  d=json.loads(data['RESULT.json']);assert d=={'state':'ORIGINAL_DEFECT_REPRODUCED_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False};return d
 gate('completion',completion)
 if 'FAILURE.txt' in data:result['gate_errors'].append({'gate':'collector','error':data['FAILURE.txt'].decode()})
 if r['conclusion']!='success' or j['conclusion']!='success':result['gate_errors'].append({'gate':'workflow','error':[r['conclusion'],j['conclusion']]})
 if result['gate_errors']:result['state']='ORIGINAL_FAILURE_RECEIVED_NO_ADMISSION'
 else:result['state']='ORIGINAL_BOLT_BEND_DEFECT_VERIFIED_NOT_REPAIR_ACCEPTANCE'
 (H/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='gates'}))
if __name__=='__main__':receive()

