"""Prepared same-source follow-up. Dispatch only after original early evidence is accepted."""
import base64,gzip,hashlib,json,os,shutil,subprocess
from pathlib import Path
from receive_calibrated import receive
ROOT=Path.cwd();D=Path(__file__).resolve().parent;OUT=ROOT/'manual-fixed-recipient-generator-calibration-r1-evidence'
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
    subprocess.run(['curl','-fLsS','--max-time','120',P['corpus']['url'],'-o',str(corpus)],check=True)
    assert sha(corpus.read_bytes())==P['corpus']['sha256'] and corpus.stat().st_size==P['corpus']['bytes']
    save(OUT/'inputs/mtgish.lines.json',corpus.read_bytes());save(OUT/'inputs/RAW_RESPONSES.json.gz',packed)
def bind():
    assert git('rev-parse','HEAD^')==P['source']
    assert git('rev-parse',P['source']+'^{tree}')==P['tree']
    assert git('diff','--name-only',P['source'],'HEAD')==os.environ['WORKFLOW']
    assert os.environ['GITHUB_RUN_ATTEMPT']==os.environ['GITHUB_RUN_NUMBER']=='1'
    for p,h in P['bindings'].items():
        b=(ROOT/p).read_bytes();assert sha(b)==h;save(OUT/'source'/p,b)
    assert not git('diff','--name-only'), 'unexpected tracked mutation'
    for path,blob in P['golden_blobs'].items():
        assert git('hash-object',path)==blob
        save(OUT/'source'/path,(ROOT/path).read_bytes())
    b=dict(source=P['source'],tree=P['tree'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=1,early_receipt=os.environ['EARLY_RECEIPT'])
    assert len(b['early_receipt'])==40
    save(OUT/'BINDING.json',(json.dumps(b,indent=2)+'\n').encode())
    for n in ['PROTOCOL.json','INPUTS.json','IDENTITY_FLOORS.json','run_calibrated.py','receive_calibrated.py','validation.just']:save(OUT/'protocol'/n,(D/n).read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
def command(folder,name,args):
    folder.mkdir(parents=True,exist_ok=True)
    with (folder/(name+'.log')).open('wb') as log:rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(D/'validation.just'),*args],stdout=log,stderr=subprocess.STDOUT).returncode
    (folder/(name+'-exit.txt')).write_text(str(rc)+'\n');return rc

def main():
    OUT.mkdir(exist_ok=True);bind();inputs()
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
    bind()
if __name__=='__main__':
    try:main()
    except Exception as e:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
    finally:
        if OUT.exists():
            manifest={str(p.relative_to(OUT)):sha(p.read_bytes()) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'}
            (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
