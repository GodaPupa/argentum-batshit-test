from pathlib import Path
from collections import Counter
import os,json,subprocess,hashlib,traceback,xml.etree.ElementTree as ET,re
R=Path.cwd();H=Path(__file__).resolve().parent;P=json.loads((H/'PROTOCOL.json').read_text());O=R/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,d):save(p,(json.dumps(d,indent=2)+'\n').encode())
def bind(label):
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==P['source_delta']
 assert not git('status','--porcelain','--untracked-files=no')
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 for path,h in P['bindings'].items():
  b=(R/path).read_bytes();assert sha(b)==h,path;save(O/label/'source'/path,b)
 for path,h in P['protected_original_bindings'].items():
  original=subprocess.check_output(['git','show',P['originals_base']+':'+path]);assert sha(original)==h,path
  save(O/'protected-originals'/path,original)
  if path not in P['authorized_production_paths']+['docs/card-sdk-language-reference.md']:assert (R/path).read_bytes()==original,path
 save(O/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
def stage(name,recipe):
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:p.unlink()
 folder=O/name;folder.mkdir()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(H/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,
   env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:save(folder/'junit'/p.relative_to(R),p.read_bytes())
 return rc
normal={path:(R/path).read_bytes() for path in P['normal_forms']}
def form(name):
 for path,b in normal.items():(R/path).write_bytes(b)
 if name!='normal':
  m=P['mutants'][name];b=(R/m['form_path']).read_bytes();assert sha(b)==m['sha256'];(R/m['path']).write_bytes(b)
 for path,h in P['bindings'].items():
  expected=P['mutants'][name]['sha256'] if name!='normal' and path==P['mutants'][name]['path'] else h
  assert sha((R/path).read_bytes())==expected,path
 save(O/'forms'/name/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 for path in normal:save(O/'forms'/name/path,(R/path).read_bytes())
def report(stage_name,rc,classes,failed,m=None):
 rows=[]
 for p in sorted((O/stage_name/'junit').rglob('TEST-*.xml')):
  root=ET.parse(p).getroot()
  assert root.tag=='testsuite'
  cases=list(root.iter('testcase'))
  assert int(root.get('tests'))==len(cases)
  assert int(root.get('failures'))==sum(bool(t.findall('failure')) for t in cases)
  assert int(root.get('errors'))==sum(bool(t.findall('error')) for t in cases)
  assert int(root.get('skipped'))==sum(bool(t.findall('skipped')) for t in cases)
  for t in cases:rows.append({'class':t.get('classname'),'name':t.get('name'),'failure':[f.attrib|{'text':f.text} for f in t.findall('failure')],
   'errors':[f.attrib|{'text':f.text} for f in t.findall('error')],'skips':[f.attrib|{'text':f.text} for f in t.findall('skipped')]})
 js(O/(stage_name+'-ROWS.json'),{'exit':rc,'rows':rows})
 expected=Counter((c,n) for c in classes for n in P['cases'][c])
 assert Counter((r['class'],r['name']) for r in rows)==expected,'exact identities/multiplicities'
 assert all(not r['errors'] and not r['skips'] for r in rows),'No errors or skips'
 observed=Counter((r['class'],r['name']) for r in rows if r['failure'])
 assert observed==Counter(failed),'exact declared negative failures only'
 assert (rc!=0)==bool(failed),'exit versus reports'
 if m:
  for r in rows:
   if not r['failure']:continue
   assert len(r['failure'])==1
   f=r['failure'][0];msg=f.get('message','');body=f.get('text') or ''
   assert 'Assertion' in f.get('type','') or 'Assertion' in body,'behavioral assertion, not compiler/runtime error'
   assert re.search(re.escape(m['assertion_file'])+r':\d+',body),'exact assertion location'
   assert re.search(r'expected.*?'+re.escape(m['expected']),msg,re.I|re.S),msg
   assert re.search(r'but.*?'+re.escape(m['actual']),msg,re.I|re.S),msg
 js(O/(stage_name+'-CHECK.json'),{'verified':True,'rows':len(rows),'failures':len(observed),'expected_failures':failed})
try:
 O.mkdir();bind('initial')
 assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
 js(O/'BINDING.json',{k:os.environ[k] for k in ['SOURCE','SOURCE_TREE','WORKFLOW','GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT','GITHUB_RUN_NUMBER']}|
  {'validation':git('rev-parse','HEAD'),'validation_tree':git('rev-parse','HEAD^{tree}'),'workflow_blob':git('hash-object',os.environ['WORKFLOW'])})
 for p in H.rglob('*'):
  if p.is_file():save(O/'protocol'/p.relative_to(H),p.read_bytes())
 save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes())
 for name in ['normal',*P['mutants']]:
  form(name);assert stage('compile-'+name,'compile')==0,'Every normal/mutant must compile before behavior'
 js(O/'COMPILE-FIRST.json',{'all_forms_compiled':True,'forms':['normal',*P['mutants']]})
 form('normal');rc=stage('normal','normal');report('normal',rc,list(P['cases']),[])
 for name,m in P['mutants'].items():
  form(name);rc=stage('negative-'+name,name)
  report('negative-'+name,rc,[m['test_class']],[(m['test_class'],n) for n in m['failures']],m)
 form('normal');rc=stage('restored-normal','normal');report('restored-normal',rc,list(P['cases']),[])
 bind('final');js(O/'RESULT.json',{'state':'EARLY_GATES_PASSED_ORIGINAL_RECEIVING_AND_FULL_COMPATIBILITY_REQUIRED','accepted':False,
  'normal_rows':P['expected_normal_rows'],'compiled_forms':6,'negative_forms':5,'restored_normal':True})
except Exception:
 O.mkdir(exist_ok=True);(O/'FAILURE.txt').write_text(traceback.format_exc());raise
finally:
 for path,b in normal.items():(R/path).write_bytes(b)
 js(O/'RESTORATION.json',{'normal_restored':all((R/path).read_bytes()==b for path,b in normal.items()),
  'tracked_diff':git('diff','--'),'status':git('status','--porcelain','--untracked-files=no')})
 js(O/'MANIFEST.json',{str(p.relative_to(O)):sha(p.read_bytes()) for p in sorted(O.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'})
