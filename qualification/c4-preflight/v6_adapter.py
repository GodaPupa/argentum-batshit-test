"""Hard-disabled V6 Route B adapter source.

No runtime switches. Every privileged entry refuses before its first operation.
Below the refusal in _review_body is review-only backend source, deliberately
unreachable pending real authority, manager transport and observer integration.
The offline model is not used to authenticate any runtime input.
"""
import errno
import fcntl
import hashlib
import json
import os
from pathlib import Path
import select
import signal
import stat
import struct
import time
import uuid
import kernel_supervisor as v4
from v6_contract import Blocked

ELF_SHA256='2b4fa91b0800579c6fc73330ba78822a0da4ae1a041b4cfbc9a80818672e0ec6'
SOURCE_SHA256='d050d5fff4950a9262f4b86c2b19f082144ce208262f152a1739d981b5431d23'
V5_PARENT='1e968451789bf724999e327a1978fa0dbd9b0331'
STAGED_IMAGE=Path('/opt/argentum-c4-v6/adversarial-helper-v6.elf')
EVIDENCE_PARENT=Path('/var/lib/argentum-c4-v6')
# Linux UAPI fcntl.h: Python builds need not expose these symbolic constants.
F_ADD_SEALS=1033
F_GET_SEALS=1034
SEALS=0x0001|0x0002|0x0004|0x0008 # SEAL, SHRINK, GROW, WRITE


def _disarmed():
    raise Blocked('BLOCKED_HOST_PARENT: source-only V6; authority/manager transport and independent observer not qualified; runtime DISABLED')


def supervise_fixed_v6():
    """No executable/path/PID/approval arguments. Always refuses."""
    _disarmed()


def compile_request(*args,**kwargs):
    raise Blocked('compile DISABLED: no V6 result can enable C4 compilation')


def _require_runtime_authority():
    # Intentionally no JSON/boolean/env permit interpretation.
    _disarmed()


def _authenticated_manager_scope():
    raise Blocked('BLOCKED_HOST_PARENT: authenticated PID1 transport is not implemented; offline dictionaries are not authority')


def _fixed_parent_observer():
    raise Blocked('INCOMPLETE_RUNTIME: bounded independent observer is design-only; polling/helper rows cannot substitute')


def _digest_fd(fd):
    before=os.fstat(fd)
    v4.require(stat.S_ISREG(before.st_mode),'image not regular')
    h=hashlib.sha256();offset=0
    while offset<before.st_size:
        block=os.pread(fd,min(65536,before.st_size-offset),offset)
        v4.require(bool(block),'short image read');h.update(block);offset+=len(block)
    after=os.fstat(fd)
    fields=lambda x:(x.st_dev,x.st_ino,x.st_size,x.st_mode,x.st_uid,x.st_gid,x.st_mtime_ns,x.st_ctime_ns)
    v4.require(fields(before)==fields(after),'image changed during held-FD hash')
    return h.hexdigest(),[before.st_dev,before.st_ino]


def verify_image_fd(fd, sealed=False):
    s=os.fstat(fd)
    v4.require(s.st_uid==0 and s.st_gid==0 and not s.st_mode&0o7022,'unsafe image owner/mode')
    digest,identity=_digest_fd(fd)
    v4.require(digest==ELF_SHA256,'wrong ELF bytes')
    if sealed:v4.require(fcntl.fcntl(fd,F_GET_SEALS)&SEALS==SEALS,'executable memfd not immutable')
    return {'sha256':digest,'identity':identity,'bytes':s.st_size,'sealed':sealed}


def scope_record(s_identity,c_identity,uid,gid,nonce):
    """Exact 72-byte little endian ABI; only verified supervisor supplies this."""
    values=(*s_identity,*c_identity,uid,gid)
    v4.require(len(values)==6 and all(type(x) is int and 0<x<2**64 for x in values),'invalid scope IDs')
    v4.require(s_identity!=c_identity and isinstance(nonce,bytes) and len(nonce)==16 and any(nonce),'invalid nonce/alias')
    return struct.pack('<8s6Q16s',b'ARGC4V6\0',*values,nonce)


def remaining(deadline,now=None):
    value=deadline-(time.monotonic() if now is None else now)
    if value<=0:raise Blocked('INCOMPLETE_RUNTIME: absolute deadline exceeded')
    return value


def _sealed_memfd(name,data,mode):
    _disarmed() # no backend callable can allocate/prepare a runtime image here
    fd=os.memfd_create(name,os.MFD_ALLOW_SEALING|os.MFD_CLOEXEC)
    try:
        offset=0
        while offset<len(data):
            count=os.write(fd,data[offset:]);v4.require(count>0,'short memfd write');offset+=count
        os.fchmod(fd,mode);fcntl.fcntl(fd,F_ADD_SEALS,SEALS)
        return fd
    except BaseException:
        os.close(fd);raise


