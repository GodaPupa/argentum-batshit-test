"""Read-only byte/JSON preservation check; never imports or executes packet code."""
import hashlib
import json
import stat
import zipfile
from pathlib import Path

ROOT = Path('izzet-takeover/shared-receiving')
PACKET = ROOT / 'legacy-factory-recovery-20260927'
OUT = Path('sphinx-audit/izzet-held-recovery-review')

def digest(data):
    return hashlib.sha256(data).hexdigest()

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def identity(path):
    data = path.read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': digest(data)}

def check_archive(path, expected_sha, expected_size, local_root=None, index=None):
    raw = path.read_bytes()
    assert len(raw) == expected_size and digest(raw) == expected_sha
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        names = [v.filename for v in infos]
        assert len(names) == len(set(names))
        assert archive.testzip() is None
        assert all(not v.is_dir() and not stat.S_ISLNK(v.external_attr >> 16) for v in infos)
        assert all(not Path(n).is_absolute() and '..' not in Path(n).parts for n in names)
        manifest_bytes = archive.read('archive-manifest.json')
        manifest = json.loads(manifest_bytes)
        records = manifest['files']
        assert len(records) == len({r['path'] for r in records})
        assert set(names) == {r['path'] for r in records} | {'archive-manifest.json'}
        if index:
            assert records == index['records']
            assert digest(manifest_bytes) == index['manifest_sha256']
            assert len(names) == index['members']
            assert sum(r['bytes'] for r in records) == index['uncompressed_bytes']
        checked = []
        for rec in records:
            data = archive.read(rec['path'])
            local_path = (local_root / rec['path']) if local_root else Path(rec['path'])
            local_data = local_path.read_bytes()
            assert len(data) == rec['bytes'] and digest(data) == rec['sha256'], rec['path']
            assert data == local_data, rec['path']
            checked.append({**rec, 'git_blob_sha1': blob(data), 'local_path': str(local_path),
                            'local_bytes_identical': True, 'crc_verified': True})
        return {'archive': identity(path), 'members': len(names), 'payload_members': len(records),
                'all_member_uncompressed_bytes': sum(v.file_size for v in infos),
                'payload_uncompressed_bytes': sum(r['bytes'] for r in records),
                'manifest_sha256': digest(manifest_bytes), 'files': checked}

index = json.loads((PACKET / 'archive-index.json').read_bytes())
original = check_archive(PACKET / 'held-factory-preparation.zip',
                        '4276c2f631306991fff1c8d6104383656f73e648f653ae2c231e32933c9cc728',
                        496995, index=index)
successor = check_archive(PACKET / 'visibility-successor-held.zip',
                         '613ff0bebca9651304962760636abab14ff0583d2f9c1a19284a91e85192ff54',
                         32102, local_root=ROOT / 'legacy-factory-visibility-successor')
