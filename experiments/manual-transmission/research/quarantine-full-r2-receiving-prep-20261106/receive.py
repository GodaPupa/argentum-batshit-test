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
   assert f.get('type')=='org.opentest4j.AssertionFailedError','exact behavioral assertion type'
   w=contract.get('witnesses',{}).get('\t'.join([r['class'],r['name']]))
   if w:
    assert re.search(re.escape(w['file'])+':'+str(w['line'])+r'\b',body),'exact assertion location'
    if w['kind']=='trace-null-empty':
     import difflib
     assert msg.startswith('org.opentest4j.AssertionFailedError: Values differed at keys entities\nexpected:<')
     values=msg.split('expected:<',1)[1].split('> but was:<');assert len(values)==2 and values[1].endswith('>')
     expected,actual=values[0],values[1][:-1]
     diffs=[(op,expected[i:j],actual[k:l],expected[max(0,i-45):j+15]) for op,i,j,k,l in difflib.SequenceMatcher(None,expected,actual,autojunk=False).get_opcodes() if op!='equal']
     assert len(diffs)==1 and diffs[0][:3]==('replace','null','[]') and 'castChoices' in diffs[0][3],diffs
     continue
    expected,actual=w['expected'],w['actual']
   else:
    assert re.search(re.escape(contract['assertion_file'])+':'+str(contract['assertion_line'])+r'\b',body),'exact assertion location'
    expected,actual=contract['expected'],contract['actual']
   assert re.search(r'expected\s*:?\s*<?'+re.escape(expected)+r'(?![\w.])',msg,re.I|re.S),msg
   assert re.search(r'but\s+(?:actual\s+)?(?:was\s*)?:?\s*<?'+re.escape(actual)+r'(?![\w.])',msg,re.I|re.S),msg
def verify_envelope(files, expected):
 """Validate the recovery wrapper without altering the original runner manifest."""
 envelope = json.loads(files['RECOVERY_EVENT.json'])
 exact = {key: expected[key] for key in ('epoch','branch','event','event_tree','first_parent',
          'first_tree','validation','validation_tree','source','workflow','protocol_blob','run')}
 exact.update(source_tree=expected['tree'], event_workflow_blob=expected['workflow_blob'],
              validation_workflow_blob=expected['workflow_blob'], run_number=1, attempt=1)
 assert envelope == exact, 'event and detached validation identity'
 assert all(n in ('MANIFEST.json','RECOVERY_EVENT.json') or n.startswith('qualification/') for n in files)
 inner = {n[len('qualification/'):]:b for n,b in files.items() if n.startswith('qualification/')}
 assert json.loads(inner['MANIFEST.json']) == {n:sha(b) for n,b in inner.items() if n!='MANIFEST.json'}, 'original qualification manifest'
 return inner

def verify_api(api,e,b):
 r=api['run'];jobs=api['jobs']['jobs'];a=api['artifact']
 assert r['id']==e['run'] and r['head_sha']==e['event'] and r['run_attempt']==r['run_number']==1 and r['status']=='completed'
 assert r['repository']['full_name']==e['repository'] and r['head_branch']==e['branch']
 assert r['path']==e['workflow'] and r['event']=='push'
 assert api['jobs']['total_count']==len(jobs)==1 and jobs[0]['id']==e['job'] and jobs[0]['run_attempt']==1
 assert jobs[0]['run_id']==e['run'] and jobs[0]['head_sha']==e['event'] and jobs[0]['status']=='completed'
 for k,s,t,parents in [('source_commit','source','tree',[e['parent']]),
                       ('validation_commit','validation','validation_tree',[e['source']]),
                       ('event_commit','event','event_tree',[e['first_parent'],e['validation']])]:
  commit=api[k]
  assert commit['sha']==e[s] and commit['tree']['sha']==e[t] and [x['sha'] for x in commit['parents']]==parents
 assert api['first_commit']['sha']==e['first_parent'] and api['first_commit']['tree']['sha']==e['first_tree']
 assert api['source_compare']['status']=='ahead' and api['source_compare']['ahead_by']==1
 assert {f['filename']:f['status'] for f in api['source_compare']['files']}==e['source_files']
 assert api['validation_compare']['status']=='ahead' and api['validation_compare']['ahead_by']==1
 for delta in ('validation_compare','event_first_compare'):
  assert [(f['filename'],f['status'],f['sha']) for f in api[delta]['files']]==[(e['workflow'],'added',e['workflow_blob'])]
 assert api['artifacts']['total_count']==len(api['artifacts']['artifacts'])==1
 assert api['artifacts']['artifacts'][0]==a and not a['expired']
 assert a['id']==e['artifact'] and a['name']==e['artifact_name']
 assert a['workflow_run']['id']==e['run'] and a['workflow_run']['head_sha']==e['event']
 assert len(b)==a['size_in_bytes']==e['artifact_bytes'] and a['digest']=='sha256:'+sha(b)=='sha256:'+e['artifact_sha256']

