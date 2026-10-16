"""V6.1 new-interface source-local tests only. NO host, ELF, CI, or real backend."""
import copy
import json
import os
import unittest
from unittest.mock import patch
import v6_adapter
import v61_scope as m
import v61_observer as o
import v61_watchdog as w
import v61_gate as g
from v6_contract import Blocked, DENIALS, unit_contract

BOOT='12345678-1234-1234-1234-123456789abc'
INV='a'*32
RUN='r'*36
IMAGE=v6_adapter.ELF_SHA256
SOURCE=v6_adapter.SOURCE_SHA256
D='/system.slice/'+unit_contract()['unit_name']

def scope():
    nodes={}
    for name,index,suffix in [('D',1,''),('S',2,'/supervisor'),('C',3,'/probe')]:
        nodes[name]={'fstat_dev_ino':[31,index], 'expected_dev_ino':[31,index],
           'mount_id':88,'statfs':'cgroup2','path':D+suffix,'uid':0,'gid':0,'mode':0o755,
           'acl':[],'control_files_root_only':True,'open_directory_no_symlink':True,
           'fd_stable':True,'direct_pids':[234] if name=='S' else []}
    nodes['S']['manager_created']=True;nodes['C']['exclusive_new']=True
    return {'boot':BOOT,'invocation':INV,'invocation_new':True,
      'source_run_token':RUN,'owner_enable_memory_authority':False,
      'path':D,
      'manager':{'well_known':'org.freedesktop.systemd1','unique_owner':':1.33',
       'before_owner':':1.33','after_owner':':1.33', 'owner_pid':1,'manager_boot':BOOT,
       'version':255,'bus':{'path':'/run/dbus/system_bus_socket','uid':0,'peer_uid':0,'peer_pid':111},
       'introspected_properties':list(m._REQUIRED_BUS_PROPERTIES),
       'introspected_methods':list(m._REQUIRED_MANAGER_METHODS)},
      'effective':{'Id':unit_contract()['unit_name'],'InvocationID':INV,
       'ControlGroup':D,'DelegatedControllers':['memory'],'DelegateSubgroup':'supervisor',
       'KillMode':'control-group','SendSIGKILL':True,'Restart':'no',
       'RuntimeMaxSec':60,'TimeoutStopSec':5,'ExecHooks':[],'MainPID':234},
      'process':{'main_pid_start':[234,19999],'supervisor_pid_start':[234,19999],
       'pidfd_matches_start':True,'pidfd_alive':True,'supervisor_in_s':True},
      'namespaces':{'host_cgroup':9,'worker_cgroup':9,'host_mnt':10,
       'worker_mnt':10,'host_user':11,'worker_user':11},
      'mount':{'fs':'cgroup2','root':'/','point':'/sys/fs/cgroup','id':88,'unique':True},
      'held_fds':nodes,'memory_available_at_d':True,'memory_enabled_at_d':True,
      'controller_readback_fresh':True,'ancestor_diff':[],
      'unexpected_control_subtrees':[],'worker_can_write_ancestor':False,
      'worker_can_write_d_control':False}

