#!/usr/bin/env python3
"""Bounded same-input factory differential. No experiment execution or policy admission."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import selectors
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(os.environ['GITHUB_WORKSPACE'])
CONTROL = ROOT / 'control'
OUT = ROOT / 'output'
CONTROL_PATHS = ['.github/workflows/izzet-legacy-factory-qualification.yml', 'qualify.py', 'gate.json']
SOURCES = {
    'before': ('af16191eba3c93efa40f4ab28c2454b4b3c23d88', 'e9aba19e87a05fd6844b536d394b143f8e8a4235'),
    'fixed': ('e825589e1deff9de09f4ffe78aece41dc5ac0df9', '52932b643bc05419435996655903044f798e9f3e'),
}

def dump(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')

def source_snapshot(gate, role, destination):
    root = ROOT / role
    result = {'role': role, 'head': None, 'tree': None, 'status': None,
              'authority_sha256': {}, 'test_source_git_blobs': {}, 'dependency_sha256': {},
              'production_sha256': None, 'errors': []}
    # Persist the attempt before any Git or file read, including rejected checkouts.
    dump(destination, result)
    operation = 'start'
    try:
        for key, args in [('head', ('rev-parse', 'HEAD')), ('tree', ('rev-parse', 'HEAD^{tree}')),
                          ('status', ('status', '--porcelain'))]:
            operation = 'git identity: ' + key
            result[key] = git(root, *args)
            dump(destination, result)
        operation = 'production: ' + gate['production_path']
        result['production_sha256'] = sha(root / gate['production_path'])
        dump(destination, result)
        for path in gate['preserved_authority_sha256']:
            operation = 'authority: ' + path
            result['authority_sha256'][path] = sha(root / path)
            dump(destination, result)
        for path in gate['dependency_sha256']:
            operation = 'dependency: ' + path
            result['dependency_sha256'][path] = sha(root / path)
            dump(destination, result)
        for bank in gate['banks']:
            path = bank['test_source']
            operation = 'test source: ' + path
            result['test_source_git_blobs'][path] = git(root, 'hash-object', path)
            dump(destination, result)
        operation = 'validate observed source'
        assert (result['head'], result['tree']) == SOURCES[role]
        assert result['head'] == gate['sources'][role]['head']
        assert result['tree'] == gate['sources'][role]['tree']
        assert not result['status'], 'Source checkout must be completely clean'
        assert result['authority_sha256'] == gate['preserved_authority_sha256']
        assert result['dependency_sha256'] == gate['dependency_sha256']
        assert result['production_sha256'] == gate['sources'][role]['production_sha256']
        for bank in gate['banks']:
            assert result['test_source_git_blobs'][bank['test_source']] == bank['test_source_git_blob']
    except Exception as error:
        result['errors'].append({'operation': operation,
            'kind': 'validation_error' if operation == 'validate observed source' else 'read_error',
            'error': type(error).__name__ + ': ' + str(error)})
        dump(destination, result)
        raise
    return result

def observation_map(output, marker):
    matches = re.findall(r'^' + re.escape(marker) + r' (\{[^\r\n]+\})$', output, re.M)
    assert len(matches) == 1, 'Missing or repeated complete observation map: ' + marker
    result = {}
    for item in matches[0][1:-1].split(', '):
        pair = item.split('=')
        assert len(pair) == 2 and pair[0] not in result and pair[1] in ('true', 'false')
        result[pair[0]] = pair[1] == 'true'
    return result

def collect_xml(path, bank, started, finished):
    raw = path.read_bytes()
    suite = ET.fromstring(raw)
    cases = suite.findall('testcase')
    names = [case.attrib['name'] for case in cases]
    assert suite.tag == 'testsuite' and suite.attrib['name'] == bank['class']
    assert len(cases) == int(suite.attrib['tests']) == bank['expected_cases']
    assert len(names) == len(set(names)) and sorted(names) == sorted(bank['case_names'])
    assert all(case.attrib.get('classname') == bank['class'] for case in cases)
    assert not any(suite.findall('.//' + kind) for kind in ('error', 'skipped'))
    assert int(suite.attrib.get('errors', 0)) == int(suite.attrib.get('skipped', 0)) == 0
    actual_failures = []
    failures = []
    for case in cases:
        entries = case.findall('failure')
        assert len(entries) <= 1
        if entries:
            actual_failures.append(case.attrib['name'])
            failures.append({'case': case.attrib['name'], 'attributes': entries[0].attrib,
                             'text': entries[0].text or ''})
    assert sorted(actual_failures) == sorted(bank['expected_failure_names'])
    assert int(suite.attrib.get('failures', 0)) == len(suite.findall('.//failure')) == len(actual_failures)
    assert started - 2_000_000_000 <= path.stat().st_mtime_ns <= finished + 2_000_000_000
    observations = {}
    if bank['requires_observation_maps']:
        output = '\n'.join(node.text or '' for node in suite.findall('.//system-out'))
        observed = observation_map(output, 'IZZET_LEGACY_FACTORY_DIFFERENTIAL')
        fixed = bank['source_role'] == 'fixed'
        expected = {'explicitLegacy': True, 'explicitCompleted': False,
                    'renamedCompanion': False, 'renamedReusable': False,
                    'frozenCompanion': fixed, 'frozenReusable': fixed,
                    'equalCopyCompanion': fixed, 'equalCopyReusable': fixed}
        assert observed == expected, 'Same-input strategy differential was not demonstrated'
        same_id = observation_map(output, 'IZZET_LEGACY_FACTORY_SAME_ID_CONTROL')
        assert same_id == {'explicitLegacy': True, 'explicitCompleted': False,
                           'alteredCompanion': False, 'alteredReusable': False}
        observations = {'differential': observed, 'same_id_control': same_id}
    if actual_failures:
        assert bank['source_role'] == 'before' and len(failures) == 1
        failure = failures[0]
        diagnostic = failure['attributes'].get('message', '') + '\n' + failure['text']
        assert 'FROZEN_FACTORY_LEGACY_BINDING' in diagnostic
        assert failure['attributes'].get('type', '').endswith('AssertionFailedError')
        assert 'EXPLICIT_STRATEGY_DIFFERENTIAL' not in diagnostic
        assert 'STATE_UNCHANGED_AFTER_' not in diagnostic
    return {'class': bank['class'], 'actual_cases': len(cases), 'case_names': names,
            'passed': len(cases) - len(failures), 'failed': len(failures), 'errors': 0, 'skipped': 0,
            'declared_expected_failures': failures, 'observations': observations,
            'xml_sha256': hashlib.sha256(raw).hexdigest()}

def prepare_build_semaphore(gate):
    """Use the packaged upstream shlock; never install services or replace locking semantics."""
    cfg = gate['build_semaphore']
    folder = OUT / 'build-semaphore'
    folder.mkdir()
    tool = ROOT / 'shlock-prerequisite'
    tool.mkdir(exist_ok=False)
    packages = folder / 'packages'
    packages.mkdir()
    reports = tool / 'no-test-results'
    lists = tool / 'apt-lists'
    (lists / 'partial').mkdir(parents=True)
    cache = tool / 'apt-cache'
    cache.mkdir()
    apt_options = ['-o', 'Dir::State::Lists=' + str(lists), '-o', 'Dir::Cache=' + str(cache),
                   '-o', 'APT::Sandbox::User=', '-o', 'Acquire::Retries=0']
    record = {'status': 'INCOMPLETE_BEFORE_ANY_JVM', 'commands': [], 'packages': [],
              'utility_checks': [], 'errors': [], 'official_games': 0}
    def save():dump(folder / 'prerequisite.json', record)
    def small(command):
        row = {'command': command, 'started_ns': time.time_ns()}
        record['commands'].append(row);save()
        log = folder / ('metadata-' + str(len(record['commands'])) + '.log')
        limits = dict(gate['resource_limits'], command_timeout_seconds=15, stdout_bytes=65536)
        row.update(run_capped(command, tool, log, reports, OUT, limits))
        row.update({'stdout': log.read_text(errors='replace'), 'finished_ns': time.time_ns()})
        save()
        assert row['output_complete'] and row['exit_status'] == 0, 'Prerequisite metadata command failed'
        return row['stdout']
    def capped(label, command, cwd):
        row = {'command': command, 'started_ns': time.time_ns()}
        record['commands'].append(row);save()
        limits = dict(gate['resource_limits'], command_timeout_seconds=cfg['setup_command_timeout_seconds'], stdout_bytes=4194304)
        row.update(run_capped(command, cwd, folder / (label + '.log'), reports, OUT, limits))
        row['finished_ns'] = time.time_ns();save()
        assert row['output_complete'] and row['exit_status'] == 0, 'Prerequisite setup failed; no JVM admitted'
    save()
    try:
        os_release = Path('/etc/os-release').read_text()
        (folder / 'os-release.txt').write_text(os_release)
        assert '\nID=ubuntu\n' in '\n' + os_release
        assert '\nVERSION_ID="24.04"\n' in '\n' + os_release
        assert small(['dpkg', '--print-architecture']).strip() == cfg['architecture'] == 'amd64'
        assert os.environ.get('GRADLE_LOCK_SLOTS') == str(cfg['lock_slots']) == '1'
        assert 'GRADLE_LOCK_FILE' not in os.environ, 'Preserve the existing machine-global default lock'
        # Authenticated APT metadata and package extraction remain entirely in owned directories.
        # No root process, package installation, maintainer script or service is started.
        capped('apt-update', ['apt-get', *apt_options, 'update'], tool)
        for package in cfg['packages']:
            spec = package + '=' + cfg['package_version']
            metadata = small(['apt-cache', *apt_options, 'show', spec])
            (folder / (package + '-apt-metadata.txt')).write_text(metadata)
            stanzas = [dict(line.split(': ', 1) for line in part.splitlines() if ': ' in line and not line.startswith(' '))
                       for part in metadata.strip().split('\n\n')]
            chosen = [s for s in stanzas if s.get('Package') == package and s.get('Version') == cfg['package_version']
                      and s.get('Architecture') == cfg['architecture']]
            assert len(chosen) == 1
            meta = chosen[0]
            assert meta['Filename'].startswith(cfg['package_filename_prefix'])
            assert int(meta['Size']) <= cfg['package_max_bytes']
            before = set(packages.glob('*.deb'))
            capped('download-' + package, ['apt-get', *apt_options, 'download', spec], packages)
            added = set(packages.glob('*.deb')) - before
            assert len(added) == 1
            deb = added.pop()
            actual = {'package': package, 'version': cfg['package_version'], 'file': deb.name,
                      'bytes': deb.stat().st_size, 'sha256': sha(deb), 'apt_metadata': meta}
            record['packages'].append(actual);save()
            assert actual['sha256'] == meta['SHA256'] and actual['bytes'] == int(meta['Size'])
            for field, expected in [('Package', package), ('Version', cfg['package_version']), ('Architecture', cfg['architecture'])]:
                assert small(['dpkg-deb', '--field', str(deb), field]).strip() == expected
            small(['dpkg-deb', '--extract', str(deb), str(tool / 'extracted')])
        binary = tool / 'extracted' / cfg['upstream_binary']
        libraries = tool / 'extracted' / cfg['upstream_library_directory']
        assert binary.is_file() and os.access(binary, os.X_OK)
        record['binary'] = {'path': str(binary), 'bytes': binary.stat().st_size, 'sha256': sha(binary)}
        record['libraries'] = {str(p.relative_to(tool)): {'bytes': p.stat().st_size, 'sha256': sha(p)}
                               for p in sorted(libraries.glob('libinn*.so*')) if p.is_file()}
        assert record['libraries']
        owned_bin = tool / 'bin'
        owned_bin.mkdir()
        (owned_bin / 'shlock').symlink_to(binary)
        os.environ['PATH'] = str(owned_bin) + os.pathsep + os.environ['PATH']
        os.environ['LD_LIBRARY_PATH'] = str(libraries) + (os.pathsep + os.environ['LD_LIBRARY_PATH'] if os.environ.get('LD_LIBRARY_PATH') else '')
        assert Path(shutil.which('shlock')).resolve() == binary.resolve()
        record['resolved_command'] = shutil.which('shlock')
        record['default_lock'] = str(Path.home() / '.cache/argentum/gradle.lock')
        save()
        lock = tool / 'owned-prerequisite-test.lock'
        assert not lock.exists()
        def probe(label, expected):
            result = subprocess.run(['shlock', '-f', str(lock), '-p', str(os.getpid())], capture_output=True, timeout=10)
            row = {'label': label, 'exit_status': result.returncode, 'stdout': result.stdout.decode(errors='replace'),
                   'stderr': result.stderr.decode(errors='replace'), 'lock_content': lock.read_text() if lock.exists() else None}
            record['utility_checks'].append(row);save()
            assert len(result.stdout) + len(result.stderr) <= 65536
            assert (result.returncode == 0) == expected
            assert row['lock_content'].strip() == str(os.getpid())
        try:
            probe('acquire owned test lock', True)
            probe('reject already live holder without overwrite', False)
            assert lock.read_text().strip() == str(os.getpid());lock.unlink()
            probe('reacquire after explicit owned release', True)
        finally:
            if lock.exists():
                assert lock.read_text().strip() == str(os.getpid()), 'Never remove a foreign lock'
                lock.unlink()
        record['status'] = 'UPSTREAM_SHLOCK_PREREQUISITE_QUALIFIED_BEFORE_JVM'
        save()
        return {'binary_path': str(binary), 'binary_sha256': sha(binary), 'lock_path': record['default_lock']}
    except Exception as error:
        record['errors'].append(type(error).__name__ + ': ' + str(error));save()
        raise

def run_bank(gate, bank, index, semaphore):
    root = ROOT / bank['source_role']
    limits = gate['resource_limits']
    folder = OUT / f"{index:02d}-{bank['stage']}"
    folder.mkdir()
    before = source_snapshot(gate, bank['source_role'], folder / 'source-before.json')
    xml_dir = root / bank['module'] / 'build/test-results/test'
    # Only generated reports inside these two newly created owned checkouts are removed.
    for path in xml_dir.glob('TEST-*.xml'):
        path.unlink()
    assert not list(xml_dir.glob('TEST-*.xml'))
    command = ['just', 'test-class', bank['class'].rsplit('.', 1)[1], '--rerun', '--no-build-cache',
               '--no-daemon', '-DupdateSnapshots=false', '--info', '--stacktrace', '--max-workers=1',
               '-PkotlinCompileParallelism=1', '-Pkotlin.compiler.execution.strategy=in-process',
               '-Dorg.gradle.jvmargs=-Xmx4g']
    row = {'stage': bank['stage'], 'source_role': bank['source_role'], 'class': bank['class'],
           'expected_cases': bank['expected_cases'], 'expected_failure_names': bank['expected_failure_names'],
           'command': command, 'status': 'INCOMPLETE', 'started_ns': time.time_ns()}
    dump(folder / 'command.json', row)
    try:
        assert sha(Path(semaphore['binary_path'])) == semaphore['binary_sha256']
        assert Path(shutil.which('shlock')).resolve() == Path(semaphore['binary_path']).resolve()
        row.update(run_capped(command, root, folder / 'command.log', xml_dir, OUT, limits, semaphore['lock_path']))
        row['finished_ns'] = time.time_ns()
        (folder / 'exit-status.txt').write_text(str(row['exit_status']) + '\n')
        row['observed_report_bytes_after_exit'] = directory_bytes(xml_dir)
        assert row['process_group_cleanup']['quiescent'], 'Owned process group still live; no report bytes copied'
        if row['observed_report_bytes_after_exit'] > limits['test_result_bytes']:
            row['technical_limit'] = 'TEST_RESULT_BYTE_LIMIT'
            row['output_complete'] = False
        paths = sorted(xml_dir.glob('TEST-*.xml'))
        row['xml_retention'] = []
        for path in paths:
            size = path.stat().st_size
            allowed = max(0, min(limits['test_result_bytes'], limits['artifact_bytes'] - directory_bytes(OUT) - 4194304))
            with path.open('rb') as source:
                raw = source.read(min(size, allowed))
            complete = len(raw) == size
            (folder / (path.name if complete else path.name + '.partial')).write_bytes(raw)
            row['xml_retention'].append({'name': path.name, 'observed_bytes': size,
                'retained_bytes': len(raw), 'complete': complete, 'sha256': hashlib.sha256(raw).hexdigest()})
        if not all(item['complete'] for item in row['xml_retention']):
            row['technical_limit'] = 'INCOMPLETE_XML_BYTE_LIMIT'
            row['output_complete'] = False
        assert row['output_complete'], 'Technical resource stop; prefixes remain incomplete evidence'
        assert row['exit_status'] == (1 if bank['expected_failure_names'] else 0), 'Unexpected command exit'
        assert len(paths) == 1 and paths[0].name == 'TEST-' + bank['class'] + '.xml'
        row['actual'] = collect_xml(paths[0], bank, row['started_ns'], row['finished_ns'])
        task = ':' + bank['module'].replace('/', ':') + ':test'
        log = (folder / 'command.log').read_text()
        lines = log.splitlines()
        assert 'running ./gradlew unlocked' not in log, 'Unlocked fallback is not qualified'
        assert row['owned_semaphore_observations'], 'No actual owned wrapper lock observation'
        assert any(line.strip() == '> Task ' + task or line.strip() == '> Task ' + task + ' FAILED' for line in lines)
        assert not any(line.strip() == '> Task ' + task + ' ' + suffix
                       for line in lines for suffix in ('FROM-CACHE', 'UP-TO-DATE', 'NO-SOURCE', 'SKIPPED'))
        assert len(re.findall(r'^Gradle Test Executor \d+ started executing tests\.$', log, re.M)) == 1
        assert len(re.findall(r'^Gradle Test Executor \d+ finished executing tests\.$', log, re.M)) == 1
        assert before == source_snapshot(gate, bank['source_role'], folder / 'source-after.json')
        row['status'] = 'PASS_DECLARED_BEHAVIOR_RAW_FAILURES_RETAINED'
    except Exception as error:
        row['status'] = 'FAILED_PRESERVED_FOR_REVIEW'
        row['error'] = type(error).__name__ + ': ' + str(error)
    finally:
        dump(folder / 'command.json', row)
    return row

def main():
    OUT.mkdir(exist_ok=False)
    audit = {'schema': 'izzet-factory-differential-audit/v1', 'status': 'INCOMPLETE',
             'control_files_sha256': {}, 'control_head': None, 'source_before': {}, 'source_after': {},
             'run_id': os.environ.get('GITHUB_RUN_ID'), 'attempt': os.environ.get('GITHUB_RUN_ATTEMPT'),
             'event': os.environ.get('GITHUB_EVENT_NAME'), 'stages': [], 'errors': [],
             'new_software_identities': 2, 'new_pilot_cases': 0, 'official_games': 0, 'official_seeds': 0,
             'gameplay_authorized': False, 'full_runtime_accepted': False}
    dump(OUT / 'audit.json', audit)
    try:
        for path in CONTROL_PATHS:
            target = OUT / 'control' / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(CONTROL / path, target)
            audit['control_files_sha256'][path] = sha(target)
            dump(OUT / 'audit.json', audit)
        gate = json.loads((CONTROL / 'gate.json').read_text())
        audit['control_head'] = git(CONTROL, 'rev-parse', 'HEAD')
        assert os.environ['GITHUB_REPOSITORY'] == 'GodaPupa/argentum-batshit-test'
        assert os.environ['GITHUB_RUN_ATTEMPT'] == '1' and os.environ['GITHUB_EVENT_NAME'] == 'push'
        assert audit['control_head'] == os.environ['GITHUB_SHA']
        assert not git(CONTROL, 'status', '--porcelain')
        assert sorted(git(CONTROL, 'ls-files').splitlines()) == sorted(CONTROL_PATHS)
        assert gate['ready_for_execution'] is True and gate['source_review']
        assert os.environ['GITHUB_REF'] == 'refs/heads/lab/izzet-legacy-factory-qualification-20260927'
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        assert event['before'] == '0' * 40 and event['created'] is True and event['after'] == audit['control_head']
        audit['activation_event'] = {key: event[key] for key in ['before', 'after', 'created']}
        assert shutil.disk_usage(ROOT).free >= gate['resource_limits']['minimum_free_bytes']
        ids = [(bank['class'], name) for bank in gate['banks'] for name in bank['case_names']]
        assert len(gate['banks']) == 3 and len(ids) == 11 and len(set(ids)) == 9
        assert [b['source_role'] for b in gate['banks']] == ['before', 'fixed', 'fixed']
        assert [len(b['expected_failure_names']) for b in gate['banks']] == [1, 0, 0]
        audit['build_semaphore'] = prepare_build_semaphore(gate)
        dump(OUT / 'audit.json', audit)
        for role in SOURCES:
            audit['source_before'][role] = source_snapshot(gate, role, OUT / ('source-before-' + role + '.json'))
            dump(OUT / 'audit.json', audit)
        for index, bank in enumerate(gate['banks'], 1):
            audit['current_stage'] = bank['stage']
            dump(OUT / 'audit.json', audit)
            audit['stages'].append(run_bank(gate, bank, index, audit['build_semaphore']))
            dump(OUT / 'audit.json', audit)
            if audit['stages'][-1]['status'] != 'PASS_DECLARED_BEHAVIOR_RAW_FAILURES_RETAINED':
                audit['unattempted_stages'] = [rest['stage'] for rest in gate['banks'][index:]]
                break
        for role in SOURCES:
            audit['source_after'][role] = source_snapshot(gate, role, OUT / ('source-after-' + role + '.json'))
            dump(OUT / 'audit.json', audit)
        assert audit['source_before'] == audit['source_after']
        assert audit['control_files_sha256'] == {p: sha(CONTROL / p) for p in CONTROL_PATHS}
        assert len(audit['stages']) == 3 and all(row['status'] == 'PASS_DECLARED_BEHAVIOR_RAW_FAILURES_RETAINED' for row in audit['stages'])
        audit['observed_software_results'] = {'case_executions': sum(r['actual']['actual_cases'] for r in audit['stages']),
            'passed': sum(r['actual']['passed'] for r in audit['stages']),
            'failed': sum(r['actual']['failed'] for r in audit['stages']),
            'errors': 0, 'skipped': 0, 'distinct_identities': 9}
        assert audit['observed_software_results'] == {'case_executions': 11, 'passed': 10, 'failed': 1, 'errors': 0, 'skipped': 0, 'distinct_identities': 9}
        audit['status'] = 'DECLARED_DIFFERENTIAL_OBSERVED_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW'
    except Exception as error:
        audit['errors'].append(type(error).__name__ + ': ' + str(error))
        raise
    finally:
        dump(OUT / 'audit.json', audit)

# Bounded-process helpers retain the reviewed corrections and their separate history.


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def directory_bytes(path):
    return sum(p.stat().st_size for p in path.rglob('*') if p.is_file()) if path.exists() else 0


def owned_group_members(group_id):
    """Observe the owned group; unknown candidate identity is not process absence."""
    result = {'members': [], 'complete': False, 'errors': [],
              'excluded': {'nonmatching_group_or_session': 0, 'different_namespace': 0, 'vanished': 0}}
    operation = 'read current PID namespace'
    try:
        namespace = os.readlink('/proc/self/ns/pid')
        operation = 'enumerate /proc'
        entries = list(Path('/proc').iterdir())
    except Exception as error:
        result['errors'].append({'operation': operation, 'error': type(error).__name__ + ': ' + str(error)})
        return result
    for path in entries:
        if not path.name.isdigit():
            continue
        operation = 'read group and session identifiers'
        try:
            # Status is normally readable even where a foreign user's namespace symlink is not.
            # Any member of our own namespace must have these innermost identifiers.
            status = dict(line.split(':', 1) for line in (path / 'status').read_text().splitlines() if ':' in line)
            pgrp = int(status['NSpgid'].split()[-1])
            session = int(status['NSsid'].split()[-1])
            if pgrp != group_id or session != group_id:
                result['excluded']['nonmatching_group_or_session'] += 1
                continue
            operation = 'read candidate PID namespace'
            if os.readlink(path / 'ns/pid') != namespace:
                result['excluded']['different_namespace'] += 1
                continue
            operation = 'read candidate process identity'
            pid = int(status['NSpid'].split()[-1])
            fields = (path / 'stat').read_text().rsplit(')', 1)[1].split()
            result['members'].append({'pid': pid, 'proc_pid': int(path.name), 'state': fields[0],
                'group': pgrp, 'session': session, 'start_ticks': int(fields[19]), 'pid_namespace': namespace})
        except (FileNotFoundError, ProcessLookupError):
            result['excluded']['vanished'] += 1
        except Exception as error:
            result['errors'].append({'proc_entry': path.name, 'operation': operation,
                                    'error': type(error).__name__ + ': ' + str(error)})
    result['members'].sort(key=lambda row: row['pid'])
    result['complete'] = not result['errors']
    return result

def terminate_owned_group(process, destination=None):
    # Persist an initial record before observations. Unknown visibility cannot qualify XML.
    record = {'group': process.pid, 'before': [], 'after': [], 'signals': [], 'errors': [],
              'observation_complete': True, 'scans': 0, 'quiescent': False}
    def save():
        if destination is not None:
            dump(destination, record)
    def observe(label):
        observation = owned_group_members(process.pid)
        record['scans'] += 1
        record[label + '_observation'] = observation
        record['observation_complete'] = record['observation_complete'] and observation['complete']
        for error in observation['errors']:
            if error not in record['errors']:
                record['errors'].append(error)
        save()
        return observation
    def live(rows):return [row for row in rows if row['state'] not in ('Z', 'X')]
    save()
    initial = observe('before')
    record['before'] = initial['members']
    save()
    for sig in (signal.SIGTERM, signal.SIGKILL):
        current = observe('latest')
        if not current['complete'] or not live(current['members']):
            break
        try:
            os.killpg(process.pid, sig)
            record['signals'].append(signal.Signals(sig).name)
        except ProcessLookupError:
            pass
        except Exception as error:
            record['errors'].append({'operation': 'signal owned group',
                                    'error': type(error).__name__ + ': ' + str(error)})
        save()
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            process.poll()
            current = observe('latest')
            if not current['complete'] or not live(current['members']):
                break
            time.sleep(0.05)
    try:
        process.wait(timeout=1)
    except Exception as error:
        record['errors'].append({'operation': 'wait for owned wrapper',
                                'error': type(error).__name__ + ': ' + str(error)})
    final = observe('after')
    record['after'] = final['members']
    record['quiescent'] = (record['observation_complete'] and not record['errors']
                           and not live(record['after']) and process.poll() is not None)
    save()
    return record



def run_capped(command, root, log_path, reports, out, limits, semaphore_lock=None):
    start = time.monotonic()
    reason = None
    written = 0
    discarded_observed_bytes = 0
    owned_semaphore_observations = []
    process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    try:
        with log_path.open('wb') as log:
            while selector.get_map():
                if semaphore_lock:
                    try:
                        text = Path(semaphore_lock).read_text().strip()
                        if text.isdigit() and len(text) <= 16:
                            holder = int(text)
                            if os.getpgid(holder) == process.pid and holder not in [r['holder_pid'] for r in owned_semaphore_observations]:
                                owned_semaphore_observations.append({'holder_pid': holder, 'owned_process_group': process.pid, 'observed_ns': time.time_ns()})
                    except (FileNotFoundError, ProcessLookupError, PermissionError):
                        pass
                if time.monotonic() - start > limits['command_timeout_seconds']:
                    reason = 'COMMAND_TIMEOUT'; break
                if directory_bytes(reports) > limits['test_result_bytes']:
                    reason = 'TEST_RESULT_BYTE_LIMIT'; break
                if directory_bytes(out) >= limits['artifact_bytes'] - 4194304:
                    reason = 'ARTIFACT_BYTE_LIMIT'; break
                for key, _ in selector.select(timeout=0.1):
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    remaining = min(limits['stdout_bytes'] - written, limits['artifact_bytes'] - directory_bytes(out) - 4194304)
                    keep = chunk[:max(0, remaining)]
                    log.write(keep); log.flush(); written += len(keep)
                    if len(keep) < len(chunk):
                        discarded_observed_bytes += len(chunk) - len(keep)
                        reason = 'STDOUT_OR_ARTIFACT_BYTE_LIMIT'; break
                if reason:
                    break
            if not reason:
                try:
                    process.wait(timeout=max(0.01, limits['command_timeout_seconds'] - (time.monotonic() - start)))
                except subprocess.TimeoutExpired:
                    reason = 'COMMAND_TIMEOUT'
    finally:
        # Parent exit is insufficient: a child can close stdout yet keep writing XML.
        normal_wrapper_exit = reason is None and process.poll() is not None
        cleanup = terminate_owned_group(process, log_path.with_name(log_path.name + ".process-cleanup.json"))
        if normal_wrapper_exit and any(row['state'] not in ('Z', 'X') for row in cleanup['before']):
            reason = 'OWNED_DESCENDANTS_AFTER_WRAPPER_EXIT'
        if not cleanup['quiescent']:
            reason = ('OWNED_PROCESS_OBSERVATION_INCOMPLETE' if not cleanup['observation_complete']
                      else 'OWNED_PROCESS_GROUP_NOT_QUIESCENT')
        selector.close()
        process.stdout.close()
    return {'exit_status': process.returncode, 'technical_limit': reason, 'stdout_retained_bytes': written,
            'discarded_observed_pipe_bytes': discarded_observed_bytes, 'output_complete': reason is None,
            'owned_semaphore_observations': owned_semaphore_observations,
            'process_group_cleanup': cleanup,
            'elapsed_seconds': time.monotonic() - start}


if __name__ == "__main__":
    main()
