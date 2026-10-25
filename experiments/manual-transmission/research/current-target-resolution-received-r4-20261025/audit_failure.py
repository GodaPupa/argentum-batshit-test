from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as E,re
R=Path(__file__).parent;A=R/'raw';M=json.loads((R/'METADATA.json').read_text());P=json.loads((A/'protocol/PROTOCOL.json').read_text());S=Path('/workspace/scratch/390bd1587480/resolver-r1')
def source_file(path):
 if (R.parent/'resolver-r4/source'/path).exists():return R.parent/'resolver-r4/source'/path
 for v in [2,3,4]:
  if f'/current-target-resolution-r{v}-20261025/' in path:return R.parent/f'resolver-r{v}'/Path(path).name
 return S/path
h=lambda b:hashlib.sha256(b).hexdigest();blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
z=R/'original-12590367691.zip';assert z.stat().st_size==M['artifact']['size_in_bytes']==1856929;assert 'sha256:'+h(z.read_bytes())==M['artifact']['digest']
with zipfile.ZipFile(z) as Z:
 assert len(Z.namelist())==len(set(Z.namelist())) and Z.testzip() is None
 assert set(Z.namelist())=={str(p.relative_to(A)) for p in A.rglob('*') if p.is_file()}
 for name in Z.namelist():assert Z.read(name)==(A/name).read_bytes()
manifest={}
for line in (A/'SHA256SUMS').read_text().splitlines():
 digest,name=line.split('  ',1);assert name not in manifest;assert h((A/name).read_bytes())==digest;manifest[name]=digest
assert set(manifest)=={str(p.relative_to(A)) for p in A.rglob('*') if p.is_file()}-{'SHA256SUMS'}
source='457a644a0f24289e90241f0bf12fa40a933edb43';validation='9296091334ff6aa18e2d9382da896f2547958498'
assert M['source']['sha']==source and M['source']['tree']['sha']=='d3306e35cc1584e4f587118202ec25b924b51c84';assert M['source']['parents'][0]['sha']=='ea5538b6bf6935f2e15cf3c29db0d1f5917ff128'
assert M['validation']['sha']==validation and M['validation']['parents'][0]['sha']==source
assert M['validation']['tree']['sha']=='dfd084bcbc60f4d61ae82970449d662f71a9435d'
assert M['main']['object']['sha']=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
assert M['run']['id']==40171122250 and M['run']['head_sha']==validation and M['run']['run_attempt']==M['run']['run_number']==1
assert M['run']['workflow_id']==395714200 and M['run']['conclusion']=='failure' and M['run']['status']=='completed'
assert M['jobs']['total_count']==1 and M['jobs']['jobs'][0]['id']==121101976037
assert [s['number'] for s in M['jobs']['jobs'][0]['steps'] if s['conclusion']=='failure']==[19]
assert M['artifact']['workflow_run']['head_sha']==validation and M['artifact']['workflow_run']['id']==40171122250
assert blob((A/'protocol/WORKFLOW.yml').read_bytes())==M['validation_delta']['files'][0]['sha']=='a784d1547d9e28dedd2ab795c21db041654fde0a'
assert len(M['validation_delta']['files'])==1
assert len(M['delta']['files'])==26
for f in M['delta']['files']:assert blob(source_file(f['filename']).read_bytes())==f['sha']
for name in ['gate.py','PROTOCOL.json','DESIGN.md']:assert (A/'protocol'/name).read_bytes()==(R.parent/'resolver-r4'/name).read_bytes()
for path,digest in P['implementation_sha256'].items():assert h(source_file(path).read_bytes())==digest
assert not (A/'FINAL_TRACKED_CHANGES.txt').read_bytes()
B=json.loads((A/'BINDING.json').read_text());assert B['source']==source and B['validation']==validation and B['protected_bindings']==143 and B['authorized_resolver_successor']
assert B['delta']==[[('M' if f['status']=='modified' else 'A'),f['filename']] for f in M['delta']['files']]
assert (A/'SOURCE_DELTA.txt').read_text().splitlines()==['\t'.join(x) for x in B['delta']]
for stage in ['normal','mutant']:
 d=A/'compilation'/stage;r=json.loads((d/'RECEIPT.json').read_text());log=(d/'gradle.log').read_text()
 assert int((d/'exit.txt').read_text())==0 and r['exit']==0 and r['source']==source and r['validation']==validation and r['run']=='40171122250'
 assert not r['behavioral_evidence'] and r['tests']==0 and not list(d.rglob('*.xml'))
 assert h((d/'gradle.log').read_bytes())==r['log_sha256'];assert 'BUILD SUCCESSFUL' in log and not re.search(r'^e: ',log,re.M)
 for path,digest in r['implementation_sha256'].items():
  b=(d/Path(path).name).read_bytes();assert h(b)==digest
  normal=source_file(path).read_text()
  mutated=normal.replace('val ref = slots?.get(index)?.objectRef','val ref: com.wingedsheep.engine.state.ObjectRef? = null') if path.endswith('StackResolver.kt') else normal.replace('requirements[i].id?.let','requirements.getOrNull(offset + j)?.id?.let')
  assert b.decode()==(normal if stage=='normal' else mutated)
