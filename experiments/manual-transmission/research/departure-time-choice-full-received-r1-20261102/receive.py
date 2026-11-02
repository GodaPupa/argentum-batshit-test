"""Original early artifact receiver. No dispatch, gameplay or full acceptance."""
from pathlib import Path,PurePosixPath
from collections import Counter
import json,hashlib,zipfile,sys,stat,re,xml.etree.ElementTree as ET
sha=lambda b:hashlib.sha256(b).hexdigest()
gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
def archive_files(path):
 with zipfile.ZipFile(path) as z:
  names=z.namelist();assert len(names)==len(set(names)), 'duplicate ZIP member'
  assert all(not PurePosixPath(n).is_absolute() and '..' not in PurePosixPath(n).parts and '\\' not in n for n in names),'unsafe ZIP path'
  assert all(not stat.S_ISLNK(i.external_attr>>16) for i in z.infolist()),'ZIP symlink'
  files={n:z.read(n) for n in names if not n.endswith('/')}
 assert json.loads(files['MANIFEST.json'])=={n:sha(b) for n,b in files.items() if n!='MANIFEST.json'},'manifest exact bytes/set'
 return files
def rows_from_files(files,stage):
 rows=[]
 for n,b in sorted(files.items()):
  if not n.startswith(stage+'/junit/') or not n.endswith('.xml'):continue
  root=ET.fromstring(b);assert root.tag=='testsuite';cases=list(root.iter('testcase'))
  assert int(root.get('tests'))==len(cases)
  for label,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
   assert int(root.get(label,0))==sum(bool(t.findall(tag)) for t in cases)
  for t in cases:rows.append({'class':t.get('classname'),'name':t.get('name'),
   **{key:[f.attrib|{'text':f.text} for f in t.findall(tag)] for key,tag in [('failure','failure'),('errors','error'),('skips','skipped')]}})
 return rows
def check_rows(rows,cases,failed,contract=None):
 assert Counter((r['class'],r['name']) for r in rows)==Counter((c,n) for c,ns in cases.items() for n in ns),'identity/multiplicity'
 assert all(not r['errors'] and not r['skips'] for r in rows),'errors/skips forbidden'
 assert Counter((r['class'],r['name']) for r in rows if r['failure'])==Counter(tuple(x) for x in failed),'exact failure set'
 if contract:
  for r in rows:
   if not r['failure']:continue
   assert len(r['failure'])==1;f=r['failure'][0];body=f.get('text') or '';msg=f.get('message','')
   assert 'Assertion' in f.get('type','') or 'Assertion' in body,'behavioral assertion required'
   assert re.search(re.escape(contract['assertion_file'])+':'+str(contract['assertion_line'])+r'\b',body),'exact assertion location'
   assert re.search(r'expected\s*:?\s*<?'+re.escape(contract['expected'])+r'(?![\w.])',msg,re.I|re.S),msg
   assert re.search(r'but\s+(?:actual\s+)?(?:was\s*)?:?\s*<?'+re.escape(contract['actual'])+r'(?![\w.])',msg,re.I|re.S),msg
