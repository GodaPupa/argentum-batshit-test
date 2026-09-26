#!/usr/bin/env python3
"""Run the one existing source08 mtgish-tooling CI suite restored from cache.

This is software regression qualification on one immutable source, never an
experimental runner. It does not alter source files or expected test outcomes.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
import xml.etree.ElementTree as ET

SOURCE = '3a4f99a7653839506e96d19e6639f58d9e8c5ced'
TREE = '56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6'
ROOT = Path(os.environ['GITHUB_WORKSPACE'])
CHECKOUT = ROOT / 'source'
OUT = ROOT / 'output'
CONTROL = ROOT / 'control'
ROUTES = [(':mtgish-tooling:test', 'AsPermanentEntersCounterTest', 'mtgish-tooling')]


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_owned(command, cwd, label, timeout):
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
    OUT.mkdir(exist_ok=False)
    expected = json.loads((CONTROL / 'existing-mtgish-case-identities.json').read_text())
    manifest = {'schema': 'ferocity-source08-existing-mtgish-freshness-v1',
                'status': 'INCOMPLETE', 'source_head': SOURCE, 'source_tree': TREE,
                'original_ci_run': 36268324392,
                'run_id': os.environ['GITHUB_RUN_ID'],
                'run_attempt': os.environ['GITHUB_RUN_ATTEMPT'],
                'event': os.environ['GITHUB_EVENT_NAME'],
                'control_head': git(CONTROL, 'rev-parse', 'HEAD'),
                'scope': 'Exactly existing147 mtgish-tooling software cases; three existing data-dependent skips retained. No resource calibration, export or experimental gameplay.',
                'official_games': 0, 'gameplay_authorized': False,
                'stages': [], 'errors': []}
    try:
        assert os.environ['GITHUB_REPOSITORY'] == 'GodaPupa/argentum-batshit-test'
        assert os.environ['GITHUB_RUN_ATTEMPT'] == '1'
        assert os.environ['GITHUB_EVENT_NAME'] == 'push'
        assert os.environ['GITHUB_REF'] == 'refs/heads/lab/ferocity-source08-mtgish-freshness-20260926'
        event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
        assert event['before'] == '0' * 40 and event['created'] is True
        assert event['after'] == os.environ['GITHUB_SHA']
        assert not (Path.home() / '.cache/scryfall/por.json').exists(), 'Opt-in cache must remain absent'
        assert sum(len(row['cases']) for row in expected['rows']) == 147
        assert sum(c['skipped'] for row in expected['rows'] for c in row['cases']) == 3
        assert manifest['control_head'] == os.environ['GITHUB_SHA']
        assert git(CHECKOUT, 'rev-parse', 'HEAD') == expected['source'] == SOURCE
        assert git(CHECKOUT, 'rev-parse', 'HEAD^{tree}') == expected['merge_tree'] == TREE
        assert not git(CHECKOUT, 'status', '--porcelain')
        control_paths = git(CONTROL, 'ls-files').splitlines()
        assert sorted(control_paths) == sorted([
            '.github/workflows/ferocity-source08-mtgish-freshness.yml',
            'existing-mtgish-case-identities.json', 'ferocity-mtgish-freshness.py'])
        manifest['control_files_sha256'] = {p: digest(CONTROL / p) for p in control_paths}
        for path in control_paths:
            dest = OUT / 'control' / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(CONTROL / path, dest)
        manifest['dependencies_sha256'] = {p: digest(CHECKOUT / p) for p in [
            'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties',
            'gradle/wrapper/gradle-wrapper.jar', 'gradlew', 'settings.gradle.kts',
            'justfile', 'scripts/test-class', 'scripts/gradle-locked']}
        input_paths = [p for p in git(CHECKOUT, 'ls-files').splitlines()
                       if p.startswith(('mtgish-tooling/', 'mtg-sdk/src/main/', 'mtg-sets/', 'buildSrc/', 'gradle/'))
                       or p in ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'gradlew', 'justfile', 'scripts/test-class', 'scripts/gradle-locked']]
        before_inputs = {p: digest(CHECKOUT / p) for p in input_paths}
        (OUT / 'executable-and-read-inputs-before.json').write_text(json.dumps(before_inputs, indent=2, sort_keys=True) + '\n')
        manifest['input_map_sha256'] = digest(OUT / 'executable-and-read-inputs-before.json')
        for task, selector, module in ROUTES:
            folder = OUT / module
            folder.mkdir()
            command = ['just', 'test-class', selector, '--tests=*', '--rerun',
                       '--no-build-cache', '--info', '--stacktrace', '--max-workers=1',
                       '-PkotlinCompileParallelism=1',
                       '-Pkotlin.compiler.execution.strategy=in-process',
                       '-Dorg.gradle.jvmargs=-Xmx4g']
            row = run_owned(command, CHECKOUT, module + '/command', 1200)
            started = row['started_ns']
            row.update({'task': task, 'suites': []})
            manifest['stages'].append(row)
            (folder / 'command.json').write_text(json.dumps(row, indent=2) + '\n')
            xml_dir = CHECKOUT / module / 'build/test-results/test'
            wanted = {r['class']: r for r in expected['rows']
                      if r['module'] == task and not r['class'].startswith('Gradle Test Run ')}
            markers = {r['class']: r for r in expected['rows']
                       if r['module'] == task and r['class'].startswith('Gradle Test Run ')}
            observed = {}
            seen = set()
            paths = sorted(xml_dir.glob('TEST-*.xml'))
            # Preserve the entire produced bank before a validation error can stop parsing.
            for path in paths:
                shutil.copyfile(path, folder / path.name)
            row['reporting_markers'] = []
            for path in paths:
                raw = path.read_bytes()
                suite = ET.fromstring(raw)
                cases = [{'name': c.attrib['name'], 'skipped': c.find('skipped') is not None}
                         for c in suite.findall('testcase')]
                identity = suite.attrib['name']
                assert identity not in seen, 'Duplicate suite'
                seen.add(identity)
                assert len(cases) == int(suite.attrib['tests'])
                assert not any(int(suite.attrib.get(k, 0)) for k in ['failures', 'errors'])
                assert not suite.findall('.//failure') and not suite.findall('.//error')
                if identity in markers:
                    assert cases == markers[identity]['cases'], 'Disabled-spec marker changed'
                    assert not suite.findall('.//failure') and not suite.findall('.//error')
                    row['reporting_markers'].append({'class': identity, 'cases': cases,
                                                     'sha256': hashlib.sha256(raw).hexdigest(),
                                                     'actual_tests_counted': 0})
                    continue
                assert identity not in observed, 'Duplicate suite'
                observed[identity] = cases
                row['suites'].append({'class': identity, 'cases': len(cases),
                                      'skipped': sum(c['skipped'] for c in cases),
                                      'sha256': hashlib.sha256(raw).hexdigest()})
                assert path.stat().st_mtime_ns >= started - 2_000_000_000, 'Stale XML'
            assert row['exit_status'] == 0, 'Test command failed or timed out: ' + task
            assert set(observed) == set(wanted), 'Suite identity drift: ' + task
            for identity, cases in observed.items():
                assert sorted(cases, key=lambda c: c['name']) == sorted(
                    wanted[identity]['cases'], key=lambda c: c['name']), identity
            lines = (folder / 'command.log').read_text().splitlines()
            assert any(line.strip() == '> Task ' + task for line in lines), 'No actual test task'
            assert not any(line.strip() in ['> Task ' + task + ' ' + suffix
                       for suffix in ['FROM-CACHE', 'UP-TO-DATE', 'NO-SOURCE', 'SKIPPED']]
                       for line in lines), 'Required test did not run'
            assert not git(CHECKOUT, 'status', '--porcelain'), 'Source changed'
            row['status'] = 'PASS_EXISTING_IDENTITIES_AND_SKIP_SET_UNCHANGED'
        assert len(manifest['stages']) == 1
        assert git(CHECKOUT, 'rev-parse', 'HEAD') == SOURCE
        assert git(CHECKOUT, 'rev-parse', 'HEAD^{tree}') == TREE
        assert manifest['control_files_sha256'] == {p: digest(CONTROL / p) for p in control_paths}
        after_inputs = {p: digest(CHECKOUT / p) for p in input_paths}
        (OUT / 'executable-and-read-inputs-after.json').write_text(json.dumps(after_inputs, indent=2, sort_keys=True) + '\n')
        assert before_inputs == after_inputs, 'Executable or SDK text inputs changed'
        manifest['status'] = 'PASS_REQUIRES_ARTIFACT_REVIEW'
    except Exception as exc:
        manifest['errors'].append(type(exc).__name__ + ': ' + str(exc))
        raise
    finally:
        (OUT / 'audit.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
