"""Exact PR212 supplemental receiving. Frozen manifests and collector remain untouched."""
import hashlib, importlib.util, json, pathlib, shutil, subprocess, sys, xml.etree.ElementTree as ET

ROOT = pathlib.Path('.')
BASE = ROOT / 'experiments/izzet-science'
OUT = ROOT / 'build/reports/pr212-exact-compat'
SUPPLEMENT = BASE / 'pr212-exact-runtime-supplement.json'
PERMIT = BASE / 'pr212-compat-execution-permit.json'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*args): return subprocess.check_output(['git', *args], text=True).strip()
def bind():
    supplement = json.loads(SUPPLEMENT.read_text())
    permit = json.loads(PERMIT.read_text())
    runtime = supplement['runtime']
    assert runtime == '4865a7e0d2fa0766a32ca46fb049fefa2c8fb769'
    assert permit['runtime'] == runtime and permit['execution_scope'] == '32+16+4_EXCLUDED_RECEIVING_ONLY'
    assert git('rev-parse','HEAD^') == permit['parent']
    subprocess.run(['git','merge-base','--is-ancestor',runtime,'HEAD'],check=True)
    assert not git('status','--porcelain')
    changed = set(git('diff','--name-only',runtime,'HEAD').splitlines())
    assert changed == set(permit['allowed_changed_paths']), changed
    assert set(permit['sha256']) == changed - {str(PERMIT)}
    for p,h in permit['sha256'].items(): assert sha(ROOT/p)==h,p
    review = json.loads((ROOT / permit['review_path']).read_text())
    assert review['execution_authorized'] is True
    assert review['runtime'] == runtime
    assert review['supplement_sha256'] == sha(SUPPLEMENT)
    assert review['wrapper_sha256'] == sha(pathlib.Path(__file__))
    assert review['workflow_sha256'] == sha(ROOT/'.github/workflows/pr212-exact-compat-supplement.yml')
    replacements = supplement['substitutions']
    manifests = []
    original_union = {}
    for path,digest in supplement['manifest_sha256'].items():
        assert sha(ROOT/path)==digest,path
        manifest=json.loads((ROOT/path).read_text())
        for p,h in manifest['source_files_sha256'].items():
            assert p not in original_union or original_union[p]==h,p
            original_union[p]=h
            expected=replacements[p]['after'] if p in replacements else h
            if p in replacements: assert replacements[p]['before']==h
            assert sha(ROOT/p)==expected,p
        for p,h in manifest.get('authority_files',{}).items(): assert sha(ROOT/p)==h,p
        manifests.append(manifest)
    assert len(original_union)==64 and len(replacements)==2
    assert set(replacements) <= set(original_union)
    for p,h in supplement['reviewed_context_sha256'].items(): assert sha(ROOT/p)==h,p
    combat,token = manifests
    assert len(combat['stages'])==4 and sum(s['expected_cases'] for s in combat['stages'])==32
    assert [s['expected_cases'] for s in token['stages']]==[14,2,4]
    for manifest in manifests:
        assert [s['class'] for s in manifest['stages']]==[b['class'] for b in manifest['banks']]
        assert all(len(b['case_names'])==s['expected_cases'] for b,s in zip(manifest['banks'],manifest['stages']))
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/'source-provenance.json').write_text(json.dumps({
        'head':git('rev-parse','HEAD'),'tree':git('rev-parse','HEAD^{tree}'),
        'runtime':runtime,'changed_paths':sorted(changed),'permit':permit,
        'supplement':supplement,'review':review,'official_counters':0},indent=2)+'\n')
    return manifests

def execute(manifests):
    spec=importlib.util.spec_from_file_location('frozen_collector',
        'lab-coordinator/shared-capabilities/collect-priority-receiving.py')
    collector=importlib.util.module_from_spec(spec);spec.loader.exec_module(collector)
    reports=[]
    for index,manifest in enumerate(manifests):
        folder=OUT/('combat' if index==0 else 'token')
        folder.mkdir(parents=True,exist_ok=False)
        groups=[manifest['stages']] if index==0 else [[s] for s in manifest['stages']]
        for group in groups:
            command=['./gradlew']
            modules=list(dict.fromkeys(s['module'] for s in group))
            assert len(modules)==1
            command += [':'+modules[0].replace('/',':')+':test']
            for stage in group: command += ['--tests',stage['class']]
            command += ['--rerun-tasks','--no-build-cache','--console=plain','--max-workers=1',
                '-PkotlinCompileParallelism=1','-Pkotlin.compiler.execution.strategy=in-process',
                '-Dorg.gradle.jvmargs=-Xmx4g']
            log=folder/(group[0]['stage']+'.log')
            with log.open('wb') as stream:
                result=subprocess.run(command,stdout=stream,stderr=subprocess.STDOUT)
            for stage in group:
                retained=folder/'tests'/stage['stage'];retained.mkdir(parents=True,exist_ok=False)
                (retained/'exit-status.txt').write_text(str(result.returncode)+'\n')
                xml=ROOT/stage['module']/'build/test-results/test'/('TEST-'+stage['class']+'.xml')
                if xml.exists(): shutil.copyfile(xml,retained/xml.name)
        rows,errors=collector.collect_case_rows(folder,manifest['stages'])
        for bank,row in zip(manifest['banks'],rows):
            if set(row.get('case_names',[]))!=set(bank['case_names']):
                errors.append('Frozen case identities differ: '+bank['class'])
        report={'partition':'combat32' if index==0 else 'token16+entry4','stages':rows,
            'errors':errors,'status':'INCOMPLETE' if errors else 'PASS_REQUIRES_INDEPENDENT_AUDIT',
            'official_counters':0}
        (folder/'actual-case-audit.json').write_text(json.dumps(report,indent=2)+'\n')
        reports.append(report)
    # Detect tracked source changes during execution as well.
    assert not git('status','--porcelain')
    return 1 if any(r['errors'] for r in reports) else 0

if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    try:
        manifests=bind()
        sys.exit(execute(manifests))
    except Exception as exc:
        (OUT/'pretest-or-collector-failure.txt').write_text(type(exc).__name__+': '+str(exc)+'\n')
        raise
