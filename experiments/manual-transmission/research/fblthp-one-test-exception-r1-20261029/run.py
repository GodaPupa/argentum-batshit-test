"""Exact authorized test successor: compile all forms, exercise controls, full dependent gates."""
from pathlib import Path
import os,json,subprocess,traceback
from audit import P,sha,inventory,junit,client
from report_audit import compile_gate,reports
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent;OUT=ROOT/P['evidence']
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def bind(label):
 source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',source+'^')==P['base'] and git('rev-parse','HEAD^')==source
 assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 assert git('diff','--name-only',P['base'],source).splitlines()==sorted(P['source_delta'])
 assert not git('status','--porcelain','--untracked-files=no')
 approved='experiments/manual-transmission/research/fblthp-dependent-received-r1-20261029/'
 original=subprocess.check_output(['git','show',P['base']+':'+P['test_path']])
 archived=subprocess.check_output(['git','show',P['base']+':'+approved+'ORIGINAL_BECOMES_TARGET_TEST.kt'])
 candidate=subprocess.check_output(['git','show',P['base']+':'+approved+'PROPOSED_BECOMES_TARGET_TEST.kt'])
 assert original==archived and sha(original)==P['original_test_sha256']
 assert (ROOT/P['test_path']).read_bytes()==candidate and sha(candidate)==P['proposed_test_sha256']
 for path,digest in P['bindings'].items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 for path,blob in P['git_bindings'].items():
  b=(ROOT/path).read_bytes();assert git('hash-object',path)==blob,path;save(OUT/label/'source'/path,b)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  for n in ['ORIGINAL_BECOMES_TARGET_TEST.kt','PROPOSED_BECOMES_TARGET_TEST.kt','NEXT_ADMISSION.json']:
   save(OUT/'approved'/n,subprocess.check_output(['git','show',P['base']+':'+approved+n]))
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']),web_client_tree=git('rev-parse',source+':web-client'))
  assert b['web_client_tree']==git('rev-parse',P['base']+':web-client')
  assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
def mutate(name):
 m=P['mutants'][name];f=ROOT/m['path'];s=f.read_text();assert sha(s.encode())==P['bindings'][m['path']]
 assert s.count(m['old'])==m['occurrences'];s=s.replace(m['old'],m['new']);assert sha(s.encode())==m['sha256'];f.write_text(s)
 assert git('diff','--name-only')==m['path']
 save(OUT/'forms'/name/m['path'],s.encode())
def restore():
 for path in P['mutation_paths']:save(ROOT/path,subprocess.check_output(['git','show',os.environ['SOURCE']+':'+path]))
def stage(name,recipe,kind,expected=None,failures=None):
 folder=OUT/name;folder.mkdir(parents=True)
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:f.unlink()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
 result=compile_gate(folder) if kind=='compile' else reports(folder,expected,failures) if kind=='focused' else junit(folder,expected)
 (folder/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
def client_stage():
 folder=OUT/'client';folder.mkdir();contract=json.loads((ROOT/P['client_contract']).read_text())
 commands={'npm-ci':['npm','ci'],'typecheck':['npm','run','typecheck'],'test':['npm','test','--','--run',*[p.removeprefix('web-client/') for p in contract['client_cases']],'--reporter=json','--outputFile='+str(folder/'vitest.json')]}
 for name,cmd in commands.items():
  with (folder/(name+'.log')).open('wb') as log:rc=subprocess.run(cmd,cwd=ROOT/'web-client',stdout=log,stderr=subprocess.STDOUT).returncode
  (folder/(name+'-exit.txt')).write_text(str(rc)+'\n');assert rc==0,name
 (folder/'SUMMARY.json').write_text(json.dumps(client(folder,contract),indent=2)+'\n')
def main():
 OUT.mkdir();bind('initial');I=inventory(ROOT)
 assert sha(json.dumps(I,sort_keys=True,separators=(',',':')).encode())==P['inventory_sha256']
 save(OUT/'INVENTORY.json',(json.dumps(I,indent=2)+'\n').encode())
 for script in ['check_audit.py','check_reports.py']:
  raw=subprocess.check_output(['python3',str(HERE/script)]);save(OUT/(script+'.json'),raw)
 stage('compile-normal','compile','compile')
 for name in P['mutants']:
  try:mutate(name);stage('compile-'+name,'compile-mutant','compile')
  finally:restore()
 bind('compiled-restored');stage('focused-normal','focused','focused',P['focused_cases'])
 for name,m in P['mutants'].items():
  try:mutate(name);stage(name,'axes','focused',P['axes_cases'],m['witnesses'])
  finally:restore()
 bind('mutants-restored');stage('focused-restored','focused','focused',P['focused_cases'])
 for name in ['cards','ordinary']:stage(name,name,'ordinary',I[name])
 client_stage();bind('final');save(OUT/'RESULT.json',b'{"state":"ONE_TEST_DEPENDENT_CLIENT_PASSED_ORIGINAL_RECEIVING_PENDING","feature_admitted":false,"card_admitted":false}\n')
if __name__=='__main__':
 try:main()
 except Exception:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:restore();bind('final')
  finally:
   OUT.mkdir(exist_ok=True);manifest={str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'};(OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