def receive(archive,api_file,expected_file,out):
 b=Path(archive).read_bytes();api=json.loads(Path(api_file).read_text());e=json.loads(Path(expected_file).read_text())
 verify_api(api,e,b)
 r=api['run'];jobs=api['jobs']['jobs'];a=api['artifact']
 files=verify_envelope(archive_files(archive),e)
 binding=json.loads(files['BINDING.json'])
 for k,v in {'SOURCE':e['source'],'SOURCE_TREE':e['tree'],'validation':e['validation'],'validation_tree':e['validation_tree'],'WORKFLOW':e['workflow'],'workflow_blob':e['workflow_blob']}.items():assert binding[k]==v,k
 assert int(binding['GITHUB_RUN_ID'])==e['run'] and int(binding['GITHUB_RUN_ATTEMPT'])==int(binding['GITHUB_RUN_NUMBER'])==1
 assert gitsha(files['WORKFLOW.yml'])==e['workflow_blob']
 assert {n.removeprefix('protocol/') for n in files if n.startswith('protocol/')}==set(e['frozen_protocol_files'])
 for p,h in e['frozen_protocol_files'].items():assert sha(files['protocol/'+p])==h,p
 p=json.loads(files['protocol/PROTOCOL.json']);assert p['base']==e['parent']
 assert e['negative_contracts']==p['mutants']
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
  cases={c:p['cases'][c] for c in m['test_classes']} if m else p['cases']
  failed=m['failed_identities'] if m else []
  rows=rows_from_files(files,stage);check_rows(rows,cases,failed,e['negative_contracts'][name] if m else None)
  rc=int(files[stage+'/exit.txt']);assert (rc!=0)==bool(failed)
  saved=json.loads(files[stage+'-ROWS.json']);assert saved['exit']==rc
  canonical=lambda rows:sorted(json.dumps(r,sort_keys=True) for r in rows)
  assert canonical(saved['rows'])==canonical(rows),'full raw payloads agree'
  reports[stage]={'rows':rows,'exit':rc,'failed':len(failed)}
 restoration=json.loads(files['RESTORATION.json']);assert restoration['normal_restored'] and restoration['tracked_diff']==restoration['status']==''
 result=json.loads(files['RESULT.json']);assert result=={'state':'EARLY_GATES_PASSED_ORIGINAL_RECEIVING_AND_FULL_COMPATIBILITY_REQUIRED','accepted':False,'normal_rows':115,'compiled_forms':7,'negative_forms':6,'restored_normal':True}
 assert r['conclusion']==jobs[0]['conclusion']=='success'
 report={'state':'ORIGINAL_EARLY_GATES_VERIFIED_NOT_FULL_ACCEPTANCE','source':e['source'],'validation':e['validation'],'event':e['event'],'run':r['id'],'job':jobs[0]['id'],
  'artifact':a['id'],'archive_bytes':len(b),'archive_sha256':sha(b),'manifest_entries':len(files)-1,'bindings':len(p['bindings']),
  'compiled_forms':7,'normal':115,'restored':115,'negative_witnesses':sum(x['failed'] for x in reports.values()),'reports':reports,'accepted':False,
  'remaining':'Immutable receipt/archive/readback; full same-source396cards19239baseline43exactskips+83existing+8additions64client and original receiving.'}
 Path(out).write_text(json.dumps(report,indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='reports'}))
if __name__=='__main__':receive(*sys.argv[1:])
