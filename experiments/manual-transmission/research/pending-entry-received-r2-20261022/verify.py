"""Read-only independent receipt audit. Never dispatches builds, tests or games."""
from pathlib import Path
import hashlib, io, json, re, sys, zipfile, subprocess
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent
meta=json.loads((root/'ARTIFACT.json').read_text())
chunks=[]
for p in meta['parts']:
 b=(root/p['name']).read_bytes()
 assert len(b)==p['bytes'] and hashlib.sha256(b).hexdigest()==p['sha256'],p['name']
 chunks.append(b)
data=b''.join(chunks)
assert len(data)==meta['size_in_bytes']
assert hashlib.sha256(data).hexdigest()==meta['digest'].removeprefix('sha256:')
z=zipfile.ZipFile(io.BytesIO(data));assert z.testzip() is None
names=set(z.namelist());assert len(names)==len(z.namelist())
manifest={}
for line in z.read('SHA256SUMS').decode().splitlines():
 h,n=line.split('  ',1);assert n not in manifest
 assert hashlib.sha256(z.read(n)).hexdigest()==h,n
 manifest[n]=h
assert names==set(manifest)|{'SHA256SUMS'}
source='7b84856bba24923a9e8efc78461433f70ad3145c'
validation='a82a0ed35d5925d0c0a34b7b1b27359c3e364990'
protocol=json.loads((root/'PROTOCOL.json').read_text())
bindings=json.loads((root/'EXPECTED_BINDINGS.json').read_text())
observed_bindings={}
for mode in ['fixed','negative']:
 name='binding-'+mode+'.json'
 if name not in names:continue
 b=json.loads(z.read(name));assert b['source']==source and b['validation']==validation
 assert b['source_tree']=='7f0bb6ceb8fec8d1d3314c246f271ba4a5cfb519'
 assert b['baseline']==protocol['baseline_source'] and b['mode']==mode
 assert b['source_manifest']==protocol['source_manifest'] and b['bindings']==bindings
 assert len(b['bindings'])==22 and b['goldens_verified']==23
 assert b['official_games']==b['official_seeds']==0 and b['precision_reserved']==2
 observed_bindings[mode]=b
