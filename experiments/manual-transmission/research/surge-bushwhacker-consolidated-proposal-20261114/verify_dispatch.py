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

# The old normalization proof remains bound to its original immutable source.
# This separate authority grants one prospective clean-room qualification.
NORMALIZED_SOURCE='3c7a9dd9a028b8644c92b67b3351ecf413eec484'
HISTORICAL_CHECKPOINT='62b95ee8f854cb9a29830832c287106ad8baaac4'
CLEAN_CHARTER_COMMIT='c943c4eb9859a59aa4f9c3c8ac492e6519ce7855'
CLEAN_NAMESPACE='experiments/manual-transmission/autonomy/clean-room-recovery-20261124'
CLEAN_CHARTER_PATH=CLEAN_NAMESPACE+'/CHARTER.json'
CLEAN_CHARTER_BLOB='944b7989a6e8a2d8551cc0a322ee7ce1b8eee709'
CLEAN_CHARTER_SHA256='0795eedb0206add384500f6555698a43dbca06f907a0e2ef7ebaeffe99c79915'
CLEAN_BRANCH='manual-transmission/clean-room-recovery-20261124'
CLEAN_EPOCH='manual-clean-room-20261124-01'
CLEAN_OWNER='manual-clean-room-foreground-20261124'

def verify_clean_room_authority(envelope):
    c=envelope['clean_room']
    check(c['recovery_id']==CLEAN_EPOCH and c['branch']==CLEAN_BRANCH,'clean-room identity')
    check(c['charter_commit']==CLEAN_CHARTER_COMMIT and c['charter_blob']==CLEAN_CHARTER_BLOB,'clean-room charter identity')
    check(c['attempt']==1 and envelope['attempt']==1,'clean-room attempt one only')
    check(c['lane'] in ('local','ci'),'clean-room execution lane')
    check(git('rev-parse',CLEAN_CHARTER_COMMIT+':'+CLEAN_CHARTER_PATH)==CLEAN_CHARTER_BLOB,'immutable clean-room charter blob')
    charter_bytes=raw(CLEAN_CHARTER_COMMIT,CLEAN_CHARTER_PATH)
    check(digest(charter_bytes)==CLEAN_CHARTER_SHA256,'immutable clean-room charter hash')
    charter=json.loads(charter_bytes)
    check(charter['authorization']['state']=='EXPLICIT_USER_GRANT','clean-room explicit authority')
    check(charter['source_basis']==NORMALIZED_SOURCE and charter['base_checkpoint']==HISTORICAL_CHECKPOINT,'clean-room source basis')
    check(charter['budgets']['fresh_complete_local_qualifications']==1 and not charter['budgets']['candidate03_allowed'],'clean-room finite allowance')
    first=envelope['first_parent']
    check(git('merge-base','--is-ancestor',CLEAN_CHARTER_COMMIT,first)=='','clean-room carrier lineage')
    check(raw(first,CLEAN_CHARTER_PATH)==charter_bytes,'clean-room carrier charter unchanged')
    historical_ledger='experiments/manual-transmission/autonomy/CURRENT.json'
    check(raw(first,historical_ledger)==raw(HISTORICAL_CHECKPOINT,historical_ledger),'historical ledger preserved')
    current=json.loads(raw(first,CLEAN_NAMESPACE+'/CURRENT.json'))
    check(current['recovery_id']==CLEAN_EPOCH and current['branch']==CLEAN_BRANCH,'clean-room ledger identity')
    check(current['charter']==CLEAN_CHARTER_PATH and current['charter_sha256']==CLEAN_CHARTER_SHA256,'clean-room ledger charter')
    check(current['owner']==CLEAN_OWNER and current['lease']['owner']==CLEAN_OWNER,'clean-room sole foreground owner')
    check(current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False,'clean-room foreground scope')
    check(current['lease']['writable_ref']=='refs/heads/'+CLEAN_BRANCH,'clean-room writable ref')
    check(current['status'] in ('QUALIFICATION_RESERVED','QUALIFICATION_RUNNING','CI_RESERVED'),'clean-room launch state')
    check(current['safeguards']==charter['safeguards'],'clean-room safeguards unchanged')
    check(current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0,'clean-room official counters')
    a=current['attempts']
    check(a['local_reserved']==a['local_started']==1,'clean-room local launch durably reserved once')
    if c['lane']=='local':
        check(c['attempt_id']==CLEAN_EPOCH+'/local-01' and a['local_completed']==a['ci_activated']==a['original_artifact_downloads']==0,'clean-room local attempt identity')
        check(current['status'] in ('QUALIFICATION_RESERVED','QUALIFICATION_RUNNING'),'clean-room local state')
    else:
        check(c['attempt_id']==CLEAN_EPOCH+'/ci-01' and a['ci_activated']==1 and a['original_artifact_downloads']==0,'clean-room CI attempt identity')
        check(current['status']=='CI_RESERVED' and a['local_completed']==1,'clean-room CI prerequisite state')
        receipt=current['local_result']
        check(receipt['state']=='ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED','clean-room CI all local gates prerequisite')
        check(receipt['source']==envelope['source'] and receipt['immutable_readback']=='PASS','clean-room CI exact local source readback')
        check(current['prior_candidate02_ci_activations']==0,'clean-room CI does not reset historical activations')
    q=envelope['qualification'];freeze=current['source_freeze']
    check(q==freeze,'clean-room exact prospective source freeze')
    check(q['candidate_index']=='02' and q['source']==envelope['source'],'clean-room Candidate02 source identity')
    check(q['source_basis']==NORMALIZED_SOURCE and q['binding_count']==376,'clean-room frozen basis and count')
    check(current['admitted'] is False,'clean-room pre-admission only')
    return current

