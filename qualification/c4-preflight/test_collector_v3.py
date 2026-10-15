"""Local fixtures for V3 refusals. No actual supervisor, cgroup, JVM or worker."""
import copy
import base64
import importlib.util
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import test_collector_v2 as v2
from containment_model import ContainmentModel, Refusal
from memory_collector import sample, assess

class V3Tests(unittest.TestCase):
    def setUp(self):
        self.f=v2.V2Tests('test_d1_positive_across_gap');self.f.setUp();self.addCleanup(self.f.doCleanups)
        self.p=self.f.p

    def test_F1_forged_dict_cannot_pass(self):
        for proof in [{'qualifying':True},{'qualifying':False},{},None,object()]:
            self.assertEqual(self.p.qualification_status({'status':'OBSERVED_ZERO','qualifying':True},{'status':'NO_FATAL_SIGNATURE_OBSERVED','complete':True},proof,True),'BLOCKED')

    def test_F2_five_fatal_formats(self):
        cases=['e: java.lang.OutOfMemoryError: GC overhead limit exceeded','> java.lang.OutOfMemoryError: Java heap space','# There is insufficient memory for the Java Runtime Environment to continue.','Expiring Daemon because JVM heap space is exhausted','Could not connect to kotlin daemon. Using fallback strategy.']
        for line in cases:
            with self.subTest(line=line):self.assertEqual(self.p.scan_failure_logs([self.f.log(line,'compiler')],('compiler',))['status'],'FAIL')

    def test_F3_echo_simulation_not_definitive_fatal(self):
        for line in ['Gradle build daemon disappeared unexpectedly (simulated)','echo Gradle build daemon disappeared unexpectedly','example: java.lang.OutOfMemoryError: heap']:
            with self.subTest(line=line):
                r=self.p.scan_failure_logs([self.f.log(line,'compiler')],('compiler',));self.assertNotEqual(r['status'],'FAIL');self.assertFalse(r['complete'])

    def test_B12_empty_attributed_log(self):
        for text in ['', ' \n']:
            r=self.p.scan_failure_logs([self.f.log(text,'compiler')],('compiler',));self.assertEqual(r['status'],'INCOMPLETE');self.assertFalse(r['complete']);self.assertIn('empty',r['gaps'][0]['reason'])

    def test_B1_attributed_failure_in_annotated_line(self):
        r=self.p.scan_failure_logs([self.f.log('e: java.lang.OutOfMemoryError: heap','compiler')],('compiler',));self.assertEqual(r['status'],'FAIL')

    def test_B10_parent_start_regression_explicit(self):
        t=self.f.tracker();r=self.f.observe(t,self.f.row(),self.f.row(101,9,100,java=True));self.assertTrue(any('parent start-tick regression' in str(x) for x in r['issues']));self.assertFalse(r['jvm_seen'])

    def test_parent_pid_reuse_does_not_bind_new_child(self):
        t=self.f.tracker();self.f.observe(t,self.f.row());r=self.f.observe(t,self.f.row(start=20),self.f.row(101,21,100,java=True));self.assertFalse(r['jvm_seen']);self.assertNotIn(101,r['relevant_pids'])

    def test_missing_kotlin_log_is_not_clean(self):
        r=self.p.scan_failure_logs([self.f.log('compiler OK','compiler'),self.f.log('daemon OK','gradle')]);self.assertEqual(r['status'],'INCOMPLETE');self.assertTrue(any(x.get('kind')=='kotlin' for x in r['gaps']))

    def test_positive_intervals_not_misreported_as_aggregate(self):
        a,missing,b=self.f.sequence();self.f.f.events(2,2);c=sample(self.f.f.reader)
        r=assess([a,missing,b,c]);self.assertEqual(r['status'],'OOM_EVENTS_OBSERVED');self.assertEqual(len(r['positive_evidence']),2);self.assertIn('never an aggregate',r['delta_semantics']);self.assertFalse(r['intervals_complete'])

    def test_no_subprocess_backend_or_forged_gate_activation(self):
        self.assertNotIn('subprocess',self.p.__dict__)
        with patch.object(self.p,'sample',return_value=sample(self.f.f.reader)),patch.object(self.p,'qualification_status',return_value='PASS'),patch.object(self.p,'run',side_effect=AssertionError('must never execute')) as run:
            self.assertEqual(self.p.main(),2);run.assert_not_called()

    def test_denial_hook_never_executes(self):
        with self.assertRaisesRegex(RuntimeError,'no trusted supervisor'):self.p.run(['java','-version'])

    def test_existing_evidence_directory_refused(self):
        with patch.dict(os.environ,RUNNER_TEMP=self.f.tmp.name,C4_REVISION='R5'):
            spec=importlib.util.spec_from_file_location('duplicate_preflight',Path(__file__).with_name('preflight.py'))
            with self.assertRaises(FileExistsError):spec.loader.exec_module(importlib.util.module_from_spec(spec))

    def test_raw_log_bytes_retained(self):
        raw='e: java.lang.OutOfMemoryError: heap\n';r=self.p.scan_failure_logs([self.f.log(raw,'compiler')],('compiler',));self.assertEqual(base64.b64decode(r['logs'][0]['raw_base64']),raw.encode())

