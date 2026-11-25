#!/usr/bin/env python3
"""Prepared only. Root may run once after original qualification preservation.

One javac invocation and three standalone host JVMs; no repository execution,
Gradle, MockK, test framework, package download, or host-property mutation.
"""
from __future__ import annotations

import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import time
import urllib.parse

HERE = Path(__file__).resolve().parent
HOST = Path('/workspace/scratch/65bf30f1888a/manual-lint-host')
JDK = HOST / 'jdk/jdk-21.0.13+9'
AGENT = HOST / 'gradle-user-home/caches/modules-2/files-2.1/net.bytebuddy/byte-buddy-agent/1.18.10/9426d28828bdcdf42666bb7a68c468279ea78f59/byte-buddy-agent-1.18.10.jar'
TRUST = HOST / 'java-platform-cacerts'
LOCK = Path.home() / '.cache/argentum/manual-transmission.lock'
TIMEOUT_SECONDS = 30
GRACE_SECONDS = 2


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat().replace('+00:00', 'Z')


def digest(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return {'bytes': Path(path).stat().st_size, 'sha256': h.hexdigest()}


def save(path, value):
    Path(path).write_text(json.dumps(value, indent=2, sort_keys=True) + '\n')


def verify_inputs():
    pins = json.loads((HERE / 'HOST_INPUTS.json').read_text())
    for path, pin in pins['files'].items():
        if digest(path) != pin:
            raise RuntimeError('Pinned host input changed: ' + path)
    return pins


def safe_current_options():
    """Reproduce observed properties with this invocation's proxy, without writes.

    Never reuse the failed run's port. Unexpected JVM option injections are
    rejected without printing their content; no policy/security option is removed.
    """
    for name in ('JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        if os.environ.get(name, '').strip():
            raise RuntimeError(name + ' is unexpectedly set; review before execution')
    owned = {
        'org.gradle.daemon', 'javax.net.ssl.trustStore', 'http.nonProxyHosts',
        'http.proxyHost', 'http.proxyPort', 'https.proxyHost', 'https.proxyPort',
    }
    inherited = os.environ.get('JAVA_TOOL_OPTIONS', '')
    for option in shlex.split(inherited):
        if not option.startswith('-D') or option[2:].split('=', 1)[0] not in owned:
            raise RuntimeError('Unexpected JAVA_TOOL_OPTIONS entry; review before execution')
    properties = [
        '-Dorg.gradle.daemon=false',
        '-Djavax.net.ssl.trustStore=' + str(TRUST),
        '-Dhttp.nonProxyHosts=localhost|127.*|[::1]',
    ]
    proxy_properties = []
    proxy_observations = {}
    for protocol in ('http', 'https'):
        raw = os.environ.get(protocol + '_proxy') or os.environ.get(protocol.upper() + '_PROXY')
        if not raw:
            raise RuntimeError('Invocation-current ' + protocol + ' proxy missing')
        proxy = urllib.parse.urlsplit(raw)
        if proxy.username is not None or proxy.password is not None:
            raise RuntimeError('Credential-bearing proxy refused without printing it')
        if proxy.scheme != 'http' or proxy.hostname not in ('127.0.0.1', 'localhost') or not proxy.port:
            raise RuntimeError('Unexpected proxy shape; review before execution')
        proxy_observations[protocol] = {'host': proxy.hostname, 'port': proxy.port}
        proxy_properties += ['-D' + protocol + '.proxyHost=' + proxy.hostname,
                             '-D' + protocol + '.proxyPort=' + str(proxy.port)]
    return properties, proxy_properties, proxy_observations


def live_group_members(group_id):
    """Inspect only PID/group/session/state columns, never command/environment."""
    found = []
    for path in Path('/proc').glob('[0-9]*/stat'):
        try:
            raw = path.read_text()
            fields = raw[raw.rfind(')') + 2:].split()
            state, group, session = fields[0], int(fields[2]), int(fields[3])
            if group == group_id and session == group_id and state != 'Z':
                found.append(int(path.parent.name))
        except (FileNotFoundError, ProcessLookupError, PermissionError, ValueError, IndexError):
            pass
    return sorted(found)


def clean_owned_group(group_id):
    initial = live_group_members(group_id)
    if initial:
        try:
            os.killpg(group_id, signal.SIGTERM)
        except ProcessLookupError:
            pass
        deadline = time.monotonic() + GRACE_SECONDS
        while live_group_members(group_id) and time.monotonic() < deadline:
            time.sleep(0.05)
        if live_group_members(group_id):
            try:
                os.killpg(group_id, signal.SIGKILL)
            except ProcessLookupError:
                pass
            deadline = time.monotonic() + GRACE_SECONDS
            while live_group_members(group_id) and time.monotonic() < deadline:
                time.sleep(0.05)
    return {'live_members_before_cleanup': initial, 'live_members_after_cleanup': live_group_members(group_id)}


def run_process(label, argv, env, directory):
    directory.mkdir()
    record = {'label': label, 'argv': argv, 'cwd': str(directory),
              'java_tool_options': env['JAVA_TOOL_OPTIONS'], 'started_utc': utc(),
              'timeout_seconds': TIMEOUT_SECONDS, 'stdout': 'stdout.log', 'stderr': 'stderr.log'}
    save(directory / 'COMMAND.json', record)
    process = None
    began = time.monotonic()
    try:
        with (directory / 'stdout.log').open('wb') as stdout, (directory / 'stderr.log').open('wb') as stderr:
            process = subprocess.Popen(argv, cwd=directory, env=env, stdout=stdout, stderr=stderr,
                                       start_new_session=True)
            record['pid'] = process.pid
            record['owned_process_group'] = process.pid
            save(directory / 'COMMAND.json', record)
            try:
                record['returncode'] = process.wait(timeout=TIMEOUT_SECONDS)
                record['timed_out'] = False
            except subprocess.TimeoutExpired:
                record['timed_out'] = True
                record['cleanup'] = clean_owned_group(process.pid)
                try:
                    record['returncode'] = process.wait(timeout=GRACE_SECONDS)
                except subprocess.TimeoutExpired:
                    record['returncode'] = None
    finally:
        if process is not None:
            cleanup = clean_owned_group(process.pid)
            if 'cleanup' in record:
                record['cleanup_after_wait'] = cleanup
            else:
                record['cleanup'] = cleanup
            record['no_live_owned_helpers_remain'] = not cleanup['live_members_after_cleanup']
        record['elapsed_seconds'] = round(time.monotonic() - began, 6)
        record['terminal_utc'] = utc()
        for prefix in ('PROBE_START ', 'PROBE_RESULT '):
            path = directory / 'stdout.log'
            for line in path.read_text(errors='replace').splitlines() if path.exists() else []:
                if line.startswith(prefix):
                    record[prefix.strip().lower()] = json.loads(line[len(prefix):])
        save(directory / 'RESULT.json', record)
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true', required=True)
    parser.add_argument('--preservation-commit', required=True)
    parser.add_argument('--preservation-receipt', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.preservation_commit):
        parser.error('An actual immutable original-preservation commit is required')
    if not args.preservation_receipt.is_file():
        parser.error('Original preservation receipt must already exist')
    out = args.out.resolve()
    scratch = Path('/workspace/scratch/65bf30f1888a').resolve()
    if out.parent != scratch or not out.name.startswith('manual-mockk-host-probe-') or out.exists():
        parser.error('Use a new direct scratch directory named manual-mockk-host-probe-*')
    # Nonblocking exclusive lock: an active qualification causes refusal, not waiting.
    with LOCK.open('rb') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        inputs = verify_inputs()
        common, proxies, proxy_observations = safe_current_options()
        env = os.environ.copy()
        env['JAVA_HOME'] = str(JDK)
        env['JAVA_TOOL_OPTIONS'] = shlex.join(common + proxies)
        properties = HOST / 'gradle-user-home/gradle.properties'
        properties_before = digest(properties)
        out.mkdir()
        shutil.copyfile(HERE / 'AttachProbe.java', out / 'AttachProbe.java')
        classes = out / 'classes'
        classes.mkdir()
        receipt = {
            'schema': 'manual-transmission/standalone-attach-host-probe/v1',
            'state': 'RUNNING', 'started_utc': utc(), 'owner_pid': os.getpid(),
            'original_preservation_commit': args.preservation_commit,
            'original_preservation_receipt': {'path': str(args.preservation_receipt.resolve()), **digest(args.preservation_receipt)},
            'scope': 'standalone host diagnostic; no candidate behavior or qualification',
            'gradle_invocations': 0, 'repository_test_invocations': 0, 'qualification_invocations': 0,
            'network_requests': 0, 'jvm_process_limit': 'one javac plus three probe JVMs and their own Byte Buddy helpers',
            'proxy_observations_current_invocation': proxy_observations,
            'host_inputs': inputs, 'host_gradle_properties_before': properties_before,
            'driver': digest(Path(__file__)), 'java_source': digest(out / 'AttachProbe.java'),
            'commands': [],
        }
        save(out / 'RECEIPT.json', receipt)
        try:
            compiled = run_process('compile_standalone_probe', [str(JDK / 'bin/javac'), '-cp', str(AGENT),
                                   '-d', str(classes), str(out / 'AttachProbe.java')], env, out / 'compile')
            receipt['commands'].append(compiled)
            if compiled.get('returncode') != 0 or not compiled.get('no_live_owned_helpers_remain'):
                raise RuntimeError('Standalone probe compilation failed or left a live child; cases not started')
            for label, mode, remove_proxies in (
                ('01_original_dynamic', 'dynamic', False),
                ('02_dynamic_without_proxy_properties', 'dynamic', True),
                ('03_startup_agent_original_properties', 'startup', False),
            ):
                case_env = env.copy()
                case_env['JAVA_TOOL_OPTIONS'] = shlex.join(common if remove_proxies else common + proxies)
                directory = out / label
                argv = [str(JDK / 'bin/java'), '-Dnet.bytebuddy.agent.attacher.dump=' + str(directory / 'attacher.dump')]
                if mode == 'startup':
                    argv += ['-javaagent:' + str(AGENT)]
                argv += ['-cp', str(classes) + os.pathsep + str(AGENT), 'AttachProbe', mode]
                record = run_process(label, argv, case_env, directory)
                receipt['commands'].append(record)
                save(out / 'RECEIPT.json', receipt)
                if not record.get('no_live_owned_helpers_remain'):
                    raise RuntimeError('Owned helper cleanup incomplete; later cases not started')
            receipt['state'] = 'DIAGNOSTIC_COMPLETED'
            receipt['case_outcomes'] = {r['label']: r.get('probe_result', {}).get('state', 'NO_RESULT')
                                        for r in receipt['commands'] if r['label'] != 'compile_standalone_probe'}
            receipt['interpretation'] = 'Human review required; this is not a test/qualification PASS and starts no retry.'
        except BaseException as failure:
            receipt['state'] = 'DIAGNOSTIC_ERROR'
            receipt['error_type'] = type(failure).__name__
            receipt['error_message'] = str(failure)
        finally:
            receipt['host_gradle_properties_after'] = digest(properties)
            receipt['host_gradle_properties_unchanged'] = receipt['host_gradle_properties_after'] == properties_before
            receipt['host_inputs_unchanged'] = all(digest(p) == pin for p, pin in inputs['files'].items())
            receipt['terminal_utc'] = utc()
            save(out / 'RECEIPT.json', receipt)
            manifest = {str(p.relative_to(out)): digest(p) for p in sorted(out.rglob('*')) if p.is_file()}
            save(out / 'MANIFEST.json', {'schema': 'manual-transmission/host-probe-manifest/v1', 'files': manifest})
        if not receipt['host_gradle_properties_unchanged'] or not receipt['host_inputs_unchanged']:
            raise RuntimeError('Host inputs changed during diagnosis; review receipt without rerunning')
        print(json.dumps({'state': receipt['state'], 'out': str(out), 'case_outcomes': receipt.get('case_outcomes', {})}))
        return 0 if receipt['state'] == 'DIAGNOSTIC_COMPLETED' else 1


if __name__ == '__main__':
    raise SystemExit(main())