def _enable_memory_inside_d(scope):
    _disarmed()
    scope.revalidate_before_mutation()
    v4.require(scope.direct_d_pids()==[], 'D contains direct processes')
    available=v4.read_fd(scope.controllers_fd).split()
    enabled=v4.read_fd(scope.subtree_fd).split()
    v4.require('memory' in available, 'BLOCKED_HOST_PARENT: upstream memory unavailable')
    if 'memory' not in enabled:
        # scope.dfd must already be authenticated. No ancestor traversal/writes.
        scope.require_explicit_d_local_enable_authority()
        v4.write_at(scope.dfd,'cgroup.subtree_control','+memory')
    v4.require('memory' in v4.read_fd(scope.subtree_fd).split(), 'D enablement not observed')
    scope.require_upstream_unchanged()


def _bounded_drain(gfd,efd,life,deadline,journal):
    _disarmed()
    # Exact held C only; never D, S, mount root or a PID list.
    remaining(deadline);v4.write_at(gfd,'cgroup.kill','1');journal['kill_issued']=True
    while True:
        remaining(deadline);raw=v4.read_fd(efd)
        if v4.counters(raw,('populated',))['populated']==0:
            journal['populated_zero']=raw
            journal['final']=life.sample() # same held FD, after zero
            return
        time.sleep(min(0.02,remaining(deadline)))


def _bounded_reap(pid,deadline):
    _disarmed()
    while True:
        remaining(deadline);found,status=os.waitpid(pid,os.WNOHANG)
        if found==pid:return status
        time.sleep(min(0.02,remaining(deadline)))


