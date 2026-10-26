"""Locked bounded generator qualification; preserve all original failure evidence."""
import hashlib,json,os,shutil,subprocess
from pathlib import Path
from reports import P,stage
ROOT=Path.cwd();RECORD=Path(__file__).resolve().parent;OUT=ROOT/'manual-generator-calibration-repair-early-r1-evidence'
sha=lambda b:hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT).decode().strip()
def save(path,body):path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(body)
def original(path):return subprocess.check_output(['git','show',os.environ['SOURCE']+':'+path],cwd=ROOT)
def restore():
    for path in P['bindings']:save(ROOT/path,original(path))
def mutate(variant):
    if variant not in P['mutations']:return
    m=P['mutations'][variant];path=ROOT/m['path'];text=path.read_text()
    for r in m['replacements']:
        assert text.count(r['anchor'])==1;text=text.replace(r['anchor'],r['replacement'])
    path.write_text(text)
def capture(folder):
    for path in P['bindings']:save(folder/'source'/path,(ROOT/path).read_bytes())
    save(folder/'SOURCE.diff',subprocess.check_output(['git','diff','--',*P['bindings']],cwd=ROOT))
def execute(name,recipe):
    folder=OUT/name;folder.mkdir(parents=True,exist_ok=True);capture(folder)
    junit=ROOT/'mtgish-tooling/build/test-results/test'
    if junit.exists():shutil.rmtree(junit)
    projections=ROOT/'mtgish-tooling/build/calibration-projections'
    if projections.exists():shutil.rmtree(projections)
    with (folder/'output.log').open('wb') as log:
        rc=subprocess.run(['just','--working-directory',str(ROOT),'--justfile',str(RECORD/'validation.just'),recipe],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n')
    if junit.exists():shutil.copytree(junit,folder/'junit',dirs_exist_ok=True)
    result=stage(folder);
    projections=ROOT/'mtgish-tooling/build/calibration-projections'
    if not name.startswith('compile-'):
        assert sorted(p.stem for p in projections.glob('*.txt'))==P['projection_sets']
        shutil.copytree(projections,folder/'projections',dirs_exist_ok=True)
    (folder/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
def bind():
    source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('rev-parse',source+'^')==P['base']
    assert git('rev-parse','HEAD^')==source
    assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
    record=str(RECORD.relative_to(ROOT))
    delta=git('diff','--name-only',P['base'],source).splitlines()
    assert sorted(delta)==sorted(P['source_delta']),delta
    for path,digest in {**P['protected'],**P['bindings']}.items():
        data=(ROOT/path).read_bytes();assert sha(data)==digest,path;save(OUT/'source'/path,data)
    # All inherited fixtures and source bytes outside declared delta remain identical to the pinned draft base.
    assert not git('diff','--name-only',P['base'],source,'--','mtgish-tooling/src/test/resources/fixtures','mtg-sets/src/test/resources/snapshots')
    for file in P['protocol_files']:
        save(OUT/'protocol'/file,(RECORD/file).read_bytes())
    for code in P['projection_sets']:
        path='mtgish-tooling/src/test/resources/fixtures/'+code.lower()+'.emitted.golden.txt'
        save(OUT/'original-goldens'/(code+'.txt'),(ROOT/path).read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
    binding=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']),base=P['base'],source_delta=delta)
    assert binding['attempt']==binding['run_number']==1
    (OUT/'BINDING.json').write_text(json.dumps(binding,indent=2)+'\n')
def main():
    OUT.mkdir(exist_ok=True);bind()
    for variant in ['normal',*P['mutations']]:
        restore();mutate(variant);execute('compile-'+variant,'compile')
    for variant in ['normal',*P['mutations'],'restored']:
        restore();mutate(variant);execute(variant,'targeted')
    restore();bind()
if __name__=='__main__':
    try:main()
    except Exception as e:
        OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
    finally:
        try:
            restore();capture(OUT/'final-restored')
            for path,digest in {**P['protected'],**P['bindings']}.items():assert sha((ROOT/path).read_bytes())==digest,path
            (OUT/'RESTORATION.json').write_text(json.dumps(dict(restored=True,bindings=P['bindings'],protected=P['protected']),indent=2)+'\n')
        finally:
            if OUT.exists():
                manifest={str(p.relative_to(OUT)):sha(p.read_bytes()) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'}
                (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
