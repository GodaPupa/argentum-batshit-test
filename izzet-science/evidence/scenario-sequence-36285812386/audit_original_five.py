#!/usr/bin/env python3
"""Read-only audit of the sole five-case qualification's original retained ZIP."""
from pathlib import Path
import base64
import datetime as dt
import hashlib
import io
import json
import zipfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
WORK = Path.cwd()
ARCHIVE = next((WORK / 'attachments').rglob('izzet-five-sequence-original-10920303876.zip'), HERE / 'original-artifact-10920303876.zip')

def input_bytes(name):
    path = HERE / name
    if path.exists():
        return path.read_bytes()
    with zipfile.ZipFile(HERE / 'audit-inputs.zip') as archive:
        return archive.read(name)

sha = lambda raw: hashlib.sha256(raw).hexdigest()
gitsha = lambda raw: hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
metadata = json.loads(input_bytes('completed-metadata.json').decode())
live = {r['path']: json.loads(r['result']['structuredContent']['content']) for r in metadata['results']}
run = live['actions/runs/36285812386']
jobs = live['actions/runs/36285812386/jobs?filter=all&per_page=100']['jobs']
artifacts = live['actions/runs/36285812386/artifacts?per_page=100']['artifacts']
assert run['status'] == 'completed' and run['conclusion'] == 'success' and run['run_attempt'] == 1
assert run['event'] == 'push' and run['head_sha'] == '58802ba41a246cfd6acfbb4cd6298daa61066ed4'
assert len(jobs) == 1 and jobs[0]['conclusion'] == 'success'
assert len(artifacts) == 1 and artifacts[0]['id'] == 10920303876
raw_zip = ARCHIVE.read_bytes()
assert len(raw_zip) == artifacts[0]['size_in_bytes'] == 259224
assert sha(raw_zip) == artifacts[0]['digest'].removeprefix('sha256:') == 'a061f984897a77464c98b4ed3e0b9e6325d02edc562314a7bd4b8ff6379acacf'

