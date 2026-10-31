from pathlib import Path
import json,hashlib,zipfile,xml.etree.ElementTree as ET,collections,re
R=Path(__file__).parent
meta=json.loads((R/'metadata.json').read_text());pins=json.loads((R/'pins.json').read_text());p=pins['protocol']
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
raw=(R/'original.zip').read_bytes()
assert len(raw)==meta['size_in_bytes']==331955
assert sha(raw)==meta['digest'].removeprefix('sha256:')=='31739cb48ea58747adfc7dc3e7d8ea402839eb0e997af7527d9de367cc33e9a3'
with zipfile.ZipFile(R/'original.zip') as z:
 assert len(z.namelist())==len(set(z.namelist())) and z.testzip() is None
 assert all(not n.startswith('/') and '..' not in Path(n).parts for n in z.namelist())
 files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
j=lambda n:json.loads(files[n])
m=j('MANIFEST.json');assert set(m)==set(files)-{'MANIFEST.json'}
assert all(sha(files[k])==v for k,v in m.items())
assert meta['id']==13059373327 and meta['workflow_run']['id']==41245535657
run=meta['run'];assert run['id']==41245535657 and run['run_attempt']==run['run_number']==1
assert run['status']=='completed' and run['conclusion']=='failure' and run['event']=='push'
assert run['head_sha']==pins['validation'] and run['path']==pins['workflow']
jobs=meta['jobs']['jobs'];assert len(jobs)==1 and jobs[0]['id']==124668066700 and jobs[0]['run_id']==41245535657
assert jobs[0]['status']=='completed' and jobs[0]['conclusion']=='failure'
assert next(x for x in jobs[0]['steps'] if x['number']==8)['conclusion']=='success'
for key,parent in [('source_commit',p['base']),('validation_commit',pins['source'])]:
 c=meta[key];assert [x['sha'] for x in c['parents']]==[parent]
 assert c['tree']['sha']==pins['tree' if key=='source_commit' else 'validation_tree']
