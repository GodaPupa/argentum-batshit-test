"""Compile and observe unchanged production; preserve original reports and bytes."""
from pathlib import Path
import os,json,subprocess,hashlib,re,traceback
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from audit import P,verify
from mutants import forms
ROOT=Path.cwd();HERE=Path(__file__).resolve().parent;OUT=ROOT/P['evidence']
sha=lambda b:hashlib.sha256(b).hexdigest()
git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def js(p,v):save(p,(json.dumps(v,indent=2)+'\n').encode())
def bind(label):
    source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('rev-parse',source+'^')==P['base'] and git('rev-parse','HEAD^')==source
    assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
    assert git('diff','--name-only',P['base'],source).splitlines()==sorted(P['source_delta'])
    assert git('rev-parse','refs/remotes/origin/main')==P['main']
    assert not git('status','--porcelain','--untracked-files=no')
    trees={}
    for path in P['unchanged_trees']:
        before=git('rev-parse',P['base']+':'+path);after=git('rev-parse',source+':'+path)
        assert before==after,path;trees[path]=after
    js(OUT/label/'UNCHANGED_TREES.json',trees)
    for path,digest in P['bindings'].items():
        b=(ROOT/path).read_bytes();assert sha(b)==digest,path;save(OUT/label/'source'/path,b)
    for path,digest in P['git_bindings'].items():
        assert git('hash-object',path)==digest,path;save(OUT/label/'source'/path,(ROOT/path).read_bytes())
    save(OUT/label/'TRACKED.diff',subprocess.check_output(['git','diff','--']))
    if label=='initial':
        for f in HERE.iterdir():
            if f.is_file():save(OUT/'protocol'/f.name,f.read_bytes())
        save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
        b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']))
        assert b['attempt']==b['run_number']==1;js(OUT/'BINDING.json',b)
def stage(name,recipe,form="normal"):
    folder=OUT/name;folder.mkdir(parents=True)
    for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
        if OUT not in f.parents:f.unlink()
    with (folder/'output.log').open('wb') as log:
        rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n')
    for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
        if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
    if recipe=='compile':
        log=(folder/'output.log').read_text();assert rc==0 and 'BUILD SUCCESSFUL' in log
        assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
        assert not list((folder/'junit').rglob('TEST-*.xml'))
    else:js(folder/'SUMMARY.json',verify(folder,form))
def main():
    OUT.mkdir();bind('initial')
    receipt=json.loads((ROOT/P['reuse_receipt']/'RECEIPT.json').read_text());classification=json.loads((ROOT/P['reuse_receipt']/'CLASSIFICATION.json').read_text())
    assert receipt['run']==classification['run']==41035962802
    assert classification['compiled_forms']==9 and classification['normal_cases']==16 and classification['negative_witnesses']==36 and classification['negative_passing_controls']==76
    save(OUT/'REUSED_RECEIPT.json',(ROOT/P['reuse_receipt']/'RECEIPT.json').read_bytes())
    save(OUT/'REUSED_CLASSIFICATION.json',(ROOT/P['reuse_receipt']/'CLASSIFICATION.json').read_bytes())
    controls=subprocess.check_output(['python3',str(HERE/'check_audit.py')]);save(OUT/'AUDITOR_CONTROLS.json',controls)
    path=ROOT/P['cost_path'];normal=path.read_text();allforms={'normal':normal,**forms(normal)}
    assert sha(normal.encode())==P['forms']['normal_sha256']
    for name,content in allforms.items():
        if name!='normal':assert sha(content.encode())==P['forms']['forms'][name]['sha256']
        save(OUT/'forms'/(name+'.kt'),content.encode())
    try:
        path.write_text(allforms['base-type']);stage('compile-base-type','compile')
        stage('negative-base-type','tests','base-type')
        path.write_text(normal);stage('compile-restored','compile');stage('restored','tests','restored')
    finally:
        path.write_text(normal)
        assert sha(path.read_bytes())==P['forms']['normal_sha256']
    bind('final')
    js(OUT/'RESULT.json',{'state':'MISSING_EARLY_GATES_COMPLETE_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False,'remaining_mandatory_gates':P['remaining_mandatory_gates']})
if __name__=='__main__':
    try:main()
    except Exception:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
    finally:
        try:bind('final')
        finally:
            OUT.mkdir(exist_ok=True)
            js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
