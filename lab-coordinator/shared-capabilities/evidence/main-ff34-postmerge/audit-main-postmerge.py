#!/usr/bin/env python3
"""Read-only audit of retained connector-decoded postmerge job logs.

This does not download or parse current JUnit artifacts. Case lines are log
observations, not independently reconstructed current XML case identities.
"""
import collections
import gzip
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
PRIOR = ROOT.parent / 'pr186-audit'
HEAD = 'ff34ac8fa2efbd41f233e877cb2908c2cab5929a'
PR_MERGE = 'fc5c443fc9d674d85dc1942a553319fee8b69d87'
TREE = '88967f3a9ba0479661f02150f82ddcc9a2066e42'

def read_json(path):
    return json.loads(path.read_text())

def sha(data):
    return hashlib.sha256(data).hexdigest()

def lines(raw):
    return [re.sub(r'^\ufeff?\d{4}-\d\d-\d\dT\S+\s', '', s)
            for s in raw.decode('utf-8').splitlines()]

def file_pin(path):
    data = path.read_bytes()
    return {'file': path.name, 'bytes': len(data), 'sha256': sha(data)}

live = [read_json(ROOT / f'live-{i}.json')['data'] for i in range(8)]
assert live[0]['sha'] == HEAD and live[1]['sha'] == PR_MERGE
assert live[0]['tree']['sha'] == live[1]['tree']['sha'] == TREE
all_jobs = []
for run_i, jobs_i, artifacts_i, expected_run, expected_jobs in [
        (2, 3, 4, 36279987737, 10), (5, 6, 7, 36279987814, 1)]:
    run, jobs, artifacts = live[run_i], live[jobs_i], live[artifacts_i]
    assert run['id'] == expected_run and run['head_sha'] == HEAD
    assert (run['event'], run['run_attempt'], run['status'], run['conclusion']) == ('push', 1, 'completed', 'success')
    assert jobs['total_count'] == len(jobs['jobs']) == expected_jobs
    assert artifacts['total_count'] == len(artifacts['artifacts'])
    for job in jobs['jobs']:
        assert job['head_sha'] == HEAD and job['run_attempt'] == 1
        assert job['status'] == 'completed' and job['conclusion'] == 'success'
        assert all(s['conclusion'] in ('success', 'skipped') for s in job['steps'])
    all_jobs += jobs['jobs']
job_by_id = {j['id']: j for j in all_jobs}
inventory = read_json(ROOT / 'decoded-log-inventory.json')
assert set(job_by_id) == {i['job_id'] for i in inventory}
audit_jobs = []
log_lines = {}
for item in inventory:
    compressed = (ROOT / Path(item['path']).name).read_bytes()
    raw = gzip.decompress(compressed)
    assert (len(compressed), sha(compressed)) == (item['gzip_bytes'], item['gzip_sha256'])
    assert (len(raw), sha(raw)) == (item['decoded_bytes'], item['decoded_sha256'])
    ls = lines(raw)
    log_lines[item['job_id']] = ls
    job = job_by_id[item['job_id']]
    checkouts = [ls[i + 1] for i, s in enumerate(ls[:-1]) if 'git log -1 --format=%H' in s]
    if job['name'] == 'backend':
        assert not checkouts and 'All backend test groups passed.' in ls
    else:
        assert checkouts == [HEAD], (item['job_id'], checkouts)
    tasks = collections.defaultdict(set)
    results = collections.Counter()
    case_lines = []
    for line in ls:
        task = re.fullmatch(r'> Task (\S+:test)(?: (FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED))?', line)
        if task:
            tasks[task[1]].add(task[2] or 'fresh-task-heading')
        result = re.search(r' >.* (PASSED|FAILED|SKIPPED)$', line)
        if result and not line.startswith('> Task '):
            results[result[1]] += 1
            case_lines.append(line)
    assert results['FAILED'] == 0
    assert not any('##[error]' in line or 'BUILD FAILED' in line for line in ls)
    successes = [s for s in ls if s.startswith('BUILD SUCCESSFUL')]
    if job['name'].startswith('test ('):
        assert len(successes) == 1
        assert all(states <= {'fresh-task-heading', 'FROM-CACHE'} for states in tasks.values())
    audit_jobs.append({
        'job_id': job['id'], 'name': job['name'], 'run_id': job['run_id'],
        'conclusion': job['conclusion'], 'started_at': job['started_at'], 'completed_at': job['completed_at'],
        'actual_checkout_sha': checkouts[0] if checkouts else None,
        'task_headings': {k: sorted(v) for k, v in sorted(tasks.items())},
        'test_result_log_lines': dict(results),
        'duplicate_result_text_lines': len(case_lines) - len(set(case_lines)),
        'build_success_lines': successes,
        'warnings': sorted(set(s for s in ls if '##[warning]' in s)),
        'preserved_nonfatal_stderr_or_cache_messages': [s for s in ls if
            'grep: write error: Broken pipe' in s or 'ReserveCacheError:' in s],
        'non_success_steps': [{'name': s['name'], 'conclusion': s['conclusion']}
                              for s in job['steps'] if s['conclusion'] != 'success'],
        'log': item,
    })
