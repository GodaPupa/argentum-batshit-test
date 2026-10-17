import os,socket,tempfile,hashlib,unittest
from test_candidates import B,Log,Backend
from common import Blocked
from integration import InstalledImage,ScopeEstablishment
from external_service import Channel,EventLoop,service_main
from observer_lifecycle import AttachmentLifecycle,construct_authenticated_provider
from watchdog import Controller
class IntegrationTests(unittest.TestCase):
    def pair(self):
        a,b=socket.socketpair(socket.AF_UNIX,socket.SOCK_SEQPACKET)
        self.addCleanup(a.close);self.addCleanup(b.close);peer=(os.getpid(),os.getuid(),os.getgid())
        return Channel(a,B,peer),Channel(b,B,peer)
    def test_real_fd_escrow_cloexec(self):
        a,b=self.pair()
        with tempfile.TemporaryFile() as f:
            a.send('REGISTER_D',f.fileno());kind,fd=b.receive()
            try:
                self.assertEqual(kind,'REGISTER_D');self.assertEqual(os.fstat(fd).st_ino,os.fstat(f.fileno()).st_ino);self.assertFalse(os.get_inheritable(fd))
            finally:os.close(fd)
    def test_replay_rejected(self):
        a,b=self.pair();a.send('HEARTBEAT');b.receive();a.tx=0;a.send('HEARTBEAT')
        with self.assertRaises(Blocked):b.receive()
    def test_wrong_binding(self):
        a,b=self.pair();a.binding=dict(a.binding,run='e'*32);a.send('HEARTBEAT')
        with self.assertRaises(Blocked):b.receive()
    def test_extra_fd_rejected(self):
        a,b=self.pair()
        with tempfile.TemporaryFile() as f:
            with self.assertRaises(Blocked):a.send('HEARTBEAT',f.fileno())
    def test_wrong_peer(self):
        a,b=self.pair()
        with self.assertRaises(Blocked):Channel(a.sock,B,(999,0,0))
    def test_duplicate_json(self):
        a,b=self.pair();a.sock.send(b'{"v":1,"v":1}')
        with self.assertRaises(Blocked):b.receive()
    def test_image_drift(self):
        with tempfile.NamedTemporaryFile() as f:
            f.write(b'original');f.flush();os.fchmod(f.fileno(),0o600)
            im=InstalledImage(f.fileno(),hashlib.sha256(b'original').hexdigest(),os.getuid())
            try:
                im.revalidate();f.seek(0);f.write(b'modified');f.flush()
                with self.assertRaises(Blocked):im.revalidate()
            finally:im.close()
    def test_image_permissions(self):
        with tempfile.NamedTemporaryFile() as f:
            f.write(b'x');f.flush();os.fchmod(f.fileno(),0o666)
            with self.assertRaises(Blocked):InstalledImage(f.fileno(),hashlib.sha256(b'x').hexdigest(),os.getuid())
    def scope(self,fail=False):
        class S:
            def __init__(self):self.calls=[]
            def __getattr__(self,k):
                def f():
                    self.calls.append(k)
                    if fail and k=='enable_memory_at_d':raise OSError('uncertain')
                    if k=='upstream_snapshot':return 'same'
                    if k=='open_and_verify_dsc':return self
                return f
        return S()
    def test_scope_order_no_retry(self):
        b=self.scope();s=ScopeEstablishment(b,Log());s.establish()
        self.assertLess(b.calls.index('require_external_watchdog_armed_for_d'),b.calls.index('enable_memory_at_d'))
        self.assertLess(b.calls.index('enable_memory_at_d'),b.calls.index('create_exclusive_c'))
        with self.assertRaises(Blocked):s.establish()
    def test_scope_uncertain_stops(self):
        b=self.scope(True);s=ScopeEstablishment(b,Log())
        with self.assertRaises(OSError):s.establish()
        self.assertNotIn('create_exclusive_c',b.calls)
        with self.assertRaises(Blocked):s.establish()
    def loop(self,events):
        b=Backend();c=Controller(B,b,Log(),10,lambda:0);c.arm()
        return b,c,EventLoop(c,None,lambda timeout:events,lambda:True,lambda:0)
    def test_supervisor_death_drain(self):
        b,c,l=self.loop({'pidfd'});self.assertEqual(l.tick(),'DRAINING');b.pop=False
        self.assertEqual(l.tick(),'DONE');self.assertEqual(b.calls,1)
        with self.assertRaises(Blocked):l.tick()
    def test_observer_loss(self):
        b,c,l=self.loop({'observer'});l.tick();self.assertEqual(c.trigger,'OBSERVER_LOSS');self.assertEqual(b.calls,1)
    def test_kill_failure_no_retry(self):
        b,c,l=self.loop({'pidfd'});b.fail=True
        with self.assertRaises(OSError):l.tick()
        l.tick();self.assertEqual(b.calls,1);self.assertEqual(c.state,'FAILED')
    def test_durable_return_failure_drains(self):
        class BadLog(Log):
            def append(self,k,d):
                if k=='KILL_RETURNED':raise OSError('disk')
                super().append(k,d)
        b=Backend();c=Controller(B,b,BadLog(),10,lambda:0);c.arm();c.terminate('SUPERVISOR_DEATH');b.pop=False;c.drain_tick(True)
        self.assertEqual(c.state,'INCOMPLETE_EVIDENCE');self.assertEqual(b.calls,1)
    def test_barriers(self):
        for f in [service_main,construct_authenticated_provider]:
            with self.assertRaises(Blocked):f()
