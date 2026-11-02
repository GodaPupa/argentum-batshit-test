"""Receive the original compile stop; no behavioral acceptance is possible."""
from pathlib import Path
import json,re,hashlib
from receive import archive_files,sha,gitsha
def receive(archive,api_file,expected_file,out):
 b=Path(archive).read_bytes();a=json.loads(Path(api_file).read_text());e=json.loads(Path(expected_file).read_text())
 run=a['run'];jobs=a['jobs']['jobs'];artifact=a['artifact']
 assert run['id']==e['run'] and run['head_sha']==e['validation'] and run['path']==e['workflow'] and run['event']=='push'
 assert run['run_number']==run['run_attempt']==1 and run['status']=='completed' and run['conclusion']=='failure'
 assert a['jobs']['total_count']==len(jobs)==1 and jobs[0]['id']==e['job'] and jobs[0]['run_id']==e['run'] and jobs[0]['head_sha']==e['validation']
 assert jobs[0]['run_attempt']==1 and jobs[0]['status']=='completed' and jobs[0]['conclusion']=='failure'
 for k,s,t,parent in [('source_commit','source','tree','parent'),('validation_commit','validation','validation_tree','source')]:
  assert a[k]['sha']==e[s] and a[k]['tree']['sha']==e[t] and [x['sha'] for x in a[k]['parents']]==[e[parent]]
 assert a['source_compare']['status']=='ahead' and a['source_compare']['ahead_by']==1
 assert {f['filename']:f['status'] for f in a['source_compare']['files']}==e['source_files']
 assert a['validation_compare']['status']=='ahead' and a['validation_compare']['ahead_by']==1
 assert [(f['filename'],f['status']) for f in a['validation_compare']['files']]==[(e['workflow'],'added')]
 assert a['artifacts']['total_count']==1 and a['artifacts']['artifacts']==[artifact]
 assert artifact['id']==e['artifact'] and artifact['name']==e['artifact_name'] and not artifact['expired']
 assert artifact['workflow_run']['id']==e['run'] and artifact['workflow_run']['head_sha']==e['validation']
 assert len(b)==artifact['size_in_bytes']==e['artifact_bytes'] and artifact['digest']=='sha256:'+sha(b)=='sha256:'+e['artifact_sha256']
 f=archive_files(archive);binding=json.loads(f['BINDING.json'])
 for k,v in {'SOURCE':e['source'],'SOURCE_TREE':e['tree'],'validation':e['validation'],'validation_tree':e['validation_tree'],'WORKFLOW':e['workflow'],'workflow_blob':e['workflow_blob']}.items():assert binding[k]==v,k
 assert int(binding['GITHUB_RUN_ID'])==e['run'] and int(binding['GITHUB_RUN_ATTEMPT'])==int(binding['GITHUB_RUN_NUMBER'])==1
 assert gitsha(f['WORKFLOW.yml'])==e['workflow_blob']
 for p,h in e['frozen_protocol_files'].items():assert sha(f['protocol/'+p])==h,p
 p=json.loads(f['protocol/PROTOCOL.json']);assert p['base']==e['parent']
 assert f['initial/TRACKED.diff']==b'' and f['forms/normal/TRACKED.diff']==b''
 for path,h in p['bindings'].items():assert sha(f['initial/source/'+path])==h,path
 for path,h in p['protected_original_bindings'].items():
  assert sha(f['protected-originals/'+path])==h,path
  if path not in p['authorized_production_paths']+['docs/card-sdk-language-reference.md']:assert f['protected-originals/'+path]==f['initial/source/'+path]
 for path,h in p['normal_forms'].items():assert sha(f['forms/normal/'+path])==h,path
 assert {n.split('/')[0] for n in f if n.startswith('compile-')}=={'compile-normal'}
 assert not any(n.endswith('.xml') for n in f),'No behavior ran'
 assert f['compile-normal/exit.txt'].strip()==b'1'
 log=f['compile-normal/output.log'].decode()
 assert '> Task :rules-engine:compileKotlin\n' in log and '> Task :rules-engine:compileTestKotlin FAILED' in log
 assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:compileTestKotlin']
 errors=re.findall(r'^e: .*?/scenarios/([^\n]+)$',log,re.M)
 expected=["DepartureTimeChoiceCaptureTest.kt:68:65 Argument type mismatch: actual type is 'TriggerSpec', but 'EventPattern' was expected.",
 "DepartureTimeNegativeWitnessTest.kt:28:76 Argument type mismatch: actual type is 'TriggerSpec', but 'EventPattern' was expected.",
 "DepartureTimeNegativeWitnessTest.kt:50:76 Argument type mismatch: actual type is 'TriggerSpec', but 'EventPattern' was expected.",
 "SourceDepartureChoiceRepairTest.kt:249:57 Argument type mismatch: actual type is 'TriggerSpec', but 'EventPattern' was expected."]
 assert sorted(errors)==sorted(expected) and len(re.findall(r'^e: ',log,re.M))==4
 assert json.loads(f['RESTORATION.json'])=={'normal_restored':True,'tracked_diff':'','status':''}
 assert 'Every normal/mutant must compile before behavior' in f['FAILURE.txt'].decode()
 result={'state':'ORIGINAL_COMPILE_FAILURE_RECEIVED_NOT_BEHAVIORAL_EVIDENCE','source':e['source'],'tree':e['tree'],'validation':e['validation'],
  'run':e['run'],'job':e['job'],'attempt':1,'artifact':e['artifact'],'bytes':len(b),'sha256':sha(b),'manifest_entries':len(f)-1,
  'initial_bindings':len(p['bindings']),'protected_original_bindings':len(p['protected_original_bindings']),'normal_source_forms_verified':4,
  'production_compilation':'passed','normal_test_compilation':'failed','behavioral_rows':0,'negative_witnesses':0,'restoration':'normal restored; clean tracked diff',
  'compiler_errors':errors,'cause':'Four focused test factories passed TriggerSpec to TriggeredAbility.create(EventPattern). TriggerSpec.event is the required existing event value; default SELF binding equals Triggers.EntersBattlefield.binding.',
  'bounded_next':'Correct exactly four fixture factory arguments to Triggers.EntersBattlefield.event. Preserve every assertion/identity and all production bytes. Freeze new immutable source and compile all six forms before behavior; no unchanged rerun.',
  'remaining_gates':['all normal/mutant compilation','83normal/restored and five exact negative forms','original early receiving','full same-source compatibility and original receiving'],'accepted':False}
 Path(out).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k not in ['compiler_errors','remaining_gates']}))
if __name__=='__main__':
 import sys
 receive(*sys.argv[1:])