def verify_m14_successor(source):
    names=['audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json']
    mechanical={RECORD+'/'+n for n in names}
    check(git('show','-s','--format=%P',source)==NORMALIZED_SOURCE,'M14 exact single normalized parent')
    check(set(git('diff','--name-only',NORMALIZED_SOURCE,source).splitlines())==mechanical,'M14 exact three mechanical source paths')
    previous=raw(NORMALIZED_SOURCE,RECORD+'/audit_reports.py')
    check(digest(previous)=='ef6e33a446f30bd8d4e64bd5649baba9bc4a0c0e96b9e91057ff22fe4dfe7e9d','M14 preserved normalized audit')
    check(previous.count(M14_OLD_VALUE_CHECKS)==1,'M14 exact replacement location')
    amended=previous.replace(M14_OLD_VALUE_CHECKS,M14_NEW_VALUE_CHECKS)
    check(raw(source,RECORD+'/audit_reports.py')==amended,'M14 only exact directed-label amendment; all other audit bytes preserved')
    old_pins=json.loads(raw(NORMALIZED_SOURCE,RECORD+'/CANDIDATE_PINS.json'))
    new_pins=json.loads(raw(source,RECORD+'/CANDIDATE_PINS.json'))
    check(set(new_pins)==set(old_pins)|{'clean_room_m14_syntax'},'M14 bounded pin metadata')
    check(all(new_pins[k]==v for k,v in old_pins.items() if k!='bindings'),'M14 historical pin metadata unchanged')
    check(set(new_pins['bindings'])==set(old_pins['bindings']) and len(new_pins['bindings'])==376,'M14 all 376 binding paths preserved')
    changed={p for p in old_pins['bindings'] if old_pins['bindings'][p]!=new_pins['bindings'][p]}
    check(changed=={RECORD+'/audit_reports.py',RECORD+'/verify_dispatch.py'},'M14 only audit and verifier binding values changed')
    for path in changed:
        content=raw(source,path)
        check(new_pins['bindings'][path]=={'sha256':digest(content),'bytes':len(content)},'M14 exact derived hash and byte length '+path)
    metadata=dict(recovery_id=CLEAN_EPOCH,branch=CLEAN_BRANCH,authorization_commit=CLEAN_CHARTER_COMMIT,
                  charter_blob=CLEAN_CHARTER_BLOB,charter_sha256=CLEAN_CHARTER_SHA256,prior_source=NORMALIZED_SOURCE,
                  prior_audit_sha256=digest(previous),audit_sha256=digest(amended),scope='M14-copy-choice exact directed labels only',
                  normal_expected='SELF_ALTERNATIVE',mutant_expected=None,M01_M13_unchanged=True,
                  all_semantic_inputs_unchanged=True,candidate03_prohibited=True)
    check(new_pins['clean_room_m14_syntax']==metadata,'M14 exact derived amendment metadata')
    return digest(amended)

