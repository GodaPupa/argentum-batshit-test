"""R2 only corrects the R1 guard's classification of archived source paths."""
from pathlib import Path
import hashlib, json, os, subprocess

R1_SOURCE='8df096e2072947f5c4cf04ea47a1871dd836bd10'
R1_ROOT='experiments/manual-transmission/research/pt02-control-closure-r1-20261023'
R2_ROOT='experiments/manual-transmission/research/pt02-control-closure-r2-20261023'
R2_FILES={R2_ROOT+'/gate.py',R2_ROOT+'/AMENDMENT.json'}
ORIGINAL_SHA='42d9361794f3480fe0d53ae2d1b6e16151b3361a17739be13447846d330713e5'
ARCHIVE='experiments/manual-transmission/research/paused-token-transaction-draft-r1-20261023/'
body=Path(R1_ROOT+'/gate.py').read_bytes()
assert hashlib.sha256(body).hexdigest()==ORIGINAL_SHA

def classify_production(all_changed, restored):
    """Reject any unexpected live file; distinguish the seven exact historical archive copies."""
    possible={p for p in all_changed if '/src/main/' in p}
    archived={p for p in possible if p.startswith(ARCHIVE)}
    expected_archived={ARCHIVE+p for p in restored if '/src/main/' in p and
        not p.endswith('/ObjectReferencePropagation.kt') and
        not p.endswith('/SubmitDecisionHandler.kt')}
    assert archived==expected_archived and len(archived)==7, sorted(archived)
    live=possible-archived
    assert live=={p for p in restored if '/src/main/' in p} and len(live)==9, sorted(live)
    return live

text=body.decode()
old="production={p for p in git('diff','--name-only',BASE,source).splitlines() if '/src/main/' in p}"
new="production=classify_production(git('diff','--name-only',BASE,source).splitlines(),restore)"
assert text.count(old)==1
text=text.replace(old,new,1)
old_allowed="allowed={TEST,str(ROOT/'PROTOCOL.json'),str(ROOT/'gate.py'),str(ROOT/'validation.just')}"
assert text.count(old_allowed)==1
text=text.replace(old_allowed,old_allowed+' | R2_FILES',1)
assert text.count("assert production=={p for p in restore if '/src/main/' in p} and len(production)==9")==1
compile(text,'<R2-classified-R1-gate>','exec')

def verify_wrapper():
    git=lambda *a:subprocess.check_output(['git',*a],text=True).strip()
    source=os.environ['SOURCE']
    assert git('merge-base',R1_SOURCE,source)==R1_SOURCE
    assert set(git('diff','--name-only',R1_SOURCE,source).splitlines())==R2_FILES
    assert os.environ['R2_RECORD']==R2_ROOT
    amendment=json.loads(Path(R2_ROOT+'/AMENDMENT.json').read_text())
    assert amendment['prior_failed_run']==39998698512
    assert amendment['prior_artifact_sha256']=='c2eed103080ec80c1dbb04aef4625089e752d99f58af429f88e16f1f2122c901'
    assert amendment['original_gate_sha256']==ORIGINAL_SHA
    assert amendment['test_expectations_changed'] is False
    restore=json.loads(Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023/PROTOCOL.json').read_text())['baseline_restore_paths']+['rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/decision/SubmitDecisionHandler.kt']
    actual=git('diff','--name-only','418cdfdacd5191549e9fc8e4a071330120b071b7',source).splitlines()
    live=classify_production(actual,restore)
    # A new live source file must still be rejected, not hidden by the archive correction.
    try:classify_production(actual+['mtg-sdk/src/main/unexpected-control.kt'],restore)
    except AssertionError:pass
    else:raise AssertionError('Classifier accepted an unexpected live production path')
    out=Path('manual-pt02-closure-evidence/protocol');out.mkdir(parents=True,exist_ok=True)
    (out/'R2_EFFECTIVE_GATE.py').write_text(text)
    report=dict(status='EXACT_NINE_LIVE_SEVEN_ARCHIVED_PATHS_VERIFIED',r1_source=R1_SOURCE,
        source=source,live_production_paths=sorted(live),archived_source_paths=sorted(p for p in actual if p.startswith(ARCHIVE) and '/src/main/' in p),
        original_gate_sha256=ORIGINAL_SHA,effective_gate_sha256=hashlib.sha256(text.encode()).hexdigest(),
        unexpected_live_file_rejected=True,test_expectations_changed=False,prior_run_disposition='FAILED_BEFORE_TEST_EXECUTION')
    (out/'R2_CLASSIFICATION.json').write_text(json.dumps(report,indent=2)+'\n')

if __name__=='__main__':
    verify_wrapper()
    exec(compile(text,'<R2-classified-R1-gate>','exec'),globals())