class Provider:
    def __init__(self):self.cpus={0,1};self.loss={0:0,1:0};self.calls=[];self.bad=False
    def online_cpus(self):return self.cpus
    def loss_counters(self):return self.loss
    def validate_installation_and_kernel(self,b):
        if self.bad:raise Blocked('source mismatch')
    def attach_reviewed_hooks(self,s):return [11,12]
    def verify_links_and_maps(self,l,b):pass
    def flush_durable_events(self):self.calls.append('flush')
    def detach_once(self,l):self.calls.append('detach')
class Scope:
    def revalidate(self):pass
class ObserverIntegrationTests(unittest.TestCase):
    def fixture(self):
        p=Provider();return p,AttachmentLifecycle(p,Log(),B,{0,1})
    def test_attach_drain(self):
        p,o=self.fixture();o.prepare_bound_capture(Scope(),B);r=o.finish_after_drain(lambda:True)
        self.assertFalse(r['kernel_authentication_proven']);self.assertEqual(p.calls,['flush','detach'])
        with self.assertRaises(Blocked):o.finish_after_drain(lambda:True)
    def test_hotplug(self):
        p,o=self.fixture();o.prepare_bound_capture(Scope(),B);p.cpus.add(2)
        with self.assertRaises(Blocked):o.check_health()
    def test_loss_missing_map(self):
        for loss in [{0:0},{0:0,1:1},{0:0,1:False}]:
            p,o=self.fixture();o.prepare_bound_capture(Scope(),B);p.loss=loss
            with self.assertRaises(Blocked):o.check_health()
    def test_source_mismatch(self):
        p,o=self.fixture();p.bad=True
        with self.assertRaises(Blocked):o.prepare_bound_capture(Scope(),B)
        self.assertEqual(o.state,'FAILED')
    def test_drain_required(self):
        p,o=self.fixture();o.prepare_bound_capture(Scope(),B)
        with self.assertRaises(Blocked):o.finish_after_drain(lambda:False)
        self.assertEqual(p.calls,[])
class AdditionalIntegrationTests(unittest.TestCase):
    def test_wrong_image_hash(self):
        with tempfile.NamedTemporaryFile() as f:
            f.write(b'original');f.flush();os.fchmod(f.fileno(),0o600)
            with self.assertRaises(Blocked):InstalledImage(f.fileno(),'0'*64,os.getuid())
    def test_prepare_positive_and_failure(self):
        import asyncio
        from integration import IntegratedPreparation
        class Manager:
            async def bind(self):pass
            async def revalidate(self,b):pass
        class Factory:
            def establish(self):return Scope()
        class Image:
            def revalidate(self):pass
        async def go(fail):
            p=Provider();p.bad=fail;o=AttachmentLifecycle(p,Log(),B,{0,1})
            b=Backend();c=Controller(B,b,Log(),10,lambda:0)
            coordinator=IntegratedPreparation(Manager(),Factory(),o,c,Image(),Log())
            if fail:
                with self.assertRaises(Blocked):await coordinator.prepare(B)
                self.assertEqual(b.calls,1);self.assertEqual(c.state,'DRAINING')
            else:
                r=await coordinator.prepare(B);self.assertFalse(r['worker_released']);self.assertFalse(r['runtime_qualified'])
            with self.assertRaises(Blocked):await coordinator.prepare(B)
        asyncio.run(go(False));asyncio.run(go(True))
