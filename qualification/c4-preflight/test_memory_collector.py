"""Synthetic filesystem tests only: no Gradle, JVM, hosted jobs or OOM induction."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from memory_collector import Reader, sample, compare, assess


class CollectorTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.reader = Reader(self.root, pid=42)
        self.put('/proc/sys/kernel/random/boot_id', '00000000-0000-0000-0000-000000000001')
        self.v2()

    def put(self, path, value):
        p = self.root / path.lstrip('/'); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(value)

    def v2(self, group='/job', mount='/sys/fs/cgroup', root='/', directory=None, options='rw'):
        self.put('/proc/self/cgroup', '0::'+group+'\n')
        esc = lambda s: s.replace(' ', '\\040')
        self.put('/proc/self/mountinfo', f'31 20 0:27 {esc(root)} {esc(mount)} rw - cgroup2 cgroup {options}\n')
        self.directory = directory or mount + group
        self.put(self.directory+'/cgroup.procs', '42\n43\n')
        self.events()

    def events(self, oom=0, kills=0):
        self.put(self.directory+'/memory.events', f'low 0\nhigh 0\nmax 0\noom {oom}\noom_kill {kills}\n')

    def pair(self):
        a = sample(self.reader); b = sample(self.reader)
        self.assertTrue(a['available'], a)
        return a, b

    def test_valid_zero(self):
        r=compare(*self.pair()); self.assertEqual(r['status'],'OBSERVED_ZERO'); self.assertTrue(r['qualifying']); self.assertEqual(r['deltas'],{'oom':0,'oom_kill':0})

    def test_nonzero_events(self):
        a=sample(self.reader); self.events(2,1);r=compare(a,sample(self.reader));self.assertEqual(r['status'],'OOM_EVENTS_OBSERVED');self.assertFalse(r['qualifying']);self.assertEqual(r['deltas'],{'oom':2,'oom_kill':1})

    def test_nonzero_baseline_zero_change(self):
        self.events(4,2); self.assertTrue(compare(*self.pair())['qualifying'])

    def test_missing_counter(self):
        (self.root/'sys/fs/cgroup/job/memory.events').unlink();s=sample(self.reader);self.assertFalse(s['available']);self.assertIsNone(s['counters']);self.assertIsNone(compare(s,s)['deltas'])

    def test_root_counter_not_required(self):
        self.assertFalse((self.root/'sys/fs/cgroup/memory.events').exists());self.assertTrue(sample(self.reader)['available'])

    def test_alternative_mount(self):
        self.v2(mount='/run/memory');self.assertEqual(sample(self.reader)['counter_path'],'/run/memory/job/memory.events')

    def test_escaped_mount(self):
        self.v2(mount='/run/memory space');self.assertTrue(sample(self.reader)['available'])

    def test_bind_mount_root(self):
        self.v2(group='/tenant/job',root='/tenant',mount='/run/mem',directory='/run/mem/job');self.assertEqual(sample(self.reader)['mapping'],'mount-root-relative')

    def test_namespace_root(self):
        self.v2(group='/',root='/tenant/job',mount='/run/mem',directory='/run/mem');self.assertTrue(sample(self.reader)['available'])

    def test_wrong_pid(self):
        self.put(self.directory+'/cgroup.procs','7\n');self.assertFalse(sample(self.reader)['available'])

    def test_missing_proc_files(self):
        for name in ['cgroup','mountinfo']:
            with self.subTest(name=name):
                p=self.root/'proc/self'/name;raw=p.read_text();p.unlink();self.assertFalse(sample(self.reader)['available']);p.write_text(raw)

    def test_malformed_counters(self):
        for text in ['oom -1\noom_kill 0','oom NaN\noom_kill 0','oom 1.2\noom_kill 0','oom 0\noom 1\noom_kill 0','oom 0 extra\noom_kill 0','oom_kill 0','']:
            with self.subTest(text=text):
                self.put(self.directory+'/memory.events',text);s=sample(self.reader);self.assertFalse(s['available']);self.assertIsNone(s['counters'])

    def test_unknown_integer_fields_allowed(self):
        self.put(self.directory+'/memory.events','future 1\noom_kill 0\noom 0\n');self.assertTrue(sample(self.reader)['available'])

    def test_counter_decrease(self):
        self.events(2,1);a=sample(self.reader);self.events();r=compare(a,sample(self.reader));self.assertEqual(r['status'],'INCOMPLETE');self.assertIsNone(r['deltas'])

    def test_counter_replacement(self):
        a,b=self.pair();b['counter_identity'][1]+=1;self.assertEqual(compare(a,b)['status'],'INCOMPLETE')

    def test_boot_change(self):
        a,b=self.pair();b['boot_id']='00000000-0000-0000-0000-000000000002';self.assertEqual(compare(a,b)['status'],'INCOMPLETE')

    def test_migration(self):
        a=sample(self.reader);self.v2(group='/other');self.assertEqual(compare(a,sample(self.reader))['status'],'INCOMPLETE')

    def test_bad_sample_order(self):
        a,b=self.pair();b['monotonic_ns']=a['monotonic_ns'];self.assertEqual(compare(a,b)['status'],'INCOMPLETE')

    def test_transient_missing_sample(self):
        a,b=self.pair();missing=dict(a,available=False,counters=None);self.assertEqual(assess([a,missing,b])['status'],'INCOMPLETE')

    def test_valid_sample_series(self):
        self.assertTrue(assess([sample(self.reader) for _ in range(3)])['qualifying'])

    def test_series_event(self):
        a=sample(self.reader);self.events(1,0);self.assertEqual(assess([a,sample(self.reader),sample(self.reader)])['status'],'OOM_EVENTS_OBSERVED')

    def test_one_sample_incomplete(self):
        self.assertFalse(assess([sample(self.reader)])['qualifying'])

    def test_legacy_kill_counter(self):
        self.put('/proc/self/cgroup','4:memory:/job\n0::/job\n')
        self.put('/proc/self/mountinfo','32 20 0:28 / /legacy rw - cgroup cgroup rw,memory\n31 20 0:27 / /unified rw - cgroup2 cgroup rw\n')
        self.put('/legacy/job/cgroup.procs','42\n')
        self.put('/legacy/job/memory.oom_control','oom_kill_disable 0\nunder_oom 0\noom_kill 3\n')
        a=sample(self.reader);self.assertTrue(a['available']);self.assertEqual(a['counters'],{'oom_kill':3});self.assertFalse(compare(a,sample(self.reader))['qualifying'])
        self.put('/legacy/job/memory.oom_control','oom_kill_disable 0\nunder_oom 0\noom_kill 4\n');self.assertEqual(compare(a,sample(self.reader))['status'],'OOM_EVENTS_OBSERVED')

    def test_legacy_failcnt_not_substitute(self):
        self.put('/proc/self/cgroup','4:memory:/job\n');self.put('/proc/self/mountinfo','32 20 0:28 / /legacy rw - cgroup cgroup rw,memory\n')
        self.put('/legacy/job/cgroup.procs','42');self.put('/legacy/job/memory.oom_control','under_oom 0\noom_kill_disable 0');self.put('/legacy/job/memory.failcnt','0');self.assertFalse(sample(self.reader)['available'])

    def test_local_events_scope_rejected(self):
        self.v2(options='rw,memory_localevents');s=sample(self.reader);self.assertTrue(s['available']);self.assertFalse(s['qualifying_scope']);self.assertFalse(compare(s,sample(self.reader))['qualifying'])

    def test_local_file_not_substitute(self):
        p=self.root/'sys/fs/cgroup/job';(p/'memory.events').rename(p/'memory.events.local');self.assertFalse(sample(self.reader)['available'])

    def test_ambiguous_controllers(self):
        self.put('/proc/self/cgroup','0::/job\n4:memory:/job\n');self.put('/proc/self/mountinfo',(self.root/'proc/self/mountinfo').read_text()+'32 20 0:28 / /legacy rw - cgroup cgroup rw,memory\n');self.put('/legacy/job/cgroup.procs','42');self.put('/legacy/job/memory.oom_control','oom_kill 0');self.assertFalse(sample(self.reader)['available'])

    def test_permission_denied(self):
        class Denied(Reader):
            def read(self,name):
                if name.endswith('memory.events'):raise PermissionError('fixture denied')
                return super().read(name)
        self.assertFalse(sample(Denied(self.root,42))['available'])

    def test_discovery_race(self):
        class Moving(Reader):
            reads=0
            def read(self,name):
                s=super().read(name)
                if name=='/proc/self/cgroup':
                    self.reads+=1
                    if self.reads>1:return '0::/moved\n'
                return s
        self.assertFalse(sample(Moving(self.root,42))['available'])

    def test_malformed_discovery(self):
        for text in ['nonsense','0::/../bad','0::/job (deleted)','0::relative','0::/job\n0::/job']:
            with self.subTest(text=text):
                self.put('/proc/self/cgroup',text);self.assertFalse(sample(self.reader)['available'])

    def test_malformed_mountinfo(self):
        self.put('/proc/self/mountinfo','31 20 0:27 / /sys/fs/cgroup rw cgroup2 cgroup rw');self.assertFalse(sample(self.reader)['available'])


if __name__=='__main__':
    unittest.main(verbosity=2)