def observation():
    b={'boot':BOOT,'invocation':INV,'run':RUN,'source_sha256':SOURCE,
       'image_sha256':IMAGE,'d':[31,1],'s':[31,2],'c':[31,3]}
    events=[]
    def add(kind,**k):
        i=len(events)+1
        events.append(dict(binding=b,source='independent-kernel-channel',
                       seq=i,t_ns=i+5,cpu=0,kind=kind,**k))
    for kind in ['fork','exec','exit','reparent','cgroup_attach']:
        add(kind,task=[101,10])
    for i,case in enumerate(DENIALS):
        s='openat' if case=='migration' else o.SYSCALLS[case][0]
        kw=dict(case=case,task=[101,10],cookie=i+1,syscall=s,
                pidfd_start_verified=True)
        if case=='migration':kw.update(object_source='kernel-resolved-file-inode',
                          target=b['s'],target_role='S',filename='cgroup.procs')
        if case in ('unix_socket','unix_socketpair'):kw['domain']='AF_UNIX'
        add('syscall_enter',**kw)
        kw=dict(case=case,task=[101,10],cookie=i+1,syscall=s,
                pidfd_start_verified=True,raw_ret=-13,errno=13)
        if case=='migration':kw.update(membership_before=b['c'],membership_after=b['c'])
        add('syscall_exit',**kw)
    return {'binding':b, 'records':events,
        'provider':{'stopping':False,'uses_ptrace':False,'capabilities':[
            'tracepoint/fentry','syscall_entry_exit','fork_exec_exit','reparent','cgroup_move',
            'fd_target_inode','pidfd_start','exec_image_fd','privilege_seccomp','loss_counters'],
            'attach_success':True,'probe_pinned':True,'source_verified':True,
            'observer_alive_through_drain':True,'online_cpus':[0]},
        'cpus':[{'id':0,'lost':0,'overrun':0,'seq_gaps':0,'attach_ns':1,'detach_ns':40}],
        'times':{'armed':2,'released':4,'last_event':35,'drained':36,'detached':39},
        'coverage_gaps':[],'unknown_processes':[],'unpaired_syscalls':[],
        'helper_only_proof':False,'owned_processes':[[101,10]],
        'all_pidfds_start_verified':True,'all_descendants_accounted':True,
        'fork_graph_verified':True,'reparent_source_verified':True,
        'persistent_descendant_alive_pre_kill':True,
        'postexec_proof_source':'independent-kernel-backed-image-fd',
        'postexec_image_sha256':IMAGE,'postexec_privilege_verified':True,
        'postexec_seccomp_filter_verified':True,'oom_samples':[]}

def watchdog():
    b={'boot':BOOT,'invocation':INV,'unit':unit_contract()['unit_name'],
       'run':RUN,'d':[31,1],'s':[31,2]}
    return {'binding':b,
       'controller_outside_dsc':True,'external_controller_alive_after_supervisor_death':True,
       'controller_root_owned':True,'held_dfd_broker_attested':True,
       'supervisor_pidfd_start_verified':True,'trigger':'SUPERVISOR_DEATH',
       'pre_c_join_covered':True,'pre_pidfd_window_covered':True,
       'stop_method':'held-D-FD-cgroup.kill','numeric_pid_kill':False,'StopUnit_by_name':False,
       'fresh_invocation_before_stop':INV,'fresh_boot_before_stop':BOOT,
       'unit_name_before_stop':b['unit'],'fresh_dfd_id_before_stop':b['d'],
       'no_unit_name_reuse_window':True,'fd_kill_target':b['d'],
       'd_same_fd_at_kill':True,'all_descendants_drained':True,
       'unit_manager_inactive_same_invocation':True,
       'poststop_event_source':'independent-held-D-cgroup.events',
       'bounds':{'armed_ns':10,'released_ns':12,'triggered_ns':13,
           'kill_ns':16,'drained_ns':18,'final_ns':20,'absolute_ns':100},
       'events':[{'stage':x,'binding':b,'origin':'external-watchdog-attested','t_ns':11+i}
                 for i,x in enumerate(w.STAGES)],
       'journal_fsync_verified':True,'stop_receipt_scope':b,
       'observer_loss_logged':True}

