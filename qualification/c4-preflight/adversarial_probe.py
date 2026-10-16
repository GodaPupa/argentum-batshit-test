"""V5 source-only fixed-image entry and OFFLINE receipt evaluator.

There is deliberately no privileged dispatch edge from this module to V4.
A JSON receipt is not independently authenticated execution authority.
"""
import hashlib
import json
import stat
from pathlib import Path
from kernel_supervisor import Refusal

ELF_SHA256 = '5c94b6671b4be6e2fbfcf46fc20fc17229ed15912a650362bfd0b1ec78dcd9b6'
HELPER_SHA256 = '449429b3137d20f4f470f434877879e245c810a9876e89413c5aaae3327c73e6'
V4_COMMIT = 'defc04acf75a9fa040ed7c43068d3b75cb730ec4'
PROPOSED_PARENT = '/sys/fs/cgroup/argentum-c4-v5'
DENIALS = ('migration','setuid','setgid','capset','unshare','setns','mount','unix_socket','unix_socketpair')
OBSERVATIONS = ('inherited_fds','membership_unchanged','network_inventory','post_seal',
                'reparent_descendant','persistent_descendant','launcher_complete')


def fixed_image_identity():
    """Read only. Hash fixed sibling bytes; no arbitrary path or build-on-run."""
    base = Path(__file__).parent
    image = base / 'adversarial-helper-v5.elf'
    s = image.lstat()
    if not stat.S_ISREG(s.st_mode) or stat.S_IMODE(s.st_mode) != 0o755:
        raise Refusal('IMAGE_NOT_FROZEN: regular 0755 ELF required')
    data = image.read_bytes()
    if hashlib.sha256(data).hexdigest() != ELF_SHA256 or data[:4] != b'\x7fELF':
        raise Refusal('IMAGE_NOT_FROZEN: ELF mismatch')
    if hashlib.sha256((base/'adversarial_helper.c').read_bytes()).hexdigest() != HELPER_SHA256:
        raise Refusal('IMAGE_NOT_FROZEN: helper source mismatch')
    return {'elf_sha256':ELF_SHA256, 'helper_sha256':HELPER_SHA256,
            'v4_parent':V4_COMMIT, 'bytes':len(data), 'mode':'0755'}


def supervise_fixed_adversarial_v5(evidence):
    """Named fixed entry, DISCONNECTED pending approved parent and backend review.

    A mandatory evidence path is checked but no directories/files are created.
    No arguments, environment, approval JSON, caller UID or sudo can enable it.
    This is a preparation gate, NOT an implemented privileged V5 adapter.
    """
    if not isinstance(evidence, Path) or not evidence.is_absolute() or '..' in evidence.parts:
        raise Refusal('INCOMPLETE_RUNTIME: absolute evidence path required')
    fixed_image_identity()
    raise Refusal('BLOCKED_HOST_PARENT: no independently approved protected delegation; '
                  'V5 privileged adapter disconnected; no systemd ancestor fallback')


def parent_policy(record):
    """Offline policy predicate only; cannot grant host authority."""
    keys = ('exact_parent_approved','exclusive_delegation','root_protected',
            'memory_already_enabled','supervisor_outside','held_identity_verified')
    return (isinstance(record,dict) and record.get('path') == PROPOSED_PARENT
            and all(record.get(k) is True for k in keys)
            and record.get('upstream_mutations') == []
            and record.get('systemd_managed') is False)


