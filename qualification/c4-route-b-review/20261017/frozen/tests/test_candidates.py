import asyncio
import copy
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from common import Binding,Blocked,Journal
from manager import ManagerSession,HeldScope,FDIdentity,validate_effective,DBusNextWire
from observer import Capture,LibbpfRing,decode_record,DENIALS
from watchdog import Controller,DFDTermination,Escrow

B=Binding('12345678-1234-1234-1234-123456789abc','a'*32,'b'*32,'c'*64,'d'*64)
class Log:
    def __init__(self):self.rows=[]
    def append(self,k,d):self.rows.append((k,d))
class Backend:
    def __init__(self):self.calls=0;self.pop=True;self.drift=False;self.fail=False
    def revalidate(self):
        if self.drift:raise Blocked('identity drift')
    def kill_once(self):
        self.calls+=1
        if self.fail:raise OSError('uncertain write')
    def populated(self):return self.pop
class Wire:
    peer=(99,0,0)
    def __init__(self):self.owner=':1.0';self.pid=1;self.uid=0;self.calls=[]
    async def call(self,d,p,i,m,s='',body=None):
        self.calls.append(m)
        if m=='GetNameOwner':return [self.owner]
        if m=='GetConnectionCredentials':return [{'ProcessID':self.pid,'UnixUserID':self.uid}]
        raise AssertionError(m)

class ManagerTests(unittest.TestCase):
    def test_bind_and_owner_drift(self):
        async def f():
            w=Wire();m=ManagerSession(w,lambda:B.boot);await m.bind();await m.revalidate();w.owner=':1.1'
            with self.assertRaises(Blocked):await m.revalidate()
        asyncio.run(f())
    def test_wrong_peer_pid_and_uid(self):
        async def f():
            for key,value in [('peer',(99,1000,1000)),('pid',2),('uid',1000)]:
                w=Wire();setattr(w,key,value)
                with self.assertRaises(Blocked):await ManagerSession(w,lambda:B.boot).bind()
        asyncio.run(f())
    def test_boot_drift(self):
        async def f():
            w=Wire();boot=[B.boot];m=ManagerSession(w,lambda:boot[0]);await m.bind();boot[0]='changed'
            with self.assertRaises(Blocked):await m.revalidate()
        asyncio.run(f())
    def test_method_allowlist(self):
        async def f():
            with self.assertRaises(Blocked):await ManagerSession(Wire(),lambda:B.boot).daemon('StopUnit',['x'])
        asyncio.run(f())
    def fixture(self):
        c=json.loads((Path(__file__).parents[1]/'reference/v6-unit-contract.json').read_text())
        s={'Type':'exec','User':'root','Group':'root','Delegate':True,'DelegateControllers':['memory'],'DelegateSubgroup':'supervisor','Restart':'no','KillMode':'control-group','SendSIGKILL':True,'RuntimeMaxUSec':60000000,'TimeoutStopUSec':5000000,'MainPID':1,'ControlGroup':'/system.slice/argentum-c4-v6-probe.service','ExecStart':[['/opt/argentum-c4-v6/supervisor',['/opt/argentum-c4-v6/supervisor'],False,0,0,0,0,0,0,0]]}
        for k in ['ExecStartPre','ExecStartPost','ExecReload','ExecStop','ExecStopPost']:s[k]=[]
        return {'unit':{'Id':B.unit,'InvocationID':list(bytes.fromhex(B.invocation)),'ActiveState':'active'},'service':s},c
    def test_effective_properties(self):
        r,c=self.fixture();self.assertEqual(validate_effective(r,B,c)[1],1)
        for key,value in [('Delegate',False),('DelegateControllers',[]),('DelegateSubgroup','wrong'),('ExecStart',[]),('ExecStop',['evil']),('RuntimeMaxUSec',999999999)]:
            bad=copy.deepcopy(r);bad['service'][key]=value
            with self.subTest(key=key),self.assertRaises(Blocked):validate_effective(bad,B,c)
    def test_stale_invocation(self):
        r,c=self.fixture();r['unit']['InvocationID']=[0]*16
        with self.assertRaises(Blocked):validate_effective(r,B,c)
    def test_fd_scope_and_substitution(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'system.slice/unit/supervisor').mkdir(parents=True);(root/'system.slice/unit/probe').mkdir()
            fd=os.open(root,os.O_RDONLY|os.O_DIRECTORY)
            # Ordinary local directory fixtures, never cgroup operations.
            identity=lambda f:FDIdentity(os.fstat(f).st_dev,os.fstat(f).st_ino,1)
            scope=HeldScope(fd,'/system.slice/unit',identity,lambda *a:None)
            try:
                scope.revalidate();(root/'system.slice/unit/probe').rename(root/'system.slice/unit/old');(root/'system.slice/unit/probe').mkdir()
                with self.assertRaises(Blocked):scope.revalidate()
            finally:scope.close();os.close(fd)
    def test_symlink_and_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            fd=os.open(d,os.O_RDONLY|os.O_DIRECTORY)
            try:
                for path in ['/','/system.slice','/system.slice/../evil','/system.slice//evil']:
                    with self.assertRaises(Blocked):HeldScope(fd,path)
                (Path(d)/'x').symlink_to('/tmp',target_is_directory=True)
                with self.assertRaises(OSError):HeldScope(fd,'/x/y',lambda f:FDIdentity(1,os.fstat(f).st_ino,1),lambda *a:None)
            finally:os.close(fd)
    def test_live_transport_disarmed(self):
        with self.assertRaises(Blocked):asyncio.run(DBusNextWire.open_live())

