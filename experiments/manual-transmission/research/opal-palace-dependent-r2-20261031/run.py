"""Compile and observe unchanged production; preserve original reports and bytes."""
from pathlib import Path
import os,json,subprocess,hashlib,re,traceback
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from audit import P,sha,inventory,junit,client
from report_audit import compile_gate,reports
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
    assert P['state']=='FROZEN_AFTER_ORIGINAL_EARLY_RECEIVING'
    receipt=(ROOT/P['early_receipt']).read_bytes();assert git('hash-object',P['early_receipt'])==P['early_receipt_blob']
    rd=json.loads(receipt);assert rd['state']=='ORIGINAL_EARLY_VERIFIED_DEPENDENT_GATES_PENDING' and rd['pins']['source']==P['early_source'] and rd['negative_witnesses']==18 and rd['negative_controls']==170
    save(OUT/'EARLY_RECEIPT.json',receipt)
    assert git('rev-parse','refs/remotes/origin/main')==P['main']
    assert not git('status','--porcelain','--untracked-files=no')
    trees={}
    for path in P['unchanged_trees']:
        before=git('rev-parse',P['early_source']+':'+path);after=git('rev-parse',source+':'+path)
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
def stage(name,recipe,kind,expected=None):
    folder=OUT/name;folder.mkdir(parents=True)
    for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
        if OUT not in f.parents:f.unlink()
    with (folder/'output.log').open('wb') as log:
        rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=subprocess.STDOUT,env={k:v for k,v in os.environ.items() if k not in ['KOTEST_INCLUDE_PATTERN','UPDATE_SNAPSHOTS']}).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n')
    for f in ROOT.glob('**/build/test-results/test/TEST-*.xml'):
        if OUT not in f.parents:save(folder/'junit'/f.relative_to(ROOT),f.read_bytes())
    result=compile_gate(folder) if kind=='compile' else reports(folder,expected) if kind=='focused' else junit(folder,expected)
    js(folder/'SUMMARY.json',result)
def client_stage():
    folder=OUT/'client';folder.mkdir();contract=json.loads((ROOT/P['client_contract']).read_text())
    commands={'npm-ci':['npm','ci'],'typecheck':['npm','run','typecheck'],'test':['npm','test','--','--run',*[p.removeprefix('web-client/') for p in contract['client_cases']],'--reporter=json','--outputFile='+str(folder/'vitest.json')]}
    for name,cmd in commands.items():
        with (folder/(name+'.log')).open('wb') as log:rc=subprocess.run(cmd,cwd=ROOT/'web-client',stdout=log,stderr=subprocess.STDOUT).returncode
        (folder/(name+'-exit.txt')).write_text(str(rc)+'\n');assert rc==0,name
    js(folder/'SUMMARY.json',client(folder,contract))
def assay_stage():
    folder=OUT/'assay-war';folder.mkdir()
    for recipe in ['corpus','assay-war']:
        with (folder/(recipe+'.stdout.log')).open('wb') as log,(folder/(recipe+'.stderr.log')).open('wb') as err:
            rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),recipe],stdout=log,stderr=err).returncode
        (folder/(recipe+'-exit.txt')).write_text(str(rc)+'\n');assert rc==0,recipe
    cache=Path.home()/'.cache/scryfall/_bulk-oracle-cards.jsonl.gz';save(folder/'oracle-cards.jsonl.gz',cache.read_bytes())
    with (folder/'assay-c13.stdout.log').open('wb') as log,(folder/'assay-c13.stderr.log').open('wb') as err:
        rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(HERE/'validation.just'),'assay-c13'],stdout=log,stderr=err).returncode
    (folder/'assay-c13-exit.txt').write_text(str(rc)+'\n');assert rc==0
    baseline=(HERE/'ORIGINAL_WAR_DIFFERENTIAL.txt').read_bytes();assert sha(baseline)==P['assay_baseline_sha256']
    assert (folder/'assay-war.stdout.log').read_bytes()==baseline,'Changed WAR differential requires explicit original classification, never normalization'
    js(folder/'SUMMARY.json',{'original_differential_preserved':True,'sha256':sha(baseline),'corpus_sha256':sha(cache.read_bytes()),'classified_bolt_bend_structural_divergences':1})
def main():
    OUT.mkdir();bind('initial');I=inventory(ROOT)
    assert sha(json.dumps(I,sort_keys=True,separators=(',',':')).encode())==P['inventory_sha256']
    js(OUT/'INVENTORY.json',I)
    save(OUT/'check_audit.json',subprocess.check_output(['python3',str(HERE/'check_audit.py')]))
    save(OUT/'check_differential.json',subprocess.check_output(['python3',str(HERE/'check_differential.py')]))
    reused=json.loads((HERE/'REUSED_GATES.json').read_text())
    prior=(ROOT/reused['receipt_path']).read_bytes();rd=json.loads(prior)
    assert rd['state']=='ORIGINAL_DEPENDENT_SNAPSHOT_FAILURE_VERIFIED' and rd['source']==reused['source']
    assert rd['compile_normal_passed'] and rd['focused_passed']==137 and rd['run']==41157568547
    save(OUT/'REUSED_DEPENDENT_RECEIPT.json',prior)
    js(OUT/'REUSED_GATES.json',reused)
    for name in ['cards','ordinary']:stage(name,name,'ordinary',I[name])
    client_stage();assay_stage();bind('final')
    js(OUT/'RESULT.json',{'state':'DEPENDENT_EXECUTION_COMPLETE_ORIGINAL_RECEIVING_PENDING','feature_admitted':False,'card_admitted':False})
if __name__=='__main__':
    try:main()
    except Exception:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(traceback.format_exc());raise
    finally:
        try:bind('final')
        finally:
            OUT.mkdir(exist_ok=True)
            js(OUT/'MANIFEST.json',{str(f.relative_to(OUT)):sha(f.read_bytes()) for f in sorted(OUT.rglob('*')) if f.is_file() and f.name!='MANIFEST.json'})
