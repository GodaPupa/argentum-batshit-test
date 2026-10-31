"""Bounded original diagnostic; never admission or compatibility acceptance."""
from pathlib import Path
import hashlib,json,os,subprocess,traceback,xml.etree.ElementTree as ET
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent
P=json.loads((HERE/'PROTOCOL.json').read_text());OUT=ROOT/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
def bind(label):
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 delta=git('diff','--name-status',P['base'],os.environ['SOURCE']).splitlines()
 assert all(line.startswith('A\t') for line in delta),delta
 assert sorted(line[2:] for line in delta)==sorted(P['source_delta']),delta
 assert not git('status','--porcelain','--untracked-files=no')
 for name,digest in P['form_hashes'].items():
  assert sha((HERE/'forms'/f'{name}.kt').read_bytes())==digest,name
 assert sha((ROOT/P['test']).read_bytes())==P['test_sha256']
 assert (ROOT/P['card']).read_bytes()==(HERE/'forms/normal.kt').read_bytes()
 protected={}
 for path in P['unchanged_paths']:
  a=git('rev-parse',P['base']+':'+path);b=git('rev-parse',os.environ['SOURCE']+':'+path)
  assert a==b,path;protected[path]=b
 js(OUT/label/'PROTECTED.json',protected)
 if label=='initial':
  for f in HERE.rglob('*'):
   if f.is_file() and '__pycache__' not in f.parts:save(OUT/'protocol'/f.relative_to(HERE),f.read_bytes())
  save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
  save(OUT/'scenario.kt',(ROOT/P['test']).read_bytes())
  pin=dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))
  assert pin['attempt']==pin['run_number']==1
  js(OUT/'BINDING.json',pin)
def stage(name,recipe,form):
 folder=OUT/name;folder.mkdir(parents=True)
 b=(HERE/'forms'/f'{form}.kt').read_bytes()
 (ROOT/P['card']).write_bytes(b)
 assert sha((ROOT/P['card']).read_bytes())==P['form_hashes'][form]
 save(folder/'CARD.kt',b)
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:f.unlink()
 env={k:v for k,v in os.environ.items() if k not in ('KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS')}
 with (folder/'output.log').open('wb') as log:
  rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env=env).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 reports=[]
 for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
  if OUT not in f.parents:
   save(folder/'junit'/f.relative_to(ROOT),f.read_bytes());reports.append(f)
 log=(folder/'output.log').read_text(errors='replace')
 if recipe=='compile':
  ok=rc==0 and 'BUILD SUCCESSFUL' in log and ':mtg-sets:2024:tests:compileTestKotlin' in log and not reports
  js(folder/'SUMMARY.json',dict(compile_passed=ok,rc=rc,form=form,behavioral_evidence=False))
  assert ok,('Compilation failed; no behavioral inference',form,rc)
  return None
 rows=[]
 for f in reports:
  root=ET.parse(f).getroot()
  for case in root.iter('testcase'):
   rows.append(dict(report=str(f.relative_to(ROOT)),classname=case.get('classname'),name=case.get('name'),failures=[dict(type=n.get('type'),message=n.get('message'),text=n.text) for n in case.findall('failure')],errors=[dict(type=n.get('type'),message=n.get('message'),text=n.text) for n in case.findall('error')],skipped=len(case.findall('skipped'))))
 identities=[r['name'] for r in rows]
 assert sorted(identities)==sorted(P['case_names']),(form,identities)
 assert all(r['classname']==P['class'] and not r['skipped'] for r in rows),rows
 failed=[r for r in rows if r['failures'] or r['errors']]
 assert rc in [0,1] and (rc!=0)==bool(failed),(form,rc,len(failed))
 result=dict(form=form,rc=rc,rows=rows,total=len(rows),failed=len(failed),skipped=0,admitted=False)
 js(folder/'SUMMARY.json',result)
 return result
def main():
 OUT.mkdir();bind('initial')
 for form in P['forms']:stage('compile-'+form,'compile',form)
 js(OUT/'ALL_FORMS_COMPILED.json',dict(forms=P['forms'],all_compiled_before_behavior=True))
 results={}
 for form in P['forms']:results[form]=stage('observe-'+form,'observe',form)
 (ROOT/P['card']).write_bytes((HERE/'forms/normal.kt').read_bytes())
 bind('restored')
 js(OUT/'RESULT.json',dict(state='DIAGNOSTIC_OBSERVED_ORIGINAL_RECEIVING_REQUIRED',counts={k:dict(total=v['total'],failed=v['failed']) for k,v in results.items()},feature_admitted=False,card_admitted=False,full_qualification=False))
 return 1 if results['normal']['failed'] else 0
if __name__=='__main__':
 status=1
 try:status=main()
 except Exception:
  OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc())
 finally:
  (ROOT/P['card']).write_bytes((HERE/'forms/normal.kt').read_bytes())
  try:bind('final')
  except Exception:(OUT/'FINAL_BINDING_FAILURE.txt').write_text(traceback.format_exc());status=1
  OUT.mkdir(exist_ok=True)
  js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
 raise SystemExit(status)
