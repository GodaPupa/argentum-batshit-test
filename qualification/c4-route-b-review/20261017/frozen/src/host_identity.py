"""Linux host identity candidate; live calls remain behind deployment barrier."""
import ctypes
import os
import re
from common import require,deployment_barrier
from manager import fd_identity,root_only

CGROUP2_SUPER_MAGIC=0x63677270

def parse_mountinfo(text):
    found=[]
    for line in text.splitlines():
        left,sep,right=line.partition(' - ')
        require(bool(sep),'mountinfo malformed')
        a,b=left.split(),right.split();require(len(a)>=6 and len(b)>=3,'mountinfo columns')
        if b[0]=='cgroup2':
            require(a[3]=='/' and a[4]=='/sys/fs/cgroup','noncanonical cgroup mount view')
            require('memory_localevents' not in b[2].split(','),'local memory events mount')
            found.append(int(a[0]))
    require(len(found)==1,'ambiguous/missing cgroup2 mount')
    return found[0]

def parse_starttime(raw):
    # comm can contain spaces and ')'; fields following final ')' start at state.
    close=raw.rfind(')');require(close>0,'proc stat format')
    fields=raw[close+1:].split();require(len(fields)>19,'proc stat length')
    require(fields[19].isdigit(),'proc stat starttime');return int(fields[19])

class HostRoot:
    @classmethod
    def open_live(cls):
        deployment_barrier()
        obj=cls();obj.fd=-1
        with open('/proc/self/mountinfo',encoding='ascii') as f:mount=parse_mountinfo(f.read())
        require(os.stat('/proc/self/ns/cgroup')==os.stat('/proc/1/ns/cgroup'),'cgroup namespace mismatch')
        require(os.stat('/proc/self/ns/mnt')==os.stat('/proc/1/ns/mnt'),'mount namespace mismatch')
        fd=os.open('/',os.O_RDONLY|os.O_DIRECTORY|os.O_CLOEXEC)
        try:
            for name in ['sys','fs','cgroup']:
                nxt=os.open(name,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=fd);os.close(fd);fd=nxt;root_only(fd)
            buf=ctypes.create_string_buffer(256)
            libc=ctypes.CDLL(None,use_errno=True);libc.fstatfs.argtypes=[ctypes.c_int,ctypes.c_void_p];libc.fstatfs.restype=ctypes.c_int
            require(libc.fstatfs(fd,buf)==0,'fstatfs error')
            require(ctypes.c_long.from_buffer(buf).value==CGROUP2_SUPER_MAGIC,'not cgroup2 filesystem')
            require(fd_identity(fd).mount==mount,'mount ID drift')
            with open('/proc/sys/kernel/random/boot_id',encoding='ascii') as f:obj.boot=f.read().strip()
            require(bool(re.fullmatch('[0-9a-f-]{36}',obj.boot)),'boot syntax')
            obj.fd=fd;fd=-1;return obj
        finally:
            if fd>=0:os.close(fd)
    def close(self):
        if self.fd>=0:os.close(self.fd);self.fd=-1

def held_pidfd_live(pid,expected_start):
    deployment_barrier()
    require(type(pid) is int and pid>0 and type(expected_start) is int and expected_start>0,'manager PID/start binding')
    def start():
        with open('/proc/'+str(pid)+'/stat',encoding='ascii') as f:return parse_starttime(f.read())
    require(start()==expected_start,'PID reused before pidfd')
    fd=os.pidfd_open(pid,0)
    try:
        require(start()==expected_start,'PID reused during pidfd acquisition')
        with open('/proc/self/fdinfo/'+str(fd),encoding='ascii') as f:info=dict(x.split(':',1) for x in f if ':' in x)
        require(int(info['Pid'])==pid,'pidfd target mismatch')
        return fd
    except BaseException:os.close(fd);raise
