"""Candidate fixed PID1 read transport and descriptor scope broker.

No running host calls on import. Live construction is sealed by deployment_barrier.
No StartTransientUnit, arbitrary command executor or ancestor writes are exposed.
Unit creation transaction remains a separately bound integration dependency.
"""
import asyncio
import errno
import os
import socket
import stat
import struct
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from common import Blocked, require, deployment_barrier

BUS='/run/dbus/system_bus_socket'
MANAGER='org.freedesktop.systemd1'
ROOT='/org/freedesktop/systemd1'
UNIT='argentum-c4-v6-probe.service'

class DBusNextWire:
    """Concrete dbus-next 0.2.3 adapter. Dependency not installed/run in local tests.

    _sock is a private API; exact dependency audit and pin are mandatory before
    deployment. Missing or incompatible API fails closed, never busctl fallback.
    """
    @classmethod
    async def open_live(cls):
        deployment_barrier()
        from dbus_next.aio import MessageBus
        from dbus_next.auth import AuthExternal
        bus=MessageBus(bus_address='unix:path='+BUS,auth=AuthExternal(),negotiate_unix_fd=False)
        await asyncio.wait_for(bus.connect(),2)
        obj=cls();obj.bus=bus
        try:
            obj.peer=struct.unpack('3i',bus._sock.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))
            require(obj.peer[1:]==(0,0),'non-root system-bus peer')
        except BaseException:
            bus.disconnect();raise
        return obj
    async def call(self,destination,path,interface,member,signature='',body=None):
        allowed = (
            destination=='org.freedesktop.DBus' and path=='/org/freedesktop/DBus' and interface=='org.freedesktop.DBus' and member in {'GetNameOwner','GetConnectionCredentials'} and signature=='s' and type(body) is list and len(body)==1 and (body[0]==MANAGER or (isinstance(body[0],str) and body[0].startswith(':')))
        ) or (
            isinstance(destination,str) and destination.startswith(':') and (
                (path==ROOT and interface==MANAGER+'.Manager' and member=='GetUnit' and signature=='s' and body==[UNIT]) or
                (path==ROOT+'/unit/argentum_2dc4_2dv6_2dprobe_2eservice' and interface=='org.freedesktop.DBus.Introspectable' and member=='Introspect' and signature=='' and not body) or
                (path==ROOT+'/unit/argentum_2dc4_2dv6_2dprobe_2eservice' and interface=='org.freedesktop.DBus.Properties' and member=='GetAll' and signature=='s' and body in [[MANAGER+'.Unit'],[MANAGER+'.Service']])
            )
        )
        require(allowed,'DBus endpoint allowlist')
        from dbus_next import Message, MessageType
        reply=await asyncio.wait_for(self.bus.call(Message(destination=destination,path=path,interface=interface,member=member,signature=signature,body=body or [])),2)
        require(reply is not None and reply.message_type==MessageType.METHOD_RETURN,'DBus error/disconnect')
        require(reply.sender==destination,'reply sender drift')
        return reply.body
    def close(self):self.bus.disconnect()

def unbox(x):
    if hasattr(x,'value'):return unbox(x.value)
    if isinstance(x,dict):return {k:unbox(v) for k,v in x.items()}
    if isinstance(x,(list,tuple)):return [unbox(v) for v in x]
    return x

