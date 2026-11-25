"""One hosted readiness sequence and, after proof, one unchanged qualification.

Importing this module and audit_readiness() are read-only. Execution requires the
explicit __main__ entry point. Readiness evidence is outside the frozen qualifier.
"""
from pathlib import Path
from collections import Counter
from datetime import datetime, timezone
import argparse
import fcntl
import hashlib
import json
import os
import re
import signal
import shutil
import subprocess
import sys
import threading
import time
import traceback
import urllib.request
import xml.etree.ElementTree as ET

RECORD = 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
RECOVERY_ID = 'manual-host-requalification-20261124-01'
RUNTIME_NAME = 'manual-host-requalification-runtime'
TOOLS_NAME = 'manual-host-requalification-tools'
WORKER_MAIN = 'worker.org.gradle.process.internal.worker.GradleWorkerMain'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


def read(path):
    return json.loads(Path(path).read_bytes())


def utc():
    return datetime.now(timezone.utc).isoformat()


def stamp(path, data):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    Path(path).write_bytes((json.dumps(data, indent=2) + '\n').encode())


def bindings(envelope):
    return dict(event=envelope['event'], source=envelope['source'],
                source_tree=envelope['qualification']['source_tree'],
                validation=envelope['validation'], run_id=int(envelope['run']),
                job_id=envelope['job_id'], selected_plan='B_HOSTED_ONCE',
                execution_environment='github_hosted', local_pass=False)


def test_executor_label(argv):
    """Gradle 9.6.1 appends its quoted worker display name after WorkerMain."""
    if not isinstance(argv, list) or argv.count(WORKER_MAIN) != 1:
        return None
    trailing = argv[argv.index(WORKER_MAIN) + 1:]
    if len(trailing) != 1 or not isinstance(trailing[0], str):
        return None
    label = trailing[0]
    if len(label) >= 2 and label[0] == label[-1] == "'":
        label = label[1:-1]
    return label if re.fullmatch(r'Gradle Test Executor [0-9]+', label) else None


def parse_xml(runtime, contract):
    """Parse only the preserved readiness XML, preserving exact test identities."""
    folder = Path(runtime) / contract['readiness_xml_directory']
    files = sorted(folder.glob('TEST-*.xml'))
    require(bool(files), 'No original readiness XML')
    rows, paths = [], {}
    for file in files:
        data = file.read_bytes()
        require(b'<!DOCTYPE' not in data and b'<!ENTITY' not in data, 'XML entity declaration')
        paths[file.relative_to(runtime).as_posix()] = sha(data)
        root = ET.fromstring(data)
        for case in root.iter('testcase'):
            identity = '\t'.join(('game-server', case.get('classname', ''), case.get('name', '')))
            flags = dict(failures=len(case.findall('failure')), errors=len(case.findall('error')),
                         skipped=len(case.findall('skipped')))
            require(all(v <= 1 for v in flags.values()), 'Duplicate testcase outcome flags')
            rows.append(dict(identity=identity, **flags))
    actual = Counter(row['identity'] for row in rows)
    summary = dict(rows=len(rows), passed=sum(not any(row[k] for k in ('failures', 'errors', 'skipped')) for row in rows),
                   failures=sum(row['failures'] for row in rows), errors=sum(row['errors'] for row in rows),
                   skipped=sum(row['skipped'] for row in rows),
                   mockk=sum('\tcom.wingedsheep.gameserver.ai.AiControllerProviderTest\t' in row['identity'] for row in rows),
                   flyway=sum('\tcom.wingedsheep.gameserver.persistence.FlywayMigrationTest\t' in row['identity'] for row in rows))
    return dict(identities=dict(actual), summary=summary, xml_sha256=paths)


