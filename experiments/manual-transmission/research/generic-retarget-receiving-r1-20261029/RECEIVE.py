"""Receive original frozen early artifacts once; never grants feature/card admission."""
from pathlib import Path,PurePosixPath
from collections import Counter
import sys,json,hashlib,zipfile,importlib.util
R=Path(__file__).resolve().parent
pins=json.loads((R/'PINS.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
run=json.loads((R/'RUN.json').read_text());jobs=json.loads((R/'JOBS.json').read_text());artifacts=json.loads((R/'ARTIFACTS.json').read_text())
assert run['id']==pins['run'] and run['head_sha']==pins['validation'] and run['run_attempt']==run['run_number']==1
assert run['event']=='push' and run['workflow_id']==pins['workflow_id']
assert run['status']=='completed' and run['path']==pins['workflow'] and run['head_branch']==pins['branch']
assert jobs['total_count']==1 and len(jobs['jobs'])==1
job=jobs['jobs'][0];assert job['id']==pins['job'];assert job['run_id']==run['id'] and job['status']=='completed' and job['head_sha']==pins['validation']
assert artifacts['total_count']==1 and len(artifacts['artifacts'])==1
a=artifacts['artifacts'][0];assert a['workflow_run']['id']==run['id'] and a['workflow_run']['head_sha']==pins['validation'] and not a['expired']
assert a['name']=='manual-generic-retarget-early-r1-'+str(run['id'])
b=(R/'original.zip').read_bytes();assert len(b)==a['size_in_bytes'] and 'sha256:'+sha(b)==a['digest']
with zipfile.ZipFile(R/'original.zip') as z:
 assert z.testzip() is None
 names=z.namelist();assert len(names)==len(set(names))
 assert all(not PurePosixPath(x).is_absolute() and '..' not in PurePosixPath(x).parts for x in names)
 data={x:z.read(x) for x in names if not x.endswith('/')}
manifest=json.loads(data['MANIFEST.json']);assert set(manifest)==set(data)-{'MANIFEST.json'}
for path,digest in manifest.items():assert sha(data[path])==digest,path
for name,blob in pins['protocol_blobs'].items():assert gitsha(data['protocol/'+name])==blob,name
assert gitsha(data['WORKFLOW.yml'])==pins['workflow_blob']
binding=json.loads(data['BINDING.json'])
for key in ['source','tree','validation','validation_tree','workflow','workflow_blob']:assert binding[key]==pins[key],key
assert int(binding['run'])==run['id'] and binding['attempt']==binding['run_number']==1
out=R/'extracted';assert not out.exists(),'Original extraction already exists; inspect saved result rather than receive twice'
for path,value in data.items():
 p=out/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(value)
P=json.loads(data['protocol/PROTOCOL.json'])
phases=['initial','final']+[n for n in ['compiled-restored','mutants-restored'] if any(k.startswith(n+'/source/') for k in data)]
for phase in phases:
 for path,digest in {**P['accepted_bindings'],**P['new_bindings']}.items():
  value=data[phase+'/source/'+path]
  if path in P['git_bindings']:assert gitsha(value)==P['git_bindings'][path],(phase,path)
  else:assert sha(value)==digest,(phase,path)
 for path,blob in P['git_bindings'].items():assert gitsha(data[phase+'/source/'+path])==blob
 assert data[phase+'/TRACKED.diff']==b''
for path in P['allowed_protected_successors']:
 value=data['final/source/'+path]
 for ad in reversed(P['protected_additions'][path]):
  at=ad['byte_offset'];addition=ad['bytes'].encode();assert value[at:at+len(addition)]==addition;value=value[:at]+value[at+len(addition):]
 assert sha(value)==P['accepted_bindings'][path],path
for name,m in (P['mutants'] | P['generic_mutants']).items():
 key='forms/'+name+'/'+m['path']
 if key in data:
  assert sha(data[key])==m['sha256']
  original=data['initial/source/'+m['path']].decode();assert original.count(m['old'])==m['occurrences']
  assert original.replace(m['old'],m['new']).encode()==data[key]
sys.path.insert(0,str(out/'protocol'));import audit
result={'state':'ORIGINAL_ARTIFACT_RECEIVED','source':pins['source'],'tree':pins['tree'],'validation':pins['validation'],'workflow_blob':pins['workflow_blob'],'run':run['id'],'job':job['id'],'attempt':1,'artifact':a['id'],'bytes':len(b),'sha256':sha(b),'manifest_entries':len(manifest),'feature_admitted':False,'card_admitted':False,'official_games':0,'official_seeds':0,'stages':{}}
# Failed qualification evidence is still received; no missing/failed phase becomes behavioral proof.
for name in ['compile-normal']+['compile-'+n for n in (P['mutants'] | P['generic_mutants'])]:
 if (out/name/'exit.txt').exists():
  try:result['stages'][name]=audit.compile_gate(out/name)
  except AssertionError:result['stages'][name]={'passed':False,'behavioral_negative_evidence':False}
# Preserve all observed report identities and exact failure witnesses even on terminal failures.
import xml.etree.ElementTree as ET
observed={}
for path,value in data.items():
 if '/junit/' not in path or not path.endswith('.xml'):continue
 stage=path.split('/',1)[0];root=ET.fromstring(value);rows=[]
 for tc in root.iter('testcase'):
  row={'classname':tc.get('classname'),'name':tc.get('name'),'failures':[],'errors':[],'skipped':len(tc.findall('skipped'))}
  for kind in ['failure','error']:
   row[kind+'s']=[{'type':n.get('type'),'message':n.get('message'),'text':n.text or ''} for n in tc.findall(kind)]
  rows.append(row)
 observed.setdefault(stage,[]).append({'report':path,'rows':rows})
(R/'OBSERVED_REPORTS.json').write_text(json.dumps(observed,indent=2)+'\n')
result['observed_reports']={stage:{'reports':len(reports),'tests':sum(len(r['rows']) for r in reports),'failures':sum(len(row['failures']) for r in reports for row in r['rows']),'errors':sum(len(row['errors']) for r in reports for row in r['rows']),'skips':sum(row['skipped'] for r in reports for row in r['rows'])} for stage,reports in observed.items()}
result['source_binding_phases_verified']=phases
if 'FAILURE.txt' in data:
 result['state']='FAILED_ORIGINAL_RECEIVED_NO_ADMISSION';result['failure']=data['FAILURE.txt'].decode()
else:
 assert run['conclusion']==job['conclusion']=='success'
 assert len(result['stages'])==16 and all(v.get('compiled') for v in result['stages'].values())
 for label in ['generic-normal','generic-restored']:result['stages'][label]=audit.reports(out/label,P['generic_cases'])
 for name,m in P['generic_mutants'].items():result['stages'][name]=audit.reports(out/name,P['generic_cases'],m['witnesses'])
 for label in ['normal','restored']:result['stages'][label]=audit.reports(out/label,P['normal_cases'])
 result['stages']['legacy-modal']=audit.reports(out/'legacy-modal',P['legacy_cases'])
 for name,m in P['mutants'].items():
  assert sha(data['forms/'+name+'/'+m['path']])==m['sha256']
  original=data['initial/source/'+m['path']].decode();assert original.count(m['old'])==m['occurrences']
  assert original.replace(m['old'],m['new']).encode()==data['forms/'+name+'/'+m['path']]
  result['stages'][name]=audit.reports(out/name,P['normal_cases'],m['witnesses'])
 result['stages']['export']=audit.reports(out/'export',P['export_cases'])
 result['snapshot_insertion']=audit.snapshot_insertion(data['export/WAR.original.json'],data['export/WAR.json'])
 assert sha(data['export/WAR.original.json'])==P['original_war_sha256']
 assert json.loads(data['export/INSERTION.json'])==result['snapshot_insertion']
 result['runner_result']=json.loads(data['RESULT.json']);assert result['runner_result']['compiled_forms']==16 and result['runner_result']['generic_cases']==25 and result['runner_result']['generic_mutants']==7;assert result['runner_result']['state']=='EARLY_GATES_PASSED_ORIGINAL_RECEIVING_PENDING'
 result['state']='EARLY_GATES_RECEIVED_NOT_FEATURE_OR_CARD_ADMISSION'
(R/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k not in ['stages','failure']},indent=2))
