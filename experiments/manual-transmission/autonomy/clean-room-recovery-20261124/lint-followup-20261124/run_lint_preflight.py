#!/usr/bin/env python3
"""One root-run lint preflight. Drafting this file never starts a build.

The durable reservation must be the observed remote head. The new source test
may be untracked, but the worktree must otherwise be exactly S1 plus the two
approved patch files. This is separately counted; it is not a qualification.
"""
from pathlib import Path
from collections import Counter
import argparse
import base64
import datetime as dt
import fcntl
import gzip
import hashlib
import importlib.util
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import traceback

HOST = Path(__file__).resolve().parent
NAMESPACE = 'experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124'
RECORD = 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
BRANCH = 'manual-transmission/clean-room-recovery-20261124'
EPOCH = 'manual-lint-followup-20261124-01'
ATTEMPT = EPOCH + '/preflight-01'
S1 = '2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
CHARTER_COMMIT = '86450c1ecb227804dbd45acf5ba28689ce0c61bc'
PATCH_SHA = '2bab419c8cff73247321425ca56d1fc17a0e861cefbc859865423a62bc50ede3'
LINTER = 'mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/serialization/CardLinter.kt'
TEST = 'mtg-sdk/src/test/kotlin/com/wingedsheep/sdk/serialization/SelfAlternativeChoiceLintTest.kt'
PATCH_HASHES = {
    LINTER: 'c914ea3fd93bccdf4799825e133de809b38296191b121f61b0cd4b2f1f3eee86',
    TEST: 'cc203c2af5dfbf8755458e927a577ab1710995140bcb46aa623e93716930f2c7',
}
SDK_CLASS = 'com.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest'
SDK_NAMES = [
    'accepts a SELF_ALTERNATIVE read with an unconditional self-alternative cost',
    'accepts a SELF_ALTERNATIVE read with a conditional self-alternative cost',
    'accepts an ETB intervening-if SELF_ALTERNATIVE read with its declared cost',
    'rejects a SELF_ALTERNATIVE read when the cost is omitted',
    'rejects a SELF_ALTERNATIVE read when the cost is explicitly null',
    'does not declare KICKED from a self-alternative cost',
    'does not declare SELF_ALTERNATIVE from a kicker cost',
    'shares a front-face self-alternative declaration with a back-face reader',
]
SDK_IDENTITIES = ['\t'.join(('mtg-sdk', SDK_CLASS, name)) for name in SDK_NAMES]
FLAGS = ['--no-build-cache', '--no-daemon', '--console=plain', '--stacktrace', '--max-workers=2', '-PkotlinCompileParallelism=1']
COMMANDS = [
    ('sdk', ['scripts/gradle-locked', ':mtg-sdk:cleanTest', ':mtg-sdk:test', '--tests', SDK_CLASS, *FLAGS]),
    ('registry', ['scripts/gradle-locked', ':mtg-sets:cleanTest', ':mtg-sets:test', *FLAGS]),
]
MEMORY = b'\norg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC\nkotlin.compiler.execution.strategy=in-process\norg.gradle.workers.max=2\n'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def sha(data):
    return hashlib.sha256(data).hexdigest()


def metadata(path):
    data = path.read_bytes()
    return {'sha256': sha(data), 'bytes': len(data)}


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def git(repo, *args):
    result = subprocess.run(['git', *args], cwd=repo, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, 'Git read failed: ' + ' '.join(args[:2]))
    return result.stdout


