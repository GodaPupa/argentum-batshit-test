#!/usr/bin/env python3
"""Provenance and strict acceptance for the four frozen browser cases; never launches tests."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'ferocity-recycling/evidence/browser/combat'
BANK = ROOT / 'ferocity-recycling/runtime-audits/combat-browser/PRE_EXECUTION_PLAN_05.json'
MODULES = ('mtg-sdk/', 'mtg-sets/', 'rules-engine/', 'game-server/', 'ai/', 'mtg-search/',
           'oracle-assay/', 'mtgish-tooling/', 'web-client/', 'e2e-scenarios/', 'buildSrc/', 'gradle/', 'scripts/')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def command(args, cwd=ROOT):
    return subprocess.check_output(args, cwd=cwd, text=True, stderr=subprocess.STDOUT).strip()


def write(name, value):
    path = OUT / name
    # Every phase is single-use. An existing receipt is never overwritten by a rerun.
    with path.open('x') as handle:
        json.dump(value, handle, indent=2, ensure_ascii=False)
        handle.write('\n')


def snapshot():
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
    paths = [p for p in tracked if p and (
        p in ('gradlew', 'gradlew.bat', 'build.gradle.kts', 'settings.gradle.kts', 'gradle.properties', 'justfile')
        or p.startswith(MODULES)
        or p in ('ferocity-recycling/tools/record_combat_browser.py',
                 'ferocity-recycling/combat-browser.just', 'scripts/gradle-locked',
                 'ferocity-recycling/runtime-audits/combat-browser/PRE_EXECUTION_PLAN.json',
                 'ferocity-recycling/runtime-audits/combat-browser/PRE_EXECUTION_PLAN_05.json',
                 '.github/workflows/ferocity-recycling-combat-browser.yml'))]
    return {'head': command(['git', 'rev-parse', 'HEAD']),
            'tracked_status': command(['git', 'status', '--porcelain', '--untracked-files=no']),
            'files': {p: sha(ROOT / p) for p in sorted(paths)}}


def binary(path):
    real = Path(path).resolve(strict=True)
    return {'path': str(real), 'sha256': sha(real)}


def runtime():
    node = json.loads(command(['node', '-e', 'process.stdout.write(JSON.stringify({path:process.execPath,version:process.version,platform:process.platform,arch:process.arch}))']))
    browser = json.loads(command(['node', '-e', 'const p=require("playwright");const fs=require("fs");process.stdout.write(JSON.stringify({executable:p.chromium.executablePath(),playwright:JSON.parse(fs.readFileSync("node_modules/playwright/package.json","utf8")).version,browsers:JSON.parse(fs.readFileSync("node_modules/playwright-core/browsers.json","utf8"))}))'], ROOT / 'e2e-scenarios'))
    java_home = Path(os.environ['JAVA_HOME']).resolve(strict=True)
    browser_home = Path(os.environ['PLAYWRIGHT_BROWSERS_PATH']).resolve(strict=True)
    # Headless Chromium can launch the separately installed headless-shell binary rather than
    # chromium.executablePath(). Pin the whole installed browser/FFmpeg closure, not just Chrome.
    browser_files = {str(p.relative_to(browser_home)): sha(p)
                     for p in sorted(browser_home.rglob('*')) if p.is_file()}
    if not browser_files:
        raise RuntimeError('Installed browser closure is empty')
    return {'node': node | binary(node['path']), 'npm_version': command(['npm', '--version']),
            'just': binary(shutil.which('just')), 'just_version': command(['just', '--version']),
            'java': binary(java_home / 'bin/java'), 'java_version': command([str(java_home / 'bin/java'), '-version']),
            'java_release': (java_home / 'release').read_text(),
            'chromium': binary(browser.pop('executable')), 'playwright': browser,
            'installed_browser_home': str(browser_home), 'installed_browser_files': browser_files,
            'locks': {p: sha(ROOT / p) for p in ('web-client/package-lock.json', 'e2e-scenarios/package-lock.json')}}


def specs(suites):
    return [spec for suite in suites for spec in suite.get('specs', []) + specs(suite.get('suites', []))]


def finish(provision_status, client_status, browser_status):
    before = json.loads((OUT / 'source-before.json').read_text())
    after = snapshot()
    write('source-after.json', after)
    reasons = []
    if before['source'] != after or after['tracked_status']:
        reasons.append('SOURCE_OR_TRACKED_WORKTREE_CHANGED')
    if (provision_status, client_status, browser_status) != ('success', 'success', 'success'):
        reasons.append('REQUIRED_COMMAND_NOT_SUCCESSFUL')
    raw = {}
    entries = []
    try:
        exit_code = int((OUT / 'browser-exit-code.txt').read_text().strip())
        if exit_code != 0:
            reasons.append('BROWSER_PROCESS_NONZERO')
        raw = json.loads((OUT / 'results.json').read_text())
        entries = specs(raw.get('suites', []))
        bank = json.loads(BANK.read_text())
        expected = sorted((case['file'].split('/tests/', 1)[1], case['title']) for case in bank['cases'])
        actual = sorted((spec['file'].replace('\\', '/').split('/tests/')[-1], spec['title']) for spec in entries)
        if actual != expected:
            reasons.append('EXACT_FOUR_CASE_INVENTORY_MISMATCH')
        for spec in entries:
            tests = spec.get('tests', [])
            if len(tests) != 1 or tests[0].get('expectedStatus') != 'passed':
                reasons.append('PROJECT_OR_EXPECTED_STATUS_MISMATCH')
                continue
            results = tests[0].get('results', [])
            if len(results) != 1 or results[0].get('status') != 'passed' or results[0].get('retry', 0) != 0:
                reasons.append('FAILED_SKIPPED_RETRIED_OR_INCOMPLETE_CASE')
        if raw.get('errors'):
            reasons.append('GLOBAL_PLAYWRIGHT_ERROR')
        prior_runtime = json.loads((OUT / 'runtime-before.json').read_text())
        current_runtime = runtime()
        write('runtime-after.json', current_runtime)
        if prior_runtime != current_runtime:
            reasons.append('RUNTIME_OR_LOCK_IDENTITY_CHANGED')
    except Exception as error:
        reasons.append('MISSING_OR_UNREADABLE_REQUIRED_EVIDENCE:' + type(error).__name__)
    artifacts = {str(p.relative_to(OUT)): {'sha256': sha(p), 'bytes': p.stat().st_size}
                 for p in sorted(OUT.rglob('*')) if p.is_file()}
    receipt = {'schema': 'ferocity-combat-browser-receipt-v1', 'finished_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
               'status': 'PASS_FOUR_FIXED_BROWSER_CASES' if not reasons else 'NOT_ACCEPTED',
               'reasons': sorted(set(reasons)), 'source_head': after['head'], 'bank_sha256': sha(BANK),
               'command_outcomes': {'provision': provision_status, 'client_build': client_status, 'browser': browser_status},
               'observed_case_count': len(entries), 'playwright_stats': raw.get('stats'),
               'artifacts': artifacts, 'research_game_counts': {'development': 0, 'evaluation': 0, 'confirmation': 0},
               'scope': 'Current CombatResolutionBoard through real browser/server actions. Legacy modal has no demonstrated production route; no whole-game/pilot claim.'}
    write('receipt.json', receipt)
    print(json.dumps({'status': receipt['status'], 'reasons': receipt['reasons']}))
    return 0 if not reasons else 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('phase', choices=['before', 'runtime', 'finish'])
    parser.add_argument('--provision-status', default='unknown')
    parser.add_argument('--client-status', default='unknown')
    parser.add_argument('--browser-status', default='unknown')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.phase == 'before':
        source = snapshot()
        if source['tracked_status']:
            raise RuntimeError('Initial tracked worktree is not clean')
        if source['head'] != os.environ['EXPECTED_HEAD']:
            raise RuntimeError('Checkout is not the exact expected PR head or dispatched source')
        bank = json.loads(BANK.read_text())
        # The current engine may have an independently reviewed source successor before dispatch;
        # capture that exact whole-source commit above. These UI/harness/lock bytes are fixed here.
        for path, expected in bank['existing_sources'].items():
            if path.startswith(('e2e-scenarios/', 'web-client/')) and sha(ROOT / path) != expected:
                raise RuntimeError('Frozen browser input differs: ' + path)
        write('source-before.json', {'source': source, 'bank_sha256': sha(BANK),
              'run_id': os.environ.get('GITHUB_RUN_ID'), 'run_attempt': os.environ.get('GITHUB_RUN_ATTEMPT'),
              'expected_head': os.environ['EXPECTED_HEAD'], 'trigger_event': os.environ.get('GITHUB_EVENT_NAME'),
              'trigger_sha': os.environ.get('GITHUB_SHA'),
              'started_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'runner_image': {key: os.environ.get(key) for key in ('ImageOS', 'ImageVersion', 'RUNNER_ARCH')}})
    elif args.phase == 'runtime':
        write('runtime-before.json', runtime())
    else:
        return finish(args.provision_status, args.client_status, args.browser_status)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        OUT.mkdir(parents=True, exist_ok=True)
        phase = sys.argv[1] if len(sys.argv) > 1 else 'unknown'
        failure = {'status': 'NOT_ACCEPTED', 'phase': phase, 'exception': type(error).__name__,
                   'message': str(error), 'utc': datetime.datetime.now(datetime.timezone.utc).isoformat()}
        # Even provisioning/preflight failures receive a durable, distinct artifact. The original
        # phase receipt, if one exists, is never replaced and an incomplete run cannot pass.
        with (OUT / f'phase-error-{phase}.json').open('x') as handle:
            json.dump(failure, handle, indent=2)
            handle.write('\n')
        print(json.dumps(failure), file=sys.stderr)
        sys.exit(1)
