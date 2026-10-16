"""Route B offline contracts. These checks never grant execution authority."""
from dataclasses import dataclass
import copy
import json
from pathlib import Path
import re

class Blocked(RuntimeError): pass

def need(ok, why):
    if not ok: raise Blocked(why)

def unit_contract():
    return json.loads(Path(__file__).with_name('v6-unit-contract.json').read_text())

@dataclass(frozen=True)
class ModelScope:
    d: tuple
    s: tuple
    c: tuple
    boot: str
    invocation: str
    mount: int
    namespace: int
    authority: str = 'SYNTHETIC_ONLY_NOT_A_PERMIT'

def _validate_scope_model(r):
    """Strict synthetic schema. Future backend must obtain raw trusted channels.
    A dictionary with matching fields is not authenticated PID1 readback.
    """
    need(isinstance(r,dict),'BLOCKED_HOST_PARENT: missing readback')
    contract=unit_contract()
    need(type(r.get('manager_pid')) is int and r['manager_pid']==1 and type(r.get('manager_version')) is int and r['manager_version']>=254,'BLOCKED_HOST_PARENT: PID1/version')
    need(r.get('manager_channel')=='authenticated-system-bus','BLOCKED_HOST_PARENT: manager channel')
    need(r.get('unit')==contract['unit_name'] and r.get('properties')==contract['properties'],'BLOCKED_HOST_PARENT: effective properties')
    need(r.get('new_invocation') is True and r.get('upstream_diff')==[],'BLOCKED_HOST_PARENT: reuse/upstream effects')
    need(isinstance(r.get('invocation'),str) and re.fullmatch('[0-9a-f]{32}',r['invocation']),'BLOCKED_HOST_PARENT: invocation')
    need(isinstance(r.get('boot'),str) and re.fullmatch('[0-9a-f-]{36}',r['boot']),'BLOCKED_HOST_PARENT: boot')
    m=r.get('mount',{})
    need(m.get('type')=='cgroup2' and m.get('root')=='/' and m.get('point')=='/sys/fs/cgroup' and m.get('hierarchical_events') is True,'BLOCKED_HOST_PARENT: mount view')
    need(type(m.get('id')) is int and m['id']>0,'BLOCKED_HOST_PARENT: mount ID')
    need(r.get('host_cgroup_ns')==r.get('supervisor_cgroup_ns') and type(r.get('host_cgroup_ns')) is int,'BLOCKED_HOST_PARENT: mixed namespaces')
    nodes=r.get('nodes',{})
    need(set(nodes)=={'D','S','C'},'BLOCKED_HOST_PARENT: scope nodes')
    identities=[]
    for name in ('D','S','C'):
        n=nodes[name];identity=n.get('id')
        need(isinstance(identity,list) and len(identity)==2 and all(type(v) is int and v>0 for v in identity),'BLOCKED_HOST_PARENT: inode identity')
        identities.append(tuple(identity))
        need(n.get('held_fd_id')==identity and n.get('boot')==r['boot'] and n.get('mount_id')==m['id'],'BLOCKED_HOST_PARENT: FD/boot/mount drift')
        need(n.get('uid')==0 and n.get('gid')==0 and n.get('mode')==0o755 and n.get('acl')==[],'BLOCKED_HOST_PARENT: ownership/ACL')
        need(n.get('type')=='domain' and n.get('control_files_root_only') is True,'BLOCKED_HOST_PARENT: control files')
    need(len(set(identities))==3,'BLOCKED_HOST_PARENT: aliased nodes')
    d,s,c=(nodes[n] for n in ('D','S','C'))
    need(isinstance(d.get('path'),str) and d['path'].startswith('/') and '..' not in d['path'].split('/') and '//' not in d['path'],'BLOCKED_HOST_PARENT: malformed D')
    need(d['path'] not in ('/','/system.slice','/system.slice/hosted-compute-agent.service'),'BLOCKED_HOST_PARENT: shared parent')
    need(d['path']==r.get('manager_control_group'),'BLOCKED_HOST_PARENT: ControlGroup')
    need(s.get('path')==d['path']+'/supervisor' and c.get('path')==d['path']+'/probe','BLOCKED_HOST_PARENT: sibling mapping')
    need(r.get('self_membership')==s['path'] and r.get('main_identity')==r.get('self_identity'),'BLOCKED_HOST_PARENT: supervisor identity')
    ident=r.get('self_identity')
    need(isinstance(ident,list) and len(ident)==2 and all(type(v) is int and v>0 for v in ident),'BLOCKED_HOST_PARENT: PID/start')
    need(d.get('direct_pids')==[] and s.get('direct_pids')==[ident[0]] and c.get('direct_pids')==[],'BLOCKED_HOST_PARENT: population')
    need(d.get('memory_available') is True and d.get('memory_enabled') is True and s.get('manager_created') is True and c.get('fresh') is True,'BLOCKED_HOST_PARENT: delegation/freshness')
    need(r.get('ancestor_acl_verified') is True and r.get('ancestor_worker_writable') is False and r.get('evidence_fresh') is True,'BLOCKED_HOST_PARENT: ancestors/evidence')
    need(r.get('worker_uid_reserved') is True and r.get('worker_gid_reserved') is True,'BLOCKED_HOST_PARENT: worker identity reservation')
    return ModelScope(*identities,r['boot'],r['invocation'],m['id'],r['host_cgroup_ns'])

def validate_scope_model(r):
    try:return _validate_scope_model(r)
    except (TypeError,KeyError,AttributeError,ValueError) as exc:
        raise Blocked('BLOCKED_HOST_PARENT: malformed readback') from exc


