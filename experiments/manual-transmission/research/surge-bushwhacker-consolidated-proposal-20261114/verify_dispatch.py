"""Authenticate candidate bytes against the frozen grant before executing them."""
from pathlib import Path
import hashlib,json,os,subprocess,sys
P=Path(__file__).resolve().parent
ROOT=P.parents[3]
RECORD=P.relative_to(ROOT).as_posix()
def git(*a):return subprocess.check_output(['git',*a],cwd=ROOT).decode().strip()
def raw(ref,path):return subprocess.check_output(['git','show',ref+':'+path],cwd=ROOT)
def digest(b):return hashlib.sha256(b).hexdigest()
def check(ok,label):
    if not ok:raise ValueError(label)
def verify(envelope):
    source=envelope['source'];q=envelope['qualification'];grant=envelope['grant']
    proposal='888ad9f24b0a6f9dc84376f0204ee24bf9538838'
    check(grant['explicit_user_grant'] is True and grant['proposal_commit']==proposal,'explicit grant')
    check(grant['protocol_blob']=='a2c26c3b31834a7182bbe0b3a6ceb83922443cd4','protocol grant pin')
    check(git('rev-parse',source+'^{tree}')==q['source_tree'],'source tree')
    check(git('rev-parse',source+':'+RECORD+'/PROTOCOL.json')==grant['protocol_blob'],'source protocol')
    check(git('merge-base','--is-ancestor','b50fcd351d78a9375646b72e0ffea3ba77334fd6',source)=='','source lineage')
    proto=json.loads(raw(proposal,RECORD+'/PROTOCOL.json'))
    manifest=json.loads(raw(proposal,RECORD+'/MANIFEST.json'))
    for name,pin in manifest['files'].items():
        check(digest(raw(source,RECORD+'/'+name))==pin['sha256'],'immutable proposal changed '+name)
    allowed={x['path'] for x in proto['shipping_production_paths']+proto['documentation']+proto['test_paths']}
    allowed.add(proto['snapshot_path']);allowed.add('experiments/manual-transmission/autonomy/CURRENT.json')
    allowed.update(RECORD+'/'+n for n in proto['infrastructure_exact_files'])
    if q['candidate_index']=='02':
        amendment=current_authority=json.loads(raw('c0049aa6b4c44455e7764941f15a838338e3738b','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_candidate02_authorization']
        check(amendment['state']=='EXPLICIT_USER_GRANT' and amendment['local_failure_accepted_as_successor_prerequisite'] is True,'bounded local-failure successor authority')
        base='c0049aa6b4c44455e7764941f15a838338e3738b'
        check(git('show','-s','--format=%P',source)==base,'sole successor source parent')
        allowed=set(amendment['source_delta_paths'])|{RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json'}
        delta=set(git('diff','--name-only',base,source).splitlines())
        check(delta==allowed,'exact candidate02 source and binding delta')
    else:
        delta=set(git('diff','--name-only',proposal,source).splitlines())
    # The sole intervening R2 publication readback is already inside the proposal commit.
    check(delta<=allowed,'unlisted source changes '+str(delta-allowed))
    check(not any(p.startswith('.github/workflows/') for p in delta),'workflow in source rather than validation')
    docs=proto['documentation'][0]['path'];check(raw(source,docs).startswith(raw(proto['source_parent'],docs)),'documentation prefix')
    pins=json.loads(raw(source,RECORD+'/CANDIDATE_PINS.json'))
    check(pins['proposal_commit']==proposal and pins['candidate_index']==q['candidate_index'],'candidate pins identity')
    check(git('rev-parse',source+':'+RECORD+'/CANDIDATE_PINS.json')==q['candidate_pins_blob'],'candidate pins blob')
    for path,pin in pins['bindings'].items():
        b=(ROOT/path).read_bytes()
        check(digest(b)==pin['sha256'] and b==raw(source,path),'bound bytes '+path)
    protected=json.loads(raw(proposal,RECORD+'/PROTECTED_BINDINGS.json'))
    # Existing source, tests and goldens outside the exact allowlist cannot differ from reviewed base.
    reviewed=proto['source_parent']
    changes=set(git('diff','--name-only',reviewed,source).splitlines())
    exceptions={docs,proto['snapshot_path'],proto['shipping_production_paths'][1]['path']}
    if q['candidate_index']=='02':exceptions.add('rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/CostCalculator.kt')
    for path,pin in protected['bindings'].items():
        if path not in exceptions:check(raw(source,path)==raw(reviewed,path),'protected original '+path)
    current=json.loads(raw(envelope['first_parent'],'experiments/manual-transmission/autonomy/CURRENT.json'))
    check(current['owner'] is not None and current['lease']['owner']==current['owner'],'sole foreground lease')
    check(current['status']=='WORKING' and current['automation']['enabled_at_latest_readback'] is False,'execution state')
    check(current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0,'official counters')
    if q['candidate_index']=='02':
        check(current['surge_bushwhacker_candidate02_authorization']==current_authority,'successor grant changed')
        prior=current['surge_candidate01_host_recovery']
        check(prior['local_failure_evidence_commit']=='b9286f63c0c92f78604d1582904810f6229dbfc0','prior local failure')
        check(prior['local_readback']['commit']=='7da5143ba620c8258fda09b42bd1c618299e4453' and prior['local_readback']['state']=='PASS','prior exact readback')
        check(prior['source_unchanged'] is True and prior['compiled_forms_verified']==15,'prior restored compile evidence')
        check(prior['ci_activations']==prior['original_github_downloads']==0,'candidate01 never activated or received')
        check(pins['predecessor']['source']=='2b195520143045981fe4fdb64a16169473f0dbde','predecessor identity')
        check(pins['predecessor']['archive_sha256']==prior['local_archive']['sha256'],'predecessor archive pin')
    else:check(q['candidate_index']=='01','candidate cap')
    return pins
if __name__=='__main__':
    path=Path(sys.argv[1]) if len(sys.argv)>1 else Path(os.environ['RUNNER_TEMP'])/'RECOVERY_EVENT.json'
    envelope=json.loads(path.read_text());verify(envelope)
    print(json.dumps({'state':'SOURCE_BINDING_PASS','source':envelope['source'],'candidate_index':envelope['qualification']['candidate_index']}))
