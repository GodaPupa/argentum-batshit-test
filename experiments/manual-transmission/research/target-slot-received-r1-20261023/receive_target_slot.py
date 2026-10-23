"""Audit the original R1 artifact, without running tests or changing source."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, re, subprocess, xml.etree.ElementTree as ET, zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent / 'argentum'
RAW = ROOT / 'target-slot-original'
SOURCE = '5bc1b733d3e19fee4da7e33174bf4a480bd92812'
BASE = '0b5018a0df392acc96d8b38e20dd72ccae3ff48a'
VALIDATION = '01b34276b5f56127eba4e06036e21a5b4bc2785e'
RESEARCH = REPO / 'experiments/manual-transmission/research'
P = json.loads((RAW / 'protocol/PROTOCOL.json').read_text())
M = json.loads((ROOT / 'live-receipt-metadata.json').read_text())
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
git = lambda *a: subprocess.check_output(['git','-C',str(REPO),*a], text=True).strip()
jread = lambda p: json.loads(p.read_text())

archive = ROOT / 'original-12527358718.zip'
assert archive.stat().st_size == M['artifact']['size_in_bytes'] == 34289134
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
assert M['run']['head_sha']==VALIDATION and M['run']['id']==40010257089
assert M['artifact']['workflow_run']['head_sha']==VALIDATION
assert [p['sha'] for p in M['validation_commit']['parents']]==[SOURCE]
workflow=M['validation_delta']['files']
assert len(workflow)==1 and workflow[0]['status']=='added'
assert workflow[0]['filename']=='.github/workflows/manual-target-slot-plan-r1-20261023.yml'
assert blob((RAW/'protocol/WORKFLOW.yml').read_bytes())==workflow[0]['sha']
assert M['jobs']['total_count']==1
job=M['jobs']['jobs'][0]
assert job['id']==120608504200 and job['conclusion']=='success' and job['run_attempt']==1
assert all(s['conclusion']=='success' for s in job['steps'])
binding=jread(RAW/'BINDING.json')
assert binding['source']==SOURCE and binding['validation']==VALIDATION
assert binding['tree']==git('rev-parse',SOURCE+'^{tree}')=='acc3ced89285f9c90860f99ca7515ac5c7bb3ff3'
assert git('merge-base',BASE,SOURCE)==BASE
delta=[l.split('\t') for l in git('diff','--name-status',BASE,SOURCE).splitlines()]
assert delta==binding['delta'] and len(delta)==6 and all(d[0]=='A' for d in delta)
assert (RAW/'SOURCE_DELTA.txt').read_text().strip()==git('diff','--name-status',BASE,SOURCE)
assert (RAW/'SOURCE_PINS.txt').read_text().splitlines()==[VALIDATION,binding['tree']]
assert not (RAW/'FINAL_TRACKED_CHANGES.txt').read_bytes()
for path, expected in P['implementation_sha256'].items():
    assert sha((REPO/path).read_bytes())==expected
for name in ['PROTOCOL.json','DESIGN.md','gate.py']:
    assert (RAW/'protocol'/name).read_bytes()==(RESEARCH/'target-slot-plan-r1-20261023'/name).read_bytes()
bindings=jread(RESEARCH/'paused-token-entry-r2-20261023/BINDINGS.json')
goldens=jread(RESEARCH/'ordinary-build-r1-20261022/GOLDENS.json')
assert len(bindings)==144 and len(goldens)==23
for path,spec in bindings.items(): assert sha((REPO/path).read_bytes())==spec['sha256'],path
for spec in goldens: assert sha((REPO/spec['path']).read_bytes())==spec['sha256']
deck='experiments/manual-transmission/control/v0.7.decklist.txt'
assert git('rev-parse',SOURCE+':'+deck)==P['protected']['deck_blob']
assert sha((REPO/deck).read_bytes())=='2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1'
fixture_roots=['rules-engine/src/test/resources/legacy-suspensions','rules-engine/src/test/resources/suspension-traces']
fixtures=[p for root in fixture_roots for p in (REPO/root).rglob('*') if p.is_file()]
assert len(fixtures)==82
assert git('diff','--name-only',BASE,SOURCE,'--',*fixture_roots)==''

expected=jread(RESEARCH/'paused-token-entry-r4-20261023/EXPECTED_CASES.json')|P['new_test_cases']
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
    assert summary['source']==SOURCE and summary['tree']==binding['tree'] and summary['validation']==VALIDATION and summary['run']=='40010257089'
    assert dict(totals)==summary['totals'] and dict(modules)==summary['modules']
    assert dup==summary['duplicate_labels'] and failed==summary['failures'] and sorted(skipped)==summary['skips']
    assert {k:sorted(v) for k,v in cases.items()}==summary['cases']
    rc=int((RAW/stage/'exit.txt').read_text())
    if stage=='mutation':
        assert rc!=0 and totals==dict(suites=1,rows=2,passed=0,failures=2,errors=0,skipped=0)
        assert sorted(cases['TargetSlotMutationWitnessTest'])==P['new_test_cases']['TargetSlotMutationWitnessTest']
        for f in failed:
            assert f['type']=='org.opentest4j.AssertionFailedError'
            first=re.search(r'TargetSlotMutationWitnessTest\.kt:(\d+)',f['stack'])
            assert int(first[1])==P['mutation_first_frames'][f['identity']]
    else:
        assert rc==0 and totals['errors']==totals['failures']==0
        for cls,identities in expected.items():
            if stage=='broad' and cls in {'CardDefinitionSnapshotTest','CardLintTest','EntersAsCopyExceptionsSerializationTest'}: continue
            assert sorted(cases.get(cls,[]))==identities,(stage,cls)
        assert totals['passed']=={'focused':712,'broad':14775,'ordinary':18383}[stage]
        assert totals['skipped']==(43 if stage=='ordinary' else 0)
        if stage=='focused': assert cases.keys()==expected.keys() and len(modules)==6 and not dup
        else: assert dup==duplicates and len(dup)==11 and modules['rules-engine']['passed']==4000
        if stage=='ordinary':
            assert len(modules)==20 and sorted(skipped)==sorted(skips)
            assert modules['oracle-assay']['passed']==568 and modules['mtg-sdk']['passed']==529
    log=(RAW/stage/'gradle.log').read_text()
    tasks={m[1]:m[2] or 'EXECUTED' for m in re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$',log,re.M)}
    required={':'+m.replace('/',':')+':test':('FAILED' if stage=='mutation' else 'EXECUTED') for m in modules}
    assert all(tasks.get(k)==v for k,v in required.items())
    stage_results[stage]=dict(totals=totals,modules=modules,duplicates=dup,required_tasks=required,identities_sha256=sha(json.dumps(sorted(names.items())).encode()))

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

result=dict(status='ACCEPTED_STRUCTURAL_PREREQUISITE_ONLY',source=SOURCE,tree=binding['tree'],validation=VALIDATION,run=40010257089,job=120608504200,attempt=1,artifact=M['artifact'],verified_manifest_entries=len(manifest),source_delta=delta,stages=stage_results,assay=received,protected_bindings=144,protected_goldens=23,legacy_fixture_files=82,official_games=0,official_seeds=0,precision_reserved=2,spellskite_implemented=False,live_capture_integrated=False,external_independent_review=False,remaining_component_gates=[],next='Trace and integrate original target-instance capture and per-slot resolution, then fixed-recipient redirection and actual Spellskite scenarios.')
(ROOT/'VERIFIED_RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:result[k] for k in ['status','source','run','verified_manifest_entries','protected_bindings','protected_goldens','legacy_fixture_files','remaining_component_gates']},indent=2))
print(json.dumps({k:v['totals'] for k,v in stage_results.items()},indent=2))
