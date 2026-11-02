"""Original-artifact full receiving. Never dispatches or grants admission."""
import json,hashlib,zipfile,tempfile,sys,importlib.util
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from receive import archive_files,gitsha
sha=lambda b:hashlib.sha256(b).hexdigest()
def receive(archive,api_path,expected_path,out):
 e=json.loads(Path(expected_path).read_text());a=json.loads(Path(api_path).read_text());b=Path(archive).read_bytes()
 r=a['run'];j=a['jobs']['jobs'];ar=a['artifacts']['artifacts']
 assert r['id']==e['run'] and r['head_sha']==e['validation'] and r['path']==e['workflow'] and r['event']=='push'
 assert r['run_number']==r['run_attempt']==1 and r['status']=='completed' and r['conclusion']=='success'
 assert a['jobs']['total_count']==len(j)==1 and j[0]['id']==e['job'] and j[0]['head_sha']==e['validation'] and j[0]['run_id']==e['run'] and j[0]['run_attempt']==1 and j[0]['conclusion']=='success'
 assert a['artifacts']['total_count']==len(ar)==1;ar=ar[0]
 assert ar['id']==e['artifact'] and ar['name']==e['artifact_name'] and not ar['expired'] and ar['workflow_run']['head_sha']==e['validation'] and ar['workflow_run']['id']==e['run']
 assert len(b)==ar['size_in_bytes']==e['artifact_bytes'] and ar['digest']=='sha256:'+sha(b)=='sha256:'+e['artifact_sha256']
 for key,s,t,parent in [('source_commit','source','tree','base'),('validation_commit','validation','validation_tree','source')]:
  c=a[key];assert c['sha']==e[s] and c['tree']['sha']==e[t] and [p['sha'] for p in c['parents']]==[e[parent]]
 for key,files in [('source_compare',e['source_files']),('validation_compare',{e['workflow']:'added'})]:
  c=a[key];assert c['status']=='ahead' and c['ahead_by']==1 and {f['filename']:f['status'] for f in c['files']}==files
 F=archive_files(archive);P=json.loads(F['protocol/PROTOCOL.json']);B=json.loads(F['BINDING.json'])
 for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert B[k]==e[k],k
 assert int(B['run'])==e['run'] and B['attempt']==B['run_number']==1 and gitsha(F['WORKFLOW.yml'])==e['workflow_blob']
 for p,h in e['frozen_protocol_files'].items():assert sha(F['protocol/'+p])==h,p
 assert P['base']==e['base'] and P['early_receipt_commit']==e['early_receipt_commit']
 assert F['EARLY_RECEIPT.json']==F['protocol/EARLY_RECEIPT.json'] and gitsha(F['EARLY_RECEIPT.json'])==P['early_receipt_blob']
 I=json.loads(F['INVENTORY.json']);assert sha(json.dumps(I,sort_keys=True,separators=(',',':')).encode())==P['inventory_sha256']==e['inventory_sha256']
 for label in ['initial','final']:
  assert F[label+'/TRACKED.diff']==b''
  for p,h in P['bindings'].items():assert sha(F[label+'/source/'+p])==h,p
  for p,h in P['git_bindings'].items():assert gitsha(F[label+'/source/'+p])==h,p
  assert json.loads(F[label+'/UNCHANGED_TREES.json'])==e['unchanged_trees']
 with tempfile.TemporaryDirectory() as d:
  root=Path(d)
  for p,bytes_ in F.items():
   dest=root/p;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(bytes_)
  sys.path.insert(0,str(root/'protocol'))
  for module in ['audit','report_audit']:sys.modules.pop(module,None)
  from audit import junit,client
  from report_audit import compile_gate,reports
  result={'compile':compile_gate(root/'compile'),'focused':reports(root/'focused',P['focused_cases'])}
  for name in ['cards','ordinary']:result[name]=junit(root/name,I[name])
  contract=json.loads(F['initial/source/'+P['client_contract']]);result['client']=client(root/'client',contract)
  for name,v in result.items():assert json.loads(F[name+'/SUMMARY.json'])==v,name
 assert json.loads(F['RESULT.json'])=={'state':'DEPENDENT_EXECUTION_COMPLETE_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False}
 Path(out).write_text(json.dumps({'state':'ORIGINAL_FULL_GATES_VERIFIED_ADMISSION_REVIEW_PENDING','source':e['source'],'run':e['run'],'artifact':ar['id'],'sha256':sha(b),'bytes':len(b),'manifest_entries':len(F)-1,'reports':result,'accepted':False},indent=2)+'\n')
if __name__=='__main__':receive(*sys.argv[1:])
