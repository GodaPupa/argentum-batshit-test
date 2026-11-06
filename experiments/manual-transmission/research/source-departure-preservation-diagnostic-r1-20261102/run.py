from pathlib import Path
import os,json,subprocess,hashlib,shutil,traceback,xml.etree.ElementTree as ET
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
 assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==sorted(P['source_delta'])
 assert not git('status','--porcelain','--untracked-files=no')
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 for path,h in P['bindings'].items():
  b=(R/path).read_bytes();assert sha(b)==h,path;save(O/label/'source'/path,b)
 save(O/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 assert sha((R/P['test']).read_bytes())==P['test_sha256']
 save(O/label/'TEST.kt',(R/P['test']).read_bytes())
 assert sha((R/P['preservation_test']).read_bytes())==P['preservation_test_sha256']
 save(O/label/'PRESERVATION.kt',(R/P['preservation_test']).read_bytes())
def stage(name,recipe):
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:p.unlink()
 folder=O/name;folder.mkdir()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(H/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:save(folder/'junit'/p.relative_to(R),p.read_bytes())
 return rc
originals={path:(R/path).read_bytes() for path in P['forms']}
def form(name):
 for path,spec in P['forms'].items():
  b=originals[path] if name=='original' else (R/spec['draft_path']).read_bytes()
  assert sha(b)==spec[name+'_sha256'],path
  (R/path).write_bytes(b)
 for path in P['bindings']:
  expected=P['forms'][path][name+'_sha256'] if path in P['forms'] else P['bindings'][path]
  assert sha((R/path).read_bytes())==expected,path
 save(O/'forms'/name/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 for path in P['forms']:save(O/'forms'/name/path,(R/path).read_bytes())
try:
 O.mkdir();bind('initial')
 js(O/'BINDING.json',{k:os.environ[k] for k in ['SOURCE','SOURCE_TREE','WORKFLOW','GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT','GITHUB_RUN_NUMBER']}|{'validation':git('rev-parse','HEAD'),'validation_tree':git('rev-parse','HEAD^{tree}'),'workflow_blob':git('hash-object',os.environ['WORKFLOW'])})
 assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
 for p in H.rglob('*'):
  if p.is_file():save(O/'protocol'/p.relative_to(H),p.read_bytes())
 save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes())
 for name in ['original','draft']:
  form(name);assert stage('compile-'+name,'compile')==0,'All source forms must compile before behavioral observation'
 observations={}
 for name in ['original','draft']:
  form(name);rc=stage('observe-'+name,'observe');rows=[]
  for p in (O/('observe-'+name)/'junit').rglob('TEST-*.xml'):
   for t in ET.parse(p).getroot().iter('testcase'):rows.append({'class':t.get('classname'),'name':t.get('name'),'failure':[f.attrib|{'text':f.text} for f in t.findall('failure')],'errors':len(t.findall('error')),'skips':len(t.findall('skipped'))})
  js(O/('OBSERVATION-'+name+'.json'),{'exit':rc,'rows':rows,'accepted':False,'behavioral_interpretation_pending_original_receiving':True})
  assert len(rows)==10 and {x['name'] for x in rows}==set(P['cases']),rows
  assert all(not x['errors'] and not x['skips'] for x in rows)
  observations[name]={'exit':rc,'rows':len(rows),'failed':sum(bool(x['failure']) for x in rows)}
 form('original');bind('final');js(O/'RESULT.json',{'state':'PRESERVATION_DIAGNOSTIC_OBSERVED_NOT_ACCEPTED','production_changed':False,'observations':observations})

except Exception:
 O.mkdir(exist_ok=True);(O/'FAILURE.txt').write_text(traceback.format_exc());raise
finally:
 for path,b in originals.items():(R/path).write_bytes(b)
 js(O/'RESTORATION.json',{'original_restored':all((R/path).read_bytes()==b for path,b in originals.items()),'tracked_diff':git('diff','--'),'status':git('status','--porcelain','--untracked-files=no')})
 js(O/'MANIFEST.json',{str(p.relative_to(O)):sha(p.read_bytes()) for p in sorted(O.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'})