class ManagerTests(unittest.TestCase):
    def test_fixture_is_only_a_model(self):
        a=m.assess_model(scope())
        self.assertEqual(a.state,'SYNTHETIC_SCOPE_CONSISTENT')
        self.assertTrue(a.model_only);self.assertFalse(a.runtime_authorized)
        self.assertFalse(a.privileged_fd_issued)
    def test_dict_with_fake_authorized_flag_never_grants_capability(self):
        s=scope();s['authenticated']=True;s['owner_permit']=True
        self.assertFalse(m.assess_model(s).runtime_authorized)
        with self.assertRaises(Blocked):m.authenticated_held_scope(s)
    def test_pid1_or_bus_spoof(self):
        for path,v in [('manager.owner_pid',999),('manager.unique_owner','fake'),
                       ('manager.bus.peer_uid',1000),('manager.before_owner',':1.100'),
                       ('manager.after_owner',':1.100'),('manager.manager_boot','wrong')]:
            x=scope();d=x;parts=path.split('.')
            for p in parts[:-1]:d=d[p]
            d[parts[-1]]=v
            with self.subTest(path=path):self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_version_introspection_required(self):
        for action in (lambda r:r['manager'].update(version=253),
                       lambda r:r['manager'].update(introspected_properties=[]),
                       lambda r:r['manager'].update(introspected_methods=[])):
            x=scope();action(x);self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_effective_delegate_is_not_declared_only(self):
        for k,v in [('DelegatedControllers',[]),('DelegateSubgroup','wrong'),
                    ('ExecHooks',['/bin/sh']),('ControlGroup','/system.slice/foo'),
                    ('MainPID',1),('KillMode','process')]:
            x=scope();x['effective'][k]=v
            with self.subTest(k=k):self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_pid_reuse_start_or_pidfd(self):
        for field,val in [('main_pid_start',[234,20]),('pidfd_matches_start',False),
                          ('pidfd_alive',False),('supervisor_in_s',False)]:
            x=scope();x['process'][field]=val
            with self.subTest(field=field):self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_inode_acl_mode_and_mount(self):
        for field,val in [('fstat_dev_ino',[31,4]),('uid',1000),('acl',['u:1000:rwx']),
                          ('mode',0o777),('mount_id',99),('statfs','tmpfs'),('fd_stable',False)]:
            x=scope();x['held_fds']['D'][field]=val
            with self.subTest(field=field):self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
        x=scope();x['mount']['unique']=False;self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_c_wrong_population_or_namespace(self):
        x=scope();x['held_fds']['C']['direct_pids']=[123];self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
        x=scope();x['namespaces']['worker_cgroup']=12;self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_ancestor_mutation_and_memory(self):
        for field,val in [('ancestor_diff',['changed']),('unexpected_control_subtrees',['.control']),
                          ('memory_enabled_at_d',False),('worker_can_write_d_control',True),
                          ('worker_can_write_ancestor',True)]:
            x=scope();x[field]=val
            with self.subTest(field=field):self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')
    def test_revalidation_detects_new_invocation_and_fd(self):
        a=scope();b=scope();self.assertEqual(m.revalidate_model(a,b).state,'SYNTHETIC_SCOPE_CONSISTENT')
        b['invocation']='b'*32;b['effective']['InvocationID']='b'*32
        self.assertEqual(m.revalidate_model(a,b).state,'BLOCKED_HOST_PARENT')
        b=scope();b['held_fds']['D']['fstat_dev_ino']=[31,11];b['held_fds']['D']['expected_dev_ino']=[31,11]
        self.assertEqual(m.revalidate_model(a,b).state,'BLOCKED_HOST_PARENT')
    def test_bad_malformed_readback_never_crashes(self):
        for x in (None,{},True,[],{'manager':True},dict(scope(),held_fds={'D':3}),dict(scope(),process=None)):
            with self.subTest(x=repr(x)[:20]):self.assertEqual(m.assess_model(x).state,'BLOCKED_HOST_PARENT')