matrix = [j for j in audit_jobs if j['name'].startswith('test (')]
assert len(matrix) == 7
matrix_tasks = {k: v for j in matrix for k, v in j['task_headings'].items()}
assert len(matrix_tasks) == 19
cached = sorted(k for k, v in matrix_tasks.items() if v == ['FROM-CACHE'])
assert cached == [':mtg-search:test', ':mtgish-tooling:test']
fresh = sorted(k for k, v in matrix_tasks.items() if v == ['fresh-task-heading'])
assert len(fresh) == 17
totals = collections.Counter()
for job in matrix:
    totals.update(job['test_result_log_lines'])
assert totals == {'PASSED': 16987, 'SKIPPED': 49}
validation = next(j for j in audit_jobs if j['job_id'] == 108509861331)
assert validation['test_result_log_lines'] == {'PASSED': 17549, 'SKIPPED': 49}
assert validation['task_headings'][':oracle-assay:test'] == ['fresh-task-heading']
for task in (':gym:test', ':gym-trainer:test'):
    assert validation['task_headings'][task] == ['UP-TO-DATE', 'fresh-task-heading']
assert validation['task_headings'][':mtg-search:test'] == ['FROM-CACHE']
assert validation['task_headings'][':mtgish-tooling:test'] == ['FROM-CACHE']
assert len([v for v in validation['task_headings'].values() if v == ['NO-SOURCE']]) == 10
assert live[7]['total_count'] == 0 and live[4]['total_count'] == 7
frontend_patterns = ('npm warn deprecated recharts@', '6 vulnerabilities (2 moderate, 4 high)',
                     '(!) Some chunks are larger than 500 kB after minification.')
old_frontend_raw = gzip.decompress((PRIOR / 'decoded-job-108501665417.log.gz').read_bytes())
old_frontend = lines(old_frontend_raw)
front_warnings = [s for s in log_lines[108509861313] if any(s.startswith(p) for p in frontend_patterns)]
assert len(front_warnings) == 3
assert all(s in old_frontend for s in front_warnings)
coverage_lines = log_lines[108509861381]
assert coverage_lines.count('grep: write error: Broken pipe') == 2
assert 'shell: /usr/bin/bash -e {0}' in coverage_lines
cache_races = {job_id: [s for s in ls if 'ReserveCacheError:' in s]
               for job_id, ls in log_lines.items() if any('ReserveCacheError:' in s for s in ls)}
assert set(cache_races) == {108509861487, 108509861542}
workflow_sources = read_json(ROOT / 'workflow-source.json')
workflow_pins = []
for entry in workflow_sources:
    blob = entry['data']['content'].encode()
    git_sha = hashlib.sha1(b'blob ' + str(len(blob)).encode() + b'\0' + blob).hexdigest()
    assert git_sha == entry['data']['sha']
    workflow_pins.append({'path': entry['path'], 'git_blob': git_sha, 'sha256': sha(blob)})