assert set(observed_bindings)=={'fixed','negative'}, 'Missing source binding'
stages={};gaps=[]
for stage in ['negative','focused','broad','ordinary']:
 prefix=stage+'/';xmlnames=sorted(n for n in names if n.startswith(prefix+'junit/') and n.endswith('.xml'))
 if prefix+'exit.txt' not in names:
  gaps.append(stage+' not executed');continue
 rc=int(z.read(prefix+'exit.txt').strip());mods={};classes={};failures=[];skips=[];required_cases={}
 for name in xmlnames:
  module=name[len(prefix+'junit/'):].split('/build/test-results/test/')[0]
  suite=ET.fromstring(z.read(name));cases=suite.findall('.//testcase')
  assert len(cases)==int(suite.get('tests')),(name,'row count')
  row=mods.setdefault(module,dict(rows=0,passed=0,failures=0,errors=0,skipped=0,classes=0))
  row['rows']+=len(cases);row['classes']+=1
  for label,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
   n=sum(c.find(tag) is not None for c in cases);assert n==int(suite.get(label)),(name,label)
   row[label]+=n
  for c in cases:
   ident={'classname':c.get('classname'),'name':c.get('name'),'xml':name}
   cls=c.get('classname').rsplit('.',1)[-1];classes[cls]=classes.get(cls,0)+1
   if cls in {'PendingCopyEntryScenarioTest','CopiedAsEntersChoiceScenarioTest','EntersAsCopyExceptionsScenarioTest','EntersAsCopyExceptionsSerializationTest'}: required_cases.setdefault(cls,[]).append(ident)
   if c.find('skipped') is not None:skips.append(ident)
   elif c.find('failure') is not None or c.find('error') is not None:
    f=c.find('failure')
    if f is None:f=c.find('error')
    failures.append(dict(ident,type=f.get('type'),message=f.get('message'),detail=f.text))
   else:row['passed']+=1
 totals={k:sum(m[k] for m in mods.values()) for k in ['rows','passed','failures','errors','skipped','classes']}
 if prefix+'SUMMARY.json' in names:
  s=json.loads(z.read(prefix+'SUMMARY.json'));assert s['source']==source and s['validation']==validation
  assert s['totals']==totals and set(s['modules'])==set(mods)
  for m,v in mods.items():
   for k,n in v.items():assert s['modules'][m][k]==n,(stage,m,k)
 tracked=z.read(prefix+'tracked-changes.txt').decode() if prefix+'tracked-changes.txt' in names else None
 expected_ok=False
 if stage=='negative':
  expected_ok=(totals==dict(rows=9,passed=1,failures=8,errors=0,skipped=0,classes=1) and rc!=0
    and classes=={'PendingCopyEntryScenarioTest':9}
    and {line[3:] for line in tracked.splitlines()}=={p for p in protocol['source_manifest'] if '/src/main/' in p})
 else:
  expected_ok=(rc==0 and not tracked and totals['failures']==totals['errors']==0 and classes.get('PendingCopyEntryScenarioTest')==9 and classes.get('CopiedAsEntersChoiceScenarioTest')==7 and classes.get('EntersAsCopyExceptionsScenarioTest')==10)
  if stage=='focused':expected_ok &= totals['passed']==371 and totals['skipped']==0 and classes.get('EntersAsCopyExceptionsSerializationTest')==2
  elif stage=='broad':
   required={'rules-engine'}|{'mtg-sets/'+era+'/tests' for era in ['1993-1999','2000-2002','2003-2007','2008-2016','2017-2022','2023','2024','2025','2026']}
   expected_ok &= required <= mods.keys() and sum(mods.get(m,{}).get('passed',0) for m in required)==14555 and mods.get('rules-engine',{}).get('passed')==3780 and totals['skipped']==0
  else:
   expected_ok &= totals['passed']==18163 and totals['skipped']==43 and len(mods)==20 and mods.get('oracle-assay',{}).get('passed')==568 and mods.get('mtg-sdk',{}).get('passed')==529 and mods.get('rules-engine',{}).get('passed')==3780
   expected_ok &= sorted(s['classname']+'::'+s['name'] for s in skips)==sorted(json.loads((root/'BASELINE_SKIPS.json').read_text()))
 if not expected_ok:gaps.append(stage+' qualification gate not satisfied')
 log=z.read(prefix+'gradle.log').decode(errors='replace') if prefix+'gradle.log' in names else ''
 task_outcomes=[{'task':m.group(1),'outcome':m.group(2) or 'EXECUTED'} for m in re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$',log,re.M)]
 stages[stage]={'required_cases':required_cases,'gradle_task_outcomes':task_outcomes,'exit':rc,'totals':totals,'modules':mods,'classes':classes,'failures':failures,'skips':skips,'tracked_changes':tracked,'gate_satisfied':bool(expected_ok)}
if 'negative' in stages:
 observed={c['name']:('assertion_failure' if any(f['name']==c['name'] for f in stages['negative']['failures']) else 'pass') for c in stages['negative']['required_cases'].get('PendingCopyEntryScenarioTest',[])}
 expected={c['name']:c['predicted_baseline'] for c in protocol['cases']}
 if observed!=expected:gaps.append('Preregistered nine-case baseline verdict mismatch')
 expected_locations={c['name']:c['expected_first_failure_line'] for c in protocol['cases'] if c['predicted_baseline']=='assertion_failure'}
 locations={}
 for f in stages['negative']['failures']:
  frame=re.search(r'PendingCopyEntryScenarioTest.kt:(\d+)',f['detail'] or '')
  locations[f['name']]=int(frame.group(1)) if frame else None
 if locations!=expected_locations or any(f['type']!='org.opentest4j.AssertionFailedError' for f in stages['negative']['failures']):
  gaps.append('Invalid negative controls: first failure location or assertion type differs from preregistration')
  stages['negative']['gate_satisfied']=False
 stages['negative']['first_failure_locations']=locations
 original_cases=sorted(c['classname']+'::'+c['name'] for c in stages['negative']['required_cases'].get('PendingCopyEntryScenarioTest',[]))
 for stage in ['focused','broad','ordinary']:
  if stage in stages:
   received_cases=sorted(c['classname']+'::'+c['name'] for c in stages[stage]['required_cases'].get('PendingCopyEntryScenarioTest',[]))
   assert received_cases==original_cases,(stage,'nine-case identity drift')
assay={}
for n in ['build-exit.txt','gate-exit.txt','differential-exit.txt']:
 if 'assay/'+n not in names:gaps.append('Assay '+n+' missing')
 else:
  assay[n]=int(z.read('assay/'+n).strip())
  if assay[n]!=0:gaps.append('Assay '+n+' nonzero')
if 'assay/gate.log' in names:
 gate=z.read('assay/gate.log').decode()
 for label in ['Cards assayed','Faces','Ability lines','Round-trips byte-exact','Alternate spelling normalized','Declined','Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)','Cards fully covered']:
  rows=[s.strip() for s in gate.splitlines() if s.strip().startswith(label)]
  if len(rows)!=1:gaps.append('Assay field missing: '+label);continue
  value=rows[0][len(label):].strip();assay[label]=value
  n=int(re.match(r'[\s:]*([0-9]+)',value).group(1))
  if label=='Cards assayed' and n!=2000:gaps.append('Assay card count mismatch')
  if label in ['Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)'] and n!=0:gaps.append('Assay nonzero '+label)
for n in ['oracle-bulk-sha256.txt','oracle-bulk-bytes.txt']:
 if 'assay/'+n in names:assay[n]=z.read('assay/'+n).decode().strip()
 else:gaps.append('Missing corpus identity: '+n)
if 'oracle-bulk-sha256.txt' in assay:assert re.fullmatch('[0-9a-f]{64}',assay['oracle-bulk-sha256.txt'].split()[0])
if 'oracle-bulk-bytes.txt' in assay:assert int(assay['oracle-bulk-bytes.txt'].split()[0])>0
if 'assay/differential.log' in names:
 differential=z.read('assay/differential.log').decode()
 assay['differential_sha256']=hashlib.sha256(z.read('assay/differential.log')).hexdigest()
 assay['divergent_cards']=[{'card':m[0],'set':m[1]} for m in re.findall(r'^  (.+)  \[([A-Z0-9]+)\]$',differential,re.M)]
 assay['differential_review']='Compare original structural findings to the preserved prior report before acceptance; zero process exit is decode success only.'
if not gaps:
 negative=stages['negative']['failures']
 assert all(f['type']=='org.opentest4j.AssertionFailedError' for f in negative)
 locations=[int(re.search(r'PendingCopyEntryScenarioTest.kt:(\d+)',f['detail']).group(1)) for f in negative]
 assert sorted(locations)==sorted([120,120,120,120,164,202,218,235]),locations
 subprocess.run([sys.executable,str(root/'compare_differential.py')],check=True,capture_output=True,text=True)
 comparison=json.loads((root/'DIFFERENTIAL_COMPARISON.json').read_text())
 assert comparison['current_artifact_id']==meta['id'] and comparison['current_report_sha256']==assay['differential_sha256']
 if comparison['qualification']!='UNCHANGED_DIVERGENCES_RETAINED':gaps.append('Differential review incomplete')
 else:assay['differential_review']='All 51 original divergence blocks are byte-identical; prior classifications retained, no divergence waived or normalized.'
duplicate=json.loads((root/'DUPLICATE_RUN.json').read_text())
assert duplicate['id']==39830599140 and duplicate['head_sha']==validation
assert duplicate['run_number']==2 and duplicate['run_attempt']==1
if duplicate['status']!='completed' or duplicate['conclusion']!='skipped' or len(duplicate['jobs'])!=1 or duplicate['jobs'][0]['conclusion']!='skipped' or duplicate['jobs'][0]['steps']:
 gaps.append('Guarded duplicate did not reach verified skipped disposition')
run=json.loads((root/'RUN.json').read_text())
assert run['id']==39830545066 and run['head_sha']==validation and run['run_attempt']==1
if run['status']!='completed' or run['conclusion']!='success':gaps.append('GitHub run not completed successfully')
assert len(run['jobs'])==1 and run['jobs'][0]['id']==120005209308
if run['jobs'][0]['status']!='completed' or run['jobs'][0]['conclusion']!='success':gaps.append('GitHub job not completed successfully')
for step in run['jobs'][0]['steps']:
 if step['conclusion'] not in ['success','skipped']:gaps.append('Workflow step incomplete/failed: '+step['name'])
report={'run':run['id'],'source':source,'validation':validation,'artifact_id':meta['id'],'zip_bytes':len(data),'zip_sha256':hashlib.sha256(data).hexdigest(),'manifest_entries':len(manifest),'bindings':observed_bindings,'stages':stages,'assay':assay,'remaining_gates':gaps,'status':'PENDING_DIRECT_COPY_ENTRY_QUALIFIED_COMPONENT_ONLY' if not gaps else 'QUALIFICATION_INCOMPLETE','official_games':0,'official_seeds':0,'competitive_admission':False}
if '--write' in sys.argv:(root/'INDEPENDENT_AUDIT.json').write_text(json.dumps(report,indent=2)+'\n')
else:assert json.loads((root/'INDEPENDENT_AUDIT.json').read_text())==report
print(json.dumps({'status':report['status'],'stages':{s:v['totals'] for s,v in stages.items()},'remaining_gates':gaps},indent=2))
