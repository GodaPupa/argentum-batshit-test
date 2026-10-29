"""Compile-first card/ordinary dependent gates; all other admission gates remain required."""
from pathlib import Path
import os,json,subprocess,shutil,hashlib,traceback
from audit import P,sha,inventory,junit
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
 for path,digest in P['bindings'].items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 for path,blob in P['git_bindings'].items():
  b=(ROOT/path).read_bytes();assert git('hash-object',path)==blob,path;save(OUT/label/'source'/path,b)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))
  assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
def stage(name,expected=None):
 folder=OUT/name;folder.mkdir(parents=True)
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:f.unlink()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),name],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
 if name=='compile':
  import re
  text=(folder/'output.log').read_text();assert rc==0 and 'BUILD SUCCESSFUL' in text
  assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',text,re.M)
  result={'compiled':True}
 else:result=junit(folder,expected)
 (folder/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
def main():
 OUT.mkdir();bind('initial')
 I=inventory(ROOT);assert sha(json.dumps(I,sort_keys=True,separators=(',',':')).encode())==P['inventory_sha256']
 save(OUT/'INVENTORY.json',(json.dumps(I,indent=2)+'\n').encode())
 subprocess.run(['python3',str(HERE/'check_audit.py')],check=True)
 stage('compile')
 for name in ['cards','ordinary']:stage(name,I[name])
 bind('final');save(OUT/'RESULT.json',b'{"state":"DEPENDENT_REPORTS_PASSED_ORIGINAL_RECEIVING_PENDING"}\n')
if __name__=='__main__':
 try:main()
 except Exception:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:bind('final')
  finally:
   OUT.mkdir(exist_ok=True);manifest={str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'};(OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