class ObserverTests(unittest.TestCase):
    def setUp(self):self.log=Log();self.c=Capture(B,[0],[10,20],self.log)
    def row(self,kind='exec',seq=0,data=None):return json.dumps(dict(binding=B.__dict__,cpu=0,cpu_seq=seq,t_ns=seq+1,kind=kind,task=[10,20],data=data or {})).encode()
    def test_capture_never_qualifies(self):
        self.c.ingest(self.row());out=self.c.finish({0:0},0,1,2,3);self.assertFalse(out['runtime_qualified']);self.assertFalse(out['kernel_attested']);self.assertEqual(out['status'],'INCOMPLETE')
    def test_missing_and_lost_events(self):
        self.c.ingest(self.row(seq=3));out=self.c.finish({0:1},0,1,4,5);self.assertIn('sequence/time gap',out['gaps']);self.assertIn('kernel loss counters absent/nonzero',out['gaps'])
    def test_unknown_task(self):
        row=json.loads(self.row());row['task']=[999,1]
        with self.assertRaises(Blocked):self.c.ingest(json.dumps(row).encode())
    def test_binding_substitution(self):
        row=json.loads(self.row());row['binding']['invocation']='f'*32
        with self.assertRaises(Blocked):self.c.ingest(json.dumps(row).encode())
    def test_descendant_tracking(self):
        self.c.ingest(self.row('fork',data={'child':[11,21]}));row=json.loads(self.row(seq=1));row['task']=[11,21];self.c.ingest(json.dumps(row).encode())
        self.assertIn((11,21),self.c.owned)
    def test_duplicate_json(self):
        with self.assertRaises(Blocked):decode_record(b'{"x":1,"x":2}')
    def test_hotplug_cpu(self):
        row=json.loads(self.row());row['cpu']=2
        with self.assertRaises(Blocked):self.c.ingest(json.dumps(row).encode())
    def test_unpaired_syscall(self):
        with self.assertRaises(Blocked):self.c.ingest(self.row('syscall_exit',data={'case':'mount','cookie':1,'raw_ret':-1}))
    def test_positive_survives_loss(self):
        self.c.ingest(self.row('escape',seq=3));self.assertEqual(self.c.finish({0:2},0,1,4,5)['status'],'FAIL_SIGNAL_UNATTESTED')
    def test_record_limit(self):
        self.c.limit=0
        with self.assertRaises(Blocked):self.c.ingest(self.row())
    def test_live_ring_disarmed(self):
        with self.assertRaises(Blocked):LibbpfRing(1,self.c)

class WatchdogTests(unittest.TestCase):
    def setUp(self):self.b=Backend();self.log=Log();self.now=0;self.c=Controller(B,self.b,self.log,10,lambda:self.now)
    def test_death_and_drain(self):
        self.c.arm();self.c.release_record();self.c.detect(True,5,True);self.assertEqual(self.b.calls,1);self.assertFalse(self.c.drain_tick(True));self.b.pop=False;self.assertTrue(self.c.drain_tick(True))
    def test_pre_pidfd_and_pre_c_covered(self):
        for key in ['pre_pidfd_failed','pre_c_failed']:
            b=Backend();c=Controller(B,b,Log(),10,lambda:0);c.arm();c.detect(False,5,True,**{key:True});self.assertEqual(b.calls,1)
    def test_before_arm_rejected(self):
        with self.assertRaises(Blocked):self.c.release_record()
        self.assertEqual(self.b.calls,0)
    def test_stale_invocation_prevents_kill(self):
        self.c.arm();self.b.drift=True
        with self.assertRaises(Blocked):self.c.terminate('EXPLICIT_CANCEL')
        self.assertEqual(self.b.calls,0)
    def test_uncertain_write_not_retried(self):
        self.c.arm();self.b.fail=True
        with self.assertRaises(OSError):self.c.terminate('EXPLICIT_CANCEL')
        with self.assertRaises(Blocked):self.c.terminate('EXPLICIT_CANCEL')
        self.assertEqual(self.b.calls,1)
    def test_cleanup_deadline(self):
        self.c.arm();self.c.terminate('OBSERVER_LOSS');self.now=11
        with self.assertRaises(Blocked):self.c.drain_tick(True)
        self.assertEqual(self.c.state,'FAILED')
    def test_manager_inactive_required(self):
        self.c.arm();self.c.terminate('HEARTBEAT_TIMEOUT');self.b.pop=False
        with self.assertRaises(Blocked):self.c.drain_tick(False)
    def test_invalid_trigger(self):
        self.c.arm()
        with self.assertRaises(Blocked):self.c.terminate('kill_any_pid')
    def test_journal_failure_before_release(self):
        self.log.append=lambda *a:(_ for _ in ()).throw(OSError('fsync'))
        with self.assertRaises(OSError):self.c.arm()
        with self.assertRaises(Blocked):self.c.release_record()
    def test_live_write_disarmed(self):
        with self.assertRaises(Blocked):DFDTermination(1,None,None,B)
        with self.assertRaises(Blocked):Escrow.receive_live(None,None,None)