assert pins['validation_delta']==[dict(path=pins['workflow'],sha=pins['workflow_blob'],status='added')]
assert blob(files['WORKFLOW.yml'])==pins['workflow_blob']
assert j('BINDING.json')=={k:pins[k] for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']}|dict(run='41245535657',attempt=1,run_number=1)
assert all(d['status']=='added' for d in meta['delta'])
assert sorted(d['path'] for d in meta['delta'])==sorted(p['source_delta'])
for d in meta['delta']:
 path=d['path']
 n='protocol/'+path[len(p['record'])+1:] if path.startswith(p['record']+'/') else 'scenario.kt' if path==p['test'] else 'protocol/forms/normal.kt'
 assert blob(files[n])==d['sha'],(path,n)
assert j('protocol/PROTOCOL.json')==p
protected={'game-server':'88b7cb3260dee8b710d5278361dbcb57962f3ab4','mtg-sdk':'51b097156bff57e30aeca2d6338ccfc5a62a65e3','rules-engine':'ba2e20898cc69d709d895933952301c8ff23ce2e','web-client':'0ce36368d74a34f392e85b261771863a2a9d004c','mtg-sets/src/test/resources/snapshots/cards/MH3.json':'25b01370752b8d2fe97389c453a300dce833531a'}
root={e['path']:e['sha'] for e in meta['source_tree']['tree']}
for k,v in protected.items():
 if '/' not in k:assert root[k]==v
for label in ['initial','restored','final']:assert j(label+'/PROTECTED.json')==protected
assert j('ALL_FORMS_COMPILED.json')==dict(forms=p['forms'],all_compiled_before_behavior=True)
rows={};counts={}
for f in p['forms']:
 for stage in ['compile','observe']:
  pre=f'{stage}-{f}/';assert sha(files[pre+'CARD.kt'])==p['form_hashes'][f]
  assert sha(files[pre+'scenario.kt'])==p['test_form_hashes'].get(f,p['test_sha256'])
  reports=[n for n in files if n.startswith(pre+'junit/') and n.endswith('.xml')]
  if stage=='compile':
   assert not reports and files[pre+'exit.txt'].strip()==b'0'
   assert b'BUILD SUCCESSFUL' in files[pre+'output.log'] and b':mtg-sets:2024:tests:compileTestKotlin' in files[pre+'output.log']
   assert j(pre+'SUMMARY.json')==dict(compile_passed=True,rc=0,form=f,behavioral_evidence=False)
  else:
   assert len(reports)==1
   x=ET.fromstring(files[reports[0]]);rs=list(x.iter('testcase'));actual=[]
   for r in rs:
    actual.append(dict(report=reports[0][len(pre+'junit/'):],classname=r.get('classname'),name=r.get('name'),failures=[dict(type=e.get('type'),message=e.get('message'),text=e.text) for e in r.findall('failure')],errors=[dict(type=e.get('type'),message=e.get('message'),text=e.text) for e in r.findall('error')],skipped=len(r.findall('skipped'))))
   assert collections.Counter(r['name'] for r in actual)==collections.Counter(p['case_names'])
   assert len(actual)==28 and all(r['classname']==p['class'] and r['skipped']==0 and not r['errors'] for r in actual)
   failed=sum(bool(r['failures']) for r in actual);rc=int(files[pre+'exit.txt'])
   assert x.get('tests')=='28' and x.get('skipped')==x.get('errors')=='0' and int(x.get('failures'))==failed
   assert rc==int(bool(failed))
   assert j(pre+'SUMMARY.json')==dict(form=f,rc=rc,rows=actual,total=28,failed=failed,skipped=0,admitted=False)
   rows[f]={r['name']:r for r in actual};counts[f]=dict(total=28,failed=failed)
assert j('RESULT.json')==dict(state='DIAGNOSTIC_OBSERVED_ORIGINAL_RECEIVING_REQUIRED',counts=counts,feature_admitted=False,card_admitted=False,full_qualification=False)
assert sha(files['scenario.kt'])==p['test_sha256']
witnesses={};excluded={}
need={'life2':'expected:<17> but was:<18>','white':'expected:<1> but was:<0>','allow_transform':'expected:<Disciple of Freyalise> but was:<Garden of Freyalise>','actual_error':'Expected null but actual was "You can only play lands on your turn"','require_complete':'expected:<true> but was:<false>'}
for f,fragment in need.items():
 c=p['controls'][f];normal=rows['normal'][c['case']];r=rows[f][c['case']]
 assert not normal['failures'] and len(r['failures'])==1
 e=r['failures'][0];assert e['type']=='org.opentest4j.AssertionFailedError' and fragment in e['message']
 assert f'DiscipleOfFreyaliseScenarioTest.kt:{c["assertion_line"]}' in e['text']
 assert files[f'observe-{f}/scenario.kt'].decode().splitlines()[c['assertion_line']-1].strip()==c['assertion']
 witnesses[f]=dict(case=c['case'],line=c['assertion_line'],message=e['message'],type=e['type'],normal_pass=True)
for f in ['actual_error','require_complete']:
 changed=p['controls'][f]['case']
 for name,n in rows['normal'].items():
  if name!=changed:assert bool(n['failures'])==bool(rows[f][name]['failures']), (f,name)
assert not rows['normal']['DF19 exile permission castOnly=true distinguishes Garden play']['failures']
excluded['no_tapped']='Earlier No pending YesNoDecision at line226, not declared tapped assertion line228.'
excluded['life1']='Localization only; normal already fails at life total. No negative qualification credit.'
norm=[]
for r in rows['normal'].values():
 if r['failures']:
  es=[dict(type=e['type'],message=e['message'],lines=re.findall(r'DiscipleOfFreyaliseScenarioTest.kt:\d+',e['text'])[:3]) for e in r['failures']]
  norm.append(dict(name=r['name'],failures=es))
result=dict(state='ORIGINAL_DIAGNOSTIC_VERIFIED_NOT_ADMISSION',pins={k:pins[k] for k in ['source','tree','validation','validation_tree','workflow','workflow_blob']},run=41245535657,job=124668066700,attempt=1,artifact=13059373327,archive_bytes=len(raw),archive_sha256=sha(raw),manifest_entries=len(m),all_eight_forms_compiled_before_behavior=True,counts=counts,skips_per_form=0,exact_identities_per_form=28,verified_witnesses=witnesses,excluded_controls=excluded,normal_failures=norm,df19_cast_only_rejected_with_actual_error=True,protected_bindings=protected,source_restored=True,restoration_limit='Original frozen runner checked clean tracked source and protected trees after restoration; no restored behavioral rerun was declared or performed.',card_admitted=False,feature_admitted=False,full_qualification=False,remaining_gates=p['remaining_admission_gates'])
(R/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
(R/'MANIFEST.json').write_bytes(files['MANIFEST.json'])
print(json.dumps({k:result[k] for k in ['state','archive_bytes','manifest_entries','counts','verified_witnesses','excluded_controls']},indent=2))
