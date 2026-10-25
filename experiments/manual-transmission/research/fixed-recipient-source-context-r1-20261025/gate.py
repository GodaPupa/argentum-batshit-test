"""Frozen early compiler/behavioral gate, never full feature acceptance."""
from pathlib import Path
from collections import Counter
import hashlib,json,os,re,shutil,subprocess,sys
import xml.etree.ElementTree as ET
ROOT=Path('experiments/manual-transmission/research/fixed-recipient-source-context-r1-20261025')
OUT=Path('manual-fixed-recipient-source-context-r1-evidence')
P=json.loads((ROOT/'PROTOCOL.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
obj=lambda ref,p:subprocess.check_output(['git','show',ref+':'+str(p)])
SOURCE=os.environ['SOURCE']
RESOLVER='rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt'

def mutant(text):
    for anchor,replacement in P['mutation_replacements']:
        assert text.count(anchor)==1,anchor
        text=text.replace(anchor,replacement,1)
    return text

def check_form(kind):
    for path,digest in P['implementation_sha256'].items():
        normal=obj(SOURCE,path)
        assert sha(normal)==digest,path
        expected=mutant(normal.decode()).encode() if kind=='mutant' and path==P['mutation_path'] else normal
        assert Path(path).read_bytes()==expected,path
    assert git('diff','--name-only').splitlines()==([P['mutation_path']] if kind=='mutant' else [])

def bind():
    assert git('rev-parse',SOURCE+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('rev-parse',SOURCE+'^')==P['base']
    assert git('rev-parse','HEAD^')==SOURCE
    assert git('diff','--name-only',SOURCE,'HEAD')==os.environ['WORKFLOW']
    assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
    assert git('rev-parse','refs/remotes/origin/main')==P['protected']['main']
    expected=set(P['changed_paths'])|{str(ROOT/n) for n in ['PROTOCOL.json','DESIGN.md','gate.py']}
    delta=[line.split('\t') for line in git('diff','--name-status',P['base'],SOURCE).splitlines()]
    assert {row[1] for row in delta}==expected
    assert all(len(row)==2 and row[0]==('M' if row[1] in P['modified_paths'] else 'A') for row in delta)
    check_form('normal')
    assert not git('status','--porcelain','--untracked-files=no')
    bindings=json.loads(Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023/BINDINGS.json').read_text())
    assert len(bindings)==144
    hashes={path:spec['sha256'] for path,spec in bindings.items()}
    hashes[RESOLVER]=P['resolver_sha256']
    golds=json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
    assert len(golds)==23 and len(P['legacy_fixture_sha256'])==82
    hashes.update({r['path']:r['sha256'] for r in golds})
    hashes.update(P['legacy_fixture_sha256']);hashes.update(P['recording_protected_sha256'])
    hashes.update(P['implementation_sha256'])
    deck='experiments/manual-transmission/control/v0.7.decklist.txt'
    assert git('rev-parse',SOURCE+':'+deck)==P['protected']['deck_blob']
    hashes[deck]=sha(obj(SOURCE,deck))
    OUT.mkdir(exist_ok=True)
    for path,digest in hashes.items():
        body=Path(path).read_bytes();assert sha(body)==digest,path
        dest=OUT/'source'/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(body)
    for path in ROOT.iterdir():
        if path.is_file():shutil.copyfile(path,OUT/path.name)
    workflow=os.environ['WORKFLOW'];(OUT/'WORKFLOW.yml').write_bytes(Path(workflow).read_bytes())
    (OUT/'PROTECTED.json').write_text(json.dumps(hashes,sort_keys=True,indent=2)+'\n')
    identity=dict(source=SOURCE,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),
        base=P['base'],workflow=workflow,workflow_blob=git('rev-parse','HEAD:'+workflow),
        run=os.environ['GITHUB_RUN_ID'],attempt=1,run_number=1,delta=delta)
    (OUT/'BINDING.json').write_text(json.dumps(identity,indent=2)+'\n')
    flags='--no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1'
    recipe='compile:\n    scripts/gradle-locked :rules-engine:compileKotlin :rules-engine:compileTestKotlin '+flags+'\n'
    recipe+="targeted:\n    scripts/gradle-locked :rules-engine:test --tests '*FixedRecipientRetargetingTest' --tests '*FixedRecipientMutationWitnessTest' --tests '*FixedRecipientPlayerRelationTest' --tests '*FixedRecipientSourceContextTest' "+flags+'\n'
    recipe+="mutation:\n    scripts/gradle-locked :rules-engine:test --tests '*FixedRecipientSourceContextTest' "+flags+'\n'
    (OUT/'validation.just').write_text(recipe)

def restore():
    for path in P['implementation_sha256']:Path(path).write_bytes(obj(SOURCE,path))
    check_form('normal')

def reports():
    return [p for p in Path('.').glob('**/build/test-results/test/TEST-*.xml') if OUT.name not in p.parts]

def prepare(stage):
    out=OUT/stage;out.mkdir(parents=True,exist_ok=True)
    prior=[]
    for p in reports():prior.append(dict(path=str(p),sha256=sha(p.read_bytes())));p.unlink()
    (out/'DELETED_PRIOR_REPORTS.json').write_text(json.dumps(prior,indent=2)+'\n')
    kind='mutant' if stage in ['compile-mutant','mutation'] else 'normal'
    if kind=='mutant':Path(P['mutation_path']).write_text(mutant(obj(SOURCE,P['mutation_path']).decode()))
    check_form(kind)
    (out/'SOURCE.diff').write_bytes(subprocess.check_output(['git','diff']))
    for path in P['implementation_sha256']:
        dest=out/'source'/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(Path(path).read_bytes())
    assert not reports()

def receive(stage):
    out=OUT/stage;rc=int((out/'exit.txt').read_text());log=(out/'gradle.log').read_text()
    kind='mutant' if stage in ['compile-mutant','mutation'] else 'normal';check_form(kind)
    assert not re.search(r'^e: |^> Task .*:compile\S* FAILED$|Compilation error|Unresolved reference',log,re.M)
    if stage.startswith('compile-'):
        assert rc==0 and 'BUILD SUCCESSFUL' in log and not reports()
        assert re.search(r'^> Task :rules-engine:compileKotlin$',log,re.M)
        assert re.search(r'^> Task :rules-engine:compileTestKotlin(?: UP-TO-DATE)?$',log,re.M)
        assert not re.search(r'^> Task :rules-engine:test(?: |$)',log,re.M)
        result=dict(stage=stage,compile=True,tests=0)
    else:
        cases=[];failures=[];errors=[];skips=[]
        for path in reports():
            assert str(path).startswith('rules-engine/build/test-results/test/'),path
            dest=out/'junit'/path;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(path,dest)
            suite=ET.fromstring(path.read_bytes());rows=suite.findall('.//testcase')
            assert int(suite.get('tests'))==len(rows)
            for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
                assert int(suite.get(attr))==sum(r.find(tag) is not None for r in rows)
            for row in rows:
                identity=row.get('classname')+'::'+row.get('name');cases.append(identity)
                if row.find('error') is not None:errors.append(identity)
                if row.find('skipped') is not None:skips.append(identity)
                f=row.find('failure')
                if f is not None:failures.append(dict(identity=identity,type=f.get('type'),stack=f.text or ''))
        result=dict(stage=stage,cases=sorted(cases),failures=failures,errors=errors,skips=skips,exit=rc)
        (out/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
        assert len(cases)==len(set(cases)) and not errors and not skips
        if stage=='mutation':
            assert rc!=0 and sorted(cases)==P['negative_cases']
            assert {f['identity'] for f in failures}==set(P['negative_first_frames'])
            assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:test']
            for f in failures:
                assert f['type']=='org.opentest4j.AssertionFailedError'
                frame=re.search(r'FixedRecipientSourceContextTest\.kt:(\d+)',f['stack'])
                assert frame and int(frame.group(1))==P['negative_first_frames'][f['identity']]
        else:
            assert rc==0 and not failures and sorted(cases)==P['positive_cases']
            assert re.search(r'^> Task :rules-engine:test$',log,re.M)
    (out/'RECEIPT.json').write_text(json.dumps(result,indent=2)+'\n')

def manifest():
    files={str(p.relative_to(OUT)):sha(p.read_bytes()) for p in OUT.rglob('*') if p.is_file() and p.name!='MANIFEST.json'}
    (OUT/'MANIFEST.json').write_text(json.dumps(files,sort_keys=True,indent=2)+'\n')

mode=sys.argv[1]
if mode=='bind':bind()
elif mode=='restore':restore()
elif mode=='prepare':prepare(sys.argv[2])
elif mode=='receive':receive(sys.argv[2])
elif mode=='manifest':manifest()
else:raise SystemExit('Unknown mode')
