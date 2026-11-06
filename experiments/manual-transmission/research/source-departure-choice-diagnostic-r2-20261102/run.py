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
def stage(name):
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:p.unlink()
 folder=O/name;folder.mkdir()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(H/'validation.just'),name],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:save(folder/'junit'/p.relative_to(R),p.read_bytes())
 return rc
try:
 O.mkdir();bind('initial')
 js(O/'BINDING.json',{k:os.environ[k] for k in ['SOURCE','SOURCE_TREE','WORKFLOW','GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT','GITHUB_RUN_NUMBER'] }|{'validation':git('rev-parse','HEAD'),'validation_tree':git('rev-parse','HEAD^{tree}'),'workflow_blob':git('hash-object',os.environ['WORKFLOW'])})
 assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
 for p in H.iterdir():
  if p.is_file():save(O/'protocol'/p.name,p.read_bytes())
 save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes())
 assert stage('compile')==0,'Compilation must pass before original behavioral observation'
 rc=stage('observe');rows=[]
 for p in (O/'observe/junit').rglob('TEST-*.xml'):
  for t in ET.parse(p).getroot().iter('testcase'):rows.append({'class':t.get('classname'),'name':t.get('name'),'failure':[f.attrib|{'text':f.text} for f in t.findall('failure')],'errors':len(t.findall('error')),'skips':len(t.findall('skipped'))})
 assert len(rows)==8 and {x['name'] for x in rows}==set(P['cases'])
 assert all(not x['errors'] and not x['skips'] for x in rows)
 js(O/'OBSERVATION.json',{'exit':rc,'rows':rows,'accepted':False,'behavioral_interpretation_pending_original_receiving':True})
 bind('final');js(O/'RESULT.json',{'state':'ORIGINAL_DIAGNOSTIC_OBSERVED_NOT_ACCEPTED','production_changed':False})
except Exception:
 O.mkdir(exist_ok=True);(O/'FAILURE.txt').write_text(traceback.format_exc());raise
finally:
 js(O/'MANIFEST.json',{str(p.relative_to(O)):sha(p.read_bytes()) for p in sorted(O.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'})