M14_OLD_VALUE_CHECKS=b"    require(re.search(r'expected\\s*:?\\s*<?'+re.escape(expected)+r'>?',text,re.I) is not None,f'wrong expected value {directory}')\n    require(re.search(r'(?:but was|actual)\\s*:?\\s*<?'+re.escape(actual)+r'>?',text,re.I) is not None,f'wrong actual value {directory}')\n"
M14_NEW_VALUE_CHECKS=b"    if negative['id']=='M14-copy-choice':\n        require(expected=='SELF_ALTERNATIVE' and actual=='null','M14 frozen directed value contract')\n        value_pattern=(r'(?:org\\.opentest4j\\.AssertionFailedError:\\s*)?expected\\s*:?\\s*'\n                       r'(?:<SELF_ALTERNATIVE>|SELF_ALTERNATIVE)\\s+'\n                       r'(?:but\\s+was|(?:but\\s+)?actual(?:\\s+was)?)\\s*:?\\s*'\n                       r'(?:<null>|null)')\n        stack_lines=''.join(failure.itertext()).strip().splitlines()\n        require(bool(stack_lines),'M14 missing original assertion text')\n        value_lines=[failure.get('message','').strip(),stack_lines[0].strip()]\n        require(all(re.fullmatch(value_pattern,line,re.I) is not None for line in value_lines),\n                f'M14 wrong or ambiguous directed assertion values {directory}')\n    else:\n        require(re.search(r'expected\\s*:?\\s*<?'+re.escape(expected)+r'>?',text,re.I) is not None,f'wrong expected value {directory}')\n        require(re.search(r'(?:but was|actual)\\s*:?\\s*<?'+re.escape(actual)+r'>?',text,re.I) is not None,f'wrong actual value {directory}')\n"

