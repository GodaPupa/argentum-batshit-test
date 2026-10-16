"""Only offline fixtures/read-only descriptors; no backend, ELF or kernel probe."""
import ast
import copy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import v6_contract as c
import v6_adapter as a

class V6Tests(unittest.TestCase):
    def parent(self):
        boot='12345678-1234-1234-1234-123456789abc';d='/system.slice/fixture.service'
        r={'manager_pid':1,'manager_version':255,'manager_channel':'authenticated-system-bus',
           'unit':c.unit_contract()['unit_name'],'properties':c.unit_contract()['properties'],
           'new_invocation':True,'upstream_diff':[],'invocation':'a'*32,'boot':boot,
           'mount':{'type':'cgroup2','root':'/','point':'/sys/fs/cgroup','id':36,'hierarchical_events':True},
           'host_cgroup_ns':10,'supervisor_cgroup_ns':10,'manager_control_group':d,
           'self_membership':d+'/supervisor','self_identity':[100,20],'main_identity':[100,20],
           'ancestor_acl_verified':True,'ancestor_worker_writable':False,'evidence_fresh':True,
           'worker_uid_reserved':True,'worker_gid_reserved':True,'nodes':{}}
        for name,num,path in [('D',1,d),('S',2,d+'/supervisor'),('C',3,d+'/probe')]:
            r['nodes'][name]={'id':[30,num],'held_fd_id':[30,num],'boot':boot,'mount_id':36,
                              'uid':0,'gid':0,'mode':0o755,'acl':[],'type':'domain',
                              'control_files_root_only':True,'path':path,'direct_pids':[100] if name=='S' else []}
        r['nodes']['D'].update(memory_available=True,memory_enabled=True)
        r['nodes']['S']['manager_created']=True;r['nodes']['C']['fresh']=True
        return r
    def receipt(self):
        p=self.parent();b={'run':'nonce','boot':p['boot'],'invocation':p['invocation'],'counter_id':[30,9],'cgroup_id':[30,3],'image':a.ELF_SHA256}
        r={'parent':p,'binding':b,'samples':[dict(binding=b,scope='v2-hierarchical',seq=i,oom=0,oom_kill=0,phase=phase) for i,phase in enumerate(['creation','post_drain'])],
           'lifecycle':[dict(binding=b,event=n,origin='fixed-parent-observer',mono_ns=i+1) for i,n in enumerate(c.STAGES)],
           'kernel_events':[dict(binding=b,event=n,origin='fixed-parent-observer',image=a.ELF_SHA256,process=[101,30],pidfd_bound=True,ret=-1,errno=13,target=[30,2],membership_before=[30,3],membership_after=[30,3]) for n in c.DENIALS],
           'helper_rows':[dict(case=n,ret=-1,errno=13,pid=101,run='nonce') for n in c.DENIALS],
           'cancelled':False,'deadline_exceeded':False,'observer_gaps':[],'owned_processes':[[101,30]]}
        for key in ('held_image_hashed sealed_image same_fd_exec postexec_image_proven preexec_seal postexec_seal filter_verified scratch_fds_verified fd_allowlist_verified all_forks_observed reparent_observed persistent_alive_before_kill descendant_pidfd_bound descendant_membership same_fd_final final_after_zero reaped removed logs_sealed manifest_sealed').split():r[key]=True
        return r
    def status(self,r):return c.evaluate_model(r,a.ELF_SHA256)['status']
    def reject(self,r):
        with self.assertRaises(c.Blocked):c.validate_scope_model(r)
    def test_good_model_never_authorizes(self):
        r=c.evaluate_model(self.receipt(),a.ELF_SHA256);self.assertEqual(r['status'],'SYNTHETIC_NEGATIVE_CONTROL_COMPLETE')
        for k in ('runtime_enforcement_qualified','compiler_enabled','hosted_permission'):self.assertIs(r[k],False)
    def test_wrong_manager_version_and_channel(self):
        for key,v in [('manager_pid',2),('manager_pid',True),('manager_version',253),('manager_channel','caller-json')]:
            r=self.parent();r[key]=v;self.reject(r)
    def test_wrong_unit_boot_invocation(self):
        for key,v in [('unit','other.service'),('boot','other'),('invocation','bad'),('new_invocation',False)]:
            r=self.parent();r[key]=v;self.reject(r)
    def test_property_drift_no_fallback(self):
        for key,v in [('Delegate',[]),('DelegateSubgroup',''),('Restart','always'),('ExecStart',['/bin/sh']),('RuntimeMaxSec',0),('KillMode','process'),('ExecStop',['cleanup'])]:
            r=self.parent();r['properties'][key]=v;self.reject(r)
    def test_upstream_effects(self):
        r=self.parent();r['upstream_diff']=['system.slice changed'];self.reject(r)
    def test_missing_target_and_shared_parent(self):
        r=self.parent();del r['nodes']['S'];self.reject(r)
        for path in ['/','/system.slice','/system.slice/hosted-compute-agent.service']:
            r=self.parent();r['nodes']['D']['path']=path;r['manager_control_group']=path;self.reject(r)
    def test_permissions_acl_and_control_ownership(self):
        for name in ('D','S','C'):
            for key,v in [('uid',1001),('gid',1001),('mode',0o775),('acl',['user:1001:rwx']),('control_files_root_only',False)]:
                r=self.parent();r['nodes'][name][key]=v;self.reject(r)
    def test_mixed_namespace_mount_or_fd(self):
        r=self.parent();r['supervisor_cgroup_ns']=11;self.reject(r)
        for key,v in [('type','cgroup'),('root','/somewhere'),('hierarchical_events',False)]:
            r=self.parent();r['mount'][key]=v;self.reject(r)
        r=self.parent();r['nodes']['S']['held_fd_id']=[30,99];self.reject(r)
    def test_pid_reuse_population_or_supervisor_inside_c(self):
        r=self.parent();r['self_identity']=[100,21];self.reject(r)
        for name in ('D','S','C'):
            r=self.parent();r['nodes'][name]['direct_pids']=[100,999];self.reject(r)
        r=self.parent();r['self_membership']=r['nodes']['C']['path'];self.reject(r)
    def test_delegation_reserved_uid_evidence_gates(self):
        for name,key in [('D','memory_available'),('D','memory_enabled'),('S','manager_created'),('C','fresh')]:
            r=self.parent();r['nodes'][name][key]=False;self.reject(r)
        for key in ('ancestor_acl_verified','worker_uid_reserved','worker_gid_reserved','evidence_fresh'):
            r=self.parent();r[key]=False;self.reject(r)
    def test_malformed_scope(self):
        for value in [None,[],{'nodes':None},'approved']:
            self.reject(value)
    def test_denial_enoent_ebusy_never_complete(self):
        for err in (2,16,38,0):
            r=self.receipt();r['kernel_events'][0]['errno']=err;r['helper_rows'][0]['errno']=err
            self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_migration_to_d_not_s_or_membership_change(self):
        for key,v in [('target',[30,1]),('target',[30,99]),('membership_before',[30,2]),('membership_after',[30,2])]:
            r=self.receipt();r['kernel_events'][0][key]=v;self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_forged_helper_rows_and_missing_parent_observation(self):
        r=self.receipt();r['kernel_events']=[];self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
        r=self.receipt();r['helper_rows'][0]['ret']=0;self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
        r=self.receipt();r['kernel_events'][0]['origin']='helper';self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_wrong_executed_image_and_handoff(self):
        r=self.receipt();r['binding']=dict(r['binding'],image='wrong');self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
        r=self.receipt();r['kernel_events'][0]['image']='wrapper';self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_unowned_pid_and_pidfd_mismatch(self):
        for key,v in [('process',[101,31]),('pidfd_bound',False)]:
            r=self.receipt();r['kernel_events'][0][key]=v;self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_all_observation_lifecycle_cleanup_gates(self):
        for key,value in self.receipt().items():
            if value is True:
                r=self.receipt();r[key]=False
                with self.subTest(key=key):self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_missing_intermediate_fork_and_reparent(self):
        for name in ('fork','reparent','persistent','postexec','final','manifest'):
            r=self.receipt();r['lifecycle']=[x for x in r['lifecycle'] if x['event']!=name];self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_cancellation_deadline_and_polling_gaps(self):
        for key,v in [('cancelled',True),('deadline_exceeded',True),('observer_gaps',['lost child'])]:
            r=self.receipt();r[key]=v;self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_positive_oom_survives_gaps_and_cleanup_failure(self):
        r=self.receipt();r['samples'].insert(1,None);r['samples'][-1]['oom_kill']=1;r['manifest_sealed']=False;r['cancelled']=True;self.assertEqual(self.status(r),'FAIL')
    def test_positive_creation_reset(self):
        r=self.receipt();r['samples'][0]['oom']=1;self.assertEqual(self.status(r),'FAIL')
    def test_positive_escape_and_forbidden_success(self):
        for event in ['escape','fatal',*c.DENIALS]:
            r=self.receipt();r['kernel_events'][0]['event']=event;r['kernel_events'][0]['ret']=0;r['observer_gaps']=['later gap'];self.assertEqual(self.status(r),'FAIL')
    def test_wrong_counter_identity_boot_or_limited_scope(self):
        for change in [('scope','v1-kills-only'),('scope','v2-local'),('oom',None),('seq',False)]:
            r=self.receipt();r['samples'][-1][change[0]]=change[1];self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
        r=self.receipt();r['samples'][-1]['binding']=dict(r['binding'],counter_id=[30,999]);self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_missing_final_wrong_phase(self):
        r=self.receipt();r['samples'][-1]['phase']='pre_drain';self.assertEqual(self.status(r),'INCOMPLETE_RUNTIME')
    def test_deadlines_not_reset(self):
        self.assertEqual(a.remaining(10,7),3)
        for now in (10,11):
            with self.assertRaises(c.Blocked):a.remaining(10,now)
    def test_runtime_and_compiler_unconditionally_disabled(self):
        with patch.object(a.os,'fork',side_effect=AssertionError('fork forbidden')),patch.object(a.os,'mkdir',side_effect=AssertionError('mkdir forbidden')),patch.object(a.os,'memfd_create',side_effect=AssertionError('memfd forbidden')),patch.object(a.v4,'write_at',side_effect=AssertionError('cgroup write forbidden')),patch.object(a.os,'waitpid',side_effect=AssertionError('wait forbidden')):
            for fun,args in [(a.supervise_fixed_v6,()),(a._enable_memory_inside_d,(None,)),(a._review_body,()),(a._sealed_memfd,('x',b'x',0)),(a._bounded_drain,(1,2,None,10,{})),(a._bounded_reap,(99,10)),(a.compile_request,({'approved':True},))]:
                with self.assertRaises(c.Blocked):fun(*args)
    def test_manager_and_observer_no_json_authority(self):
        for fn in (a._authenticated_manager_scope,a._fixed_parent_observer):
            with self.assertRaises(c.Blocked):fn()
    def test_scope_binary_contract(self):
        data=a.scope_record([30,2],[30,3],1002,1003,b'x'*16);self.assertEqual(len(data),72);self.assertEqual(data[:8],b'ARGC4V6\0')
        for s_id,c_id,nonce in [([30,2],[30,2],b'x'*16),([30,2],[30,3],b'\0'*16)]:
            with self.assertRaises(a.v4.Refusal):a.scope_record(s_id,c_id,1002,1003,nonce)
    def test_held_fd_image_hash_and_inplace_mutation(self):
        image=Path(a.__file__).with_name('adversarial-helper-v6.elf').read_bytes()
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'image';p.write_bytes(image);p.chmod(0o555);fd=os.open(p,os.O_RDONLY)
            try:
                self.assertEqual(a._digest_fd(fd)[0],a.ELF_SHA256)
                # Same inode, different bytes: must fail the exact image check.
                p.chmod(0o755);p.write_bytes(b'bad')
                with self.assertRaises(a.v4.Refusal):a.verify_image_fd(fd)
            finally:os.close(fd)
    def test_hash_read_detects_stat_race(self):
        p=Path(a.__file__).with_name('adversarial-helper-v6.elf');fd=os.open(p,os.O_RDONLY);original=os.fstat(fd)
        class Changed:
            def __getattr__(self,n):return getattr(original,n)+(1 if n=='st_mtime_ns' else 0)
        try:
            with patch.object(a.os,'fstat',side_effect=[original,Changed()]):
                with self.assertRaisesRegex(a.v4.Refusal,'changed'):a._digest_fd(fd)
        finally:os.close(fd)
    def test_exact_elf_and_source_build_manifest(self):
        p=Path(a.__file__).parent;m=json.loads((p/'v6-helper-build.json').read_text())
        self.assertEqual(hashlib.sha256((p/'adversarial-helper-v6.elf').read_bytes()).hexdigest(),a.ELF_SHA256)
        self.assertEqual(hashlib.sha256((p/'adversarial_helper_v6.c').read_bytes()).hexdigest(),a.SOURCE_SHA256)
        self.assertEqual(m['elf_sha256'],a.ELF_SHA256);self.assertTrue(m['rebuild_identical']);self.assertFalse(m['executed'])
    def test_no_unbounded_wait_or_systemd_creation_api(self):
        tree=ast.parse(Path(a.__file__).read_text())
        for n in ast.walk(tree):
            if isinstance(n,ast.Call) and isinstance(n.func,ast.Attribute) and n.func.attr=='waitpid':
                self.assertIsInstance(n.args[1],ast.Attribute);self.assertEqual(n.args[1].attr,'WNOHANG')
        self.assertNotIn('subprocess',Path(a.__file__).read_text())