with zipfile.ZipFile(PACKET / 'held-factory-preparation.zip') as archive:
    prefix = 'izzet-takeover/shared-receiving/'
    load = lambda p: json.loads(archive.read(prefix + p))
    gate = load('legacy-factory-control/gate.json')
    control = load('legacy-factory-control/candidate-control-manifest.json')
    assert gate['ready_for_execution'] is False and gate['source_review'] is None
    assert control['source_refs_created'] is False and control['events_created'] == 0
    for name, expected in control['files'].items():
        data = archive.read(prefix + 'legacy-factory-control/' + name)
        assert len(data) == expected['bytes'] and digest(data) == expected['sha256']
    banks = gate['banks']
    executions = sum(len(b['case_names']) for b in banks)
    identities = {(b['class'], n) for b in banks for n in b['case_names']}
    assert executions == 11 and len(identities) == 9
    assert len(banks[0]['expected_failure_names']) == 1
    assert not banks[1]['expected_failure_names'] and not banks[2]['expected_failure_names']
    candidate = load('legacy-factory-candidate/candidate-manifest.json')
    prepared = load('legacy-factory-candidate/prepared-sources.json')
    metadata = load('legacy-factory-control/prepared-source-metadata.json')
    lookup = {r['path']: r['value'] for r in metadata}
    for role in ['before', 'fixed']:
        commit = lookup['git/commits/' + prepared[role]['commit']]
        assert commit['sha'] == prepared[role]['commit']
        assert commit['tree']['sha'] == prepared[role]['tree']
        assert gate['sources'][role]['head'] == commit['sha']
        assert gate['sources'][role]['tree'] == commit['tree']['sha']
    assert lookup['git/commits/' + prepared['before']['commit']]['parents'][0]['sha'] == prepared['base']
    assert lookup['git/commits/' + prepared['fixed']['commit']]['parents'][0]['sha'] == prepared['before']['commit']
    candidate_files = []
    for row in candidate['files']:
        data = archive.read(prefix + 'legacy-factory-candidate/candidate/' + row['path'])
        assert len(data) == row['bytes'] and digest(data) == row['sha256']
        assert blob(data) == row['git_blob'] == prepared['blobs'][row['path']]
        candidate_files.append(row)
    baseline = archive.read(prefix + 'legacy-factory-candidate/baseline/' + gate['production_path'])
    assert digest(baseline) == gate['sources']['before']['production_sha256']
    assert blob(baseline) == candidate['production_before_blob']
    source_review = archive.read('pest-takeover/izzet-factory-source-review/independent-source-review.json')
    assert digest(source_review) == prepared['source_review_sha256'] == gate['source_candidate_review_sha256']
    prior_collectors = []
    for version in ['superseded-685e-controls', 'superseded-0d95-without-semaphore',
                    'superseded-4c37-process-boundary', 'superseded-7493-proc-namespace-assumption']:
        data = archive.read(prefix + 'legacy-factory-control/' + version + '/qualify.py')
        prior_collectors.append({'version': version, 'bytes': len(data), 'sha256': digest(data)})
    initial_finding = load('legacy-factory-control/independent-initial-finding/initial-retention-finding.json')
    namespace_failure = load('legacy-factory-control/superseded-7493-proc-namespace-assumption/failed-selfcheck.json')
    assert initial_finding['results'][0]['snapshot_exists'] is False
    assert namespace_failure['observed_return']['process_group_cleanup']['quiescent'] is True
    guard_counts = {}
    for filename, expected in [('local-parser-guard-checks.json', 13),
                               ('local-source-retention-checks.json', 6),
                               ('local-owned-process-checks.json', 4)]:
        document = load('legacy-factory-control/' + filename)
        assert document['collector_sha256'] == control['files']['qualify.py']['sha256']
        assert len(document['checks']) == expected
        guard_counts[filename] = expected
    with zipfile.ZipFile(PACKET / 'visibility-successor-held.zip') as added:
        assert added.read('gate.json') == archive.read(prefix + 'legacy-factory-control/gate.json')
        assert added.read('izzet-legacy-factory-qualification.yml') == archive.read(prefix + 'legacy-factory-control/izzet-legacy-factory-qualification.yml')
        extra = json.loads(added.read('successor-manifest.json'))
        assert extra['parent_collector_sha256'] == control['files']['qualify.py']['sha256']
        assert digest(added.read('static-permission-observation-finding.json')) == extra['finding']
        for name, expected in extra['files'].items():
            data = added.read(name)
            assert len(data) == expected['bytes'] and digest(data) == expected['sha256']
        observation_guards = json.loads(added.read('local-visibility-guard-checks.json'))
        # Preserve author check evidence; never execute these checks in this review.
        assert len(observation_guards['checks']) == 10

report = {
    'scope': 'PRESERVATION_ONLY_NO_PACKET_CODE_IMPORTED_OR_EXECUTED',
    'original': original,
    'post_freeze_addendum': successor,
    'wrappers': [identity(PACKET / name) for name in ['archive-index.json', 'README.md']],
    'prepared_source_metadata_internally_consistent': prepared,
    'candidate_files_rehashed': candidate_files,
    'prior_collectors_preserved': prior_collectors,
    'original_control_held': control,
    'prospective_executions': executions,
    'prospective_unique_identities': len(identities),
    'author_guard_counts_only_not_reexecution': guard_counts,
    'post_freeze_permission_finding_sha256': extra['finding'],
    'post_freeze_successor_held_manifest': extra,
    'limits': ['Metadata source object identities reconciled within preserved original API responses, not freshly fetched.',
               'No semantic source/controller acceptance or execution authorization.',
               'No fresh ledger/ref/workflow status query; root owns current publication preflight.',
               'Read-only byte checks only; no packet import, process probe, package command, JVM or engine execution.']
}
OUT.mkdir(parents=True, exist_ok=True)
(OUT / 'independent-byte-preservation-proof.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({'original_payloads': len(original['files']), 'addendum_payloads': len(successor['files']),
                  'all_local_bytes_equal': True, 'review_only': True,
                  'proof': identity(OUT / 'independent-byte-preservation-proof.json')}, indent=2))
