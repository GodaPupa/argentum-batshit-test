"""Receiver-owned independent recount of original R1 bytes, never candidate helpers."""
import io
import json
import re
import zipfile
import xml.etree.ElementTree as ET
from common import ROOT, digest, load_json


def original_reuse():
    plan, r1 = load_json('BASELINE_REUSE.json'), load_json('R1_PROTOCOL.json')
    meta, run = load_json('R1_ARTIFACT.json'), load_json('R1_RUN.json')
    body = (ROOT / 'R1_original-12497122147.zip').read_bytes()
    if len(body) != 103571 or digest(body) != '252e35406d499cc2b7b8d4f91f6b8ace6338f6034aadefbaa546c302e0f39ca6':
        raise ValueError('Original failed R1 ZIP integrity differs')
    if meta['id'] != 12497122147 or meta['size_in_bytes'] != len(body) or meta['digest'] != 'sha256:' + digest(body):
        raise ValueError('Original failed R1 artifact metadata differs')
    if run['id'] != 39943006929 or run['run_attempt'] != 1 or run['head_sha'] != plan['validation'] or run['conclusion'] != 'failure':
        raise ValueError('R1 failed execution identity differs')
    if len(run['jobs']) != 1 or run['jobs'][0]['id'] != 120378784251 or run['jobs'][0]['conclusion'] != 'failure':
        raise ValueError('R1 failed job identity differs')
    if load_json('R1_RECEIVING_AUDIT.json')['status'] != 'QUALIFICATION_INCOMPLETE':
        raise ValueError('R1 failed receiving disposition changed')
    z = zipfile.ZipFile(io.BytesIO(body))
    if z.testzip() is not None:
        raise ValueError('R1 ZIP CRC failure')
    names = z.namelist()
    if len(names) != len(set(names)):
        raise ValueError('R1 ZIP has duplicate entries')
    manifest = {}
    for line in z.read('SHA256SUMS').decode().splitlines():
        expected, name = line.split('  ', 1)
        if name in manifest or digest(z.read(name)) != expected:
            raise ValueError('R1 payload inventory differs: ' + name)
        manifest[name] = expected
    if set(names) != set(manifest) | {'SHA256SUMS'}:
        raise ValueError('R1 full payload inventory differs')
    for mode in ['fixed', 'negative']:
        binding = json.loads(z.read('binding-' + mode + '.json'))
        for field in ['source', 'source_tree', 'validation', 'validation_tree', 'baseline']:
            if binding[field] != plan[field]:
                raise ValueError('R1 binding differs: ' + mode + '/' + field)
        if binding['source_manifest'] != r1['source_manifest'] or binding['bindings'] != load_json('BINDINGS.json'):
            raise ValueError('R1 exact source/protected bindings differ')
        if binding['goldens_verified'] != 23 or binding['official_games'] != 0 or binding['official_seeds'] != 0:
            raise ValueError('R1 safeguard binding differs')
    tracked = {line[3:] for line in z.read('negative/tracked-changes.txt').decode().splitlines()}
    if tracked != set(r1['baseline_restore_paths']) or int(z.read('negative/exit.txt')) == 0:
        raise ValueError('R1 baseline restoration/process result differs')
    log = z.read('negative/gradle.log').decode(errors='replace')
    tasks = {match.group(1): match.group(2) or 'EXECUTED' for match in
        re.finditer(r'^> Task ([^\s]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?$', log, re.M)}
    execution = {task: tasks.get(task) for task in [':rules-engine:compileKotlin', ':rules-engine:compileTestFixturesKotlin', ':rules-engine:compileTestKotlin', ':rules-engine:test']}
    if list(execution.values()) != ['EXECUTED', 'EXECUTED', 'EXECUTED', 'FAILED']:
        raise ValueError('R1 compile/test tasks were not executed')
    previous = json.loads(z.read('negative/PREEXISTING_REPORTS.json'))
    previous_names = {'negative/preexisting-junit/' + item['path'] for item in previous}
    if len(previous_names) != len(previous) or previous_names != {name for name in names if name.startswith('negative/preexisting-junit/')}:
        raise ValueError('R1 freshness inventory differs')
    for item in previous:
        payload = z.read('negative/preexisting-junit/' + item['path'])
        if len(payload) != item['bytes'] or digest(payload) != item['sha256']:
            raise ValueError('R1 stale report bytes differ')
    expected = {case['classname'] + '::' + case['name']: case for case in plan['reused_cases']}
    original = {case['classname'] + '::' + case['name']: case for case in r1['cases']}
    corrections = {case['classname'] + '::' + case['name']: case for case in plan['frame_corrections']}
    if len(corrections) != 2:
        raise ValueError('Unexpected R1 frame correction count')
    for identity, case in expected.items():
        if case['predicted_baseline'] != original[identity]['predicted_baseline']:
            raise ValueError('Original reused verdict was reclassified: ' + identity)
        old_frame, frame = original[identity]['expected_first_failure_line'], case['expected_first_failure_line']
        if identity in corrections:
            correction = corrections[identity]
            if not (identity.split('::', 1)[1].startswith('PX01 ') and 'sourceCopy=false' in identity
                    and old_frame == correction['original_preregistered_first_frame'] == 81
                    and frame == correction['observed_original_first_frame'] == correction['r2_reused_first_frame'] == 80):
                raise ValueError('Frame correction scope differs: ' + identity)
        elif old_frame != frame:
            raise ValueError('Undisclosed first-frame reclassification: ' + identity)
    observed, retained, failures, classes = {}, {}, [], set()
    full = dict(rows=0, passed=0, assertion_failures=0, null_pointer_exceptions=0)
    for name in sorted(names):
        if not name.startswith('negative/junit/') or not name.endswith('.xml'):
            continue
        suite = ET.fromstring(z.read(name))
        cases = suite.findall('.//testcase')
        if len(cases) != int(suite.get('tests')):
            raise ValueError('R1 XML row count differs')
        for label in ['failures', 'errors', 'skipped']:
            tag = {'failures': 'failure', 'errors': 'error', 'skipped': 'skipped'}[label]
            if sum(case.find(tag) is not None for case in cases) != int(suite.get(label)):
                raise ValueError('R1 XML result count differs: ' + label)
        for case in cases:
            identity = case.get('classname') + '::' + case.get('name')
            if identity not in original or identity in observed or case.find('error') is not None or case.find('skipped') is not None:
                raise ValueError('R1 XML identity/error/skip differs: ' + identity)
            failure = case.find('failure')
            kind = 'pass' if failure is None else 'assertion_failure' if failure.get('type') == 'org.opentest4j.AssertionFailedError' else 'null_pointer_exception' if failure.get('type') == 'java.lang.NullPointerException' else 'unexpected_exception'
            if kind == 'unexpected_exception':
                raise ValueError('Unexpected original exception: ' + identity)
            observed[identity] = kind
            full['rows'] += 1
            full[{'pass': 'passed', 'assertion_failure': 'assertion_failures', 'null_pointer_exception': 'null_pointer_exceptions'}[kind]] += 1
            if identity not in expected:
                continue
            spec = expected[identity]
            if kind != spec['predicted_baseline']:
                raise ValueError('Reused56 verdict differs: ' + identity)
            frame = None
            if failure is not None:
                match = re.search(re.escape(spec['classname'].rsplit('.', 1)[-1]) + r'\.kt:(\d+)', failure.text or '')
                frame = int(match.group(1)) if match else None
                if frame != spec['expected_first_failure_line']:
                    raise ValueError('Reused56 first assertion frame differs: ' + identity)
                failures.append(dict(identity=identity, type=failure.get('type'), message=failure.get('message'), detail=failure.text, xml=name))
            retained[identity] = dict(verdict=kind, first_assertion_frame=frame, xml=name, xml_sha256=digest(z.read(name)))
            classes.add(spec['classname'])
    if set(observed) != set(original) or full != dict(rows=70, passed=22, assertion_failures=44, null_pointer_exceptions=4):
        raise ValueError('Original R1 failed result changed')
    totals = dict(rows=len(retained), passed=sum(case['verdict'] == 'pass' for case in retained.values()), failures=len(failures), errors=0, skipped=0, classes=len(classes))
    if set(retained) != set(expected) or totals != dict(rows=56, passed=14, failures=42, errors=0, skipped=0, classes=4):
        raise ValueError('Original reused56 scope differs')
    return dict(status='ORIGINAL56_VERIFIED_FOR_R2_REUSE_ONLY', r1_status='FAILED_UNADOPTED',
        receipt=plan['receipt'], artifact_id=meta['id'], artifact_bytes=len(body), artifact_sha256=digest(body),
        payload_inventory=manifest, r1_actual=full, reused_totals=totals, reused_cases=retained,
        original_failures=failures, original_compile_and_test_tasks=execution,
        original_preexisting_reports=previous, frame_corrections=plan['frame_corrections'], excluded_invalid14=True)


