from pathlib import Path
import json,os,subprocess,hashlib,shutil,time
from reports import stage
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent;OUT=ROOT/'manual-activation-payment-repair-r1-evidence'
P=json.loads((HERE/'PROTOCOL.json').read_text());sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
TARGET=ROOT/P['handler'];NORMAL=TARGET.read_bytes();FORMS={'normal':NORMAL}
for name,m in P['mutants'].items():
    b=NORMAL
    for c in m['changes']:
        assert b.count(c['old'].encode())==c['count'];b=b.replace(c['old'].encode(),c['new'].encode())
    assert sha(b)==m['sha256'];FORMS[name]=b

def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def bind(label):
    source=os.environ['SOURCE']
    assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('rev-parse',source+'^')==P['base'] and git('rev-parse','HEAD^')==source
    assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
    assert git('diff','--name-only',P['base'],source).splitlines()==sorted(P['source_delta'])
    assert git('rev-parse','refs/remotes/origin/main')==P['main']
    assert not git('status','--porcelain','--untracked-files=no')
    for p,h in {**P['bindings'],**P['protected']}.items():
        b=(ROOT/p).read_bytes();assert sha(b)==h,p;save(OUT/label/'source'/p,b)
    for p,h in P['blob_bindings'].items():
        assert git('hash-object',p)==h,p;save(OUT/label/'source'/p,(ROOT/p).read_bytes())
    save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
def run(name,recipe):
    folder=OUT/name;folder.mkdir()
    results=ROOT/'rules-engine/build/test-results/test'
    if results.exists():shutil.rmtree(results)
    started=time.time_ns()
    with (folder/'output.log').open('wb') as f:
        rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=f,stderr=subprocess.STDOUT).returncode
    (folder/'exit.txt').write_text(str(rc))
    (folder/'TIMING.json').write_text(json.dumps(dict(started_ns=started,ended_ns=time.time_ns())))
    if results.exists():shutil.copytree(results,folder/'junit')
    save(folder/'FORM.kt',TARGET.read_bytes())
    return stage(folder)
def main():
    OUT.mkdir(exist_ok=True);bind('initial')
    for f in HERE.iterdir():
        if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
    b=dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))
    assert b['attempt']==b['run_number']==1;save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
    subprocess.run(['python3',str(HERE/'check_controls.py')],check=True)
    gates={'compile-normal':run('compile-normal','compile')}
    for name in P['mutants']:
        TARGET.write_bytes(FORMS[name]);gates['compile-'+name]=run('compile-'+name,'compile')
    TARGET.write_bytes(NORMAL);bind('pre-behavior')
    gates['normal']=run('normal','targeted')
    for name in P['mutants']:
        TARGET.write_bytes(FORMS[name]);gates[name]=run(name,'controls')
    TARGET.write_bytes(NORMAL);gates['restored']=run('restored','targeted')
    (OUT/'EARLY.json').write_text(json.dumps(dict(state='EARLY_REPAIR_GATES_PASSED_NOT_FULL_ACCEPTANCE',gates=gates),indent=2)+'\n')
if __name__=='__main__':
    try:main()
    except Exception as e:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
    finally:
        TARGET.write_bytes(NORMAL)
        try:bind('final')
        finally:
            manifest={str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'}
            (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
