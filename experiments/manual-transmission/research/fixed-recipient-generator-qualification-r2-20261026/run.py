"""Bounded R2 baseline repair and calibration; reuse received original R1 control evidence without relabeling its failure."""
import base64,gzip,hashlib,json,os,shutil,subprocess
from pathlib import Path
from receive_calibrated import receive
from reports import stage
ROOT=Path.cwd();D=Path(__file__).resolve().parent;OUT=ROOT/'manual-fixed-recipient-generator-r2-evidence'
sha=lambda b:hashlib.sha256(b).hexdigest()
P=json.loads((D/'PROTOCOL.json').read_text());I=json.loads((D/'INPUTS.json').read_text())
def git(*args):return subprocess.check_output(['git',*args]).decode().strip()
def save(p,b):p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b)
def inputs():
    packed=(D/'RAW_RESPONSES.json.gz').read_bytes();assert sha(packed)==I['raw_pack_sha256']
    raw=json.loads(gzip.decompress(packed));assert set(raw)==set(I['raw'])
    for n,body in raw.items():assert sha(body.encode())==I['raw'][n]['sha256']
    cache=Path.home()/'.cache/scryfall'
    for code in ['por','isd']:
        b=(D/'inputs'/(code+'.json')).read_bytes();assert sha(b)==I['sets'][code]['sha256']
        save(cache/(code+'.json'),b);save(OUT/'inputs'/(code+'.json'),b)
    corpus=ROOT/'mtgish-tooling/data/mtgish.lines.json';corpus.parent.mkdir(parents=True,exist_ok=True)
    with (OUT/'inputs/corpus-download.log').open('wb') as log:
        rc=subprocess.run(['curl','-fLsS','--max-time','120',P['corpus']['url'],'-o',str(corpus)],stdout=log,stderr=subprocess.STDOUT).returncode
    (OUT/'inputs/corpus-download-exit.txt').write_text(str(rc));assert rc==0
    assert sha(corpus.read_bytes())==P['corpus']['sha256'] and corpus.stat().st_size==P['corpus']['bytes']
    save(OUT/'inputs/mtgish.lines.json',corpus.read_bytes());save(OUT/'inputs/RAW_RESPONSES.json.gz',packed)
def bind():
    source=os.environ['SOURCE']
    assert git('rev-parse','HEAD^')==source
    assert git('rev-parse',source+'^')==P['base']
    assert sorted(git('diff','--name-only',P['base'],source).splitlines())==sorted(P['source_delta'])
    assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
    assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
    for p,h in {**P['protected'],**P['bindings']}.items():
        b=(ROOT/p).read_bytes();assert sha(b)==h;save(OUT/'source'/p,b)
    assert not git('diff','--name-only'), 'unexpected tracked mutation'
    for path,blob in P['golden_blobs'].items():
        assert git('hash-object',path)==blob
        save(OUT/'source'/path,(ROOT/path).read_bytes())
    b=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=1,early_receipt=P['inherited_early']['receipt_commit'],base=P['base'],source_delta=P['source_delta'])
    assert len(b['early_receipt'])==40
    assert P['bindings']==P['inherited_early']['required_unchanged_bindings']
    inherited=(D/'INHERITED_R1_RECEIPT.json').read_bytes()
    assert sha(inherited)==P['inherited_early']['receipt_sha256']
    proof=json.loads(inherited)
    assert proof['state']==P['inherited_early']['original_state']
    assert proof['source']==P['base'] and proof['run']==P['inherited_early']['run']
    assert proof['artifact_sha256']==P['inherited_early']['artifact_sha256']
    assert set(proof['gates'])==set(P['inherited_early']['stages'])
    assert {x['stage'] for x in proof['gate_errors']}=={'full-module','collector'}
    save(OUT/'INHERITED_R1_RECEIPT.json',inherited)
    save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
    for n in P['protocol_files']:save(OUT/'protocol'/n,(D/n).read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
def command(folder,name,args):
    folder.mkdir(parents=True,exist_ok=True)
    with (folder/(name+'.log')).open('wb') as log:rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(D/'validation.just'),*args],stdout=log,stderr=subprocess.STDOUT).returncode
    (folder/(name+'-exit.txt')).write_text(str(rc)+'\n');return rc

def main():
    OUT.mkdir(exist_ok=True);bind()
    # Preserve the existing clean-run baseline; never edit tests or hide a new skip.
    assert not (ROOT/'mtgish-tooling/data/mtgish.lines.json').exists()
    assert not (Path.home()/'.cache/scryfall/por.json').exists()
    folder=OUT/'full-module';folder.mkdir(exist_ok=True)
    junit=ROOT/'mtgish-tooling/build/test-results/test'
    if junit.exists():shutil.rmtree(junit)
    rc=command(folder,'module',['full-module'])
    save(folder/'output.log',(folder/'module.log').read_bytes())
    save(folder/'exit.txt',(folder/'module-exit.txt').read_bytes())
    if junit.exists():shutil.copytree(junit,folder/'junit',dirs_exist_ok=True)
    result=stage(folder);save(folder/'SUMMARY.json',(json.dumps(result,indent=2)+'\n').encode())
    inputs()
    assert command(OUT/'install','install',['install'])==0
    for code in ['por','isd']:
        folder=OUT/code;root=ROOT/'mtg-sets/build/generated-cards'
        if root.exists():shutil.rmtree(root)
        rc=command(folder,'compile',['compile',code.upper()])
        if root.exists():shutil.copytree(root,folder/'generated-all',dirs_exist_ok=True)
        assert rc==0
        save(folder/(code+'.generated.json'),(root/(code+'.generated.json')).read_bytes())
        shutil.copytree(root/'src',folder/'generated-src',dirs_exist_ok=True)
        save(folder/(code.upper()+'.golden.json'),(ROOT/('mtg-sets/src/test/resources/snapshots/cards/'+code.upper()+'.json')).read_bytes())
        command(folder,'fidelity',['fidelity',code.upper()])
        result=receive(folder,code);save(folder/'SUMMARY.json',(json.dumps(result,indent=2)+'\n').encode())
        for s in ['por','isd']:assert sha((Path.home()/'.cache/scryfall'/(s+'.json')).read_bytes())==I['sets'][s]['sha256']
    assert sha((ROOT/'mtgish-tooling/data/mtgish.lines.json').read_bytes())==P['corpus']['sha256']
    save(OUT/'FINAL_INPUTS.json',json.dumps(dict(corpus=P['corpus']['sha256'],caches={code:I['sets'][code]['sha256'] for code in ['por','isd']})).encode())
    bind()
if __name__=='__main__':
    try:main()
    except Exception as e:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
    finally:
        if OUT.exists():
            try:
                bind();save(OUT/'FINAL_SOURCE_BINDING.json',json.dumps(dict(unchanged=True,bindings=P['bindings'],protected=P['protected'])).encode())
            except Exception as e:save(OUT/'FINAL_SOURCE_FAILURE.txt',str(e).encode())
            manifest={str(p.relative_to(OUT)):sha(p.read_bytes()) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'}
            (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