class ObserverTests(unittest.TestCase):
    def test_full_fixture_is_not_runtime_proof(self):
        a=o.evaluate_model(observation());self.assertEqual(a.status,'SYNTHETIC_WITNESS_CONSISTENT')
        self.assertFalse(a.kernel_backend_attested);self.assertFalse(a.runtime_qualified)
    def test_nonstopping_and_attach_cannot_be_omitted(self):
        for key,val in [('stopping',True),('uses_ptrace',True),('attach_success',False),
                        ('observer_alive_through_drain',False),('capabilities',[])]:
            x=observation();x['provider'][key]=val
            with self.subTest(key=key):self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_ring_drop_and_cpu_gap(self):
        for key,val in [('lost',1),('overrun',1),('seq_gaps',1),('attach_ns',5),('detach_ns',37)]:
            x=observation();x['cpus'][0][key]=val
            with self.subTest(key=key):self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
        x=observation();x['provider']['online_cpus']=[0,1]
        self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_unpaired_duplicate_mismatched_cookie(self):
        for mutate in [lambda e:e.pop(),
                       lambda e:e.append(copy.deepcopy(e[-1])),
                       lambda e:e[-1].update(cookie=999)]:
            x=observation();mutate(x['records'])
            self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_wrong_migration_target_s_vs_d(self):
        for key,val in [('target',[31,1]),('target_role','D'),('filename','cgroup.subtree_control'),
                        ('object_source','helper-json')]:
            x=observation();e=next(e for e in x['records'] if e['kind']=='syscall_enter' and e.get('case')=='migration');e[key]=val
            with self.subTest(key=key):self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_sock_family_and_raw_errno(self):
        x=observation();e=next(e for e in x['records'] if e.get('case')=='unix_socket' and e['kind']=='syscall_enter');e['domain']='AF_INET'
        self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
        x=observation();e=next(e for e in x['records'] if e.get('case')=='setuid' and e['kind']=='syscall_exit');e['raw_ret']=-2;e['errno']=2
        self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_image_and_credential_proof(self):
        for key,val in [('postexec_image_sha256','bad'),('postexec_privilege_verified',False),
                        ('postexec_seccomp_filter_verified',False),('all_pidfds_start_verified',False),
                        ('reparent_source_verified',False),('persistent_descendant_alive_pre_kill',False)]:
            x=observation();x[key]=val
            with self.subTest(key=key):self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_helper_rows_do_not_supply_proof(self):
        x=observation();x['helper_rows']=[{'case':i,'ret':-1,'errno':13,'nonce':'x'} for i in DENIALS]
        x['records']=[]
        self.assertEqual(o.evaluate_model(x).status,'INCOMPLETE_OBSERVATION')
    def test_positive_escape_oom_and_denial_success_over_gap(self):
        x=observation();x['coverage_gaps']=['observer died']
        e=next(e for e in x['records'] if e.get('case')=='setuid' and e['kind']=='syscall_exit');e['raw_ret']=0
        self.assertEqual(o.evaluate_model(x).status,'FAIL')
        x=observation();x['cpus'][0]['lost']=99;x['oom_samples']=[{'binding':x['binding'],
                     'source':'independent-held-memory.events','oom':1,'oom_kill':0}]
        self.assertEqual(o.evaluate_model(x).status,'FAIL')
        x=observation();x['records'].append(dict(x['records'][-1],kind='escape'))
        self.assertEqual(o.evaluate_model(x).status,'FAIL')
    def test_observer_host_refusal_and_malformed(self):
        with self.assertRaises(Blocked):o.require_kernel_backend({'ready':True})
        for x in (None,True,[],{},dict(observation(),records='fake')):
            self.assertIn(o.evaluate_model(x).status,('BLOCKED_OBSERVER','INCOMPLETE_OBSERVATION'))

