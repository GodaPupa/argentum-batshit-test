"""INACTIVE proposal: exact-source excluded qualification, never gameplay authority.

The external controller must authenticate the approval and resolve the platform
restriction before publishing a triggering ref. This script checks correspondence;
it cannot authenticate its own workflow or confer publication/run permission.
"""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import xml.etree.ElementTree as ET

REPOSITORY = 'GodaPupa/argentum-batshit-test'
SOURCE_PARENT = '9a4230e0d2acbc38b1e8a7d3e6c3cda5dd41e459'
SOURCE_TREE = '6ec0b237d79ba160e28f0a5959ff12fbf2278497'
BASE_TREE = '867fcb4de592c77eac6dc63292a61cd1c54c1dd7'
REF = 'refs/heads/play-first/receiving-v2-corrected-nongame-qualification-20261011'
SOURCE_REF = 'refs/heads/play-first/receiving-v2-corrected-source-20261011'
WORKFLOW = '.github/workflows/play-first-receiving-v2-nongame-qualification-20261011.yml'
PREFIX = 'scripts/play-first/qualification-v2/'
ADDITIONS = {WORKFLOW, PREFIX+'qualify.py', PREFIX+'qualification.just', PREFIX+'source-pins.json'}
SOURCE_DELTA = {
    'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt': 'M',
    'scripts/play-first/strict_receiving.py': 'M',
    'scripts/play-first/test_strict_receiving.py': 'M',
    'scripts/play-first/test_authority_parity.py': 'A',
}
MODULES = ['test_isolated_case', 'test_isolated_receiving', 'test_authority_parity', 'test_strict_receiving']
ROOT = Path(os.environ['GITHUB_WORKSPACE']).resolve()

def require(value, message):
    if not value:
        raise ValueError(message)

def digest(data):
    return hashlib.sha256(data).hexdigest()

def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args], text=True).strip()

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as output:
        json.dump(value, output, indent=2, sort_keys=True)
        output.write('\n')

def layout():
    run = os.environ['GITHUB_RUN_ID']
    require(bool(re.fullmatch('[1-9][0-9]*', run)), 'invalid run ID')
    return Path(os.environ['RUNNER_TEMP']).resolve() / ('argentum-receiving-v2-' + run + '-attempt-1')

def validate():
    require(os.name == 'posix', 'trusted POSIX runner required')
    require(os.environ.get('GITHUB_RUN_ATTEMPT') == '1', 'attempt two is prohibited')
    require(os.environ.get('GITHUB_RUN_NUMBER') == '1', 'another workflow run is prohibited')
    require(os.environ.get('GITHUB_EVENT_NAME') == 'push', 'push-only qualification')
    require(os.environ.get('GITHUB_REPOSITORY') == REPOSITORY, 'repository mismatch')
    require(os.environ.get('GITHUB_REF') == REF, 'qualification ref mismatch')
    require(not os.environ.get('PYTHONOPTIMIZE') and not os.environ.get('PYTHONPATH'), 'inherited Python overrides refused')
    forbidden = [k for k,v in os.environ.items() if v and k.startswith('PLAY_FIRST') and (k.endswith('ACK') or 'CASE' in k or 'SOURCE_SHA' in k)]
    require(not forbidden, 'gameplay opt-ins or case/source variables are present')
    raw = os.environ.get('ARGENTUM_QUALIFICATION_APPROVAL_JSON', '')
    approval = json.loads(raw)
    require(approval.get('authorized') is True and approval.get('scope') == 'EXCLUDED_NON_GAMEPLAY_QUALIFICATION_ONLY', 'no external qualification approval')
    require(approval.get('gameplayAuthorized') is False, 'gameplayAuthorized must be explicitly false for excluded non-gameplay qualification')
    require(approval.get('platformResolutionRecord') and approval.get('qualificationAuthorityRecord'), 'missing external disposition records')
    require(approval.get('repository') == REPOSITORY and approval.get('validationRef') == REF, 'approval repository/ref mismatch')
    require(approval.get('sourceRef') == SOURCE_REF, 'source ref does not match the exact predeclared branch')
    require(approval.get('event') == 'push' and approval.get('expectedPythonTestsPerMode') == 84 and approval.get('expectedKotlinTests') == 8, 'qualification scope mismatch')
    for key in ('sourceCommit', 'validationCommit', 'sourceTree', 'validationTree', 'sourceParent', 'workflowBlob'):
        require(type(approval.get(key)) is str and bool(re.fullmatch('[0-9a-f]{40}', approval[key])), 'unbound approval: '+key)
    require(approval['sourceParent'] == SOURCE_PARENT and approval['sourceTree'] == SOURCE_TREE, 'corrected source binding mismatch')
    require(approval.get('workflowPath') == WORKFLOW and approval.get('runAttempt') == 1
            and type(approval.get('runNumber')) is int and approval['runNumber'] == 1,
            'approval workflow/attempt/run-number mismatch')
    require(git('rev-parse', 'HEAD') == approval['validationCommit'] == os.environ['GITHUB_SHA'], 'validation HEAD mismatch')
    require(git('rev-parse', 'HEAD^{tree}') == approval['validationTree'], 'validation tree mismatch')
    require(git('show', '-s', '--format=%P', 'HEAD').split() == [approval['sourceCommit']], 'validation must have one exact source parent')
    require(git('show', '-s', '--format=%P', approval['sourceCommit']).split() == [SOURCE_PARENT], 'corrected source must be one direct V2 child')
    require(git('rev-parse', approval['sourceCommit']+'^{tree}') == SOURCE_TREE, 'source tree mismatch')
    require(git('rev-parse', SOURCE_PARENT+'^{tree}') == BASE_TREE, 'baseline tree mismatch')
    source_delta = {p:s for s,p in (line.split('\t') for line in git('diff', '--no-renames', '--name-status', SOURCE_PARENT, approval['sourceCommit']).splitlines())}
    require(source_delta == SOURCE_DELTA, 'unexpected source changes')
    added = approval.get('addedFiles')
    require(type(added) is dict and set(added) == ADDITIONS, 'unexpected approval addition set')
    actual = git('diff', '--no-renames', '--name-status', approval['sourceCommit'], 'HEAD').splitlines()
    require(set(actual) == {'A\t'+p for p in ADDITIONS}, 'validation is not exactly four additions')
    for path, pin in added.items():
        require(type(pin) is dict and pin.get('mode') == '100644', 'addition mode mismatch')
        require(git('ls-tree', 'HEAD', '--', path).split('\t')[0] == '100644 blob '+pin['gitBlob'], 'addition blob mismatch: '+path)
    require(added[WORKFLOW]['gitBlob'] == approval['workflowBlob'], 'workflow blob mismatch')
    require(not git('status', '--porcelain=v1', '--untracked-files=all'), 'dirty checkout')
    config = json.loads((ROOT/(PREFIX+'source-pins.json')).read_text())
    require(config['sourceTree'] == SOURCE_TREE and config['sourceParent'] == SOURCE_PARENT, 'source pin document mismatch')
    for path, pin in config['files'].items():
        payload = (ROOT/path).read_bytes()
        require(digest(payload) == pin['sha256'] and git('rev-parse', approval['sourceCommit']+':'+path) == pin['gitBlob'], 'source file mismatch: '+path)
    return approval, config, digest(raw.encode())

