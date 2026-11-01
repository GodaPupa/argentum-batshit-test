import sys,json,hashlib,zipfile,contextlib,io
from pathlib import Path
from collections import Counter
api=json.loads(Path('payoff-api.json').read_text())
original=Path('payoff-receiver-original.py').read_text()
assert original.count("for k in ['initial','restored','final']:")==1
# Failed runner never reaches bind('restored'); final bind and restored source are separately required.
# Preserve the original receiver and original failed protocol; this is a diagnostic receipt, not acceptance.
receiver=original.replace("for k in ['initial','restored','final']:","for k in ['initial','final']:")
sys.argv=['receive.py','payoff-original.zip']
ns={}
with contextlib.redirect_stdout(io.StringIO()):exec(compile(receiver,'diagnostic-receiver','exec'),ns)
files=ns['files'];P=ns['P'];B=ns['B'];obs=ns['observed'];restored=ns['restored'];normal=ns['normal']
gitsha=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
run=api['run'];job=api['jobs']['jobs'];art=api['artifacts']['artifacts']
assert run['id']==41286630084 and run['head_sha']==B['validation'] and run['event']=='push' and run['run_attempt']==run['run_number']==1
assert run['status']=='completed' and run['conclusion']=='failure' and run['path']==B['workflow']
assert len(job)==len(art)==1 and job[0]['id']==124789138819 and job[0]['conclusion']=='failure'
assert art[0]['id']==13075204415 and not art[0]['expired'] and art[0]['size_in_bytes']==130762
assert art[0]['digest']=='sha256:'+ns['sha'](Path('payoff-original.zip').read_bytes())
assert art[0]['workflow_run']['id']==run['id'] and art[0]['workflow_run']['head_sha']==B['validation']
assert api['source']['sha']==B['source'] and api['source']['tree']['sha']==B['tree'] and api['source']['parents'][0]['sha']==P['base']
assert api['validation']['sha']==B['validation'] and api['validation']['tree']['sha']==B['validation_tree'] and api['validation']['parents'][0]['sha']==B['source']
delta=api['delta']['files']; vd=api['validation_delta']['files']
assert {f['filename'] for f in delta}==set(normal)|{P['record']+'/'+n for n in ['PROTOCOL.json','run.py','validation.just']}
for f in delta:
 b=normal.get(f['filename'])
 if b is None:b=files['protocol/'+f['filename'].split('/')[-1]]
 assert gitsha(b)==f['sha'],f['filename']
assert len(vd)==1 and vd[0]['filename']==B['workflow'] and vd[0]['sha']==B['workflow_blob']==gitsha(files['WORKFLOW.yml'])
assert b"AssertionError: ('draw_count', 9)" in files['FAILURE.txt']
assert 'FINAL_BINDING_FAILURE.txt' not in files and 'restored/PROTECTED.json' not in files
assert all(ns['witnesses'].values()) and len(ns['witnesses'])==3
assert all(not x['failed'] for x in obs['normal'].values()) and all(not x['failed'] for x in restored.values())
failed={f:sorted(n for n,r in rows.items() if r['failed']) for f,rows in obs.items()}
assert {f:len(v) for f,v in failed.items()}=={'normal':0,'stored_power':11,'draw_count':9,'success_gate':1}
missing=sorted(set(failed['stored_power'])-set(failed['draw_count']))
assert missing==['DF03 selection rejects the source and still accepts another own creature','DF04 selection rejects an opponent creature']
s=normal[P['test']].decode()
for name in missing:
 body=s.split('test("'+name+'") {',1)[1].split('\n    }',1)[0]
 assert 'getLifeTotal(me) shouldBe 25' in body and 'getHandSize' not in body
for form,w in P['required_witnesses'].items():
 assert s.splitlines()[w['line']-1].strip()==w['assertion']
for form,rows in obs.items():
 report=json.loads(files['observe-'+form+'/REPORT.json'])
 assert report['total']==30 and report['failed']==len(failed[form])
 assert Counter(r['name'] for r in report['rows'])==Counter(rows.keys())
 for r in report['rows']:
  assert bool(r['failures'] or r['errors'])==rows[r['name']]['failed'] and not r['errors'] and r['skipped']==0
assert json.loads(files['RESTORED_REPORT.json'])==json.loads(files['observe-restored/REPORT.json'])
unaffected=sum(not r['failed'] for f,rows in obs.items() if f!='normal' for r in rows.values())
assert unaffected==69
result=ns['result']
result.update(state='ORIGINAL_DIAGNOSTIC_RECEIVED_PROTOCOL_COUNT_MISMATCH',immutable_api_source_bindings=True,failed_identities=failed,unaffected_outcomes=unaffected,missing_draw_assertion_cases=missing,original_protocol_expected_failed=P['expected_failed'],original_failure_preserved=True,protected_initial_final_equal=True,restored_source_exact=True,normal_and_restored_pass=True,acceptance=False,diagnosis='Protocol incorrectly copied eleven power-mutant failures to draw mutant: DF03 and DF04 assert life payoff but contain no hand-size assertion. All nine draw assertions fail, including the exact declared line87 witness. No test assertion, report, or original protocol changed; full qualification remains pending.')
Path('payoff-audit.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='failed_identities'},indent=2))
