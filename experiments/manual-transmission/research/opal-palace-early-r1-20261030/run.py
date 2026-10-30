"""Prepared early qualification runner; requires an immutable finalized PROTOCOL before use."""
from pathlib import Path
import hashlib,json,os,re,subprocess,traceback
from audit import verify,verify_export
from mutants import forms
HERE=Path(__file__).resolve().parent;ROOT=Path.cwd()
P=json.loads((HERE/'PROTOCOL.json').read_text())
C=json.loads((HERE/'EARLY_CONTRACT.json').read_text())
OUT=ROOT/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *args:subprocess.check_output(['git',*args],text=True).strip()
def save(path,data):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
def js(path,value):save(path,(json.dumps(value,indent=2)+'\n').encode())
def identity():
    for f in HERE.iterdir():
        if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
    js(OUT/'BINDING.json',dict(source=os.environ['SOURCE'],tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER'])))
def bind(label):
    assert git('rev-parse','HEAD^')==os.environ['SOURCE']
    assert git('rev-parse',os.environ['SOURCE']+'^')==P['base']
    assert git('rev-parse',P['base']+'^{tree}')==P['base_tree']
    assert git('rev-parse',os.environ['SOURCE']+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('diff','--name-only',os.environ['SOURCE'],'HEAD')==os.environ['WORKFLOW']
    assert git('diff','--name-only',P['base'],os.environ['SOURCE']).splitlines()==sorted(P['source_delta'])
    assert git('rev-parse','refs/remotes/origin/main')==P['main']
    assert not git('status','--porcelain','--untracked-files=no')
    for path,digest in P['bindings'].items():
        b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
    for path,digest in P['git_bindings'].items():
        assert git('hash-object',path)==digest,path;save(OUT/label/'source'/path,(ROOT/path).read_bytes())
    for path,digest in P['original_bindings'].items():
        b=subprocess.check_output(['git','show',P['base']+':'+path]);assert sha(b)==digest,path
        save(OUT/label/'original'/path,b)
    trees={}
    for path in P['unchanged_modules']:
        before=git('rev-parse',P['base']+':'+path);assert before==git('rev-parse',os.environ['SOURCE']+':'+path),path
        trees[path]=before
    js(OUT/label/'UNCHANGED_TREES.json',trees)
    save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
def bind_form(stage,changes):
    expected=dict(P['bindings']);expected.update({p:sha(s.encode()) for p,s in changes.items()})
    observed={}
    for path,digest in expected.items():
        actual=sha((ROOT/path).read_bytes());assert actual==digest,path;observed[path]=actual
    for path,digest in P['git_bindings'].items():assert git('hash-object',path)==digest,path
    assert git('diff','--name-only').splitlines()==sorted(changes)
    js(OUT/stage/'SOURCE_HASHES.json',observed)
    save(OUT/stage/'SOURCE.diff',subprocess.check_output(['git','diff','--']))
def stage(name,recipe,changes,form='normal'):
    folder=OUT/name;folder.mkdir(parents=True)
    bind_form(name+'/before',changes)
    for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
        if OUT not in f.parents:f.unlink()
    env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}
    with (folder/'output.log').open('wb') as log:
        rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env=env).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n')
    for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
        if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
    bind_form(name+'/after',changes)
    raw={str(p.relative_to(folder)):p.read_bytes() for p in folder.rglob('*') if p.is_file()}
    if recipe=='compile':
        log=raw['output.log'].decode();assert rc==0 and 'BUILD SUCCESSFUL' in log
        assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
        assert not any(k.startswith('junit/') for k in raw)
    elif recipe=='snapshot-export':js(folder/'SUMMARY.json',verify_export(raw))
    else:js(folder/'SUMMARY.json',verify(raw,C,form))
def main():
    OUT.mkdir();identity();bind('initial')
    save(OUT/'AUDITOR_CONTROLS.json',subprocess.check_output(['python3',str(HERE/'check_audit.py')]))
    normal={p:(ROOT/p).read_text() for p in C['normal_hashes']}
    assert {p:sha(s.encode()) for p,s in normal.items()}==C['normal_hashes']
    mutants=forms(normal)
    assert {key:{p:sha(s.encode()) for p,s in changes.items()} for key,changes in mutants.items()}==C['forms']
    touched=set().union(*(set(changes) for changes in mutants.values()))
    for p in touched:save(OUT/'forms/normal'/p,normal[p].encode())
    for key,changes in mutants.items():
        for p,s in changes.items():save(OUT/'forms'/key/p,s.encode())
    def apply(changes):
        for p in touched:(ROOT/p).write_text(changes.get(p,normal[p]))
    try:
        stage('compile-normal','compile',{})
        for key,changes in mutants.items():
            apply(changes);stage('compile-'+key,'compile',changes)
        apply({});stage('normal','normal',{})
        for key,changes in mutants.items():
            apply(changes);stage('negative-'+key,key,changes,key)
        apply({});stage('compile-restored','compile',{});stage('restored','restored',{},'restored')
        stage('snapshot-export','snapshot-export',{})
        for name in ['OpalPalace.block.json','C13.candidate.json']:
            save(OUT/'snapshot'/name,(ROOT/'mtg-sets/build/opal-palace-candidate'/name).read_bytes())
    finally:
        apply({})
        for p in touched:assert sha((ROOT/p).read_bytes())==C['normal_hashes'][p]
    bind('final')
    js(OUT/'RESULT.json',dict(state='EARLY_EXECUTION_COMPLETE_ORIGINAL_RECEIVING_PENDING',feature_admitted=False,card_admitted=False,remaining_mandatory_gates=P['remaining_mandatory_gates']))
if __name__=='__main__':
    try:main()
    except Exception:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
    finally:
        try:bind('final')
        finally:
            OUT.mkdir(exist_ok=True)
            js(OUT/'MANIFEST.json',{str(p.relative_to(OUT)):sha(p.read_bytes()) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'})
