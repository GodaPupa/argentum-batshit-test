"""Concrete libbpf ring consumer plus bounded evidence accumulator.

NO invented kernel offsets, no attach, no privilege changes. A separately reviewed
host-specific producer must supply complete records and kernel loss-map snapshots.
A JSON record/transport peer can never set kernel_attested or runtime_qualified.
"""
import ctypes
import json
from common import require, Blocked, deployment_barrier

KINDS={'fork','vfork','clone','exec','exit','reparent','cgroup_attach','syscall_enter','syscall_exit','escape','fatal','credential','seccomp','image'}
DENIALS={'migration','setuid','setgid','capset','unshare','setns','mount','unix_socket','unix_socketpair'}
REQUIRED={'fork','exec','exit','reparent','cgroup_attach','syscall_enter','syscall_exit','credential','seccomp','image'}

def pair(x):return type(x) is list and len(x)==2 and all(type(v) is int and v>0 for v in x)

def decode_record(raw):
    require(len(raw)<=16384,'oversize kernel record')
    def unique(pairs):
        d={}
        for k,v in pairs:
            require(k not in d,'duplicate JSON field');d[k]=v
        return d
    r=json.loads(raw,object_pairs_hook=unique,parse_constant=lambda _: (_ for _ in ()).throw(Blocked('nonfinite JSON')))
    require(type(r) is dict,'record object')
    require(set(r)=={'binding','cpu','cpu_seq','t_ns','kind','task','data'},'record schema')
    require(r['kind'] in KINDS and pair(r['task']),'event kind/task')
    require(all(type(r[k]) is int and r[k]>=0 for k in ('cpu','cpu_seq','t_ns')),'event integers')
    require(type(r['data']) is dict,'event data')
    return r

class Capture:
    def __init__(self,binding,cpus,root_task,journal,max_records=100000):
        require(cpus and all(type(x) is int and x>=0 for x in cpus) and len(cpus)==len(set(cpus)),'CPU set')
        require(pair(root_task),'root task identity')
        self.binding=binding.__dict__;self.cpus=set(cpus);self.owned={tuple(root_task)}
        self.seq={x:0 for x in cpus};self.last={x:-1 for x in cpus};self.journal=journal
        self.gaps=[];self.positive=[];self.kinds=set();self.pending={};self.cases=set();self.count=0;self.limit=max_records;self.dead=False
    def ingest(self,raw):
        require(not self.dead,'capture terminal')
        try:
            r=decode_record(raw)
            require(r['binding']==self.binding,'event binding drift')
            require(r['cpu'] in self.cpus,'CPU hotplug/uncovered CPU')
            cpu=r['cpu'];task=tuple(r['task']);data=r['data']
            require(task in self.owned,'untracked descendant')
            # Preserve raw evidence BEFORE derived checks; scoped positive signals
            # survive sequence gaps. Producer authentication remains unresolved.
            self.journal.append('raw_event',r)
            if r['kind'] in {'escape','fatal'} or (r['kind']=='syscall_exit' and data.get('case') in DENIALS and type(data.get('raw_ret')) is int and data['raw_ret']>=0):self.positive.append(r)
            if r['cpu_seq']!=self.seq[cpu] or r['t_ns']<self.last[cpu]:self.gaps.append('sequence/time gap')
            self.seq[cpu]=r['cpu_seq']+1;self.last[cpu]=r['t_ns']
            self.count+=1;require(self.count<=self.limit,'record limit')
            if r['kind'] in {'fork','vfork','clone'}:
                child=data.get('child');require(pair(child) and tuple(child) not in self.owned,'child identity collision');self.owned.add(tuple(child))
            if r['kind'] in {'syscall_enter','syscall_exit'}:
                case=data.get('case');cookie=data.get('cookie')
                require(case in DENIALS and type(cookie) is int and cookie>0,'syscall case/cookie')
                key=(task,cookie)
                if r['kind']=='syscall_enter':
                    require(key not in self.pending,'duplicate syscall enter');self.pending[key]=r
                else:
                    require(key in self.pending,'unpaired syscall exit');entry=self.pending.pop(key)
                    require(entry['data']['case']==case and entry['t_ns']<r['t_ns'],'syscall pairing drift')
                    require(case not in self.cases,'duplicate negative control');self.cases.add(case)
            self.kinds.add(r['kind'])
        except BaseException:
            self.dead=True;self.gaps.append('rejected record or journal failure');raise
    def finish(self,losses,attached_ns,released_ns,drained_ns,detached_ns):
        require(not self.dead,'capture rejected')
        self.dead=True
        if not (type(losses) is dict and set(losses)==self.cpus and all(type(v) is int and v==0 for v in losses.values())):self.gaps.append('kernel loss counters absent/nonzero')
        if not all(type(v) is int for v in (attached_ns,released_ns,drained_ns,detached_ns)) or not attached_ns<released_ns<=drained_ns<=detached_ns:self.gaps.append('coverage interval')
        if self.pending or self.cases!=DENIALS or not REQUIRED<=self.kinds:self.gaps.append('missing lifecycle/negative controls')
        out={'status':'FAIL_SIGNAL_UNATTESTED' if self.positive else 'INCOMPLETE' if self.gaps else 'CAPTURE_STRUCTURALLY_COMPLETE_UNATTESTED','gaps':self.gaps,'kernel_attested':False,'runtime_qualified':False,'records':self.count}
        self.journal.append('capture_end',out)
        return out

class LibbpfRing:
    """Actual ring_buffer__new/poll/free FFI; unexecuted until reviewed deployment.

    map_fd must come from the verified loader/map-info check. This code does not
    authenticate it. No library discovery or arbitrary library path is accepted.
    C callback never propagates Python exceptions across ABI.
    """
    def __init__(self,map_fd,capture):
        deployment_barrier()
        self.capture=capture;self.error=None;self.ptr=None
        self.lib=ctypes.CDLL('libbpf.so.1',use_errno=True)
        cbtype=ctypes.CFUNCTYPE(ctypes.c_int,ctypes.c_void_p,ctypes.c_void_p,ctypes.c_size_t)
        def accept(ctx,data,size):
            try:
                require(size<=16384,'ring sample oversized');capture.ingest(ctypes.string_at(data,size));return 0
            except BaseException as exc:
                self.error=exc;return -1
        self.cb=cbtype(accept)
        self.lib.ring_buffer__new.argtypes=[ctypes.c_int,cbtype,ctypes.c_void_p,ctypes.c_void_p];self.lib.ring_buffer__new.restype=ctypes.c_void_p
        self.lib.ring_buffer__poll.argtypes=[ctypes.c_void_p,ctypes.c_int];self.lib.ring_buffer__poll.restype=ctypes.c_int
        self.lib.ring_buffer__free.argtypes=[ctypes.c_void_p];self.lib.ring_buffer__free.restype=None
        self.ptr=self.lib.ring_buffer__new(map_fd,self.cb,None,None)
        require(bool(self.ptr),'ring initialization failed')
    def poll_once(self):
        require(self.ptr is not None,'closed ring')
        rc=self.lib.ring_buffer__poll(self.ptr,50)
        if self.error:raise self.error
        require(rc>=0,'ring poll error; no retry')
        return rc
    def close(self):
        if self.ptr:self.lib.ring_buffer__free(self.ptr);self.ptr=None
