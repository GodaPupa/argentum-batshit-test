#!/usr/bin/env python3
"""Fresh standard CI on one exact source; preserves failures and never admits gameplay."""
import hashlib
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(os.environ['GITHUB_WORKSPACE'])
SOURCE = ROOT / 'source'
CONTROL = ROOT / 'control'
OUT = ROOT / 'output'
SOURCE_HEAD = 'a0c5b995c2829c0b4069562b1f1eede8a820dd97'
SOURCE_TREE = '1ee5590bacf2f6507fff6487be9d3e20df8b5e40'
CONTROL_FILES = ['.github/workflows/sphinx-full-ci.yml', 'sphinx-full-ci.py', 'full-ci-gate.json']

def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def snapshot():
    value = {'head': git(SOURCE, 'rev-parse', 'HEAD'), 'tree': git(SOURCE, 'rev-parse', 'HEAD^{tree}'),
             'status': git(SOURCE, 'status', '--porcelain')}
    assert value == {'head': SOURCE_HEAD, 'tree': SOURCE_TREE, 'status': ''}, value
    return value

def run(command, cwd, label, timeout):
    row = {'command': command, 'cwd': str(cwd.relative_to(ROOT)), 'started_ns': time.time_ns()}
    path = OUT / (label + '.json')
    path.write_text(json.dumps(row, indent=2) + '\n')
    try:
        with (OUT / (label + '.log')).open('wb') as log:
            process = subprocess.Popen(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            row['owned_process_group'] = process.pid
            path.write_text(json.dumps(row, indent=2) + '\n')
            try:
                row['exit_status'] = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                row['timeout_seconds'] = timeout
                row['exit_status'] = 124
                # Retain the original CI watchdog's JVM diagnostics, bounded individually.
                # This fresh runner has one source command; only its process group is killed.
                with (OUT / (label + '-timeout-jvm.log')).open('wb') as diagnostic:
                    java_bin = Path(os.environ.get('JAVA_HOME', '/nonexistent')) / 'bin'
                    try:
                        jps = subprocess.run([str(java_bin / 'jps'), '-q'], stdout=subprocess.PIPE,
                                             stderr=subprocess.STDOUT, timeout=15)
                        diagnostic.write(jps.stdout)
                        for raw_pid in jps.stdout.decode(errors='replace').splitlines():
                            if raw_pid.isdigit():
                                try:
                                    if os.getpgid(int(raw_pid)) == process.pid:
                                        subprocess.run([str(java_bin / 'jstack'), '-l', raw_pid],
                                            stdout=diagnostic, stderr=subprocess.STDOUT, timeout=20)
                                except (ProcessLookupError, subprocess.TimeoutExpired):
                                    diagnostic.write(b'JVM departed or bounded thread dump timed out\n')
                    except (OSError, subprocess.TimeoutExpired):
                        diagnostic.write(b'JVM inventory unavailable within bounded timeout\n')
                for sig in [signal.SIGTERM, signal.SIGKILL]:
                    try:
                        os.killpg(process.pid, sig)
                    except ProcessLookupError:
                        pass
                    if sig == signal.SIGTERM:
                        time.sleep(5)
                process.wait(timeout=10)
                # SIGKILL covers all remaining members before XML copying starts.
                row['owned_process_group_terminated'] = True
    finally:
        row['finished_ns'] = time.time_ns()
        path.write_text(json.dumps(row, indent=2) + '\n')
    return row

def main():
    group = sys.argv[1]
    OUT.mkdir(exist_ok=False)
    gate = json.loads((CONTROL / 'full-ci-gate.json').read_text())
    audit = {'schema': 'sphinx-full-ci-exact-source-v1', 'group': group, 'status': 'INCOMPLETE',
             'run_id': os.environ['GITHUB_RUN_ID'], 'attempt': os.environ['GITHUB_RUN_ATTEMPT'],
             'control_head': git(CONTROL, 'rev-parse', 'HEAD'), 'errors': [], 'commands': [],
             'official_seeds': 0, 'official_games': 0, 'gameplay_authorized': False}
    try:
        assert os.environ['GITHUB_REPOSITORY'] == 'GodaPupa/argentum-batshit-test'
        assert os.environ['GITHUB_EVENT_NAME'] == 'push' and os.environ['GITHUB_RUN_ATTEMPT'] == '1'
        assert audit['control_head'] == os.environ['GITHUB_SHA']
        assert not git(CONTROL, 'status', '--porcelain')
        assert sorted(git(CONTROL, 'ls-files').splitlines()) == sorted(CONTROL_FILES)
        assert gate['ready_for_execution'] is True and gate['source_review']
        assert gate['source_head'] == SOURCE_HEAD and gate['source_tree'] == SOURCE_TREE
        assert group == 'frontend' or group in gate['backend_groups']
        audit['source_before'] = snapshot()
        audit['control_files_sha256'] = {p: sha(CONTROL / p) for p in CONTROL_FILES}
        for p in CONTROL_FILES:
            dest = OUT / 'control' / p
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(CONTROL / p, dest)
        assert sha(SOURCE / '.github/workflows/ci.yml') == gate['original_ci_sha256']
        assert not any(os.environ.get(k) for k in gate['forbidden_execution_environment'])
        (OUT / 'audit.json').write_text(json.dumps(audit, indent=2) + '\n')
        if group == 'frontend':
            for label, command in [('npm-ci', ['npm', 'ci']), ('typecheck', ['npm', 'run', 'typecheck']),
                                   ('build', ['npm', 'run', 'build'])]:
                row = run(command, SOURCE / 'web-client', label, 1200)
                audit['commands'].append(row)
                if row['exit_status']:
                    audit['errors'].append(label + ' failed')
                    break
        else:
            tasks = gate['backend_groups'][group]
            modules = [task[1:-5].replace(':', '/') for task in tasks]
            for module in modules:
                assert not list((SOURCE / module / 'build/test-results/test').glob('TEST-*.xml'))
            command = ['scripts/gradle-locked'] + tasks + ['--rerun-tasks', '--no-build-cache', '--info',
                '--stacktrace', '--continue', '--max-workers=1', '-PkotlinCompileParallelism=1',
                '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx4g']
            row = run(command, SOURCE, 'backend', 1200)
            audit['commands'].append(row)
            if row['exit_status']:
                audit['errors'].append('backend command failed or timed out; original outputs retained')
            log = (OUT / 'backend.log').read_text(errors='replace').splitlines()
            # Preserve every produced XML before parsing or judging any individual suite.
            for module in modules:
                for file in (SOURCE / module / 'build/test-results/test').glob('TEST-*.xml'):
                    dest = OUT / 'xml' / module / file.name
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(file, dest)
            audit['modules'] = []
            for task, module in zip(tasks, modules):
                xmls = sorted((SOURCE / module / 'build/test-results/test').glob('TEST-*.xml'))
                entry = {'task': task, 'module': module, 'suites': [], 'xml_count': len(xmls)}
                audit['modules'].append(entry)
                if not xmls:
                    audit['errors'].append('No actual required-module XML: ' + task)
                if '> Task ' + task not in log and '> Task ' + task + ' FAILED' not in log:
                    audit['errors'].append('No fresh required task: ' + task)
                if any('> Task ' + task + ' ' + s in log for s in ['FROM-CACHE', 'UP-TO-DATE', 'NO-SOURCE', 'SKIPPED']):
                    audit['errors'].append('Required task not fresh: ' + task)
                for file in xmls:
                    dest = OUT / 'xml' / module / file.name
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(file, dest)
                    try:
                        suite = ET.fromstring(file.read_bytes())
                    except ET.ParseError as error:
                        audit['errors'].append('Malformed preserved XML: ' + str(file) + ': ' + str(error))
                        continue
                    cases = suite.findall('testcase')
                    item = {'file': dest.relative_to(OUT).as_posix(), 'sha256': sha(dest),
                            'class': suite.attrib.get('name'), 'cases': len(cases),
                            'failures': len(suite.findall('.//failure')), 'errors': len(suite.findall('.//error')),
                            'skipped': len(suite.findall('.//skipped')),
                            'case_names': [x.attrib.get('name') for x in cases]}
                    entry['suites'].append(item)
                    if suite.tag != 'testsuite' or len(cases) != int(suite.attrib['tests']):
                        audit['errors'].append('Invalid suite: ' + str(file))
                    if item['failures'] or item['errors']:
                        audit['errors'].append('Test failure/error: ' + item['class'])
                    if not row['started_ns'] - 2_000_000_000 <= file.stat().st_mtime_ns <= row['finished_ns'] + 2_000_000_000:
                        audit['errors'].append('XML outside command time: ' + str(file))
            # Existing disabled cases remain explicitly visible; zero skips are not invented.
            audit['skips_require_artifact_review'] = True
        audit['source_after'] = snapshot()
        assert audit['source_before'] == audit['source_after']
        assert audit['control_files_sha256'] == {p: sha(CONTROL / p) for p in CONTROL_FILES}
        audit['status'] = 'PASS_PENDING_RAW_REVIEW' if not audit['errors'] else 'FAILED_PRESERVED'
    except Exception as error:
        audit['errors'].append(type(error).__name__ + ': ' + str(error))
        audit['status'] = 'FAILED_PRESERVED'
    finally:
        (OUT / 'audit.json').write_text(json.dumps(audit, indent=2) + '\n')
    return 1 if audit['errors'] else 0

if __name__ == '__main__':
    raise SystemExit(main())
