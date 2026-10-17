"""External watchdog channel and selector loop, no installed daemon in this build."""
import array
import json
import os
import select
import socket
import struct
import time
from common import require,Blocked,deployment_barrier

class Channel:
    """Preconnected Unix seqpacket; peer lifetime pinned by launcher pidfd.

    Root peer credential checks are necessary, not sufficient deployment proof.
    The trusted launcher must bind PID/start/pidfd and attest location outside D/S/C.
    """
    def __init__(self,sock,binding,expected_peer):
        self.sock=sock;self.binding=binding.__dict__;self.tx=0;self.rx=0
        require(sock.getsockopt(socket.SOL_SOCKET,socket.SO_TYPE)==socket.SOCK_SEQPACKET,'channel type')
        require(struct.unpack('3i',sock.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))==expected_peer,'channel peer')
        sock.settimeout(1)
    def send(self,kind,fd=None):
        require(kind in {'REGISTER_D','ARMED','RELEASED','HEARTBEAT','OBSERVER_LOSS','C_JOIN_FAILURE','PIDFD_FAILURE','CANCEL'},'channel kind')
        require((kind=='REGISTER_D')==(fd is not None),'FD message scope')
        raw=json.dumps({'v':1,'seq':self.tx,'binding':self.binding,'kind':kind},sort_keys=True,separators=(',',':')).encode()
        ancillary=[] if fd is None else [(socket.SOL_SOCKET,socket.SCM_RIGHTS,array.array('i',[fd]))]
        # Reserve sequence before send; uncertain outcome cannot be retried.
        self.tx+=1
        require(self.sock.sendmsg([raw],ancillary)==len(raw),'uncertain channel send')
    def receive(self):
        data,anc,flags,_=self.sock.recvmsg(4096,socket.CMSG_SPACE(16*4),socket.MSG_CMSG_CLOEXEC)
        fds=[]
        try:
            for level,kind,raw in anc:
                if level==socket.SOL_SOCKET and kind==socket.SCM_RIGHTS:
                    vals=array.array('i');vals.frombytes(raw[:len(raw)//vals.itemsize*vals.itemsize]);fds.extend(vals)
                else:raise Blocked('unexpected ancillary record')
            require(data and not flags&(socket.MSG_TRUNC|socket.MSG_CTRUNC),'channel EOF/truncated')
            def unique(pairs):
                d={}
                for k,v in pairs:require(k not in d,'duplicate field');d[k]=v
                return d
            r=json.loads(data,object_pairs_hook=unique)
            require(set(r)=={'v','seq','binding','kind'} and r['v']==1 and type(r['seq']) is int and r['seq']==self.rx and r['binding']==self.binding,'channel binding/sequence')
            kind=r['kind'];require(kind in {'REGISTER_D','ARMED','RELEASED','HEARTBEAT','OBSERVER_LOSS','C_JOIN_FAILURE','PIDFD_FAILURE','CANCEL'},'channel kind')
            require(len(fds)==(1 if kind=='REGISTER_D' else 0),'channel FD count')
            self.rx+=1
            return kind,(fds.pop() if fds else None)
        finally:
            for fd in fds:os.close(fd)

class EventLoop:
    """Bounded selector driver. No PID signals; no automatic termination retry."""
    def __init__(self,controller,channel,wait,manager_inactive,clock=time.monotonic,heartbeat_seconds=1):
        self.controller=controller;self.channel=channel;self.wait=wait;self.manager_inactive=manager_inactive;self.clock=clock
        self.heartbeat_seconds=heartbeat_seconds;self.heartbeat_deadline=clock()+heartbeat_seconds;self.closed=False
    def tick(self):
        c=self.controller
        require(not self.closed,'event loop terminal')
        if c.state=='DRAINING':
            self.wait(max(0,min(0.05,c.deadline-self.clock())))
            c.drain_tick(self.manager_inactive())
        elif c.state in ('ARMED','RELEASED'):
            timeout=max(0,min(0.05,c.deadline-self.clock(),self.heartbeat_deadline-self.clock()))
            events=self.wait(timeout)
            death='pidfd' in events;observer_lost='observer' in events;pre_c=False;pre_pid=False
            if 'channel' in events:
                try:kind,fd=self.channel.receive()
                except Exception:
                    kind='CANCEL'
                else:
                    if fd is not None:
                        os.close(fd);kind='CANCEL'
                if kind=='HEARTBEAT':self.heartbeat_deadline=min(c.deadline,self.clock()+self.heartbeat_seconds)
                elif kind=='RELEASED':c.release_record()
                elif kind=='C_JOIN_FAILURE':pre_c=True
                elif kind=='PIDFD_FAILURE':pre_pid=True
                elif kind=='OBSERVER_LOSS':observer_lost=True
                elif kind=='CANCEL':c.terminate('EXPLICIT_CANCEL')
                else:c.terminate('EXPLICIT_CANCEL')
            if c.state in ('ARMED','RELEASED'):c.detect(death,self.heartbeat_deadline,not observer_lost,pre_c,pre_pid)
        self.closed=c.state in ('DONE','FAILED','INCOMPLETE_EVIDENCE')
        return c.state
    def run(self):
        while not self.closed:self.tick()
        return {'state':self.controller.state,'runtime_qualified':False}

class PollWait:
    def __init__(self,pidfd,channel_fd,observer_fd):
        deployment_barrier()
        require(len({pidfd,channel_fd,observer_fd})==3,'aliased event descriptors')
        self.poll=select.poll();self.names={pidfd:'pidfd',channel_fd:'channel',observer_fd:'observer'}
        for fd in self.names:self.poll.register(fd,select.POLLIN|select.POLLHUP|select.POLLERR)
    def __call__(self,seconds):return {self.names[fd] for fd,mask in self.poll.poll(max(0,int(seconds*1000)))}

def service_main():
    deployment_barrier()
    # A sealed inherited descriptor table and installation-bound production
    # constructors must be supplied by the independent deployment review.

if __name__=='__main__':service_main()
