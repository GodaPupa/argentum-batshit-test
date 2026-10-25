"""Bounded original-source characteristics qualification. No official gameplay or card admission."""
from pathlib import Path
from collections import Counter, defaultdict
import hashlib, json, os, re, shutil, subprocess, sys
import xml.etree.ElementTree as ET

ROOT = Path('experiments/manual-transmission/research/source-characteristics-r1-20261025')
OUT = Path('manual-source-characteristics-r1-evidence')
BASE = 'e067e4b803d3cb2358d0b363929b940ebcfa2cbf'
OLD = Path('experiments/manual-transmission/research/paused-token-entry-r4-20261023')
R2 = Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023')
R5 = Path('experiments/manual-transmission/research/paused-token-entry-r5-20261023')
PRODS = ['rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/AbilitySourceCharacteristics.kt', 'rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/SourceTypeTargeting.kt', 'rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt']
PRODS = list(map(Path,PRODS))
TEST = Path('rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PublicTargetGroupsTest.kt')
P = json.loads((ROOT / 'PROTOCOL.json').read_text())
sha = lambda b: hashlib.sha256(b).hexdigest()
git = lambda *a: subprocess.check_output(['git', *a], text=True).strip()
obj = lambda ref, path: subprocess.check_output(['git', 'show', f'{ref}:{path}'])
SOURCE = os.environ['SOURCE']
RECOVERY_PARENT = BASE


def mutant(body, variant):
    contract = P['mutation_contract']
    assert body.count(contract['anchor']) == 1
    assert variant in ['live-only','wrong-visit']
    return body.replace(contract['anchor'], contract[variant], 1)


def form(path, variant):
    normal = obj(SOURCE, str(path)).decode()
    return mutant(normal, variant) if str(path) == P['mutation_contract']['path'] and variant != 'normal' else normal


def check_forms(stage):
    for prod in PRODS:
        assert prod.read_text() == form(prod, stage), str(prod)
    assert git('diff','--name-only').splitlines() == ([] if stage == 'normal' else [P['mutation_contract']['path']])


