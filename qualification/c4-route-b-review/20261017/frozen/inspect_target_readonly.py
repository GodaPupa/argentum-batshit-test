"""Bounded metadata-only observation. No credentials, sockets or mutations."""
import datetime,hashlib,json,os,platform,subprocess
out={'schema':'ARGENTUM_ROUTE_B_CONTAINER_FACTS_1','captured_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'codespace':os.environ.get('CODESPACE_NAME'),'uname':list(platform.uname()),'uid':os.getuid(),'euid':os.geteuid(),'files':{},'namespaces':{},'commands':{},'scope':'CONTAINER_VISIBLE_NOT_HOST_AUTHORITY'}
for p in ['/proc/1/comm','/proc/sys/kernel/random/boot_id','/proc/1/cgroup','/proc/self/cgroup','/proc/sys/kernel/perf_event_paranoid','/proc/sys/kernel/unprivileged_bpf_disabled','/sys/fs/cgroup/cgroup.controllers','/sys/fs/cgroup/cgroup.subtree_control']:
 try:
  with open(p,'rb') as f:b=f.read(8193)
  out['files'][p]={'content':b[:8192].decode(errors='replace'),'truncated':len(b)>8192}
 except OSError as e:out['files'][p]={'errno':e.errno}
for p in ['/proc/1/status','/proc/self/status']:
 try:
  with open(p) as f:out['files'][p]=[l.strip() for l in f if l.startswith(('Uid:','Gid:','Cap','NoNewPrivs:','Seccomp:'))]
 except OSError as e:out['files'][p]={'errno':e.errno}
with open('/proc/self/mountinfo') as f:out['cgroup_mounts']=[l.strip() for l in f if ' - cgroup' in l]
for who in ['1','self']:
 for n in ['pid','mnt','cgroup','user']:
  p='/proc/'+who+'/ns/'+n
  try:out['namespaces'][p]=os.readlink(p)
  except OSError as e:out['namespaces'][p]={'errno':e.errno}
for p in ['/run/dbus/system_bus_socket','/sys/kernel/btf/vmlinux','/sys/kernel/tracing/events/raw_syscalls/sys_enter','/sys/kernel/tracing/events/raw_syscalls/sys_exit']:
 try:
  s=os.stat(p);out['files'][p]={'mode':s.st_mode,'uid':s.st_uid,'gid':s.st_gid,'size':s.st_size}
 except OSError as e:out['files'][p]={'errno':e.errno}
try:
 with open('/sys/kernel/btf/vmlinux','rb') as f:b=f.read(64*1024*1024+1)
 out['btf']={'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest() if len(b)<=64*1024*1024 else None,'bounded':len(b)<=64*1024*1024}
except OSError as e:out['btf']={'errno':e.errno}
for argv in [['systemctl','--version'],['dpkg-query','-W','-f=${Package} ${Version}\n','systemd','libbpf1','python3-dbus-next']]:
 try:
  r=subprocess.run(argv,capture_output=True,text=True,timeout=3);out['commands'][argv[0]]={'exit':r.returncode,'stdout':r.stdout[:8192],'stderr':r.stderr[:8192]}
 except (OSError,subprocess.TimeoutExpired) as e:out['commands'][argv[0]]={'error':type(e).__name__}
print(json.dumps(out,sort_keys=True))
