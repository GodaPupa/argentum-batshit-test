from pathlib import Path
import hashlib,json,zipfile,xml.etree.ElementTree as E,re
R=Path(__file__).parent;A=R/'raw';M=json.loads((R/'METADATA.json').read_text());P=json.loads((A/'protocol/PROTOCOL.json').read_text());S=Path('/workspace/scratch/390bd1587480/resolver-r1')
h=lambda b:hashlib.sha256(b).hexdigest();blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
z=R/'original-12586165301.zip';assert z.stat().st_size==M['artifact']['size_in_bytes']==173314;assert 'sha256:'+h(z.read_bytes())==M['artifact']['digest']
with zipfile.ZipFile(z) as Z:
 assert len(Z.namelist())==len(set(Z.namelist())) and Z.testzip() is None
 assert set(Z.namelist())=={str(p.relative_to(A)) for p in A.rglob('*') if p.is_file()}
 for name in Z.namelist():assert Z.read(name)==(A/name).read_bytes()
manifest={}
for line in (A/'SHA256SUMS').read_text().splitlines():
 digest,name=line.split('  ',1);assert name not in manifest;assert h((A/name).read_bytes())==digest;manifest[name]=digest
assert set(manifest)=={str(p.relative_to(A)) for p in A.rglob('*') if p.is_file()}-{'SHA256SUMS'}
source='41dfad5616b0143df07dae6aa098539085a94a99';validation='818ff6ef7d597b9323b028497814ca3fe378b1f0'
assert M['source']['sha']==source and M['source']['tree']['sha']=='6b05894d4e3fc1857ef2c93e907ea08905514624';assert M['source']['parents'][0]['sha']==P['base']
assert M['validation']['sha']==validation and M['validation']['parents'][0]['sha']==source
assert M['validation']['tree']['sha']=='cfffd8db43c49daafd3f55489f972d59858d01e3'
assert M['main']['object']['sha']=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
assert M['run']['id']==40158813599 and M['run']['head_sha']==validation and M['run']['run_attempt']==M['run']['run_number']==1
assert M['run']['workflow_id']==395617274 and M['run']['conclusion']=='failure' and M['run']['status']=='completed'
assert M['jobs']['total_count']==1 and M['jobs']['jobs'][0]['id']==121065805389
assert [s['number'] for s in M['jobs']['jobs'][0]['steps'] if s['conclusion']=='failure']==[16]
assert M['artifact']['workflow_run']['head_sha']==validation and M['artifact']['workflow_run']['id']==40158813599
assert blob((A/'protocol/WORKFLOW.yml').read_bytes())==M['validation_delta']['files'][0]['sha']=='b0f201414b29a0c708997e4a8f420b4c008b95a4'
assert len(M['validation_delta']['files'])==1
assert len(M['delta']['files'])==15
for f in M['delta']['files']:assert blob((S/f['filename']).read_bytes())==f['sha']
for name in ['gate.py','PROTOCOL.json','DESIGN.md']:assert (A/'protocol'/name).read_bytes()==(S/'experiments/manual-transmission/research/current-target-resolution-r1-20261025'/name).read_bytes()
for path,digest in P['implementation_sha256'].items():assert h((S/path).read_bytes())==digest
assert not (A/'FINAL_TRACKED_CHANGES.txt').read_bytes()
B=json.loads((A/'BINDING.json').read_text());assert B['source']==source and B['validation']==validation and B['protected_bindings']==143 and B['authorized_resolver_successor']
assert B['delta']==[[('M' if f['status']=='modified' else 'A'),f['filename']] for f in M['delta']['files']]
assert (A/'SOURCE_DELTA.txt').read_text().splitlines()==M['archived_delta_expected']
for stage in ['normal','mutant']:
 d=A/'compilation'/stage;r=json.loads((d/'RECEIPT.json').read_text());log=(d/'gradle.log').read_text()
 assert int((d/'exit.txt').read_text())==0 and r['exit']==0 and r['source']==source and r['validation']==validation and r['run']=='40158813599'
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
log=(A/'mutation/gradle.log').read_text();assert 'No tests found for given includes:' in log and int((A/'mutation/exit.txt').read_text())!=0
assert not list((A/'mutation').rglob('TEST-*.xml'));assert json.loads((A/'mutation/SUMMARY.json').read_text())['totals']['rows']==0
assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
for stage in ['focused','broad','ordinary','assay']:assert not (A/stage).exists()
result=dict(status='VERIFIED_FAILURE_ZERO_MUTATION_TESTS_NOT_BEHAVIORAL_EVIDENCE',source=source,validation=validation,run=40158813599,job=121065805389,attempt=1,artifact=M['artifact'],manifest_entries=len(manifest),normal_and_mutant_compilation='PASS',positive_cases=results,mutation_rows=0,additional_harness_defect='Archived SOURCE_DELTA used inherited95b0ab3 baseline (47 paths), while correct BINDING and API R5-to-R1 delta have15. Preserve and correct archive command on successor.',diagnosis='Gradle method-style includes *CurrentTargetResolutionTest.CO01* through CO08 matched no Kotest tests. All42 targeted tests ran with class-level filter immediately before mutation. Runtime negative evidence absent; required later regressions correctly skipped.',next='Repair only runner selection and exact receiver expectations on new immutable source; preserve production/tests/assertions and original archive. Clean inherited R5 protocol prose explicitly. Full qualification and original receiving still required.',official_games=0,official_seeds=0,precision_reserved=2)
(R/'VERIFIED_FAILURE.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
