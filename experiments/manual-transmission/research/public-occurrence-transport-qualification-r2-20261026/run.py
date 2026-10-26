"""Locked early transport qualification; always archive failure evidence."""
import hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
from reports import P,stage
ROOT=Path.cwd(); RECORD=Path(__file__).resolve().parent
OUT=ROOT/'manual-public-occurrence-transport-r2-evidence'
sha=lambda b:hashlib.sha256(b).hexdigest()
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT).decode().strip()
def save(path,body):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(body)
def original(path):return subprocess.check_output(['git','show',os.environ['SOURCE']+':'+path],cwd=ROOT)
def restore():
    for path in P['bindings']:save(ROOT/path,original(path))
def mutate(variant):
    if variant=='normal':return
    path=next(p for p in P['bindings'] if p.endswith('PendingDecisionRouting.kt' if variant=='server-mutant' else 'gameplaySlice.ts'))
    text=(ROOT/path).read_text()
    if variant=='server-mutant':
        anchor='is ChooseOptionDecision -> copy(id = id)';replacement='is ChooseOptionDecision -> copy(id = id, optionCardIds = null)'
        assert text.count(anchor)==1;text=text.replace(anchor,replacement)
    else:
        start=text.index('submitOptionDecision: (decisionId, optionIndex) => {')
        end=text.index('submitReplacementDecision:',start)
        body=text[start:end]
        if variant=='client-index':anchor='        optionIndex,';replacement='        optionIndex: optionIndex - optionIndex,'
        else:
            assert variant=='client-stale'
            anchor='if (!pendingDecision || pendingDecision.id !== decisionId || !playerId) return';replacement='if (!pendingDecision || !playerId) return'
        assert body.count(anchor)==1;text=text[:start]+body.replace(anchor,replacement)+text[end:]
    (ROOT/path).write_text(text)
def capture(folder):
    for path in P['bindings']:save(folder/'source'/path,(ROOT/path).read_bytes())
    save(folder/'SOURCE.diff',subprocess.check_output(['git','diff','--',*P['bindings']],cwd=ROOT))
def execute(name,args,cwd=ROOT):
    folder=OUT/name;folder.mkdir(parents=True,exist_ok=True);capture(folder)
    junit=ROOT/'game-server/build/test-results/test'
    if junit.exists():shutil.rmtree(junit)
    with (folder/'output.log').open('wb') as log:
        rc=subprocess.run(args,cwd=cwd,stdout=log,stderr=subprocess.STDOUT).returncode
    (folder/'exit.txt').write_text(str(rc)+'\n')
    if junit.exists():shutil.copytree(junit,folder/'junit',dirs_exist_ok=True)
    result=stage(folder);(folder/'SUMMARY.json').write_text(json.dumps(result,indent=2)+'\n')
def bind():
    source=os.environ['SOURCE'];assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('rev-parse',source+'^')==P['base']
    assert git('rev-parse','HEAD^')==source
    assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
    for path,digest in {**P['protected'],**P['bindings']}.items():
        data=(ROOT/path).read_bytes();assert sha(data)==digest,path;save(OUT/'source'/path,data)
    for file in ['PROTOCOL.json','reports.py','run.py','check_reports.py']:
        save(OUT/'protocol'/file,(RECORD/file).read_bytes())
    save(OUT/'WORKFLOW.yml',(ROOT/os.environ['WORKFLOW']).read_bytes())
    binding=dict(source=source,tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),validation_tree=git('rev-parse','HEAD^{tree}'),workflow=os.environ['WORKFLOW'],workflow_blob=git('hash-object',os.environ['WORKFLOW']),run=os.environ['GITHUB_RUN_ID'],attempt=int(os.environ['GITHUB_RUN_ATTEMPT']),run_number=int(os.environ['GITHUB_RUN_NUMBER']),base=P['base'])
    assert binding['attempt']==binding['run_number']==1
    (OUT/'BINDING.json').write_text(json.dumps(binding,indent=2)+'\n')
    return binding
def main():
    OUT.mkdir(exist_ok=True);bind()
    with (OUT/'npm-ci.log').open('wb') as log:
        rc=subprocess.run(['npm','ci'],cwd=ROOT/'web-client',stdout=log,stderr=subprocess.STDOUT).returncode
    (OUT/'npm-ci-exit.txt').write_text(str(rc));assert rc==0
    recipe=OUT/'validation.just'
    recipe.write_text('compile-server:\n    scripts/gradle-locked :game-server:compileTestKotlin --console=plain\n')
    for variant in ['normal','server-mutant','client-index','client-stale']:
        restore();mutate(variant)
        if variant in ['normal','server-mutant']:
            execute('compile-server-'+variant,['just','--working-directory',str(ROOT),'--justfile',str(recipe),'compile-server'])
        if variant!='server-mutant':execute('compile-client-'+variant,['npm','run','typecheck'],ROOT/'web-client')
    for variant in ['normal','server-mutant','client-index','client-stale','restored']:
        restore();mutate('normal' if variant=='restored' else variant)
        if variant in ['normal','server-mutant','restored']:
            name='server-'+variant if variant!='server-mutant' else variant
            execute(name,['just','test-class','FixedRecipientChoiceTransportTest','--rerun-tasks','--console=plain'])
        if variant!='server-mutant':
            name='client-'+variant if variant in ['normal','restored'] else variant
            execute(name,['npm','test','--','src/store/slices/liveInteraction.test.ts','--reporter=json','--outputFile='+str(OUT/name/'vitest.json')],ROOT/'web-client')
    restore();bind()
if __name__=='__main__':
    failed=False
    try:main()
    except Exception as e:
        failed=True;OUT.mkdir(exist_ok=True);(OUT/'FAILURE.txt').write_text(type(e).__name__+': '+str(e));raise
    finally:
        try:restore()
        finally:
            if OUT.exists():
                manifest={str(p.relative_to(OUT)):sha(p.read_bytes()) for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='MANIFEST.json'}
                (OUT/'MANIFEST.json').write_text(json.dumps(manifest,indent=2)+'\n')