class ModelTests(unittest.TestCase):
    def facts(self):
        keys=('controller_exists delegation_verified mount_identity_verified memory_controller hierarchical_events exclusive_new_group fresh_output fresh_home fresh_gradle_home fresh_tmp fresh_xdg supervisor_outside supervisor_evidence_protected kill_supported ancestor_migration_denied lifecycle_channel_exclusive').split()
        return dict(dict.fromkeys(keys,True),run_id='synthetic-unique-1',source='frozen-source',manifest='manifest',version=2,group='/exclusive/run',populated=0,boot_id='boot',cgroup_identity=[1,2],mount_identity=[1,3],launcher_identity=[99,10],counters={'oom':0,'oom_kill':0},tick=0)

    def model(self):return ContainmentModel('frozen-source','manifest',set())

    def created(self):
        m=self.model();m.create(self.facts());return m

    def worker(self,m):
        keys='joined_before_exec namespace_view_verified no_userns_privilege_recovery no_external_net_route no_external_unix_ipc private_daemon_endpoints daemon_protocol_versions_verified cold_compile_semantics_preserved'.split()
        return dict(dict.fromkeys(keys,True),identity=m.identity,uid=1001,gid=1001,groups=[],capabilities=[],no_new_privs=1,writable_cgroup_procs=False,ancestor_write=False)

    def running(self):
        m=self.created();m.preexec(self.worker(m));return m

    def audited(self):
        m=self.running();m.audit(dict(identity=m.identity,namespace_visible=True,inside=True,start_identity_verified=True,pinned_java_inode=True,launch_bound=True));return m

    def drain(self,m):return dict(identity=m.identity,supervisor_outside=True,kill_supported=True,bounded=True,populated=0,counters={'oom':0,'oom_kill':0},tick=1,counter_handles_held=True,evidence_sealed_before_remove=True)

    def test_all_modeled_prerequisites_still_not_kernel_proof(self):
        m=self.audited();m.launcher_exit(m.identity);m.drained(self.drain(m));r=m.disposition();self.assertEqual(r['model_state'],'SEALED');self.assertFalse(r['qualifying']);self.assertEqual(r['status'],'BLOCKED_D2')

    def test_missing_controller_delegation_root_shared_prepopulated(self):
        for key,value in [('controller_exists',False),('delegation_verified',False),('group','/'),('version',1),('exclusive_new_group',False),('populated',1),('memory_controller',False),('hierarchical_events',False),('mount_identity_verified',False),('fresh_output',False)]:
            with self.subTest(key=key):
                f=self.facts();f[key]=value;m=self.model()
                with self.assertRaises(Refusal):m.create(f)
                self.assertFalse(m.disposition()['qualifying'])

    def test_source_manifest_run_identity_mismatch(self):
        for key in ['source','manifest','run_id']:
            with self.subTest(key=key):
                f=self.facts();f[key]=None
                with self.assertRaises(Refusal):self.model().create(f)
        m=self.created();n=ContainmentModel('frozen-source','manifest',m.used)
        with self.assertRaisesRegex(Refusal,'reused'):n.create(self.facts())

    def test_nonzero_initial_events_are_failure_not_fresh(self):
        f=self.facts();f['counters']['oom']=1;m=self.model()
        with self.assertRaises(Refusal):m.create(f)
        self.assertEqual(m.disposition()['model_outcome'],'FAIL')

    def test_privilege_capability_and_none_uncertainty(self):
        for key,value in [('uid',0),('gid',None),('groups',[0]),('capabilities',['CAP_SYS_ADMIN']),('no_new_privs',None),('joined_before_exec',False),('namespace_view_verified',False),('no_userns_privilege_recovery',False)]:
            with self.subTest(key=key):
                m=self.created();f=self.worker(m);f[key]=value
                with self.assertRaises(Refusal):m.preexec(f)

    def test_migration_writability_refused(self):
        for key in ['writable_cgroup_procs','ancestor_write']:
            m=self.created();f=self.worker(m);f[key]=True
            with self.assertRaises(Refusal):m.preexec(f)

    def test_fresh_homes_do_not_replace_ipc_isolation(self):
        for key in ['no_external_net_route','no_external_unix_ipc','private_daemon_endpoints','daemon_protocol_versions_verified','cold_compile_semantics_preserved']:
            with self.subTest(key=key):
                m=self.created();f=self.worker(m);f[key]=False
                with self.assertRaises(Refusal):m.preexec(f)

    def test_external_same_and_different_jdk_daemon_refused(self):
        for jdk in ['same-pinned-inode','different-JDK']:
            with self.subTest(jdk=jdk):
                m=self.running()
                with self.assertRaises(Refusal):m.external_ipc(dict(jdk=jdk,connection_denied=False))

    def test_transient_reparented_doublefork_are_not_polling_proof(self):
        for kind in ['transient','reparented','double-forked']:
            m=self.running();m.audit(dict(identity=m.identity,kind=kind,namespace_visible=True,inside=True,start_identity_verified=True,pinned_java_inode=True,launch_bound=True));self.assertFalse(m.disposition()['qualifying'])

    def test_hidden_namespace_migration_or_pid_reuse(self):
        for key in ['namespace_visible','inside','start_identity_verified']:
            m=self.running();f=dict(identity=m.identity,namespace_visible=True,inside=True,start_identity_verified=True);f[key]=False
            with self.assertRaises(Refusal):m.audit(f)

    def test_counter_disappearance_reset_or_identity_change(self):
        for values,tick,identity in [(None,1,None),({'oom':-1,'oom_kill':0},1,None),({'oom':0,'oom_kill':0},1,{'forged':True})]:
            m=self.running()
            with self.assertRaises(Refusal):m.counter(values,tick,m.identity if identity is None else identity)
        m=self.running();m.counter({'oom':2,'oom_kill':1},1,m.identity)
        with self.assertRaises(Refusal):m.counter({'oom':1,'oom_kill':0},2,m.identity)
        self.assertEqual(m.disposition()['model_outcome'],'FAIL')

    def test_positive_oom_and_gap_both_retained(self):
        m=self.running();m.counter({'oom':1,'oom_kill':1},1,m.identity)
        with self.assertRaises(Refusal):m.counter(None,2,m.identity)
        self.assertEqual(m.disposition()['model_outcome'],'FAIL');self.assertTrue(m.disposition()['gaps'])

    def test_kill_absent_or_supervisor_inside(self):
        for key in ['kill_supported','supervisor_outside']:
            f=self.facts();f[key]=False
            with self.assertRaises(Refusal):self.model().create(f)

    def test_never_empty_and_final_read_failures(self):
        for key,value in [('populated',1),('bounded',False),('counters',None),('counter_handles_held',False),('evidence_sealed_before_remove',False)]:
            with self.subTest(key=key):
                m=self.audited();m.launcher_exit(m.identity);f=self.drain(m);f[key]=value
                with self.assertRaises(Refusal):m.drained(f)

    def test_empty_inventory_never_proves_jvm_execution(self):
        m=self.running();m.launcher_exit(m.identity)
        with self.assertRaisesRegex(Refusal,'no bound JVM'):m.drained(self.drain(m))

    def test_hashchain_mutation_not_authentication(self):
        m=self.created();r=m.disposition();r['event_chain'][0]['payload']['run_id']='forged';self.assertNotEqual(r['event_chain'][0]['payload'],m.events[0]['payload']);self.assertFalse(m.disposition()['qualifying'])
