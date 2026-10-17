"""Route B integration coordinator. No operational authorization is minted.

Live adapters remain sealed. Interfaces are exercised only with synthetic
backends; identity-bearing records are not accepted as proof of host authority.
"""
import hashlib
import os
import stat
from contextlib import ExitStack
from common import require,Blocked,deployment_barrier

class InstalledImage:
    """Own a duplicate FD; hash the same regular file before/after the handshake."""
    def __init__(self,fd,expected_sha,owner=0,max_bytes=32*1024*1024):
        self.fd=os.dup(fd);self.expected_sha=expected_sha;self.owner=owner;self.max_bytes=max_bytes
        try:self.identity=self.check()
        except BaseException:self.close();raise
    def check(self):
        before=os.fstat(self.fd)
        require(stat.S_ISREG(before.st_mode) and before.st_uid==self.owner and not before.st_mode&0o022,'untrusted installed image')
        require(before.st_nlink==1 and 0<before.st_size<=self.max_bytes,'image size/link count')
        h=hashlib.sha256();off=0
        while off<before.st_size:
            b=os.pread(self.fd,min(65536,before.st_size-off),off);require(bool(b),'short image read');h.update(b);off+=len(b)
        after=os.fstat(self.fd)
        key=lambda s:(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns,s.st_mode,s.st_uid,s.st_gid)
        require(key(before)==key(after) and h.hexdigest()==self.expected_sha,'installed image drift')
        return key(after)
    def revalidate(self):require(self.check()==self.identity,'installed inode replaced/modified')
    def close(self):
        if self.fd>=0:os.close(self.fd);self.fd=-1
    @classmethod
    def open_live(cls,root_fd,expected_sha):
        deployment_barrier()
        # root_fd is the independently authenticated /opt/argentum-c4-v6 directory.
        fd=os.open('supervisor',os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=root_fd)
        try:return cls(fd,expected_sha)
        finally:os.close(fd)

class ScopeEstablishment:
    """One-shot mutation ordering; exact D only, no ancestor/controller fallback."""
    def __init__(self,backend,journal):self.backend=backend;self.journal=journal;self.used=False
    def establish(self):
        require(not self.used,'scope attempt consumed');self.used=True
        b=self.backend
        b.revalidate_manager_and_held_d()
        b.require_supervisor_in_manager_created_s()
        b.require_external_watchdog_armed_for_d()
        b.require_empty_d_and_absent_c()
        baseline=b.upstream_snapshot()
        # Revalidation occurs immediately before every allowed mutation.
        for operation in ['enable_memory_at_d','create_exclusive_c']:
            b.revalidate_manager_and_held_d();self.journal.append('SCOPE_MUTATION_INTENT',{'operation':operation})
            getattr(b,operation)()
            self.journal.append('SCOPE_MUTATION_RETURNED',{'operation':operation})
        require(b.upstream_snapshot()==baseline,'upstream controller mutation')
        scope=b.open_and_verify_dsc()
        self.journal.append('HELD_SCOPE_ESTABLISHED',{})
        return scope

class FixedDWriter:
    """Concrete fixed-name writes below held D; never called during local tests."""
    def __init__(self,dfd,revalidate):
        deployment_barrier()
        self.fd=os.dup(dfd);self.revalidate=revalidate;self.memory_attempted=False;self.c_attempted=False
    def enable_memory_at_d(self):
        require(not self.memory_attempted,'memory enable consumed');self.revalidate()
        fd=os.open('cgroup.subtree_control',os.O_WRONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self.fd)
        try:
            self.revalidate();self.memory_attempted=True
            require(os.write(fd,b'+memory')==7,'uncertain controller write')
        finally:os.close(fd)
    def create_exclusive_c(self):
        require(not self.c_attempted,'C creation consumed');self.revalidate();self.c_attempted=True
        os.mkdir('probe',0o755,dir_fd=self.fd)
    def close(self):os.close(self.fd)

class IntegratedPreparation:
    """Connects verified installation, scope, observer and watchdog preparation.

    Does not release an ELF or return a runtime permit. Candidate dependency
    injection is not a replacement for reviewed production construction.
    """
    def __init__(self,manager,scope_factory,observer,watchdog,image,journal):
        self.manager=manager;self.scope_factory=scope_factory;self.observer=observer;self.watchdog=watchdog;self.image=image;self.journal=journal;self.used=False
    async def prepare(self,binding):
        require(not self.used,'preparation consumed');self.used=True
        self.image.revalidate()
        await self.manager.bind()
        await self.manager.revalidate(binding)
        # Supervisor starts held at its own release gate. Watchdog registration
        # covers the pre-C/pre-pidfd interval, before scope setup can fail.
        self.watchdog.arm()
        try:
            scope=self.scope_factory.establish()
            self.observer.prepare_bound_capture(scope,binding)
            await self.manager.revalidate(binding);self.image.revalidate();scope.revalidate()
            self.journal.append('PREPARED_NO_WORKER_RELEASE',{})
            return {'status':'PREPARED_NOT_RUNTIME_QUALIFIED','runtime_qualified':False,'worker_released':False}
        except BaseException:
            # Invoke the existing single-attempt controller, not an ad hoc kill.
            if self.watchdog.state in ('ARMED','RELEASED'):
                self.watchdog.terminate('PRE_C_JOIN_FAILURE')
            raise