class JournalTests(unittest.TestCase):
    def test_chain_and_exclusive_creation(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/'events.jsonl';j=Journal(path,B);j.append('one',{});j.append('two',{});j.close()
            rows=[json.loads(x) for x in path.read_text().splitlines()]
            self.assertEqual(rows[1]['record']['previous'],rows[0]['sha256'])
            for row in rows:self.assertEqual(hashlib.sha256(json.dumps(row['record'],sort_keys=True,separators=(',',':')).encode()).hexdigest(),row['sha256'])
            with self.assertRaises(FileExistsError):Journal(path,B)
    def test_fsync_error_is_sticky(self):
        with tempfile.TemporaryDirectory() as d:
            j=Journal(Path(d)/'events',B)
            try:
                with patch('common.os.fsync',side_effect=OSError('disk')):
                    with self.assertRaises(OSError):j.append('one',{})
                with self.assertRaises(Blocked):j.append('two',{})
            finally:j.close()

if __name__=='__main__':unittest.main()

class AdditionalTests(unittest.TestCase):
    def test_mount_and_proc_parsers(self):
        from host_identity import parse_mountinfo,parse_starttime
        good='29 23 0:26 / /sys/fs/cgroup rw,nosuid,nodev,noexec,relatime - cgroup2 cgroup rw'
        self.assertEqual(parse_mountinfo(good),29)
        for bad in [good+'\n'+good,good.replace('/ /sys','/hidden /sys'),good.replace('cgroup rw','cgroup rw,memory_localevents')]:
            with self.assertRaises(Blocked):parse_mountinfo(bad)
        self.assertEqual(parse_starttime('42 (tricky ) name) S '+'0 '*18+'123 0'),123)
    def test_journal_loss_after_arm_still_terminates_without_claim(self):
        b=Backend();log=Log();c=Controller(B,b,log,10,lambda:0);c.arm()
        log.append=lambda *a:(_ for _ in ()).throw(OSError('disk failed'))
        c.terminate('SUPERVISOR_DEATH');self.assertEqual(b.calls,1);b.pop=False
        self.assertFalse(c.drain_tick(True));self.assertEqual(c.state,'INCOMPLETE_EVIDENCE')
    def test_host_live_interfaces_disarmed(self):
        from host_identity import HostRoot,held_pidfd_live
        with self.assertRaises(Blocked):HostRoot.open_live()
        with self.assertRaises(Blocked):held_pidfd_live(1,1)

class CreationTests(unittest.TestCase):
    def test_fixed_properties_reject_drift(self):
        from unit_creation import fixed_properties
        data=(Path(__file__).parents[1]/'reference/v6-unit-contract.json').read_bytes()
        props=fixed_properties(data,b'fixture',hashlib.sha256(b'fixture').hexdigest())
        self.assertEqual(dict((n,v) for n,s,v in props)['ExecStart'][0][1],['/opt/argentum-c4-v6/supervisor'])
        with self.assertRaises(Blocked):fixed_properties(data+b' ',b'fixture',hashlib.sha256(b'fixture').hexdigest())
        with self.assertRaises(Blocked):fixed_properties(data,b'drift','0'*64)
    def test_single_creation_and_ancestor_changes(self):
        from unit_creation import CreationTransaction
        class Controls:
            def __init__(self):self.n=0;self.drift=False
            async def check_live_identity_and_authority(self):pass
            async def require_absent_unit(self):pass
            async def require_external_controller_ready(self):pass
            async def read_upstream_state(self):self.n+=1;return self.n if self.drift else 1
            async def verify_effective_properties_and_hold_scope(self):pass
        class Sender:
            def __init__(self):self.n=0
            async def create_once(self):self.n+=1;return 'job'
        async def run():
            c=Controls();s=Sender();tx=CreationTransaction(c,s,Log())
            self.assertFalse((await tx.execute_once())['worker_released'])
            with self.assertRaises(Blocked):await tx.execute_once()
            self.assertEqual(s.n,1)
            c=Controls();c.drift=True;s=Sender();tx=CreationTransaction(c,s,Log())
            with self.assertRaises(Blocked):await tx.execute_once()
            self.assertEqual(s.n,1)
        asyncio.run(run())
    def test_no_generic_dbus_write(self):
        async def run():
            w=DBusNextWire()
            with self.assertRaises(Blocked):await w.call(':1.0','/org/freedesktop/systemd1','org.freedesktop.systemd1.Manager','StopUnit','ss',['anything','replace'])
        asyncio.run(run())
