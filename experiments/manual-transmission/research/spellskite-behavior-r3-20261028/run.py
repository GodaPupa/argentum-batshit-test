from pathlib import Path
import json,os,subprocess,hashlib,time,shutil
from audit import audit
D=Path(__file__).resolve().parent;R=Path.cwd();O=R/'manual-spellskite-behavior-r3-evidence';P=json.loads((D/'PROTOCOL.json').read_text());H=lambda b:hashlib.sha256(b).hexdigest();git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
T=R/P['test'];N=T.read_bytes();forms={'normal':N}
for n,m in P['mutants'].items():
 assert N.count(m['old'].encode())==m['count'];forms[n]=m['new'].encode().join(N.rsplit(m['old'].encode(),1));assert H(forms[n])==m['sha256']
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def bind():
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE'];assert git('rev-parse',os.environ['SOURCE']+'^')==P['base'];assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW'];assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==sorted(P['source_delta']);assert git('rev-parse','refs/remotes/origin/main')==P['main'];assert not git('status','--porcelain','--untracked-files=no');assert H(T.read_bytes())==P['normal_sha256']
 for p,h in {**P['protected'],**P['bindings']}.items():
  b=(R/p).read_bytes();assert H(b)==h,p;save(O/'source'/p,b)
try:
 O.mkdir();bind()
 for n in ['PROTOCOL.json','run.py','audit.py','validation.just','SOURCE_REVIEW.json','SYNTHETIC_CHECKS.json']:save(O/'protocol'/n,(D/n).read_bytes())
 save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes());save(O/'BINDING.json',json.dumps(dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))).encode());assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
 gates={}
 for stage in P['stages']:
  form=forms['normal' if stage in ['normal','restored'] else stage.removeprefix('compile-')];T.write_bytes(form);d=O/stage;d.mkdir();save(d/'FORM.kt',form)
  report=R/'rules-engine/build/test-results/test'
  if not stage.startswith('compile-'):shutil.rmtree(report,ignore_errors=True)
  started=time.time_ns()
  with (d/'output.log').open('wb') as f:rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(D/'validation.just'),'compile' if stage.startswith('compile-') else 'targeted'],stdout=f,stderr=subprocess.STDOUT).returncode
  save(d/'RESULT.json',json.dumps(dict(exit=rc,started_ns=started,ended_ns=time.time_ns(),form_sha256=H(form))).encode())
  if not stage.startswith('compile-') and report.exists():
   for p in report.glob('TEST-*.xml'):save(d/'junit'/p.name,p.read_bytes())
  gates[stage]=audit(d,stage);save(O/'GATES.json',json.dumps(gates,indent=2).encode())
 save(O/'RESULT.json',b'{"state":"BEHAVIORAL_GATES_COMPLETE_ORIGINAL_RECEIVING_REQUIRED"}')
except Exception as e:
 O.mkdir(exist_ok=True);save(O/'FAILURE.txt',(type(e).__name__+': '+str(e)).encode());raise
finally:
 T.write_bytes(N)
 try:bind();save(O/'RESTORATION.json',b'{"restored":true}')
 finally:save(O/'MANIFEST.json',json.dumps({str(f.relative_to(O)):H(f.read_bytes()) for f in sorted(O.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'},indent=2).encode())