def receive_composite(archive, names, stages, check):
    result = original_reuse()
    for name in load_json('BASELINE_REUSE.json')['inputs']:
        path = 'baseline-reuse/' + name
        check(path in names and archive.read(path) == (ROOT / ('R1_' + name)).read_bytes(), 'R2 retained original R1 input differs/missing: ' + name)
    if check('baseline-reuse/REUSED56.json' in names, 'Workflow reused56 report missing'):
        observed = json.loads(archive.read('baseline-reuse/REUSED56.json'))
        for key in ['status', 'r1_status', 'reused_totals', 'reused_cases', 'r1_actual', 'original_compile_and_test_tasks', 'original_preexisting_reports', 'frame_corrections']:
            check(observed.get(key) == result[key], 'Workflow reused56 differs from independent recount: ' + key)
    negative = stages.get('negative')
    check(bool(negative and negative['gate_satisfied']), 'Fresh14 negative gate incomplete; composite cannot qualify')
    if negative:
        combined = {key: negative['totals'][key] + result['reused_totals'][key] for key in negative['totals']}
        check(combined == load_json('RECEIVER.json')['expected_composite'], 'Independent composite70 count differs')
        protocol = load_json('PROTOCOL.json')
        fresh = {case['classname'] + '::' + case['name'] for case in protocol['cases']}
        reused = set(result['reused_cases'])
        check(not fresh & reused and fresh | reused == {case['classname'] + '::' + case['name'] for case in protocol['composite_baseline_cases']}, 'Composite70 identity overlap/drift')
        result['fresh14'] = negative['totals']
        result['composite70'] = combined
        if check('negative/COMPOSITE_BASELINE.json' in names, 'Workflow composite receiving report missing'):
            recorded = json.loads(archive.read('negative/COMPOSITE_BASELINE.json'))
            config = load_json('RECEIVER.json')
            for key, expected in [('source', config['source']), ('source_tree', config['source_tree']),
                ('validation', config['validation']), ('fresh_run', str(config['run_id'])), ('fresh_attempt', '1'),
                ('fresh14', negative['totals']), ('reused56', result['reused_totals']), ('composite70', combined),
                ('r1_status', 'FAILED_UNADOPTED'), ('one_execution', False), ('official_games', 0),
                ('official_seeds', 0), ('external_independent_review', False), ('competitive_admission', False)]:
                check(recorded.get(key) == expected, 'Workflow composite differs: ' + key)
    result['one_execution'] = False
    return result
