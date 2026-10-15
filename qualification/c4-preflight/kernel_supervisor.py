#!/usr/bin/env python3
"""Linux V4 supervisor source. CLI and compiler activation are DISABLED.

Only supervise_probe() has an OS implementation, for future separately approved
containment validation. It executes the single fixed /usr/bin/true image. Import,
synthetic tests and CLI never invoke it. No claim of hosted validation.
"""
import ctypes
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import resource
import select
import signal
import socket
import struct
import stat
import time
import uuid

from memory_collector import counters, mounts, memberships
from jvm_evidence import scan_failure_logs

class Refusal(RuntimeError):
    pass


def require(ok, message):
    if not ok:
        raise Refusal(message)


def read_status(pid):
    return dict(line.split(':', 1) for line in Path('/proc', str(pid), 'status').read_text().splitlines() if ':' in line)


def process_identity(pid):
    raw = Path('/proc', str(pid), 'stat').read_text()
    fields = raw[raw.rfind(')')+2:].split()
    require(raw.split(' ', 1)[0] == str(pid) and len(fields) >= 20, 'invalid PID stat')
    return [pid, int(fields[19])]


def safe_root_path(path):
    """Reject symlink components and paths writable by non-root at every level."""
    path = Path(path)
    require(path.is_absolute() and '..' not in path.parts, 'unsafe root path')
    for part in [path, *path.parents]:
        s = part.lstat()
        require(not part.is_symlink() and s.st_uid == 0 and not s.st_mode & 0o022,
                'non-root-writable or symlink path: '+str(part))
    return path


def file_identity(fd):
    s=os.fstat(fd);return [s.st_dev,s.st_ino]


def open_at(directory, name, flags=os.O_RDONLY):
    return os.open(name, flags | os.O_CLOEXEC | os.O_NOFOLLOW, dir_fd=directory)


def read_fd(fd):
    return os.pread(fd, 1024*1024, 0).decode('utf-8')


def write_at(directory, name, text):
    fd=open_at(directory,name,os.O_WRONLY)
    try: require(os.write(fd,text.encode()) == len(text.encode()), 'short kernel write')
    finally: os.close(fd)


def validate_seal(status, identity, expected_identity, group, expected_group, namespaces, parent_namespaces):
    require(identity == expected_identity, 'PID reuse during pre-exec')
    require(group == expected_group, 'worker membership escaped')
    for key in ('Uid','Gid'):
        values=[int(v) for v in status[key].split()]
        require(len(values)==4 and len(set(values))==1 and values[0]>0, 'unsealed credentials')
    require(not status['Groups'].strip(), 'supplementary groups remain')
    for key in ('CapInh','CapPrm','CapEff','CapBnd','CapAmb'):
        require(int(status[key].strip(),16)==0,'capability remains: '+key)
    require(status['NoNewPrivs'].strip()=='1' and status['Seccomp'].strip()=='2','NoNewPrivs/seccomp absent')
    require(all(namespaces[n] != parent_namespaces[n] for n in ('net','ipc','mnt')), 'namespace isolation absent')


def namespace_ids(pid):
    return {n:os.stat('/proc/'+str(pid)+'/ns/'+n).st_ino for n in ('net','ipc','mnt')}


class CounterLifetime:
    """Same held descriptor + identity for all reads, including post-drain."""
    def __init__(self, fd, boot):
        self.fd=fd;self.identity=file_identity(fd);self.boot=boot
        self.samples=[];self.positive=[];self.gaps=[]

    def sample(self):
        try:
            require(file_identity(self.fd)==self.identity,'counter descriptor identity changed')
            require(Path('/proc/sys/kernel/random/boot_id').read_text().strip()==self.boot,'boot changed')
            raw=read_fd(self.fd);v=counters(raw,('oom','oom_kill'));v={k:v[k] for k in ('oom','oom_kill')}
            receipt={'tick_ns':time.monotonic_ns(),'identity':self.identity,'raw':raw,'counters':v}
            if self.samples:
                last=self.samples[-1]['counters'];require(all(v[k]>=last[k] for k in v),'counter reset')
                delta={k:v[k]-last[k] for k in v}
                if any(delta.values()):self.positive.append({'from':len(self.samples)-1,'to':len(self.samples),'delta':delta})
            else:
                if any(v.values()):self.positive.append({'creation_nonzero':v})
                require(v=={'oom':0,'oom_kill':0},'nonzero creation counters')
            self.samples.append(receipt);return receipt
        except Exception as exc:
            self.gaps.append(str(exc));raise

    def result(self):
        return {'status':'FAIL' if self.positive else ('INCOMPLETE' if self.gaps or len(self.samples)<2 else 'OBSERVED_ZERO'),
                'samples':self.samples,'positive_intervals':self.positive,'gaps':self.gaps,
                'scope':'exclusive cgroup subtree; no historical or host-global claim'}