def bind():
    assert os.environ['SOURCE_TREE'] == git('rev-parse', SOURCE + '^{tree}')
    assert git('rev-parse', 'HEAD^') == SOURCE
    assert git('diff', '--name-only', SOURCE, 'HEAD') == os.environ['WORKFLOW']
    assert git('merge-base', BASE, SOURCE) == BASE
    assert git('rev-parse', SOURCE + '^') == RECOVERY_PARENT
    assert os.environ.get('GITHUB_RUN_ATTEMPT', '1') == '1'
    assert os.environ.get('GITHUB_RUN_NUMBER', '1') == '1'
    assert git('rev-parse', 'refs/remotes/origin/main') == P['protected']['main']
    expected = set(P['implementation_sha256']) | set(P['record_extra_paths']) | {str(ROOT / name) for name in ['PROTOCOL.json', 'gate.py', 'DESIGN.md']}
    delta = [line.split('\t') for line in git('diff', '--name-status', BASE, SOURCE).splitlines()]
    assert {row[1] for row in delta} == expected
    assert all(len(row) == 2 and row[0] == ('M' if row[1] in P['modified_paths'] else 'A') for row in delta), delta
    for path, expected_sha in P['implementation_sha256'].items():
        assert sha(Path(path).read_bytes()) == expected_sha, path
        assert Path(path).read_bytes() == obj(SOURCE, path), path
    assert not git('status', '--porcelain', '--untracked-files=no')
    deck = 'experiments/manual-transmission/control/v0.7.decklist.txt'
    assert git('rev-parse', SOURCE + ':' + deck) == P['protected']['deck_blob']
    bindings = json.loads((R2 / 'BINDINGS.json').read_text())
    assert len(bindings) == 144
    for path, spec in bindings.items():
        if path == P['authorization']['replaced_binding']:
            assert spec['sha256'] == P['authorization']['frozen_binding_sha256']
            assert sha(obj(BASE,path)) == P['authorization']['accepted_predecessor_sha256']
            assert sha(Path(path).read_bytes()) == P['implementation_sha256'][path]
        else:
            assert sha(Path(path).read_bytes()) == spec['sha256'], path
    golds = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
    assert len(golds) == 23
    for row in golds:
        assert sha(Path(row['path']).read_bytes()) == row['sha256'], row['path']
    assert len(P['legacy_fixture_sha256']) == 82
    for path, expected_sha in P['legacy_fixture_sha256'].items():
        assert sha(Path(path).read_bytes()) == expected_sha, path
    for path, expected_sha in P['recording_protected_sha256'].items():
        assert sha(Path(path).read_bytes()) == expected_sha, path
        assert Path(path).read_bytes() == obj(RECOVERY_PARENT, path), path
    OUT.mkdir(exist_ok=True)
    old_recipe = (OLD / 'validation.just').read_text()
    anchor = "--tests '*EntryChoiceEventProvenanceTest'"
    assert old_recipe.count(anchor) == 1
    recipe = old_recipe.replace(anchor, anchor + " --tests '*TargetSlotPlanTest' --tests '*TargetSlotMutationWitnessTest' --tests '*RetargetHistoryTest' --tests '*RetargetHistoryMutationWitnessTest' --tests '*RetargetHistoryIntegrationTest' --tests '*TargetSlotCaptureTest' --tests '*TargetSlotCaptureMutationWitnessTest' --tests '*TargetSlotCaptureIntegrationTest' --tests '*ModalTargetCaptureTest' --tests '*ModalTargetCaptureMutationWitnessTest' --tests '*ModalSpellCaptureTest' --tests '*ModalSpellCaptureMutationWitnessTest' --tests '*ModalSpellRecoveryBoundaryTest' --tests '*ActivationTargetCaptureTest' --tests '*ActivationTargetCaptureLiveFlowTest' --tests '*ActivationTargetCaptureBoundaryTest' --tests '*PublicTargetGroupsTest' --tests '*CurrentTargetResolutionTest' --tests '*CurrentTargetAuthorityBoundaryTest' --tests '*AbilitySourceCharacteristicsTest' --tests '*AbilitySourceLegacyBoundaryTest' --tests '*AbilitySourceDepartureIntegrationTest' --tests '*AbilitySourceCostDepartureTest' --tests '*CurrentTargetSourceHistoryDiagnosticTest'", 1)
    recipe += "\nmutation:\n    scripts/gradle-locked :rules-engine:test --tests '*AbilitySourceCharacteristicsTest' --tests '*CurrentTargetSourceHistoryDiagnosticTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    recipe += "\ncompile-engine:\n    scripts/gradle-locked :rules-engine:compileKotlin :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    recipe += "\ntargeted:\n    scripts/gradle-locked :rules-engine:test --tests '*ModalTriggeredAbilityOnStackTest' --tests '*ModalTargetCaptureTest' --tests '*PublicTargetGroupsTest' --tests '*CurrentTargetResolutionTest' --tests '*CurrentTargetAuthorityBoundaryTest' --tests '*AbilitySourceCharacteristicsTest' --tests '*AbilitySourceLegacyBoundaryTest' --tests '*AbilitySourceDepartureIntegrationTest' --tests '*AbilitySourceCostDepartureTest' --tests '*CurrentTargetSourceHistoryDiagnosticTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    recipe += "\nactor:\n    scripts/gradle-locked :gym:test --tests '*ActorObservationBoundaryTest' --tests '*ActorPriorityObservationTest' --tests '*ActorActivationHistoryBoundaryTest' --tests '*ActorPublicTargetGroupsBoundaryTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    recipe += "\nrecording:\n    scripts/gradle-locked :ai:test --tests '*FrozenBaselineTest' --tests '*ActionRecordingCompatibilityTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1\n"
    (OUT / 'validation.just').write_text(recipe)
    for path in set(P['implementation_sha256']) | set(P['legacy_fixture_sha256']) | set(bindings) | {r['path'] for r in golds} | set(P['recording_protected_sha256']) | {deck}:
        dest = OUT / 'source' / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, dest)
    for path in [R2/'BINDINGS.json', R2/'BASELINE_SKIPS.json', R5/'QUALIFIED_DUPLICATES.json', Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json')]:
        dest = OUT / 'preserved' / path.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, dest)
    (OUT / 'BINDING.json').write_text(json.dumps(dict(source=SOURCE, tree=os.environ['SOURCE_TREE'], validation=git('rev-parse', 'HEAD'), base=BASE, delta=delta, protected_bindings=143, authorized_resolver_source_characteristics_successor=True, protected_goldens=23, official_games=0, official_seeds=0, precision_reserved=2), indent=2)+'\n')


