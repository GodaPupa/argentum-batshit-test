"""Receive a bounded source-history diagnostic, not an implementation qualification."""
from pathlib import Path
import hashlib, json, os, re, shutil, subprocess, sys
import xml.etree.ElementTree as ET

BASE = 'e067e4b803d3cb2358d0b363929b940ebcfa2cbf'
ROOT = Path('experiments/manual-transmission/research/source-history-diagnostic-r2-20261025')
OUT = Path('manual-source-history-diagnostic-r2-evidence')
TEST = 'rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/CurrentTargetSourceHistoryDiagnosticTest.kt'
CLS = 'com.wingedsheep.engine.scenarios.CurrentTargetSourceHistoryDiagnosticTest'
NAMES = [
    'SH01 live red source respects protection',
    'SH02 departed trigger still destroys an unprotected target',
    'SH03 departed activation still destroys an unprotected target',
    'SH04 departed red trigger respects captured source protection',
    'SH05 departed red activation respects captured source protection',
]
sha = lambda data: hashlib.sha256(data).hexdigest()
git = lambda *args: subprocess.check_output(['git', *args], text=True).strip()
SOURCE = os.environ['SOURCE']

def bind():
    assert git('rev-parse', SOURCE + '^') == BASE
    assert git('rev-parse', SOURCE + '^{tree}') == os.environ['SOURCE_TREE']
    assert git('rev-parse', 'HEAD^') == SOURCE
    assert git('diff', '--name-only', SOURCE, 'HEAD') == os.environ['WORKFLOW']
    assert os.environ['GITHUB_RUN_NUMBER'] == os.environ['GITHUB_RUN_ATTEMPT'] == '1'
    expected = {TEST} | {str(ROOT / name) for name in ['PROTOCOL.json', 'gate.py', 'REVIEW.json']}
    delta = [line.split('\t') for line in git('diff', '--name-status', BASE, SOURCE).splitlines()]
    assert len(delta) == 4 and {row[1] for row in delta} == expected and all(row[0] == 'A' for row in delta), delta
    assert not git('status', '--porcelain', '--untracked-files=no')
    prior = json.loads(Path('experiments/manual-transmission/research/current-target-resolution-r5-20261025/PROTOCOL.json').read_text())
    assert git('rev-parse', 'refs/remotes/origin/main') == prior['protected']['main']
    for path, digest in prior['implementation_sha256'].items():
        assert sha(Path(path).read_bytes()) == digest, path
    bindings = json.loads(Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023/BINDINGS.json').read_text())
    assert len(bindings) == 144
    resolver = prior['authorization']['replaced_binding']
    for path, spec in bindings.items():
        expected_hash = prior['implementation_sha256'][path] if path == resolver else spec['sha256']
        assert sha(Path(path).read_bytes()) == expected_hash, path
    goldens = json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
    assert len(goldens) == 23
    for item in goldens:
        assert sha(Path(item['path']).read_bytes()) == item['sha256'], item['path']
    assert len(prior['legacy_fixture_sha256']) == 82
    for path, digest in prior['legacy_fixture_sha256'].items():
        assert sha(Path(path).read_bytes()) == digest, path
    deck = 'experiments/manual-transmission/control/v0.7.decklist.txt'
    assert git('rev-parse', SOURCE + ':' + deck) == prior['protected']['deck_blob']
    OUT.mkdir(exist_ok=True)
    (OUT / 'BINDING.json').write_text(json.dumps(dict(source=SOURCE, tree=os.environ['SOURCE_TREE'], validation=git('rev-parse','HEAD'), base=BASE, delta=delta, protected_bindings=143, accepted_resolver_unchanged=True, goldens=23, fixtures=82, run=os.environ['GITHUB_RUN_ID'], attempt=1, job=os.environ['GITHUB_JOB']), indent=2)+'\n')
    flags = '--no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1'
    (OUT / 'validation.just').write_text('compile:\n    scripts/gradle-locked :rules-engine:compileKotlin :rules-engine:compileTestKotlin '+flags+'\n\ndiagnostic:\n    scripts/gradle-locked :rules-engine:test --tests \'*CurrentTargetSourceHistoryDiagnosticTest\' '+flags+'\n')

def prepare():
    for file in Path('.').glob('**/build/test-results/test/TEST-*.xml'):
        if OUT.name not in file.parts: file.unlink()

def receive():
    assert (OUT/'compile-exit.txt').read_text().strip() == '0', 'Compilation is not behavioral evidence'
    log = (OUT/'diagnostic.log').read_text()
    assert 'compileTestKotlin FAILED' not in log and 'compileKotlin FAILED' not in log
    files = [p for p in Path('.').glob('**/build/test-results/test/TEST-*.xml') if OUT.name not in p.parts]
    assert len(files) == 1, files
    suite = ET.parse(files[0]).getroot(); rows = suite.findall('testcase')
    assert len(rows) == 5 and suite.get('tests') == '5'
    assert sorted((r.get('classname'),r.get('name')) for r in rows) == sorted((CLS,n) for n in NAMES)
    assert all(r.find('error') is None and r.find('skipped') is None for r in rows)
    failures = {r.get('name'): r.find('failure') for r in rows if r.find('failure') is not None}
    assert suite.get('errors') == '0' and suite.get('skipped') == '0'
    assert int(suite.get('failures')) == len(failures)
    if not failures:
        assert (OUT/'test-exit.txt').read_text().strip() == '0'
        result = 'HYPOTHESIS_NOT_REPRODUCED_ALL_FIVE_PASS'
    else:
        assert set(failures) == set(NAMES[3:]), list(failures)
        assert (OUT/'test-exit.txt').read_text().strip() != '0'
        for name, line in zip(NAMES[3:], [81,85]):
            failure = failures[name]
            assert failure.get('type') == 'org.opentest4j.AssertionFailedError', failure.attrib
            assert f'CurrentTargetSourceHistoryDiagnosticTest.kt:{line}' in (failure.text or '')
            assert 'expected:<true> but was:<false>' in (failure.text or '') or 'expected: true but was: false' in (failure.text or '')
        result = 'REPRODUCED_TWO_SOURCE_HISTORY_PROTECTION_FAILURES_THREE_CONTROLS_PASS'
    dst = OUT/'junit'/files[0]; dst.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(files[0],dst)
    (OUT/'DIAGNOSTIC_RESULT.json').write_text(json.dumps(dict(state=result, cases=5, failures=len(failures), errors=0, skips=0, source_unchanged=True, implementation_acceptance=False, frames=[81,85] if failures else []),indent=2)+'\n')

def archive():
    OUT.mkdir(exist_ok=True)
    for path in [TEST, str(ROOT/'PROTOCOL.json'), str(ROOT/'gate.py'), str(ROOT/'REVIEW.json')]:
        dst=OUT/'source'/path; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(path,dst)
    shutil.copyfile(os.environ['WORKFLOW'], OUT/'validation-workflow.yml')
    (OUT/'tracked-status.txt').write_text(git('status','--porcelain','--untracked-files=no')+'\n')
    (OUT/'source-delta.patch').write_text(git('diff',BASE,SOURCE)+'\n')
    manifest={str(p.relative_to(OUT)):dict(bytes=p.stat().st_size,sha256=sha(p.read_bytes())) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'}
    (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__ == '__main__':
    {'bind':bind,'prepare':prepare,'receive':receive,'archive':archive}[sys.argv[1]]()