def drain(group_fd, events_fd, lifetime, timeout=15):
    """Only this newly created group; caller remains outside. No PID-list killing."""
    write_at(group_fd,'cgroup.kill','1')
    end=time.monotonic()+timeout
    while True:
        raw=read_fd(events_fd);v=counters(raw,('populated',))
        if v['populated']==0:
            final=lifetime.sample()
            return {'populated_raw':raw,'final_counter':final,'drained':True}
        require(time.monotonic()<end,'cgroup drain deadline exceeded')
        time.sleep(0.02)


def libc_call(name,*args):
    lib=ctypes.CDLL(None,use_errno=True)
    result=getattr(lib,name)(*args)
    if result != 0:raise OSError(ctypes.get_errno(),name)


def mount_tmpfs(target):
    libc_call('mount',ctypes.c_char_p(b'tmpfs'),ctypes.c_char_p(os.fsencode(target)),ctypes.c_char_p(b'tmpfs'),ctypes.c_ulong(2|4|8),ctypes.c_char_p(b'mode=1777'))


def seccomp_rules():
    """Pure rule specification shared by installer and synthetic tests."""
    blocked=['unshare','setns','mount','umount2','pivot_root','chroot','ptrace','process_vm_writev',
             'pidfd_getfd','bpf','open_by_handle_at','io_uring_setup','keyctl','add_key','request_key']
    rules=[(n,errno.EPERM,None) for n in blocked]
    rules.append(('clone3',errno.ENOSYS,None)) # glibc can fall back to filtered clone
    # Reject all namespace-creating clone flags, while preserving ordinary threads/fork.
    for flag in (0x02000000,0x04000000,0x08000000,0x10000000,0x20000000,0x40000000,0x00020000):
        rules.append(('clone',errno.EPERM,(0,7,flag,flag))) # MASKED_EQ
    # UNIX pathname sockets cross net namespaces; deny both socket and socketpair.
    for name in ('socket','socketpair'):
        rules.append((name,errno.EPERM,(0,4,socket.AF_UNIX,0))) # EQ
    return rules


def install_seccomp():
    lib=ctypes.CDLL('libseccomp.so.2',use_errno=True)
    class Arg(ctypes.Structure):
        _fields_=[('arg',ctypes.c_uint),('op',ctypes.c_int),('a',ctypes.c_uint64),('b',ctypes.c_uint64)]
    lib.seccomp_init.argtypes=[ctypes.c_uint32];lib.seccomp_init.restype=ctypes.c_void_p
    lib.seccomp_syscall_resolve_name.argtypes=[ctypes.c_char_p];lib.seccomp_syscall_resolve_name.restype=ctypes.c_int
    lib.seccomp_rule_add_array.argtypes=[ctypes.c_void_p,ctypes.c_uint32,ctypes.c_int,ctypes.c_uint,ctypes.POINTER(Arg)]
    lib.seccomp_load.argtypes=[ctypes.c_void_p];lib.seccomp_release.argtypes=[ctypes.c_void_p]
    ctx=lib.seccomp_init(0x7fff0000);require(ctx,'seccomp allocation failed')
    try:
        for name,error,condition in seccomp_rules():
            nr=lib.seccomp_syscall_resolve_name(name.encode());require(nr>=0,'unknown syscall '+name)
            arg=Arg(*condition) if condition else None
            require(lib.seccomp_rule_add_array(ctx,0x00050000|error,nr,1 if arg else 0,ctypes.byref(arg) if arg else None)==0,'seccomp rule rejected')
        require(lib.seccomp_load(ctx)==0,'seccomp load failed')
    finally:lib.seccomp_release(ctx)


