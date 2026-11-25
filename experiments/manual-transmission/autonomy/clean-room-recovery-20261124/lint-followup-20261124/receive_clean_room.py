"""Original-artifact receiver for the authorized Manual clean-room epoch.

This adapter leaves the frozen receive.py unchanged. It loads the original
unpack()/independent_audit() from immutable source S, retains the original
receive() orchestration verbatim, and validates actual new-epoch metadata.
It never rewrites an archive or substitutes the historical epoch's identity.

CLI:
  python receive_clean_room.py --source-root REPO ARCHIVE META EXPECTED OUT

Run only after qualification is terminal and the source worktree is restored.
The caller must obtain EXPECTED independently from frozen carrier C / validation
V / event E and fresh GitHub run/job/artifact observations. Do not derive it from
ARCHIVE's own envelope. The caller must durably claim the single download before
transport, preserve the downloaded bytes even if prevalidation fails, and record
fresh reconciliation. This script performs no GitHub download or API request.

META and EXPECTED need every original receiver field:
  repository, branch, epoch, candidate_index, event, source, source_tree,
  validation, validation_tree, workflow_blob, workflow_sha256, protocol_blob,
  candidate_pins_blob, run_id, job_id, artifact_id, artifact_name,
  artifact_digest, artifact_size, attempt, run_number, status, job_status,
  conclusion, job_conclusion, already_received, fresh_reconciliation,
  sole_writer, automation_disabled.
This epoch also requires matching:
  first_parent, workflow_path, recovery_id, charter_commit, charter_blob,
  charter_sha256, lane, attempt_id, prior_receipt_count (exactly zero).
IDs are actual GitHub IDs; attempt and run_number are integer 1.

The prospective CI freeze must be the original SOURCE_FREEZE.qualification plus
exactly validation, validation_tree, workflow_path, workflow_blob,
workflow_sha256, local_freeze_sha256, receiver_sha256, and receiver_blob.
Operational files are read from C's immutable Git objects. E has ordered parents
C,V; V has sole parent S; both carrier activation and validation add only the
new workflow. The original raw archive, CRC/manifest checks, evidence audit,
failure preservation, no-overwrite destination and exact-byte parts remain.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import json
import re
import subprocess
import sys
import types

sys.dont_write_bytecode = True

SOURCE = '82ec335573572edfd2a2e4d734d2f0d81801139f'
SOURCE_TREE = '923ec79e1c867eedf35b408153bf60988fbd26b6'
SOURCE_BASIS = '2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
HISTORICAL_CHECKPOINT = '62b95ee8f854cb9a29830832c287106ad8baaac4'
RECORD = 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
NAMESPACE = 'experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124'
REPOSITORY = 'GodaPupa/argentum-batshit-test'
BRANCH = 'manual-transmission/clean-room-recovery-20261124'
EPOCH = 'manual-lint-followup-20261124-01'
CHARTER_COMMIT = '86450c1ecb227804dbd45acf5ba28689ce0c61bc'
CHARTER_BLOB = '418fb7f5a0ebeed92582f8285e76305c39250efa'
CHARTER_SHA256 = 'a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e'
PINS_BLOB = '7e30df16c53a59358d507733b2a5443e2a3c9075'
PROTOCOL_BLOB = 'a2c26c3b31834a7182bbe0b3a6ceb83922443cd4'
WORKFLOW = '.github/workflows/manual-lint-followup-surge-bushwhacker-20261124.yml'
ARTIFACT_PREFIX = 'manual-lint-followup-candidate02-20261124-'
RECEIVER_PATH = NAMESPACE + '/receive_clean_room.py'
SOURCE_HASHES = {
    'receive.py': '00be55d69206bae346f602c3a633a2056fe77de59c3e0691e689001bc101fbd6',
    'audit_reports.py': 'aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81',
    'verify_dispatch.py': '23ea2debb05adc95194f2fcc41fc6c84ad3070d76987f5b6b96af061d3ddabb7',
    'CANDIDATE_PINS.json': 'b0e8da912f98b75a35c44db4a3e9e3334d941af3891e0bb28281490ab01e7bbd',
}
CI_FREEZE_KEYS = {
    'validation', 'validation_tree', 'workflow_path', 'workflow_blob',
    'workflow_sha256', 'local_freeze_sha256', 'receiver_sha256', 'receiver_blob',
}
_ROOT = None
_BASE = None
_VERIFIER = None
_CONTEXT = None
PRIOR_RECOVERY_CHECKPOINT = '618f562154ff10a9da082a144d43c2011703d2e0'
PRIOR_NAMESPACE = 'experiments/manual-transmission/autonomy/clean-room-recovery-20261124'
if any(str(value).startswith('PENDING_') for value in (SOURCE, SOURCE_TREE, CHARTER_COMMIT, CHARTER_BLOB, CHARTER_SHA256, PINS_BLOB, *SOURCE_HASHES.values(),)):
    raise RuntimeError('Unfinalized lint-followup draft: fill all pinned identities before use')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def sha(b):
    return hashlib.sha256(b).hexdigest()


def digest(p):
    return sha(Path(p).read_bytes())


def read(p):
    return json.loads(Path(p).read_text())


def write(p, x):
    Path(p).write_text(json.dumps(x, indent=2) + '\n')


def git(*args):
    require(_ROOT is not None, 'configure --source-root before receiving')
    return subprocess.check_output(['git', *args], cwd=_ROOT).decode().strip()


def raw(ref, path):
    require(_ROOT is not None, 'configure --source-root before receiving')
    return subprocess.check_output(['git', 'show', ref + ':' + path], cwd=_ROOT)


def object_id(value, label):
    require(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{40}', value) is not None,
            'invalid immutable object identity ' + label)
    return value


def load_pinned_module(name, contents, path):
    module = types.ModuleType(name)
    module.__file__ = str(path)
    exec(compile(contents, str(path), 'exec'), module.__dict__)
    return module


def configure(source_root):
    """Read immutable S and its restored bound files; do not execute behavior."""
    global _ROOT, _BASE, _VERIFIER, _CONTEXT
    _ROOT = Path(source_root).resolve()
    _BASE = None
    _VERIFIER = None
    _CONTEXT = None
    require(git('rev-parse', SOURCE + '^{tree}') == SOURCE_TREE, 'pinned source tree')
    require(git('show', '-s', '--format=%P', SOURCE) == SOURCE_BASIS, 'lint followup source sole parent')
    require(git('rev-parse', SOURCE + ':' + RECORD + '/CANDIDATE_PINS.json') == PINS_BLOB,
            'pinned candidate metadata blob')
    files = {}
    for name, expected in SOURCE_HASHES.items():
        contents = raw(SOURCE, RECORD + '/' + name)
        require(sha(contents) == expected, 'immutable receiver dependency ' + name)
        require((_ROOT / RECORD / name).read_bytes() == contents,
                'receiving worktree dependency differs from frozen source ' + name)
        files[name] = contents
    pins = json.loads(files['CANDIDATE_PINS.json'])
    require(len(pins['bindings']) == 378, 'exact receiving source binding count')
    for path, pin in pins['bindings'].items():
        contents = (_ROOT / path).read_bytes()
        require(len(contents) == pin['bytes'] and sha(contents) == pin['sha256'],
                'receiving source not restored and bound ' + path)
    audit = load_pinned_module('_manual_clean_room_audit', files['audit_reports.py'],
                               _ROOT / RECORD / 'audit_reports.py')
    # Only resolve the frozen original's import to the pinned amended auditor.
    # No receiver function or validation hook is monkeypatched.
    missing = object()
    previous = sys.modules.get('audit_reports', missing)
    sys.modules['audit_reports'] = audit
    try:
        _BASE = load_pinned_module('_manual_frozen_original_receiver', files['receive.py'],
                                   _ROOT / RECORD / 'receive.py')
    finally:
        if previous is missing:
            del sys.modules['audit_reports']
        else:
            sys.modules['audit_reports'] = previous
    _VERIFIER = load_pinned_module('_manual_frozen_source_verifier', files['verify_dispatch.py'],
                                   _ROOT / RECORD / 'verify_dispatch.py')


def envelope_scope_check(actual, expected):
    """All original envelope checks, under the explicitly authorized new identity."""
    for envelope in (actual, expected):
        for key in ('attempt', 'run_number', 'prior_receipt_count'):
            require(type(envelope[key]) is int, 'non-integer counter ' + key)
    for key in ('repository','branch','epoch','candidate_index','event','source','source_tree','validation','validation_tree','workflow_blob','workflow_sha256','protocol_blob','candidate_pins_blob','run_id','job_id','artifact_id','artifact_name','artifact_digest','artifact_size'):
        require(actual[key]==expected[key],'wrong '+key)
    require(actual['attempt']==actual['run_number']==1,'attempt/run number cap')
    require(actual['status']=='completed' and actual['job_status']=='completed','nonterminal original')
    require(actual['already_received'] is False,'already received artifact')
    require(actual['candidate_index']=='02','clean-room Candidate02 only')
    require(actual['repository']==REPOSITORY,'repository')
    require(actual['branch']==BRANCH,'branch')
    require(actual['epoch']==EPOCH,'epoch')
    require(actual['protocol_blob']==PROTOCOL_BLOB,'protocol')
    require(actual['fresh_reconciliation'] is True and actual['sole_writer'] is True and actual['automation_disabled'] is True,'live receiving gate')
    for key in ('first_parent','workflow_path','recovery_id','charter_commit','charter_blob','charter_sha256','lane','attempt_id','prior_receipt_count',
                'attempt','run_number','status','job_status','conclusion','job_conclusion','already_received',
                'fresh_reconciliation','sole_writer','automation_disabled'):
        require(actual[key] == expected[key], 'wrong independently observed ' + key)
    require(actual['recovery_id'] == EPOCH and actual['lane'] == 'ci', 'clean-room CI identity')
    require(actual['charter_commit'] == CHARTER_COMMIT and actual['charter_blob'] == CHARTER_BLOB
            and actual['charter_sha256'] == CHARTER_SHA256, 'clean-room charter identity')
    require(actual['attempt_id'] == EPOCH + '/ci-01', 'clean-room CI attempt identity')
    require(actual['prior_receipt_count'] == 0, 'prior clean-room receipt exists')
    require(actual['workflow_path'] == WORKFLOW, 'clean-room workflow path')
    require(actual['source'] == SOURCE and actual['source_tree'] == SOURCE_TREE
            and actual['candidate_pins_blob'] == PINS_BLOB, 'clean-room frozen source identity')
    require(actual['artifact_name'] == ARTIFACT_PREFIX + str(actual['run_id']), 'clean-room original artifact name')
    for key in ('first_parent', 'event', 'validation', 'validation_tree', 'workflow_blob'):
        object_id(actual[key], key)


def verify_expected_topology(expected):
    """Bind independent expected metadata to actual immutable C,V,E objects."""
    require(_BASE is not None and _VERIFIER is not None, 'receiver dependencies not configured')
    first = expected['first_parent']
    validation = expected['validation']
    event = expected['event']
    require(git('show', '-s', '--format=%P', event).split() == [first, validation],
            'event ordered parents must be carrier and validation')
    require(git('show', '-s', '--format=%P', validation) == SOURCE,
            'validation sole parent must be frozen source')
    require(git('diff', '--name-only', first, event).splitlines() == [WORKFLOW],
            'activation changes only the new workflow')
    require(git('diff', '--name-only', SOURCE, validation).splitlines() == [WORKFLOW],
            'validation changes only the new workflow')
    require(git('rev-parse', validation + '^{tree}') == expected['validation_tree'],
            'independent validation tree')
    require(raw(first, PRIOR_NAMESPACE + '/CURRENT.json') == raw(PRIOR_RECOVERY_CHECKPOINT, PRIOR_NAMESPACE + '/CURRENT.json'),
            'previous failed recovery ledger unchanged')
    current = json.loads(raw(first, NAMESPACE + '/CURRENT.json'))
    require(current['owner'] == current['lease']['owner'] == 'manual-lint-foreground-20261124', 'lint followup owner')
    q = current['source_freeze']
    require(q['source'] == SOURCE and q['source_tree'] == SOURCE_TREE
            and q['source_basis'] == SOURCE_BASIS and q['candidate_index'] == '02'
            and q['binding_count'] == 378 and q['candidate_pins_blob'] == PINS_BLOB,
            'carrier exact frozen source')
    for key in ('validation', 'validation_tree', 'workflow_path', 'workflow_blob', 'workflow_sha256',
                'source', 'source_tree', 'candidate_pins_blob', 'candidate_index'):
        require(q[key] == expected[key], 'carrier differs from independent expected ' + key)
    workflow_bytes = raw(event, WORKFLOW)
    require(workflow_bytes == raw(validation, WORKFLOW) == raw(first, NAMESPACE + '/CI_ACTIVATION.yml'),
            'workflow differs from prospective frozen template')
    require(git('rev-parse', event + ':' + WORKFLOW) == git('rev-parse', validation + ':' + WORKFLOW)
            == q['workflow_blob'], 'exact operative workflow blob')
    require(sha(workflow_bytes) == q['workflow_sha256'], 'exact operative workflow hash')
    local_freeze_bytes = raw(first, NAMESPACE + '/SOURCE_FREEZE.json')
    require(sha(local_freeze_bytes) == q['local_freeze_sha256'], 'original local freeze hash')
    local_freeze = json.loads(local_freeze_bytes)['qualification']
    require(set(q) == set(local_freeze) | CI_FREEZE_KEYS
            and all(q[key] == value for key, value in local_freeze.items()),
            'CI freeze must preserve every original local source field')
    receiver_bytes = raw(first, RECEIVER_PATH)
    require(sha(receiver_bytes) == q['receiver_sha256']
            and git('rev-parse', first + ':' + RECEIVER_PATH) == q['receiver_blob'],
            'prospectively pinned clean-room receiver')
    require(receiver_bytes == Path(__file__).read_bytes(), 'executing receiver differs from pinned adapter')
    charter_bytes = raw(CHARTER_COMMIT, NAMESPACE + '/CHARTER.json')
    require(git('rev-parse', CHARTER_COMMIT + ':' + NAMESPACE + '/CHARTER.json') == CHARTER_BLOB
            and sha(charter_bytes) == CHARTER_SHA256, 'immutable charter identity')
    require(raw(first, NAMESPACE + '/CHARTER.json') == charter_bytes, 'carrier charter unchanged')
    historical_path = 'experiments/manual-transmission/autonomy/CURRENT.json'
    historical = raw(HISTORICAL_CHECKPOINT, historical_path)
    require(raw(first, historical_path) == historical, 'historical ledger unchanged')
    grant = json.loads(historical)['surge_bushwhacker_production_authorization']
    require(git('rev-parse', SOURCE + ':' + RECORD + '/PROTOCOL.json') == expected['protocol_blob']
            == grant['protocol_blob'], 'original protocol remains bound')
    local = current['local_result']
    receipt_commit = object_id(local['receipt_commit'], 'local receipt commit')
    receipt_path = local['receipt_path']
    require(isinstance(receipt_path, str) and receipt_path.startswith(NAMESPACE + '/LOCAL_QUALIFICATION_01/')
            and '..' not in Path(receipt_path).parts and '\\' not in receipt_path,
            'local receipt must belong to the new recovery namespace')
    require(git('merge-base', '--is-ancestor', CHARTER_COMMIT, receipt_commit) == ''
            and git('merge-base', '--is-ancestor', receipt_commit, first) == '',
            'local receipt must belong to preserved carrier history')
    receipt = json.loads(raw(receipt_commit, receipt_path))
    require(local['state'] == receipt['state'] == 'ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED'
            and local['source'] == receipt['source'] == SOURCE
            and local['immutable_readback'] == 'PASS', 'exact original local pass and readback')
    require(receipt['all_frozen_local_gates_pass'] is True and receipt['source_bindings_verified'] == 378
            and receipt['archive_sha256'] == local['archive_sha256']
            and receipt['source_basis'] == SOURCE_BASIS, 'local receipt evidence binding')
    envelope = dict(event=event, first_parent=first, validation=validation, source=SOURCE,
                    qualification=q, grant=grant, run=str(expected['run_id']), attempt=1,
                    lint_followup=dict(recovery_id=EPOCH, branch=BRANCH, charter_commit=CHARTER_COMMIT,
                                    charter_blob=CHARTER_BLOB, lane='ci', attempt=1,
                                    attempt_id=EPOCH + '/ci-01'))
    # Reuse the frozen verifier for all historical proof and live-lane reservation fields.
    _VERIFIER.verify(envelope)
    return envelope


def envelope_check(actual, expected):
    global _CONTEXT
    _CONTEXT = None
    envelope_scope_check(actual, expected)
    _CONTEXT = copy.deepcopy(verify_expected_topology(expected))


def unpack(archive, destination, metadata):
    require(_BASE is not None, 'receiver dependencies not configured')
    return _BASE.unpack(archive, destination, metadata)


def independent_audit(extracted, metadata):
    require(_CONTEXT is not None, 'independent expected envelope was not validated')
    event = read(extracted / 'RECOVERY_EVENT.json')
    require('lint_followup' in event and 'clean_room' not in event, 'exclusive new followup authority envelope')
    for key in ('event', 'first_parent', 'validation', 'source', 'qualification', 'grant', 'attempt', 'lint_followup'):
        require(event[key] == _CONTEXT[key], 'original clean-room event differs from frozen expected ' + key)
    require(str(event['run']) == _CONTEXT['run'] == str(metadata['run_id']), 'original clean-room run identity')
    return _BASE.independent_audit(extracted, metadata)


# The following receive() function is byte-for-byte the immutable original.
# Its global envelope_check is the new identity adapter; unpack and independent_audit
# delegate to the unchanged frozen receiver after the explicit extra bindings above.
def receive(archive,metadata,expected,destination):
    envelope_check(metadata,expected);require(not destination.exists(),'already received candidate')
    destination.mkdir(parents=True)
    # Retain the original even when audit fails. Never download or invoke this receiver again.
    original=destination/'original.zip';original.write_bytes(archive.read_bytes())
    receipt=dict(schema='manual-transmission/surge-original-receipt/v1',metadata=metadata,original_received_once=True,admitted=False)
    try:
        files=unpack(original,destination/'extracted',metadata);receipt['members']=files
        receipt['audit']=independent_audit(destination/'extracted',metadata);receipt['state']='RAW_AUDIT_PASS_READBACK_PENDING'
    except Exception as exc:
        receipt['state']='FAILED_OR_INCOMPLETE_ORIGINAL_PRESERVED';receipt['blocker']=str(exc)
    # Parts are the original bytes, not a repacked zip. A remote readback must independently reassemble them.
    b=original.read_bytes();index=[]
    import base64
    for n,start in enumerate(range(0,len(b),450000)):
        part=b[start:start+450000];name=f'original.part{n:04d}.b64'
        (destination/name).write_bytes(base64.b64encode(part)+b'\n')
        index.append(dict(file=name,bytes=len(part),sha256=hashlib.sha256(part).hexdigest()))
    write(destination/'ARCHIVE_PARTS.json',dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest(),parts=index))
    write(destination/'RECEIPT.json',receipt)
    return receipt
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', required=True, type=Path)
    parser.add_argument('archive', type=Path)
    parser.add_argument('metadata', type=Path)
    parser.add_argument('expected', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    configure(args.source_root)
    result = receive(args.archive, read(args.metadata), read(args.expected), args.destination)
    print(json.dumps(result, indent=2))
    sys.exit(0 if result['state'] == 'RAW_AUDIT_PASS_READBACK_PENDING' else 1)

