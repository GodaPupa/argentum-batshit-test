from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as E,re
R=Path(__file__).parent;A=R/'raw';M=json.loads((R/'METADATA.json').read_text());P=json.loads((A/'protocol/PROTOCOL.json').read_text());S=Path('/workspace/scratch/390bd1587480/resolver-r1')
def source_file(path):
 for v in [2,3]:
  if f'/current-target-resolution-r{v}-20261025/' in path:return R.parent/f'resolver-r{v}'/Path(path).name
 return S/path
h=lambda b:hashlib.sha256(b).hexdigest();blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
z=R/'original-12588704263.zip';assert z.stat().st_size==M['artifact']['size_in_bytes']==284107;assert 'sha256:'+h(z.read_bytes())==M['artifact']['digest']
with zipfile.ZipFile(z) as Z:
 assert len(Z.namelist())==len(set(Z.namelist())) and Z.testzip() is None
 assert set(Z.namelist())=={str(p.relative_to(A)) for p in A.rglob('*') if p.is_file()}
 for name in Z.namelist():assert Z.read(name)==(A/name).read_bytes()
manifest={}
for line in (A/'SHA256SUMS').read_text().splitlines():
 digest,name=line.split('  ',1);assert name not in manifest;assert h((A/name).read_bytes())==digest;manifest[name]=digest
assert set(manifest)=={str(p.relative_to(A)) for p in A.rglob('*') if p.is_file()}-{'SHA256SUMS'}
source='ea5538b6bf6935f2e15cf3c29db0d1f5917ff128';validation='d5603e630a586ac87ad60ea7201fb472fdb3cda1'
assert M['source']['sha']==source and M['source']['tree']['sha']=='1060b67da209e93116c6a3b88860888f790fb20f';assert M['source']['parents'][0]['sha']=='1b508f6b2549a39f5d02a4abae044edfc73611f0'
assert M['validation']['sha']==validation and M['validation']['parents'][0]['sha']==source
assert M['validation']['tree']['sha']=='02f6aa6d14522d96c59fbf8e4328699a69e85e69'
assert M['main']['object']['sha']=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
assert M['run']['id']==40166823499 and M['run']['head_sha']==validation and M['run']['run_attempt']==M['run']['run_number']==1
assert M['run']['workflow_id']==395677752 and M['run']['conclusion']=='failure' and M['run']['status']=='completed'
assert M['jobs']['total_count']==1 and M['jobs']['jobs'][0]['id']==121089477587
assert [s['number'] for s in M['jobs']['jobs'][0]['steps'] if s['conclusion']=='failure']==[18]
assert M['artifact']['workflow_run']['head_sha']==validation and M['artifact']['workflow_run']['id']==40166823499
assert blob((A/'protocol/WORKFLOW.yml').read_bytes())==M['validation_delta']['files'][0]['sha']=='74430c924eb2b9c04cf8eaf04cac995767ad2612'
assert len(M['validation_delta']['files'])==1
assert len(M['delta']['files'])==21
for f in M['delta']['files']:assert blob(source_file(f['filename']).read_bytes())==f['sha']
for name in ['gate.py','PROTOCOL.json','DESIGN.md']:assert (A/'protocol'/name).read_bytes()==(R.parent/'resolver-r3'/name).read_bytes()
for path,digest in P['implementation_sha256'].items():assert h((S/path).read_bytes())==digest
assert not (A/'FINAL_TRACKED_CHANGES.txt').read_bytes()
B=json.loads((A/'BINDING.json').read_text());assert B['source']==source and B['validation']==validation and B['protected_bindings']==143 and B['authorized_resolver_successor']
assert B['delta']==[[('M' if f['status']=='modified' else 'A'),f['filename']] for f in M['delta']['files']]
assert (A/'SOURCE_DELTA.txt').read_text().splitlines()==['\t'.join(x) for x in B['delta']]
for stage in ['normal','mutant']:
 d=A/'compilation'/stage;r=json.loads((d/'RECEIPT.json').read_text());log=(d/'gradle.log').read_text()
 assert int((d/'exit.txt').read_text())==0 and r['exit']==0 and r['source']==source and r['validation']==validation and r['run']=='40166823499'
 assert not r['behavioral_evidence'] and r['tests']==0 and not list(d.rglob('*.xml'))
 assert h((d/'gradle.log').read_bytes())==r['log_sha256'];assert 'BUILD SUCCESSFUL' in log and not re.search(r'^e: ',log,re.M)
 for path,digest in r['implementation_sha256'].items():
  b=(d/Path(path).name).read_bytes();assert h(b)==digest
  normal=(S/path).read_text()
  mutated=normal.replace('val ref = slots?.get(index)?.objectRef','val ref: com.wingedsheep.engine.state.ObjectRef? = null') if path.endswith('StackResolver.kt') else normal.replace('requirements[i].id?.let','requirements.getOrNull(offset + j)?.id?.let')
  assert b.decode()==(normal if stage=='normal' else mutated)
