#!/usr/bin/env python3
"""Run four existing CI suites whose original results were restored from cache.

This is software regression qualification on one immutable source, never an
experimental runner. It does not alter source files or expected test outcomes.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

SOURCE = '994383d4a9495bcb34d5aae0e69181bcf4c04d45'
TREE = '4b8f4d155a37e6b66ab623d344b19cedf435d845'
ROOT = Path(os.environ['GITHUB_WORKSPACE'])
CHECKOUT = ROOT / 'source'
OUT = ROOT / 'output'
CONTROL = ROOT / 'control'
ROUTES = [
    (':mtg-sdk:test', 'CounterTypeClientMirrorTest', 'mtg-sdk'),
    (':mtg-sets:test', 'CardDefinitionSnapshotTest', 'mtg-sets'),
    (':mtg-search:test', 'ScryfallSyntaxComplianceTest', 'mtg-search'),
    (':mtgish-tooling:test', 'AsPermanentEntersCounterTest', 'mtgish-tooling'),
]


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    OUT.mkdir(exist_ok=False)
    expected = json.loads((CONTROL / 'cached-ci-case-identities.json').read_text())
    manifest = {'schema': 'shared-existing-ci-freshness-supplement-v1',
                'status': 'INCOMPLETE', 'source_head': SOURCE, 'source_tree': TREE,
                'original_ci_run': 36267982979,
                'run_id': os.environ['GITHUB_RUN_ID'],
                'run_attempt': os.environ['GITHUB_RUN_ATTEMPT'],
                'event': os.environ['GITHUB_EVENT_NAME'],
                'control_head': git(CONTROL, 'rev-parse', 'HEAD'),
                'scope': 'Four existing software CI modules only; not experimental gameplay.',
                'official_games': 0, 'gameplay_authorized': False,
                'stages': [], 'errors': []}
    try:
        assert os.environ['GITHUB_REPOSITORY'] == 'GodaPupa/argentum-batshit-test'
        assert os.environ['GITHUB_RUN_ATTEMPT'] == '1'
        assert manifest['control_head'] == os.environ['GITHUB_SHA']
        assert git(CHECKOUT, 'rev-parse', 'HEAD') == expected['source'] == SOURCE
        assert git(CHECKOUT, 'rev-parse', 'HEAD^{tree}') == expected['merge_tree'] == TREE
        assert not git(CHECKOUT, 'status', '--porcelain')
        control_paths = git(CONTROL, 'ls-files').splitlines()
        assert sorted(control_paths) == sorted([
            '.github/workflows/shared-existing-ci-freshness.yml',
            'cached-ci-case-identities.json', 'fresh-ci-supplement.py'])
        manifest['control_files_sha256'] = {p: digest(CONTROL / p) for p in control_paths}
        manifest['dependencies_sha256'] = {p: digest(CHECKOUT / p) for p in [
            'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties',
            'gradle/wrapper/gradle-wrapper.jar', 'gradlew', 'settings.gradle.kts',
            'justfile', 'scripts/test-class', 'scripts/gradle-locked']}
        for task, selector, module in ROUTES:
            folder = OUT / module
            folder.mkdir()
            command = ['just', 'test-class', selector, '--tests=*', '--rerun',
                       '--no-build-cache', '--info', '--stacktrace', '--max-workers=1',
                       '-PkotlinCompileParallelism=1',
                       '-Pkotlin.compiler.execution.strategy=in-process',
                       '-Dorg.gradle.jvmargs=-Xmx4g']
            started = time.time_ns()
            with (folder / 'command.log').open('wb') as stream:
                run = subprocess.run(command, cwd=CHECKOUT, stdout=stream,
                                     stderr=subprocess.STDOUT)
            row = {'task': task, 'command': command, 'exit_status': run.returncode,
                   'started_ns': started, 'finished_ns': time.time_ns(), 'suites': []}
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
            assert run.returncode == 0, 'Test command failed: ' + task
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
        assert len(manifest['stages']) == 4
        assert git(CHECKOUT, 'rev-parse', 'HEAD') == SOURCE
        assert git(CHECKOUT, 'rev-parse', 'HEAD^{tree}') == TREE
        assert manifest['control_files_sha256'] == {p: digest(CONTROL / p) for p in control_paths}
        manifest['status'] = 'PASS_REQUIRES_ARTIFACT_REVIEW'
    except Exception as exc:
        manifest['errors'].append(type(exc).__name__ + ': ' + str(exc))
        raise
    finally:
        (OUT / 'audit.json').write_text(json.dumps(manifest, indent=2) + '\n')


if __name__ == '__main__':
    main()
