"""Audit original modal spell capture R1 evidence; no engine tests or gameplay executed."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, os, re, subprocess, xml.etree.ElementTree as ET, zipfile

ROOT = Path(os.environ.get('MANUAL_RECEIPT_ROOT', Path(__file__).resolve().parent))
REPO = Path(os.environ.get('MANUAL_REPO', ROOT.parent / 'argentum'))
RAW = ROOT / 'spell-r3-original'
OVERLAY = Path(os.environ.get('MANUAL_SOURCE_OVERLAY', ROOT.parent / 'spell-r3'))
def source_path(path):
    return OVERLAY / path if (OVERLAY / path).exists() else REPO / path
SOURCE = '035e0ec3da8729c43018ffcc839aa821517dcda3'
BASE = '4c3aba1a17f5a116b94b179940ef7ecd75310cda'
VALIDATION = '266f4a28fecd66fabf4f34aa4e14cab20b0a771f'
RESEARCH = REPO / 'experiments/manual-transmission/research'
P = json.loads((RAW / 'protocol/PROTOCOL.json').read_text())
M = json.loads((ROOT / 'spell-r3-metadata.json').read_text())
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
git = lambda *a: subprocess.check_output(['git','-C',str(REPO),*a], text=True).strip()
jread = lambda p: json.loads(p.read_text())

archive = ROOT / ('original-'+str(M['artifact']['id'])+'.zip')
assert archive.stat().st_size == M['artifact']['size_in_bytes']
assert 'sha256:'+sha(archive.read_bytes()) == M['artifact']['digest']
with zipfile.ZipFile(archive) as z:
    names=z.namelist()
    assert len(names)==len(set(names)) and z.testzip() is None
    archive_files={n for n in names if not n.endswith('/')}
    assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
manifest={}
for line in (RAW/'SHA256SUMS').read_text().splitlines():
    digest,name=line.split('  ',1)
    assert name not in manifest and sha((RAW/name).read_bytes())==digest
    manifest[name]=digest
assert archive_files=={str(p.relative_to(RAW)) for p in RAW.rglob('*') if p.is_file()}
with zipfile.ZipFile(archive) as z:
    for name in archive_files: assert z.read(name)==(RAW/name).read_bytes()
assert set(manifest)=={str(p.relative_to(RAW)) for p in RAW.rglob('*') if p.is_file()}-{'SHA256SUMS'}
assert M['main']['object']['sha']=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
assert M['run']['status']=='completed' and M['run']['conclusion']=='success'
assert M['run']['run_attempt']==M['run']['run_number']==1
assert M['run']['head_sha']==VALIDATION and M['run']['id']==40071650391
assert M['artifact']['id']==M['artifact_id_pin'] and M['artifact']['size_in_bytes']==M['artifact_bytes_pin']
assert M['artifact']['digest']=='sha256:'+M['artifact_sha256_pin']
assert M['artifact']['workflow_run']['head_sha']==VALIDATION
assert M['artifact']['workflow_run']['id']==40071650391
assert M['artifact']['name']=='manual-modal-spell-capture-r3-40071650391'
assert M['run']['event']=='push' and M['run']['path']=='.github/workflows/manual-modal-spell-capture-r3-20261024.yml'
assert M['source_delta']['base_commit']['sha']=='103ac16642d60c0d02bd12004d37f2f87cf7f324'
assert [p['sha'] for p in M['validation_commit']['parents']]==[SOURCE]
workflow=M['validation_delta']['files']
assert len(workflow)==1 and workflow[0]['status']=='added'
assert workflow[0]['filename']=='.github/workflows/manual-modal-spell-capture-r3-20261024.yml'
assert blob((RAW/'protocol/WORKFLOW.yml').read_bytes())==workflow[0]['sha']
assert M['jobs']['total_count']==1
job=M['jobs']['jobs'][0]
assert job['id']==120806179924 and job['conclusion']=='success' and job['run_attempt']==1
assert all(s['conclusion']=='success' for s in job['steps'])
binding=jread(RAW/'BINDING.json')
assert binding['source']==SOURCE and binding['validation']==VALIDATION
assert binding['tree']==M['source_commit']['tree']['sha']=='bb50c6e9de41619e796ac5550a449484bfcbf7cf'
assert [x['sha'] for x in M['source_commit']['parents']]==[BASE]
all_source_files=M['source_delta']['files']
expected_paths=set(P['implementation_sha256'])|set(P['record_extra_paths'])|{f'experiments/manual-transmission/research/modal-spell-capture-r3-20261024/{name}' for name in ['PROTOCOL.json','DESIGN.md','gate.py']}
assert len(all_source_files)==len(expected_paths)==24
assert {f['filename'] for f in all_source_files}==expected_paths
for f in all_source_files:
    assert f['status']==('modified' if f['filename'] in P['modified_paths'] else 'added')
    assert blob(source_path(f['filename']).read_bytes())==f['sha']
delta=[('M' if f['status']=='modified' else 'A')+'\t'+f['filename'] for f in all_source_files]
assert (RAW/'SOURCE_DELTA.txt').read_text().splitlines()==delta
assert binding['delta']==[r.split('\t') for r in delta]
assert (RAW/'SOURCE_PINS.txt').read_text().splitlines()==[VALIDATION,binding['tree']]
assert not (RAW/'FINAL_TRACKED_CHANGES.txt').read_bytes()
recipe=(RESEARCH/'paused-token-entry-r4-20261023/validation.just').read_text()
recipe_anchor="--tests '*EntryChoiceEventProvenanceTest'"
assert recipe.count(recipe_anchor)==1
recipe=recipe.replace(recipe_anchor,recipe_anchor+" --tests '*TargetSlotPlanTest' --tests '*TargetSlotMutationWitnessTest' --tests '*RetargetHistoryTest' --tests '*RetargetHistoryMutationWitnessTest' --tests '*RetargetHistoryIntegrationTest' --tests '*TargetSlotCaptureTest' --tests '*TargetSlotCaptureMutationWitnessTest' --tests '*TargetSlotCaptureIntegrationTest' --tests '*ModalTargetCaptureTest' --tests '*ModalTargetCaptureMutationWitnessTest' --tests '*ModalSpellCaptureTest' --tests '*ModalSpellCaptureMutationWitnessTest' --tests '*ModalSpellRecoveryBoundaryTest'",1)
recipe+="\nmutation:\n    scripts/gradle-locked :rules-engine:test --tests '*ModalSpellCaptureMutationWitnessTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
compile_command='scripts/gradle-locked :rules-engine:compileKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1'
recipe+='\ncompile-engine:\n    '+compile_command+'\n'
recipe+='\ntargeted:\n    scripts/gradle-locked :rules-engine:test --tests \'*ModalSpellCaptureTest\' --tests \'*ModalSpellCaptureMutationWitnessTest\' --tests \'*ModalSpellRecoveryBoundaryTest\' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n'
assert (RAW/'validation.just').read_text()==recipe

for path, expected in P['original18_test_sha256'].items():
    assert sha(source_path(path).read_bytes()) == expected
assert len(P['original18_test_sha256']) == 2
for path, expected in P['implementation_sha256'].items():
    assert sha(source_path(path).read_bytes())==expected
for name in ['PROTOCOL.json','DESIGN.md','gate.py']:
    assert (RAW/'protocol'/name).read_bytes()==(OVERLAY/'experiments/manual-transmission/research/modal-spell-capture-r3-20261024'/name).read_bytes()
bindings=jread(RESEARCH/'paused-token-entry-r2-20261023/BINDINGS.json')
goldens=jread(RESEARCH/'ordinary-build-r1-20261022/GOLDENS.json')
assert len(bindings)==144 and len(goldens)==23
for path,spec in bindings.items(): assert sha(source_path(path).read_bytes())==spec['sha256'],path
for spec in goldens: assert sha((REPO/spec['path']).read_bytes())==spec['sha256']
deck='experiments/manual-transmission/control/v0.7.decklist.txt'
assert P['protected']['deck_blob']=='c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
assert sha((REPO/deck).read_bytes())=='2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1'
fixture_roots=['rules-engine/src/test/resources/legacy-suspensions','rules-engine/src/test/resources/suspension-traces']
fixtures=[p for root in fixture_roots for p in (REPO/root).rglob('*') if p.is_file()]
assert len(fixtures)==82
for path,digest in P['legacy_fixture_sha256'].items(): assert sha(source_path(path).read_bytes())==digest

expected=jread(RESEARCH/'paused-token-entry-r4-20261023/EXPECTED_CASES.json')|jread(RESEARCH/'target-slot-plan-r1-20261023/PROTOCOL.json')['new_test_cases']|jread(RESEARCH/'retarget-history-r1-20261023/PROTOCOL.json')['new_test_cases']|jread(RESEARCH/'target-slot-capture-r1-20261023/PROTOCOL.json')['new_test_cases']|jread(RESEARCH/'modal-target-capture-r1-20261024/PROTOCOL.json')['new_test_cases']|P['new_test_cases']
duplicates=jread(RESEARCH/'paused-token-entry-r5-20261023/QUALIFIED_DUPLICATES.json')
skips=jread(RESEARCH/'paused-token-entry-r2-20261023/BASELINE_SKIPS.json')
stage_results={}
for stage in ['mutation','targeted','focused','broad','ordinary']:
    modules=defaultdict(Counter); names=Counter(); cases=defaultdict(list); failed=[]; skipped=[]
    for path in sorted((RAW/stage/'junit').rglob('TEST-*.xml')):
        module=str(path.relative_to(RAW/stage/'junit')).split('/build/test-results/test/')[0]
        suite=ET.parse(path).getroot(); rows=suite.findall('.//testcase')
        assert int(suite.get('tests'))==len(rows)
        for attr in ['failures','errors','skipped']:
            tag={'failures':'failure','errors':'error','skipped':'skipped'}[attr]
            assert int(suite.get(attr))==sum(row.find(tag) is not None for row in rows)
        modules[module]['suites']+=1
        for row in rows:
            cls,name=row.get('classname'),row.get('name'); identity=cls+'::'+name
            names[module+'\t'+cls+'\t'+name]+=1;cases[cls.rsplit('.',1)[-1]].append(identity)
            modules[module]['rows']+=1
            result=next((t for t in ['failure','error','skipped'] if row.find(t) is not None),None)
            key={None:'passed','failure':'failures','error':'errors','skipped':'skipped'}[result]
            modules[module][key]+=1
            if result=='skipped': skipped.append(identity)
            elif result:
                e=row.find(result);failed.append(dict(identity=identity,type=e.get('type'),message=e.get('message'),stack=e.text or ''))
    totals=Counter()
    for module in modules.values():totals.update(module)
    for key in ['suites','rows','passed','failures','errors','skipped']:totals.setdefault(key,0)
    dup={k:v for k,v in names.items() if v>1}
    summary=jread(RAW/stage/'SUMMARY.json')
    assert summary['source']==SOURCE and summary['tree']==binding['tree'] and summary['validation']==VALIDATION and summary['run']=='40071650391'
    assert dict(totals)==summary['totals'] and dict(modules)==summary['modules']
    assert dup==summary['duplicate_labels'] and failed==summary['failures'] and sorted(skipped)==summary['skips']
    assert {k:sorted(v) for k,v in cases.items()}==summary['cases']
    rc=int((RAW/stage/'exit.txt').read_text())
    if stage=='mutation':
        assert rc!=0 and totals==dict(suites=1,rows=2,passed=0,failures=2,errors=0,skipped=0)
        assert sorted(cases['ModalSpellCaptureMutationWitnessTest'])==P['new_test_cases']['ModalSpellCaptureMutationWitnessTest']
        for f in failed:
            assert f['type']=='org.opentest4j.AssertionFailedError'
            first=re.search(r'ModalSpellCaptureMutationWitnessTest\.kt:(\d+)',f['stack'])
            assert int(first[1])==P['mutation_first_frames'][f['identity']]
    elif stage=='targeted':
        assert rc==0 and totals['passed']==23 and totals['errors']==totals['failures']==totals['skipped']==0
        assert set(modules)=={'rules-engine'} and not dup
        assert {k:sorted(v) for k,v in cases.items()}==P['new_test_cases']
    else:
        assert rc==0 and totals['errors']==totals['failures']==0
        for cls,identities in expected.items():
            if stage=='broad' and cls in {'CardDefinitionSnapshotTest','CardLintTest','EntersAsCopyExceptionsSerializationTest'}: continue
            assert sorted(cases.get(cls,[]))==identities,(stage,cls)
        assert totals['passed']=={'focused':782,'broad':14845,'ordinary':18453}[stage]
        assert totals['skipped']==(43 if stage=='ordinary' else 0)
        broad_modules={'rules-engine'}|{'mtg-sets/'+era+'/tests' for era in ['1993-1999','2000-2002','2003-2007','2008-2016','2017-2022','2023','2024','2025','2026']}
        if stage=='focused':
            assert cases.keys()==expected.keys() and not dup
            assert set(modules)=={'rules-engine','mtg-sdk','mtg-sets','mtg-sets/2023/tests','mtg-sets/2024/tests','mtg-sets/2025/tests'}
        else:
            assert dup==duplicates and len(dup)==11 and modules['rules-engine']['passed']==4070
            assert set(modules)==(broad_modules if stage=='broad' else broad_modules|{'ai','game-server','gym','gym-server','gym-trainer','mtg-sdk','mtg-search','mtg-sets','mtgish-tooling','oracle-assay'})
        if stage=='ordinary':
            assert len(modules)==20 and sorted(skipped)==sorted(skips)
            assert modules['oracle-assay']['passed']==568 and modules['mtg-sdk']['passed']==529
    if stage not in ['mutation','targeted']:
        baseline_names=names.copy()
        for identities in P['new_test_cases'].values():
            for identity in identities: assert baseline_names.pop('rules-engine\t'+identity.replace('::','\t',1),None)==1
        expected_old=P['original_report_baseline']['identity_counters'][stage]
        assert sum(baseline_names.values())==expected_old['rows']
        assert sha(json.dumps(baseline_names,sort_keys=True,separators=(',',':')).encode())==expected_old['sha256']
    log=(RAW/stage/'gradle.log').read_text()
    tasks={m[1]:m[2] or 'EXECUTED' for m in re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$',log,re.M)}
    required={':'+m.replace('/',':')+':test':('FAILED' if stage=='mutation' else 'EXECUTED') for m in modules}
    assert all(tasks.get(k)==v for k,v in required.items())
    if stage=='mutation':
        assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
        assert {task for task,outcome in tasks.items() if outcome=='FAILED'}=={':rules-engine:test'}
    stage_results[stage]=dict(totals=totals,modules=modules,duplicates=dup,required_tasks=required,identities_sha256=sha(json.dumps(sorted(names.items())).encode()))

# Independently reconstruct the declared executable loss mutation.
prod_rel='rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/continuations/CastModalContinuationResumer.kt'
prod_path=source_path(prod_rel)
normal=prod_path.read_text()
anchor='                }))'
assert normal.count(anchor)==1
fixed=normal.replace(anchor,'                }).let { captured -> captured.copy(instances = captured.instances.filter { it.slots.isNotEmpty() }.map { group -> group.copy(slots = group.slots.map { slot -> slot.copy(objectRef = null) }) }) })',1)
import tempfile
def verify_patch(patch, payload):
    lines=patch.splitlines()
    assert [line for line in lines if line.startswith('diff --git ')]==[f'diff --git a/{prod_rel} b/{prod_rel}']
    assert [line for line in lines if line.startswith('--- ')]==['--- a/'+prod_rel]
    assert [line for line in lines if line.startswith('+++ ')]==['+++ b/'+prod_rel]
    index=re.fullmatch(r'index ([0-9a-f]+)\.\.([0-9a-f]+) 100644',lines[1])
    assert index and len(index[1])>=7 and len(index[2])>=7
    assert blob(normal.encode()).startswith(index[1]) and blob(payload.encode()).startswith(index[2])
    with tempfile.TemporaryDirectory(prefix='manual-patch-audit-') as temp:
        dest=Path(temp)/prod_rel;dest.parent.mkdir(parents=True);dest.write_text(normal)
        subprocess.run(['git','apply','--check','-'],cwd=temp,input=patch.encode(),check=True)
        subprocess.run(['git','apply','-'],cwd=temp,input=patch.encode(),check=True)
        assert dest.read_text()==payload
        assert [p.relative_to(temp).as_posix() for p in Path(temp).rglob('*') if p.is_file()]==[prod_rel]
verify_patch((RAW/'MUTATION.diff').read_text(),fixed)
compile_results={}
for stage,payload in [('normal',normal),('mutant',fixed)]:
    folder=RAW/'compilation'/stage;log=(folder/'gradle.log').read_text();receipt=jread(folder/'RECEIPT.json')
    assert compile_command in log
    assert (folder/'CastModalContinuationResumer.kt').read_text()==payload
    if stage=='normal':assert (folder/'SOURCE.diff').read_text()=='\n'
    else:verify_patch((folder/'SOURCE.diff').read_text(),payload)
    assert receipt['source']==SOURCE and receipt['validation']==VALIDATION and receipt['run']=='40071650391'
    assert receipt['implementation_sha256']==sha(payload.encode()) and receipt['log_sha256']==sha((folder/'gradle.log').read_bytes())
    assert receipt['tests']==0 and receipt['expected_disposition_verified'] and not receipt['behavioral_evidence']
    rc=int((folder/'exit.txt').read_text());assert receipt['exit']==rc
    assert not list(folder.rglob('*.xml'))
    errors=[line for line in log.splitlines() if line.startswith('e: ')]
    assert rc==0 and not errors and 'BUILD SUCCESSFUL' in log and re.search(r'^> Task :rules-engine:compileKotlin$',log,re.M)
    compile_results[stage]=receipt
disposition=jread(RAW/'DISPOSITION.json')
assert disposition['compilation_controls']=={stage:True for stage in ['normal','mutant']}
assert disposition['original_reports_present']=={stage:True for stage in ['mutation','targeted','focused','broad','ordinary']}
assert disposition['assay_receipt_present'] and not disposition['component_adopted']
assert disposition['official_games']==disposition['official_seeds']==0 and disposition['precision_reserved']==2

assay=RAW/'assay'; received=jread(assay/'RECEIVING.json')
for name in ['build-exit.txt','gate-exit.txt','differential-exit.txt','corpus-capture-exit.txt']: assert int((assay/name).read_text())==0
assert received['remaining_gates']==[]
assert set(received['metrics'])=={'Cards assayed','Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)'}
for label,expected_value in received['metrics'].items():
    values=[l.strip()[len(label):].strip() for l in (assay/'gate.log').read_text().splitlines() if l.strip().startswith(label)]
    assert len(values)==1 and int(re.match(r'[\s:]*([0-9]+)',values[0])[1])==expected_value==(2000 if label=='Cards assayed' else 0)
data=(assay/'oracle-bulk-corpus.jsonl.gz').read_bytes()
assert len(data)==received['corpus']['bytes'] and sha(data)==received['corpus']['sha256']
assert sha(data)==(assay/'oracle-bulk-sha256.txt').read_text().split()[0]
assert len(data)==int((assay/'oracle-bulk-bytes.txt').read_text().split()[0])
baseline=(RESEARCH/'paused-token-entry-r2-20261023/BASELINE_DIFFERENTIAL.log').read_bytes()
assert baseline==(assay/'differential.log').read_bytes()
assert sha(baseline)=='14b77880d7ef45a8c3cfb5bc31ce340ed17aff6320bb32a577e30bf3f5a327e2'
assert len(re.findall(rb'^  (.+)  \[([A-Z0-9]+)\]$',baseline,re.M))==51

result=dict(status='ACCEPTED_BOUNDED_ORIGINAL_MODAL_SPELL_CAPTURE_ONLY',source=SOURCE,tree=binding['tree'],validation=VALIDATION,run=40071650391,job=120806179924,attempt=1,artifact=M['artifact'],verified_manifest_entries=len(manifest),source_delta=delta,compilation_controls=compile_results,stages=stage_results,assay=received,protected_bindings=144,protected_goldens=23,legacy_fixture_files=82,official_games=0,official_seeds=0,precision_reserved=2,spellskite_implemented=False,external_independent_review=False,remaining_component_gates=[])
(ROOT/'SPELL_R3_VERIFIED_RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['status','source','run','verified_manifest_entries','protected_bindings','protected_goldens','legacy_fixture_files','remaining_component_gates']},indent=2))
print(json.dumps({k:v['totals'] for k,v in stage_results.items()},indent=2))
