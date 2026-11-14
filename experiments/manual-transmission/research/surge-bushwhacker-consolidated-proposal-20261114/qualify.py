"""One source-frozen qualification; isolated mutations always restore, and failures remain evidence."""
from pathlib import Path
from collections import Counter
import hashlib,json,os,re,shlex,shutil,subprocess,sys,traceback,urllib.request
from audit_reports import check_rows,audit_all,require,digest
from verify_dispatch import verify
P=Path(__file__).resolve().parent;R=P.parents[3];E=R/'manual-surge-bushwhacker-evidence'
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,x):save(p,(json.dumps(x,indent=2)+'\n').encode())
def read(p):return json.loads(p.read_text())
def git(*a):return subprocess.check_output(['git',*a],cwd=R)
commands=[];pins=read(P/'CANDIDATE_PINS.json');matrix=read(P/'TEST_MATRIX.json');mutants=read(P/'COMPILE_WITNESSES.json')['mutants']
normal={m['path']:(R/m['path']).read_bytes() for m in mutants}
expected={r['identity']:1 for r in matrix['rows'] if r['family']!='client'}
def bind():
    for path,pin in pins['bindings'].items():require(digest(R/path)==pin['sha256'],'binding drift '+path)
    require(not git('diff','--name-only').strip(),'tracked worktree drift')
def form(m=None):
    for path,b in normal.items():save(R/path,b)
    label='normal' if m is None else m['id']
    if m:
        require(digest(P/m['patch'])==m['patch_sha256'],'patch drift')
        subprocess.run(['git','apply','--check',str(P/m['patch'])],cwd=R,check=True)
        subprocess.run(['git','apply',str(P/m['patch'])],cwd=R,check=True)
        require(digest(R/m['path'])==m['mutant_sha256'],'mutant drift')
    for path,pin in pins['bindings'].items():
        wanted=m['mutant_sha256'] if m and path==m['path'] else pin['sha256']
        require(digest(R/path)==wanted,'isolated form drift '+path)
    for path in normal:save(E/'source-forms'/label/path,(R/path).read_bytes())
    save(E/'source-forms'/label/'TRACKED.diff',git('diff','--'))
def reports():
    # Walk only known Gradle modules, never archived historical evidence.
    for buildfile in R.glob('**/build.gradle.kts'):
        if any(x in buildfile.parts for x in ('node_modules','.gradle','build')):continue
        yield from (buildfile.parent/'build/test-results/test').glob('TEST-*.xml')
def run(name,args,cwd=R,junit=False):
    if junit:
        for f in list(reports()):f.unlink()
    stdout=E/(name+'.stdout');stderr=E/(name+'.stderr')
    env={k:v for k,v in os.environ.items() if k not in ('KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS')}
    print('START '+name,flush=True)
    with stdout.open('wb') as out,stderr.open('wb') as err:
        code=subprocess.run(args,cwd=cwd,stdout=out,stderr=err,env=env).returncode
    commands.append(dict(name=name,argv=args,cwd=str(cwd),returncode=code));js(E/'COMMANDS.json',commands)
    if junit:
        for f in reports():save(E/name/f.relative_to(R),f.read_bytes())
    print('END '+name+' '+str(code),flush=True);return code
def recipe(name):
    lines=(P/'validation.just').read_text().splitlines();i=lines.index(name+':');return shlex.split(lines[i+1].strip())
def gradle(name,args,junit=False):
    # Force test tasks, not prior reports, as evidence while preserving compilation caches.
    tasks=[x for x in args if x.startswith(':') and x.endswith(':test')]
    if junit:
        clean=[x[:-4]+'cleanTest' for x in tasks]
        if not clean:clean=['cleanTest']
        args=[args[0],*clean,*args[1:]]
    return run(name,args,junit=junit)
def targeted(name,m=None):
    if m:
        module,klass,witness=m['witness'].split('\t');pm,pc,positive=m['positive_control'].split('\t');require(module==pm,'cross-module negative')
        args=['scripts/gradle-locked',':'+module.replace('/',':')+':test','--tests',klass+'.'+witness,'--tests',pc+'.'+positive,
              '--no-build-cache','--no-daemon','--console=plain','--stacktrace','--max-workers=2','-PkotlinCompileParallelism=1']
        code=gradle(name,args,True);require(code!=0,'mutant did not fail '+name)
        check_rows(E/name,{m['witness']:1,m['positive_control']:1},negative=m)
    else:
        require(gradle(name,recipe('targeted'),True)==0,'targeted failed '+name);check_rows(E/name,expected)
def client(name,files=None):
    args=['npm','test','--',*(files or []),'--reporter=json','--outputFile='+str(E/(name+'.json'))]
    require(run(name,args,R/'web-client')==0,name+' failed')