results={}
for stage,field,count in [('recording','recording_cases',5),('actor','actor_cases',39),('targeted','targeted_cases',58)]:
 cases={};total=0
 for file in (A/stage/'junit').rglob('TEST-*.xml'):
  s=E.parse(file).getroot();rows=s.findall('.//testcase');assert int(s.get('tests'))==len(rows)
  for row in rows:
   assert not any(row.find(t) is not None for t in ['failure','error','skipped']);cls=row.get('classname');cases.setdefault(cls.rsplit('.',1)[-1],[]).append(cls+'::'+row.get('name'));total+=1
 assert total==count;assert {k:sorted(v) for k,v in cases.items()}==P[field];assert int((A/stage/'exit.txt').read_text())==0;results[stage]=count
log=(A/'mutation/gradle.log').read_text();assert int((A/'mutation/exit.txt').read_text())!=0
rows=[]
for file in (A/'mutation').rglob('TEST-*.xml'):rows+=E.parse(file).getroot().findall('.//testcase')
assert len(rows)==29
failures={};skips=[]
for row in rows:
 ident=row.get('classname')+'::'+row.get('name');f=row.find('failure')
 if f is not None:
  assert f.get('type')=='org.opentest4j.AssertionFailedError';frame=int(re.search(re.escape(row.get('classname').rsplit('.',1)[-1])+r'\.kt:(\d+)',f.text)[1]);failures[ident]=frame
 else:
  assert row.find('skipped') is None and row.find('error') is None;skips.append(ident)
assert failures==P['mutation_first_frames'] and len(failures)==14
assert sorted(skips)==sorted(set(P['mutation_cases'])-set(P['mutation_first_frames'])) and len(skips)==15
assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
stages={}
for stage,expected_count in [('focused',849),('broad',6894)]:
 rows=[];bad={}
 for file in (A/stage).rglob('TEST-*.xml'):
  for row in E.parse(file).getroot().findall('.//testcase'):
   rows.append(row);assert row.find('error') is None and row.find('skipped') is None
   f=row.find('failure')
   if f is not None:
    assert f.get('type')=='java.util.NoSuchElementException'
    frame=int(re.search(r'ModalTriggeredAbilityOnStackTest\.kt:(\d+)',f.text)[1]);bad[row.get('classname')+'::'+row.get('name')]=frame
 assert len(rows)==expected_count
 if stage=='focused':assert not bad and int((A/stage/'exit.txt').read_text())==0
 else:assert sorted(bad.values())==[79,100] and int((A/stage/'exit.txt').read_text())!=0
 stages[stage]=dict(cases=len(rows),passes=len(rows)-len(bad),failures=bad,skips=0,errors=0)
for stage in ['ordinary','assay']:assert not (A/stage).exists()
result=dict(status='VERIFIED_FAILURE_TARGETLESS_MODAL_TRIGGER_NOT_ENQUEUED',source=source,validation=validation,run=40171122250,job=121101976037,attempt=1,artifact=M['artifact'],manifest_entries=len(manifest),normal_and_mutant_compilation='PASS',positive_cases=results,mutation_failures=failures,mutation_passes=skips,mutation_skips=[],stages=stages,diagnosis='Fresh targetless modal trigger constructs a zero-occurrence plan; putTriggeredAbility has no TargetsComponent for zero targets, so separate authority installation returns error before enqueue. Existing ModalTriggeredAbilityOnStackTest fails at79/100. Focused849 and all14 mutation witnesses passed. Ordinary/Assay not executed.',next='Preserve original targetless modal assertions; do not fabricate targeting authority when no target occurrences exist. Qualify bounded producer repair on new immutable source with these original cases in the early targeted gate.',official_games=0,official_seeds=0,precision_reserved=2)
(R/'VERIFIED_FAILURE.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:result[k] for k in ['status','manifest_entries','positive_cases','stages']}))