class ManagerSession:
    """Transport orchestrator. Injection is testability, NOT a trust credential."""
    def __init__(self,wire,boot_reader):
        self.wire=wire;self.boot_reader=boot_reader;self.owner=None;self.boot=None;self.unit_path=None
    async def daemon(self,member,body):
        require(member in {'GetNameOwner','GetConnectionCredentials'},'daemon method allowlist')
        return await self.wire.call('org.freedesktop.DBus','/org/freedesktop/DBus','org.freedesktop.DBus',member,'s',body)
    async def bind(self):
        require(self.wire.peer[1:]==(0,0),'bus credentials')
        before=self.boot_reader()
        owner=(await self.daemon('GetNameOwner',[MANAGER]))[0]
        require(isinstance(owner,str) and owner.startswith(':'),'unique owner')
        credentials=unbox((await self.daemon('GetConnectionCredentials',[owner]))[0])
        require(type(credentials.get('ProcessID')) is int and credentials['ProcessID']==1 and type(credentials.get('UnixUserID')) is int and credentials['UnixUserID']==0,'owner is not root PID1')
        require((await self.daemon('GetNameOwner',[MANAGER]))[0]==owner and self.boot_reader()==before,'boot/owner race')
        self.owner=owner;self.boot=before
    async def revalidate(self,binding=None):
        require(self.owner is not None,'unbound manager')
        require(self.boot_reader()==self.boot,'boot drift')
        require((await self.daemon('GetNameOwner',[MANAGER]))[0]==self.owner,'PID1 owner drift')
        credentials=unbox((await self.daemon('GetConnectionCredentials',[self.owner]))[0])
        require(credentials.get('ProcessID')==1 and credentials.get('UnixUserID')==0,'PID1 credentials drift')
        if binding is not None:
            props=await self.snapshot()
            require(props['unit']['InvocationID']==list(bytes.fromhex(binding.invocation)),'stale invocation')
            require(binding.boot==self.boot and binding.unit==UNIT,'binding drift')
            return props
    async def snapshot(self):
        require(self.owner is not None,'unbound manager')
        before=(await self.daemon('GetNameOwner',[MANAGER]))[0]
        path=(await self.wire.call(self.owner,ROOT,MANAGER+'.Manager','GetUnit','s',[UNIT]))[0]
        require(path==ROOT+'/unit/argentum_2dc4_2dv6_2dprobe_2eservice','unexpected unit path')
        result={}
        for key,interface in [('unit',MANAGER+'.Unit'),('service',MANAGER+'.Service')]:
            xml=(await self.wire.call(self.owner,path,'org.freedesktop.DBus.Introspectable','Introspect'))[0]
            require(len(xml)<1048576,'oversize introspection')
            node=ET.fromstring(xml)
            interfaces={n.attrib.get('name'):{p.attrib.get('name') for p in n.findall('property')} for n in node.findall('interface')}
            expected={'InvocationID'} if key=='unit' else {'Delegate','DelegateSubgroup','MainPID','ControlGroup'}
            require(expected<=interfaces.get(interface,set()),'live interface unsupported')
            result[key]=unbox((await self.wire.call(self.owner,path,'org.freedesktop.DBus.Properties','GetAll','s',[interface]))[0])
        require(before==self.owner==(await self.daemon('GetNameOwner',[MANAGER]))[0] and self.boot_reader()==self.boot,'snapshot owner/boot race')
        self.unit_path=path
        return result

@dataclass(frozen=True)
class FDIdentity:
    dev:int
    inode:int
    mount:int

def fd_identity(fd):
    s=os.fstat(fd)
    with open('/proc/self/fdinfo/'+str(fd),encoding='ascii') as f:
        rows=dict(line.strip().split(':',1) for line in f if ':' in line)
    return FDIdentity(s.st_dev,s.st_ino,int(rows['mnt_id']))

def root_only(fd,directory=True):
    s=os.fstat(fd)
    require((stat.S_ISDIR(s.st_mode) if directory else stat.S_ISREG(s.st_mode)),'FD type')
    require(s.st_uid==s.st_gid==0 and not s.st_mode&0o022,'unsafe ownership/mode')
    try:acl=os.getxattr(fd,'system.posix_acl_access')
    except OSError as e:
        require(e.errno==errno.ENODATA,'ACL unreadable');acl=b''
    require(not acl,'extended ACL not approved')