def evaluate_synthetic_receipt(r):
    """Validate model fixtures only. Never C4/D2 qualification or authentication.

    Positive same-scope counters survive a different missing/reset interval.
    Complete negative receipts require the entire lifecycle, not errno alone.
    """
    out={'status':'INCOMPLETE_RUNTIME','qualifying':False,'compiler_enabled':False,
         'authentication':'OFFLINE_SYNTHETIC_ONLY','gaps':[], 'positive':[]}
    if not isinstance(r,dict):return out
    binding = (r.get('elf_sha256') == ELF_SHA256 and r.get('helper_sha256') == HELPER_SHA256
               and isinstance(r.get('run_id'),str) and bool(r['run_id'])
               and isinstance(r.get('source_commit'),str) and len(r['source_commit'])==40
               and all(c in '0123456789abcdef' for c in r['source_commit']))
    if not binding:out['gaps'].append('source/run binding');return out
    samples=r.get('samples',[]);prev=None;valid=[]
    for s in samples if isinstance(samples,list) else []:
        good=(isinstance(s,dict) and s.get('counter_id') == r.get('counter_id')
              and isinstance(r.get('counter_id'),list) and len(r['counter_id'])==2
              and s.get('boot') == r.get('boot') and bool(r.get('boot'))
              and s.get('scope') == 'v2-hierarchical' and s.get('run_id') == r['run_id']
              and all(type(s.get(k)) is int and s[k]>=0 for k in ('oom','oom_kill','seq')))
        if not good:out['gaps'].append('missing/mismatched counter sample');continue
        if s['oom']>0 or s['oom_kill']>0:out['positive'].append(s)
        if prev and (s['seq']<=prev['seq'] or s['oom']<prev['oom'] or s['oom_kill']<prev['oom_kill']):
            out['gaps'].append('counter reset/order')
        prev=s;valid.append(s)
    rows = r.get('helper_rows', [])
    successful_forbidden = isinstance(rows,list) and any(
        isinstance(row,dict) and row.get('case') in DENIALS
        and type(row.get('ret')) is int and row['ret'] >= 0 for row in rows)
    if r.get('demonstrated_escape') is True or r.get('source_bound_fatal') is True or successful_forbidden or out['positive']:
        out['status']='FAIL';return out
    if not parent_policy(r.get('parent')):
        out['status']='BLOCKED_HOST_PARENT';return out
    if not valid or len(valid)!=len(samples) or len(valid)<2 or valid[0].get('phase')!='creation' or valid[-1].get('phase')!='post_drain':
        out['gaps'].append('incomplete lifetime')
    checks=('pid_start_verified','preexec_membership','postexec_membership','descendant_membership',
            'reparent_verified','privileges_sealed','namespaces_verified','scratch_mounts_verified',
            'fd_closure_verified','pidfd_bound','group_fd_bound','counter_fd_bound',
            'root_fd_bound','image_fd_bound','persistent_before_kill','kill_written',
            'populated_zero','same_fd_final','counter_before_remove','removed',
            'logs_complete','manifest_complete','no_external_ipc_observed')
    if any(r.get(k) is not True for k in checks):out['gaps'].append('missing lifecycle/seal evidence')
    events=r.get('events',[])
    if events!=['creation','preexec','exec','reparent','persistent','kill','drained','final','logs','remove','manifest']:
        out['gaps'].append('lifecycle ordering')
    rows=r.get('helper_rows',[])
    if not isinstance(rows,list) or len(rows)!=len(DENIALS)+len(OBSERVATIONS):out['gaps'].append('partial helper output')
    else:
        cases={}
        for row in rows:
            if not isinstance(row,dict) or row.get('case') in cases:out['gaps'].append('malformed/duplicate helper row');continue
            cases[row.get('case')]=row
        if set(cases)!=set(DENIALS+OBSERVATIONS):out['gaps'].append('case set mismatch')
        for name in DENIALS:
            row=cases.get(name,{})
            if type(row.get('ret')) is not int or row['ret']!=-1 or row.get('errno') not in (1,13):out['gaps'].append('denial not observed: '+name)
        for name in OBSERVATIONS:
            row=cases.get(name,{})
            if type(row.get('ret')) is not int or row['ret']!=0:out['gaps'].append('observation absent: '+name)
    if not out['gaps']:out['status']='NEGATIVE_CONTROL_OBSERVED'
    out['limitations']=['synthetic schema is not a kernel receipt or independent attestation',
                         'errno does not identify seccomp versus DAC enforcement',
                         'compiler daemon reuse and dependency egress remain blocked']
    return out


if __name__=='__main__':
    raise SystemExit('DISABLED: local source-only V5; BLOCKED_HOST_PARENT')
