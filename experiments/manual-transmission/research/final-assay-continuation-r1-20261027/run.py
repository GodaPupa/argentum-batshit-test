"""Run only unfinished Assay gates on the unchanged coherent R2 runtime source."""
from pathlib import Path
import json,os,subprocess,hashlib
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent;OUT=ROOT/'manual-final-assay-r1-evidence'
P=json.loads((HERE/'PROTOCOL.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def bind(label):
 source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',source+'^')==P['base'] and git('rev-parse','HEAD^')==source
 assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
 assert git('diff','--name-only',P['base'],source).splitlines()==sorted(P['source_delta'])
 assert git('rev-parse',P['runtime_source']+'^{tree}')==P['runtime_tree']
 assert not git('diff','--name-only',P['runtime_source'],source,'--',':(exclude)experiments/manual-transmission/research')
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 assert not git('status','--porcelain','--untracked-files=no')
 for path,digest in {**P['bindings'],**P['protected']}.items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  for name,digest in P['prior_receipt_bindings'].items():
   b=(ROOT/P['prior_receipt_path']/name).read_bytes();assert sha(b)==digest;save(OUT/'prior-receipt'/name,b)
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']),runtime_source=P['runtime_source'],runtime_tree=P['runtime_tree'])
  assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
def main():
 OUT.mkdir(exist_ok=True);bind('initial')
 subprocess.run(['python3',str(HERE/'check_assay_controls.py')],check=True)
 folder=OUT/'assay';folder.mkdir()
 for recipe,log,exitfile in [('assay-build','build.log','build-exit.txt'),('assay','gate.log','gate-exit.txt'),('assay-differential','differential.log','differential-exit.txt')]:
  with (folder/log).open('wb') as f:rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=f,stderr=subprocess.STDOUT).returncode
  (folder/exitfile).write_text(str(rc));assert rc==0,recipe
  if recipe=='assay':
   b=(Path.home()/'.cache/scryfall/_bulk-oracle-cards.jsonl.gz').read_bytes();save(folder/'oracle-bulk-corpus.jsonl.gz',b);(folder/'oracle-bulk-sha256.txt').write_text(sha(b));(folder/'oracle-bulk-bytes.txt').write_text(str(len(b)));(folder/'corpus-capture-exit.txt').write_text('0')
 subprocess.run(['python3',str(HERE/'check_assay.py')],env=dict(os.environ,RECORD=str(HERE),EVIDENCE=str(OUT)),check=True)
if __name__=='__main__':
 try:main()
 except Exception as e:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
 finally:
  try:bind('final')
  finally:
   manifest={str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'};(OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
