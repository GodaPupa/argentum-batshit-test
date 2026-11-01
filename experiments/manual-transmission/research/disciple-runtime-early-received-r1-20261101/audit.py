import json,zipfile,hashlib,subprocess,sys,re
from pathlib import Path
from collections import Counter
import xml.etree.ElementTree as ET
z=zipfile.ZipFile('early-original.zip'); f={n:z.read(n) for n in z.namelist()}
A=json.loads(Path('receiving-api.json').read_text()); D=json.loads(Path('receiving-bound.json').read_text())
P=json.loads(f['protocol/PROTOCOL.json']); B=json.loads(f['BINDING.json'])
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
base=json.loads(subprocess.check_output([sys.executable,'disciple-runtime-receive.py','early-original.zip']))
assert A['git/commits/'+B['source']]['tree']['sha']==B['tree']
assert A['git/commits/'+B['source']]['parents'][0]['sha']==P['base']
assert A['git/commits/'+B['validation']]['tree']['sha']==B['validation_tree']
assert A['git/commits/'+B['validation']]['parents'][0]['sha']==B['source']
delta=A['compare/'+B['source']+'...'+B['validation']]
assert delta['ahead_by']==1 and [(x['filename'],x['sha']) for x in delta['files']]==[(B['workflow'],B['workflow_blob'])]
run=A['actions/runs/41282463247']; job=A['actions/runs/41282463247/jobs']['jobs'][0]
assert run['id']==41282463247 and run['head_sha']==B['validation'] and run['event']=='push'
assert run['run_attempt']==run['run_number']==1 and run['status']=='completed' and run['conclusion']=='failure'
assert job['id']==124776990869 and job['run_attempt']==1 and job['conclusion']=='failure'
art=A['actions/runs/41282463247/artifacts']['artifacts'];assert len(art)==1
art=art[0];assert art['id']==13073926795 and art['size_in_bytes']==base['zip_bytes']==614916
assert art['digest']=='sha256:'+base['zip_sha256'] and not art['expired']
assert art['workflow_run']['head_sha']==B['validation'] and art['workflow_run']['id']==run['id']
for p,r in D.items():
 b=r['content'].encode();assert blob(b)==r['sha']
 n='WORKFLOW.yml' if p==B['workflow'] else 'protocol/'+p.split('/')[-1] if p.startswith(P['record']+'/') else 'compile-normal/source/'+p
 assert f[n]==b,(p,n)
runner=f['protocol/run.py'].decode()
assert runner.index("for form in P['forms']:stage(form,'compile')")<runner.index("results={form:stage(form,'observe')")
rows={}
for form in P['forms']+['restored']:
 stage='observe-'+form;names=[n for n in f if n.startswith(stage+'/junit/') and n.endswith('.xml')];assert len(names)==1
 cs=list(ET.fromstring(f[names[0]]).iter('testcase'));assert len(cs)==29
 rr={}
 for c in cs:
  failures=c.findall('failure');assert not c.findall('error') and not c.findall('skipped')
  rr[c.get('name')]={'failed':bool(failures),'failures':[{'type':x.get('type'),'message':x.get('message'),'line':re.findall(r'DiscipleOfFreyaliseScenarioTest.kt:(\d+)',x.text or '')[:1]} for x in failures]}
 report=json.loads(f[stage+'/REPORT.json']);assert report['total']==29 and report['failed']==sum(x['failed'] for x in rr.values())
 assert Counter(x['name'] for x in report['rows'])==Counter(rr.keys())
 for r in report['rows']:assert bool(r['failures'] or r['errors'])==rr[r['name']]['failed'] and r['skipped']==0
 rows[form]=rr
assert rows['normal']==rows['restored']
witnesses=[]
for form,w in P['required_witnesses'].items():
 changed=[n for n in rows['normal'] if rows['normal'][n]!=rows[form][n]]
 assert changed==[w['case']],changed
 assert not rows['normal'][w['case']]['failed'] and rows[form][w['case']]['failed']
 source=f['observe-'+form+'/source/'+P['test']].decode().splitlines()
 assert source[w['line']-1].strip()==w['assertion']
 witnesses.append(dict(form=form,**w,raw_failure=rows[form][w['case']],unaffected_outcomes=28))
assert all(base['candidate_witnesses'].values())
failures={n:r for n,r in rows['normal'].items() if r['failed']}
assert len(failures)==10 and all(r['failures'][0]['message']=='org.opentest4j.AssertionFailedError: expected:<25> but was:<20>' for r in failures.values())
result=dict(**base,metadata_verified=True,immutable_sources_verified=True,compile_before_behavior_verified=True,normal_restored_identical=True,raw_report_agreement=True,witnesses=witnesses,unaffected_outcomes=84,normal_failures=failures,source_bindings={p:r['sha'] for p,r in D.items()},protected=json.loads(f['initial/PROTECTED.json']),binding=B)
result['state']='ORIGINAL_DIAGNOSTIC_RECEIVED_FAILURE_NOT_ADMISSION'
Path('early-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['state','zip_bytes','zip_sha256','counts','restored_failed','unaffected_outcomes']},indent=2))