def blob(carrier, revision, path):
    return git(carrier, 'show', revision + ':' + path)


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check_authority(args, source, carrier, out):
    require(re.fullmatch('[0-9a-f]{40}', args.authority_commit) is not None, 'An exact observed authority commit is required')
    authority = args.authority_commit
    current_bytes = blob(carrier, authority, NAMESPACE + '/CURRENT.json')
    current = json.loads(current_bytes)
    charter_bytes = blob(carrier, authority, NAMESPACE + '/CHARTER.json')
    charter = json.loads(charter_bytes)
    require(current['schema'] == 'manual-transmission/lint-followup-current/v1', 'Wrong CURRENT schema')
    require(current['recovery_id'] == EPOCH and current['branch'] == BRANCH, 'Wrong preflight epoch or branch')
    require(current['status'] == 'LINT_PREFLIGHT_RESERVED' and current['source_basis'] == S1, 'No matching durable preflight reservation')
    require(current['source_freeze'] is None, 'This preflight must precede the new source freeze')
    require(current['charter'] == NAMESPACE + '/CHARTER.json' and sha(charter_bytes) == current['charter_sha256'], 'Charter bytes differ from CURRENT')
    require(blob(carrier, CHARTER_COMMIT, NAMESPACE + '/CHARTER.json') == charter_bytes, 'Charter changed after explicit authorization')
    require(subprocess.run(['git', 'merge-base', '--is-ancestor', CHARTER_COMMIT, authority], cwd=carrier, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0, 'Authority does not descend from the authorized charter')
    require(charter['authorization']['state'] == 'EXPLICIT_USER_GRANT' and charter['source_basis'] == S1, 'Wrong source authority')
    require(charter['recovery_id'] == EPOCH and charter['branch'] == BRANCH, 'Wrong charter scope')
    amendment = charter['source_amendment']
    require(amendment['prepared_patch_sha256'] == PATCH_SHA, 'Wrong approved patch')
    require(sha(blob(carrier, amendment['prepared_patch_commit'], amendment['prepared_patch_path'])) == PATCH_SHA, 'Prepared patch bytes drifted')
    require(Counter(amendment['regression_identities']) == Counter(SDK_IDENTITIES), 'New regression inventory drifted')
    require(charter['required_gates']['registry'] == 401, 'Registry cardinality drifted')
    for name, expected in {'preflight_reserved':1, 'preflight_started':1, 'preflight_completed':0,
                           'local_reserved':0, 'local_started':0, 'local_completed':0,
                           'ci_activated':0, 'original_artifact_downloads':0}.items():
        value = current['attempts'][name]
        require(type(value) is int and value == expected, 'Attempt counter mismatch: ' + name)
    lease = current['lease']
    require(lease['sole_source_writer'] is True and lease['scheduled_executor_enabled'] is False, 'No exclusive foreground lease')
    require(dt.datetime.fromisoformat(lease['expires_utc']) > dt.datetime.now(dt.timezone.utc), 'Foreground lease expired')
    reservation = current['preflight_reservation']
    require(reservation['attempt_id'] == ATTEMPT and reservation['source_basis'] == S1, 'Wrong preflight reservation')
    require(reservation['prepared_patch_sha256'] == PATCH_SHA, 'Reservation patch mismatch')
    require(reservation['launcher_sha256'] == sha(Path(__file__).read_bytes()), 'Launcher differs from the reserved bytes')
    require(reservation['environment_helper_sha256'] == sha((HOST / 'environment.py').read_bytes()), 'Environment helper differs from reservation')
    require(Path(reservation['output_directory']).resolve() == out, 'Output directory differs from reservation')
    remote = git(carrier, 'ls-remote', 'origin', 'refs/heads/' + BRANCH).decode().splitlines()
    require(len(remote) == 1 and remote[0].split() == [authority, 'refs/heads/' + BRANCH], 'Observed remote authority is no longer current')
    require(git(source, 'rev-parse', 'HEAD').decode().strip() == S1, 'Source HEAD differs from S1')
    return current, current_bytes, charter_bytes


def source_snapshot(source, carrier):
    require(git(source, 'rev-parse', 'HEAD').decode().strip() == S1, 'Source HEAD changed')
    status = set(git(source, 'status', '--porcelain=v1', '--untracked-files=all', '-z').decode().split('\0')) - {''}
    require(status == {' M ' + LINTER, '?? ' + TEST}, 'Source must be exactly the modified linter plus the untracked new test')
    require(not git(source, 'diff', '--cached', '--name-only').strip(), 'Source index must remain unchanged')
    for name, expected in PATCH_HASHES.items():
        require(sha((source / name).read_bytes()) == expected, 'Prepared patch file mismatch: ' + name)
    for name in ('scripts/gradle-locked', RECORD + '/audit_reports.py', RECORD + '/validation.just', RECORD + '/CANDIDATE_PINS.json'):
        require((source / name).read_bytes() == blob(carrier, S1, name), 'Frozen execution input drift: ' + name)
    pins = json.loads(blob(carrier, S1, RECORD + '/CANDIDATE_PINS.json'))
    require(len(pins['bindings']) == 376, 'Original source binding count drift')
    for name, pin in pins['bindings'].items():
        require(metadata(source / name) == pin, 'Original protected binding changed: ' + name)
    files = {name: metadata(source / name) for name in sorted(set(pins['bindings']) | set(PATCH_HASHES))}
    require(len(files) == 378, 'Preflight source hash inventory must be 376 original plus two patch paths')
    tracked_diff = git(source, 'diff', '--binary', 'HEAD', '--')
    untracked = sorted(set(git(source, 'ls-files', '--others', '--exclude-standard', '-z').decode().split('\0')) - {''})
    return {'source_basis': S1, 'head': S1, 'head_tree': git(source, 'rev-parse', 'HEAD^{tree}').decode().strip(),
            'files': files, 'patch_files': {name: files[name] for name in PATCH_HASHES},
            'hash_inventory_scope': 'all 376 original bindings plus the two exact prepared patch files',
            'tracked_diff': tracked_diff.decode(), 'tracked_diff_sha256': sha(tracked_diff),
            'status_porcelain_v1': sorted(status), 'untracked_paths': untracked}


def report_files(source, module_buildfiles):
    for name in module_buildfiles:
        yield from sorted((source / name).parent.joinpath('build/test-results/test').glob('TEST-*.xml'))


def run_stage(name, argv, source, out, env, module_buildfiles, commands):
    # Match the frozen qualifier: remove all old JUnit XML, then run cleanTest.
    stale = list(report_files(source, module_buildfiles))
    save(out / (name + '-cleared-reports.json'), {p.relative_to(source).as_posix(): metadata(p) for p in stale})
    for path in stale:
        path.unlink()
    entry = {'name': name, 'argv': argv, 'cwd': str(source), 'started_utc': now(), 'ended_utc': None, 'returncode': None}
    commands.append(entry)
    save(out / 'COMMANDS.json', commands)
    print('START ' + name, flush=True)
    child = None
    try:
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            child = subprocess.Popen(argv, cwd=source, env=env, stdout=stdout, stderr=stderr, start_new_session=True)
            entry['returncode'] = child.wait()
    except BaseException:
        if child is not None and child.poll() is None:
            os.killpg(child.pid, signal.SIGTERM)
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()
            entry['returncode'] = child.returncode
        raise
    finally:
        entry['ended_utc'] = now()
        save(out / 'COMMANDS.json', commands)
        for path in report_files(source, module_buildfiles):
            require(not path.is_symlink(), 'Symlinked raw report')
            destination = out / name / path.relative_to(source)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
        print('END ' + name + ' ' + str(entry['returncode']), flush=True)
    return entry['returncode']


def inspect_gate(audit, out, name, expected, returncode):
    rows = audit.xml_rows(out / name)
    serial = [{'identity':r['identity'], 'skip':r['skip'], 'failure':r['failure'] is not None,
               'error':r['error'] is not None, 'xml':r['xml']} for r in rows]
    save(out / (name + '-ROWS.json'), serial)
    result = {'state':'FAIL', 'rows':len(rows),
              'passed':sum(not (r['skip'] or r['failure'] or r['error']) for r in serial),
              'failures':sum(r['failure'] for r in serial), 'errors':sum(r['error'] for r in serial),
              'skips':sum(r['skip'] for r in serial), 'identities':sorted(r['identity'] for r in serial),
              'raw_report_directory':name, 'returncode':returncode,
              'exact_identity_multiplicities':Counter(r['identity'] for r in serial) == Counter(expected)}
    if returncode == 0 and result['exact_identity_multiplicities'] and result['passed'] == sum(expected.values()) and not any(result[k] for k in ('failures','errors','skips')):
        audit.check_rows(out / name, expected)
        result['state'] = 'PASS'
    save(out / (name + '-CHECK.json'), result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--authority-commit', required=True)
    parser.add_argument('--source', default='/workspace/scratch/65bf30f1888a/manual-lint-source')
    parser.add_argument('--carrier', default='/workspace/scratch/65bf30f1888a/manual-transmission-clean-room')
    parser.add_argument('--out', default='/workspace/scratch/65bf30f1888a/manual-lint-preflight-01')
    args = parser.parse_args()
    source, carrier, out = (Path(value).resolve() for value in (args.source, args.carrier, args.out))
    require(not any(out.is_relative_to(p) for p in (source, carrier, HOST)), 'Evidence must be outside source, carrier and host trees')
    lockpath = Path.home() / '.cache/argentum/manual-transmission.lock'
    lockpath.parent.mkdir(parents=True, exist_ok=True)
    with lockpath.open('a+') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        current, current_bytes, charter_bytes = check_authority(args, source, carrier, out)
        require(not out.exists(), 'This reserved preflight already has local state; never relaunch it')
        require(not (HOST / 'BUILD_TOOLCHAIN_READY_PREFLIGHT.json').exists(), 'A previous readiness receipt already exists')
        out.mkdir(parents=True)
        (out / 'AUTHORITY_CURRENT.json').write_bytes(current_bytes)
        (out / 'AUTHORITY_CHARTER.json').write_bytes(charter_bytes)
        shutil.copy2(Path(__file__), out / 'run_lint_preflight.py')
        shutil.copy2(HOST / 'environment.py', out / 'environment.py')
        shutil.copy2(HOST / 'HOST_PROVISIONING_RECEIPT.json', out / 'HOST_PROVISIONING_RECEIPT.json')
        start = {'schema':'manual-transmission/lint-followup-preflight-start/v1', 'state':'PREFLIGHT_STARTED',
                 'authority_commit':args.authority_commit, 'recovery_id':EPOCH, 'attempt_id':ATTEMPT,
                 'source_basis':S1, 'prepared_patch_sha256':PATCH_SHA, 'started_utc':now(),
                 'preflight_invocations':1, 'full_qualification_invocations':0, 'process_id':os.getpid(),
                 'lock':'standard exclusive Manual flock', 'planned_commands':[{'name':n,'argv':a} for n,a in COMMANDS]}
        save(out / 'PREFLIGHT_START.json', start)
        commands, results = [], {}
        before = after = None
        props = original = None
        restored = False
        failure = None
        old_handlers = {}
        def interrupted(signum, frame):
            raise InterruptedError('Preflight interrupted by signal ' + str(signum))
        for signum in (signal.SIGINT, signal.SIGTERM):
            old_handlers[signum] = signal.signal(signum, interrupted)
        try:
            before = source_snapshot(source, carrier)
            save(out / 'source-before.json', before)
            baseline = json.loads(gzip.decompress(base64.b64decode(blob(carrier, S1, RECORD + '/ACCEPTED_INVENTORY.json.gz.b64'))))
            matrix = json.loads(blob(carrier, S1, RECORD + '/TEST_MATRIX.json'))
            registry = Counter(baseline['cards']['identities'])
            registry.update(row['identity'] for row in matrix['rows'] if row['family'] == 'registry')
            require(sum(registry.values()) == 401 and not baseline['cards']['skips'], 'Frozen registry inventory drift')
            expected = {'sdk':Counter(SDK_IDENTITIES), 'registry':registry}
            save(out / 'EXPECTED_IDENTITIES.json', {name:dict(value) for name,value in expected.items()})
            sys.dont_write_bytecode = True
            environment = load_module('manual_lint_preflight_environment', HOST / 'environment.py')
            # Same live process and same lock as both child commands; never reuse old ports.
            env = environment.prepare_environment()
            props = Path(env['GRADLE_USER_HOME']) / 'gradle.properties'
            original = props.read_bytes()
            props.write_bytes(original + MEMORY)
            env.update(PYTHONDONTWRITEBYTECODE='1', GRADLE_LOCK_SLOTS='1')
            for key in ('KOTEST_INCLUDE_PATTERN', 'UPDATE_SNAPSHOTS'):
                env.pop(key, None)
            audit = load_module('manual_lint_preflight_frozen_audit', source / RECORD / 'audit_reports.py')
            tracked_paths = git(source, 'ls-files', '-z').decode().split('\0')
            module_buildfiles = [name for name in tracked_paths if name.endswith('build.gradle.kts') and not any(part in Path(name).parts for part in ('node_modules','.gradle','build'))]
            for name, argv in COMMANDS:
                code = run_stage(name, argv, source, out, env, module_buildfiles, commands)
                results[name] = inspect_gate(audit, out, name, expected[name], code)
                require(results[name]['state'] == 'PASS', name + ' gate did not pass exactly')
        except BaseException as exc:
            failure = {'type':type(exc).__name__, 'message':str(exc)}
            (out / 'FAILURE.txt').write_text(traceback.format_exc())
        finally:
            if props is not None and original is not None:
                try:
                    props.write_bytes(original)
                    restored = props.read_bytes() == original
                except BaseException as exc:
                    failure = failure or {'type':type(exc).__name__, 'message':'Host property restoration failed'}
                    (out / 'RESTORATION_FAILURE.txt').write_text(traceback.format_exc())
            try:
                after = source_snapshot(source, carrier)
                save(out / 'source-after.json', after)
            except BaseException as exc:
                failure = failure or {'type':type(exc).__name__, 'message':'Source verification failed after preflight'}
                (out / 'SOURCE_VERIFICATION_FAILURE.txt').write_text(traceback.format_exc())
            unchanged = before is not None and after == before
            passed = failure is None and restored and unchanged and all(results.get(name,{}).get('state') == 'PASS' for name,_ in COMMANDS)
            receipt = {'schema':'manual-transmission/lint-followup-preflight/v1', 'state':'PASS' if passed else 'FAIL',
                       'authority_commit':args.authority_commit, 'recovery_id':EPOCH, 'attempt_id':ATTEMPT,
                       'source_basis':S1, 'prepared_patch_sha256':PATCH_SHA,
                       'patch_files':before['patch_files'] if before else {},
                       'source_exact_prepared_patch':before is not None, 'source_unchanged':unchanged,
                       'source_before_manifest_sha256':metadata(out / 'source-before.json')['sha256'] if before else None,
                       'source_after_manifest_sha256':metadata(out / 'source-after.json')['sha256'] if after else None,
                       'original_bindings_verified':376 if before is not None and after is not None else 0,
                       'host_properties_restored':restored, 'started_utc':start['started_utc'], 'ended_utc':now(),
                       'preflight_invocations':1, 'full_qualification_invocations':0,
                       'gradle_commands_started':len(commands), 'sdk_gate':results.get('sdk'), 'registry_gate':results.get('registry'),
                       'manifest_path':'MANIFEST.json', 'failure':failure, 'admitted':False,
                       'ci_activations':0, 'original_artifact_downloads':0, 'phase2_games':0, 'phase2_seeds':0}
            save(out / 'COMMANDS.json', commands)
            if passed:
                ready = {'state':'PASS', 'schema':'manual-transmission/lint-preflight-build-ready/v1',
                         'host_properties_restored':True, 'authority_commit':args.authority_commit,
                         'attempt_id':ATTEMPT, 'source_basis':S1, 'prepared_patch_sha256':PATCH_SHA,
                         'sdk_rows':8, 'registry_rows':401, 'preflight_invocations':1,
                         'full_qualification_invocations':0, 'ended_utc':receipt['ended_utc']}
                save(HOST / 'BUILD_TOOLCHAIN_READY_PREFLIGHT.json', ready)
                shutil.copy2(HOST / 'BUILD_TOOLCHAIN_READY_PREFLIGHT.json', out / 'BUILD_TOOLCHAIN_READY_PREFLIGHT.json')
            save(out / 'PREFLIGHT_RECEIPT.json', receipt)
            save(out / 'MANIFEST.json', {'schema':'manual-transmission/preflight-files/v1',
                'files':{p.relative_to(out).as_posix():metadata(p) for p in sorted(out.rglob('*')) if p.is_file() and p != out / 'MANIFEST.json'}})
            for signum, handler in old_handlers.items():
                signal.signal(signum, handler)
            print(json.dumps({'state':receipt['state'], 'receipt':str(out / 'PREFLIGHT_RECEIPT.json'),
                              'host_properties_restored':restored, 'source_unchanged':unchanged,
                              'full_qualification_invocations':0}), flush=True)
        return 0 if passed else 1


if __name__ == '__main__':
    sys.exit(main())