def _review_body():
    """Dormant concrete backend source. Not runnable through another API.

    Manager scope and observer providers deliberately refuse; an independent
    reviewer must assess those additional source deltas before any activation.
    Never remove this gate merely to run tests.
    """
    _disarmed()
    _require_runtime_authority()
    scope=_authenticated_manager_scope() # holds D/S; actual transport unavailable
    observer=_fixed_parent_observer() # obtain before mutation; unavailable
    start=time.monotonic();overall=start+38
    setup_end=start+8
    fds=[];pid=pidfd=gfd=efd=cfd=None;life=None;created=False;drained=False
    journal={'compile_enabled':False,'runtime_enforcement_qualified':False,'status':'INCOMPLETE_RUNTIME'}
    # Expected future scope object is root-owned backend state, never caller JSON.
    # All paths below derive from held D or fixed root-protected evidence staging.
    evidence=v4.safe_root_path(EVIDENCE_PARENT)/str(uuid.uuid4())
    evidence.mkdir(mode=0o700)
    def save():
        raw=(json.dumps(journal,sort_keys=True,indent=2)+'\n').encode()
        temp=evidence/'receipt.tmp'
        fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC|os.O_NOFOLLOW,0o600)
        try:
            offset=0
            while offset<len(raw):
                n=os.write(fd,raw[offset:]);v4.require(n>0,'short journal write');offset+=n
            os.fsync(fd)
        finally:os.close(fd)
        os.replace(temp,evidence/'receipt.json')
        d=os.open(evidence,os.O_RDONLY|os.O_DIRECTORY)
        try:os.fsync(d)
        finally:os.close(d)
    try:
        remaining(setup_end);scope.revalidate_before_mutation()
        v4.require(scope.direct_d_pids()==[],'D has internal processes')
        # Source contract allows only D-local enablement after owner authorization.
        _enable_memory_inside_d(scope) # only reviewed D-local write; no ancestors
        os.mkdir('probe',0o755,dir_fd=scope.dfd);created=True
        gfd=v4.open_at(scope.dfd,'probe',os.O_RDONLY|os.O_DIRECTORY)
        efd=v4.open_at(gfd,'cgroup.events');cfd=v4.open_at(gfd,'memory.events')
        v4.require(v4.counters(v4.read_fd(efd),('populated',))['populated']==0,'C not fresh empty')
        scope.verify_new_c(gfd)
        life=v4.CounterLifetime(cfd,scope.boot);life.sample()
        # Stage hash and executed hash refer to different inodes, explicitly.
        image=v4.safe_root_path(STAGED_IMAGE)
        sourcefd=os.open(image,os.O_RDONLY|os.O_CLOEXEC|os.O_NOFOLLOW);fds.append(sourcefd)
        journal['staged_image']=verify_image_fd(sourcefd)
        data=os.pread(sourcefd,journal['staged_image']['bytes'],0)
        v4.require(hashlib.sha256(data).hexdigest()==ELF_SHA256,'staged source changed')
        execfd=_sealed_memfd('argentum-v6-image',data,0o555);fds.append(execfd)
        journal['executed_image']=verify_image_fd(execfd,True)
        sfd=v4.open_at(scope.dfd,'supervisor',os.O_PATH|os.O_DIRECTORY);fds.append(sfd)
        scope.require_same_s(sfd)
        nonce=os.urandom(16)
        contractfd=_sealed_memfd('argentum-v6-scope',scope_record(v4.file_identity(sfd),v4.file_identity(gfd),scope.uid,scope.gid,nonce),0o444);fds.append(contractfd)
        ready_r,ready_w=os.pipe2(os.O_CLOEXEC);go_r,go_w=os.pipe2(os.O_CLOEXEC);fds.extend((ready_r,ready_w,go_r,go_w))
        logfd=os.open(evidence/'helper.jsonl',os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_CLOEXEC,0o600);fds.append(logfd)
        parentns=v4.namespace_ids('self');remaining(setup_end);pid=os.fork()
        if pid==0:
            try:
                # Remap explicit shared descriptors collision-free before closing.
                keep={name:fcntl.fcntl(fd,fcntl.F_DUPFD_CLOEXEC,20) for name,fd in
                      [('ready',ready_w),('go',go_r),('image',execfd),('s',sfd),('contract',contractfd),('log',logfd)]}
                v4.require(os.read(keep['go'],1)==b'J','join release absent')
                os.dup2(keep['log'],1);os.dup2(keep['log'],2)
                null=os.open('/dev/null',os.O_RDONLY);os.dup2(null,0);os.close(null)
                os.dup2(keep['s'],3,inheritable=True);os.dup2(keep['contract'],4,inheritable=True)
                preserve={0,1,2,3,4,keep['ready'],keep['go'],keep['image']}
                for entry in list(Path('/proc/self/fd').iterdir()):
                    fd=int(entry.name)
                    if fd not in preserve:
                        try:os.close(fd)
                        except OSError:pass
                v4.seal_worker(scope.uid,scope.gid,scope.private_home)
                os.write(keep['ready'],b'R');v4.require(os.read(keep['go'],1)==b'E','exec release absent')
                os.close(keep['ready']);os.close(keep['go'])
                os.execve(keep['image'],['argentum-v6-fixed-helper'],{})
            except BaseException:os._exit(126)
        os.close(ready_w);fds.remove(ready_w);os.close(go_r);fds.remove(go_r)
        identity=v4.process_identity(pid);pidfd=os.pidfd_open(pid)
        observer.bind_held_child(pid,pidfd,identity,gfd) # no arbitrary external PID
        v4.write_at(gfd,'cgroup.procs',str(pid));os.write(go_w,b'J')
        seal_end=min(overall,time.monotonic()+5);v4.wait_pipe(ready_r,remaining(seal_end))
        scope.verify_preexec(pid,pidfd,identity,gfd,parentns)
        observer.require_trace_armed_without_gaps()
        verify_image_fd(execfd,True);life.sample();save();os.write(go_w,b'E')
        # Must see executed image, all forks, reparent and live descendant.
        journal['observation']=observer.observe_until_persistent(min(overall,time.monotonic()+10),ELF_SHA256,nonce)
        observer.require_postexec_seal_and_live_descendant(gfd)
        _bounded_drain(gfd,efd,life,min(overall,time.monotonic()+10),journal);drained=True
        journal['launcher_status']=_bounded_reap(pid,min(overall,time.monotonic()+5))
        observer.require_all_owned_descendants_reaped(overall)
        journal['logs']=observer.seal_scoped_logs(evidence,overall)
        journal['status']='NEGATIVE_CONTROL_OBSERVED' # only reachable with real provider proof
    except BaseException as exc:
        journal['error']=repr(exc)
    finally:
        # All paths share the original absolute deadline, not a reset timeout.
        if created and life and not drained:
            try:_bounded_drain(gfd,efd,life,overall,journal);drained=True
            except BaseException as exc:journal['cleanup_error']=repr(exc)
        if pidfd is not None:
            try:
                remaining(overall);signal.pidfd_send_signal(pidfd,signal.SIGKILL)
                _bounded_reap(pid,overall)
            except (ProcessLookupError,ChildProcessError):pass
            except BaseException as exc:journal['reap_error']=repr(exc)
        if life:
            journal['memory']=life.result()
            if life.positive:journal['status']='FAIL'
            elif journal['memory']['status']!='OBSERVED_ZERO':journal['status']='INCOMPLETE_RUNTIME'
        if any(k in journal for k in ('error','cleanup_error','reap_error')) and journal['status']!='FAIL':journal['status']='INCOMPLETE_RUNTIME'
        save() # preserve counters before unlink/removal
        if drained:
            try:os.rmdir('probe',dir_fd=scope.dfd);journal['removed']=True
            except OSError as exc:
                journal['remove_error']=repr(exc)
                if journal['status']!='FAIL':journal['status']='INCOMPLETE_RUNTIME'
        for fd in [*fds,pidfd,cfd,efd,gfd]:
            if fd is not None:
                try:os.close(fd)
                except OSError:pass
        save()
        # Real provider must implement bounded durable manifest & exact unit
        # cleanup. If unavailable, it refuses and cannot emit a clean terminal.
        try:observer.finalize_manifest_and_unit_receipt(evidence,overall,journal)
        except BaseException as exc:
            journal['manifest_error']=repr(exc)
            if journal['status']!='FAIL':journal['status']='INCOMPLETE_RUNTIME'
            save()
    return journal

if __name__=='__main__':
    _disarmed()