with zipfile.ZipFile(io.BytesIO(raw_zip)) as z:
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist())) == 14
    members = [{'path': n, 'bytes': len(z.read(n)), 'sha256': sha(z.read(n))} for n in z.namelist()]
    audit = json.loads(z.read('audit.json'))
    gate = json.loads(z.read('control/gate.json'))
    before = json.loads(z.read('source-before.json'))
    after = json.loads(z.read('source-after.json'))
    assert before == after == audit['source_before'] == audit['source_after'] and before['status'] == ''
    assert before['head'] == audit['source_head'] == 'af8685ec97fd0f56195f25e7d3ca83c609fa018a'
    assert before['tree'] == audit['source_tree'] == 'b46e165748abf32b2c8856052e5df3d8ffa910f9'
    assert live['git/commits/' + before['head']]['tree']['sha'] == before['tree']
    assert audit['control_head'] == run['head_sha']
    assert live['git/commits/' + audit['control_head']]['tree']['sha'] == '99c8c9b409b6dbd08475f6f73fb4e7bca9a9d817'
    assert audit['status'] == 'PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW' and not audit['errors']
    assert audit['official_games'] == audit['official_seeds'] == audit['new_pilot_cases'] == 0
    assert not audit['gameplay_authorized'] and not audit['full_runtime_accepted']
    controls = {}
    for name in ['qualify.py', 'gate.json', '.github/workflows/izzet-scenario-sequence-qualification.yml']:
        raw = z.read('control/' + name)
        local = HERE / 'final-release' / name.rsplit('/', 1)[-1]
        assert raw == input_bytes('final-release/' + name.rsplit('/', 1)[-1]) and sha(raw) == audit['control_files_sha256'][name]
        controls[name] = {'sha256': sha(raw), 'git_blob': gitsha(raw)}
    unready = dict(gate, ready_for_execution=False, source_review=None)
    assert (json.dumps(unready, indent=2) + '\n').encode() == input_bytes('gate.json')
    assert gate['ready_for_execution'] is True

    source_bodies = {}
    for item in json.loads(input_bytes('completed-source-pins.json').decode())['results']:
        result = item['result']
        if result.get('isError'):
            assert item['path'] == 'gradle/wrapper/gradle-wrapper.jar'
            continue
        data = result['structuredContent']
        body = base64.b64decode(data['content']) if data.get('encoding') == 'base64' else data['content'].encode()
        assert gitsha(body) == data['sha']
        source_bodies[item['path']] = {'sha256': sha(body), 'git_blob': data['sha'], 'provenance': 'fresh immutable source file fetch'}
    recovery = json.loads(input_bytes('recovered-wrapper-source.json').decode())
    wrapper = base64.b64decode(recovery['content'])
    assert gitsha(wrapper) == recovery['git_blob'] == 'b1b8ef56b44f16b14dc800fa8103a6d89abb526f'
    assert sha(wrapper) == recovery['sha256'] and len(wrapper) == recovery['bytes']
    inherited_tree = json.loads(input_bytes('root-current-pin-tree.json')); assert inherited_tree['source'] == before['tree']
    assert next(r['sha'] for r in inherited_tree['files'] if r['path'] == 'gradle/wrapper/gradle-wrapper.jar') == gitsha(wrapper)
    source_bodies['gradle/wrapper/gradle-wrapper.jar'] = {
        'sha256': sha(wrapper), 'git_blob': gitsha(wrapper),
        'provenance': 'Recovered exact current Git blob from an existing historical worktree file; not a fresh binary fetch',
        'recovered_path': recovery['recovery_path'],
        'retained_recovery_record_sha256': sha(input_bytes('recovered-wrapper-source.json')),
        'current_tree_binding': 'Root independent fresh source-tree path inventory binds recovered wrapper to exact af tree'}
    assert len(source_bodies) == 20
    for path, digest in before['authority_sha256'].items():
        assert source_bodies[path]['sha256'] == digest == gate['preserved_authority_sha256'][path]
    for path, blob in before['test_source_git_blobs'].items():
        assert source_bodies[path]['git_blob'] == blob
    for path, digest in audit['dependency_files_sha256'].items():
        assert source_bodies[path]['sha256'] == digest

    stages = []
    identities = []
    for index, bank in enumerate(gate['banks'], 1):
        folder = f"{index:02d}-{bank['stage']}"
        command = json.loads(z.read(folder + '/command.json'))
        assert command == audit['stages'][index - 1]
        assert command['exit_status'] == int(z.read(folder + '/exit-status.txt')) == 0
        assert command['technical_limit'] is None and command['output_complete'] is True
        assert command['discarded_observed_pipe_bytes'] == 0
        assert command['observed_report_bytes_after_exit'] <= gate['resource_limits']['test_result_bytes']
        xml = z.read(folder + '/TEST-' + bank['class'] + '.xml')
        suite = ET.fromstring(xml)
        cases = suite.findall('testcase')
        names = [c.attrib['name'] for c in cases]
        assert suite.attrib['name'] == bank['class']
        assert int(suite.attrib['tests']) == len(cases) == bank['expected_cases']
        assert len(names) == len(set(names)) and sorted(names) == sorted(bank['case_names'])
        assert all(c.attrib['classname'] == bank['class'] for c in cases)
        assert not any(int(suite.attrib.get(x, '0')) for x in ['failures', 'errors', 'skipped'])
        assert not any(suite.findall('.//' + x) for x in ['failure', 'error', 'skipped'])
        timestamp = dt.datetime.fromisoformat(suite.attrib['timestamp'].replace('Z', '+00:00'))
        if timestamp.tzinfo is None:
            timestamp = timestamp.replace(tzinfo=dt.timezone.utc)
        assert command['started_ns']/1e9 - 2 <= timestamp.timestamp() <= command['finished_ns']/1e9 + 2
        raw_log = z.read(folder + '/command.log')
        assert len(raw_log) == command['stdout_retained_bytes'] <= gate['resource_limits']['stdout_bytes']
        lines = raw_log.decode().splitlines()
        task = '> Task :' + bank['module'].replace('/', ':') + ':test'
        assert sum(line.strip() == task for line in lines) >= 1
        assert not any(line.strip() == task + ' ' + suffix for suffix in ['FROM-CACHE','UP-TO-DATE','NO-SOURCE','SKIPPED'] for line in lines)
        starts = [line for line in lines if 'Gradle Test Executor' in line and 'started executing tests' in line]
        finishes = [line for line in lines if 'Gradle Test Executor' in line and 'finished executing tests' in line]
        assert len(starts) == len(finishes) == 1 and any('BUILD SUCCESSFUL' in line for line in lines)
        assert command['command'][:2] == ['just','test-class'] and all(flag in command['command'] for flag in ['--rerun','--no-build-cache','--no-daemon','-DupdateSnapshots=false'])
        assert command['xml_retention'][0]['complete'] and command['xml_retention'][0]['sha256'] == sha(xml)
        identities.extend((bank['class'], name) for name in names)
        stages.append({'class': bank['class'], 'cases': len(cases), 'case_names': names, 'exit': 0,
                       'xml_sha256': sha(xml), 'xml_timestamp': suite.attrib['timestamp'],
                       'log_bytes': len(raw_log), 'log_sha256': sha(raw_log),
                       'required_fresh_task': task, 'task_heading_occurrences': sum(line.strip() == task for line in lines), 'fresh_worker_start': starts[0], 'fresh_worker_finish': finishes[0]})
    assert len(identities) == len(set(identities)) == 5

report = {'schema': 'izzet-five-original-sequence-qualification-author-raw-audit-v1',
          'status': 'PASS_FIVE_CASE_COMPONENT_REQUIRES_INDEPENDENT_REVIEW',
          'artifact_id': 10920303876, 'original_archive': str(ARCHIVE.relative_to(WORK)),
          'archive_bytes': len(raw_zip), 'archive_sha256': sha(raw_zip), 'members': members,
          'run_id': run['id'], 'attempt': run['run_attempt'], 'event': run['event'],
          'job_id': jobs[0]['id'], 'completed_at': jobs[0]['completed_at'],
          'source': before, 'controls': controls, 'source_and_dependency_pins': source_bodies,
          'stages': stages, 'counts': {'classes': 2, 'actual_cases': 5, 'passed': 5, 'failed': 0, 'errors': 0, 'skipped': 0, 'fresh_worker_pairs': 2},
          'limitations': ['Original failed973 and six-case artifacts remain unchanged; corrected test source is prospective and explicitly reviewed',
                         'No baseline/golden result,973 rerun,fullCI,whole-runtime or pilot admission',
                         'No official seeds,allocations,games,newpilotcases or deck-performance evidence'],
          'audit_script_sha256': sha(Path(__file__).read_bytes()), 'official_games': 0}
(HERE / 'original-five-author-audit.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'status': report['status'], 'counts': report['counts'], 'source_pins': len(source_bodies), 'archive_sha256': report['archive_sha256']}))
