"""Real browser/server qualification. Snapshot fixtures originate in compiled engine tests."""
import hashlib,json,os,shutil,signal,subprocess,time,urllib.request
from pathlib import Path
from audit import audit,discovery
ROOT=Path.cwd();D=Path(__file__).resolve().parent;OUT=ROOT/'manual-public-occurrence-ui-r5-evidence'
P=json.loads((D/'PROTOCOL.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def record(p,o):save(p,(json.dumps(o,indent=2)+'\n').encode())
def bind():
    assert git('rev-parse','HEAD^')==os.environ['SOURCE']
    assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
    assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
    assert sorted(git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines())==sorted(P['source_delta'])
    assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
    for path,digest in {**P['protected'],**P['bindings']}.items():
        raw=(ROOT/path).read_bytes();assert sha(raw)==digest,path;save(OUT/'source'/path,raw)
    assert not git('diff','--name-only')
    for n in P['protocol_files']:save(OUT/'protocol'/n,(D/n).read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
    record(OUT/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=1,base=P['base'],source_delta=P['source_delta']))
def command(stage,args,cwd=ROOT,env=None):
    folder=OUT/stage;folder.mkdir(parents=True,exist_ok=True)
    with (folder/'output.log').open('wb') as log:rc=subprocess.run(args,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n');return rc
def recipe(stage,*args,env=None):return command(stage,['just','--working-directory',str(ROOT),'--justfile',str(D/'validation.just'),*args],env=env)
def mutate(m):
    path=ROOT/m['path'];raw=path.read_text();assert raw.count(m['old'])==1,m['name']
    path.write_text(raw.replace(m['old'],m['new']));save(OUT/'mutants'/m['name']/m['path'],path.read_bytes())
def restore():
    for path in {m['path'] for m in P['mutations']}:
        save(ROOT/path,subprocess.check_output(['git','show',os.environ['SOURCE']+':'+path]))
def start(name,args,env,cwd=ROOT):
    folder=OUT/name;folder.mkdir(parents=True,exist_ok=True)
    log=(folder/'output.log').open('wb');proc=subprocess.Popen(args,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
    return proc,log
def stop(pair):
    proc,log=pair
    if proc.poll() is None:
        os.killpg(proc.pid,signal.SIGTERM)
        try:proc.wait(timeout=20)
        except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait(timeout=10)
    log.close()
def ready(url,pair,timeout):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        assert pair[0].poll() is None,'server exited before readiness'
        try:
            with urllib.request.urlopen(url,timeout=3) as response:
                if response.status==200:return
        except Exception:pass
        time.sleep(3)
    raise AssertionError('readiness timeout '+url)
def browser(stage,grep,env):
    e={**env,'MANUAL_UI_REPORT':str(OUT/stage/'report.json'),'MANUAL_UI_RESULTS':str(OUT/stage/'results')}
    client=start(stage+'/client',['npm','run','dev','--','--host','127.0.0.1','--strictPort'],e,ROOT/'web-client')
    try:
        ready(e['E2E_BASE_URL'],client,90)
        recipe(stage,'browser',grep,env=e)
    finally:stop(client)
def main():
    OUT.mkdir(exist_ok=True);bind()
    env={**os.environ,'MANUAL_UI_FIXTURE_DIR':str(OUT/'fixtures'),'E2E_BASE_URL':'http://127.0.0.1:5173','E2E_SERVER_URL':'http://127.0.0.1:8080','GAME_SERVER_URL':'http://127.0.0.1:8080','GAME_DEV_ENDPOINTS_ENABLED':'true','SKIP_WEB_SERVER':'true','CI':'true'}
    assert recipe('compile-normal','compile',env=env)==0
    for module in ['web-client','e2e-scenarios']:
        assert command('install-'+module,['npm','ci'],ROOT/module,env)==0
    assert recipe('compile-client-normal','client-typecheck',env=env)==0
    assert recipe('compile-browser-tests','browser-typecheck',env=env)==0
    for m in P['mutations']:
        mutate(m)
        try:assert recipe('compile-'+m['name'],'client-typecheck',env=env)==0
        finally:restore()
    for m in P['mutations']:
        assert command('discover-'+m['name'],['npx','playwright','test','--config','manual-occurrence.config.ts','--grep',m['grep'],'--list','--reporter=json'],ROOT/'e2e-scenarios',env)==0
    discovery(OUT)
    bind()
    assert command('install-browser',['npx','playwright','install','--with-deps','chromium'],ROOT/'e2e-scenarios',env)==0
    junit=ROOT/'rules-engine/build/test-results/test'
    if junit.exists():shutil.rmtree(junit)
    rc=recipe('fixture-tests','fixtures',env=env)
    if junit.exists():shutil.copytree(junit,OUT/'fixture-tests/junit')
    assert rc==0
    server=start('server',['just','--working-directory',str(ROOT),'--justfile',str(D/'validation.just'),'server'],env)
    try:
        ready(env['E2E_SERVER_URL']+'/api/dev/scenarios/cards',server,600)
        browser('normal','UI ',env)
        audit(OUT,only='normal')
        for m in P['mutations']:
            mutate(m)
            try:
                browser(m['name'],m['grep'],env)
                audit(OUT,only=m['name'])
            finally:restore()
        browser('restored','UI ',env)
        record(OUT/'AUDIT.json',audit(OUT))
    finally:stop(server)
    bind();record(OUT/'FINAL_SOURCE_BINDING.json',dict(unchanged=True,bindings=P['bindings'],protected=P['protected']))
if __name__=='__main__':
    try:main()
    except Exception as e:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
    finally:
        restore()
        try:bind();record(OUT/'RESTORATION.json',{'restored':True})
        except Exception as e:record(OUT/'RESTORATION_FAILURE.json',{'error':str(e)})
        record(OUT/'MANIFEST.json',{str(p.relative_to(OUT)):sha(p.read_bytes()) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'})
