"""Synthetic pure-readiness proof controls. Never invokes run_hosted or project tests."""
from pathlib import Path
from datetime import datetime, timezone
import copy
import hashlib
import importlib.util
import json
import tempfile
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
REPO = HERE.parent / 'manual-transmission-clean-room'
RECORD = 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
spec = importlib.util.spec_from_file_location('_synthetic_readiness_audit', HERE / 'hosted_runtime.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def fixture(folder):
    c = json.loads((HERE / 'READINESS_CONTRACT.json').read_bytes())
    # The bytes below are a synthetic fixture, never the deployed readiness contract.
    agent = b'SYNTHETIC_AGENT_BYTES_FOR_READ_ONLY_AUDITOR_CONTROLS\n'
    c['agent'].update(bytes=len(agent), sha256=module.sha(agent))
    module.stamp(folder / 'READINESS_CONTRACT.json', c)
    pin_bytes = (REPO / RECORD / 'CANDIDATE_PINS.json').read_bytes()
    pins = json.loads(pin_bytes)
    q = dict(source_tree='synthetic-source-tree', readiness_contract_sha256=module.sha((folder / 'READINESS_CONTRACT.json').read_bytes()),
             readiness_helper_sha256=module.sha((HERE / 'hosted_runtime.py').read_bytes()), candidate_pins_blob=module.blob(pin_bytes))
    e = dict(event='synthetic-event', source='synthetic-source', validation='synthetic-validation', run='101', job_id=202, qualification=q)
    src = folder / 'readiness'
    src.mkdir()
    (src / 'CANDIDATE_PINS.json').write_bytes(pin_bytes)
    module.stamp(src / 'SOURCE_STATE.json', dict(before=pins['bindings'], after=pins['bindings'], tracked_clean_before=True,
        tracked_clean_after=True, test_sources=c['test_sources'],
        expected_bindings_sha256=module.sha(json.dumps(pins['bindings'], sort_keys=True, separators=(',', ':')).encode())))
    xml_dir = folder / c['readiness_xml_directory']
    xml_dir.mkdir(parents=True)
    for klass in c['selectors']:
        suite = ET.Element('testsuite', name=klass)
        for identity in c['expected_identities']:
            _, k, name = identity.split('\t')
            if k == klass:
                ET.SubElement(suite, 'testcase', classname=klass, name=name)
        (xml_dir / ('TEST-' + klass + '.xml')).write_bytes(ET.tostring(suite))
    toolchain = dict(runner_environment='github-hosted', runner_os=c['runner_os'], runner_arch=c['runner_arch'],
        java_runtime_version=c['jdk_runtime_version'], java_implementor='Eclipse Adoptium', java_executable='/synthetic/jdk/bin/java',
        node_version=c['node_version'], npm_version=c['npm_version'], gradle_version=c['gradle_version'],
        agent=dict(path='/synthetic/agent.jar', bytes=len(agent), sha256=module.sha(agent)),
        docker_version_returncode=0, docker_info_returncode=0)
    module.stamp(folder / 'HOST_TOOLCHAIN.json', toolchain)
    (folder / 'host').mkdir()
    (folder / 'host' / 'byte-buddy-agent-1.18.10.jar').write_bytes(agent)
    (src / 'gradle.stdout').write_text('Welcome to Gradle 9.6.1!\n')
    settings = dict(gradle_properties=c['host_gradle_properties'], JAVA_TOOL_OPTIONS='-javaagent:/synthetic/agent.jar',
        GRADLE_USER_HOME='/synthetic/gradle', GRADLE_LOCK_SLOTS='1', GRADLE_OPTS='-Dorg.gradle.daemon=false',
        KOTEST_INCLUDE_PATTERN=None, UPDATE_SNAPSHOTS=None)
    module.stamp(folder / 'HOST_SETTINGS.json', settings)
    module.stamp(src / 'TEST_WORKERS.json', [dict(java_tool_options=settings['JAVA_TOOL_OPTIONS'], gradle_user_home=settings['GRADLE_USER_HOME'],
        java_executable=toolchain['java_executable'], test_executor_label='Gradle Test Executor 1',
        argv=['worker.org.gradle.process.internal.worker.GradleWorkerMain', "'Gradle Test Executor 1'"])])
    times = ['2026-11-25T20:00:00+00:00', '2026-11-25T20:00:01+00:00', '2026-11-25T20:00:02+00:00', '2026-11-25T20:00:03+00:00']
    settings_sha = module.sha((folder / 'HOST_SETTINGS.json').read_bytes())
    module.stamp(src / 'COMMAND.json', dict(argv=c['gradle_argv'], returncode=0, timed_out=False,
        started_utc=times[1], ended_utc=times[2], host_settings_sha256=settings_sha))
    parsed = module.parse_xml(folder, c)
    receipt = dict(schema='manual-transmission/hosted-readiness/v1', **module.bindings(e), state='PASS',
        readiness_sequence=1, gradle_invocations=1, contract_sha256=q['readiness_contract_sha256'], helper_sha256=q['readiness_helper_sha256'],
        source_bindings_verified=378, source_restored=True, gradle_returncode=0, summary=parsed['summary'], xml_sha256=parsed['xml_sha256'],
        expected_bindings_sha256=module.read(src / 'SOURCE_STATE.json')['expected_bindings_sha256'],
        toolchain_sha256=module.sha((folder / 'HOST_TOOLCHAIN.json').read_bytes()), host_settings_sha256=settings_sha,
        started_utc=times[0], ended_utc=times[3])
    module.stamp(folder / 'HOSTED_READINESS.json', receipt)
    return c, e


def change_json(folder, path, update):
    obj = module.read(folder / path)
    update(obj)
    module.stamp(folder / path, obj)


def change_xml(folder, contract, action):
    path = next((folder / contract['readiness_xml_directory']).glob('TEST-*.xml'))
    root = ET.fromstring(path.read_bytes())
    action(root)
    path.write_bytes(ET.tostring(root))
    parsed = module.parse_xml(folder, contract)
    change_json(folder, 'HOSTED_READINESS.json', lambda x: x.update(summary=parsed['summary'], xml_sha256=parsed['xml_sha256']))


def forged_binding_table(folder, contract):
    state = module.read(folder / 'readiness/SOURCE_STATE.json')
    first = next(iter(state['before']))
    state['before'][first]['sha256'] = '0' * 64
    state['after'] = copy.deepcopy(state['before'])
    state['expected_bindings_sha256'] = module.sha(json.dumps(state['before'], sort_keys=True, separators=(',', ':')).encode())
    module.stamp(folder / 'readiness/SOURCE_STATE.json', state)
    change_json(folder, 'HOSTED_READINESS.json', lambda x: x.update(expected_bindings_sha256=state['expected_bindings_sha256']))


controls = [
    ('synthetic_consistent_readonly_fixture', None),
    ('wrong_run_id', lambda f,c: change_json(f, 'HOSTED_READINESS.json', lambda x:x.update(run_id=999))),
    ('wrong_job_id', lambda f,c: change_json(f, 'HOSTED_READINESS.json', lambda x:x.update(job_id=999))),
    ('wrong_source', lambda f,c: change_json(f, 'HOSTED_READINESS.json', lambda x:x.update(source='wrong'))),
    ('boolean_sequence', lambda f,c: change_json(f, 'HOSTED_READINESS.json', lambda x:x.update(readiness_sequence=True))),
    ('extra_sequence', lambda f,c: change_json(f, 'HOSTED_READINESS.json', lambda x:x.update(readiness_sequence=2))),
    ('missing_identity', lambda f,c: change_xml(f, c, lambda x:x.remove(x.find('testcase')))),
    ('duplicate_identity', lambda f,c: change_xml(f, c, lambda x:x.append(copy.deepcopy(x.find('testcase'))))),
    ('skipped_identity', lambda f,c: change_xml(f, c, lambda x:ET.SubElement(x.find('testcase'), 'skipped'))),
    ('failed_identity', lambda f,c: change_xml(f, c, lambda x:ET.SubElement(x.find('testcase'), 'failure'))),
    ('forged_self_consistent_binding_table', forged_binding_table),
    ('changed_agent_bytes', lambda f,c:(f/'host/byte-buddy-agent-1.18.10.jar').write_bytes(b'changed')),
    ('no_actual_worker', lambda f,c:module.stamp(f/'readiness/TEST_WORKERS.json', [])),
    ('wrong_worker_agent', lambda f,c:change_json(f,'readiness/TEST_WORKERS.json',lambda x:x[0].update(java_tool_options='-javaagent:wrong'))),
    ('generic_gradle_worker_not_test_executor', lambda f,c:change_json(f,'readiness/TEST_WORKERS.json',lambda x:x[0].update(argv=[module.WORKER_MAIN,"'Gradle Worker 1'"],test_executor_label='Gradle Worker 1'))),
    ('worker_main_without_label', lambda f,c:change_json(f,'readiness/TEST_WORKERS.json',lambda x:x[0].update(argv=[module.WORKER_MAIN]))),
    ('forged_test_executor_label', lambda f,c:change_json(f,'readiness/TEST_WORKERS.json',lambda x:x[0].update(test_executor_label='Gradle Test Executor 2'))),
    ('wrong_command', lambda f,c:change_json(f,'readiness/COMMAND.json',lambda x:x.update(argv=['true']))),
    ('failed_command', lambda f,c:change_json(f,'readiness/COMMAND.json',lambda x:x.update(returncode=1))),
    ('timeout_command', lambda f,c:change_json(f,'readiness/COMMAND.json',lambda x:x.update(timed_out=True))),
]
results = []
for name, mutate in controls:
    with tempfile.TemporaryDirectory(prefix='manual-readiness-synthetic-') as temporary:
        folder = Path(temporary)
        contract, envelope = fixture(folder)
        if mutate:
            mutate(folder, contract)
        before = {p.relative_to(folder).as_posix():module.sha(p.read_bytes()) for p in folder.rglob('*') if p.is_file()}
        try:
            answer = module.audit_readiness(folder, contract, envelope)
            accepted = answer['state'] == 'READINESS_AUDIT_PASS'
            reason = answer['state']
        except Exception as exc:
            accepted = False
            reason = str(exc)
        after = {p.relative_to(folder).as_posix():module.sha(p.read_bytes()) for p in folder.rglob('*') if p.is_file()}
        assert before == after, 'Auditor mutated synthetic evidence'
        assert accepted == (mutate is None), (name, reason)
        results.append(dict(name=name, expected='accept' if mutate is None else 'reject', accepted=accepted,
                            state='PASS', reason=reason, evidence_unchanged=True))
report = dict(schema='manual-transmission/hosted-readiness-auditor-controls/v1', state='PASS',
    created_utc=datetime.now(timezone.utc).isoformat(), synthetic_fixtures_only=True, actual_readiness_sequences=0,
    actual_project_tests=0, full_qualification_invocations=0, source_modified=False,
    helper_sha256=module.sha((HERE/'hosted_runtime.py').read_bytes()), controls=results)
module.stamp(HERE/'READINESS_AUDIT_CONTROLS.json', report)
print(json.dumps(dict(state=report['state'], controls=len(results), positive=1, rejected=len(results)-1,
    actual_readiness_sequences=0, full_qualification_invocations=0), indent=2))