class HeldScope:
    """Owns duplicate descriptors. Only fixed D/S/C traversal, never guessed D.

    root_fd must be supplied by the reviewed mount resolver after fstatfs/statx
    and namespace verification. This module does NOT claim that resolver exists.
    """
    def __init__(self,root_fd,control_group,identity=fd_identity,ownership=root_only):
        parts=control_group.split('/')
        require(control_group.startswith('/') and len(parts)>2 and all(p not in ('','.','..') for p in parts[1:]),'unsafe ControlGroup')
        require(control_group not in ['/system.slice','/system.slice/hosted-compute-agent.service'],'shared parent')
        self.fds={};self.identity=identity;self.ownership=ownership
        fd=os.dup(root_fd)
        try:
            mount=identity(fd).mount
            ownership(fd)
            for part in parts[1:]:
                nxt=os.open(part,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=fd)
                os.close(fd);fd=nxt;ownership(fd)
                require(identity(fd).mount==mount,'cross-mount scope')
            self.fds['D']=fd;fd=-1
            for role,name in [('S','supervisor'),('C','probe')]:
                self.fds[role]=os.open(name,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self.fds['D'])
                ownership(self.fds[role]);require(identity(self.fds[role]).mount==mount,'cross-mount child')
            self.ids={k:identity(v) for k,v in self.fds.items()}
            require(len(set(self.ids.values()))==3,'aliased scope')
        except BaseException:
            self.close();raise
        finally:
            if fd>=0:os.close(fd)
    def revalidate(self):
        for k,fd in self.fds.items():
            self.ownership(fd);require(self.identity(fd)==self.ids[k],'FD substitution')
        for k,n in [('S','supervisor'),('C','probe')]:
            fd=os.open(n,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self.fds['D'])
            try:require(self.identity(fd)==self.ids[k],'child path replaced')
            finally:os.close(fd)
    def read(self,role,name):
        require(role in self.fds and name in {'cgroup.type','cgroup.procs','cgroup.controllers','cgroup.subtree_control','cgroup.events','memory.events'},'control read allowlist')
        self.revalidate();fd=os.open(name,os.O_RDONLY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=self.fds[role])
        try:
            self.ownership(fd,False);require(self.identity(fd).mount==self.ids[role].mount,'control mount')
            data=os.read(fd,65537);require(len(data)<=65536,'control too large');return data.decode('ascii')
        finally:os.close(fd)
    def verify_topology(self,supervisor_pid):
        require(type(supervisor_pid) is int and supervisor_pid>0,'MainPID shape')
        require(self.read('D','cgroup.procs').strip()=='' and self.read('C','cgroup.procs').strip()=='','nonempty D/C')
        require(self.read('S','cgroup.procs').split()==[str(supervisor_pid)],'S population')
        require(all(self.read(k,'cgroup.type').strip()=='domain' for k in self.fds),'non-domain')
        require('memory' in self.read('D','cgroup.controllers').split() and 'memory' in self.read('D','cgroup.subtree_control').split(),'memory not enabled')
    def close(self):
        for fd in self.fds.values():os.close(fd)
        self.fds.clear()

# Pure conversion of effective D-Bus values. A caller-supplied dict is NOT proof.
def validate_effective(snapshot,binding,contract):
    u,s=snapshot['unit'],snapshot['service']
    require(u['Id']==UNIT and u['InvocationID']==list(bytes.fromhex(binding.invocation)),'unit/invocation')
    require(u.get('ActiveState')=='active','unit not active')
    require(s.get('Type')=='exec' and s.get('User')=='root' and s.get('Group')=='root','service identity')
    require(s.get('Delegate') is True and s.get('DelegateControllers')==['memory'] and s.get('DelegateSubgroup')=='supervisor','delegation properties')
    require(s.get('Restart')=='no' and s.get('KillMode')=='control-group' and s.get('SendSIGKILL') is True,'lifecycle policy')
    require(s.get('RuntimeMaxUSec')==contract['properties']['RuntimeMaxSec']*1000000 and s.get('TimeoutStopUSec')==contract['properties']['TimeoutStopSec']*1000000,'time bound drift')
    for key in ('ExecStartPre','ExecStartPost','ExecReload','ExecStop','ExecStopPost'):require(s.get(key)==[],'unexpected service hook')
    # systemd ExecStart a(sasbttttuii): compare command/argv/ignore-failure prefix;
    # execution bookkeeping is not source identity and is preserved separately.
    commands=s.get('ExecStart')
    require(type(commands) is list and len(commands)==1 and len(commands[0])==10,'ExecStart shape')
    require(commands[0][0]=='/opt/argentum-c4-v6/supervisor' and commands[0][1]==['/opt/argentum-c4-v6/supervisor'] and commands[0][2] is False,'arbitrary command')
    require(type(s.get('MainPID')) is int and s['MainPID']>0,'MainPID')
    require(type(s.get('ControlGroup')) is str and s['ControlGroup'].startswith('/') and '..' not in s['ControlGroup'].split('/'),'ControlGroup')
    return s['ControlGroup'],s['MainPID']
