"""One coherent-source full compatibility run; no production mutation or admission."""
from pathlib import Path
import os,json,shutil,subprocess,hashlib
from audit import P,sha,inventory,junit,client
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent;OUT=ROOT/'manual-activation-payment-full-r1-evidence'
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def bind(label):
 source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',source+'^')==P['base'] and git('rev-parse','HEAD^')==source
 assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 assert git('diff','--name-only',P['base'],source).splitlines()==sorted(P['source_delta'])
 assert not git('diff','--name-only',P['accepted_source'],source,'--',':(exclude)experiments/manual-transmission/research')
 assert not git('status','--porcelain','--untracked-files=no')
 for path,digest in {**P['bindings'],**P['protected']}.items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 for path,blob in json.loads((ROOT/P['inventory']/'CLIENT64_CONTRACT.json').read_text())['source_blobs'].items():assert git('hash-object',path)==blob
 live='web-client/src/store/slices/liveInteraction.test.ts';assert sha((ROOT/live).read_bytes())==json.loads((ROOT/P['inventory']/'CLIENT64_CONTRACT.json').read_text())['new_client_suite_binding']
 assert not git('diff','--name-only',P['generator_source'],source,'--','mtgish-tooling')
 early=(ROOT/P['early_receipt']).read_bytes();assert sha(early)==P['early_receipt_sha256']
 assert json.loads(early)['state']=='RECEIVED_EARLY_PAYMENT_REPAIR_FULL_COMPATIBILITY_PENDING'
 save(OUT/label/'EARLY_RECEIPT.json',early)
 for proof in json.loads((HERE/'ASSAY_REUSE.json').read_text())['proof']:
  assert git('rev-parse',source+':'+proof['path'])==proof['candidate']==proof['accepted']
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  for f in (ROOT/P['inventory']).iterdir():
   if f.is_file():save(OUT/P['inventory']/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))
  assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
def command(args,log,cwd=ROOT):
 log.parent.mkdir(parents=True,exist_ok=True)
 with log.open('wb') as f:rc=subprocess.run(args,cwd=cwd,stdout=f,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k!='KOTEST_INCLUDE_PATTERN'}).returncode
 return rc
def just(recipe,folder,log='output.log',exitfile='exit.txt'):
 rc=command(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],folder/log);(folder/exitfile).write_text(str(rc)+'\n');return rc
def main():
 OUT.mkdir(exist_ok=True);bind('initial');I=inventory(ROOT)
 folder=OUT/'client';folder.mkdir()
 for name,args in [('npm-ci',['npm','ci']),('typecheck',['npm','run','typecheck']),('test',['npm','test','--',*[p.removeprefix('web-client/') for p in json.loads((ROOT/P['inventory']/'CLIENT64_CONTRACT.json').read_text())['client_cases']],'--reporter=json','--outputFile='+str(folder/'vitest.json')])]:
  rc=command(args,folder/(name+'.log'),ROOT/'web-client');(folder/(name+'-exit.txt')).write_text(str(rc));assert rc==0
 client(folder,json.loads((ROOT/P['inventory']/'CLIENT64_CONTRACT.json').read_text()))
 for stage in P['stages']:
  folder=OUT/stage;folder.mkdir()
  for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
   if OUT not in f.parents:f.unlink()
  just(stage,folder)
  for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
   if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
  summary=junit(folder,I[stage]);(folder/'SUMMARY.json').write_text(json.dumps(summary,indent=2)+'\n')
 bind('final')
if __name__=='__main__':
 try:main()
 except Exception as e:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
 finally:
  try:bind('final')
  finally:
   manifest={str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'};(OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