DENIALS=('migration','setuid','setgid','capset','unshare','setns','mount','unix_socket','unix_socketpair')
STAGES=('creation','preexec','postexec','fork','reparent','persistent','kill','drained','final','logs','removed','manifest')

def evaluate_model(r, expected_image):
    """Model receipt only, never a runtime qualification or authority grant.
    Positive scoped evidence remains FAIL across unrelated gaps/cancellation.
    """
    out={'status':'INCOMPLETE_RUNTIME','model_only':True,'runtime_enforcement_qualified':False,
         'compiler_enabled':False,'hosted_permission':False,'gaps':[],'positive':[]}
    if not isinstance(r,dict):return out
    binding=r.get('binding',{})
    required=('run','boot','invocation','counter_id','cgroup_id','image')
    if any(k not in binding for k in required) or binding.get('image')!=expected_image:
        out['gaps'].append('source/scope binding absent');return out
    need_fields={'run':str,'boot':str,'invocation':str,'counter_id':list,'cgroup_id':list}
    if any(type(binding.get(k)) is not t or not binding[k] for k,t in need_fields.items()):return out
    def bound(x):return isinstance(x,dict) and x.get('binding')==binding
    samples=r.get('samples',[])
    if not isinstance(samples,list):samples=[]
    prev=None;valid=[]
    for x in samples:
        if not bound(x) or x.get('scope')!='v2-hierarchical' or any(type(x.get(k)) is not int or x[k]<0 for k in ('oom','oom_kill','seq')):
            out['gaps'].append('counter gap/identity/scope');continue
        if x['oom'] or x['oom_kill']:out['positive'].append(copy.deepcopy(x))
        if prev and (x['seq']<=prev['seq'] or x['oom']<prev['oom'] or x['oom_kill']<prev['oom_kill']):out['gaps'].append('reset/order')
        prev=x;valid.append(x)
    observed=r.get('kernel_events',[])
    if not isinstance(observed,list):observed=[]
    for x in observed:
        if bound(x) and x.get('origin')=='fixed-parent-observer' and (x.get('event') in ('escape','fatal') or (x.get('event') in DENIALS and type(x.get('ret')) is int and x['ret']>=0)):
            out['positive'].append(copy.deepcopy(x))
    if out['positive']:out['status']='FAIL';return out
    try:scope=validate_scope_model(r.get('parent'))
    except (Blocked,TypeError,KeyError):out['status']='BLOCKED_HOST_PARENT';return out
    if binding['boot']!=scope.boot or binding['invocation']!=scope.invocation or binding['cgroup_id']!=list(scope.c):out['gaps'].append('scope channel mismatch')
    if len(valid)<2 or len(valid)!=len(samples) or valid[0].get('phase')!='creation' or valid[-1].get('phase')!='post_drain':out['gaps'].append('counter lifecycle')
    evidence=r.get('lifecycle',[])
    if not isinstance(evidence,list) or [x.get('event') for x in evidence if isinstance(x,dict)]!=list(STAGES):out['gaps'].append('lifecycle incomplete/order')
    else:
        previous=-1
        for x in evidence:
            if not bound(x) or x.get('origin')!='fixed-parent-observer' or type(x.get('mono_ns')) is not int or x['mono_ns']<=previous:out['gaps'].append('unbound lifecycle/time')
            previous=x.get('mono_ns',-1)
    gates=('held_image_hashed','sealed_image','same_fd_exec','postexec_image_proven','preexec_seal',
           'postexec_seal','filter_verified','scratch_fds_verified','fd_allowlist_verified',
           'all_forks_observed','reparent_observed','persistent_alive_before_kill','descendant_pidfd_bound',
           'descendant_membership','same_fd_final','final_after_zero','reaped','removed','logs_sealed','manifest_sealed')
    if any(r.get(k) is not True for k in gates):out['gaps'].append('missing mandatory independent evidence')
    if r.get('cancelled') is not False or r.get('deadline_exceeded') is not False or r.get('observer_gaps')!=[]:out['gaps'].append('cancellation/timeout/observation gaps')
    rows=r.get('helper_rows',[])
    if not isinstance(rows,list):rows=[]
    for case in DENIALS:
        parent=[x for x in observed if isinstance(x,dict) and x.get('event')==case]
        helper=[x for x in rows if isinstance(x,dict) and x.get('case')==case]
        if len(parent)!=1 or len(helper)!=1:out['gaps'].append('missing/duplicate denial '+case);continue
        x,h=parent[0],helper[0]
        identity=x.get('process')
        if not bound(x) or x.get('origin')!='fixed-parent-observer' or x.get('image')!=expected_image or identity not in r.get('owned_processes',[]) or x.get('pidfd_bound') is not True or not isinstance(identity,list) or len(identity)!=2 or any(type(v) is not int or v<=0 for v in identity):out['gaps'].append('unbound denial '+case);continue
        if type(x.get('ret')) is not int or x['ret']!=-1 or type(x.get('errno')) is not int or x['errno'] not in (1,13):out['gaps'].append('ambiguous denial '+case)
        if h.get('ret')!=x.get('ret') or h.get('errno')!=x.get('errno') or h.get('pid')!=identity[0] or h.get('run')!=binding['run']:out['gaps'].append('helper/kernel disagreement '+case)
        if case=='migration' and (x.get('target')!=list(scope.s) or x.get('membership_before')!=list(scope.c) or x.get('membership_after')!=list(scope.c)):out['gaps'].append('wrong S or changed C')
    if not out['gaps']:out['status']='SYNTHETIC_NEGATIVE_CONTROL_COMPLETE'
    return out
