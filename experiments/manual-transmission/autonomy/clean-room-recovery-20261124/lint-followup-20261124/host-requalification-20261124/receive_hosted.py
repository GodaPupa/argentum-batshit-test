"""Original-artifact receiver for the authorized single hosted Manual grant.

This adapter leaves the frozen receive.py unchanged. It loads the original
unpack()/independent_audit() from immutable source S, retains the original
receive() orchestration verbatim, and validates actual new-epoch metadata.
It never rewrites an archive or substitutes the historical epoch's identity.

CLI:
  python receive_hosted.py --source-root REPO ARCHIVE META EXPECTED OUT

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
Also require selected_plan=B_HOSTED_ONCE, execution_environment=github_hosted,
and local_pass=false. These 41 fields must match independent metadata exactly.
IDs are actual GitHub IDs; attempt and run_number are integer 1.

The prospective hosted freeze must preserve SOURCE_FREEZE.qualification plus
the separately bound prospective source freeze and the exact CI_FREEZE_KEYS below.
No successful local execution or pre-event readiness result is asserted.
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
import datetime as dt
import zipfile
import stat

sys.dont_write_bytecode = True

SOURCE = '9ae883029afe28e1969179b00343e2ebafc836e8'
SOURCE_TREE = 'e6ec12d9a5b3c2d8ee344fe7a3ff403d2ef2256f'
SOURCE_BASIS = '82ec335573572edfd2a2e4d734d2f0d81801139f'
HISTORICAL_CHECKPOINT = '62b95ee8f854cb9a29830832c287106ad8baaac4'
RECORD = 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
NAMESPACE = 'experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124/host-requalification-20261124'
REPOSITORY = 'GodaPupa/argentum-batshit-test'
BRANCH = 'manual-transmission/clean-room-recovery-20261124'
EPOCH = 'manual-host-requalification-20261124-01'
CHARTER_COMMIT = 'bd1110a0fafcfe4039a432a9ad9e955166dbc523'
CHARTER_BLOB = '2b2a03f45c8d2b490720ce2c7ccb375b5157bcd5'
CHARTER_SHA256 = '5b4e30d8594b6c1e4a6d8f2ec2f4a0bdb0a6dba7e314e8deddffcad66b06f701'
PINS_BLOB = '8fa371d1a97c910ef0e7f4b0cba08c6a75e276bf'
PROTOCOL_BLOB = 'a2c26c3b31834a7182bbe0b3a6ceb83922443cd4'
WORKFLOW = '.github/workflows/manual-host-requalification-surge-bushwhacker-20261124.yml'
ARTIFACT_PREFIX = 'manual-host-requalification-candidate02-20261124-'
RECEIVER_PATH = NAMESPACE + '/receive_hosted.py'
READBACK_PATH = NAMESPACE + '/immutable_hosted_readback.py'
READINESS_PATH = NAMESPACE + '/hosted_runtime.py'
CLOSED_CHECKPOINT = 'a6dd2a6d84a9b046a7f77ac426c5dcbe9bb47b76'
CLOSED_NAMESPACE = NAMESPACE.rsplit('/',1)[0]
OWNER = 'manual-host-requalification-foreground-20261124'
SOURCE_HASHES = {
    'receive.py': '00be55d69206bae346f602c3a633a2056fe77de59c3e0691e689001bc101fbd6',
    'audit_reports.py': 'aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81',
    'verify_dispatch.py': '4a3570993ba9328dac7e3db94ac7daa12a24d547dd88a8ffb2246671bc7fbd1c',
    'CANDIDATE_PINS.json': '831ec02ecfa47d59b7504950ee0334fcc99da46a5e09f2bde669b90fa20a6374',
}
CI_FREEZE_KEYS = {
    'validation', 'validation_tree', 'workflow_path', 'workflow_blob', 'workflow_sha256',
    'source_freeze_sha256', 'receiver_sha256', 'receiver_blob', 'readback_sha256', 'readback_blob',
    'readiness_contract_sha256', 'readiness_helper_sha256', 'readiness_helper_blob',
}
BASE_FREEZE_KEYS = {'source','source_tree','source_basis','candidate_index','binding_count','candidate_pins_blob'}
META_KEYS = {'repository','branch','epoch','candidate_index','event','source','source_tree','validation',
    'validation_tree','workflow_blob','workflow_sha256','protocol_blob','candidate_pins_blob',
    'run_id','job_id','artifact_id','artifact_name','artifact_digest','artifact_size','attempt','run_number',
    'status','job_status','conclusion','job_conclusion','already_received','fresh_reconciliation','sole_writer',
    'automation_disabled','first_parent','workflow_path','recovery_id','charter_commit','charter_blob',
    'charter_sha256','lane','attempt_id','prior_receipt_count','selected_plan','execution_environment','local_pass'}
_READINESS_MODULE = None
_READINESS_CONTRACT = None
_RUNTIME_PROOF = None
_PINS = None

_ROOT = None
_BASE = None
_VERIFIER = None
_CONTEXT = None
PRIOR_RECOVERY_CHECKPOINT = '618f562154ff10a9da082a144d43c2011703d2e0'
PRIOR_NAMESPACE = 'experiments/manual-transmission/autonomy/clean-room-recovery-20261124'


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
    global _ROOT, _BASE, _VERIFIER, _CONTEXT, _PINS
    for name, value in [('SOURCE',SOURCE),('SOURCE_TREE',SOURCE_TREE),('CHARTER_COMMIT',CHARTER_COMMIT),('CHARTER_BLOB',CHARTER_BLOB),('PINS_BLOB',PINS_BLOB)]:
        object_id(value,name)
    for name,value in [('charter',CHARTER_SHA256),*SOURCE_HASHES.items()]:
        require(isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value) is not None,'unfinalized SHA256 '+name)
    _ROOT = Path(source_root).resolve()
    _BASE = None
    _VERIFIER = None
    _CONTEXT = None
    require(git('rev-parse', SOURCE + '^{tree}') == SOURCE_TREE, 'pinned source tree')
    require(git('show', '-s', '--format=%P', SOURCE) == SOURCE_BASIS, 'host authority source sole parent S2')
    require(set(git('diff','--name-only',SOURCE_BASIS,SOURCE).splitlines()) == {RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json'}, 'exact two-file authority source successor')
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
    _PINS = pins
    require(len(pins['bindings']) == 378, 'exact receiving source binding count')
    old_pins = json.loads(raw(SOURCE_BASIS, RECORD + '/CANDIDATE_PINS.json'))
    require(set(pins) == set(old_pins) | {'host_requalification'}, 'exact additive hosted pin metadata')
    require(set(pins['bindings']) == set(old_pins['bindings']), 'same 378 binding paths')
    require({p for p in pins['bindings'] if pins['bindings'][p] != old_pins['bindings'][p]} == {RECORD+'/verify_dispatch.py'}, 'only verifier binding changes')
    require(all(pins[k] == v for k,v in old_pins.items() if k != 'bindings'), 'all historical pin metadata preserved')
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
    for name,value in [('source',SOURCE),('source_tree',SOURCE_TREE),('pins',PINS_BLOB),('charter',CHARTER_COMMIT),('charter_blob',CHARTER_BLOB)]:
        object_id(value,name)
    require(re.fullmatch(r'[0-9a-f]{64}',CHARTER_SHA256) is not None, 'finalized charter SHA256')
    for envelope in (actual, expected):
        require(isinstance(envelope,dict) and set(envelope)==META_KEYS, 'exact 41 metadata fields')
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
    for key in ('first_parent','workflow_path','recovery_id','charter_commit','charter_blob','charter_sha256','lane','attempt_id','prior_receipt_count','execution_environment','local_pass','selected_plan',
                'attempt','run_number','status','job_status','conclusion','job_conclusion','already_received',
                'fresh_reconciliation','sole_writer','automation_disabled'):
        require(actual[key] == expected[key], 'wrong independently observed ' + key)
    require(actual['execution_environment'] == 'github_hosted' and actual['local_pass'] is False and actual['selected_plan'] == 'B_HOSTED_ONCE', 'explicit hosted-only selected authority')
    for envelope in (actual, expected):
        for key in ('run_id','job_id','artifact_id','artifact_size'):
            require(type(envelope[key]) is int and envelope[key] > 0, 'actual positive integer '+key)
        require(re.fullmatch(r'sha256:[0-9a-f]{64}',envelope['artifact_digest']) is not None, 'actual provider artifact digest')
    require(actual['recovery_id'] == EPOCH and actual['lane'] == 'ci', 'hosted CI identity')
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
    """Bind independent expectation to actual S3,C,V,E and prospective grant."""
    global _READINESS_MODULE, _READINESS_CONTRACT
    require(_BASE is not None and _VERIFIER is not None, 'receiver dependencies not configured')
    first,validation,event = (expected[k] for k in ('first_parent','validation','event'))
    require(git('show','-s','--format=%P',event).split() == [first,validation], 'event ordered C,V parents')
    require(git('show','-s','--format=%P',validation) == SOURCE, 'V sole parent S3')
    require(git('diff','--name-only',first,event).splitlines() == [WORKFLOW], 'activation workflow-only delta')
    require(git('diff','--name-only',SOURCE,validation).splitlines() == [WORKFLOW], 'validation workflow-only delta')
    require(git('rev-parse',validation+'^{tree}') == expected['validation_tree'], 'exact V tree')
    for ancestor in (CHARTER_COMMIT,CLOSED_CHECKPOINT,SOURCE):
        require(git('merge-base','--is-ancestor',ancestor,first) == '', 'carrier immutable ancestor '+ancestor)
    old_changes=git('diff','--name-only',CLOSED_CHECKPOINT,first,'--',PRIOR_NAMESPACE).splitlines()
    require(all(p.startswith(NAMESPACE+'/') for p in old_changes), 'all earlier operational paths preserved')
    permitted={RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json'}
    entire_delta=set(git('diff','--name-only',CLOSED_CHECKPOINT,first).splitlines())
    require(permitted<=entire_delta and all(p in permitted or p.startswith(NAMESPACE+'/') for p in entire_delta), 'carrier changes only two source paths and the new namespace')
    for old_namespace,old_checkpoint in ((PRIOR_NAMESPACE,PRIOR_RECOVERY_CHECKPOINT),(CLOSED_NAMESPACE,CLOSED_CHECKPOINT)):
        require(raw(first,old_namespace+'/CURRENT.json') == raw(old_checkpoint,old_namespace+'/CURRENT.json'), 'earlier consumed ledger preserved '+old_namespace)
    current=json.loads(raw(first,NAMESPACE+'/CURRENT.json'))
    require(current['recovery_id']==EPOCH and current['branch']==BRANCH and current['status']=='HOSTED_ACTIVATION_RESERVED', 'prospective hosted reservation')
    require(current['owner']==current['lease']['owner']==OWNER, 'hosted sole owner')
    require(current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False and current['lease']['writable_ref']=='refs/heads/'+BRANCH, 'hosted sole writable ref')
    for key in ('hosted_activations','readiness_reserved','qualification_reserved'):
        require(type(current['attempts'][key]) is int and current['attempts'][key]==1, 'one prospective reservation '+key)
    for key in ('readiness_started','readiness_completed','qualification_started','qualification_completed','original_artifact_downloads','original_artifact_receives','local_started'):
        require(type(current['attempts'][key]) is int and current['attempts'][key]==0, 'carrier predates actual action '+key)
    require(current['prior_candidate02_ci_activations']==0 and current['admitted'] is False, 'no old CI reset or advance admission')
    require(current['local_pass'] is False and current['execution_environment']=='github_hosted' and current['selected_plan']=='B_HOSTED_ONCE', 'carrier honest hosted execution')
    q=current['source_freeze']
    require(set(q)==BASE_FREEZE_KEYS|CI_FREEZE_KEYS, 'exact prospective hosted freeze fields')
    require(q['source_basis']==SOURCE_BASIS and q['binding_count']==378, 'exact predecessor and binding count')
    for key in ('source','source_tree','candidate_index','candidate_pins_blob','validation','validation_tree','workflow_path','workflow_blob','workflow_sha256'):
        require(q[key]==expected[key], 'carrier equals independent expectation '+key)
    workflow=raw(event,WORKFLOW)
    require(workflow==raw(validation,WORKFLOW)==raw(first,NAMESPACE+'/CI_ACTIVATION.yml'), 'prospective C/V/E workflow bytes')
    require(sha(workflow)==q['workflow_sha256'] and git('rev-parse',event+':'+WORKFLOW)==git('rev-parse',validation+':'+WORKFLOW)==q['workflow_blob'], 'actual workflow digest/blob')
    freeze_bytes=raw(first,NAMESPACE+'/SOURCE_FREEZE.json')
    require(sha(freeze_bytes)==q['source_freeze_sha256'], 'source freeze hash')
    freeze=json.loads(freeze_bytes)['qualification']
    require(set(freeze)==BASE_FREEZE_KEYS and all(q[k]==v for k,v in freeze.items()), 'exact original prospective base freeze')
    for path,prefix in ((RECEIVER_PATH,'receiver'),(READBACK_PATH,'readback'),(READINESS_PATH,'readiness_helper')):
        code=raw(first,path)
        require(sha(code)==q[prefix+'_sha256'] and git('rev-parse',first+':'+path)==q[prefix+'_blob'], 'prospective helper binding '+prefix)
        if prefix=='receiver':require(code==Path(__file__).read_bytes(), 'executing adapter was frozen before activation')
        if prefix=='readiness_helper':
            _READINESS_MODULE=load_pinned_module('_manual_immutable_readiness_auditor',code,_ROOT/path)
    contract=raw(first,NAMESPACE+'/READINESS_CONTRACT.json')
    require(sha(contract)==q['readiness_contract_sha256'], 'prospective readiness contract')
    _READINESS_CONTRACT=json.loads(contract)
    charter_bytes=raw(CHARTER_COMMIT,NAMESPACE+'/CHARTER.json')
    require(sha(charter_bytes)==CHARTER_SHA256 and git('rev-parse',CHARTER_COMMIT+':'+NAMESPACE+'/CHARTER.json')==CHARTER_BLOB and raw(first,NAMESPACE+'/CHARTER.json')==charter_bytes, 'immutable actual new charter')
    charter=json.loads(charter_bytes)
    require(current['charter']==NAMESPACE+'/CHARTER.json' and current['charter_sha256']==CHARTER_SHA256 and current['safeguards']==charter['safeguards'], 'current charter and safeguards binding')
    require(current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0, 'no Phase2')
    historical_path='experiments/manual-transmission/autonomy/CURRENT.json'
    historical=raw(HISTORICAL_CHECKPOINT,historical_path)
    require(raw(first,historical_path)==historical, 'historical ledger unchanged')
    grant=json.loads(historical)['surge_bushwhacker_production_authorization']
    require(git('rev-parse',SOURCE+':'+RECORD+'/PROTOCOL.json')==expected['protocol_blob']==grant['protocol_blob'], 'unchanged original protocol')
    return dict(event=event,first_parent=first,validation=validation,source=SOURCE,qualification=q,grant=grant,
                run=str(expected['run_id']),job_id=expected['job_id'],attempt=1,local_execution=False,
                host_requalification=dict(recovery_id=EPOCH,branch=BRANCH,charter_commit=CHARTER_COMMIT,
                    charter_blob=CHARTER_BLOB,lane='ci',attempt=1,attempt_id=EPOCH+'/ci-01',
                    selected_plan='B_HOSTED_ONCE',execution_environment='github_hosted',local_pass=False))

def envelope_check(actual, expected):
    global _CONTEXT
    _CONTEXT = None
    envelope_scope_check(actual, expected)
    _CONTEXT = copy.deepcopy(verify_expected_topology(expected))


def unpack(archive, destination, metadata):
    require(_BASE is not None, 'receiver dependencies not configured')
    # Validate exact regular archive members before delegating to the frozen unpacker.
    with zipfile.ZipFile(archive) as z:
        entries={}
        for info in z.infolist():
            name=info.filename[:-1] if info.is_dir() else info.filename
            require(name and ':' not in name and '\\' not in name and not any(ord(c)<32 or ord(c)==127 for c in name), 'unsafe original ZIP name')
            require(all(p not in ('','.','..') for p in name.split('/')), 'unsafe original ZIP path')
            require(name not in entries and not info.flag_bits&1, 'duplicate or encrypted original ZIP member')
            mode=stat.S_IFMT(info.external_attr>>16)
            require(mode in ((0,stat.S_IFDIR) if info.is_dir() else (0,stat.S_IFREG)), 'nonregular original ZIP member')
            if info.is_dir():require(info.file_size==0, 'nonempty original ZIP directory')
            entries[name]=info
        files={name for name,info in entries.items() if not info.is_dir()}
        require(not any('/'.join(name.split('/')[:n]) in files for name in entries for n in range(1,len(name.split('/')))), 'original ZIP file/directory collision')
    return _BASE.unpack(archive, destination, metadata)


def independent_audit(extracted, metadata):
    global _RUNTIME_PROOF
    require(_CONTEXT is not None, 'independently expected envelope not validated')
    hosted=read(extracted/'HOSTED_RUN.json')
    if hosted.get('state')=='ACTIVATION_OR_SETUP_FAILED_ORIGINAL_RUNTIME_EVIDENCE_PRESERVED':
        require(hosted['schema']=='manual-transmission/hosted-run/v1' and hosted['admitted'] is False, 'original early-failure runtime schema and boundary')
        require(hosted['execution_environment']=='github_hosted' and hosted['local_pass'] is False and hosted['selected_plan']=='B_HOSTED_ONCE', 'honest hosted setup failure')
        require(hosted['event']==metadata['event'] and type(hosted['run_id']) is int and hosted['run_id']==metadata['run_id'], 'original setup failure actual event/run')
        for key in ('readiness_sequences_started','readiness_sequences_completed','qualification_starts','qualification_completed'):
            require(type(hosted[key]) is int and hosted[key]==0, 'setup failure no readiness or qualification starts '+key)
        require(not (extracted/'qualification').exists(), 'setup failure no fabricated qualification')
        missing=[]
        for key,value in dict(source=SOURCE,source_tree=SOURCE_TREE,validation=metadata['validation'],job_id=metadata['job_id']).items():
            if key in hosted:require(hosted[key]==value, 'available setup failure identity '+key)
            else:missing.append('HOSTED_RUN.json:'+key)
        for name in ('RECOVERY_EVENT.json','READINESS_CONTRACT.json','HOSTED_READINESS.json'):
            path=extracted/name
            if not path.exists():missing.append(name);continue
            observed=read(path)
            if name=='RECOVERY_EVENT.json':require(observed==_CONTEXT, 'available original setup-failure envelope')
            elif name=='READINESS_CONTRACT.json':require(observed==_READINESS_CONTRACT and digest(path)==_CONTEXT['qualification']['readiness_contract_sha256'], 'available original setup-failure contract')
            else:require(observed.get('state')!='PASS', 'no readiness PASS on setup failure')
        _RUNTIME_PROOF=dict(hosted_run=hosted,readiness_state='NOT_STARTED',readiness_audit=None,missing_context=missing)
        raise ValueError('Activation or setup failed before readiness; original available context preserved, readiness and qualification starts zero')
    event=read(extracted/'RECOVERY_EVENT.json')
    require(event==_CONTEXT, 'exact exclusive original hosted envelope')
    contract=read(extracted/'READINESS_CONTRACT.json')
    require(contract==_READINESS_CONTRACT and digest(extracted/'READINESS_CONTRACT.json')==event['qualification']['readiness_contract_sha256'], 'original exact readiness contract')
    require(hosted['schema']=='manual-transmission/hosted-run/v1', 'original hosted runtime schema')
    require(hosted['execution_environment']=='github_hosted' and hosted['local_pass'] is False and hosted['selected_plan']=='B_HOSTED_ONCE', 'original hosted-only result meaning')
    for key,value in dict(source=SOURCE,source_tree=SOURCE_TREE,validation=metadata['validation'],event=metadata['event'],run_id=metadata['run_id'],job_id=metadata['job_id']).items():
        require(hosted[key]==value, 'original hosted runtime binding '+key)
    for key in ('readiness_sequences_started','readiness_sequences_completed','qualification_starts','qualification_completed'):
        require(type(hosted[key]) is int and hosted[key] in (0,1), 'bounded actual hosted counter '+key)
    require(hosted['qualification_completed']<=hosted['qualification_starts'], 'completion cannot precede qualification start')
    _RUNTIME_PROOF=dict(hosted_run=hosted,readiness_state='NOT_VALIDATED',readiness_audit=None)
    if hosted['qualification_starts']==0:
        require(not (extracted/'qualification').exists(), 'no fabricated qualification evidence before start')
        require(hosted['state']=='READINESS_FAILED_ORIGINAL_RUNTIME_EVIDENCE_PRESERVED', 'honest readiness-only failed state')
        _RUNTIME_PROOF['readiness_state']='FAILED_OR_INCOMPLETE'
        raise ValueError('Readiness failed before qualification; original runtime preserved, qualification starts zero')
    proof=_READINESS_MODULE.audit_readiness(extracted,contract,event)
    require(proof['state']=='READINESS_AUDIT_PASS', 'actual MockK/Flyway readiness required')
    source_state=read(extracted/'readiness/SOURCE_STATE.json')
    require(source_state['before']==source_state['after']==_PINS['bindings'], 'readiness actual source equals immutable S3 bindings')
    require(hosted['readiness_sequences_started']==hosted['readiness_sequences_completed']==1, 'single completed readiness sequence')
    def instant(value):
        require(isinstance(value,str), 'actual runtime timestamp')
        result=dt.datetime.fromisoformat(value.replace('Z','+00:00'))
        require(result.tzinfo is not None, 'timezone-aware runtime timestamp')
        return result
    require(instant(hosted['qualification_started_utc'])>=instant(hosted['readiness_ended_utc']), 'qualification begins only after readiness')
    require(hosted['qualification_host_settings_sha256']==proof['host_settings_sha256'] and hosted['host_settings_unchanged'] is True, 'qualification uses the readiness host settings')
    _RUNTIME_PROOF.update(readiness_state='READINESS_AUDIT_PASS',readiness_audit=proof)
    result=_BASE.independent_audit(extracted,metadata)
    require(hosted['qualification_starts']==hosted['qualification_completed']==1 and hosted['qualification_returncode']==0, 'one completed successful hosted qualification')
    require(hosted['state']=='HOSTED_QUALIFICATION_PASS_ORIGINAL_RECEIVING_REQUIRED', 'honest hosted pass state')
    require(hosted['source_restored'] is True and hosted['source_bindings_verified']==378, 'hosted final exact source restoration')
    command=read(extracted/'QUALIFICATION_COMMAND.json')
    require(command['argv']==['python3',str(Path(command['cwd'])/RECORD/'qualify.py')] and command['returncode']==0 and command['timed_out'] is False, 'original exact successful qualifier command')
    require(instant(command['started_utc'])>=instant(hosted['qualification_started_utc']) and command['ended_utc']==hosted['qualification_ended_utc'], 'original qualifier command temporal binding')
    return result

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
    hosted=dict(schema='manual-transmission/hosted-receiving-summary/v1',execution_environment='github_hosted',local_pass=False,selected_plan='B_HOSTED_ONCE',original_receiver_state=result['state'],original_receiver_receipt_sha256=digest(args.destination/'RECEIPT.json'),admitted=False,runtime=_RUNTIME_PROOF)
    hosted['state']='HOSTED_RAW_AUDIT_PASS_READBACK_PENDING' if result['state']=='RAW_AUDIT_PASS_READBACK_PENDING' else 'HOSTED_FAILED_OR_INCOMPLETE_ORIGINAL_PRESERVED'
    write(args.destination/'HOSTED_RECEIPT.json',hosted)
    print(json.dumps(result, indent=2))
    sys.exit(0 if result['state'] == 'RAW_AUDIT_PASS_READBACK_PENDING' else 1)

