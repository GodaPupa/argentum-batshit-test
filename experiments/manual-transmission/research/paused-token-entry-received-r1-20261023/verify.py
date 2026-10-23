"""Recount original retained evidence, never run candidate code/tests/games.

--write records observations, including failed/incomplete executions. Without --write,
recompute and require exact equality. --require-qualified additionally exits nonzero
unless every gate passes; reproducing an incomplete receipt is not qualification.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
sys.dont_write_bytecode = True
from common import ROOT, artifact, compare_differential, digest, frozen_inputs, load_json, save_or_compare
from audit_git_source import audit

parser = argparse.ArgumentParser()
parser.add_argument('--repo', required=True)
parser.add_argument('--write', action='store_true')
parser.add_argument('--require-qualified', action='store_true')
args = parser.parse_args()
config = frozen_inputs()
protocol = load_json('PROTOCOL.json')
expected_cases = load_json('EXPECTED_CASES.json')
bindings = load_json('BINDINGS.json')
gaps = []
report = dict(source=config['source'], source_tree=config['source_tree'], validation=config['validation'],
    validation_tree=config['validation_tree'], run=config['run_id'], attempt=config['run_attempt'],
    audit_scope=config['scope'], external_independent_review=False, remaining_gates=gaps,
    official_games=0, official_seeds=0, precision_reserved=2, competitive_admission=False,
    full_token_entry_admission=False, registry_reexecution=False)


def check(condition, message):
    if not condition:
        gaps.append(message)
    return bool(condition)


def guarded(name, fn):
    try:
        return fn()
    except Exception as error:
        gaps.append(name + ': ' + type(error).__name__ + ': ' + str(error))
        return None


source_audit = guarded('Immutable Git source audit', lambda: audit(args.repo, config))
if source_audit is not None:
    report['git_source_audit'] = source_audit
    stored_source_audit = guarded('Stored Git source audit', lambda: load_json('GIT_SOURCE_AUDIT.json'))
    check(stored_source_audit == source_audit, 'Stored Git source audit differs/missing')


def receive_run():
    run = load_json('RUN.json')
    for field, value in [('id', config['run_id']), ('head_sha', config['validation']),
                         ('run_number', 1), ('run_attempt', 1), ('event', 'push'),
                         ('head_branch', config['branch']), ('path', config['workflow'])]:
        check(run.get(field) == value, 'Run metadata mismatch: ' + field)
    check(run.get('status') == 'completed' and run.get('conclusion') == 'success', 'GitHub run not completed successfully')
    jobs = run.get('jobs', [])
    check(len(jobs) == 1, 'Expected exactly one job')
    for job in jobs:
        check(job.get('id') == config['job_id'], 'Actual job ID differs from observed run job')
        check(job.get('run_id', config['run_id']) == config['run_id'], 'Job run identity mismatch')
        check(job.get('status') == 'completed' and job.get('conclusion') == 'success', 'GitHub job not completed successfully')
        check(bool(job.get('steps')), 'Job step results missing')
        for step in job.get('steps', []):
            check(step.get('status') == 'completed' and step.get('conclusion') in ('success', 'skipped'),
                  'Workflow step failed/incomplete: ' + str(step.get('name')))
    return run


report['run_metadata'] = guarded('Run metadata', receive_run)


def receive_history():
    history = load_json('ALL_RUNS.json')
    runs = history['workflow_runs']
    check(history['total_count'] == len(runs), 'Run inventory not complete')
    check(len({run['id'] for run in runs}) == len(runs), 'Run inventory has duplicates')
    check(all(run['head_sha'] == config['validation'] for run in runs), 'Run inventory source mismatch')
    check(config['run_id'] in {run['id'] for run in runs}, 'Original run absent from inventory')
    duplicates = load_json('DUPLICATE_RUNS.json')
    check({item['id'] for item in duplicates} == {run['id'] for run in runs} - {config['run_id']}, 'Duplicate run inventory mismatch')
    check(len(duplicates) == len({item['id'] for item in duplicates}), 'Duplicate detail inventory repeats run')
    for item in duplicates:
        check(item['head_sha'] == config['validation'] and item['run_number'] > 1 and item['run_attempt'] == 1,
              'Unexpected secondary run identity: ' + str(item['id']))
        jobs = item.get('jobs', [])
        check(item.get('status') == 'completed' and item.get('conclusion') == 'skipped'
              and len(jobs) == 1 and jobs[0].get('conclusion') == 'skipped' and not jobs[0].get('steps'),
              'Duplicate lacks verified skipped disposition: ' + str(item['id']))
    return dict(all_runs=history, duplicates=duplicates)


report['execution_inventory'] = guarded('Execution inventory', receive_history)
opened = guarded('Original artifact integrity', lambda: artifact(config))
if opened is not None:
    meta, archive, names, inventory = opened
    report['artifact'] = dict(id=meta['id'], bytes=meta['size_in_bytes'], sha256=meta['digest'][7:],
        payload_entries=len(inventory), payload_inventory=inventory)

    def receive_bindings():
        observed = {}
        for mode in ['fixed', 'negative']:
            name = 'binding-' + mode + '.json'
            if not check(name in names, 'Missing ' + mode + ' source binding'):
                continue
            value = json.loads(archive.read(name))
            observed[mode] = value
            for field, expected in [('source', config['source']), ('source_tree', config['source_tree']),
                ('validation', config['validation']), ('validation_tree', config['validation_tree']),
                ('baseline', config['baseline']), ('mode', mode), ('source_manifest', protocol['source_manifest']),
                ('bindings', bindings), ('goldens_verified', 23), ('official_games', 0),
                ('official_seeds', 0), ('precision_reserved', 2), ('competitive_admission', False)]:
                check(value.get(field) == expected, mode + ' binding mismatch: ' + field)
        for filename in ['PROTOCOL.json', 'BINDINGS.json', 'EXPECTED_CASES.json', 'BASELINE_SKIPS.json',
                         'ORIGINAL_CASES.json', 'SUPPLEMENTAL_REVIEW.json', 'SOURCE_REVIEW.json', 'WORKFLOW.yml']:
            path = 'protocol/' + filename
            check(path in names and archive.read(path) == (ROOT / filename).read_bytes(), 'Artifact frozen input differs/missing: ' + filename)
        check('validation.txt' in names and archive.read('validation.txt').decode().strip() == config['validation'], 'Artifact validation identity missing/different')
        check('source-tree.txt' in names and archive.read('source-tree.txt').decode().strip() == config['source_tree'], 'Artifact source tree missing/different')
        check('final-tracked-changes.txt' in names and not archive.read('final-tracked-changes.txt').strip(), 'Final tracked changes missing/nonempty')
        if source_audit:
            check('source-delta.txt' in names and archive.read('source-delta.txt').decode() == source_audit['source_diff'], 'Artifact source delta differs/missing')
        check('toolchain/flock-version.txt' in names, 'Verified flock toolchain evidence missing')
        return observed

    report['bindings'] = guarded('Artifact source bindings', receive_bindings)
    eras = ['1993-1999', '2000-2002', '2003-2007', '2008-2016', '2017-2022', '2023', '2024', '2025', '2026']
    broad_modules = {'rules-engine'} | {'mtg-sets/' + era + '/tests' for era in eras}
    focused_modules = {'rules-engine', 'mtg-sdk', 'mtg-sets', 'mtg-sets/2023/tests', 'mtg-sets/2024/tests', 'mtg-sets/2025/tests'}
    ordinary_modules = broad_modules | {'ai', 'game-server', 'gym', 'gym-server', 'gym-trainer',
        'mtg-sdk', 'mtg-search', 'mtg-sets', 'mtgish-tooling', 'oracle-assay'}

    def receive_stage(stage):
        before = len(gaps)
        prefix = stage + '/'
        xml_names = sorted(name for name in names if name.startswith(prefix + 'junit/') and name.endswith('.xml'))
        modules, identities, classes, failures, skips = {}, set(), {}, [], []
        rc = int(archive.read(prefix + 'exit.txt').strip()) if prefix + 'exit.txt' in names else None
        tracked = archive.read(prefix + 'tracked-changes.txt').decode() if prefix + 'tracked-changes.txt' in names else None
        log = archive.read(prefix + 'gradle.log').decode(errors='replace') if prefix + 'gradle.log' in names else ''
        check(rc is not None, stage + ' exit/result missing; stage not completed')
        for name in xml_names:
            relative = name[len(prefix + 'junit/'):]
            if '/build/test-results/test/' not in relative:
                raise ValueError('Unexpected original XML path: ' + name)
            module = relative.split('/build/test-results/test/', 1)[0]
            suite = ET.fromstring(archive.read(name))
            cases = suite.findall('.//testcase')
            check(len(cases) == int(suite.get('tests')), 'XML row count: ' + name)
            row = modules.setdefault(module, dict(rows=0, passed=0, failures=0, errors=0, skipped=0, classes=0))
            row['rows'] += len(cases)
            row['classes'] += 1
            for label, tag in [('failures', 'failure'), ('errors', 'error'), ('skipped', 'skipped')]:
                count = sum(case.find(tag) is not None for case in cases)
                check(count == int(suite.get(label)), 'XML ' + label + ': ' + name)
                row[label] += count
            for case in cases:
                full = case.get('classname') + '::' + case.get('name')
                check((module, full) not in identities, stage + ' duplicate case: ' + full)
                identities.add((module, full))
                cls = case.get('classname').rsplit('.', 1)[-1]
                classes.setdefault(cls, []).append(full)
                identity = dict(classname=case.get('classname'), name=case.get('name'), xml=name)
                check(sum(case.find(tag) is not None for tag in ['skipped', 'failure', 'error']) <= 1, 'Multiple XML result tags: ' + full)
                if case.find('skipped') is not None:
                    skips.append(identity)
                elif case.find('failure') is not None or case.find('error') is not None:
                    error = case.find('failure')
                    if error is None:
                        error = case.find('error')
                    failures.append(dict(identity, type=error.get('type'), message=error.get('message'), detail=error.text))
                else:
                    row['passed'] += 1
        totals = {key: sum(row[key] for row in modules.values()) for key in ['rows', 'passed', 'failures', 'errors', 'skipped', 'classes']}
        classes = {key: sorted(value) for key, value in classes.items()}
        task_outcomes = [dict(task=match.group(1), outcome=match.group(2) or 'EXECUTED') for match in
            re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$', log, re.M)]
        result = dict(exit=rc, totals=totals, modules=modules, cases_by_class=classes, failures=failures,
            skips=skips, tracked_changes=tracked, gradle_task_outcomes=task_outcomes, original_xml_count=len(xml_names),
            gradle_log_sha256=digest(archive.read(prefix + 'gradle.log')) if prefix + 'gradle.log' in names else None)
        if check(prefix + 'SUMMARY.json' in names, stage + ' workflow summary missing'):
            summary = json.loads(archive.read(prefix + 'SUMMARY.json'))
            for field, expected in [('source', config['source']), ('source_tree', config['source_tree']),
                ('validation', config['validation']), ('run', str(config['run_id'])), ('attempt', '1'),
                ('totals', totals), ('cases_by_class', classes), ('gradle_task_outcomes', task_outcomes)]:
                check(summary.get(field) == expected, stage + ' workflow summary mismatch: ' + field)
            check(set(summary['modules']) == set(modules), stage + ' summary module scope mismatch')
            for module, counts in modules.items():
                check(all(summary['modules'].get(module, {}).get(key) == count for key, count in counts.items()), stage + ' summary module count mismatch: ' + module)
        if check(prefix + 'PREEXISTING_REPORTS.json' in names, stage + ' report freshness manifest missing'):
            previous = json.loads(archive.read(prefix + 'PREEXISTING_REPORTS.json'))
            archived_names = {prefix + 'preexisting-junit/' + entry['path'] for entry in previous}
            check(len(archived_names) == len(previous), stage + ' duplicate preexisting report identity')
            check(archived_names == {name for name in names if name.startswith(prefix + 'preexisting-junit/')},
                  stage + ' preexisting report archive inventory differs')
            for entry in previous:
                payload = archive.read(prefix + 'preexisting-junit/' + entry['path'])
                check(len(payload) == entry['bytes'] and digest(payload) == entry['sha256'], stage + ' preexisting report bytes differ')
        if stage == 'negative':
            check(totals == config['expected_negative'], 'Exact 70-control 44-failure/26-pass baseline count mismatch')
            check(set(modules) == {'rules-engine'}, 'Negative module scope mismatch')
            check(rc is not None and rc != 0, 'Negative process result must be nonzero')
            changed = {line[3:] for line in tracked.splitlines()} if tracked is not None else None
            check(changed == set(protocol['baseline_restore_paths']), 'Negative exact restored path set differs')
            expected = {case['classname'] + '::' + case['name']: case for case in protocol['cases']}
            observed = {full: 'pass' for values in classes.values() for full in values}
            locations = {}
            for failed in failures:
                full = failed['classname'] + '::' + failed['name']
                observed[full] = 'assertion_failure'
                spec = expected.get(full)
                check(spec is not None, 'Unexpected negative failure: ' + full)
                check(failed['type'] == 'org.opentest4j.AssertionFailedError', 'Negative failure is not an assertion: ' + full)
                if spec is not None:
                    frame = re.search(re.escape(spec['classname'].rsplit('.', 1)[-1]) + r'\.kt:(\d+)', failed['detail'] or '')
                    locations[full] = int(frame.group(1)) if frame else None
                    check(locations[full] == spec.get('expected_first_failure_line'), 'Negative original first assertion frame differs: ' + full)
            check(observed == {full: case['predicted_baseline'] for full, case in expected.items()}, 'Preregistered original/supplemental baseline verdict differs')
            result['first_failure_locations'] = locations
        else:
            check(rc == 0 and bool(modules) and totals['failures'] == totals['errors'] == 0, stage + ' positive execution not clean')
            check(tracked == '', stage + ' tracked source changes missing/nonempty')
            for cls, cases in expected_cases.items():
                if stage == 'broad' and cls in {'CardDefinitionSnapshotTest', 'CardLintTest', 'EntersAsCopyExceptionsSerializationTest'}:
                    continue
                check(classes.get(cls) == cases, stage + ' exact case identity drift: ' + cls)
            required = focused_modules if stage == 'focused' else broad_modules if stage == 'broad' else ordinary_modules
            check(set(modules) == required, stage + ' exact module scope differs')
            check(totals['passed'] == config['expected_positive'][stage], stage + ' passing count differs')
            check(totals['skipped'] == (43 if stage == 'ordinary' else 0), stage + ' skipped count differs')
            if stage == 'focused':
                check(set(classes) == set(expected_cases), 'Focused class scope differs')
            else:
                check(modules.get('rules-engine', {}).get('passed') == 3978, stage + ' engine case count differs')
            if stage == 'ordinary':
                check(modules.get('oracle-assay', {}).get('passed') == 568, 'Ordinary Assay count differs')
                check(modules.get('mtg-sdk', {}).get('passed') == 529, 'Ordinary SDK count differs')
                check(sorted(skip['classname'] + '::' + skip['name'] for skip in skips) == sorted(load_json('BASELINE_SKIPS.json')), 'Original 43 skip identities differ')
        tasks = {item['task']: item['outcome'] for item in task_outcomes}
        for module in modules:
            task = ':' + module.replace('/', ':') + ':test'
            check(tasks.get(task) in ({'EXECUTED', 'FAILED'} if stage == 'negative' else {'EXECUTED'}), stage + ' fresh executed task missing: ' + task)
        result['gate_satisfied'] = len(gaps) == before
        return result

    report['stages'] = {stage: guarded(stage + ' original reports', lambda stage=stage: receive_stage(stage))
                        for stage in ['negative', 'focused', 'broad', 'ordinary']}

    def receive_assay():
        result = {}
        for filename in ['build-exit.txt', 'gate-exit.txt', 'differential-exit.txt', 'corpus-capture-exit.txt']:
            path = 'assay/' + filename
            if check(path in names, 'Assay result missing: ' + filename):
                result[filename] = int(archive.read(path).strip())
                check(result[filename] == 0, 'Assay result nonzero: ' + filename)
        labels = ['Cards assayed', 'Faces', 'Ability lines', 'Round-trips byte-exact', 'Alternate spelling normalized',
            'Declined', 'Ambiguous — distinct readings', 'Print mismatch', 'Normalization not invertible',
            'Full inverse not reproduced', 'Redundant readings (same model)', 'Cards fully covered']
        gate = archive.read('assay/gate.log').decode() if 'assay/gate.log' in names else ''
        for label in labels:
            rows = [line.strip() for line in gate.splitlines() if line.strip().startswith(label)]
            if not check(len(rows) == 1, 'Assay metric missing/duplicate: ' + label):
                continue
            value = rows[0][len(label):].strip()
            result[label] = value
            number = int(re.match(r'[\s:]*([0-9]+)', value).group(1))
            if label == 'Cards assayed':
                check(number == 2000, 'Assay touchstone count differs')
            if label in labels[6:11]:
                check(number == 0, 'Assay required error metric nonzero: ' + label)
        corpus_paths = ['assay/oracle-bulk-sha256.txt', 'assay/oracle-bulk-bytes.txt', 'assay/oracle-bulk-corpus.jsonl.gz']
        if check(all(path in names for path in corpus_paths), 'Full original Assay corpus or identity missing'):
            corpus = archive.read(corpus_paths[2])
            expected_hash = archive.read(corpus_paths[0]).decode().split()[0]
            expected_bytes = int(archive.read(corpus_paths[1]).decode().split()[0])
            check(digest(corpus) == expected_hash and len(corpus) == expected_bytes, 'Raw Assay corpus integrity differs')
            result['corpus'] = dict(sha256=digest(corpus), bytes=len(corpus), retained=True)
        comparison = compare_differential(meta, archive, names)
        result['differential_comparison'] = comparison
        check(comparison['qualification'] == 'UNCHANGED_DIVERGENCES_RETAINED', 'Original 51 differential blocks not all retained unchanged')
        check(load_json('DIFFERENTIAL_COMPARISON.json') == comparison, 'Stored differential comparison differs')
        return result

    report['assay'] = guarded('Assay original evidence', receive_assay)

report['status'] = 'PAUSED_TOKEN_ENTRY_QUALIFIED_COMPONENT_ONLY' if not gaps else 'QUALIFICATION_INCOMPLETE'
report['next_authorized_step'] = ('Publish bounded original-artifact receipt on isolated branch; complete remaining runtime admission gates before any official games.'
    if not gaps else 'Preserve original artifact and precise failures. Diagnose the failed/unverified gates; no automatic rerun or component adoption.')
save_or_compare('RECEIVING_AUDIT.json', report, args.write)
print(json.dumps(dict(status=report['status'], artifact=report.get('artifact', {}).get('id'),
    totals={stage: value['totals'] for stage, value in report.get('stages', {}).items() if value}, remaining_gates=gaps), indent=2))
if args.require_qualified and gaps:
    raise SystemExit(1)
