"""Receive original attempt once. No feature/card admission from these dependent gates alone."""
from pathlib import Path,PurePosixPath
import json,hashlib,zipfile,sys,re,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent;P=json.loads((R/'PINS.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
run=json.loads((R/'RUN.json').read_text());jobs=json.loads((R/'JOBS.json').read_text());arts=json.loads((R/'ARTIFACTS.json').read_text())
assert run['id']==P['run'] and run['head_sha']==P['validation'] and run['head_branch']==P['branch']
assert run['run_attempt']==run['run_number']==1 and run['event']=='push' and run['status']=='completed'
assert run['workflow_id']==P['workflow_id'] and run['path']==P['workflow']
assert jobs['total_count']==len(jobs['jobs'])==1;j=jobs['jobs'][0]
assert j['id']==P['job'] and j['run_id']==run['id'] and j['head_sha']==P['validation'] and j['status']=='completed'
assert arts['total_count']==len(arts['artifacts'])==1;a=arts['artifacts'][0]
assert a['name']==P['artifact_name'] and not a['expired'] and a['workflow_run']['id']==run['id'] and a['workflow_run']['head_sha']==P['validation']
raw=(R/'original.zip').read_bytes();assert len(raw)==a['size_in_bytes'] and 'sha256:'+sha(raw)==a['digest']
with zipfile.ZipFile(R/'original.zip') as z:
 assert z.testzip() is None;names=z.namelist();assert len(names)==len(set(names))
 assert all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts for n in names)
 data={n:z.read(n) for n in names if not n.endswith('/')}
m=json.loads(data['MANIFEST.json']);assert set(m)==set(data)-{'MANIFEST.json'}
for n,h in m.items():assert sha(data[n])==h,n
for n,h in P['protocol_blobs'].items():assert blob(data['protocol/'+n])==h,n
assert blob(data['WORKFLOW.yml'])==P['workflow_blob']
b=json.loads(data['BINDING.json'])
for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert b[k]==P[k],k
assert int(b['run'])==run['id'] and b['attempt']==b['run_number']==1
q=json.loads(data['protocol/PROTOCOL.json'])
for phase in ['initial','final']:
 for n,h in q['bindings'].items():assert sha(data[phase+'/source/'+n])==h,(phase,n)
 for n,h in q['git_bindings'].items():assert blob(data[phase+'/source/'+n])==h,(phase,n)
 assert data[phase+'/TRACKED.diff']==b''
out=R/'extracted';assert not out.exists(),'Do not receive twice'
for n,v in data.items():
 p=out/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(v)
sys.path.insert(0,str(out/'protocol'));import audit
result=dict(state='ORIGINAL_DEPENDENT_RECEIVED_NOT_CARD_ADMISSION',source=P['source'],validation=P['validation'],run=run['id'],job=j['id'],attempt=1,artifact=a['id'],bytes=len(raw),sha256=sha(raw),manifest_entries=len(m),feature_admitted=False,card_admitted=False,stages={})
observed={}
for n,v in data.items():
 if '/junit/' not in n or not n.endswith('.xml'):continue
 root=ET.fromstring(v);observed[n]=[{'class':t.get('classname'),'name':t.get('name'),'skips':len(t.findall('skipped')),'failures':[{'type':f.get('type'),'message':f.get('message'),'text':f.text} for f in t.findall('failure')],'errors':[{'type':f.get('type'),'message':f.get('message'),'text':f.text} for f in t.findall('error')]} for t in root.iter('testcase')]
(R/'OBSERVED_REPORTS.json').write_text(json.dumps(observed,indent=2)+'\n')
if 'FAILURE.txt' in data:
 result['state']='FAILED_ORIGINAL_RECEIVED_NO_ADMISSION';result['failure']=data['FAILURE.txt'].decode()
else:
 assert run['conclusion']==j['conclusion']=='success'
 text=data['compile/output.log'].decode();assert data['compile/exit.txt'].strip()==b'0' and 'BUILD SUCCESSFUL' in text
 assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',text,re.M)
 result['stages']['compile']={'compiled':True}
 # Recompute independently from pinned original input files; never trust candidate summary.
 inv=audit.inventory(out/'initial/source')
 assert sha(json.dumps(inv,sort_keys=True,separators=(',',':')).encode())==q['inventory_sha256']
 assert inv==json.loads(data['INVENTORY.json'])
 for name in ['cards','ordinary']:result['stages'][name]=audit.junit(out/name,inv[name])
 assert json.loads(data['RESULT.json'])['state']=='DEPENDENT_REPORTS_PASSED_ORIGINAL_RECEIVING_PENDING'
(R/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['stages','failure']}))
