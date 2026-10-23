"""Bounded retarget-history qualification. No official gameplay or card admission."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, os, re, shutil, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = Path('experiments/manual-transmission/research/retarget-history-r2-20261023')
OUT = Path('manual-retarget-history-evidence')
BASE = '5bc1b733d3e19fee4da7e33174bf4a480bd92812'
OLD = Path('experiments/manual-transmission/research/paused-token-entry-r4-20261023')
R2 = Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023')
R5 = Path('experiments/manual-transmission/research/paused-token-entry-r5-20261023')
PROD = Path('rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/targeting/RetargetHistory.kt')
TEST = Path('rules-engine/src/test/kotlin/com/wingedsheep/engine/mechanics/targeting/RetargetHistoryTest.kt')
WITNESS = TEST.with_name('RetargetHistoryMutationWitnessTest.kt')
P = json.loads((ROOT / 'PROTOCOL.json').read_text())
sha = lambda b: hashlib.sha256(b).hexdigest()
git = lambda *a: subprocess.check_output(['git', *a], text=True).strip()
obj = lambda ref, path: subprocess.check_output(['git', 'show', f'{ref}:{path}'])
SOURCE = os.environ['SOURCE']


def original_mutant(body):
    original = '    if (replacements == targets) return this'
    assert body.count(original) == 1
    return body.replace(original, '    return TargetsComponent.capture(state, replacements, targetRequirements) // compiled recapture mutation', 1)


def mutant(body):
    anchor = '    if (replacements == targets) return this\n'
    assert body.count(anchor) == 1 and body.endswith('}\n')
    prefix, _unreachable_tail = body.split(anchor)
    return prefix + '    return TargetsComponent.capture(state, replacements, targetRequirements)\n}\n'


def bind():
    assert os.environ['SOURCE_TREE'] == git('rev-parse', SOURCE + '^{tree}')
    assert git('rev-parse', 'HEAD^') == SOURCE
    assert git('diff', '--name-only', SOURCE, 'HEAD') == os.environ['WORKFLOW']
    assert git('merge-base', BASE, SOURCE) == BASE
    assert os.environ.get('GITHUB_RUN_ATTEMPT', '1') == '1'
    assert os.environ.get('GITHUB_RUN_NUMBER', '1') == '1'
    assert git('rev-parse', 'refs/remotes/origin/main') == P['protected']['main']
    parent = P['repair_parent']
    assert git('rev-parse', SOURCE + '^') == parent
    assert git('rev-parse', parent + '^{tree}') == P['repair_parent_tree']
    expected = {str(ROOT / name) for name in P['repair_files']}
    delta = [line.split('\t') for line in git('diff', '--name-status', parent, SOURCE).splitlines()]
    assert {row[1] for row in delta} == expected
    assert all(len(row) == 2 and row[0] == 'A' for row in delta), delta
    original_protocol = json.loads(obj(parent, 'experiments/manual-transmission/research/retarget-history-r1-20261023/PROTOCOL.json'))
    for key in ['implementation_sha256', 'new_test_cases', 'mutation_first_frames', 'positive_counts', 'protected']:
        assert P[key] == original_protocol[key], key
    subprocess.run([sys.executable, str(ROOT/'RECEIVE_ORIGINAL.py'), str(ROOT)], check=True)
    for path, expected_sha in P['implementation_sha256'].items():
        assert sha(Path(path).read_bytes()) == expected_sha, path
        assert Path(path).read_bytes() == obj(SOURCE, path) == obj(parent, path), path
    assert not git('status', '--porcelain', '--untracked-files=no')
    deck = 'experiments/manual-transmission/control/v0.7.decklist.txt'
    assert git('rev-parse', SOURCE + ':' + deck) == P['protected']['deck_blob']
    bindings = json.loads((R2 / 'BINDINGS.json').read_text())
    assert len(bindings) == 144
    for path, spec in bindings.items():
        assert sha(Path(path).read_bytes()) == spec['sha256'], path
    golds = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
    assert len(golds) == 23
    for row in golds:
        assert sha(Path(row['path']).read_bytes()) == row['sha256'], row['path']
    OUT.mkdir(exist_ok=True)
    old_recipe = (OLD / 'validation.just').read_text()
    anchor = "--tests '*EntryChoiceEventProvenanceTest'"
    assert old_recipe.count(anchor) == 1
    recipe = old_recipe.replace(anchor, anchor + " --tests '*TargetSlotPlanTest' --tests '*TargetSlotMutationWitnessTest' --tests '*RetargetHistoryTest' --tests '*RetargetHistoryMutationWitnessTest' --tests '*RetargetHistoryIntegrationTest'", 1)
    recipe += "\nmutation:\n    scripts/gradle-locked :rules-engine:test --tests '*RetargetHistoryMutationWitnessTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    recipe += "\ncompile-engine:\n    scripts/gradle-locked :rules-engine:compileKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    (OUT / 'validation.just').write_text(recipe)
    (OUT / 'BINDING.json').write_text(json.dumps(dict(source=SOURCE, tree=os.environ['SOURCE_TREE'], validation=git('rev-parse', 'HEAD'), base=BASE, delta=delta, protected_bindings=144, protected_goldens=23, official_games=0, official_seeds=0, precision_reserved=2), indent=2)+'\n')


def reports():
    return [p for p in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml')) if OUT.name not in p.parts]


def prepare(stage):
    assert stage in ['mutation','focused','broad','ordinary']
    out = OUT / stage
    out.mkdir(parents=True, exist_ok=True)
    prior = []
    for path in reports():
        prior.append(dict(path=str(path), sha256=sha(path.read_bytes())))
        path.unlink()
    (out / 'DELETED_PRIOR_REPORTS.json').write_text(json.dumps(prior,indent=2)+'\n')
    assert not reports()


def receive(stage):
    assert stage in ['mutation','focused','broad','ordinary']
    out = OUT / stage
    out.mkdir(parents=True, exist_ok=True)
    modules = defaultdict(lambda: Counter())
    names = Counter(); cases = defaultdict(list); failures=[]; skips=[]
    for path in reports():
        dst = out / 'junit' / path
        dst.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(path,dst)
        suite=ET.fromstring(path.read_bytes()); rows=suite.findall('.//testcase')
        assert int(suite.get('tests'))==len(rows),path
        for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
            assert int(suite.get(attr)) == sum(r.find(tag) is not None for r in rows),(path,attr)
        module=str(path).split('/build/test-results/test/',1)[0]
        modules[module]['suites']+=1
        for row in rows:
            cls,name=row.get('classname'),row.get('name')
            identity=cls+'::'+name
            names[module+'\t'+cls+'\t'+name]+=1
            cases[cls.rsplit('.',1)[-1]].append(identity)
            modules[module]['rows']+=1
            failure=row.find('failure'); error=row.find('error')
            if row.find('skipped') is not None:
                modules[module]['skipped']+=1;skips.append(identity)
            elif failure is not None or error is not None:
                f=failure if failure is not None else error
                modules[module]['failures' if failure is not None else 'errors']+=1
                failures.append(dict(identity=identity,type=f.get('type'),message=f.get('message'),stack=f.text or ''))
            else:modules[module]['passed']+=1
    totals=Counter()
    for m in modules.values():totals.update(m)
    for k in ['rows','suites','passed','failures','errors','skipped']:totals.setdefault(k,0)
    dup={k:v for k,v in names.items() if v>1}
    summary=dict(stage=stage,source=SOURCE,tree=os.environ['SOURCE_TREE'],validation=os.environ.get('GITHUB_SHA'),run=os.environ.get('GITHUB_RUN_ID'),totals=dict(totals),modules={k:dict(v) for k,v in modules.items()},duplicate_labels=dup,failures=failures,skips=sorted(skips),cases={k:sorted(v) for k,v in cases.items()},scope='BOUNDED_CONTESTED_RETARGET_HISTORY_NOT_FULL_OCCURRENCE_RESOLUTION_OR_SPELLSKITE',official_games=0,official_seeds=0)
    (out/'SUMMARY.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps({k:summary[k] for k in ['stage','totals','scope']},indent=2))
    rc=int((out/'exit.txt').read_text())
    new_cases=P['new_test_cases']
    if stage=='mutation':
        assert rc!=0 and set(modules)=={'rules-engine'}
        assert totals['rows']==2 and totals['failures']==2 and totals['errors']==totals['skipped']==totals['passed']==0
        assert cases.keys()=={'RetargetHistoryMutationWitnessTest'}
        assert sorted(cases['RetargetHistoryMutationWitnessTest'])==new_cases['RetargetHistoryMutationWitnessTest']
        for f in failures:
            assert f['type']=='org.opentest4j.AssertionFailedError',f
            frame=re.search(r'RetargetHistoryMutationWitnessTest\.kt:(\d+)',f['stack'])
            expected=P['mutation_first_frames'][f['identity']]
            assert frame and int(frame.group(1))==expected,(f['identity'],frame.group(1) if frame else None)
        assert PROD.read_text()==mutant(obj(SOURCE,str(PROD)).decode())
        assert git('diff','--name-only')==str(PROD)
    else:
        assert rc==0 and totals['failures']==totals['errors']==0
        assert not git('status','--porcelain','--untracked-files=no')
        expected=json.loads((OLD/'EXPECTED_CASES.json').read_text())|json.loads(Path('experiments/manual-transmission/research/target-slot-plan-r1-20261023/PROTOCOL.json').read_text())['new_test_cases']|new_cases
        for cls,identities in expected.items():
            if stage=='broad' and cls in {'CardDefinitionSnapshotTest','CardLintTest','EntersAsCopyExceptionsSerializationTest'}:continue
            assert sorted(cases.get(cls,[]))==identities,(stage,cls,'identity mismatch')
        eras=['1993-1999','2000-2002','2003-2007','2008-2016','2017-2022','2023','2024','2025','2026']
        broad={'rules-engine'}|{'mtg-sets/'+e+'/tests' for e in eras}
        if stage=='focused':
            assert cases.keys()==expected.keys()
            assert set(modules)=={'rules-engine','mtg-sdk','mtg-sets','mtg-sets/2023/tests','mtg-sets/2024/tests','mtg-sets/2025/tests'}
            assert totals['passed']==730 and totals['skipped']==0 and not dup
        elif stage=='broad':
            assert set(modules)==broad and totals['passed']==14793 and totals['skipped']==0
            assert modules['rules-engine']['passed']==4018
        else:
            assert set(modules)==broad|{'ai','game-server','gym','gym-server','gym-trainer','mtg-sdk','mtg-search','mtg-sets','mtgish-tooling','oracle-assay'}
            assert totals['passed']==18401 and totals['skipped']==43
            assert modules['rules-engine']['passed']==4018
            assert modules['oracle-assay']['passed']==568 and modules['mtg-sdk']['passed']==529
            assert sorted(skips)==sorted(json.loads((R2/'BASELINE_SKIPS.json').read_text()))
        if stage in ['broad','ordinary']:
            assert dup==json.loads((R5/'QUALIFIED_DUPLICATES.json').read_text())
    log=(out/'gradle.log').read_text(errors='replace')
    tasks={m.group(1):m.group(2) or 'EXECUTED' for m in re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$',log,re.M)}
    if stage == 'mutation':
        assert not re.search(r'^e: ', log, re.M), 'Compiler errors are not behavioral evidence'
        assert {task for task, outcome in tasks.items() if outcome == 'FAILED'} == {':rules-engine:test'}
    for module in modules:
        assert tasks.get(':'+module.replace('/',':')+':test') in ({'FAILED'} if stage=='mutation' else {'EXECUTED'}),(module,tasks)


def assay():
    body=(OLD/'check_assay.py').read_text().replace('paused-token-r4-evidence','manual-retarget-history-evidence')
    dest=OUT/'assay'/'EFFECTIVE_CHECK.py';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
    env=dict(os.environ,RECORD=str(R2))
    subprocess.run([sys.executable,str(dest)],env=env,check=True)


def compile_prepare(stage):
    assert stage in ['normal','original-broken','repaired-mutant']
    bind()
    normal = obj(SOURCE, str(PROD)).decode()
    current = normal if stage == 'normal' else original_mutant(normal) if stage == 'original-broken' else mutant(normal)
    PROD.write_text(current)
    out=OUT/'compilation'/stage;out.mkdir(parents=True,exist_ok=True)
    (out/'RetargetHistory.kt').write_text(current)
    (out/'SOURCE.diff').write_text(git('diff')+'\n')
    assert not reports()


def compile_receive(stage):
    assert stage in ['normal','original-broken','repaired-mutant']
    out=OUT/'compilation'/stage
    log=(out/'gradle.log').read_text();rc=int((out/'exit.txt').read_text())
    normal=obj(SOURCE,str(PROD)).decode()
    expected=normal if stage=='normal' else original_mutant(normal) if stage=='original-broken' else mutant(normal)
    assert PROD.read_text()==expected==(out/'RetargetHistory.kt').read_text()
    assert git('diff','--name-only')==('' if stage=='normal' else str(PROD))
    assert not reports() and not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
    errors=[line for line in log.splitlines() if line.startswith('e: ')]
    if stage=='original-broken':
        assert rc!=0 and '> Task :rules-engine:compileKotlin FAILED' in log
        assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:compileKotlin']
        coords=[re.search(r"RetargetHistory.kt:(\d+):(\d+) Unresolved reference 'entityId'\.",line) for line in errors]
        assert len(coords)==5 and all(coords)
        assert [(int(x[1]),int(x[2])) for x in coords]==[(28,86),(29,64),(30,49),(30,86),(33,33)]
    else:
        assert rc==0 and not errors and 'BUILD SUCCESSFUL' in log
        assert re.search(r'^> Task :rules-engine:compileKotlin$',log,re.M)
    result=dict(stage=stage,source=SOURCE,validation=git('rev-parse','HEAD'),run=os.environ.get('GITHUB_RUN_ID'),exit=rc,implementation_sha256=sha(PROD.read_bytes()),log_sha256=sha((out/'gradle.log').read_bytes()),tests=0,expected_disposition_verified=True,behavioral_evidence=False)
    (out/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result,indent=2))


mode=sys.argv[1]
if mode=='bind':bind()
elif mode=='mutate':
    bind(); PROD.write_text(mutant(PROD.read_text()))
    (OUT/'MUTATION.diff').write_text(git('diff')+'\n')
elif mode=='compile-prepare':compile_prepare(sys.argv[2])
elif mode=='compile-receive':compile_receive(sys.argv[2])
elif mode=='prepare':prepare(sys.argv[2])
elif mode=='receive':receive(sys.argv[2])
elif mode=='assay':assay()
elif mode=='hash':
    OUT.mkdir(exist_ok=True);manifest=OUT/'SHA256SUMS'
    manifest.write_text(''.join(sha(p.read_bytes())+'  '+str(p.relative_to(OUT))+'\n' for p in sorted(OUT.rglob('*')) if p.is_file() and p!=manifest))
else:raise ValueError(mode)