def supervisor():
    spec = importlib.util.spec_from_file_location('qualified_source_supervisor', ROOT/'scripts/play-first/isolated_case.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def clean_result(result):
    return result.get('processStatus') == 'EXITED_ZERO' and type(result.get('returnCode')) is int and result['returnCode'] == 0 and result.get('cleanupUncertain') is False and result.get('residualGroupKilled') is False and result.get('cancellationSignal') is None

def gate():
    approval, config, approval_hash = validate()
    evidence = layout()
    marker = json.loads((evidence/'preflight.json').read_text())
    require(marker['approvalSha256'] == approval_hash and marker['validationCommit'] == approval['validationCommit']
            and marker.get('runId') == os.environ['GITHUB_RUN_ID'] and marker.get('runNumber') == 1,
            'preflight marker mismatch')
    return evidence, config

def prepare():
    approval, config, approval_hash = validate()
    evidence = layout()
    require(not evidence.exists(), 'original output already exists')
    require(ROOT not in evidence.parents and evidence != ROOT, 'evidence must be outside checkout')
    evidence.mkdir()
    save(evidence/'external-approval.json', approval)
    save(evidence/'preflight.json', {'approvalSha256':approval_hash, 'validationCommit':approval['validationCommit'], 'scope':'EXCLUDED_NON_GAMEPLAY_ONLY', 'gameplayAuthorized':False, 'runId':os.environ['GITHUB_RUN_ID'], 'runAttempt':1, 'runNumber':1})
    save(evidence/'run-source.json', {'repository':REPOSITORY, 'sourceCommit':approval['sourceCommit'], 'sourceTree':SOURCE_TREE, 'sourceParent':SOURCE_PARENT, 'validationCommit':approval['validationCommit'], 'validationTree':approval['validationTree'], 'ref':REF, 'event':'push', 'runId':os.environ['GITHUB_RUN_ID'], 'runAttempt':1, 'runNumber':1, 'workflowPath':WORKFLOW, 'workflowBlob':approval['workflowBlob']})
    (evidence/'source-tree.txt').write_text(git('ls-tree', '-r', 'HEAD')+'\n')
    for path in set(config['files']) | ADDITIONS:
        target = evidence/'source'/path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT/path, target)
    require(not list(ROOT.glob('**/build/test-results/test/TEST-*.xml')), 'fresh runner required; old JUnit XML present')