def reports():
    return [p for p in sorted(Path('.').glob('**/build/test-results/test/TEST-*.xml')) if OUT.name not in p.parts]


def prepare(stage):
    assert stage in ['recording','actor','mutation-live-only','mutation-wrong-visit','targeted','focused','broad','ordinary']
    out = OUT / stage
    out.mkdir(parents=True, exist_ok=True)
    prior = []
    for path in reports():
        prior.append(dict(path=str(path), sha256=sha(path.read_bytes())))
        path.unlink()
    (out / 'DELETED_PRIOR_REPORTS.json').write_text(json.dumps(prior,indent=2)+'\n')
    assert not reports()


def receive(stage):
    assert stage in ['recording','actor','mutation-live-only','mutation-wrong-visit','targeted','focused','broad','ordinary']
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
    summary=dict(stage=stage,source=SOURCE,tree=os.environ['SOURCE_TREE'],validation=os.environ.get('GITHUB_SHA'),run=os.environ.get('GITHUB_RUN_ID'),totals=dict(totals),modules={k:dict(v) for k,v in modules.items()},duplicate_labels=dup,failures=failures,skips=sorted(skips),cases={k:sorted(v) for k,v in cases.items()},scope='INTERNAL_FIXED_RECIPIENT_REGRESSION_NOT_FEATURE_ACCEPTANCE',official_games=0,official_seeds=0)
    (out/'SUMMARY.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps({k:summary[k] for k in ['stage','totals','scope']},indent=2))
    rc=int((out/'exit.txt').read_text())
    new_cases=P['new_test_cases']
    if stage=='recording':
        assert rc==0 and totals['passed']==P['positive_counts']['recording'] and totals['errors']==totals['failures']==totals['skipped']==0
        assert set(modules)=={'ai'} and not dup
        assert {k:sorted(v) for k,v in cases.items()}==P['recording_cases']
        assert not git('status','--porcelain','--untracked-files=no')
    elif stage=='actor':
        assert rc==0 and totals['passed']==39 and totals['errors']==totals['failures']==totals['skipped']==0
        assert set(modules)=={'gym'} and not dup
        assert {k:sorted(v) for k,v in cases.items()}==P['actor_cases']
        assert not git('status','--porcelain','--untracked-files=no')
    elif stage.startswith('mutation-'):
        assert rc!=0 and set(modules)=={'rules-engine'}
        variant = stage.removeprefix('mutation-')
        frames = P['mutation_first_frames'][variant]
        assert totals['rows']==35 and totals['failures']==len(frames) and totals['passed']==35-len(frames) and totals['errors']==totals['skipped']==0
        assert cases.keys()=={'AbilitySourceCharacteristicsTest','CurrentTargetSourceHistoryDiagnosticTest'}
        assert sorted(x for rows in cases.values() for x in rows)==P['mutation_cases']
        assert {f['identity'] for f in failures} == frames.keys()
        for f in failures:
            assert f['type']=='org.opentest4j.AssertionFailedError',f
            cls=f['identity'].split('::')[0].rsplit('.',1)[-1]
            frame=re.search(re.escape(cls)+r'\.kt:(\d+)',f['stack'])
            expected=frames[f['identity']]
            assert frame and int(frame.group(1))==expected,(f['identity'],frame.group(1) if frame else None)
        check_forms(variant)
    elif stage=='targeted':
        assert rc==0 and totals['passed']==P['positive_counts']['targeted'] and totals['errors']==totals['failures']==totals['skipped']==0
        assert set(modules)=={'rules-engine'} and not dup and cases.keys()==P['targeted_cases'].keys()
        assert {k:sorted(v) for k,v in cases.items()}==P['targeted_cases']
        assert not git('status','--porcelain','--untracked-files=no')
    else:
        assert rc==0 and totals['failures']==totals['errors']==0
        assert not git('status','--porcelain','--untracked-files=no')
        expected=json.loads((OLD/'EXPECTED_CASES.json').read_text())|json.loads(Path('experiments/manual-transmission/research/target-slot-plan-r1-20261023/PROTOCOL.json').read_text())['new_test_cases']|json.loads(Path('experiments/manual-transmission/research/retarget-history-r1-20261023/PROTOCOL.json').read_text())['new_test_cases']|json.loads(Path('experiments/manual-transmission/research/target-slot-capture-r1-20261023/PROTOCOL.json').read_text())['new_test_cases']|json.loads(Path('experiments/manual-transmission/research/modal-target-capture-r1-20261024/PROTOCOL.json').read_text())['new_test_cases']|json.loads(Path('experiments/manual-transmission/research/modal-spell-capture-r3-20261024/PROTOCOL.json').read_text())['new_test_cases']|json.loads(Path('experiments/manual-transmission/research/activation-target-capture-r2-20261024/PROTOCOL.json').read_text())['new_test_cases']|new_cases
        for cls,identities in expected.items():
            if stage=='broad' and cls in {'CardDefinitionSnapshotTest','CardLintTest','EntersAsCopyExceptionsSerializationTest'}:continue
            assert sorted(cases.get(cls,[]))==identities,(stage,cls,'identity mismatch')
        eras=['1993-1999','2000-2002','2003-2007','2008-2016','2017-2022','2023','2024','2025','2026']
        broad={'rules-engine'}|{'mtg-sets/'+e+'/tests' for e in eras}
        if stage=='focused':
            assert cases.keys()==expected.keys()
            assert set(modules)=={'rules-engine','mtg-sdk','mtg-sets','mtg-sets/2023/tests','mtg-sets/2024/tests','mtg-sets/2025/tests'}
            assert totals['passed']==P['positive_counts']['focused'] and totals['skipped']==0 and not dup
        elif stage=='broad':
            assert set(modules)==broad and totals['passed']==P['positive_counts']['broad'] and totals['skipped']==0
            assert modules['rules-engine']['passed']==P['positive_counts']['engine']
        else:
            assert set(modules)==broad|{'ai','game-server','gym','gym-server','gym-trainer','mtg-sdk','mtg-search','mtg-sets','mtgish-tooling','oracle-assay'}
            assert totals['passed']==P['positive_counts']['ordinary_passes'] and totals['skipped']==43
            assert modules['rules-engine']['passed']==P['positive_counts']['engine']
            assert modules['oracle-assay']['passed']==568 and modules['mtg-sdk']['passed']==529
            assert sorted(skips)==sorted(json.loads((R2/'BASELINE_SKIPS.json').read_text()))
        old_names = names.copy()
        if stage=='ordinary':
            for cls, identities in P['recording_new_cases'].items():
                assert sorted(cases[cls]) == identities
                for identity in identities:
                    assert old_names.pop('ai\t'+identity.replace('::','\t',1),None)==1
            assert sorted(cases['ActorPublicTargetGroupsBoundaryTest'])==sorted(P['actor_new_cases'])
            for identity in P['actor_new_cases']:
                assert old_names.pop('gym\t'+identity.replace('::','\t',1),None)==1
        for cls, identities in new_cases.items():
            for identity in identities:
                key = 'rules-engine\t' + identity.replace('::','\t',1)
                assert old_names.pop(key, None) == 1, (stage, identity)
        baseline = P['original_report_baseline']['identity_counters'][stage]
        assert sum(old_names.values()) == baseline['rows']
        assert sha(json.dumps(old_names,sort_keys=True,separators=(',',':')).encode()) == baseline['sha256']
        if stage in ['broad','ordinary']:
            assert dup==json.loads((R5/'QUALIFIED_DUPLICATES.json').read_text())
    for identity in EXTRA:
        if stage in ['focused','broad','ordinary']:
            assert names.pop('rules-engine\t'+identity.replace('::','\t',1),None)==1
    if stage in P['expected_expanded_identity_counters']:
        expanded = P['expected_expanded_identity_counters'][stage]
        assert sum(names.values()) == expanded['rows']
        assert sha(json.dumps(names,sort_keys=True,separators=(',',':')).encode()) == expanded['sha256']
    log=(out/'gradle.log').read_text(errors='replace')
    if stage.startswith('mutation-'):
        assert not re.search(r'^e: |^> Task .*:compile\S* FAILED$|Compilation error|Unresolved reference', log, re.M)
        assert re.findall(r'^> Task (\S+) FAILED$', log, re.M) == [':rules-engine:test']
    tasks={m.group(1):m.group(2) or 'EXECUTED' for m in re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$',log,re.M)}
    for module in modules:
        assert tasks.get(':'+module.replace('/',':')+':test') in ({'FAILED'} if stage.startswith('mutation-') else {'EXECUTED'}),(module,tasks)


def assay():
    body=(OLD/'check_assay.py').read_text().replace('paused-token-r4-evidence','manual-source-characteristics-r1-evidence')
    dest=OUT/'assay'/'EFFECTIVE_CHECK.py';dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(body)
    env=dict(os.environ,RECORD=str(R2))
    subprocess.run([sys.executable,str(dest)],env=env,check=True)


def compile_prepare(stage):
    assert stage in ['normal','live-only','wrong-visit']; bind()
    out=OUT/'compilation'/stage;out.mkdir(parents=True,exist_ok=True)
    for prod in PRODS:
        normal=obj(SOURCE,str(prod)).decode()
        current=form(prod, stage)
        prod.write_text(current);(out/prod.name).write_text(current)
    (out/'SOURCE.diff').write_bytes(subprocess.check_output(['git','diff']))
    prior=[]
    for path in reports():
        prior.append(dict(path=str(path),sha256=sha(path.read_bytes())));path.unlink()
    (out/'DELETED_PRIOR_REPORTS.json').write_text(json.dumps(prior,indent=2)+'\n')
    assert not reports()


def compile_receive(stage):
    assert stage in ['normal','live-only','wrong-visit'];check_forms(stage)
    out=OUT/'compilation'/stage;log=(out/'gradle.log').read_text();rc=int((out/'exit.txt').read_text())
    for prod in PRODS:assert prod.read_bytes()==(out/prod.name).read_bytes()
    assert not reports() and not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
    assert rc==0 and not re.search(r'^e: ',log,re.M) and 'BUILD SUCCESSFUL' in log
    assert re.search(r'^> Task :rules-engine:compileKotlin$',log,re.M)
    result=dict(stage=stage,source=SOURCE,validation=git('rev-parse','HEAD'),run=os.environ.get('GITHUB_RUN_ID'),exit=rc,implementation_sha256={str(p):sha(p.read_bytes()) for p in PRODS},log_sha256=sha((out/'gradle.log').read_bytes()),tests=0,expected_disposition_verified=True,behavioral_evidence=False)
    (out/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')


def client_receive():
    out=OUT/'client';data=json.loads((out/'vitest.json').read_text())
    assert int((out/'typecheck-exit.txt').read_text())==int((out/'test-exit.txt').read_text())==0
    rows=[];found={}
    for suite in data['testResults']:
        path=suite['name'].split('/web-client/',1)[-1];key='web-client/'+path
        assertions=suite['assertionResults'];assert key in P['client_cases'] and key not in found
        assert all(r['status']=='passed' and not r.get('failureMessages') for r in assertions)
        found[key]=sorted(r['title'] for r in assertions);rows+=assertions
    assert found=={k:sorted(v) for k,v in P['client_cases'].items()}
    assert len(rows)==P['client_count'] and data['success'] is True
    assert not git('status','--porcelain','--untracked-files=no')
    (out/'SUMMARY.json').write_text(json.dumps(dict(cases=found,passed=len(rows),source=SOURCE),indent=2)+'\n')