class WatchdogTests(unittest.TestCase):
    def test_model_consistent_but_not_actual_cleanup(self):
        a=w.evaluate_model(watchdog());self.assertEqual(a.status,'SYNTHETIC_TERMINATION_SEQUENCE_CONSISTENT')
        self.assertFalse(a.runtime_qualified);self.assertFalse(a.scope_terminated)
    def test_earliest_supervisor_death_pre_c_and_pre_pidfd(self):
        for trigger in ('PRE_C_JOIN_FAILURE','PRE_PIDFD_FAILURE','SUPERVISOR_DEATH','OBSERVER_LOSS'):
            x=watchdog();x['trigger']=trigger
            if trigger=='PRE_PIDFD_FAILURE':x['supervisor_pidfd_start_verified']=False
            with self.subTest(trigger=trigger):self.assertEqual(w.evaluate_model(x).status,'SYNTHETIC_TERMINATION_SEQUENCE_CONSISTENT')
    def test_reused_unit_and_invocation_stale(self):
        for key,val in [('fresh_invocation_before_stop','b'*32),('fresh_boot_before_stop','wrong'),
                        ('fresh_dfd_id_before_stop',[31,99]),('no_unit_name_reuse_window',False)]:
            x=watchdog();x[key]=val
            with self.subTest(key=key):self.assertEqual(w.evaluate_model(x).status,'INCOMPLETE_CLEANUP')
    def test_never_stopunit_or_numeric_pid_fallback(self):
        for key,val in [('StopUnit_by_name',True),('numeric_pid_kill',True),
                        ('stop_method','StopUnit'),('fd_kill_target',[31,2])]:
            x=watchdog();x[key]=val
            with self.subTest(key=key):self.assertEqual(w.evaluate_model(x).status,'INCOMPLETE_CLEANUP')
    def test_cleanup_death_and_missing_drain(self):
        for key,val in [('external_controller_alive_after_supervisor_death',False),
                        ('pre_c_join_covered',False),('pre_pidfd_window_covered',False),
                        ('all_descendants_drained',False),('journal_fsync_verified',False),
                        ('cleanup_timeout',True),('kill_failed',True)]:
            x=watchdog();x[key]=val
            with self.subTest(key=key):self.assertEqual(w.evaluate_model(x).status,'INCOMPLETE_CLEANUP')
    def test_deadline_and_event_gaps(self):
        x=watchdog();x['bounds']['absolute_ns']=18
        self.assertEqual(w.evaluate_model(x).status,'INCOMPLETE_CLEANUP')
        x=watchdog();x['events'].pop(3)
        self.assertEqual(w.evaluate_model(x).status,'INCOMPLETE_CLEANUP')
    def test_external_host_refusal_and_malformed(self):
        with self.assertRaises(Blocked):w.termination_implementation({'authenticated':True})
        for x in (None,[],{},dict(watchdog(),events='broken')):
            self.assertIn(w.evaluate_model(x).status,('BLOCKED_WATCHDOG','INCOMPLETE_CLEANUP'))

class IntegrationTests(unittest.TestCase):
    def test_consistent_three_models_never_unlock(self):
        x={'scope_before':scope(),'scope_after':scope(),'observer':observation(),'watchdog':watchdog()}
        y=g.assess_synthetic_candidate(x)
        self.assertEqual(y['disposition'],'BLOCKED_V61_RUNTIME')
        self.assertFalse(y['runtime_enforcement_qualified']);self.assertFalse(y['compiler_enabled'])
        self.assertFalse(y['authorization_consumed']);self.assertTrue(y['model_only'])
    def test_fail_model_indicator_keeps_positive_even_if_parent_blocked(self):
        x={'scope_before':{},'scope_after':{},'observer':observation(),'watchdog':watchdog()}
        x['observer']['oom_samples']=[{'binding':x['observer']['binding'],
            'source':'independent-held-memory.events','oom':0,'oom_kill':1}]
        y=g.assess_synthetic_candidate(x)
        self.assertEqual(y['disposition'],'FAIL_MODEL_INDICATOR')
        self.assertFalse(y['runtime_enforcement_qualified'])
    def test_all_entrypoints_dead_even_if_json_claims_authority(self):
        with patch.object(os,'fork',side_effect=AssertionError('unexpected fork')),\
             patch.object(os,'memfd_create',side_effect=AssertionError('unexpected memfd')):
            for func,args in ((g.release_worker,({'permit':True},)),
                              (g.compile_request,({'permit':True},)),
                              (m.authenticated_held_scope,({'source':'bus'},)),
                              (o.require_kernel_backend,({'source':'kernel'},)),
                              (w.termination_implementation,({'permit':True},)),
                              (v6_adapter.supervise_fixed_v6,())):
                with self.subTest(fun=func.__name__),self.assertRaises(Blocked):func(*args)
    def test_no_runtime_enablement_flags_and_old_preflight_unchanged(self):
        self.assertFalse(m.RUNTIME_BACKEND_ENABLED)
        self.assertFalse(o.RUNTIME_OBSERVER_ENABLED)
        self.assertFalse(w.RUNTIME_WATCHDOG_ENABLED)
        self.assertFalse(g.RUNTIME_GATE_ENABLED)
        import hashlib
        from pathlib import Path
        frozen=Path(__file__).with_name('preflight.py').read_bytes()
        self.assertEqual(hashlib.sha256(frozen).hexdigest(),
                         '7f8ff4ae8a512af7f64575171067e6ccd33c1077f31194bb118b40c8b4ea22f0')

if __name__=='__main__':unittest.main()
