#!/usr/bin/env python3
"""Read-only audit of the already completed one-use source08 software supplement."""
from pathlib import Path
import datetime as dt
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
SOURCE = '3a4f99a7653839506e96d19e6639f58d9e8c5ced'
TREE = '56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6'
CONTROL = 'c0124122528ca66c598cfc27a7d6c3f9bad45af8'
def sha(b):
    return hashlib.sha256(b).hexdigest()

def main():
    metadata = json.loads((ROOT / 'github-metadata.json').read_text())
    run = metadata['run']
    assert (run['id'], run['run_attempt'], run['head_sha'], run['event'], run['conclusion']) == (36278648943, 1, CONTROL, 'push', 'success')
    jobs = metadata['jobs']['jobs']
    assert len(jobs) == 1 and jobs[0]['id'] == 108506146945 and jobs[0]['conclusion'] == 'success'
    artifacts = metadata['artifacts']['artifacts']
    assert len(artifacts) == 1 and artifacts[0]['id'] == 10918016982
    archive = ROOT / 'original-artifact-10918016982.zip'
    raw = archive.read_bytes()
    assert len(raw) == artifacts[0]['size_in_bytes'] == 2317681
    assert sha(raw) == 'd97a0331a0de7f6490684c31a4f3cfed1800dcaff87290edddeea0c73fce691e'
    assert artifacts[0]['digest'] == 'sha256:' + sha(raw)
    report = {'schema': 'ferocity-source08-mtgish-fresh-original-audit-v1',
              'status': 'AUTHOR_RAW_AUDIT_PASS_REQUIRES_INDEPENDENT_REVIEW',
              'source': SOURCE, 'tree': TREE, 'control': CONTROL,
              'run': 36278648943, 'attempt': 1, 'job': 108506146945,
              'artifact': {'id': 10918016982, 'bytes': len(raw), 'sha256': sha(raw)},
              'members': [], 'suites': [], 'skips': []}
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist()) == len(set(z.namelist())) == 28
        assert z.testzip() is None
        for i in z.infolist():
            b = z.read(i)
            assert not i.filename.startswith('/') and '..' not in Path(i.filename).parts
            assert len(b) == i.file_size
            report['members'].append({'path': i.filename, 'bytes': len(b), 'sha256': sha(b), 'crc32': f'{i.CRC:08x}'})
        audit = json.loads(z.read('audit.json'))
        assert audit['source_head'] == SOURCE and audit['source_tree'] == TREE
        assert audit['control_head'] == CONTROL and audit['run_id'] == str(run['id']) and audit['run_attempt'] == '1'
        assert audit['status'] == 'PASS_REQUIRES_ARTIFACT_REVIEW' and not audit['errors']
        expected_control = {
            '.github/workflows/ferocity-source08-mtgish-freshness.yml': 'f6e18353ec06f497ab2132d5e4213b91b2e044ded1459f3f710c06605404e66c',
            'existing-mtgish-case-identities.json': '1b898d07158fd594ff198d0732801504f0f661c9f0fbbc5953856e17beb99fb6',
            'ferocity-mtgish-freshness.py': 'cedc03f3e6e9328fea8c9b21073ddbcf29bcaea973c0f16441b7deba6f1434e8'}
        assert audit['control_files_sha256'] == expected_control
        for path, digest in expected_control.items():
            assert sha(z.read('control/' + path)) == digest
            assert z.read('control/' + path) == (ROOT.parent / 'mtgish-fresh' / Path(path).name).read_bytes()
        freeze = json.loads(z.read('control/existing-mtgish-case-identities.json'))
        expected = {row['class']: sorted(row['cases'], key=lambda c: c['name']) for row in freeze['rows']}
        assert len(expected) == 20 and sum(len(c) for c in expected.values()) == 147
        assert freeze['source'] == SOURCE and freeze['merge_tree'] == TREE
        before = z.read('executable-and-read-inputs-before.json')
        after = z.read('executable-and-read-inputs-after.json')
        assert before == after and sha(before) == audit['input_map_sha256']
        before_map = json.loads(before)
        assert len(before_map) == 22743
        assert all(re.fullmatch('[0-9a-f]{64}', v) for v in before_map.values())
        for p, digest in audit['dependencies_sha256'].items():
            assert before_map[p] == digest
        report['input_maps'] = {'entries': len(before_map), 'before_sha256': sha(before), 'after_sha256': sha(after),
                                'scope': 'Runtime-emitted complete tracked executable and read-input maps equal; not an independent refetch of every source body.'}
        assert len(audit['stages']) == 1
        stage = audit['stages'][0]
        command = json.loads(z.read('mtgish-tooling/command.json'))
        for key in ['command', 'started_ns', 'finished_ns', 'exit_status', 'owned_process_group']:
            assert stage[key] == command[key]
        assert command['exit_status'] == 0 and command['owned_process_group'] == 2712
        assert command['finished_ns'] - command['started_ns'] < 1200 * 10**9
        assert '--rerun' in command['command'] and '--no-build-cache' in command['command']
        assert command['command'][:3] == ['just', 'test-class', 'AsPermanentEntersCounterTest']
        observed = {}
        for name in sorted(n for n in z.namelist() if n.endswith('.xml')):
            b = z.read(name)
            xml = ET.fromstring(b)
            identity = xml.attrib['name']
            assert identity not in observed and not identity.startswith('Gradle Test Run ')
            assert not xml.findall('.//failure') and not xml.findall('.//error')
            assert int(xml.attrib['failures']) == int(xml.attrib['errors']) == 0
            cases = [{'name': c.attrib['name'], 'skipped': c.find('skipped') is not None} for c in xml.findall('testcase')]
            assert len(cases) == int(xml.attrib['tests'])
            assert sum(c['skipped'] for c in cases) == int(xml.attrib['skipped'])
            observed[identity] = sorted(cases, key=lambda c: c['name'])
            timestamp = dt.datetime.fromisoformat(xml.attrib['timestamp'].replace('Z', '+00:00')).timestamp()
            assert command['started_ns'] / 1e9 - 2 <= timestamp <= command['finished_ns'] / 1e9 + 2
            row = {'class': identity, 'cases': len(cases), 'skipped': sum(c['skipped'] for c in cases), 'sha256': sha(b)}
            assert row in stage['suites']
            report['suites'].append(dict(row, timestamp=xml.attrib['timestamp']))
            report['skips'].extend({'class': identity, 'name': c['name']} for c in cases if c['skipped'])
        assert observed == expected
        assert len(report['skips']) == 3
        log = z.read('mtgish-tooling/command.log').decode()
        assert '> Task :mtgish-tooling:test\n' in log
        assert not any('> Task :mtgish-tooling:test ' + suffix in log for suffix in ['FROM-CACHE', 'UP-TO-DATE', 'NO-SOURCE', 'SKIPPED'])
        assert log.count('Gradle Test Executor 1 started executing tests.') == 1
        assert log.count('Gradle Test Executor 1 finished executing tests.') == 1
        assert "Starting process 'Gradle Test Executor 1'" in log and 'BUILD SUCCESSFUL in 1m 13s' in log
        worker = next(line for line in log.splitlines() if line.startswith("Starting process 'Gradle Test Executor 1'"))
        assert '/temurin-21-jdk-amd64/bin/java ' in worker
        report['freshness'] = {'command': command, 'actual_test_task': ':mtgish-tooling:test', 'worker_start_finish_pairs': 1,
                               'worker_command': worker, 'command_log_sha256': sha(log.encode()), 'reporting_markers': 0}
    decoded = gzip.decompress((ROOT / 'decoded-job-108506146945.log.gz').read_bytes()).decode()
    assert '[command]/usr/bin/git checkout --progress --force ' + SOURCE in decoded
    assert '[command]/usr/bin/git checkout --progress --force ' + CONTROL in decoded
    assert 'SHA256 digest of uploaded artifact is ' + sha(raw) in decoded
    report['decoded_job_log'] = {'bytes': len(decoded.encode()), 'sha256': sha(decoded.encode()), 'encoding': 'connector-decoded UTF-8; gzip transport preserved'}
    report['results'] = {'classes': 20, 'actual_cases': 147, 'passed': 144, 'skipped': 3, 'failures': 0, 'errors': 0,
                         'original_cached_names_and_skips_unchanged': True}
    report['scope_limit'] = 'Only the source08 cached mtgish147 software freshness gap. No source08 complete runtime/gameplay admission, no card export, no resource calibration authority, no new seeds/allocations/games.'
    (ROOT / 'raw-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report['results']))

if __name__ == '__main__':
    main()
