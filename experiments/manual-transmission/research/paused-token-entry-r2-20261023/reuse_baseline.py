"""Read original R1 bytes and bind reused56; no tests, builds, games, or network calls."""
from pathlib import Path
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import zipfile
import xml.etree.ElementTree as ET
sys.dont_write_bytecode = True


def audit_reuse(write=False):
    root = Path(os.environ.get('RECORD', Path(__file__).resolve().parent))
    plan = json.loads((root / 'BASELINE_REUSE.json').read_bytes())
    protocol = json.loads((root / 'PROTOCOL.json').read_bytes())
    git = lambda *args: subprocess.check_output(['git', *args])
    sha = lambda body: hashlib.sha256(body).hexdigest()
    original = {}
    for name, spec in plan['inputs'].items():
        body = git('show', plan['receipt'] + ':' + spec['path'])
        assert len(body) == spec['bytes'] and sha(body) == spec['sha256'], name
        original[name] = body
    meta, run = [json.loads(original[name]) for name in ['ARTIFACT.json', 'RUN.json']]
    assert meta['id'] == plan['artifact_id'] and meta['size_in_bytes'] == plan['artifact_bytes']
    assert meta['digest'] == 'sha256:' + plan['artifact_sha256']
    assert meta['workflow_run']['id'] == plan['run_id'] and meta['workflow_run']['head_sha'] == plan['validation']
    assert run['id'] == plan['run_id'] and run['head_sha'] == plan['validation'] and run['run_attempt'] == 1
    assert run['status'] == 'completed' and run['conclusion'] == 'failure'
    assert len(run['jobs']) == 1 and run['jobs'][0]['id'] == plan['job_id'] and run['jobs'][0]['conclusion'] == 'failure'
    steps = {step['name']: step for step in run['jobs'][0]['steps']}
    for name, conclusion in [
        ('Bind candidate, protected tests, deck, locks and goldens', 'success'),
        ('Run frozen original and supplemental baseline controls against qualified counter-Metamorph source', 'success'),
        ('Require exact preregistered verdicts, identities and first assertion frames', 'failure'),
        ('Restore and rebind exactly the candidate source', 'success'),
        ('All frozen focused cases and unchanged entry-copy-defender regressions', 'skipped'),
        ('Complete engine and all nine card-era scenario suites', 'skipped'),
        ('Ordinary whole-project build with routine AI tests', 'skipped'),
    ]:
        assert steps[name]['status'] == 'completed' and steps[name]['conclusion'] == conclusion, name
    assert json.loads(original['RECEIVING_AUDIT.json'])['status'] == 'QUALIFICATION_INCOMPLETE'
    old_protocol = json.loads(original['PROTOCOL.json'])
    assert old_protocol == json.loads(git('show', plan['source'] + ':experiments/manual-transmission/research/paused-token-entry-r1-20261023/PROTOCOL.json'))
    assert old_protocol['baseline_source'] == protocol['baseline_source'] == plan['baseline']
    assert git('rev-parse', plan['source'] + '^{tree}').decode().strip() == plan['source_tree']
    assert git('rev-parse', plan['validation'] + '^{tree}').decode().strip() == plan['validation_tree']
    archive_body = original['original-12497122147.zip']
    assert len(archive_body) == plan['artifact_bytes'] and sha(archive_body) == plan['artifact_sha256']
    z = zipfile.ZipFile(io.BytesIO(archive_body))
    assert z.testzip() is None
    names = z.namelist()
    assert len(names) == len(set(names))
    inventory = {}
    for line in z.read('SHA256SUMS').decode().splitlines():
        expected, name = line.split('  ', 1)
        assert name not in inventory and sha(z.read(name)) == expected, name
        inventory[name] = expected
    assert set(names) == set(inventory) | {'SHA256SUMS'}
    expected_bindings = json.loads((root / 'BINDINGS.json').read_bytes())
    for mode in ['fixed', 'negative']:
        binding = json.loads(z.read('binding-' + mode + '.json'))
        for field in ['source', 'source_tree', 'validation', 'validation_tree', 'baseline']:
            assert binding[field] == plan[field], (mode, field)
        assert binding['source_manifest'] == old_protocol['source_manifest']
        assert binding['bindings'] == expected_bindings and len(expected_bindings) == 144
        assert binding['goldens_verified'] == 23 and binding['official_games'] == binding['official_seeds'] == 0
    tracked = z.read('negative/tracked-changes.txt').decode()
    assert {line[3:] for line in tracked.splitlines()} == set(old_protocol['baseline_restore_paths'])
    assert int(z.read('negative/exit.txt').strip()) != 0
    log = z.read('negative/gradle.log').decode(errors='replace')
    task_outcomes = {match.group(1): match.group(2) or 'EXECUTED' for match in
        re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$', log, re.M)}
    for task in [':rules-engine:compileKotlin', ':rules-engine:compileTestFixturesKotlin', ':rules-engine:compileTestKotlin']:
        assert task_outcomes.get(task) == 'EXECUTED', (task, task_outcomes.get(task))
    assert task_outcomes.get(':rules-engine:test') == 'FAILED', task_outcomes.get(':rules-engine:test')
    previous = json.loads(z.read('negative/PREEXISTING_REPORTS.json'))
    previous_names = {'negative/preexisting-junit/' + item['path'] for item in previous}
    assert len(previous_names) == len(previous)
    assert previous_names == {name for name in names if name.startswith('negative/preexisting-junit/')}
    for item in previous:
        body = z.read('negative/preexisting-junit/' + item['path'])
        assert len(body) == item['bytes'] and sha(body) == item['sha256']
    # Every R1 production path and every unchanged fixture must remain exactly the original bytes.
    invalid = plan['original_fixture_path']
    for path, spec in old_protocol['source_manifest'].items():
        body = git('show', plan['source'] + ':' + path)
        assert sha(body) == spec['sha256'], path
        if path != invalid:
            candidate = git('show', os.environ['SOURCE'] + ':' + path) if os.environ.get('SOURCE') else Path(path).read_bytes()
            assert candidate == body, path
    old_invalid = git('show', plan['source'] + ':' + invalid)
    assert (root / 'annex/PausedTokenInvalidResponseTest.R1.kt').read_bytes() == old_invalid
    assert sha(old_invalid) == plan['original_fixture_sha256']
    insertion = (b'            val counters = result.getEntity(token)!!.get<CountersComponent>()\n'
                 b'            (counters != null) shouldBe true\n')
    new_invalid = git('show', os.environ['SOURCE'] + ':' + invalid) if os.environ.get('SOURCE') else Path(invalid).read_bytes()
    assert new_invalid.count(insertion) == 1 and new_invalid.replace(insertion, b'', 1) == old_invalid
    expected = {case['classname'] + '::' + case['name']: case for case in plan['reused_cases']}
    all_original = {case['classname'] + '::' + case['name']: case for case in old_protocol['cases']}
    for identity, case in expected.items():
        assert case['predicted_baseline'] == all_original[identity]['predicted_baseline'], identity
    observed, retained, failures, classes = {}, {}, [], set()
    full = dict(rows=0, passed=0, assertion_failures=0, null_pointer_exceptions=0)
    for name in sorted(names):
        if not name.startswith('negative/junit/') or not name.endswith('.xml'):
            continue
        suite = ET.fromstring(z.read(name))
        cases = suite.findall('.//testcase')
        assert len(cases) == int(suite.get('tests'))
        for label, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
            assert sum(case.find(tag) is not None for case in cases) == int(suite.get(label))
        for case in cases:
            identity = case.get('classname') + '::' + case.get('name')
            assert identity in all_original and identity not in observed
            assert case.find('skipped') is None and case.find('error') is None
            failure = case.find('failure')
            verdict = 'pass' if failure is None else 'assertion_failure' if failure.get('type') == 'org.opentest4j.AssertionFailedError' else 'null_pointer_exception' if failure.get('type') == 'java.lang.NullPointerException' else 'unexpected_exception'
            assert verdict != 'unexpected_exception', identity
            observed[identity] = verdict
            full['rows'] += 1
            full[{'pass': 'passed', 'assertion_failure': 'assertion_failures', 'null_pointer_exception': 'null_pointer_exceptions'}[verdict]] += 1
            if identity not in expected:
                continue
            spec = expected[identity]
            assert verdict == spec['predicted_baseline'], identity
            frame = None
            if failure is not None:
                match = re.search(re.escape(spec['classname'].rsplit('.', 1)[-1]) + r'\.kt:(\d+)', failure.text or '')
                frame = int(match.group(1)) if match else None
                assert frame == spec['expected_first_failure_line'], (identity, frame)
                failures.append(dict(identity=identity, type=failure.get('type'), message=failure.get('message'), detail=failure.text, xml=name))
            retained[identity] = dict(verdict=verdict, first_assertion_frame=frame, xml=name, xml_sha256=sha(z.read(name)))
            classes.add(spec['classname'])
    assert set(observed) == set(all_original) and full == plan['r1_actual'], full
    assert set(retained) == set(expected)
    totals = dict(rows=len(retained), passed=sum(item['verdict'] == 'pass' for item in retained.values()),
        failures=len(failures), errors=0, skipped=0, classes=len(classes))
    assert totals == plan['expected_reused'] == protocol['expected_reused_baseline']
    corrections = {item['classname'] + '::' + item['name']: item for item in plan['frame_corrections']}
    actual_corrections = {identity for identity, case in expected.items()
                          if case['expected_first_failure_line'] != all_original[identity]['expected_first_failure_line']}
    assert actual_corrections == set(corrections) and len(corrections) == 2
    for identity, correction in corrections.items():
        assert correction['original_preregistered_first_frame'] == all_original[identity]['expected_first_failure_line'] == 81
        assert correction['observed_original_first_frame'] == correction['r2_reused_first_frame'] == retained[identity]['first_assertion_frame'] == 80
    result = dict(status='ORIGINAL56_VERIFIED_FOR_R2_REUSE_ONLY', r1_status='FAILED_UNADOPTED',
        receipt=plan['receipt'], source=plan['source'], baseline=plan['baseline'], validation=plan['validation'],
        run=plan['run_id'], job=plan['job_id'], attempt=1, artifact=plan['artifact_id'],
        artifact_bytes=len(archive_body), artifact_sha256=sha(archive_body), payload_inventory=inventory,
        original_compile_and_test_tasks={task: task_outcomes[task] for task in [
            ':rules-engine:compileKotlin', ':rules-engine:compileTestFixturesKotlin', ':rules-engine:compileTestKotlin', ':rules-engine:test']},
        original_preexisting_reports=previous,
        r1_actual=full, reused_totals=totals, reused_cases=retained, original_failures=failures,
        frame_corrections=plan['frame_corrections'], excluded_invalid14=True,
        external_independent_review=False, official_games=0, official_seeds=0, competitive_admission=False)
    if write:
        out = Path('paused-token-r2-evidence/baseline-reuse')
        out.mkdir(parents=True, exist_ok=True)
        for name, body in original.items():
            (out / name).write_bytes(body)
        (out / 'REUSED56.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    result = audit_reuse(write=True)
    print(json.dumps({key: result[key] for key in ['status', 'r1_status', 'reused_totals', 'artifact', 'artifact_sha256']}, indent=2))