def verify(envelope):
    source=envelope['source'];q=envelope['qualification'];grant=envelope['grant']
    clean_room='clean_room' in envelope
    if clean_room:verify_clean_room_authority(envelope)
    normalization_source=NORMALIZED_SOURCE if clean_room else source
    amended_audit_sha='ef6e33a446f30bd8d4e64bd5649baba9bc4a0c0e96b9e91057ff22fe4dfe7e9d'
    proposal='888ad9f24b0a6f9dc84376f0204ee24bf9538838'
    check(grant['explicit_user_grant'] is True and grant['proposal_commit']==proposal,'explicit grant')
    check(grant['protocol_blob']=='a2c26c3b31834a7182bbe0b3a6ceb83922443cd4','protocol grant pin')
    check(git('rev-parse',source+'^{tree}')==q['source_tree'],'source tree')
    check(git('rev-parse',source+':'+RECORD+'/PROTOCOL.json')==grant['protocol_blob'],'source protocol')
    check(git('merge-base','--is-ancestor','b50fcd351d78a9375646b72e0ffea3ba77334fd6',source)=='','source lineage')
    proto=json.loads(raw(proposal,RECORD+'/PROTOCOL.json'))
    manifest=json.loads(raw(proposal,RECORD+'/MANIFEST.json'))
    mapping_grant=None
    if q['candidate_index']=='02':
        mapping_grant=json.loads(raw('d40a8d79323de63de67b3dc190d3d69b98981c2b','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_m01_mapping_binding_exception']
        check(mapping_grant['explicit_user_grant'] is True and mapping_grant['prior_source']=='708c82cf2147526d7f50424f49887982293da296','M01 mapping authority')
        check(set(mapping_grant['mapping_pins'])=={'TEST_MATRIX.json','COMPILE_WITNESSES.json'},'exact mapping exception names')
        for name,mpin in mapping_grant['mapping_pins'].items():
            previous=raw(mapping_grant['prior_source'],RECORD+'/'+name)
            amended=raw(source,RECORD+'/'+name)
            check(digest(previous)==mpin['old_sha256'],'M01 predecessor bytes '+name)
            check(digest(amended)==mpin['new_sha256'],'M01 exact amended bytes '+name)
            expected=json.loads(previous);actual=json.loads(amended)
            check(len(expected['mutants'])==len(actual['mutants'])==14,'M01 mutant cardinality')
            check(expected['mutants'][0]['id']==actual['mutants'][0]['id']=='M01-surge-price','M01 identity')
            fields={'witness','positive_control','assertion'} if name=='TEST_MATRIX.json' else {'witness','source_file','source_line','positive_control'}
            check(set(mpin['updates'])==fields,'M01 exact permitted fields '+name)
            expected['mutants'][0].update(mpin['updates'])
            check(actual==expected,'M01 only authorized parsed differences '+name)
    audit_grant=None
    if q['candidate_index']=='02':
        audit_grant=json.loads(raw('2d57983ddd0e78c1dc2e986d7df8f52ba83085b4','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_m01_audit_contract_authorization']
        check(audit_grant['explicit_user_grant'] is True and audit_grant['prior_source']=='640b5bf917c84e0acffb186333ebd3f4c71145cd','M01 audit authority')
        audit_path=RECORD+'/audit_reports.py'
        previous_audit=raw(audit_grant['prior_source'],audit_path)
        amended_audit=raw('942f9f37c14ecaf7cfd7a7609d3d69080392f2eb',audit_path)
        check(git('rev-parse',audit_grant['prior_source']+':'+audit_path)==audit_grant['existing_audit_blob']=='5eec3e737f7383099d15257bad36cbbde6c79876','M01 audit predecessor blob')
        check(digest(previous_audit)=='b094156d4b13e0eb2f2b06f0d52623fe4e79704791c74679cdc3ec61531da1cd','M01 audit predecessor hash')
        check(digest(amended_audit)=='6dd9feb17de5d9a8fcd406303b3bdab8b6550101f2cd6993b295591010e5838f','M01 exact amended audit hash')
        start=b'# BEGIN EXACT M01 AUDIT EXCEPTION\n';end=b'# END EXACT M01 AUDIT EXCEPTION\n'
        check(amended_audit.count(start)==amended_audit.count(end)==1,'M01 audit bounded helper')
        before,rest=amended_audit.split(start);helper,after=rest.split(end)
        check(digest(start+helper+end)=='bae703f765282c9d7819195c69e4d45ed7618c68fd82ec365f374cb24d9b7b23','M01 exact helper bytes')
        dispatch=b"    if negative is not None and negative.get('id')=='M01-surge-price':return check_m01_rows(directory,expected,skips,negative)\n"
        check(after.count(dispatch)==1 and before+after.replace(dispatch,b'')==previous_audit,'M01 generic audit bytes unchanged')
    normalization_grant=None
    if q['candidate_index']=='02':
        normalization_grant=json.loads(raw('a30dd5f35f078b8ccd68a2619b93617a907d0907','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_m02_m14_report_normalization_authorization']
        check(normalization_grant['explicit_user_grant'] is True and normalization_grant['prior_source']=='942f9f37c14ecaf7cfd7a7609d3d69080392f2eb','M02–M14 normalization authority')
        check(git('rev-parse',normalization_grant['prior_source']+':'+audit_path)==normalization_grant['prior_audit_blob']=='b37acdf87b701aee1a817f562b2040b259b9bcdb','preserved M01 audit lineage')
        normalized=raw(normalization_source,audit_path)
        check(digest(normalized)=='ef6e33a446f30bd8d4e64bd5649baba9bc4a0c0e96b9e91057ff22fe4dfe7e9d','exact normalized audit hash')
        start=b'# BEGIN EXACT M02 M14 TARGETED REPORT NORMALIZATION\n';end=b'# END EXACT M02 M14 TARGETED REPORT NORMALIZATION\n'
        check(normalized.count(start)==normalized.count(end)==1,'bounded M02–M14 helper')
        before,rest=normalized.split(start);helper,after=rest.split(end)
        check(digest(start+helper+end)=='7237e694c40a4762aff4c5761c3b9346df496fb5dff747409ec00ae286c5ef3c','exact M02–M14 helper bytes')
        dispatch=b"    if negative is not None and negative.get('id') in tuple(m['id'] for m in read(P/'COMPILE_WITNESSES.json')['mutants'][1:]):return check_targeted_class_rows(directory,expected,skips,negative)\n"
        check(after.count(dispatch)==1 and before+after.replace(dispatch,b'')==amended_audit,'M01 and generic audit byte-semantics unchanged')
        mechanical={RECORD+'/'+n for n in ['audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json']}
        check(set(normalization_grant['authorized_source_paths'])==mechanical,'exact normalization paths authority')
        check(set(git('diff','--name-only',normalization_grant['prior_source'],normalization_source).splitlines())==mechanical,'exact three normalization paths; all other source frozen')
        old_pins=json.loads(raw(normalization_grant['prior_source'],RECORD+'/CANDIDATE_PINS.json'))
        new_pins=json.loads(raw(normalization_source,RECORD+'/CANDIDATE_PINS.json'))
        check(set(new_pins)==set(old_pins)|{'targeted_report_normalization'},'mechanical successor pin metadata')
        check(all(new_pins[k]==v for k,v in old_pins.items() if k!='bindings'),'unrelated pin metadata unchanged')
        check(set(new_pins['bindings'])==set(old_pins['bindings']),'exact binding path set unchanged')
        check({path for path in old_pins['bindings'] if old_pins['bindings'][path]!=new_pins['bindings'][path]}=={audit_path,RECORD+'/verify_dispatch.py'},'only audit/verifier pins changed')
    if clean_room:amended_audit_sha=verify_m14_successor(source)
    for name,pin in manifest['files'].items():
        check(digest(raw(proposal,RECORD+'/'+name))==pin['sha256'],'immutable proposal history '+name)
        if audit_grant is not None and name=='audit_reports.py':
            check(digest(raw(source,RECORD+'/'+name))==amended_audit_sha,'amended audit proposal binding')
        elif mapping_grant is not None and name in mapping_grant['mapping_pins']:
            check(digest(raw(source,RECORD+'/'+name))==mapping_grant['mapping_pins'][name]['new_sha256'],'amended proposal binding '+name)
        else:check(digest(raw(source,RECORD+'/'+name))==pin['sha256'],'immutable proposal changed '+name)
    allowed={x['path'] for x in proto['shipping_production_paths']+proto['documentation']+proto['test_paths']}
    allowed.add(proto['snapshot_path']);allowed.add('experiments/manual-transmission/autonomy/CURRENT.json')
    allowed.update(RECORD+'/'+n for n in proto['infrastructure_exact_files'])
    if q['candidate_index']=='02':
        amendment=current_authority=json.loads(raw('c0049aa6b4c44455e7764941f15a838338e3738b','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_candidate02_authorization']
        check(amendment['state']=='EXPLICIT_USER_GRANT' and amendment['local_failure_accepted_as_successor_prerequisite'] is True,'bounded local-failure successor authority')
        fixture_grant=json.loads(raw('a6fe1e604ed884413a147fba69653bc87a803c4b','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_candidate02_fixture_exception']
        check(fixture_grant['state']=='EXPLICIT_SUPERSEDING_USER_GRANT','fixture-only user authority')
        base='e24f71840d0144a781032b1c37ba988f1b6ca9d9'
        prior_source=fixture_grant['base_candidate02']['source']
        check(prior_source=='c4fc84437dd53a8d22d5f14e657178d0b492c7b0','prior candidate02 source')
        check(git('merge-base','--is-ancestor',base,source)=='','fixture successor source lineage')
        fixture_path='mtg-sets/2008-2016/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/RecklessBushwhackerScenarioTest.kt'
        check(fixture_grant['source_delta_paths']==[fixture_path],'one fixture path authority')
        allowed={fixture_path,RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json',RECORD+'/TEST_MATRIX.json',RECORD+'/COMPILE_WITNESSES.json'}
        allowed.add(RECORD+'/audit_reports.py')
        check(allowed==set(mapping_grant['exact_delta_paths'])|{RECORD+'/audit_reports.py'},'exact authorized six paths')
        delta=set(git('diff','--name-only',base,source).splitlines())
        check(delta==allowed,'exact fixture and mechanical binding delta')
        second_grant=json.loads(raw('7cd66adaa102d3f6aca33bbf238d86493a0bce9a','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_second_fixture_binding_authorization']
        check(second_grant['explicit_user_grant'] is True and second_grant['exact_registration']=='cardRegistry.register(com.wingedsheep.mtg.sets.definitions.m10.cards.DoomBlade)','second fixture authority')
        predecessor='6161f59565d51b7aff933a8af9f3c07f00da7e65'
        previous_pins=json.loads(raw(predecessor,RECORD+'/CANDIDATE_PINS.json'))
        for path,pin in previous_pins['bindings'].items():
            check(digest(raw(base,path))==pin['sha256'],'prior candidate02 correction preserved '+path)
            if path not in allowed:check(raw(source,path)==raw(predecessor,path),'unchanged candidate02 source '+path)
        old_fixture=raw(predecessor,fixture_path)
        old_line=b'        cardRegistry.register(requireNotNull(cardRegistry.getCard("Doom Blade#M10-93")))\n'
        new_line=b'        cardRegistry.register(com.wingedsheep.mtg.sets.definitions.m10.cards.DoomBlade)\n'
        check(old_fixture.count(old_line)==1 and raw(source,fixture_path)==old_fixture.replace(old_line,new_line),'exact second fixture replacement; assertions unchanged')
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
    current=json.loads(raw(HISTORICAL_CHECKPOINT if clean_room else envelope['first_parent'],'experiments/manual-transmission/autonomy/CURRENT.json'))
    if not clean_room:
        check(current['owner'] is not None and current['lease']['owner']==current['owner'],'sole foreground lease')
        check(current['status']=='WORKING' and current['automation']['enabled_at_latest_readback'] is False,'execution state')
        check(current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0,'official counters')
    if q['candidate_index']=='02':
        check(current['surge_bushwhacker_candidate02_authorization']==current_authority,'successor grant changed')
        check(current['surge_bushwhacker_candidate02_fixture_exception']==fixture_grant,'fixture-only grant changed')
        check(current['surge_bushwhacker_second_fixture_binding_authorization']==second_grant,'second fixture grant changed')
        check(current['surge_bushwhacker_m01_mapping_binding_exception']==mapping_grant,'M01 mapping grant changed')
        check(current['surge_bushwhacker_m01_audit_contract_authorization']==audit_grant,'M01 audit grant changed')
        check(current['surge_bushwhacker_m02_m14_report_normalization_authorization']==normalization_grant,'M02–M14 normalization grant changed')
        fixture_prior=fixture_grant['base_candidate02']
        check(pins['fixture_exception']['authorization_commit']=='a6fe1e604ed884413a147fba69653bc87a803c4b','fixture authorization pin')
        check(pins['fixture_exception']['prior_candidate02']==fixture_prior,'prior candidate02 evidence pins')
        failure_path=RECORD+'/RECEIVED_CANDIDATE_02/LOCAL_QUALIFICATION_FAILURE_01/'
        previous_readback=json.loads(raw(fixture_prior['readback_commit'],failure_path+'READBACK_VERIFICATION.json'))
        check(previous_readback['state']=='PASS' and previous_readback['archive_sha256']==fixture_prior['archive_sha256'],'prior candidate02 exact readback')
        check(previous_readback['normal_pass']==65 and previous_readback['normal_fail']==2 and previous_readback['compile_forms']==15,'prior fixture-failure counts')
        check(previous_readback['source_restored_rebound'] and previous_readback['ci_activations']==previous_readback['new_github_downloads']==0,'prior candidate02 restored and undispatched')
        for name in ['ARCHIVE_PARTS.json','LOCAL_RECEIPT.json','DIAGNOSIS_AND_BOUNDARY.json','SOURCE_REBINDING.json']:
            check(raw(source,failure_path+name)==raw(fixture_prior['failure_evidence_commit'],failure_path+name),'preserved prior fixture failure '+name)
        check(raw(source,failure_path+'READBACK_VERIFICATION.json')==raw(fixture_prior['readback_commit'],failure_path+'READBACK_VERIFICATION.json'),'preserved prior fixture readback')
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
