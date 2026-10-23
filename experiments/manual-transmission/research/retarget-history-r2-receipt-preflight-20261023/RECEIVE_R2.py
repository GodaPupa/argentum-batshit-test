"""Audit the original retarget-history R2 artifact, without running tests or changing source."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, os, re, subprocess, xml.etree.ElementTree as ET, zipfile

ROOT = Path(os.environ.get('MANUAL_RECEIPT_ROOT', Path(__file__).resolve().parent))
REPO = Path(os.environ.get('MANUAL_REPO', ROOT.parent / 'argentum'))
RAW = ROOT / 'retarget-history-r2-original'
SOURCE = '0b97c51297bf9357c6b37dc2797abb27f7988e91'
BASE = '3e55adacde36f96636776165744ebc85508c12c5'
VALIDATION = 'e3d7a572d3095323c8155d7e30b850343c910ab6'
RESEARCH = REPO / 'experiments/manual-transmission/research'
P = json.loads((RAW / 'protocol/PROTOCOL.json').read_text())
M = json.loads((ROOT / 'retarget-history-r2-metadata.json').read_text())
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
    assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
manifest={}
for line in (RAW/'SHA256SUMS').read_text().splitlines():
    digest,name=line.split('  ',1)
    assert name not in manifest and sha((RAW/name).read_bytes())==digest
    manifest[name]=digest
assert set(manifest)=={str(p.relative_to(RAW)) for p in RAW.rglob('*') if p.is_file()}-{'SHA256SUMS'}
assert M['run']['status']=='completed' and M['run']['conclusion']=='success'
assert M['run']['run_attempt']==M['run']['run_number']==1
assert M['run']['head_sha']==VALIDATION and M['run']['id']==40021229824
assert M['artifact']['workflow_run']['head_sha']==VALIDATION
assert M['artifact']['workflow_run']['id']==40021229824
assert [p['sha'] for p in M['validation_commit']['parents']]==[SOURCE]
workflow=M['validation_delta']['files']
assert len(workflow)==1 and workflow[0]['status']=='added'
assert workflow[0]['filename']=='.github/workflows/manual-retarget-history-r2-20261023.yml'
assert blob((RAW/'protocol/WORKFLOW.yml').read_bytes())==workflow[0]['sha']
assert M['jobs']['total_count']==1
job=M['jobs']['jobs'][0]
assert job['id']==120646000839 and job['conclusion']=='success' and job['run_attempt']==1
assert all(s['conclusion']=='success' for s in job['steps'])
binding=jread(RAW/'BINDING.json')
assert binding['source']==SOURCE and binding['validation']==VALIDATION
assert binding['tree']==M['source_commit']['tree']['sha']=='e4bc8d6471c031e468a537565b57d1e419c0425d'
assert [x['sha'] for x in M['source_commit']['parents']]==[BASE]
assert M['cumulative_delta']['base_commit']['sha']=='5bc1b733d3e19fee4da7e33174bf4a480bd92812'
all_source_files=M['cumulative_delta']['files']
expected_paths=set(P['implementation_sha256'])|{f'experiments/manual-transmission/research/retarget-history-r1-20261023/{name}' for name in ['PROTOCOL.json','DESIGN.md','gate.py']}|{f'experiments/manual-transmission/research/retarget-history-r2-20261023/{name}' for name in P['repair_files']}
assert len(all_source_files)==len(expected_paths)==15
assert {f['filename'] for f in all_source_files}==expected_paths
for f in all_source_files:
    changed='rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/stack/ContestedRetargetExecutors.kt'
    assert f['status']==('modified' if f['filename']==changed else 'added')
    assert blob((REPO/f['filename']).read_bytes())==f['sha']
assert (RAW/'SOURCE_DELTA.txt').read_text().splitlines()==[('M' if f['status']=='modified' else 'A')+'\t'+f['filename'] for f in all_source_files]
delta=binding['delta']
assert len(delta)==7 and all(row[0]=='A' for row in delta)
assert {row[1] for row in delta}=={str(Path('experiments/manual-transmission/research/retarget-history-r2-20261023')/name) for name in P['repair_files']}
assert {f['filename'] for f in M['source_delta']['files']}=={row[1] for row in delta}
assert all(f['status']=='added' for f in M['source_delta']['files'])
assert (RAW/'SOURCE_PINS.txt').read_text().splitlines()==[VALIDATION,binding['tree']]
assert not (RAW/'FINAL_TRACKED_CHANGES.txt').read_bytes()
recipe=(RESEARCH/'paused-token-entry-r4-20261023/validation.just').read_text()
recipe_anchor="--tests '*EntryChoiceEventProvenanceTest'"
assert recipe.count(recipe_anchor)==1
recipe=recipe.replace(recipe_anchor,recipe_anchor+" --tests '*TargetSlotPlanTest' --tests '*TargetSlotMutationWitnessTest' --tests '*RetargetHistoryTest' --tests '*RetargetHistoryMutationWitnessTest' --tests '*RetargetHistoryIntegrationTest'",1)
recipe+="\nmutation:\n    scripts/gradle-locked :rules-engine:test --tests '*RetargetHistoryMutationWitnessTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
compile_command='scripts/gradle-locked :rules-engine:compileKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1'
recipe+='\ncompile-engine:\n    '+compile_command+'\n'
assert (RAW/'validation.just').read_text()==recipe

for path, expected in P['implementation_sha256'].items():
    assert sha((REPO/path).read_bytes())==expected
for name in P['repair_files']:
    assert (RAW/'protocol'/name).read_bytes()==(RESEARCH/'retarget-history-r2-20261023'/name).read_bytes()
    remote=next(f for f in M['source_delta']['files'] if f['filename'].endswith('/'+name))
    assert blob((RAW/'protocol'/name).read_bytes())==remote['sha']
bindings=jread(RESEARCH/'paused-token-entry-r2-20261023/BINDINGS.json')
goldens=jread(RESEARCH/'ordinary-build-r1-20261022/GOLDENS.json')
assert len(bindings)==144 and len(goldens)==23
for path,spec in bindings.items(): assert sha((REPO/path).read_bytes())==spec['sha256'],path
for spec in goldens: assert sha((REPO/spec['path']).read_bytes())==spec['sha256']
deck='experiments/manual-transmission/control/v0.7.decklist.txt'
assert P['protected']['deck_blob']=='c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
assert sha((REPO/deck).read_bytes())=='2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1'
fixture_roots=['rules-engine/src/test/resources/legacy-suspensions','rules-engine/src/test/resources/suspension-traces']
fixtures=[p for root in fixture_roots for p in (REPO/root).rglob('*') if p.is_file()]
assert len(fixtures)==82
assert all(f['filename'].startswith('experiments/manual-transmission/research/retarget-history-r2-20261023/') for f in M['source_delta']['files'])

expected=jread(RESEARCH/'paused-token-entry-r4-20261023/EXPECTED_CASES.json')|jread(RESEARCH/'target-slot-plan-r1-20261023/PROTOCOL.json')['new_test_cases']|P['new_test_cases']
duplicates=jread(RESEARCH/'paused-token-entry-r5-20261023/QUALIFIED_DUPLICATES.json')
skips=jread(RESEARCH/'paused-token-entry-r2-20261023/BASELINE_SKIPS.json')
stage_results={}
for stage in ['mutation','focused','broad','ordinary']:
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
    assert summary['source']==SOURCE and summary['tree']==binding['tree'] and summary['validation']==VALIDATION and summary['run']=='40021229824'
    assert dict(totals)==summary['totals'] and dict(modules)==summary['modules']
    assert dup==summary['duplicate_labels'] and failed==summary['failures'] and sorted(skipped)==summary['skips']
    assert {k:sorted(v) for k,v in cases.items()}==summary['cases']
    rc=int((RAW/stage/'exit.txt').read_text())
    if stage=='mutation':
        assert rc!=0 and totals==dict(suites=1,rows=2,passed=0,failures=2,errors=0,skipped=0)
        assert sorted(cases['RetargetHistoryMutationWitnessTest'])==P['new_test_cases']['RetargetHistoryMutationWitnessTest']
        for f in failed:
            assert f['type']=='org.opentest4j.AssertionFailedError'
            first=re.search(r'RetargetHistoryMutationWitnessTest\.kt:(\d+)',f['stack'])
            assert int(first[1])==P['mutation_first_frames'][f['identity']]
    else:
        assert rc==0 and totals['errors']==totals['failures']==0
        for cls,identities in expected.items():
            if stage=='broad' and cls in {'CardDefinitionSnapshotTest','CardLintTest','EntersAsCopyExceptionsSerializationTest'}: continue
            assert sorted(cases.get(cls,[]))==identities,(stage,cls)
        assert totals['passed']=={'focused':730,'broad':14793,'ordinary':18401}[stage]
        assert totals['skipped']==(43 if stage=='ordinary' else 0)
        broad_modules={'rules-engine'}|{'mtg-sets/'+era+'/tests' for era in ['1993-1999','2000-2002','2003-2007','2008-2016','2017-2022','2023','2024','2025','2026']}
        if stage=='focused':
            assert cases.keys()==expected.keys() and not dup
            assert set(modules)=={'rules-engine','mtg-sdk','mtg-sets','mtg-sets/2023/tests','mtg-sets/2024/tests','mtg-sets/2025/tests'}
        else:
            assert dup==duplicates and len(dup)==11 and modules['rules-engine']['passed']==4018
            assert set(modules)==(broad_modules if stage=='broad' else broad_modules|{'ai','game-server','gym','gym-server','gym-trainer','mtg-sdk','mtg-search','mtg-sets','mtgish-tooling','oracle-assay'})
        if stage=='ordinary':
            assert len(modules)==20 and sorted(skipped)==sorted(skips)
            assert modules['oracle-assay']['passed']==568 and modules['mtg-sdk']['passed']==529
    log=(RAW/stage/'gradle.log').read_text()
    tasks={m[1]:m[2] or 'EXECUTED' for m in re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$',log,re.M)}
    required={':'+m.replace('/',':')+':test':('FAILED' if stage=='mutation' else 'EXECUTED') for m in modules}
    assert all(tasks.get(k)==v for k,v in required.items())
    if stage=='mutation':
        assert not re.search(r'^e: ',log,re.M)
        assert {task for task,outcome in tasks.items() if outcome=='FAILED'}=={':rules-engine:test'}
    stage_results[stage]=dict(totals=totals,modules=modules,duplicates=dup,required_tasks=required,identities_sha256=sha(json.dumps(sorted(names.items())).encode()))

# Independently reconstruct all three diagnostic payloads, with no engine execution.
prod_path=REPO/'rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/targeting/RetargetHistory.kt'
normal=prod_path.read_text();anchor='    if (replacements == targets) return this'
assert normal.count(anchor)==1
broken=normal.replace(anchor,'    return TargetsComponent.capture(state, replacements, targetRequirements) // compiled recapture mutation',1)
fixed=normal.split(anchor+'\n')[0]+'    return TargetsComponent.capture(state, replacements, targetRequirements)\n}\n'
import tempfile
prod_rel=str(prod_path.relative_to(REPO))
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
for stage,payload in [('normal',normal),('original-broken',broken),('repaired-mutant',fixed)]:
    folder=RAW/'compilation'/stage;log=(folder/'gradle.log').read_text();receipt=jread(folder/'RECEIPT.json')
    assert compile_command in log
    assert (folder/'RetargetHistory.kt').read_text()==payload
    if stage=='normal':assert (folder/'SOURCE.diff').read_text()=='\n'
    else:verify_patch((folder/'SOURCE.diff').read_text(),payload)
    assert receipt['source']==SOURCE and receipt['validation']==VALIDATION and receipt['run']=='40021229824'
    assert receipt['implementation_sha256']==sha(payload.encode()) and receipt['log_sha256']==sha((folder/'gradle.log').read_bytes())
    assert receipt['tests']==0 and receipt['expected_disposition_verified'] and not receipt['behavioral_evidence']
    rc=int((folder/'exit.txt').read_text());assert receipt['exit']==rc
    assert not list(folder.rglob('*.xml'))
    errors=[line for line in log.splitlines() if line.startswith('e: ')]
    if stage=='original-broken':
        assert rc!=0 and re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:compileKotlin']
        coords=[re.search(r"RetargetHistory.kt:(\d+):(\d+) Unresolved reference 'entityId'\.",line) for line in errors]
        assert len(coords)==5 and all(coords)
        assert [(int(x[1]),int(x[2])) for x in coords]==[(28,86),(29,64),(30,49),(30,86),(33,33)]
    else:assert rc==0 and not errors and 'BUILD SUCCESSFUL' in log and re.search(r'^> Task :rules-engine:compileKotlin$',log,re.M)
    compile_results[stage]=receipt
# Re-audit the embedded original failure, without reclassifying it.
import base64, importlib.util
spec=importlib.util.spec_from_file_location('failed_original',RESEARCH/'retarget-history-r2-20261023/RECEIVE_ORIGINAL.py')
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
failed=mod.receive(base64.b64decode((RAW/'protocol/ORIGINAL_ARTIFACT.zip.base64').read_bytes(),validate=True),jread(RAW/'protocol/ORIGINAL_METADATA.json'))
assert failed==jread(RAW/'protocol/FAILURE_RECEIPT.json')

disposition=jread(RAW/'DISPOSITION.json')
assert disposition['compilation_controls']=={stage:True for stage in ['normal','original-broken','repaired-mutant']}
assert disposition['original_reports_present']=={stage:True for stage in ['mutation','focused','broad','ordinary']}
assert disposition['assay_receipt_present'] and not disposition['component_adopted']
assert disposition['official_games']==disposition['official_seeds']==0 and disposition['precision_reserved']==2

assay=RAW/'assay'; received=jread(assay/'RECEIVING.json')
for name in ['build-exit.txt','gate-exit.txt','differential-exit.txt','corpus-capture-exit.txt']: assert int((assay/name).read_text())==0
assert received['remaining_gates']==[]
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

result=dict(status='ACCEPTED_BOUNDED_RETARGET_HISTORY_ONLY',source=SOURCE,tree=binding['tree'],validation=VALIDATION,run=40021229824,job=120646000839,attempt=1,artifact=M['artifact'],verified_manifest_entries=len(manifest),source_delta=delta,compilation_controls=compile_results,failed_original=failed,stages=stage_results,assay=received,protected_bindings=144,protected_goldens=23,legacy_fixture_files=82,official_games=0,official_seeds=0,precision_reserved=2,spellskite_implemented=False,external_independent_review=False,remaining_component_gates=[])
(ROOT/'RETARGET_R2_VERIFIED_RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['status','source','run','verified_manifest_entries','protected_bindings','protected_goldens','legacy_fixture_files','remaining_component_gates']},indent=2))
print(json.dumps({k:v['totals'] for k,v in stage_results.items()},indent=2))