def audit_readiness(runtime, contract, envelope):
    """Read-only, artifact-safe proof; never launches, imports project code or writes."""
    runtime = Path(runtime)
    require(contract['schema'] == 'manual-transmission/hosted-readiness-contract/v1', 'Readiness contract schema')
    require(contract['recovery_id'] == RECOVERY_ID and contract['selected_plan'] == 'B_HOSTED_ONCE', 'Readiness authority')
    require(contract['execution_environment'] == 'github_hosted' and contract['local_pass'] is False, 'Readiness environment')
    contract_bytes = (runtime / 'READINESS_CONTRACT.json').read_bytes()
    require(json.loads(contract_bytes) == contract, 'Readiness contract bytes/content')
    q = envelope['qualification']
    require(sha(contract_bytes) == q['readiness_contract_sha256'], 'Readiness contract freeze')
    receipt = read(runtime / contract['readiness_receipt'])
    require(receipt['schema'] == 'manual-transmission/hosted-readiness/v1', 'Readiness receipt schema')
    require(receipt['state'] == 'PASS', 'Readiness did not pass')
    expected = bindings(envelope)
    require(all(receipt[k] == v for k, v in expected.items()), 'Readiness run/source binding')
    require(type(receipt['readiness_sequence']) is int and type(receipt['gradle_invocations']) is int
            and receipt['readiness_sequence'] == receipt['gradle_invocations'] == 1, 'Readiness invocation count')
    require(type(receipt['run_id']) is int and receipt['run_id'] > 0 and type(receipt['job_id']) is int and receipt['job_id'] > 0, 'Readiness positive numeric run/job identity')
    require(receipt['contract_sha256'] == q['readiness_contract_sha256'], 'Readiness contract identity')
    require(receipt['helper_sha256'] == q['readiness_helper_sha256'], 'Readiness helper identity')
    require(receipt['source_bindings_verified'] == contract['full_source_bindings'] == 378, 'Readiness binding count')
    require(receipt['source_restored'] is True, 'Readiness source restoration')
    require(receipt['gradle_returncode'] == 0, 'Readiness Gradle exit')
    parsed = parse_xml(runtime, contract)
    require(parsed['identities'] == contract['expected_identities'], 'Readiness exact identity multiset')
    require(parsed['summary'] == contract['expected_summary'], 'Readiness original XML outcomes')
    require(receipt['summary'] == parsed['summary'] and receipt['xml_sha256'] == parsed['xml_sha256'], 'Readiness receipt/raw discrepancy')
    source_state = read(runtime / 'readiness' / 'SOURCE_STATE.json')
    pins_bytes = (runtime / 'readiness' / 'CANDIDATE_PINS.json').read_bytes()
    require(blob(pins_bytes) == q['candidate_pins_blob'], 'Readiness immutable source pins blob')
    pinned_bindings = json.loads(pins_bytes)['bindings']
    require(source_state['before'] == pinned_bindings and source_state['after'] == pinned_bindings, 'Readiness exact immutable S3 bindings')
    require(source_state['before'] == source_state['after'] and len(source_state['before']) == 378, 'Readiness immutable sources changed')
    require(sha(json.dumps(source_state['before'], sort_keys=True, separators=(',', ':')).encode()) == source_state['expected_bindings_sha256'], 'Readiness actual binding table digest')
    require(source_state['test_sources'] == contract['test_sources'], 'Readiness unchanged exact test sources')
    require(source_state['tracked_clean_before'] is True and source_state['tracked_clean_after'] is True, 'Readiness tracked cleanliness')
    require(source_state['expected_bindings_sha256'] == receipt['expected_bindings_sha256'], 'Readiness binding table digest')
    toolchain = read(runtime / 'HOST_TOOLCHAIN.json')
    require(toolchain['runner_environment'] == 'github-hosted' and toolchain['runner_os'] == contract['runner_os'] and toolchain['runner_arch'] == contract['runner_arch'], 'Hosted VM identity')
    require(toolchain['java_runtime_version'] == contract['jdk_runtime_version'] and toolchain['java_implementor'] == 'Eclipse Adoptium', 'Exact JDK identity')
    require(toolchain['node_version'] == contract['node_version'] and toolchain['npm_version'] == contract['npm_version'], 'Exact Node/npm identity')
    require(toolchain['gradle_version'] == contract['gradle_version'], 'Exact Gradle identity')
    require(('Welcome to Gradle ' + contract['gradle_version'] + '!') in (runtime / 'readiness' / 'gradle.stdout').read_text(), 'Actual fresh Gradle version output')
    require(toolchain['agent']['sha256'] == contract['agent']['sha256'] and toolchain['agent']['bytes'] == contract['agent']['bytes'], 'Exact startup agent identity')
    agent_bytes = (runtime / 'host' / 'byte-buddy-agent-1.18.10.jar').read_bytes()
    require(sha(agent_bytes) == contract['agent']['sha256'] and len(agent_bytes) == contract['agent']['bytes'], 'Original startup agent bytes')
    require(toolchain['docker_version_returncode'] == toolchain['docker_info_returncode'] == 0, 'Docker host unavailable')
    require(sha((runtime / 'HOST_TOOLCHAIN.json').read_bytes()) == receipt['toolchain_sha256'], 'Toolchain evidence hash')
    settings = read(runtime / 'HOST_SETTINGS.json')
    require(settings['gradle_properties'] == contract['host_gradle_properties'], 'Host memory settings')
    require(settings['JAVA_TOOL_OPTIONS'] == '-javaagent:' + toolchain['agent']['path'], 'Startup agent environment')
    require(settings['GRADLE_LOCK_SLOTS'] == '1' and settings['GRADLE_OPTS'] == '-Dorg.gradle.daemon=false', 'Host lock and daemon settings')
    require(settings['KOTEST_INCLUDE_PATTERN'] is None and settings['UPDATE_SNAPSHOTS'] is None, 'Forbidden host test filter')
    require(sha((runtime / 'HOST_SETTINGS.json').read_bytes()) == receipt['host_settings_sha256'], 'Host settings evidence hash')
    workers = read(runtime / 'readiness' / 'TEST_WORKERS.json')
    require(bool(workers), 'No actual Gradle test worker observed')
    require(all(w['java_tool_options'] == settings['JAVA_TOOL_OPTIONS'] and w['gradle_user_home'] == settings['GRADLE_USER_HOME']
                and w['java_executable'] == toolchain['java_executable']
                and test_executor_label(w['argv']) is not None
                and w['test_executor_label'] == test_executor_label(w['argv']) for w in workers), 'Actual Test Executor startup settings')
    command = read(runtime / 'readiness' / 'COMMAND.json')
    require(command['argv'] == contract['gradle_argv'] and command['returncode'] == 0, 'Readiness actual command')
    require(command['started_utc'] >= receipt['started_utc'] and command['ended_utc'] <= receipt['ended_utc'], 'Readiness command temporal binding')
    require(command['host_settings_sha256'] == receipt['host_settings_sha256'], 'Readiness command environment identity')
    require(command['timed_out'] is False, 'Readiness command timeout')
    require(receipt['started_utc'] < receipt['ended_utc'], 'Readiness timestamp order')
    return dict(state='READINESS_AUDIT_PASS', **expected, summary=parsed['summary'],
                contract_sha256=receipt['contract_sha256'], helper_sha256=receipt['helper_sha256'],
                host_settings_sha256=receipt['host_settings_sha256'], toolchain_sha256=receipt['toolchain_sha256'],
                readiness_started_utc=receipt['started_utc'], readiness_ended_utc=receipt['ended_utc'])