def metadata():
    require(run('metadata-printing',['python3','scripts/check-card-printing.py','Reckless Bushwhacker'])==0,'printing gate failed')
    primary=read(P/'ogw-reckless-bushwhacker.json');url=primary['image_uris']['normal']
    with urllib.request.urlopen(urllib.request.Request(url,method='HEAD'),timeout=60) as response:
        require(response.status==200 and response.headers.get('Content-Type','').startswith('image/'),'image availability')
        js(E/'IMAGE_CHECK.json',dict(url=url,status=response.status,content_type=response.headers.get('Content-Type')))
    cli=str(R/'oracle-assay/build/install/oracle-assay/bin/oracle-assay')
    require(run('metadata-assay',[cli,'differential','--set','OGW','--top','10000'])==0,'Assay CLI failed')
    # Individual data-only views make every decline explicit, and retain the original old-card rows.
    snapshot=(R/'mtg-sets/src/test/resources/snapshots/cards/OGW.json').read_text()
    chunks=re.split(r'(?m)(?=^// )',snapshot);rows=[]
    old=git('show',pins['reviewed_source']+':mtg-sets/src/test/resources/snapshots/cards/OGW.json').decode()
    for i,chunk in enumerate(c for c in chunks if c.strip()):
        name=chunk.splitlines()[0][3:];new=name=='Reckless Bushwhacker'
        require(new or chunk in old,'old OGW entry changed')
        view=E/'assay-data'/str(i);save(view/'settings.gradle.kts',b'// data-only Assay view\n')
        save(view/'mtg-sets/src/test/resources/snapshots/cards/OGW.json',chunk.encode())
        label='assay-row-'+str(i)
        require(run(label,[cli,'differential','--set','OGW','--top','10000'],view)==0,'row undecodable '+name)
        text=(E/(label+'.stdout')).read_text()
        def count(label):
            m=re.search(r'^\s*'+re.escape(label)+r'\s+(\d+)',text,re.M);require(m is not None,'missing Assay counter '+label);return int(m[1])
        require(count('Hand-written cards')==1,'Assay row count')
        require(count('DIVERGENT — read every one')==0,'unclassified Assay divergence '+name)
        confirmed=count('Confirmed — models agree')==1
        declines=['not yet covered by the grammar','script slot not modelled yet','lines do not fold into one card','multi-face (out of scope)','no Scryfall Oracle entry','Oracle text differs from golden']
        categories=[d for d in declines if count(d)==1]
        require((confirmed and not categories) or (not confirmed and len(categories)==1),'unclassified Assay row '+name)
        rows.append(dict(card=name,new=new,classification='model agreement' if confirmed else categories[0],semantic_pass=confirmed,raw=label+'.stdout',original_entry_unchanged=not new))
    js(E/'ASSAY_CLASSIFICATION.json',dict(all_rows_classified=True,unexplained_drift=[],rows=rows,declines_are_not_semantic_passes=True))
try:
    E.mkdir(exist_ok=False)
    envelope=read(Path(os.environ['RUNNER_TEMP'])/'RECOVERY_EVENT.json');verify(envelope);bind()
    js(E/'RECOVERY_EVENT.json',envelope);js(E/'CANDIDATE_PINS.json',pins)
    for path in pins['bindings']:save(E/'frozen-source'/path,(R/path).read_bytes())
    for m in [None,*mutants]:
        form(m);label='normal' if m is None else m['id']
        require(gradle('compile-'+label,recipe('compile'))==0,'compile-first failure '+label)
    js(E/'COMPILE_FIRST.json',dict(forms=15,all_compiled=True))
    form();targeted('normal')
    for m in mutants:form(m);targeted(m['id'],m)
    form();targeted('restored');bind()
    require(gradle('cards',recipe('cards'),True)==0,'cards failed')
    require(gradle('ordinary',recipe('ordinary'),True)==0,'ordinary failed')
    require(run('client-install',['npm','ci'],R/'web-client')==0,'npm install failed')
    focused=recipe('client-focused');start=focused.index('--')+1;files=focused[start:];files=[x for x in files if not x.startswith('--reporter')]
    client('client-focused',files);client('client-full')
    require(run('client-typecheck',['npm','run','typecheck'],R/'web-client')==0,'typecheck failed')
    metadata();bind()
    js(E/'RESTORATION.json',dict(exact_normal_bytes=True,bindings=pins['bindings']))
    js(E/'AUDIT.json',audit_all(E));js(E/'RESULT.json',dict(state='ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED',admitted=False))
except Exception:
    if E.is_dir():save(E/'FAILURE.txt',traceback.format_exc().encode())
    raise
finally:
    for path,b in normal.items():save(R/path,b)
    if E.is_dir():
        actual={p:{'sha256':digest(R/p),'bytes':(R/p).stat().st_size} for p in pins['bindings']}
        js(E/'RESTORATION.json',dict(exact_normal_bytes=actual==pins['bindings'],bindings=actual))
        js(E/'MANIFEST.json',{p.relative_to(E).as_posix():digest(p) for p in sorted(E.rglob('*')) if p.is_file() and p!=E/'MANIFEST.json'})