report = {
    'schema': 'ARGENTUM_MAIN_POSTMERGE_DECODED_LOG_AUDIT_V1',
    'reviewer': '/root/manual',
    'disposition': 'PASS_BOUNDED_POSTMERGE_SOFTWARE_VALIDATION_LOG_AUDIT',
    'source': {'head': HEAD, 'tree': TREE, 'reviewed_pr186_merge': PR_MERGE,
               'entire_git_tree_equal': True, 'workflow_files': workflow_pins},
    'runs': [{k: live[i][k] for k in ('id', 'name', 'head_sha', 'event', 'run_attempt',
                                      'status', 'conclusion', 'created_at', 'updated_at', 'html_url')}
             for i in (2, 5)],
    'jobs': audit_jobs,
    'matrix_summary': {'groups': 7, 'distinct_declared_test_tasks': 19, 'fresh_tasks': fresh,
        'cached_tasks': cached, 'fresh_test_result_log_lines': dict(totals),
        'current_xml_downloaded': False,
        'current_xml_artifact_metadata': live[4]['artifacts']},
    'reuse': {
        'basis': 'Entire source tree ff34 equals independently reviewed PR186 actual merge fc5c, including all production, tests, build, dependencies, workflow and data inputs.',
        'durable_prior_evidence_commit': '04ee7c6b1394b62ca492fa3159f5e64c354e3bd1',
        'prior_independent_review_sha256': 'a962f859ef4e819405e441bd5d5994d42d6cfaf2627f72292bee68b5ad47024e',
        'prior_raw_xml_scope': 'PR186: 17346 actual cases, 17036 fresh (16987 pass,49 skip); 310 cached (307 pass,3 skip). Disabled CardImageUri Gradle marker is separate.',
        'fresh_supplement': 'Prior accepted identical-input source994383 run36271229739 supplies fresh coverage for cached mtg-search163 and mtgish147; this audit relies on that reviewed closure, and does not re-download it.',
        'not_asserted': 'Current XML bytes, timestamps or individual identities have not been downloaded/reconstructed here. Same-tree reuse does not make cached tasks fresh.'},
    'scope_differences': {
        'coverage': 'Main-only koverXmlReport ran successfully. Its overlapping test logs are not added to matrix case counts. Coverage XML/percentage was not independently downloaded.',
        'validation': 'Full Gradle test ran including oracle-assay; 17549 PASS and49 SKIP result lines, with two duplicate textual labels. No unique-case or XML count asserted. Ten aggregate NO-SOURCE era/core tasks do not substitute for actual fresh era :tests:test tasks. Subsequent named gym and gym-trainer commands were UP-TO-DATE.',
        'frontend': 'npm ci, typecheck and Vite build passed; no frontend unit/e2e test claim.',
        'validation_artifacts': 'Workflow uploads no artifacts; live artifact count is zero.'},
    'warnings': {
        'frontend_unchanged_from_pr186': front_warnings,
        'prior_frontend_log_decoded_sha256': sha(old_frontend_raw),
        'other': 'Gradle future-incompatibility and Java/Node deprecation warnings remain warnings. Validation older setup-java@v4 and Node20 action deprecations are visible; no failure or baseline-update authorization follows.',
        'coverage_stderr': 'New main-only coverage extraction emits two grep write-error Broken pipe messages at23:46:37. Exact workflow uses grep | head -1 twice, under observed bash -e without pipefail. Closing head after its first line explains upstream grep write failure (inference from source and stderr). The extraction step succeeded, but individual grep exits and the computed coverage value/XML are not exposed or independently certified. This is not evidence every subcommand exited zero, nor a test failure; preserve without altering unrelated source.',
        'cache_save_races': {'jobs': sorted(cache_races), 'scope': 'Gradle post-job transform-cache save reported ReserveCacheError while another job may create the same key. Other cache saves and jobs completed successfully. These are cache publication warnings, not test outcomes; original decoded messages retained.'},
        'snapshot': 'No failed snapshot/baseline assertion observed in decoded logs; workflow commands contain no snapshot-adoption command. No new before/after runtime-tree capture was produced, so wholesale runtime immutability is not newly certified.'},
    'limits': [
        'All retained logs are complete connector-decoded UTF8 compressed locally, not original GitHub log ZIPs.',
        'Current CI XML artifact contents not downloaded; metadata only. No current XML case-bank audit claimed.',
        'Software validation only; no project complete runtime admission, formal GitHub approval, gameplay permit, seed/claim allocation or outcome.',
        'No source mutation, workflow dispatch or rerun performed. No deck-performance evidence changed.'],
    'inputs': [file_pin(ROOT / f'live-{i}.json') for i in range(8)] +
              [file_pin(ROOT / 'workflow-source.json'), file_pin(ROOT / 'decoded-log-inventory.json')],
}
(ROOT / 'audit.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'audit': file_pin(ROOT / 'audit.json'), 'jobs': len(audit_jobs),
                  'fresh_matrix_result_lines': dict(totals), 'entire_source_tree_equal': True}))
