"""No privileged host calls. Pure checks and mocked kernel boundaries only."""
import copy
import errno
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import kernel_supervisor as k

class SupervisorTests(unittest.TestCase):
    def seal(self):
        return {'Uid':'1001 1001 1001 1001','Gid':'1002 1002 1002 1002','Groups':'','CapInh':'0','CapPrm':'0','CapEff':'0','CapBnd':'0','CapAmb':'0','NoNewPrivs':'1','Seccomp':'2'}

    def validate(self,status=None,identity=None,group='/exclusive',ns=None):
        return k.validate_seal(status or self.seal(),identity or [101,99],[101,99],group,'/exclusive',ns or {'net':2,'ipc':4,'mnt':6},{'net':1,'ipc':3,'mnt':5})

    def test_valid_preexec_seal(self):self.validate()

    def test_root_or_saved_uid_and_gid_refused(self):
        for field in ['Uid','Gid']:
            for v in ['0 0 0 0','1001 1001 0 1001']:
                s=self.seal();s[field]=v
                with self.assertRaises(k.Refusal):self.validate(s)

    def test_each_capability_set_must_be_zero(self):
        for field in ['CapInh','CapPrm','CapEff','CapBnd','CapAmb']:
            s=self.seal();s[field]='1'
            with self.assertRaises(k.Refusal):self.validate(s)

    def test_groups_nnp_and_filter_required(self):
        for field,v in [('Groups','0'),('NoNewPrivs','0'),('Seccomp','0')]:
            s=self.seal();s[field]=v
            with self.assertRaises(k.Refusal):self.validate(s)

    def test_pid_reuse(self):
        with self.assertRaisesRegex(k.Refusal,'PID reuse'):self.validate(identity=[101,100])

    def test_migration(self):
        with self.assertRaisesRegex(k.Refusal,'escaped'):self.validate(group='/outside')

    def test_external_network_ipc_mount_namespaces(self):
        for name,v in [('net',1),('ipc',3),('mnt',5)]:
            ns={'net':2,'ipc':4,'mnt':6};ns[name]=v
            with self.assertRaisesRegex(k.Refusal,'namespace'):self.validate(ns=ns)

    def test_seccomp_forbids_privilege_namespace_and_external_unix(self):
        rules=k.seccomp_rules();names={r[0] for r in rules};self.assertTrue({'unshare','setns','mount','ptrace','pidfd_getfd','bpf','io_uring_setup'}<=names)
        self.assertIn(('clone3',errno.ENOSYS,None),rules)
        flags={r[2][2] for r in rules if r[0]=='clone'}
        self.assertEqual(flags,{0x02000000,0x04000000,0x08000000,0x10000000,0x20000000,0x40000000,0x20000})
        self.assertIn(('socket',errno.EPERM,(0,4,1,0)),rules);self.assertIn(('socketpair',errno.EPERM,(0,4,1,0)),rules)

    def test_compile_has_no_runtime_override(self):
        with patch.object(k.os,'fork',side_effect=AssertionError('no fork')):
            for arg in [True,{'qualifying':True},'approved']:
                with self.assertRaisesRegex(k.Refusal,'DISABLED'):k.compile_request(arg)

    def test_nonroot_supervisor_refuses_before_operations(self):
        with patch.object(k.os,'geteuid',return_value=1001),patch.object(k.os,'fork',side_effect=AssertionError('no fork')):
            with self.assertRaisesRegex(k.Refusal,'root'):k.supervise_probe('/missing','/missing/out',1001,1001)

    def test_counter_lifetime_zero(self):
        with patch.object(k,'file_identity',return_value=[1,2]),patch.object(k.Path,'read_text',return_value='boot'),patch.object(k,'read_fd',return_value='oom 0\noom_kill 0\n'):
            life=k.CounterLifetime(7,'boot');life.sample();life.sample();self.assertEqual(life.result()['status'],'OBSERVED_ZERO')

    def test_creation_oom_is_fail_not_subtracted_baseline(self):
        with patch.object(k,'file_identity',return_value=[1,2]),patch.object(k.Path,'read_text',return_value='boot'),patch.object(k,'read_fd',return_value='oom 1\noom_kill 1\n'):
            life=k.CounterLifetime(7,'boot')
            with self.assertRaises(k.Refusal):life.sample()
            self.assertEqual(life.result()['status'],'FAIL')

    def test_counter_positive_survives_gap(self):
        with patch.object(k,'file_identity',return_value=[1,2]),patch.object(k.Path,'read_text',return_value='boot'),patch.object(k,'read_fd',side_effect=['oom 0\noom_kill 0',OSError('missing'),'oom 1\noom_kill 1']):
            life=k.CounterLifetime(7,'boot');life.sample()
            with self.assertRaises(OSError):life.sample()
            life.sample();self.assertEqual(life.result()['status'],'FAIL');self.assertTrue(life.result()['gaps'])

    def test_counter_reset_missing_final_identity_or_boot(self):
        for which in ['reset','missing','identity','boot']:
            with self.subTest(which=which),patch.object(k,'file_identity',return_value=[1,2]) as ident,patch.object(k.Path,'read_text',return_value='boot') as boot,patch.object(k,'read_fd',return_value='oom 0\noom_kill 0') as rd:
                life=k.CounterLifetime(7,'boot');life.sample()
                if which=='reset':rd.return_value='oom -1\noom_kill 0'
                if which=='missing':rd.side_effect=FileNotFoundError('missing')
                if which=='identity':ident.return_value=[1,3]
                if which=='boot':boot.return_value='other'
                with self.assertRaises(Exception):life.sample()
                self.assertEqual(life.result()['status'],'INCOMPLETE')

    def test_drain_waits_for_zero_before_final_counter(self):
        life=Mock();life.sample.return_value={'counter':'final'}
        with patch.object(k,'write_at') as write,patch.object(k,'read_fd',side_effect=['populated 1','populated 0']),patch.object(k.time,'sleep'):
            r=k.drain(7,8,life);self.assertTrue(r['drained']);write.assert_called_once_with(7,'cgroup.kill','1');life.sample.assert_called_once()

    def test_drain_absent_kill_and_never_empty(self):
        life=Mock()
        with patch.object(k,'write_at',side_effect=PermissionError('kill absent')):
            with self.assertRaises(PermissionError):k.drain(7,8,life)
        life.sample.assert_not_called()
        with patch.object(k,'write_at'),patch.object(k,'read_fd',return_value='populated 1'),patch.object(k.time,'monotonic',side_effect=[0,16]):
            with self.assertRaisesRegex(k.Refusal,'deadline'):k.drain(7,8,life)
        life.sample.assert_not_called()

    def test_drain_final_read_failure_not_success(self):
        with patch.object(k,'write_at'),patch.object(k,'read_fd',return_value='populated 0'):
            with self.assertRaises(OSError):k.drain(7,8,Mock(sample=Mock(side_effect=OSError('final missing'))))

    def test_root_path_symlink_or_writable_refused(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);(p/'target').mkdir();(p/'alias').symlink_to(p/'target')
            with self.assertRaises(k.Refusal):k.safe_root_path(p/'alias')
            os.chmod(p,0o777) # Explicit unsafe fixture; independent of ancestor ownership.
            with self.assertRaises(k.Refusal):k.safe_root_path(p)

    def test_log_capture_and_fatal_detection(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);logs=p/'logs';logs.mkdir();out=p/'out';out.mkdir();(logs/'kotlin-daemon.log').write_text('e: java.lang.OutOfMemoryError: heap\n');fd=os.open(logs,os.O_RDONLY|os.O_DIRECTORY)
            try:r=k.collect_logs(fd,out,'run','private-tmp')
            finally:os.close(fd)
            self.assertFalse(r['gaps']);self.assertEqual(len(r['files']),1)
            result=k.scan_failure_logs([{'path':r['files'][0]['captured_path'],'kind':'kotlin','source_bound':True}],('kotlin',));self.assertEqual(result['status'],'FAIL')

    def test_log_symlink_hardlink_empty_and_missing_roles(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);logs=p/'logs';logs.mkdir();out=p/'out';out.mkdir();(logs/'external.log').symlink_to('/nonexistent');fd=os.open(logs,os.O_RDONLY|os.O_DIRECTORY)
            try:r=k.collect_logs(fd,out,'run','private');self.assertTrue(r['gaps'])
            finally:os.close(fd)
            self.assertEqual(k.scan_failure_logs([])['status'],'INCOMPLETE')

    def test_handshake_timeout_or_wrong_token(self):
        with patch.object(k.select,'select',return_value=([],[],[])):
            with self.assertRaises(k.Refusal):k.wait_pipe(9,1)
        with patch.object(k.select,'select',return_value=([9],[],[])),patch.object(k.os,'read',return_value=b'X'):
            with self.assertRaises(k.Refusal):k.wait_pipe(9,1)

    def test_worker_seal_order_without_privileged_calls(self):
        import fcntl
        calls=[]
        with patch.object(k,'libc_call',side_effect=lambda name,*args:calls.append((name,args))),patch.object(k,'mount_tmpfs'),patch.object(k.socket,'socket') as sock,patch.object(fcntl,'ioctl',return_value=k.struct.pack('16sH14x',b'lo',0)),patch.object(k.Path,'read_text',return_value='40'),patch.object(k.os,'setgroups',side_effect=lambda x:calls.append(('groups',x))),patch.object(k.os,'setresgid',side_effect=lambda *x:calls.append(('gid',x))),patch.object(k.os,'setresuid',side_effect=lambda *x:calls.append(('uid',x))),patch.object(k,'install_seccomp',side_effect=lambda:calls.append(('seccomp',None))),patch.object(k,'read_status',return_value=self.seal()),patch.dict(os.environ,{},clear=True):
            k.seal_worker(1001,1002,Path('/private'))
        names=[x[0] for x in calls];self.assertEqual(names[0],'unshare');self.assertLess(names.index('groups'),names.index('uid'));self.assertLess(names.index('uid'),names.index('capset'));self.assertLess(names.index('capset'),names.index('seccomp'))
        self.assertIn(('prctl',(38,1,0,0,0)),calls)

    def test_missing_kernel_namespace_fails_before_privilege_drop(self):
        with patch.object(k,'libc_call',side_effect=PermissionError('unshare denied')),patch.object(k.os,'setresuid') as uid:
            with self.assertRaises(PermissionError):k.seal_worker(1001,1002,Path('/private'))
            uid.assert_not_called()

    def test_daemon_empty_log_never_qualifies(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'kotlin-daemon.log';p.write_text('')
            r=k.scan_failure_logs([{'path':str(p),'kind':'kotlin','source_bound':True}],('kotlin',));self.assertEqual(r['status'],'INCOMPLETE')
