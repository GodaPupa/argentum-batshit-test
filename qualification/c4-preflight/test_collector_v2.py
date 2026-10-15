"""Adversarial local fixtures; no real children, JVMs, cgroup writes or OOMs."""
import copy
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import test_memory_collector as legacy
from memory_collector import sample, compare, assess, ProcessScope, process_inventory, Reader

class V2Tests(unittest.TestCase):
    def setUp(self):
        self.f = legacy.CollectorTests('test_valid_zero'); self.f.setUp()
        self.addCleanup(self.f.doCleanups)
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        with patch.dict(os.environ,RUNNER_TEMP=self.tmp.name,C4_REVISION='R5'):
            spec=importlib.util.spec_from_file_location('controller_v2',Path(__file__).with_name('preflight.py'))
            self.p=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.p)

    def sequence(self):
        a=sample(self.f.reader);self.f.events(1,1);b=sample(self.f.reader)
        return a,dict(a,available=False,counters=None),b

    def test_d1_positive_across_gap(self):
        r=assess(self.sequence());self.assertEqual(r['status'],'OOM_EVENTS_OBSERVED');self.assertFalse(r['intervals_complete']);self.assertFalse(r['qualifying']);self.assertEqual(r['deltas'],{'oom':1,'oom_kill':1});self.assertTrue(r['gaps'])

    def test_d1_changed_identity(self):
        a,m,b=self.sequence();b['boot_id']='other';r=assess([a,m,b]);self.assertEqual(r['status'],'INCOMPLETE');self.assertIsNone(r['deltas'])

    def test_d1_malformed_endpoint(self):
        for value in [None,{}, {'oom':-1,'oom_kill':1},{'oom':True,'oom_kill':1}]:
            a,m,b=self.sequence();b['counters']=value;self.assertEqual(assess([a,m,b])['status'],'INCOMPLETE')

    def test_d1_reset(self):
        a,m,b=self.sequence();a['counters']={'oom':3,'oom_kill':3};self.assertEqual(assess([a,m,b])['status'],'INCOMPLETE')

    def test_d1_gap_contains_identity_contradiction(self):
        a,m,b=self.sequence();m['group']='/elsewhere';self.assertEqual(assess([a,m,b])['status'],'INCOMPLETE')

    def test_d1_mixed_scope(self):
        a,m,b=self.sequence();b['scope']='local_or_kills_only';b['qualifying_scope']=False;self.assertEqual(assess([a,m,b])['status'],'INCOMPLETE')

    def test_positive_interval_survives_bad_endpoint(self):
        a,_,b=self.sequence();end=dict(b,available=False,counters=None);self.assertEqual(assess([a,b,end])['status'],'OOM_EVENTS_OBSERVED')

    def test_local_zero_and_positive_named(self):
        self.f.v2(options='rw,memory_localevents');a=sample(self.f.reader);self.assertEqual(compare(a,sample(self.f.reader))['status'],'INSUFFICIENT_SCOPE');self.f.events(1,0);self.assertEqual(compare(a,sample(self.f.reader))['status'],'LOCAL_OOM_EVENTS_OBSERVED')

    def test_legacy_zero_and_kills_named(self):
        self.f.put('/proc/self/cgroup','4:memory:/job\n');self.f.put('/proc/self/mountinfo','32 20 0:28 / /legacy rw - cgroup cgroup rw,memory\n');self.f.put('/legacy/job/cgroup.procs','42');self.f.put('/legacy/job/memory.oom_control','oom_kill 0');a=sample(self.f.reader);self.assertEqual(compare(a,sample(self.f.reader))['status'],'INSUFFICIENT_SCOPE');self.f.put('/legacy/job/memory.oom_control','oom_kill 1');self.assertEqual(compare(a,sample(self.f.reader))['status'],'OOM_KILLS_OBSERVED')

    def test_optional_diagnostics_resolved_and_nonqualifying(self):
        self.f.v2(mount='/other');self.f.put('/other/job/memory.current','12\n');self.f.put('/other/job/memory.max','max\n');s=sample(self.f.reader);self.assertEqual(s['diagnostics']['memory.current']['value'],12);self.assertEqual(s['diagnostics']['memory.max']['value'],'max');self.assertFalse(s['diagnostics']['memory.peak']['available']);self.assertTrue(s['available'])

    def test_optional_diagnostic_malformed(self):
        self.f.put('/sys/fs/cgroup/job/memory.peak','NaN');s=sample(self.f.reader);self.assertTrue(s['available']);self.assertEqual(s['diagnostics']['memory.peak']['raw'],'NaN');self.assertFalse(s['diagnostics']['memory.peak']['available'])

    def test_optional_diagnostic_permission_denied(self):
        class Denied(Reader):
            def read(self,path):
                if path.endswith('memory.max'): raise PermissionError('synthetic denied')
                return super().read(path)
        s=sample(Denied(self.f.root,42));self.assertTrue(s['available']);self.assertFalse(s['diagnostics']['memory.max']['available']);self.assertIn('denied',s['diagnostics']['memory.max']['reason'])

    def row(self,pid=100,start=10,ppid=1,group='/job',java=False):
        return dict(pid=pid,start=start,ppid=ppid,session=100,group=group,exe_identity=[1,2] if java else [1,3],namespace=[4,5],raw_cgroup='0::'+group,raw_stat='fixture',cmdline='java impostor' if not java else 'opaque executable args')

    def tracker(self):
        return ProcessScope(sample(self.f.reader),(100,10),[1,2],[4,5])

    def observe(self,t,*rows): return t.observe(dict(processes=list(rows),errors=[]))

    def test_nested_subtree_jvm_observed_but_blocked(self):
        t=self.tracker();r=self.observe(t,self.row(),self.row(101,11,100,'/job/child',True));self.assertTrue(r['jvm_seen']);self.assertFalse(r['qualifying']);self.assertFalse(r['issues']);self.assertEqual(t.assessment()['status'],'BLOCKED')

    def test_outside_jvm(self):
        r=self.observe(self.tracker(),self.row(),self.row(101,11,100,'/job-other',True));self.assertTrue(any('out-of-subtree' in str(x) for x in r['issues']));self.assertFalse(r['qualifying'])

    def test_reparented_jvm_remains_bound(self):
        t=self.tracker();self.observe(t,self.row(),self.row(101,11,100,java=True));r=self.observe(t,self.row(),self.row(101,11,1,java=True));self.assertIn(101,r['relevant_pids']);self.assertFalse(r['qualifying'])

    def test_migrated_daemon(self):
        t=self.tracker();self.observe(t,self.row(),self.row(101,11,100,java=True));r=self.observe(t,self.row(),self.row(101,11,1,'/outside',True));self.assertTrue(any('migration' in str(x) for x in r['issues']))

    def test_pid_reuse(self):
        t=self.tracker();self.observe(t,self.row(),self.row(101,11,100,java=True));r=self.observe(t,self.row(),self.row(101,19,1,java=True));self.assertTrue(any('reuse' in str(x) for x in r['issues']))

    def test_unbound_daemon(self):
        r=self.observe(self.tracker(),self.row(),self.row(101,11,1,java=True));self.assertTrue(any('unbound JVM' in str(x) for x in r['issues']));self.assertFalse(r['jvm_seen'])

    def test_no_jvm_and_name_spoof(self):
        t=self.tracker();self.observe(t,self.row());self.assertIn('no bound JVM observed',t.assessment()['issues'])

    def test_duplicate_pid(self):
        r=self.observe(self.tracker(),self.row(),self.row());self.assertTrue(any('duplicate' in str(x) for x in r['issues']))

    def test_vanished_daemon(self):
        t=self.tracker();self.observe(t,self.row(),self.row(101,11,100,java=True));r=self.observe(t,self.row());self.assertTrue(any('vanished' in str(x) for x in r['issues']))

    def test_namespace_change(self):
        t=self.tracker();r=self.row(101,11,100,java=True);r['namespace']=[4,6];self.assertTrue(self.observe(t,self.row(),r)['issues'])

    def test_inventory_errors_block(self):
        t=self.tracker();r=t.observe({'processes':[],'errors':[{'pid':123,'reason':'permission denied'}]});self.assertFalse(r['qualifying']);self.assertTrue(r['issues'])

    def fake_proc(self):
        fields=['S','1','100','100']+['0']*15+['10']
        self.f.put('/proc/100/stat','100 (fixture) '+' '.join(fields))
        self.f.put('/proc/100/cgroup','0::/job\n');self.f.put('/proc/100/exe','fixture');self.f.put('/proc/100/ns/cgroup','fixture');self.f.put('/proc/100/cmdline','fixture\x00')

    def test_proc_raw_identity_record(self):
        self.fake_proc();r=process_inventory(self.f.reader);self.assertFalse(r['errors']);self.assertEqual(r['processes'][0]['start'],10);self.assertIn('raw_cgroup',r['processes'][0])

    def test_proc_identity_race(self):
        self.fake_proc()
        class Race(Reader):
            count=0
            def read(self,path):
                s=super().read(path)
                if path.endswith('/stat'):
                    self.count+=1
                    if self.count>1:return s[:-2]+'99'
                return s
        r=process_inventory(Race(self.f.root,42));self.assertTrue(r['errors']);self.assertFalse(r['processes'])

    def test_proc_missing_identity(self):
        self.fake_proc();(self.f.root/'proc/100/exe').unlink();r=process_inventory(self.f.reader);self.assertTrue(r['errors'])

    def log(self,text,kind='gradle',bound=True):
        p=Path(self.tmp.name)/(kind+'.log');p.write_text(text);return dict(path=str(p),kind=kind,source_bound=bound)

    def test_daemon_heap_oom(self):
        r=self.p.scan_failure_logs([self.log('java.lang.OutOfMemoryError: Java heap space\n')],('gradle',));self.assertEqual(r['status'],'FAIL');self.assertTrue(r['findings'][0]['sha256'])

    def test_fatal_hs_err(self):
        r=self.p.scan_failure_logs([self.log('# A fatal error has been detected by the Java Runtime Environment:\n','crash')],('crash',));self.assertEqual(r['status'],'FAIL')

    def test_fatal_survives_missing_logs(self):
        r=self.p.scan_failure_logs([self.log('Caused by: java.lang.OutOfMemoryError: GC overhead limit exceeded\n')]);self.assertEqual(r['status'],'FAIL');self.assertTrue(r['gaps']);self.assertFalse(r['complete'])

    def test_kotlin_fallback(self):
        r=self.p.scan_failure_logs([self.log('Using fallback strategy: Compile without Kotlin daemon\n','kotlin')],('kotlin',));self.assertEqual(r['status'],'FAIL')

    def test_harmless_oom_documentation(self):
        r=self.p.scan_failure_logs([self.log('INFO setting -XX:+HeapDumpOnOutOfMemoryError\nINFO documentation: java.lang.OutOfMemoryError example\n')],('gradle',));self.assertEqual(r['status'],'NO_FATAL_SIGNATURE_OBSERVED')

    def test_missing_required_logs(self):
        r=self.p.scan_failure_logs([]);self.assertEqual(r['status'],'INCOMPLETE');self.assertFalse(r['complete'])

    def test_unattributed_fatal_log(self):
        r=self.p.scan_failure_logs([self.log('java.lang.OutOfMemoryError: Java heap space','kotlin',False)]);self.assertEqual(r['status'],'INCOMPLETE');self.assertFalse(r['findings'])

    def test_unreadable_log(self):
        r=self.p.scan_failure_logs([dict(path=self.tmp.name+'/missing',kind='gradle',source_bound=True)]);self.assertEqual(r['status'],'INCOMPLETE')

    def test_positive_oom_wins_uncertainty(self):
        r=self.p.qualification_status(assess(self.sequence()),dict(status='INCOMPLETE',complete=False),dict(qualifying=False),False);self.assertEqual(r,'FAIL')

    def test_compilation_blocked_before_fetch(self):
        with patch.object(self.p,'sample',return_value=sample(self.f.reader)),patch.object(self.p,'run',side_effect=AssertionError('no subprocess')) as run:
            self.assertEqual(self.p.main(),2);run.assert_not_called()
        self.assertIn('BLOCKED_D2',(Path(self.tmp.name)/'c4-evidence/result.json').read_text())
