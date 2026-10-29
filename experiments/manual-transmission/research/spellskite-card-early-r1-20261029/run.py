"""Compile-first production-card boundary qualification; not card admission."""
from pathlib import Path
import os,json,subprocess,shutil
from audit import P,sha,compile_gate,reports,snapshot_insertion
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
 for path,digest in {**P['protected'],**P['bindings'],**P['new_bindings']}.items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
 # Apart from the two card definitions and the two production-binding test files plus exporter,
 # accepted source differs only by immutable research records. No runtime or shared SDK edits.
 assert sorted(git('diff','--name-only',P['accepted_source'],source,'--',':(exclude)experiments/manual-transmission/research').splitlines())==sorted(P['code_delta'])
 before=subprocess.check_output(['git','show',P['accepted_source']+':'+P['scenario']]).decode()
 after=(ROOT/P['scenario']).read_text()
 assert [s.strip() for s in before.splitlines() if 'shouldBe' in s]==[s.strip() for s in after.splitlines() if 'shouldBe' in s]
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))
  assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
def mutate(name):
 m=P['mutants'][name];f=ROOT/m['path'];b=f.read_text();assert sha(b.encode())==P['new_bindings'][m['path']]
 assert b.count(m['old'])==1;b=b.replace(m['old'],m['new']);assert sha(b.encode())==m['sha256'];f.write_text(b)
 save(OUT/'forms'/name/m['path'],b.encode())
def restore():
 for path in P['mutation_paths']:
  save(ROOT/path,subprocess.check_output(['git','show',os.environ['SOURCE']+':'+path]))
def stage(name,recipe,kind,expected=None,failures=None):
 folder=OUT/name;folder.mkdir(parents=True)
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:f.unlink()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
 result=compile_gate(folder) if kind=='compile' else reports(folder,expected,failures)
 (folder/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
def main():
 OUT.mkdir();bind('initial')
 stage('compile-normal','compile','compile')
 for name in P['mutants']:
  try:mutate(name);stage('compile-'+name,'compile','compile')
  finally:restore()
 bind('compiled-restored')
 stage('normal','normal','test',P['normal_cases'])
 for name,m in P['mutants'].items():
  try:mutate(name);stage(name,'registration','test',P['registration_cases'],m['witnesses'])
  finally:restore()
 bind('mutants-restored');stage('restored','normal','test',P['normal_cases'])
 stage('export','export','test',P['export_cases'])
 for code in ['NPH','CLB']:save(OUT/'export'/(code+'.json'),(ROOT/'mtg-sets/build/spellskite-export'/f'{code}.json').read_bytes())
 original=(HERE/'NPH.original.json').read_bytes();assert sha(original)==P['original_nph_sha256']
 check=snapshot_insertion(original,(OUT/'export/NPH.json').read_bytes())
 assert (OUT/'export/CLB.json').read_bytes()==(ROOT/'mtg-sets/src/test/resources/snapshots/cards/CLB.json').read_bytes()
 (OUT/'export/INSERTION.json').write_text(json.dumps(check,indent=2)+'\n')
 bind('final')
if __name__=='__main__':
 try:main()
 except Exception as e:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
 finally:
  try:restore();bind('final')
  finally:
   OUT.mkdir(exist_ok=True);manifest={str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'};(OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
