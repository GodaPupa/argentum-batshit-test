"""Host-only launcher for the unchanged MT-SURGE-BW-20261114 qualifier."""
import datetime, hashlib, json, os, pathlib, shutil, stat, subprocess, sys, urllib.parse, urllib.request

HOST = pathlib.Path(__file__).resolve().parent
ROOT = HOST.parent / 'manual-transmission'
RECORD = ROOT / 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
VALIDATION = '94025a17b5a0ded2b6d522c63e53b59397059e90'
CLAIM = '142fa46c93958e5bb1742869fcfbbc90a850a8b5'
def sha(b): return hashlib.sha256(b).hexdigest()
def git(*args): return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()
def write(name, data): (HOST/name).write_text(json.dumps(data, indent=2)+'\n')
def identity():
    pins = json.loads((RECORD/'CANDIDATE_PINS.json').read_text())
    return {'head':git('rev-parse','HEAD'), 'tree':git('rev-parse','HEAD^{tree}'), 'tracked_diff':git('diff','--'), 'index_diff':git('diff','--cached','--'), 'bindings':{p:sha((ROOT/p).read_bytes()) for p in pins['bindings']}}

before = identity()
assert before['head'] == VALIDATION and not before['tracked_diff'] and not before['index_diff']
write('SOURCE_BEFORE.json', before)
current = json.loads(subprocess.check_output(['git','show',CLAIM+':experiments/manual-transmission/autonomy/CURRENT.json'],cwd=ROOT))
envelope = dict(kind='LOCAL_PREFLIGHT_NOT_GITHUB_RUN',event=None,run=None,attempt=None,first_parent=CLAIM,source=current['surge_bushwhacker_qualification']['source'],validation=VALIDATION,qualification=current['surge_bushwhacker_qualification'],grant=current['surge_bushwhacker_production_authorization'])
write('RECOVERY_EVENT.json', envelope)
proxy = urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
assert proxy.scheme == 'http' and proxy.hostname and proxy.port
trust = HOST/'java-truststore'
if not trust.exists():
    shutil.copyfile('/etc/ssl/certs/java/cacerts', trust)
    for cert in sorted(pathlib.Path('/usr/local/share/ca-certificates').glob('*.crt')):
        subprocess.run(['keytool','-importcert','-noprompt','-trustcacerts','-alias','manual-host-'+cert.stem,'-file',str(cert),'-keystore',str(trust),'-storepass','changeit'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
props = pathlib.Path.home()/'.gradle/gradle.properties'
old = props.read_bytes() if props.exists() else None
oldmode = stat.S_IMODE(props.stat().st_mode) if props.exists() else None
if old is not None: (HOST/'gradle.properties.original').write_bytes(old)
settings = {'systemProp.http.proxyHost':proxy.hostname,'systemProp.http.proxyPort':str(proxy.port),'systemProp.https.proxyHost':proxy.hostname,'systemProp.https.proxyPort':str(proxy.port),'systemProp.javax.net.ssl.trustStore':str(trust)}
settings.update({'org.gradle.jvmargs':'-Xmx4g -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC','kotlin.compiler.execution.strategy':'in-process'})
lines = [line for line in (old or b'').decode().splitlines() if line.strip().split('=',1)[0] not in settings]
temporary = ('\n'.join(lines)+'\n'+'\n'.join(k+'='+v for k,v in settings.items())+'\n').encode()
report = {'started_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'repository_files_modified':[], 'proxy_discovered_in_same_process':True,'proxy_endpoint':f'{proxy.scheme}://{proxy.hostname}:{proxy.port}', 'user_gradle_properties':str(props),'original_properties_sha256':sha(old) if old is not None else None,'temporary_properties_sha256':sha(temporary),'temporary_settings':settings,'truststore':str(trust),'truststore_sha256':sha(trust.read_bytes()),'child_env_overrides':['GRADLE_OPTS','JAVA_TOOL_OPTIONS','RUNNER_TEMP','PYTHONDONTWRITEBYTECODE','GRADLE_LOCK_SLOTS'], 'frozen_runner':str(RECORD/'qualify.py')}
write('HOST_CHANGES.json', report)
code = 1
try:
    props.write_bytes(temporary)
    env = os.environ.copy()
    jvm = ' '.join('-D'+k.removeprefix('systemProp.')+'='+v for k,v in settings.items() if k.startswith('systemProp.'))
    jdk=HOST/'jdk-21.0.13+9'
    assert (jdk/'bin/javac').is_file()
    env.update(JAVA_HOME=str(jdk),PATH=str(jdk/'bin')+os.pathsep+env['PATH'],GRADLE_OPTS='-Dorg.gradle.daemon=false '+jvm,JAVA_TOOL_OPTIONS=jvm,RUNNER_TEMP=str(HOST),PYTHONDONTWRITEBYTECODE='1',GRADLE_LOCK_SLOTS='1')
    report['child_env_overrides'] += ['JAVA_HOME','PATH']
    report['jdk_home']=str(jdk)
    report['jdk_archive_sha256']=sha((HOST/'jdk21.tar.gz').read_bytes())
    report['jdk_publisher_checksum_verified']=report['jdk_archive_sha256']==(HOST/'jdk21.sha256.txt').read_text().split()[0]
    assert report['jdk_publisher_checksum_verified']
    write('HOST_CHANGES.json',report)
    url='https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.4.0/kotlin-gradle-plugin-2.4.0.pom'
    with urllib.request.urlopen(url, timeout=30) as response:
        data=response.read();write('DEPENDENCY_HTTP_PROBE.json',{'url':url,'status':response.status,'bytes':len(data),'sha256':sha(data)})
    subprocess.run(['python3',str(RECORD/'verify_dispatch.py'),str(HOST/'RECOVERY_EVENT.json')],cwd=ROOT,env=env,check=True)
    lock=pathlib.Path.home()/'.cache/argentum/manual-transmission.lock';lock.parent.mkdir(parents=True,exist_ok=True)
    code=subprocess.run(['flock','-x','-w','1800',str(lock),'python3',str(RECORD/'qualify.py')],cwd=ROOT,env=env).returncode
finally:
    if old is None: props.unlink(missing_ok=True)
    else:
        props.write_bytes(old)
        props.chmod(oldmode)
    after=identity();write('SOURCE_AFTER.json',after)
    report.update(finished_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),returncode=code,host_properties_restored=(props.read_bytes()==old if old is not None else not props.exists()),source_unchanged=after==before)
    write('HOST_CHANGES.json',report)
    print(json.dumps({k:report[k] for k in ['returncode','host_properties_restored','source_unchanged']}),flush=True)
sys.exit(code)