def seal_worker(uid,gid,home):
    # Actual kernel calls; NEVER invoked by the synthetic test suite.
    libc_call('unshare',0x40000000|0x08000000|0x00020000) # NET IPC MNT
    libc_call('mount',None,ctypes.c_char_p(b'/'),None,ctypes.c_ulong(1<<18|16384),None) # REC|PRIVATE
    mount_tmpfs('/tmp');mount_tmpfs('/dev/shm')
    # Fresh netns has no external interfaces. Enable only its loopback for internal daemons.
    import fcntl
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
        flags=fcntl.ioctl(sock.fileno(),0x8913,struct.pack('16sH14x',b'lo',0))
        fcntl.ioctl(sock.fileno(),0x8914,struct.pack('16sH14x',b'lo',struct.unpack('16sH14x',flags)[1]|1))
    os.environ.clear();os.environ.update(HOME=str(home),GRADLE_USER_HOME=str(home/'gradle'),TMPDIR='/tmp',XDG_CACHE_HOME=str(home/'cache'),XDG_CONFIG_HOME=str(home/'config'),PATH='/usr/bin:/bin',LANG='C.UTF-8')
    # The trusted child still has root capabilities at this point.
    last=int(Path('/proc/sys/kernel/cap_last_cap').read_text())
    for cap in range(last+1):libc_call('prctl',24,cap,0,0,0) # bounding drop
    libc_call('prctl',47,4,0,0,0) # ambient CLEAR_ALL
    os.setgroups([]);os.setresgid(gid,gid,gid);os.setresuid(uid,uid,uid)
    class CapHeader(ctypes.Structure):
        _fields_=[('version',ctypes.c_uint32),('pid',ctypes.c_int)]
    class CapData(ctypes.Structure):
        _fields_=[('effective',ctypes.c_uint32),('permitted',ctypes.c_uint32),('inheritable',ctypes.c_uint32)]
    header=CapHeader(0x20080522,0);data=(CapData*2)()
    libc_call('capset',ctypes.byref(header),ctypes.byref(data))
    libc_call('prctl',38,1,0,0,0) # NoNewPrivs
    install_seccomp()
    status=read_status('self')
    for key in ('CapInh','CapPrm','CapEff','CapBnd','CapAmb'):require(int(status[key],16)==0,'capability seal failed')
    require(status['NoNewPrivs'].strip()=='1','NoNewPrivs failed')


def verify_worker_uid_unused(uid):
    require(type(uid) is int and uid>0,'non-root dedicated UID required')
    for p in Path('/proc').iterdir():
        if p.name.isdigit():
            try: values=[int(x) for x in read_status(p.name)['Uid'].split()]
            except FileNotFoundError:raise Refusal('process inventory raced; dedicated UID unverified')
            require(uid not in values,'worker UID already in use')


def wait_pipe(fd,timeout):
    require(select.select([fd],[],[],timeout)[0], 'worker handshake deadline exceeded')
    require(os.read(fd,1)==b'R','worker handshake failed')


