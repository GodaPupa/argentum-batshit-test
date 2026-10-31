"""Receive the original frozen Opal Palace full dependent artifact once; no feature/card admission."""
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
 P=json.loads(data['protocol/PROTOCOL.json']);assert P['base']==p['base'] and P['state']=='FROZEN_AFTER_ORIGINAL_EARLY_RECEIVING'
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
 sys.path.insert(0,str(out/'protocol'))
 from audit import inventory,junit,client
 from report_audit import compile_gate,reports
 def inv():
  I=inventory(out/'initial/source')
  assert sha(json.dumps(I,sort_keys=True,separators=(',',':')).encode())==P['inventory_sha256']
  assert I==json.loads(data['INVENTORY.json'])
  assert sum(I['ordinary']['identities'].values())==19139 and sum(I['ordinary']['skips'].values())==43
  assert sum(I['cards']['identities'].values())==390 and not I['cards']['skips']
  return I
 I=inv();gate('inventory',lambda:{'ordinary':19139,'original':19074,'added':65,'skips':43,'cards':390})
 def checked(name,fn):
  got=fn();assert got==json.loads(data[name+'/SUMMARY.json']);return got
 def reused_dependent():
  d=json.loads(data['REUSED_DEPENDENT_RECEIPT.json']);link=json.loads(data['REUSED_GATES.json'])
  assert d['state']=='ORIGINAL_DEPENDENT_SNAPSHOT_FAILURE_VERIFIED' and d['compile_normal_passed'] and d['focused_passed']==137
  assert d['source']==link['source']=='b470711655da386f0b3913cbbe16befe86d139ee' and d['run']==41157568547
  assert link['receipt_commit']==P['base']=='a5944c62a5532274b3983d98450367145267b475'
  assert data['REUSED_GATES.json']==data['protocol/REUSED_GATES.json']
  return {'compile_normal_reused':True,'focused_reused':137,'receipt_commit':P['base']}
 gate('compile_and_focused_reuse',reused_dependent)
 for name in ['cards','ordinary']:gate(name,lambda name=name:checked(name,lambda:junit(out/name,I[name])))
 gate('client',lambda:checked('client',lambda:client(out/'client',json.loads(data['initial/source/'+P['client_contract']]))))
 def assay():
  for n in ['corpus','assay-war','assay-c13']:assert int(data['assay-war/'+n+'-exit.txt'])==0
  baseline=data['protocol/ORIGINAL_WAR_DIFFERENTIAL.txt'];assert sha(baseline)==P['assay_baseline_sha256'] and data['assay-war/assay-war.stdout.log']==baseline
  corpus=data['assay-war/oracle-cards.jsonl.gz'];assert gzip.decompress(corpus)
  got={'original_differential_preserved':True,'sha256':sha(baseline),'corpus_sha256':sha(corpus),'classified_bolt_bend_structural_divergences':1}
  assert got==json.loads(data['assay-war/SUMMARY.json']);return got
 gate('assay',assay)
 def c13_raw():
  b=data['assay-war/assay-c13.stdout.log'];assert b.strip()
  return {'raw_sha256':sha(b),'classification_pending':True,'decline_is_not_pass':True}
 gate('C13_raw_collection',c13_raw)
 def reuse():
  b=data['EARLY_RECEIPT.json'];assert blob(b)==P['early_receipt_blob'];d=json.loads(b)
  assert d['pins']['source']==P['early_source'] and d['negative_witnesses']==18 and d['negative_controls']==170 and d['state']=='ORIGINAL_EARLY_VERIFIED_DEPENDENT_GATES_PENDING'
  return {'run':d['pins']['run'],'receipt_blob':blob(b),'source':d['pins']['source']}
 gate('early_evidence_reuse',reuse)
 def controls(name,filename):
  got=json.loads(subprocess.check_output(['python3',str(out/'protocol'/filename)]));assert got==json.loads(data[name+'.json']);return got
 gate('auditor_controls',lambda:controls('check_audit','check_audit.py'))
 gate('assay_controls',lambda:controls('check_differential','check_differential.py'))
 def completion():
  d=json.loads(data['RESULT.json']);assert d=={'state':'DEPENDENT_EXECUTION_COMPLETE_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False};return d
 gate('completion',completion)
 if 'FAILURE.txt' in data:result['gate_errors'].append({'gate':'collector','error':data['FAILURE.txt'].decode()})
 if r['conclusion']!='success' or j['conclusion']!='success':result['gate_errors'].append({'gate':'workflow','error':[r['conclusion'],j['conclusion']]})
 if result['gate_errors']:result['state']='ORIGINAL_FAILURE_RECEIVED_NO_ADMISSION'
 else:result['state']='OPAL_DEPENDENT_ORIGINAL_VERIFIED_C13_CLASSIFICATION_AND_COMBINED_REVIEW_PENDING'
 (H/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='gates'}))
if __name__=='__main__':receive()

