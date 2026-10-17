"""External controller candidate. No service installation or worker release CLI.

The orchestrator is tested with injected backends. Real D-FD termination plumbing
is guarded; no numeric PID signal or systemd StopUnit path exists.
"""
import array
import os
import select
import socket
import struct
import time
from common import Blocked,require,deployment_barrier
from manager import fd_identity,root_only

TRIGGERS={'SUPERVISOR_DEATH','PRE_C_JOIN_FAILURE','PRE_PIDFD_FAILURE','HEARTBEAT_TIMEOUT','OBSERVER_LOSS','ABSOLUTE_DEADLINE','EXPLICIT_CANCEL'}

class Escrow:
    """SCM_RIGHTS receiver on a preconnected root broker SOCK_SEQPACKET channel.

    Expected peer PID/start binding must be established by reviewed launcher.
    Credentials alone do not attest an invocation; supplied descriptors are checked.
    """
    @staticmethod
    def receive_live(sock,expected_peer,expected_identity):
        deployment_barrier()
        peer=struct.unpack('3i',sock.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))
        require(peer==expected_peer and peer[1:]==(0,0),'escrow peer mismatch')
        require(sock.type&socket.SOCK_SEQPACKET==socket.SOCK_SEQPACKET,'escrow packet transport')
        sock.settimeout(2)
        data,anc,flags,_=sock.recvmsg(4096,socket.CMSG_SPACE(4),socket.MSG_CMSG_CLOEXEC)
        fds=[]
        try:
            for level,kind,raw in anc:
                require(level==socket.SOL_SOCKET and kind==socket.SCM_RIGHTS,'unexpected escrow ancillary data')
                values=array.array('i');values.frombytes(raw[:len(raw)//values.itemsize*values.itemsize]);fds.extend(values)
            require(not flags&(socket.MSG_TRUNC|socket.MSG_CTRUNC),'truncated escrow')
            require(len(fds)==1 and data==b'ARGENTUM_ROUTE_B_DFD_V1','escrow framing')
            root_only(fds[0]);require(fd_identity(fds[0])==expected_identity,'escrow FD substitution')
            return fds.pop()
        finally:
            for fd in fds:os.close(fd)

class DFDTermination:
    """Writes only cgroup.kill through a held D. Never opens a target by pathname.

    Snapshot callback must use the pinned live manager session and exact binding;
    deployment still blocked pending reviewed transaction/host provenance.
    """
    def __init__(self,dfd,expected,snapshot,binding):
        deployment_barrier()
        self.fd=os.dup(dfd);self.expected=expected;self.snapshot=snapshot;self.binding=binding;self.attempted=False
    def revalidate(self):
        root_only(self.fd);require(fd_identity(self.fd)==self.expected,'held D drift')
        current=self.snapshot()
        require(current==self.binding,'invocation revalidation mismatch')
    def kill_once(self):
        require(not self.attempted,'kill already attempted')
        self.revalidate()
        fd=os.open('cgroup.kill',os.O_WRONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self.fd)
        try:
            root_only(fd,False);require(fd_identity(fd).mount==self.expected.mount,'kill mount drift')
            self.revalidate();self.attempted=True
            require(os.write(fd,b'1')==1,'partial kill write; outcome uncertain')
        finally:os.close(fd)
    def populated(self):
        self.revalidate();fd=os.open('cgroup.events',os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self.fd)
        try:
            root_only(fd,False);raw=os.read(fd,4097);require(len(raw)<=4096,'events size')
            rows=[line.split() for line in raw.decode('ascii').splitlines()]
            require(all(len(x)==2 for x in rows),'events malformed')
            require(len({x[0] for x in rows})==len(rows),'events duplicate')
            values=dict(rows);require(values.get('populated') in ('0','1'),'populated absent')
            return values['populated']=='1'
        finally:os.close(fd)
    def close(self):os.close(self.fd)

class Controller:
    """Absolute deadlines never reset; failure terminal, no automatic retry."""
    def __init__(self,binding,backend,journal,deadline,clock=time.monotonic):
        require(deadline>clock(),'expired deadline')
        self.binding=binding;self.backend=backend;self.journal=journal;self.deadline=deadline;self.clock=clock
        self.state='NEW';self.kill_attempted=False;self.trigger=None
    def arm(self):
        require(self.state=='NEW','arm state')
        self.backend.revalidate()
        self.journal.append('ARMED',{'absolute_deadline':self.deadline})
        self.state='ARMED'
    def release_record(self):
        require(self.state=='ARMED' and self.clock()<self.deadline,'release before arm/after deadline')
        self.backend.revalidate();self.journal.append('RELEASED',{});self.state='RELEASED'
    def terminate(self,trigger):
        require(trigger in TRIGGERS and self.state in ('ARMED','RELEASED'),'trigger/state')
        self.trigger=trigger;self.state='TERMINATING'
        journal_error=None
        try:
            # Once armed, disk failure must not suppress a safe identity-bound
            # termination. Preserve the error; completion remains INCOMPLETE.
            try:self.journal.append('TRIGGERED',{'trigger':trigger})
            except Exception as exc:journal_error=exc
            self.backend.revalidate()
            if journal_error is None:
                try:self.journal.append('KILL_INTENT',{})
                except Exception as exc:journal_error=exc
            self.kill_attempted=True;self.backend.kill_once()
            if journal_error is None:
                try:self.journal.append('KILL_RETURNED',{})
                except Exception as exc:journal_error=exc
            self.evidence_error=journal_error
            self.state='DRAINING'
        except BaseException:
            self.state='FAILED';raise
    def drain_tick(self,manager_inactive):
        require(self.state=='DRAINING','not draining')
        try:
            require(self.clock()<=self.deadline,'cleanup absolute deadline')
            self.backend.revalidate()
            if self.backend.populated():return False
            require(manager_inactive is True,'exact invocation not inactive')
            if self.evidence_error is not None:
                self.state='INCOMPLETE_EVIDENCE';return False
            self.journal.append('DRAINED_AND_INACTIVE',{})
            self.state='DONE';return True
        except BaseException:
            self.state='FAILED';raise
    def detect(self,pidfd_ready,heartbeat_deadline,observer_alive,pre_c_failed=False,pre_pidfd_failed=False):
        require(self.state in ('ARMED','RELEASED'),'detect state')
        trigger= ('PRE_PIDFD_FAILURE' if pre_pidfd_failed else 'PRE_C_JOIN_FAILURE' if pre_c_failed else 'SUPERVISOR_DEATH' if pidfd_ready else 'OBSERVER_LOSS' if not observer_alive else 'ABSOLUTE_DEADLINE' if self.clock()>=self.deadline else 'HEARTBEAT_TIMEOUT' if self.clock()>=heartbeat_deadline else None)
        if trigger:self.terminate(trigger)
        return trigger
