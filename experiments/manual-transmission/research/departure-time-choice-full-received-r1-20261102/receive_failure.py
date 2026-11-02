"""Failure-only receiver for original run41418326149. Cannot grant acceptance."""
import json,hashlib,zipfile,tempfile,sys,importlib.util
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from receive import archive_files,gitsha
sha=lambda b:hashlib.sha256(b).hexdigest()
def receive(archive,api_path,expected_path,out):
 e=json.loads(Path(expected_path).read_text());a=json.loads(Path(api_path).read_text());b=Path(archive).read_bytes()
 r=a['run'];j=a['jobs']['jobs'];ar=a['artifacts']['artifacts']
 assert r['id']==e['run'] and r['head_sha']==e['validation'] and r['path']==e['workflow'] and r['event']=='push'
 assert r['run_number']==r['run_attempt']==1 and r['status']=='completed' and r['conclusion']=='failure'
 assert a['jobs']['total_count']==len(j)==1 and j[0]['id']==e['job'] and j[0]['head_sha']==e['validation'] and j[0]['run_id']==e['run'] and j[0]['run_attempt']==1 and j[0]['conclusion']=='failure'
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
  result['cards']=junit(root/'cards',I['cards'])
  for name,v in result.items():assert json.loads(F[name+'/SUMMARY.json'])==v,name
  result['ordinary']=failed_ordinary(F,I['ordinary'])
 assert 'RESULT.json' not in F and 'ordinary/SUMMARY.json' not in F
 assert not any(n.startswith('client/') for n in F), 'client was not reached'
 Path(out).write_text(json.dumps({'state':'ORIGINAL_FULL_FAILURE_RECEIVED_NOT_ACCEPTED','source':e['source'],'run':e['run'],'artifact':ar['id'],'sha256':sha(b),'bytes':len(b),'manifest_entries':len(F)-1,'reports':result,'accepted':False,'validation':e['validation'],'tree':e['tree'],'validation_tree':e['validation_tree'],'workflow':e['workflow'],'workflow_blob':e['workflow_blob'],'job':e['job'],'attempt':1,'bindings':len(P['bindings']),'git_bindings':len(P['git_bindings']),'unchanged_trees':len(e['unchanged_trees']),'unexecuted_client':64,'targeted_receipt_reused':e['early_receipt_commit']},indent=2)+'\n')

def failed_ordinary(F,expected):
 from collections import Counter
 import xml.etree.ElementTree as ET
 import difflib,re
 names=Counter();skips=Counter();failures=[];modules={}
 for path,b in sorted(F.items()):
  if not path.startswith('ordinary/junit/') or not path.endswith('.xml'):continue
  rel=path.split('/junit/',1)[1];module=rel.split('/build/test-results/test/')[0]
  root=ET.fromstring(b);rows=list(root.iter('testcase'));assert int(root.get('tests'))==len(rows)
  for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:assert int(root.get(attr,0))==sum(t.find(tag) is not None for t in rows)
  for t in rows:
   key='\t'.join([module,t.get('classname'),t.get('name')]);names[key]+=1
   assert t.find('error') is None
   if t.find('skipped') is not None:skips[key]+=1
   m=modules.setdefault(module,dict(rows=0,failures=0,skips=0));m['rows']+=1;m['skips']+=t.find('skipped') is not None
   fs=t.findall('failure')
   if fs:
    assert len(fs)==1;m['failures']+=1;f=fs[0];msg=f.get('message','');body=f.text or ''
    assert f.get('type')=='org.opentest4j.AssertionFailedError'
    assert msg.startswith('org.opentest4j.AssertionFailedError: Values differed at keys entities\nexpected:<')
    aa=msg.split('expected:<',1)[1].split('> but was:<');assert len(aa)==2 and aa[1].endswith('>')
    a,bb=aa[0],aa[1][:-1];ops=[(op,a[i:j],bb[k:l],a[max(0,i-45):j+15]) for op,i,j,k,l in difflib.SequenceMatcher(None,a,bb,autojunk=False).get_opcodes() if op!='equal']
    assert len(ops)==1 and ops[0][:3]==('replace','null','[]') and 'castChoices' in ops[0][3],ops
    failure={'identity':key,'report':path,'type':f.get('type'),'message':msg,'trace':body,'exact_difference':{'field':'entities.card-1002.LastKnownPermanentComponent.snapshot.castChoices','expected':None,'actual':[]}}
    failures.append(failure)
 expected_failures={
  'rules-engine\tcom.wingedsheep.engine.state.LegacySuspensionMigrationTest\tlegacy free-cast-target loads without allocation and resumes the captured response trace':('LegacySuspensionMigrationTest.kt',65),
  'rules-engine\tcom.wingedsheep.engine.state.SuspensionTraceTest\tcurrent free-cast-target suspension reproduces the captured parent response trace':('SuspensionTraceTest.kt',53)}
 assert Counter(f['identity'] for f in failures)==Counter(expected_failures.keys())
 for f in failures:
  fn,line=expected_failures[f['identity']];assert re.search(re.escape(fn)+':'+str(line)+r'\b',f['trace'])
 assert not names-Counter(expected['identities']), 'unexpected or duplicate rows'
 assert skips==Counter(expected['skips']) and sum(skips.values())==43
 assert sum(names.values())==8417 and len(failures)==2
 assert F['ordinary/exit.txt'].strip()==b'1' and b'BUILD FAILED' in F['ordinary/output.log']
 assert not re.search(rb'^e: |Compilation error|Unresolved reference',F['ordinary/output.log'],re.M)
 missing=Counter(expected['identities'])-names;assert sum(missing.values())==10905
 return {'rows':8417,'passed':8372,'failures':failures,'errors':0,'skips':43,'identities':dict(names),'skipped_identities':dict(skips),'missing_identities':dict(missing),'missing_rows':10905,'by_module':modules,'complete':False}

if __name__=='__main__':receive(*sys.argv[1:])
