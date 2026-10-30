"""Original supplemental receiving; all Assay/IR semantic classification remains explicit."""
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
 P=json.loads(data['protocol/PROTOCOL.json']);assert P['state']=='FROZEN_AFTER_VERIFIED_ONE_TEST_DEPENDENT_RECEIPT'
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
 sys.path.insert(0,str(out/'protocol'));from metadata_audit import verify
 result=dict(state='ORIGINAL_RECEIVED_CLASSIFICATION_PENDING',source=p['source'],validation=p['validation'],run=r['id'],job=j['id'],attempt=1,artifact=a['id'],bytes=len(raw),sha256=sha(raw),manifest_entries=len(manifest),gates={},gate_errors=[],feature_admitted=False,card_admitted=False)
 def gate(name,f):
  try:result['gates'][name]=f()
  except Exception as e:result['gate_errors'].append({'gate':name,'error':repr(e)})
 def bound(label):
  assert data[label+'/TRACKED.diff']==b''
  for path,h in P['bindings'].items():assert sha(data[label+'/source/'+path])==h,path
  for path,h in P['git_bindings'].items():assert blob(data[label+'/source/'+path])==h,path
  trees=json.loads(data[label+'/UNCHANGED_TREES.json']);assert trees==p['unchanged_trees'] and set(trees)==set(P['unchanged_modules'])
  return {'sha256_bindings':len(P['bindings']),'git_bindings':len(P['git_bindings']),'unchanged_trees':trees}
 for label in ['initial','final']:gate(label,lambda label=label:bound(label))
 def dependent():
  b=data['DEPENDENT_RECEIPT.json'];assert blob(b)==P['dependent_receipt_blob'];d=json.loads(b)
  assert d['state']=='ONE_TEST_DEPENDENT_CLIENT_ORIGINAL_VERIFIED_NOT_CARD_ADMISSION' and not d['gate_errors'] and d['source']==P['dependent_source'] and d['run']==40977504099
  assert d['gates']['cards']['passed']==389 and d['gates']['ordinary']['rows']==19074 and d['gates']['ordinary']['skips']==43 and d['gates']['client']['passed']==64
  return {'receipt_blob':blob(b),'run':d['run'],'reused':True}
 gate('dependent',dependent)
 def stage(n):
  rc=int(data[n+'/exit.txt']);assert rc in ((0,1) if n=='assay-compile' else (0,));log=data[n+'/stdout.log']+data[n+'/stderr.log'];assert log
  assert not re.search(rb'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
  if n=='compile-normal':assert b'BUILD SUCCESSFUL' in log
  return {'exit':rc,'stdout_sha256':sha(data[n+'/stdout.log']),'stderr_sha256':sha(data[n+'/stderr.log']),'classification_required':n in ['parse','explain','assay-compile','assay-war','current-ir']}
 for n in ['compile-normal','printing','corpus','parse','explain','assay-compile','assay-war','current-ir']:gate(n,lambda n=n:stage(n))
 def metadata():
  got=verify(data,data['protocol/ARCHIVED_WAR_METADATA.json'],data['initial/source/'+P['snapshot']]);assert got==json.loads(data['metadata/VERIFICATION.json']);return got
 gate('metadata',metadata)
 def corpus():
  bulk=data['corpus/oracle-cards.jsonl.gz'];found=[];count=0
  for line in gzip.decompress(bulk).decode().splitlines():
   line=line.strip().rstrip(',')
   if not line or line in ['[',']']:continue
   c=json.loads(line);count+=1
   if c.get('name')=='Fblthp, the Lost':found.append(c)
  assert len(found)==1;c=json.loads(data['metadata/war.json']);assert found[0]['oracle_id']==c['oracle_id'] and found[0]['oracle_text']==c['oracle_text']
  return {'cards':count,'sha256':sha(bulk),'exact_fblthp_rows':1}
 gate('corpus_identity',corpus)
 def ir():
  raw=data['current-ir/mtgish.lines.json'];found=[]
  for line in raw.decode().splitlines():
   if '"Name"' in line:
    c=json.loads(line)
    if c.get('Name')=='Fblthp, the Lost':found.append(c)
  assert len(found)==1 and found[0]==json.loads(data['current-ir/FBLTHP.json'])
  http=json.loads(data['current-ir/mtgish.lines.json.http.json']);assert http['url']==P['mtgish_url'] and http['status']==200
  return {'sha256':sha(raw),'exact_fblthp_rows':1,'classification_required':True}
 gate('current_ir_identity',ir)
 def controls():
  got=json.loads(subprocess.check_output(['python3',str(out/'protocol/check_metadata.py'),str(out/'initial/source')]));assert got==json.loads(data['check_metadata.json']);return got
 gate('synthetic_metadata_controls',controls)
 if 'FAILURE.txt' in data:result['gate_errors'].append({'gate':'collector','error':data['FAILURE.txt'].decode()})
 if r['conclusion']!='success' or j['conclusion']!='success':result['gate_errors'].append({'gate':'workflow','error':[r['conclusion'],j['conclusion']]})
 if result['gate_errors']:result['state']='ORIGINAL_FAILURE_RECEIVED_NO_ADMISSION'
 (H/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k!='gates'}))
if __name__=='__main__':receive()
