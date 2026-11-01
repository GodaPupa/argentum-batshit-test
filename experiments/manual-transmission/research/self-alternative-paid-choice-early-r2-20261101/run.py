"""Paid-choice early diagnostic. Compilation failures are not negative evidence."""
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
paths=P['paths']+[P['test']]
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
 assert sorted(delta)==sorted(P['source_delta']),delta
 for p in P['paths']:assert git('rev-parse',P['base']+':'+p)==git('rev-parse',os.environ['SOURCE']+':'+p)
 protected={}
 js(O/label/'BASE_TREE.json',git('ls-tree','-r',P['base']).splitlines())
 for p in P['unaffected']:
  a=git('rev-parse',P['base']+':'+p);b=git('rev-parse',os.environ['SOURCE']+':'+p)
  assert a==b,p;protected[p]=b
 for p in paths:assert (R/p).read_bytes()==normal[p]
 js(O/label/'PROTECTED.json',protected)
def stage(form,recipe,label=None):
 restore()
 if form!='normal':
  m=next(m for m in P['mutations'] if m['id']==form)
  p=R/m['path'];s=p.read_text();assert s.count(m['before'])==1
  p.write_text(s.replace(m['before'],m['after']))
 folder=O/(label or (recipe+'-'+form));folder.mkdir(parents=True)
 for p in paths:save(folder/'source'/p,(R/p).read_bytes())
 js(folder/'SOURCE_HASHES.json',{p:sha((R/p).read_bytes()) for p in paths})
 for p in R.glob('**/build/test-results/test/TEST-*.xml'):
  if O not in p.parents:p.unlink()
 env={k:v for k,v in os.environ.items() if k not in ('KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS')}
 with (folder/'output.log').open('wb') as f:
  rc=subprocess.run(['just','--working-directory',str(R),'--justfile',str(H/'validation.just'),recipe],stdout=f,stderr=subprocess.STDOUT,env=env).returncode
 (folder/'exit.txt').write_text(str(rc)+'\n')
 reports=[p for p in R.glob('**/build/test-results/test/TEST-*.xml') if O not in p.parents]
 if recipe=='compile':
  log=(folder/'output.log').read_text(errors='replace')
  assert rc==0 and 'BUILD SUCCESSFUL' in log and ':rules-engine:compileTestKotlin' in log and not reports,(form,rc)
  return
 rows=[]
 for p in reports:
  save(folder/'junit'/p.relative_to(R),p.read_bytes())
  for c in ET.parse(p).getroot().iter('testcase'):
   rows.append(dict(name=c.get('name'),classname=c.get('classname'),failures=[dict(type=n.get('type'),message=n.get('message'),text=n.text) for n in c.findall('failure')],errors=[dict(type=n.get('type'),message=n.get('message'),text=n.text) for n in c.findall('error')],skipped=len(c.findall('skipped'))))
 assert Counter(r['name'] for r in rows)==Counter(P['case_names']),rows
 assert all(r['classname']==P['class'] and r['skipped']==0 for r in rows)
 failed=sum(bool(r['failures'] or r['errors']) for r in rows)
 assert rc in (0,1) and (rc!=0)==bool(failed),(rc,failed)
 result=dict(form=form,total=len(rows),failed=failed,rows=rows,rc=rc)
 js(folder/'REPORT.json',result);return result
def main():
 O.mkdir();bind('initial')
 for p in P['paths']:
  save(O/'original-source'/p,subprocess.check_output(['git','show',P['base']+':'+p]))
 for p in H.iterdir():
  if p.is_file():save(O/'protocol'/p.name,p.read_bytes())
 save(O/'WORKFLOW.yml',(R/os.environ['WORKFLOW']).read_bytes())
 js(O/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=os.environ['GITHUB_RUN_ATTEMPT'],run_number=os.environ['GITHUB_RUN_NUMBER']))
 assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
 for form in P['forms']:stage(form,'compile')
 js(O/'ALL_FORMS_COMPILED.json',P['forms'])
 results={form:stage(form,'observe') for form in P['forms']}
 restored=stage('normal','observe','observe-restored')
 js(O/'RESTORED_REPORT.json',restored)
 restore();bind('restored')
 js(O/'RESULT.json',dict(counts={k:dict(total=v['total'],failed=v['failed']) for k,v in results.items()},restored_failed=restored['failed'],admitted=False,original_receiving_required=True))
 return 1 if results['normal']['failed'] or restored['failed'] else 0
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

