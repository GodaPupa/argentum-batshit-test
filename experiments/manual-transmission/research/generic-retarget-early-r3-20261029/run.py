"""Compile all seventeen frozen forms before targeted runtime execution. No admission."""
from pathlib import Path
import os,json,subprocess,traceback
from audit import P,sha,compile_gate,reports,snapshot_insertion
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent;OUT=ROOT/P['evidence']
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def bind(label):
 if label=='initial':
  for f in HERE.iterdir():
   if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  b={'source':os.environ['SOURCE'],'tree':os.environ['SOURCE_TREE'],'validation':git('rev-parse','HEAD'),'validation_tree':git('rev-parse','HEAD^{tree}'),'workflow':os.environ['WORKFLOW'],'workflow_blob':git('hash-object',os.environ['WORKFLOW']),'run':os.environ['GITHUB_RUN_ID'],'attempt':int(os.environ['GITHUB_RUN_ATTEMPT']),'run_number':int(os.environ['GITHUB_RUN_NUMBER'])}
  assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
 source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',source+'^')==P['base'] and git('rev-parse','HEAD^')==source
 assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
 assert git('rev-parse','origin/main')==P['main']
 assert git('diff','--name-only',P['base'],source).splitlines()==sorted(P['source_delta'])
 assert not git('status','--porcelain','--untracked-files=no')
 for path,blob in P['generic_input_blobs'].items():
  assert git('rev-parse',P['base']+':'+path)==blob,path
 for path,blob in P['hydration_input_git_blobs'].items():
  assert git('rev-parse',P['accepted_source']+':'+path)==blob,path
 for path,digest in P['accepted_bindings'].items():
  original=subprocess.check_output(['git','show',P['accepted_source']+':'+path]);assert sha(original)==digest,path
  actual=(ROOT/path).read_bytes()
  if path in P['git_bindings']:assert git('hash-object',path)==P['git_bindings'][path],path
  elif path not in P['allowed_protected_successors']:assert sha(actual)==digest,path
  else:assert sha(actual)==P['new_bindings'][path],path
  save(OUT/label/'source'/path,actual)
 for path,digest in P['new_bindings'].items():
  b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
 for path,blob in P['git_bindings'].items():
  assert git('hash-object',path)==blob,path;save(OUT/label/'source'/path,(ROOT/path).read_bytes())
 assert sorted(git('diff','--name-only',P['accepted_source'],source,'--',':(exclude)experiments/manual-transmission/research').splitlines())==P['code_delta']
 before=(ROOT/P['saved_draft_test']).read_text();after=(ROOT/P['scenario']).read_text()
 assert after.startswith(before[:-3]),'Original ten draft scenario bodies changed'
 for path in P['allowed_protected_successors']:
  old=subprocess.check_output(['git','show',P['accepted_source']+':'+path]);new=(ROOT/path).read_bytes()
  # Each authorized successor is additive; deleting its exact frozen additions restores original bytes.
  for addition in reversed(P['protected_additions'][path]):
   at=addition['byte_offset'];b=addition['bytes'].encode()
   assert new[at:at+len(b)]==b
   new=new[:at]+new[at+len(b):]
  assert new==old,path
 save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))

def mutate(name):
 m=(P['mutants'] | P['generic_mutants'])[name];f=ROOT/m['path'];s=f.read_text();assert sha(s.encode())==P['new_bindings'][m['path']]
 assert s.count(m['old'])==m['occurrences'];s=s.replace(m['old'],m['new']);assert sha(s.encode())==m['sha256'];f.write_text(s)
 save(OUT/'forms'/name/m['path'],s.encode())
def restore():
 for path in P['mutation_paths']:save(ROOT/path,subprocess.check_output(['git','show',os.environ['SOURCE']+':'+path]))
def stage(name,recipe,kind,expected=None,failures=None):
 folder=OUT/name;folder.mkdir(parents=True)
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:f.unlink()
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,
   env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
 result=compile_gate(folder) if kind=='compile' else reports(folder,expected,failures)
 (folder/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
def main():
 OUT.mkdir();bind('initial')
 from audit_selftest import main as audit_controls
 (OUT/'AUDIT_SELFTEST.json').write_text(json.dumps(audit_controls(),indent=2)+'\n')
 stage('compile-normal','compile','compile')
 for name in (P['mutants'] | P['generic_mutants']):
  try:mutate(name);stage('compile-'+name,'compile','compile')
  finally:restore()
 bind('compiled-restored');stage('generic-normal','generic','test',P['generic_cases']);stage('legacy-modal','legacy','test',P['legacy_cases']);stage('normal','normal','test',P['normal_cases'])
 for name,m in P['generic_mutants'].items():
  try:mutate(name);stage(name,'generic','test',P['generic_cases'],m['witnesses'])
  finally:restore()
 for name,m in P['mutants'].items():
  try:mutate(name);stage(name,'normal','test',P['normal_cases'],m['witnesses'])
  finally:restore()
 bind('mutants-restored');stage('generic-restored','generic','test',P['generic_cases']);stage('restored','normal','test',P['normal_cases'])
 stage('export','export','test',P['export_cases'])
 for name in ['WAR.json','Fblthp.json']:save(OUT/'export'/name,(ROOT/'mtg-sets/build/fblthp-export'/name).read_bytes())
 original=(ROOT/'mtg-sets/src/test/resources/snapshots/cards/WAR.json').read_bytes();assert sha(original)==P['original_war_sha256']
 save(OUT/'export/WAR.original.json',original)
 check=snapshot_insertion(original,(OUT/'export/WAR.json').read_bytes())
 (OUT/'export/INSERTION.json').write_text(json.dumps(check,indent=2)+'\n')
 bind('final');(OUT/'RESULT.json').write_text(json.dumps({'state':'EARLY_GATES_PASSED_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False,'normal':24,'restored':24,'compiled_forms':17,'generic_cases':26,'generic_mutants':8,'mutants':8,'negative_assertions':25,'passing_mutant_controls':167,'export':1,'legacy_modal':4})+'\n')
if __name__=='__main__':
 try:main()
 except Exception:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
 finally:
  try:restore();bind('final')
  finally:
   OUT.mkdir(exist_ok=True);(OUT/'MANIFEST.json').write_text(json.dumps({str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'},indent=2)+'\n')