def command(argv, cwd, env, out, timeout, observer=None, stream_stdout=False):
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    record = dict(argv=list(argv), cwd=str(cwd), started_utc=utc(), timed_out=False)
    with out.with_suffix('.stdout').open('wb') as stdout, out.with_suffix('.stderr').open('wb') as stderr:
        process = subprocess.Popen(argv, cwd=cwd, env=env, stdout=subprocess.PIPE if stream_stdout else stdout, stderr=stderr, start_new_session=True)
        record['pid'] = process.pid
        stamp(out.with_suffix('.command.json'), record)
        tee_thread = None
        if stream_stdout:
            def tee():
                for data in iter(process.stdout.readline, b''):
                    stdout.write(data)
                    stdout.flush()
                    sys.stdout.buffer.write(data)
                    sys.stdout.buffer.flush()
            tee_thread = threading.Thread(target=tee, daemon=True)
            tee_thread.start()
        if observer is not None:
            observer.start()
        try:
            record['returncode'] = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            record['timed_out'] = True
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)
            record['returncode'] = process.returncode
        finally:
            if observer is not None:
                observer.stop()
            if tee_thread is not None:
                tee_thread.join(timeout=10)
    record['ended_utc'] = utc()
    stamp(out.with_suffix('.command.json'), record)
    return record


