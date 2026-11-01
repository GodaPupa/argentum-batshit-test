"""Frozen Disciple card controls. Original receiving and full admission remain separate."""
from pathlib import Path
import json,subprocess,os,hashlib,traceback,xml.etree.ElementTree as ET
from collections import Counter
R=Path.cwd(); H=Path(__file__).resolve().parent
P=json.loads((H/'PROTOCOL.json').read_text()); O=R/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):
 p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
paths=P['paths']+[P['test'],P['registration_file']]
normal={p:(R/p).read_bytes() for p in paths}
def restore():
 for p,b in normal.items():(R/p).write_bytes(b)
def bind(label):
 assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
 assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
 assert git('rev-parse','HEAD^')==os.environ['SOURCE']
 assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
 assert git('rev-parse','refs/remotes/origin/main')==P['main']
 assert not git('status','--porcelain','--untracked-files=no')
 delta=git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()
 assert sorted(delta)==sorted(P['changed_paths']+[str(H.relative_to(R)/n) for n in P['record_files']]),delta
 protected={}
 for p in P['unaffected']:
  a=git('rev-parse',P['base']+':'+p);b=git('rev-parse',os.environ['SOURCE']+':'+p)
  assert a==b,p;protected[p]=b
 for p in paths:assert (R/p).read_bytes()==normal[p]
 for p,h in P['normal_sha256'].items():assert sha(normal[p])==h,p
 prior=subprocess.check_output(['git','show',P['base']+':'+P['test']])
 assert normal[P['test']].startswith(prior[:-3])
 js(O/label/'PROTECTED.json',protected)
def source_form(form):
 restore()
 if form!='normal':
  m=next(m for m in P['mutations'] if m['id']==form)
  p=R/m['path'];s=p.read_text()
  for e in m['edits']:
   assert s.count(e['before'])==1,(form,e['before']);s=s.replace(e['before'],e['after'])
  p.write_text(s);assert sha(p.read_bytes())==m['source_sha256'],form
def stage(form,recipe,label=None):
 source_form(form)
 folder=O/(label or (recipe+'-'+form));folder.mkdir(parents=True)
 for p in paths:save(folder/'source'/p,(R/p).read_bytes())
 js(folder/'SOURCE_HASHES.json',{p:sha((R/p).read_bytes()) for p in paths})
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:p.unlink()
 candidate=R/'mtg-sets/build/disciple-freyalise-candidate'
 if recipe=='registration':
  for p in candidate.glob('*.json'):p.unlink()
 env={k:v for k,v in os.environ.items() if k not in ('KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS')}
 with (folder/'output.log').open('wb') as f:
  rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(H/'validation.just'),recipe],stdout=f,stderr=subprocess.STDOUT,env=env).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 reports=[p for p in R.glob('**/build/test-results/test/TEST-*.xml') if O not in p.parents]
 if recipe=='compile':
  log=(folder/'output.log').read_text(errors='replace')
  assert rc==0 and 'BUILD SUCCESSFUL' in log and all(t in log for t in P['compile_tasks']) and not reports,(form,rc)
  return
 rows=[]
 for p in reports:
  save(folder/'junit'/p.relative_to(R),p.read_bytes())
  for c in ET.parse(p).getroot().iter('testcase'):
   rows.append(dict(name=c.get('name'),classname=c.get('classname'),failures=[dict(type=n.get('type'),message=n.get('message'),text=n.text) for n in c.findall('failure')],errors=[dict(type=n.get('type'),message=n.get('message'),text=n.text) for n in c.findall('error')],skipped=len(c.findall('skipped'))))
 expected=P['registration_cases'] if recipe=='registration' else P['case_names']
 cls=P['registration_class'] if recipe=='registration' else P['class']
 result=dict(form=form,total=len(rows),failed=sum(bool(r['failures'] or r['errors']) for r in rows),rows=rows,rc=rc)
 js(folder/'REPORT.json',result)
 assert Counter(r['name'] for r in rows)==Counter(expected),(form,recipe,[r['name'] for r in rows])
 assert all(r['classname']==cls and r['skipped']==0 and not r['errors'] for r in rows)
 assert rc in (0,1) and (rc!=0)==bool(result['failed']),(rc,result['failed'])
 if recipe=='registration':
  for p in candidate.glob('*.json'):save(folder/'candidate'/p.name,p.read_bytes())
  assert {p.name for p in candidate.glob('*.json')}=={'DiscipleOfFreyalise.block.json','MH3.candidate.json'}
 return result
def main():
 O.mkdir();bind('initial')
 for p in H.iterdir():
  if p.is_file():save(O/'protocol'/p.name,p.read_bytes())
 save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes())
 js(O/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=os.environ['GITHUB_RUN_ATTEMPT'],run_number=os.environ['GITHUB_RUN_NUMBER']))
 assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
 for form in P['forms']:stage(form,'compile')
 js(O/'ALL_FORMS_COMPILED.json',P['forms'])
 # Any normal failure stops before spending on the mutant behavioral sweep.
 results={'normal':stage('normal','observe')}
 assert results['normal']['failed']==0,'normal scenario failure'
 registration=stage('normal','registration')
 assert registration['failed']==0,'normal registration failure'
 for form in P['forms'][1:]:results[form]=stage(form,'observe')
 restored=stage('normal','observe','observe-restored')
 restored_registration=stage('normal','registration','registration-restored')
 assert restored['failed']==restored_registration['failed']==0
 byname=lambda v:{r['name']:r for r in v['rows']}
 witnesses={}; controls={}
 for m in P['mutations']:
  form=m['id'];rows=byname(results[form]);witnesses[form]=[]
  for w in m['witnesses']:
   row=rows[w['case']]
   assert not byname(results['normal'])[w['case']]['failures'] and not byname(restored)[w['case']]['failures']
   assert any(f['type']=='org.opentest4j.AssertionFailedError' and ('DiscipleOfFreyaliseScenarioTest.kt:'+str(w['line'])) in (f['text'] or '') for f in row['failures']),(form,w)
   assert normal[P['test']].decode().splitlines()[w['line']-1].strip()==w['assertion']
   witnesses[form].append(row)
  for name in m['unaffected_controls']:assert not rows[name]['failures'],(form,name)
  controls[form]=m['unaffected_controls']
 js(O/'WITNESSES.json',witnesses);js(O/'UNAFFECTED_CONTROLS.json',controls)
 restore();bind('restored')
 js(O/'RESULT.json',dict(counts={k:dict(total=v['total'],failed=v['failed']) for k,v in results.items()},restored_failed=restored['failed'],registration_total=registration['total'],restored_registration_total=restored_registration['total'],admitted=False,original_receiving_required=True))
 return 0
status=1
try:status=main()
except Exception:
 O.mkdir(exist_ok=True);(O/'FAILURE.txt').write_text(traceback.format_exc())
finally:
 restore()
 try:bind('final')
 except Exception:
  O.mkdir(exist_ok=True);(O/'FINAL_BINDING_FAILURE.txt').write_text(traceback.format_exc());status=1
 O.mkdir(exist_ok=True)
 js(O/'MANIFEST.json',{str(p.relative_to(O)):sha(p.read_bytes()) for p in sorted(O.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'})
raise SystemExit(status)