def collect_logs(directory_fd, output, run_id, channel, max_bytes=16*1024*1024):
    """Copy post-drain scoped logs from a retained directory FD, never /tmp globally.

    File origin is the exclusive mount/group, not proof that each line is true.
    No caller source_bound flag can grant qualification.
    """
    receipt={'run_id':run_id,'channel':channel,'directory_identity':file_identity(directory_fd),'files':[],'gaps':[]}
    def visit(fd,relative,depth):
        require(depth<=8,'log directory depth exceeds bound')
        for name in sorted(os.listdir(fd)):
            s=os.stat(name,dir_fd=fd,follow_symlinks=False);rel=relative+name
            require(not stat.S_ISLNK(s.st_mode),'log symlink refused: '+rel)
            if stat.S_ISDIR(s.st_mode):
                child=open_at(fd,name,os.O_RDONLY|os.O_DIRECTORY)
                try:visit(child,rel+'/',depth+1)
                finally:os.close(child)
            elif name.endswith('.log'):
                require(stat.S_ISREG(s.st_mode) and s.st_nlink==1,'nonregular/aliased log')
                require(len(receipt['files'])<128 and s.st_size<=max_bytes,'log capture bound exceeded')
                opened=open_at(fd,name)
                try:
                    require(file_identity(opened)==[s.st_dev,s.st_ino],'log replaced before open')
                    data=os.read(opened,max_bytes+1);after=os.fstat(opened)
                    require(len(data)==s.st_size and after.st_size==s.st_size and after.st_mtime_ns==s.st_mtime_ns,'log changed/truncated during capture')
                finally:os.close(opened)
                target=Path(output)/(channel+'-'+hashlib.sha256(rel.encode()).hexdigest()+'.log')
                with target.open('xb') as stream:stream.write(data)
                receipt['files'].append({'relative_path':rel,'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data),'identity':[s.st_dev,s.st_ino],'captured_path':str(target)})
    try:visit(directory_fd,'',0)
    except Exception as exc:receipt['gaps'].append(str(exc))
    return receipt


def supervise_probe(parent, evidence, uid, gid, timeout=5):
    """Real OS backend, inactive unless explicitly called after separate approval.

    Fixed benign image only. No argv/env/JSON permit can select a compiler here.
    Requires preexisting root-owned protected parent and evidence parent.
    """
    require(os.geteuid()==0 and os.getuid()==0,'real root supervisor required')
    require(0<timeout<=15,'probe bound invalid')
    require(type(gid) is int and gid>0,'dedicated non-root GID required')
    verify_worker_uid_unused(uid)
    parent=safe_root_path(parent);evidence=Path(evidence);safe_root_path(evidence.parent)
    require(all(p.stat().st_mode & 0o001 for p in [evidence.parent,*evidence.parent.parents]),
            'protected workspace ancestors must be traversable by dedicated worker UID')
    require(not evidence.exists(),'evidence path exists')
    raw_cg=Path('/proc/self/cgroup').read_text();raw_mount=Path('/proc/self/mountinfo').read_text()
    matches=[m for m in mounts(raw_mount) if m['version']==2 and parent.is_relative_to(m['mountpoint']) and 'memory_localevents' not in m['options']]
    require(len(matches)==1,'no unique hierarchical cgroup2 mount')
    mount=matches[0];require(mount['root']=='/','non-root mount mapping unsupported')
    require(parent != Path(mount['mountpoint']),'global cgroup root refused')
    require('memory' in (parent/'cgroup.controllers').read_text().split(),'memory controller unavailable')
    require('memory' in (parent/'cgroup.subtree_control').read_text().split(),'parent memory delegation not enabled; no automatic enable')
    # Do not create policies/permissions upstream. All migration ancestors must be protected.
    for ancestor in [parent,*parent.parents]:
        if not ancestor.is_relative_to(mount['mountpoint']):break
        for name in ('cgroup.procs','cgroup.threads'):
            s=(ancestor/name).lstat();require(s.st_uid==0 and not s.st_mode&0o022,'migration ancestor writable')
    evidence.mkdir(mode=0o700);run_id=str(uuid.uuid4());group=parent/('argentum-'+run_id)
    journal={'run_id':run_id,'status':'INCOMPLETE','raw_cgroup':raw_cg,'raw_mountinfo':raw_mount,'mount':mount,'compile_enabled':False}
    pid=None;pidfd=None;gfd=efd=cfd=None;lifetime=None;created=False;drained=False;fds=[];logdirs=[]
    journal_path=evidence/'receipt.json'
    def save():
        journal_path.write_text(json.dumps(journal,indent=2)+'\n');os.chmod(journal_path,0o600)
    try:
        group.mkdir(mode=0o755);created=True
        gfd=os.open(group,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC)
        efd=open_at(gfd,'cgroup.events');cfd=open_at(gfd,'memory.events')
        testkill=open_at(gfd,'cgroup.kill',os.O_WRONLY);os.close(testkill)
        require(counters(read_fd(efd),('populated',))['populated']==0,'group not empty')
        require((group/'cgroup.type').read_text().strip()=='domain','non-domain cgroup')
        expected_group='/'+str(group.relative_to(mount['mountpoint']))
        current=[m['group'] for m in memberships(raw_cg) if m['version']==2]
        require(len(current)==1 and not Path(current[0]).is_relative_to(expected_group),'supervisor inside target')
        for name in ('cgroup.procs','cgroup.threads'):
            s=(group/name).stat();require(s.st_uid==0 and not s.st_mode&0o022,'worker migration writable')
        boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip();lifetime=CounterLifetime(cfd,boot);lifetime.sample()
        journal.update(boot_id=boot,cgroup_identity=file_identity(gfd),counter_identity=lifetime.identity,group=expected_group)
        journal['supervisor_source_sha256']=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        home=evidence.parent/('worker-'+run_id);home.mkdir(mode=0o700);os.chown(home,uid,gid)
        journal['worker_home']=str(home)
        # Separate from root-only evidence. Preserve this private directory for
        # inspection; no unsafe recursive deletion of worker-controlled paths.
        image=safe_root_path('/usr/bin/true');image_sha=hashlib.sha256(image.read_bytes()).hexdigest()
        journal['image']={'path':str(image),'sha256':image_sha,'stat':[image.stat().st_dev,image.stat().st_ino]}
        image_fd=os.open(image,os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC)
        require(file_identity(image_fd)==journal['image']['stat'],'probe executable changed')
        fds.append(image_fd)
        ready_r,ready_w=os.pipe2(os.O_CLOEXEC);go_r,go_w=os.pipe2(os.O_CLOEXEC);fds.extend([ready_r,ready_w,go_r,go_w])
        logfd=os.open(evidence/'worker-output.log',os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_CLOEXEC,0o600);fds.append(logfd)
        parentns=namespace_ids('self');pid=os.fork()
        if pid==0:
            try:
                os.close(ready_r);os.close(go_w)
                # Parent joins the held child before this handshake releases setup.
                require(os.read(go_r,1)==b'J','membership handshake absent')
                for fd in (gfd,efd,cfd):os.close(fd)
                os.dup2(logfd,1);os.dup2(logfd,2)
                null=os.open('/dev/null',os.O_RDONLY);os.dup2(null,0);os.close(null)
                # Close inherited descriptors, keeping only handshake and standard IO.
                for item in list(Path('/proc/self/fd').iterdir()):
                    fd=int(item.name)
                    if fd>2 and fd not in (ready_w,go_r,image_fd):
                        try:os.close(fd)
                        except OSError:pass
                seal_worker(uid,gid,home)
                os.write(ready_w,b'R');require(os.read(go_r,1)==b'E','exec release absent')
                os.close(ready_w);os.close(go_r)
                os.execve(image_fd,[str(image)],dict(os.environ))
            except BaseException:
                os._exit(126)
        os.close(ready_w);fds.remove(ready_w);os.close(go_r);fds.remove(go_r)
        ident=process_identity(pid);pidfd=os.pidfd_open(pid)
        write_at(gfd,'cgroup.procs',str(pid));os.write(go_w,b'J');wait_pipe(ready_r,timeout)
        status=read_status(pid);member=[m['group'] for m in memberships(Path('/proc',str(pid),'cgroup').read_text()) if m['version']==2]
        require(len(member)==1,'ambiguous worker membership')
        validate_seal(status,process_identity(pid),ident,member[0],expected_group,namespace_ids(pid),parentns)
        require((group/'cgroup.procs').read_text().split()==[str(pid)],'unexpected pre-exec cgroup population')
        require([int(x) for x in status['Uid'].split()]==[uid]*4 and [int(x) for x in status['Gid'].split()]==[gid]*4,'unexpected worker credentials')
        journal['preexec']={'identity':ident,'status':status,'membership':member,'namespaces':namespace_ids(pid),'supervisor_namespaces':parentns}
        for channel,path in [('private-tmp',Path('/proc',str(pid),'root/tmp')),('private-home',home)]:
            descriptor=os.open(path,os.O_RDONLY|os.O_DIRECTORY|os.O_CLOEXEC)
            logdirs.append((channel,descriptor));fds.append(descriptor)
        save();os.write(go_w,b'E');start=time.monotonic();done=False
        while time.monotonic()-start<timeout:
            lifetime.sample()
            if select.select([pidfd],[],[],0.05)[0]:done=True;break
        if not done:signal.pidfd_send_signal(pidfd,signal.SIGTERM)
        _,exitstatus=os.waitpid(pid,0) if done else (pid,None)
        journal.update(launcher_exit_status=exitstatus,launcher_timed_out=not done)
        journal['drain']=drain(gfd,efd,lifetime,15);drained=True
        if not done:os.waitpid(pid,0)
        journal['status']='PROBE_OBSERVED_ONLY' if done and exitstatus==0 and lifetime.result()['status']=='OBSERVED_ZERO' else 'INCOMPLETE'
    except BaseException as exc:
        journal['error']=str(exc)
    finally:
        if created and not drained and gfd is not None and efd is not None and lifetime is not None:
            try:journal['cleanup']=drain(gfd,efd,lifetime,15);drained=True
            except BaseException as exc:journal['cleanup_error']=str(exc)
        # Child can fail before joining; pidfd signals target the original identity only.
        if pidfd is not None:
            try:signal.pidfd_send_signal(pidfd,signal.SIGKILL)
            except ProcessLookupError:pass
            try:os.waitpid(pid,0)
            except ChildProcessError:pass
        elif pid is not None and pid>0:
            # pidfd_open failed before membership/release. This is our direct,
            # unreaped fork child, whose PID cannot yet be recycled.
            try:os.kill(pid,signal.SIGKILL)
            except ProcessLookupError:pass
            try:os.waitpid(pid,0)
            except ChildProcessError:pass
        if lifetime:
            journal['memory']=lifetime.result()
            if lifetime.positive:journal['status']='FAIL'
        if drained:
            journal['scoped_logs']=[collect_logs(fd,evidence,run_id,channel) for channel,fd in logdirs]
            if any(item['gaps'] for item in journal['scoped_logs']) and journal['status']!='FAIL':journal['status']='INCOMPLETE'
            records=[]
            for item in journal['scoped_logs']:
                for file in item['files']:
                    name=Path(file['relative_path']).name
                    kind='crash' if name.startswith('hs_err_pid') else ('kotlin' if name.startswith('kotlin-daemon') else 'gradle')
                    records.append({'path':file['captured_path'],'kind':kind,'source_bound':True,
                                    'run_id':run_id,'cgroup_identity':journal.get('cgroup_identity'),
                                    'binding':'held exclusive scope directory; not individual log-line truth'})
            journal['jvm_log_evidence']=scan_failure_logs(records)
            # A true-only probe has no compiler/JVM logs and can never qualify C4.
            if journal['jvm_log_evidence']['status']=='FAIL':journal['status']='FAIL'
        journal['qualifying']=False
        journal['compile_blocker']='compile adapter disabled; isolated dependency egress unresolved; actual kernel operations not qualified'
        save() # Counter lifetime preserved BEFORE remove.
        for fd in [*fds,pidfd,cfd,efd,gfd]:
            if fd is not None:
                try:os.close(fd)
                except OSError:pass
        if created and drained:
            try:group.rmdir();journal['removed']=True
            except OSError as exc:
                journal['removal_error']=str(exc)
                if journal['status']!='FAIL':journal['status']='INCOMPLETE'
        save()
        entries=[hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name for p in sorted(evidence.iterdir()) if p.is_file()]
        (evidence/'SHA256SUMS').write_text('\n'.join(entries)+'\n')
    return journal


def compile_request(*args,**kwargs):
    raise Refusal('C4 compile DISABLED: review, authorization, kernel qualification and dependency egress required')


if __name__=='__main__':
    raise SystemExit('DISABLED: no privileged CLI or compiler entry point is authorized')
