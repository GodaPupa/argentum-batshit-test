#!/usr/bin/env python3
"""Bind and retain the fixed actor50 plus mechanical mulligan14 qualification."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
REPORT = ROOT / 'build/reports/shared-actor-mulligan'
FREEZE = 'lab-coordinator/shared-capabilities/actor-mulligan-gate-freeze.json'
SOURCE = 'lab-coordinator/shared-capabilities/actor-mulligan-receiving.json'
SOURCE_SHA = '70387d5ae31d829868335298994f25119f12cba14841bfac948c8a94ce887402'


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def write(name, value):
    (REPORT / name).write_text(json.dumps(value, indent=2) + '\n')


def binding(freeze):
    paths = sorted(set(freeze['source_sha256']) | {FREEZE})
    return {'head': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
            'checkout_status': git('status', '--porcelain'),
            'files_sha256': {p: sha((ROOT / p).read_bytes()) for p in paths}}


def bind(freeze):
    REPORT.mkdir(parents=True, exist_ok=False)
    before = binding(freeze)
    write('source-before.json', before)
    write('run-identity.json', {k: os.environ.get(k) for k in (
        'GITHUB_SHA', 'GITHUB_RUN_ID', 'GITHUB_RUN_ATTEMPT', 'GITHUB_EVENT_NAME')})
    (REPORT / 'gate-freeze.json').write_bytes((ROOT / FREEZE).read_bytes())
    (REPORT / 'source-manifest.json').write_bytes((ROOT / SOURCE).read_bytes())
    assert before['head'] == os.environ['GITHUB_SHA'] and not before['checkout_status']
    assert all(before['files_sha256'][p] == h for p, h in freeze['source_sha256'].items())
    assert before['files_sha256'][SOURCE] == SOURCE_SHA
    source = json.loads((ROOT / SOURCE).read_bytes())
    subprocess.run(['git', 'merge-base', '--is-ancestor', source['source_parent'], 'HEAD'], cwd=ROOT, check=True)
    assert source['required_actual_total'] == freeze['required_actual_total'] == 64
    assert [b['expected_cases'] for b in freeze['banks']] == [28, 12, 6, 4, 14]
    assert freeze['banks'][-1]['case_names'] == source['new_bank']['case_names']
    assert all(len(b['case_names']) == len(set(b['case_names'])) == b['expected_cases'] for b in freeze['banks'])
    assert all(before['files_sha256'][p] == h for p, h in source['source_sha256'].items())
    assert freeze['ready_for_execution'] is True
    write('binding-accepted.json', {'exact_source_bound': True, 'official_games': 0})


def execute(freeze):
    assert json.loads((REPORT / 'binding-accepted.json').read_text())['exact_source_bound']
    assert binding(freeze) == json.loads((REPORT / 'source-before.json').read_text())
    command = ['just', 'test-class', 'ActorObservationBoundaryTest',
               '--tests', '*ActorStackSourceObservationTest',
               '--tests', '*ActorTriggerOrderObservationTest',
               '--tests', '*ActorPriorityObservationTest',
               '--tests', '*ActorMulliganEligibilityTest',
               '--rerun', '--no-build-cache', '--info', '--stacktrace', '--max-workers=1',
               '-PkotlinCompileParallelism=1', '-Pkotlin.compiler.execution.strategy=in-process',
               '-Dorg.gradle.jvmargs=-Xmx4g']
    started = time.time_ns()
    with (REPORT / 'qualification.log').open('wb') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    write('command.json', {'command': command, 'exit_status': result.returncode,
                          'started_epoch_ns': started, 'finished_epoch_ns': time.time_ns(),
                          'scope': 'One actual command runs the five fixed banks; no independent class invocations are implied.'})
    print(json.dumps({'actual_command_exit': result.returncode}))
    return result.returncode


def collect(freeze):
    REPORT.mkdir(parents=True, exist_ok=True)
    errors = []
    try:
        after = binding(freeze)
        write('source-after.json', after)
        assert after == json.loads((REPORT / 'source-before.json').read_text())
        assert after['head'] == os.environ['GITHUB_SHA'] and not after['checkout_status']
        assert json.loads((REPORT / 'binding-accepted.json').read_text())['exact_source_bound']
        assert all(after['files_sha256'][p] == h for p, h in freeze['source_sha256'].items())
    except (OSError, ValueError, KeyError, AssertionError) as exc:
        errors.append('Exact source binding failed: ' + str(exc))
    command_path = REPORT / 'command.json'
    command = json.loads(command_path.read_text()) if command_path.is_file() else None
    for bank in freeze['banks']:
        folder = REPORT / 'tests' / bank['stage']
        folder.mkdir(parents=True, exist_ok=False)
        if command:
            (folder / 'exit-status.txt').write_text(str(command['exit_status']) + '\n')
        xml = ROOT / 'gym/build/test-results/test' / ('TEST-' + bank['class'] + '.xml')
        if xml.is_file():
            fresh = command and xml.stat().st_mtime_ns >= command['started_epoch_ns'] - 2_000_000_000
            shutil.copyfile(xml, folder / (xml.name if fresh else 'rejected-stale-' + xml.name))
            if not fresh:
                errors.append('Stale XML: ' + bank['stage'])
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location('shared_cases', ROOT / 'lab-coordinator/shared-capabilities/collect-priority-receiving.py')
    shared = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(shared)
    rows, case_errors = shared.collect_case_rows(REPORT, freeze['banks'])
    errors.extend(case_errors)
    for row, bank in zip(rows, freeze['banks']):
        if set(row.get('case_names', [])) != set(bank['case_names']):
            errors.append('Frozen case identities differ: ' + bank['class'])
    actual = sum(row.get('actual_cases', 0) for row in rows)
    if len(rows) != 5 or actual != 64:
        errors.append('Original50 plus separate14 bank incomplete')
    write('actual-case-audit.json', {'status': 'INCOMPLETE_OR_FAILED' if errors else 'PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW',
          'banks': rows, 'actual_cases': actual, 'errors': errors,
          'original_actor_cases': sum(row.get('actual_cases', 0) for row in rows[:4]),
          'new_mechanical_cases': rows[-1].get('actual_cases', 0) if rows else 0,
          'pilot_qualified': False, 'scheduler_integration_qualified': False,
          'receiving_runtime_accepted': False, 'gameplay_authorized': False, 'official_games': 0})
    print(json.dumps({'actual_cases': actual, 'errors': errors}))
    return 1 if errors else 0


if __name__ == '__main__':
    assert len(sys.argv) == 2 and sys.argv[1] in {'bind', 'execute', 'collect'}
    raise SystemExit({'bind': bind, 'execute': execute, 'collect': collect}[sys.argv[1]](
        json.loads((ROOT / FREEZE).read_bytes())))
