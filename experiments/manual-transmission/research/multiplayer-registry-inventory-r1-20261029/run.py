"""Seedless registry inventory, no game initializer or pilot is imported."""
from pathlib import Path
import os,json,hashlib,subprocess,shutil,traceback
H=Path(__file__).resolve().parent;R=Path.cwd();P=json.loads((H/'PROTOCOL.json').read_text());O=R/P['evidence'];sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
def bind(label):
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse','HEAD^')==os.environ['SOURCE'] and git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==sorted(P['source_delta'])
 assert git('rev-parse','refs/remotes/origin/main')==P['main'] and not git('status','--porcelain','--untracked-files=no')
 for path,h in P['bindings'].items():
  b=(R/path).read_bytes();assert sha(b)==h,path;save(O/label/'source'/path,b)
 save(O/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 if label=='initial':
  for f in H.rglob('*'):
   if f.is_file() and '__pycache__' not in f.parts:save(O/'protocol'/f.relative_to(H),f.read_bytes())
  save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes())
  js(O/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER'])))
def stage(name,recipe,env):
 d=O/name;d.mkdir(parents=True,exist_ok=True)
 with (d/'stdout.log').open('wb') as out,(d/'stderr.log').open('wb') as err:rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(H/'validation.just'),recipe],stdout=out,stderr=err,env=env).returncode
 (d/'exit.txt').write_text(str(rc)+'\n');return rc
def main():
 O.mkdir();bind('initial');assert stage('compile-normal','compile',os.environ.copy())==0
 env=os.environ.copy();env['MT_P2_AUDIT_INPUT']=str(H/'REQUEST.json');env['MT_P2_AUDIT_OUTPUT']=str(O/'registry/output')
 reportdir=R/'gym/build/test-results/test'
 if reportdir.exists():shutil.rmtree(reportdir) # generated reports only; force original new inventory reports
 rc=stage('registry','inventory',env)
 for f in reportdir.glob('*.xml'):save(O/'registry/junit'/f.name,f.read_bytes())
 generated=O/'registry/output/registry-report.json'
 if generated.exists():save(O/'registry/registry-report.json',generated.read_bytes());generated.unlink()
 assert rc==0
 from audit import audit
 raw={str(f.relative_to(O)):f.read_bytes() for f in O.rglob('*') if f.is_file()};result=audit(json.loads((H/'REQUEST.json').read_text()),raw);js(O/'COLLECTOR_INVENTORY.json',result)
 bind('final')
if __name__=='__main__':
 try:main()
 except Exception:O.mkdir(exist_ok=True);(O/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:bind('final')
  finally:
   O.mkdir(exist_ok=True);js(O/'MANIFEST.json',{str(f.relative_to(O)):sha(f.read_bytes()) for f in sorted(O.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