def python_checks():
    evidence, config = gate()
    save(evidence/'python-version.json', {'version':sys.version,'executable':sys.executable})
    overall = True
    for label, flags in [('normal', []), ('O', ['-O']), ('OO', ['-OO'])]:
        parent = evidence/'python'/label
        parent.mkdir(parents=True)
        fixtures = parent/'fixtures'
        for name in ('process','receiving','strict','tmp'):
            (fixtures/name).mkdir(parents=True)
        environment = {'PLAY_FIRST_PROCESS_FIXTURE_ROOT':str(fixtures/'process'), 'PLAY_FIRST_RECEIVING_FIXTURE_ROOT':str(fixtures/'receiving'), 'PLAY_FIRST_STRICT_FIXTURE_ROOT':str(fixtures/'strict'), 'TMPDIR':str(fixtures/'tmp')}
        previous = {k:os.environ.get(k) for k in environment}
        os.environ.update(environment)
        try:
            command = [sys.executable, *flags, '-m', 'unittest', '-v', *MODULES]
            result = supervisor().supervise(command, ROOT/'scripts/play-first', parent/'original', 300, grace=5, identity='EXCLUDED_PYTHON_'+label)
        finally:
            for key, value in previous.items():
                if value is None:os.environ.pop(key, None)
                else:os.environ[key]=value
        text = (parent/'original/child.log').read_text()
        reported = set(re.findall(r'^test_\w+ \(([\w.]+)\) \.\.\. ok$', text, re.M))
        success = clean_result(result) and bool(re.search(r'^Ran 84 tests in ', text, re.M)) and text.rstrip().endswith('OK') and reported == set(config['pythonTestIds'])
        save(parent/'assessment.json', {'passed':success,'expectedTests':84,'reportedPassedDesigns':len(reported),'notGameplayEvidence':True})
        overall = overall and success
    return 0 if overall else 1

def jvm(stage):
    evidence, config = gate()
    parent = evidence/'jvm'
    parent.mkdir(exist_ok=True)
    if stage == 'timing':
        require(clean_result(json.loads((parent/'compile/process-result.json').read_text())), 'compile failed; no retry or timing run')
        fixture = evidence/'timing-fixtures'
        fixture.mkdir()
        os.environ['PLAY_FIRST_TIMING_FIXTURE_ROOT'] = str(fixture)
    command = ['just', '--justfile', PREFIX+'qualification.just', '--working-directory', str(ROOT), stage]
    result = supervisor().supervise(command, ROOT, parent/stage, 1200 if stage=='compile' else 600, grace=5, identity='EXCLUDED_JVM_'+stage.upper())
    success = clean_result(result)
    if stage == 'timing' and success:
        paths = list(ROOT.glob('**/build/test-results/test/TEST-*.xml'))
        require(len(paths)==1 and paths[0].parent==ROOT/'gym/build/test-results/test', 'unexpected JUnit tests executed')
        suite = ET.parse(paths[0]).getroot()
        success = suite.get('name') == 'com.wingedsheep.gym.PlayFirstTimingJournalTest' and suite.get('tests') == '8' and all(suite.get(k)=='0' for k in ('failures','errors','skipped')) and {r.get('name') for r in suite.findall('testcase')} == set(config['kotlinTestNames'])
    save(parent/(stage+'-assessment.json'), {'passed':success,'adapterExecuted':False,'expectedTests':0 if stage=='compile' else 8})
    return 0 if success else 1

def preserve():
    evidence = layout()
    evidence.mkdir(exist_ok=True)
    save(evidence/'preservation.json', {'preflightPassed':(evidence/'preflight.json').exists(), 'note':'Preserves surviving fixtures and symlinks. Frozen tests clean some temporary synthetic Git objects; those are not durable originals. No retry or replacement.'})
    for module in ROOT.glob('**/build/test-results/test'):
        if module.is_dir():
            target=evidence/'junit'/module.relative_to(ROOT)
            target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copytree(module,target,symlinks=True)
    (evidence/'tracked-after.diff').write_text(git('diff'))
    (evidence/'status-after.txt').write_text(git('status','--porcelain=v1','--untracked-files=all'))
    inventory=[]
    for path in sorted(evidence.rglob('*')):
        mode=path.lstat().st_mode
        if stat.S_ISLNK(mode):
            inventory.append({'path':str(path.relative_to(evidence)),'type':'symlink','target':os.readlink(path)})
        elif stat.S_ISREG(mode):
            payload=path.read_bytes()
            inventory.append({'path':str(path.relative_to(evidence)),'type':'file','bytes':len(payload),'sha256':digest(payload)})
    save(evidence/'payload-inventory.json',inventory)
    archive=evidence.with_name(evidence.name+'-original.tar.gz')
    require(not archive.exists(), 'original archive already exists')
    with tarfile.open(archive,'x:gz',dereference=False) as output:
        output.add(evidence,arcname='evidence',recursive=True)
    checksum=archive.with_name(archive.name+'.sha256')
    with checksum.open('x') as output:output.write(digest(archive.read_bytes())+'  '+archive.name+'\n')
    require(not git('diff') and not git('status','--porcelain=v1','--untracked-files=all'), 'checkout changed during qualification; preserved as failure')

if __name__ == '__main__':
    stage=sys.argv[1] if len(sys.argv)==2 else ''
    require(stage in {'preflight','python','compile','timing','preserve'}, 'explicit fixed stage required')
    if stage=='preflight':prepare()
    elif stage=='python':sys.exit(python_checks())
    elif stage in {'compile','timing'}:sys.exit(jvm(stage))
    else:preserve()
