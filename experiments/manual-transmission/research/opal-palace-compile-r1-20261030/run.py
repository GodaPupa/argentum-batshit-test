"""Compile the bounded normal form and export a candidate snapshot; no behavioral admission."""
from pathlib import Path
import hashlib,json,os,re,subprocess,traceback
from audit import verify
HERE=Path(__file__).resolve().parent
ROOT=Path.cwd()
P=json.loads((HERE/'PROTOCOL.json').read_text())
OUT=ROOT/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
def bind(label):
 assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('rev-parse',P['base']+'^{tree}')==P['base_tree']
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==P['source_delta']
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 assert not git('status','--porcelain','--untracked-files=no')
 for path,digest in P['bindings'].items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 for path,digest in P['git_bindings'].items():
  assert git('hash-object',path)==digest,path;save(OUT/label/'source'/path,(ROOT/path).read_bytes())
 for path,digest in P['original_bindings'].items():
  b=subprocess.check_output(['git','show',P['base']+':'+path]);assert sha(b)==digest,path
  save(OUT/label/'original'/path,b)
 trees={}
 for path in P['unchanged_modules']:
  before=git('rev-parse',P['base']+':'+path);assert before==git('rev-parse',os.environ['SOURCE']+':'+path),path
  trees[path]=before
 js(OUT/label/'UNCHANGED_TREES.json',trees)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  js(OUT/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER'])))
def stage(name,recipe):
 folder=OUT/name;folder.mkdir(parents=True)
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:f.unlink()
 env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env=env).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
 assert rc==0,(name,rc)
 log=(folder/'output.log').read_text();assert 'BUILD SUCCESSFUL' in log
 assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
def main():
 OUT.mkdir();bind('initial')
 stage('compile-normal','compile')
 stage('snapshot-export','snapshot-export')
 for name in ['OpalPalace.block.json','C13.candidate.json']:
  save(OUT/'snapshot'/name,(ROOT/'mtg-sets/build/opal-palace-candidate'/name).read_bytes())
 bind('final')
 raw={str(f.relative_to(OUT)):f.read_bytes() for f in OUT.rglob('*') if f.is_file()}
 js(OUT/'AUDIT.json',verify(raw,P))
 js(OUT/'RESULT.json',dict(state='NORMAL_COMPILE_AND_SNAPSHOT_CANDIDATE_ONLY',feature_admitted=False,card_admitted=False))
if __name__=='__main__':
 try:main()
 except Exception:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:bind('final')
  finally:
   OUT.mkdir(exist_ok=True)
   js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