class WorkerObserver:
    def __init__(self, env):
        self.env = env
        self.rows = {}
        self.done = threading.Event()
        self.thread = threading.Thread(target=self.observe, daemon=True)

    def observe(self):
        while not self.done.is_set():
            for process in Path('/proc').glob('[0-9]*'):
                try:
                    argv = [v.decode(errors='replace') for v in (process / 'cmdline').read_bytes().split(b'\0') if v]
                    executor_label = test_executor_label(argv)
                    if executor_label is None:
                        continue
                    variables = dict(v.split(b'=', 1) for v in (process / 'environ').read_bytes().split(b'\0') if b'=' in v)
                    if variables.get(b'GRADLE_USER_HOME', b'').decode() != self.env['GRADLE_USER_HOME']:
                        continue
                    pid = int(process.name)
                    if pid not in self.rows:
                        self.rows[pid] = dict(pid=pid, observed_utc=utc(), argv=argv, test_executor_label=executor_label,
                                             java_tool_options=variables.get(b'JAVA_TOOL_OPTIONS', b'').decode(),
                                             gradle_user_home=variables[b'GRADLE_USER_HOME'].decode(),
                                             java_executable=str((process / 'exe').resolve()))
                except (FileNotFoundError, PermissionError, ProcessLookupError, ValueError, OSError):
                    continue
            self.done.wait(0.1)

    def start(self):
        self.thread.start()

    def stop(self):
        self.done.set()
        self.thread.join(timeout=10)


def source_state(repo, pins):
    actual = {}
    for path, pin in pins['bindings'].items():
        data = (repo / path).read_bytes()
        actual[path] = dict(sha256=sha(data), bytes=len(data))
    require(actual == pins['bindings'], 'Actual source bindings differ')
    status = subprocess.check_output(['git', 'diff', '--name-only'], cwd=repo)
    require(not status.strip(), 'Tracked source changed')
    return actual