def receive(archive,api_file,expected_file,out):
 b=Path(archive).read_bytes();api=json.loads(Path(api_file).read_text());e=json.loads(Path(expected_file).read_text())
 r=api['run'];jobs=api['jobs']['jobs'];a=api['artifact']
 assert r['id']==e['run'] and r['head_sha']==e['validation'] and r['run_attempt']==r['run_number']==1 and r['status']=='completed'
 assert r['path']==e['workflow'] and r['event']=='push'
 assert api['jobs']['total_count']==len(jobs)==1 and jobs[0]['id']==e['job'] and jobs[0]['run_attempt']==1
 assert jobs[0]['run_id']==e['run'] and jobs[0]['head_sha']==e['validation'] and jobs[0]['status']=='completed'
 for key,sha_key,tree_key,parent in [('source_commit','source','tree','parent'),('validation_commit','validation','validation_tree','source')]:
  c=api[key];assert c['sha']==e[sha_key] and c['tree']['sha']==e[tree_key] and [p['sha'] for p in c['parents']]==[e[parent]]
 assert api['source_compare']['status']=='ahead' and api['source_compare']['ahead_by']==1
 assert {f['filename']:f['status'] for f in api['source_compare']['files']}==e['source_files']
 assert api['validation_compare']['status']=='ahead' and api['validation_compare']['ahead_by']==1
 assert [(f['filename'],f['status']) for f in api['validation_compare']['files']]==[(e['workflow'],'added')]
 assert api['artifacts']['total_count']==1 and len(api['artifacts']['artifacts'])==1
 assert api['artifacts']['artifacts'][0]==a and not a['expired']
 assert a['id']==e['artifact'] and a['name']==e['artifact_name']
 assert a['workflow_run']['id']==e['run'] and a['workflow_run']['head_sha']==e['validation']
 assert len(b)==a['size_in_bytes']==e['artifact_bytes'] and a['digest']=='sha256:'+sha(b)=='sha256:'+e['artifact_sha256']
 files=archive_files(archive)
 binding=json.loads(files['BINDING.json'])
 for k,v in {'SOURCE':e['source'],'SOURCE_TREE':e['tree'],'validation':e['validation'],'validation_tree':e['validation_tree'],'WORKFLOW':e['workflow'],'workflow_blob':e['workflow_blob']}.items():assert binding[k]==v,k
 assert int(binding['GITHUB_RUN_ID'])==e['run'] and int(binding['GITHUB_RUN_ATTEMPT'])==int(binding['GITHUB_RUN_NUMBER'])==1
 assert gitsha(files['WORKFLOW.yml'])==e['workflow_blob']
 for p,h in e['frozen_protocol_files'].items():assert sha(files['protocol/'+p])==h,p
 p=json.loads(files['protocol/PROTOCOL.json']);assert p['base']==e['parent']
 for label in ['initial','final']:
  assert files[label+'/TRACKED.diff']==b''
  for path,h in p['bindings'].items():assert sha(files[label+'/source/'+path])==h,path
 for path,h in p['protected_original_bindings'].items():
  assert sha(files['protected-originals/'+path])==h,path
  if path not in p['authorized_production_paths']+['docs/card-sdk-language-reference.md']:
   assert files['initial/source/'+path]==files['protected-originals/'+path],path
 forms=['normal',*p['mutants']]
 assert json.loads(files['COMPILE-FIRST.json'])=={'all_forms_compiled':True,'forms':forms}
 for name in forms:
  assert files['compile-'+name+'/exit.txt'].strip()==b'0','Compiler failure is never negative evidence'
  log=files['compile-'+name+'/output.log'];assert b'BUILD SUCCESSFUL' in log and b':rules-engine:compileTestKotlin' in log
  for path,h in p['normal_forms'].items():
   expected=p['mutants'][name]['sha256'] if name!='normal' and p['mutants'][name]['path']==path else h
   assert sha(files['forms/'+name+'/'+path])==expected,(name,path)
  if name=='normal':assert files['forms/normal/TRACKED.diff']==b''
 reports={}
 for stage in ['normal',*['negative-'+n for n in p['mutants']],'restored-normal']:
  name=stage.removeprefix('negative-');m=p['mutants'].get(name)
  cases={m['test_class']:p['cases'][m['test_class']]} if m else p['cases']
  failed=[(m['test_class'],n) for n in m['failures']] if m else []
  rows=rows_from_files(files,stage);check_rows(rows,cases,failed,e['negative_contracts'][name] if m else None)
  rc=int(files[stage+'/exit.txt']);assert (rc!=0)==bool(failed)
  saved=json.loads(files[stage+'-ROWS.json']);assert saved['exit']==rc
  canonical=lambda rows:sorted(json.dumps(r,sort_keys=True) for r in rows)
  assert canonical(saved['rows'])==canonical(rows),'full raw payloads agree'
  reports[stage]={'rows':rows,'exit':rc,'failed':len(failed)}
 restoration=json.loads(files['RESTORATION.json']);assert restoration['normal_restored'] and restoration['tracked_diff']==restoration['status']==''
 result=json.loads(files['RESULT.json']);assert result=={'state':'EARLY_GATES_PASSED_ORIGINAL_RECEIVING_AND_FULL_COMPATIBILITY_REQUIRED','accepted':False,'normal_rows':83,'compiled_forms':6,'negative_forms':5,'restored_normal':True}
 assert r['conclusion']==jobs[0]['conclusion']=='success'
 report={'state':'ORIGINAL_EARLY_GATES_VERIFIED_NOT_FULL_ACCEPTANCE','source':e['source'],'validation':e['validation'],'run':r['id'],'job':jobs[0]['id'],
  'artifact':a['id'],'archive_bytes':len(b),'archive_sha256':sha(b),'manifest_entries':len(files)-1,'bindings':len(p['bindings']),
  'compiled_forms':6,'normal':83,'restored':83,'negative_witnesses':sum(x['failed'] for x in reports.values()),'reports':reports,'accepted':False,
  'remaining':'Immutable receipt/archive/readback; full same-source396cards19239baseline43exactskips+83additions64client and original receiving.'}
 Path(out).write_text(json.dumps(report,indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='reports'}))
if __name__=='__main__':receive(*sys.argv[1:])