results={}
for stage,field,count in [('recording','recording_cases',5),('actor','actor_cases',39),('targeted','new_test_cases',42)]:
 cases={};total=0
 for file in (A/stage/'junit').rglob('TEST-*.xml'):
  s=E.parse(file).getroot();rows=s.findall('.//testcase');assert int(s.get('tests'))==len(rows)
  for row in rows:
   assert not any(row.find(t) is not None for t in ['failure','error','skipped']);cls=row.get('classname');cases.setdefault(cls.rsplit('.',1)[-1],[]).append(cls+'::'+row.get('name'));total+=1
 assert total==count;assert {k:sorted(v) for k,v in cases.items()}==P[field];assert int((A/stage/'exit.txt').read_text())==0;results[stage]=count
log=(A/'mutation/gradle.log').read_text();assert int((A/'mutation/exit.txt').read_text())!=0
rows=[]
for file in (A/'mutation').rglob('TEST-*.xml'):rows+=E.parse(file).getroot().findall('.//testcase')
assert len(rows)==22
failures={};skips=[]
for row in rows:
 ident=row.get('classname')+'::'+row.get('name');f=row.find('failure')
 if f is not None:
  assert f.get('type')=='org.opentest4j.AssertionFailedError';frame=int(re.search(r'CurrentTargetResolutionTest\.kt:(\d+)',f.text)[1]);failures[ident]=frame
 else:
  assert row.find('skipped') is None and row.find('error') is None;skips.append(ident)
assert failures==P['mutation_first_frames'] and len(failures)==12
assert sorted(skips)==sorted(set(P['new_test_cases']['CurrentTargetResolutionTest'])-set(P['mutation_first_frames'])) and len(skips)==10
assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
focused=[];focused_failures={}
for file in (A/'focused').rglob('TEST-*.xml'):
 for row in E.parse(file).getroot().findall('.//testcase'):
  ident=row.get('classname')+'::'+row.get('name');focused.append(ident)
  assert row.find('error') is None and row.find('skipped') is None
  f=row.find('failure')
  if f is not None:
   assert f.get('type')=='org.opentest4j.AssertionFailedError'
   assert 'currentTargetPlan: Expected null' in f.text
   assert 'ModalTargetCaptureTest.kt:113' in f.text
   focused_failures[ident]=113
assert len(focused)==842 and len(set(focused))==842
assert focused_failures=={'com.wingedsheep.engine.scenarios.ModalTargetCaptureTest::MC08 provenance leaves existing target views and events unchanged':113}
assert int((A/'focused/exit.txt').read_text())!=0
assert re.findall(r'^> Task (\S+) FAILED$',(A/'focused/gradle.log').read_text(),re.M)==[':rules-engine:test']
for stage in ['broad','ordinary','assay']:assert not (A/stage).exists()
result=dict(status='VERIFIED_FAILURE_ORIGINAL_MC08_TARGET_VIEW_COMPATIBILITY',source=source,validation=validation,run=40166823499,job=121089477587,attempt=1,artifact=M['artifact'],manifest_entries=len(manifest),normal_and_mutant_compilation='PASS',positive_cases=results,mutation_failures=failures,mutation_passes=skips,mutation_skips=[],focused=dict(cases=842,passed=841,failures=focused_failures,errors=0,skips=0),diagnosis='Fresh tracked modal trigger installs currentTargetPlan on TargetsComponent, while legacy continuation has no plan. Existing MC08 component equality assertion correctly detects changed target view. All12 negative assertions passed receiving; focused failure is normal production compatibility, not negative evidence. Broad/ordinary/Assay unexecuted.',next='Repair current-authority storage/transport preserving original MC08 assertion, original target views and full per-occurrence resolution; qualify new immutable source after bounded review.',official_games=0,official_seeds=0,precision_reserved=2)
(R/'VERIFIED_FAILURE.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:result[k] for k in ['status','manifest_entries','positive_cases','focused']}))