def run_hosted(repo, runtime):
    repo, runtime = Path(repo).resolve(), Path(runtime).resolve()
    contract = read(runtime / 'READINESS_CONTRACT.json')
    envelope = read(runtime / 'RECOVERY_EVENT.json')
    q = envelope['qualification']
    require(sha(Path(__file__).read_bytes()) == q['readiness_helper_sha256'], 'Executed helper mismatch')
    require(sha((runtime / 'READINESS_CONTRACT.json').read_bytes()) == q['readiness_contract_sha256'], 'Executed contract mismatch')
    require(runtime.name == RUNTIME_NAME and runtime.parent == Path(os.environ['RUNNER_TEMP']).resolve(), 'Runtime location')
    require(not (runtime / 'HOSTED_RUN.json').exists(), 'Hosted sequence already started')
    require(os.environ['GITHUB_ACTIONS'] == 'true' and os.environ['RUNNER_ENVIRONMENT'] == 'github-hosted', 'Expected hosted execution')
    state = dict(schema='manual-transmission/hosted-run/v1', **bindings(envelope),
                 state='READINESS_STARTING', readiness_sequences_started=1, readiness_sequences_completed=0,
                 qualification_starts=0, qualification_completed=0, started_utc=utc(),
                 readiness_started_utc=utc(), host_settings_unchanged=True, admitted=False)
    stamp(runtime / 'HOSTED_RUN.json', state)
    receipt = dict(schema='manual-transmission/hosted-readiness/v1', **bindings(envelope),
                   state='IN_PROGRESS', readiness_sequence=1, gradle_invocations=0,
                   started_utc=state['readiness_started_utc'], helper_sha256=q['readiness_helper_sha256'],
                   contract_sha256=q['readiness_contract_sha256'], source_restored=False)
    stamp(runtime / 'HOSTED_READINESS.json', receipt)
    lock_file = Path.home() / '.cache/argentum/manual-transmission.lock'
    lock_file.parent.mkdir(parents=True, exist_ok=True)
    qualifier_started = False
    before = None
    pins = read(repo / RECORD / 'CANDIDATE_PINS.json')
    try:
        with lock_file.open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            before = source_state(repo, pins)
            (runtime / 'readiness').mkdir(exist_ok=False)
            shutil.copyfile(repo / RECORD / 'CANDIDATE_PINS.json', runtime / 'readiness' / 'CANDIDATE_PINS.json')
            require(len(before) == contract['full_source_bindings'], 'Expected full source binding count')
            for path, pin in contract['test_sources'].items():
                test_bytes = (repo / path).read_bytes()
                require(dict(bytes=len(test_bytes), sha256=sha(test_bytes)) == pin, 'Frozen actual readiness test source mismatch')
            require(sha((repo / RECORD / 'qualify.py').read_bytes()) == contract['qualifier_sha256'], 'Frozen qualifier changed')
            agent = runtime / 'host' / 'byte-buddy-agent-1.18.10.jar'
            agent.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(contract['agent']['url'], timeout=120) as response:
                data = response.read(contract['agent']['bytes'] + 1)
            require(len(data) == contract['agent']['bytes'] and sha(data) == contract['agent']['sha256'], 'Downloaded startup agent identity')
            agent.write_bytes(data)
            env = dict(os.environ)
            env.pop('KOTEST_INCLUDE_PATTERN', None)
            env.pop('UPDATE_SNAPSHOTS', None)
            env['JAVA_TOOL_OPTIONS'] = '-javaagent:' + str(agent)
            env['GRADLE_USER_HOME'] = str(runtime.parent / 'manual-host-requalification-gradle-home')
            env['GRADLE_LOCK_SLOTS'] = '1'
            env['GRADLE_OPTS'] = '-Dorg.gradle.daemon=false'
            gradle_home = Path(env['GRADLE_USER_HOME'])
            gradle_home.mkdir(exist_ok=False)
            (gradle_home / 'gradle.properties').write_text(contract['host_gradle_properties'])
            settings = {k: env.get(k) for k in ['JAVA_TOOL_OPTIONS', 'GRADLE_USER_HOME', 'GRADLE_LOCK_SLOTS', 'GRADLE_OPTS', 'KOTEST_INCLUDE_PATTERN', 'UPDATE_SNAPSHOTS', 'JAVA_HOME']}
            settings['gradle_properties'] = contract['host_gradle_properties']
            stamp(runtime / 'HOST_SETTINGS.json', settings)
            receipt['host_settings_sha256'] = sha((runtime / 'HOST_SETTINGS.json').read_bytes())
            release = {}
            for line in (Path(env['JAVA_HOME']) / 'release').read_text().splitlines():
                if '=' in line:
                    key, value = line.split('=', 1)
                    release[key] = value.strip('"')
            toolchain = dict(runner_environment=env.get('RUNNER_ENVIRONMENT'), runner_os=env.get('RUNNER_OS'),
                             runner_arch=env.get('RUNNER_ARCH'), runner_name=env.get('RUNNER_NAME'),
                             java_runtime_version=release.get('JAVA_RUNTIME_VERSION'), java_implementor=release.get('IMPLEMENTOR'),
                             java_executable=str((Path(env['JAVA_HOME']) / 'bin/java').resolve()),
                             agent=dict(path=str(agent), bytes=len(data), sha256=sha(data)),
                             docker_version_returncode=None, docker_info_returncode=None)
            for name, argv in [('java-version', ['java', '-version']), ('node-version', ['node', '--version']),
                               ('npm-version', ['npm', '--version']), ('docker-version', ['docker', 'version', '--format', '{{json .}}']),
                               ('docker-info', ['docker', 'info', '--format', '{{json .}}'])]:
                result = command(argv, repo, env, runtime / 'host' / name, contract['per_host_command_timeout_seconds'])
                stamp(runtime / 'host' / (name + '.json'), result)
                require(result['returncode'] == 0 and not result['timed_out'], 'Host readiness command failed: ' + name)
                if name in ('node-version', 'npm-version'):
                    toolchain[name.replace('-', '_')] = (runtime / 'host' / (name + '.stdout')).read_text().strip()
                if name in ('docker-version', 'docker-info'):
                    toolchain[name.replace('-', '_') + '_returncode'] = result['returncode']
            require(toolchain['java_runtime_version'] == contract['jdk_runtime_version'], 'JDK runtime version')
            require(toolchain['java_implementor'] == 'Eclipse Adoptium', 'JDK implementor')
            require(toolchain['node_version'] == contract['node_version'] and toolchain['npm_version'] == contract['npm_version'], 'Node/npm version')
            stamp(runtime / 'HOST_TOOLCHAIN.json', toolchain)
            receipt['toolchain_sha256'] = sha((runtime / 'HOST_TOOLCHAIN.json').read_bytes())
            receipt['gradle_invocations'] = 1
            stamp(runtime / 'HOSTED_READINESS.json', receipt)
            watcher = WorkerObserver(env)
            print('START HOSTED_READINESS: 11 actual MockK-class cases and 9 Docker/Flyway cases', flush=True)
            result = command(contract['gradle_argv'], repo, env, runtime / 'readiness' / 'gradle', contract['readiness_timeout_seconds'], watcher)
            result['host_settings_sha256'] = receipt['host_settings_sha256']
            stamp(runtime / 'readiness' / 'COMMAND.json', result)
            stamp(runtime / 'readiness' / 'TEST_WORKERS.json', list(watcher.rows.values()))
            receipt['gradle_returncode'] = result['returncode']
            actual_gradle_output = (runtime / 'readiness' / 'gradle.stdout').read_text(errors='replace')
            toolchain['gradle_version'] = contract['gradle_version'] if ('Welcome to Gradle ' + contract['gradle_version'] + '!') in actual_gradle_output else None
            stamp(runtime / 'HOST_TOOLCHAIN.json', toolchain)
            receipt['toolchain_sha256'] = sha((runtime / 'HOST_TOOLCHAIN.json').read_bytes())
            reports = repo / 'game-server/build/test-results/test'
            target = runtime / contract['readiness_xml_directory']
            target.mkdir(parents=True, exist_ok=True)
            for file in sorted(reports.glob('TEST-*.xml')):
                shutil.copyfile(file, target / file.name)
            after = source_state(repo, pins)
            expected_bindings_sha256 = sha(json.dumps(pins['bindings'], sort_keys=True, separators=(',', ':')).encode())
            stamp(runtime / 'readiness' / 'SOURCE_STATE.json', dict(before=before, after=after, tracked_clean_before=True,
                  tracked_clean_after=True, test_sources=contract['test_sources'], expected_bindings_sha256=expected_bindings_sha256))
            receipt.update(source_bindings_verified=len(after), source_restored=before == after,
                           expected_bindings_sha256=expected_bindings_sha256)
            parsed = parse_xml(runtime, contract)
            receipt.update(summary=parsed['summary'], xml_sha256=parsed['xml_sha256'], ended_utc=utc(), state='PASS')
            stamp(runtime / 'HOSTED_READINESS.json', receipt)
            # This is a provisional receipt until every exact raw check succeeds.
            proof = audit_readiness(runtime, contract, envelope)
            stamp(runtime / 'READINESS_AUDIT.json', proof)
            print('PASS HOSTED_READINESS: 20 exact identities, 20 passes, zero failures/errors/skips', flush=True)
            state.update(readiness_sequences_completed=1, readiness_ended_utc=receipt['ended_utc'], state='READINESS_PASS')
            stamp(runtime / 'HOSTED_RUN.json', state)
            # The unchanged qualifier re-verifies source and authority itself before gates.
            require(sha((gradle_home / 'gradle.properties').read_bytes()) == sha(contract['host_gradle_properties'].encode()), 'Host settings drift before qualifier')
            require(not (repo / contract['qualification_evidence_directory']).exists(), 'Qualification evidence already exists')
            qualifier_started = True
            state.update(qualification_starts=1, qualification_started_utc=utc(), state='QUALIFICATION_RUNNING',
                         qualification_host_settings_sha256=receipt['host_settings_sha256'])
            stamp(runtime / 'HOSTED_RUN.json', state)
            print('START HOSTED_QUALIFICATION: one unchanged frozen invocation', flush=True)
            qualification_result = command(['python3', str(repo / RECORD / 'qualify.py')], repo, env,
                                           runtime / 'qualification-driver', contract['qualification_timeout_seconds'], stream_stdout=True)
            stamp(runtime / 'QUALIFICATION_COMMAND.json', qualification_result)
            state.update(qualification_completed=1, qualification_ended_utc=qualification_result['ended_utc'],
                         qualification_returncode=qualification_result['returncode'])
            source_state(repo, pins)
            require(sha((gradle_home / 'gradle.properties').read_bytes()) == sha(contract['host_gradle_properties'].encode()), 'Host settings drift after qualifier')
            require(qualification_result['returncode'] == 0 and not qualification_result['timed_out'], 'Hosted qualification failed')
            raw_result = read(repo / contract['qualification_evidence_directory'] / 'RESULT.json')
            require(raw_result['state'] == 'ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED' and raw_result['admitted'] is False, 'Original qualifier result mismatch')
            state.update(state='HOSTED_QUALIFICATION_PASS_ORIGINAL_RECEIVING_REQUIRED', source_bindings_verified=378,
                         source_restored=True, original_raw_qualifier_state=raw_result['state'], local_pass=False)
    except BaseException:
        failure = traceback.format_exc()
        (runtime / 'HOSTED_FAILURE.txt').write_text(failure)
        if not qualifier_started:
            receipt.update(state='FAIL', ended_utc=utc())
            state.update(readiness_sequences_completed=1, readiness_ended_utc=receipt['ended_utc'],
                         state='READINESS_FAILED_ORIGINAL_RUNTIME_EVIDENCE_PRESERVED')
        else:
            state['state'] = 'QUALIFICATION_FAILED_ORIGINAL_RUNTIME_EVIDENCE_PRESERVED'
        stamp(runtime / 'HOSTED_READINESS.json', receipt)
        print(failure, file=sys.stderr)
    finally:
        try:
            restored = source_state(repo, pins)
            state.update(source_bindings_verified=len(restored), source_restored=True)
        except BaseException:
            state.update(source_restored=False, restoration_error=traceback.format_exc())
        state['ended_utc'] = utc()
        stamp(runtime / 'HOSTED_RUN.json', state)
    return 0 if state['state'] == 'HOSTED_QUALIFICATION_PASS_ORIGINAL_RECEIVING_REQUIRED' else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--repo', required=True)
    parser.add_argument('--runtime', required=True)
    args = parser.parse_args()
    return run_hosted(args.repo, args.runtime)


if __name__ == '__main__':
    raise SystemExit(main())
