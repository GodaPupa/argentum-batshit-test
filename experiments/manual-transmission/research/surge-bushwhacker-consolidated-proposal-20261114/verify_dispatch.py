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

LINT_PREDECESSOR='2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
LINT_PREDECESSOR_TREE='632d9a579db3eea42a1bd1f2565685009627fdf5'
LINT_PREDECESSOR_PINS_BLOB='933c265ce8a6e8427148f422608b75e8246d7a51'
LINT_PREDECESSOR_AUDIT_SHA256='280a055eb671c18936bd2d4fd64dbe49dbdc68ae2a175a38d8567b612f92f275'
LINT_PREDECESSOR_VERIFIER_SHA256='963d5d9c0cdd49794b440d9084e0c14f32b652667ea650945d2bc65d48e4167f'
LINT_PREDECESSOR_PINS_SHA256='e810d5a7d68d6cfaaf05096c37ae25eed2b259f48278e39852971ed56d21e94a'
LINT_PREDECESSOR_PRODUCTION_SHA256='84b71a4ba586586ac1e1148355fcd1a2a6e7c296f0c1cd1c03ab3bc313bdc5b4'
LINT_CHECKPOINT='618f562154ff10a9da082a144d43c2011703d2e0'
LINT_PATCH_PATH='experiments/manual-transmission/autonomy/clean-room-recovery-20261124/SELF_ALTERNATIVE_LINT_PROPOSAL.diff'
LINT_PATCH_SHA256='2bab419c8cff73247321425ca56d1fc17a0e861cefbc859865423a62bc50ede3'
LINT_PROPOSAL_PATH='experiments/manual-transmission/autonomy/clean-room-recovery-20261124/LINT_FOLLOWUP_PROPOSAL.json'
LINT_PROPOSAL_SHA256='656f50f100f1eb25fb5dc38547edd4517e872b60341ae66519aece4306421d49'
LINT_PRODUCTION_PATH='mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/serialization/CardLinter.kt'
LINT_TEST_PATH='mtg-sdk/src/test/kotlin/com/wingedsheep/sdk/serialization/SelfAlternativeChoiceLintTest.kt'
LINT_NAMESPACE='experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124'
LINT_CHARTER_PATH='experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124/CHARTER.json'
LINT_CHARTER_COMMIT='86450c1ecb227804dbd45acf5ba28689ce0c61bc'
LINT_CHARTER_BLOB='418fb7f5a0ebeed92582f8285e76305c39250efa'
LINT_CHARTER_SHA256='a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e'
LINT_EPOCH='manual-lint-followup-20261124-01'
LINT_OWNER='manual-lint-foreground-20261124'
LINT_REGRESSION_IDENTITIES=('mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\taccepts a SELF_ALTERNATIVE read with an unconditional self-alternative cost', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\taccepts a SELF_ALTERNATIVE read with a conditional self-alternative cost', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\taccepts an ETB intervening-if SELF_ALTERNATIVE read with its declared cost', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\trejects a SELF_ALTERNATIVE read when the cost is omitted', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\trejects a SELF_ALTERNATIVE read when the cost is explicitly null', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\tdoes not declare KICKED from a self-alternative cost', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\tdoes not declare SELF_ALTERNATIVE from a kicker cost', 'mtg-sdk\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\tshares a front-face self-alternative declaration with a back-face reader')
LINT_ORDINARY_INVENTORY={'prior_total': 19421, 'prior_passed': 19378, 'total': 19429, 'passed': 19386, 'historical_skips': 43, 'added': 8, 'removed_or_renamed': 0}
LINT_AUDIT_INSERTION_POINT=b"    require(sum(cards.values())==401 and sum(ordinary.values())==19421,'compatibility inventory count drift')\n"
LINT_AUDIT_ADDITION=b"    # Exactly eight prospective ordinary regressions; historical identities/skips remain intact.\n    lint_names=(\n        'accepts a SELF_ALTERNATIVE read with an unconditional self-alternative cost',\n        'accepts a SELF_ALTERNATIVE read with a conditional self-alternative cost',\n        'accepts an ETB intervening-if SELF_ALTERNATIVE read with its declared cost',\n        'rejects a SELF_ALTERNATIVE read when the cost is omitted',\n        'rejects a SELF_ALTERNATIVE read when the cost is explicitly null',\n        'does not declare KICKED from a self-alternative cost',\n        'does not declare SELF_ALTERNATIVE from a kicker cost',\n        'shares a front-face self-alternative declaration with a back-face reader',\n    )\n    lint_rows=Counter({'mtg-sdk\\tcom.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest\\t'+name:1 for name in lint_names})\n    require(len(lint_names)==len(lint_rows)==8 and not (set(lint_rows)&set(ordinary)),'lint exact eight additive ordinary identities')\n    ordinary.update(lint_rows)\n    require(sum(ordinary.values())==19429 and sum(baseline['ordinary']['skips'].values())==43,'lint compatibility inventory count drift')\n"

# The lint followup has its own prospective authority. Its immutable predecessor
# still satisfies every historical proof below; none of those grants is reused
# as current launch authority, and actual bindings always name the new source.

def lint_proposal_contents():
    patch=raw(LINT_CHECKPOINT,LINT_PATCH_PATH)
    check(digest(patch)==LINT_PATCH_SHA256,'lint immutable prepared patch')
    proposal_bytes=raw(LINT_CHECKPOINT,LINT_PROPOSAL_PATH)
    check(digest(proposal_bytes)==LINT_PROPOSAL_SHA256,'lint immutable prepared proposal')
    proposal=json.loads(proposal_bytes)
    check(proposal['base_source']==LINT_PREDECESSOR and proposal['patch_sha256']==LINT_PATCH_SHA256,'lint prepared predecessor and patch')
    check(proposal['source_paths']==[LINT_PRODUCTION_PATH,LINT_TEST_PATH],'lint exact prepared source paths')
    check(proposal['regression_identities']==list(LINT_REGRESSION_IDENTITIES),'lint exact eight prepared identities')
    check(proposal['production_lines_added']==5 and proposal['new_regression_tests']==8,'lint prepared scope')
    # This is the frozen two-hunk proposal, not a general patch interpreter.
    production_header=('diff --git a/'+LINT_PRODUCTION_PATH+' b/'+LINT_PRODUCTION_PATH+'\n'
                       '--- a/'+LINT_PRODUCTION_PATH+'\n+++ b/'+LINT_PRODUCTION_PATH+'\n@@ -868,6 +868,11 @@\n').encode()
    test_header=('diff --git a/'+LINT_TEST_PATH+' b/'+LINT_TEST_PATH+'\nnew file mode 100644\n'
                 '--- /dev/null\n+++ b/'+LINT_TEST_PATH+'\n@@ -0,0 +1,144 @@\n').encode()
    check(patch.startswith(production_header) and patch.count(test_header)==1,'lint exact prepared hunk headers')
    production_hunk,test_hunk=patch[len(production_header):].split(test_header)
    lines=production_hunk.splitlines(keepends=True)
    check(len(lines)==11 and sum(line.startswith(b'+') for line in lines)==5,'lint exactly five production additions')
    check(all(line[:1] in (b' ',b'+') for line in lines),'lint no production deletion or other operation')
    old_lines=raw(LINT_PREDECESSOR,LINT_PRODUCTION_PATH).splitlines(keepends=True)
    check(digest(b''.join(old_lines))==LINT_PREDECESSOR_PRODUCTION_SHA256,'lint frozen original linter')
    check(old_lines[867:873]==[line[1:] for line in lines if line.startswith(b' ')],'lint exact production context')
    production=b''.join(old_lines[:867]+[line[1:] for line in lines]+old_lines[873:])
    test_lines=test_hunk.splitlines(keepends=True)
    check(len(test_lines)==144 and all(line.startswith(b'+') for line in test_lines),'lint exact new test file hunk')
    tests=b''.join(line[1:] for line in test_lines)
    check(git('ls-tree','-r','--name-only',LINT_PREDECESSOR,'--',LINT_TEST_PATH)=='','lint genuinely new regression path')
    return {LINT_PRODUCTION_PATH:production,LINT_TEST_PATH:tests}

def lint_followup_metadata(amended_audit):
    return dict(recovery_id=LINT_EPOCH,branch=CLEAN_BRANCH,
                authorization_commit=LINT_CHARTER_COMMIT,charter_blob=LINT_CHARTER_BLOB,
                charter_sha256=LINT_CHARTER_SHA256,prior_source=LINT_PREDECESSOR,
                prior_source_tree=LINT_PREDECESSOR_TREE,prior_pins_blob=LINT_PREDECESSOR_PINS_BLOB,
                proposal_commit=LINT_CHECKPOINT,proposal_sha256=LINT_PROPOSAL_SHA256,
                patch_path=LINT_PATCH_PATH,patch_sha256=LINT_PATCH_SHA256,
                source_paths=[LINT_PRODUCTION_PATH,LINT_TEST_PATH],
                regression_identities=list(LINT_REGRESSION_IDENTITIES),
                ordinary_inventory=LINT_ORDINARY_INVENTORY,
                prior_audit_sha256=LINT_PREDECESSOR_AUDIT_SHA256,audit_sha256=digest(amended_audit),
                predecessor_binding_count=376,binding_count=378,
                new_binding_paths=[LINT_PRODUCTION_PATH,LINT_TEST_PATH],
                all_original_binding_paths_preserved=True,existing_test_assertions_unchanged=True,
                existing_mutation_semantics_unchanged=True,all_other_source_paths_unchanged=True,
                candidate03_prohibited=True)

def verify_lint_successor(source):
    check(git('rev-parse',LINT_PREDECESSOR+'^{tree}')==LINT_PREDECESSOR_TREE,'lint immutable predecessor tree')
    check(git('rev-parse',LINT_PREDECESSOR+':'+RECORD+'/CANDIDATE_PINS.json')==LINT_PREDECESSOR_PINS_BLOB,'lint immutable predecessor pins blob')
    for name,expected in [('audit_reports.py',LINT_PREDECESSOR_AUDIT_SHA256),
                          ('verify_dispatch.py',LINT_PREDECESSOR_VERIFIER_SHA256),
                          ('CANDIDATE_PINS.json',LINT_PREDECESSOR_PINS_SHA256)]:
        check(digest(raw(LINT_PREDECESSOR,RECORD+'/'+name))==expected,'lint immutable predecessor '+name)
    check(git('show','-s','--format=%P',source)==LINT_PREDECESSOR,'lint exact sole predecessor parent')
    mechanical={RECORD+'/'+name for name in ['audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json']}
    permitted=mechanical|{LINT_PRODUCTION_PATH,LINT_TEST_PATH}
    check(set(git('diff','--name-only',LINT_PREDECESSOR,source).splitlines())==permitted,'lint exact five-file successor delta')
    for path in permitted:
        entry=git('ls-tree',source,'--',path).split('\t',1)
        check(len(entry)==2 and entry[0].startswith('100644 blob ') and entry[1]==path,'lint regular source file '+path)
    for path,expected in lint_proposal_contents().items():
        check(raw(source,path)==expected,'lint exact prepared source bytes '+path)
    previous=raw(LINT_PREDECESSOR,RECORD+'/audit_reports.py')
    check(previous.count(LINT_AUDIT_INSERTION_POINT)==1,'lint unique historical inventory insertion point')
    amended=previous.replace(LINT_AUDIT_INSERTION_POINT,LINT_AUDIT_INSERTION_POINT+LINT_AUDIT_ADDITION)
    check(raw(source,RECORD+'/audit_reports.py')==amended,'lint exactly eight additive identities; all other audit bytes unchanged')
    old_pins=json.loads(raw(LINT_PREDECESSOR,RECORD+'/CANDIDATE_PINS.json'))
    new_pins=json.loads(raw(source,RECORD+'/CANDIDATE_PINS.json'))
    check(set(new_pins)==set(old_pins)|{'lint_followup'},'lint exact additive pin metadata')
    check(all(new_pins[key]==value for key,value in old_pins.items() if key!='bindings'),'lint all historical pin metadata unchanged')
    additions={LINT_PRODUCTION_PATH,LINT_TEST_PATH}
    check(not additions.intersection(old_pins['bindings']),'lint only newly bound paths')
    check(len(old_pins['bindings'])==376 and len(new_pins['bindings'])==378,'lint exact source binding counts')
    check(set(new_pins['bindings'])==set(old_pins['bindings'])|additions,'lint preserves every original binding path')
    changed={path for path in old_pins['bindings'] if old_pins['bindings'][path]!=new_pins['bindings'][path]}
    check(changed=={RECORD+'/audit_reports.py',RECORD+'/verify_dispatch.py'},'lint only two existing mechanical bindings change')
    for path in changed|additions:
        content=raw(source,path)
        check(new_pins['bindings'][path]=={'sha256':digest(content),'bytes':len(content)},'lint exact derived source binding '+path)
    check(new_pins['lint_followup']==lint_followup_metadata(amended),'lint exact derived amendment metadata')

def verify_lint_preflight(current,first):
    from collections import Counter
    import base64,gzip,xml.etree.ElementTree as ET
    binding=current['preflight']
    directory=LINT_NAMESPACE+'/PREFLIGHT_01'
    check(binding['state']=='PASS','lint immutable preflight required')
    check(binding['receipt_path']==directory+'/PREFLIGHT_RECEIPT.json' and binding['manifest_path']==directory+'/MANIFEST.json','lint exact preflight evidence paths')
    commit=binding['receipt_commit']
    check(git('merge-base','--is-ancestor',LINT_CHARTER_COMMIT,commit)=='','lint preflight follows fresh authority')
    check(git('merge-base','--is-ancestor',commit,first)=='','lint preflight archive precedes full launch')
    check(git('diff','--name-only',commit,first,'--',directory)=='','lint original preflight evidence unchanged')
    receipt_bytes=raw(commit,binding['receipt_path']);manifest_bytes=raw(commit,binding['manifest_path'])
    check(digest(receipt_bytes)==binding['receipt_sha256'] and digest(manifest_bytes)==binding['manifest_sha256'],'lint exact immutable preflight receipt and manifest')
    receipt=json.loads(receipt_bytes);manifest=json.loads(manifest_bytes)
    check(manifest['schema']=='manual-transmission/preflight-files/v1','lint preflight manifest schema')
    files=manifest['files']
    check(all(name and not Path(name).is_absolute() and '..' not in Path(name).parts and Path(name).as_posix()==name and '\\' not in name for name in files),'lint safe preflight member paths')
    listed=subprocess.check_output(['git','ls-tree','-rz','--name-only',commit,'--',directory],cwd=ROOT).decode().split('\0')
    members={name[len(directory)+1:] for name in listed if name}
    check(members==set(files)|{'MANIFEST.json'} and 'MANIFEST.json' not in files,'lint complete noncircular preflight manifest')
    required={'PREFLIGHT_RECEIPT.json','PREFLIGHT_START.json','COMMANDS.json','source-before.json','source-after.json',
              'sdk.stdout','sdk.stderr','registry.stdout','registry.stderr','AUTHORITY_CURRENT.json','AUTHORITY_CHARTER.json',
              'run_lint_preflight.py','environment.py','EXPECTED_IDENTITIES.json','sdk-CHECK.json','registry-CHECK.json'}
    check(required<=set(files),'lint original preflight outputs present')
    contents={name:raw(commit,directory+'/'+name) for name in files}
    check(all(files[name]=={'sha256':digest(content),'bytes':len(content)} for name,content in contents.items()),'lint all original preflight member hashes and sizes')
    check(receipt['schema']=='manual-transmission/lint-followup-preflight/v1' and receipt['state']=='PASS','lint original preflight pass receipt')
    check(receipt['recovery_id']==LINT_EPOCH and receipt['attempt_id']==LINT_EPOCH+'/preflight-01','lint preflight identity')
    check(receipt['source_basis']==LINT_PREDECESSOR and receipt['prepared_patch_sha256']==LINT_PATCH_SHA256,'lint preflight exact prepared source')
    check(receipt['preflight_invocations']==1 and receipt['full_qualification_invocations']==0 and receipt['gradle_commands_started']==2,'lint preflight is separate and complete')
    check(receipt['source_exact_prepared_patch'] is True and receipt['source_unchanged'] is True and receipt['original_bindings_verified']==376,'lint preflight source checks')
    check(receipt['host_properties_restored'] is True and receipt['failure'] is None,'lint preflight terminal restoration attestation')
    check(receipt['admitted'] is False and receipt['ci_activations']==receipt['original_artifact_downloads']==receipt['phase2_games']==receipt['phase2_seeds']==0,'lint preflight cannot claim admission or CI')
    check(receipt['manifest_path']=='MANIFEST.json','lint preflight manifest reference')
    authority=receipt['authority_commit']
    check(git('merge-base','--is-ancestor',LINT_CHARTER_COMMIT,authority)=='','lint preflight reservation follows charter')
    check(git('merge-base','--is-ancestor',authority,commit)=='','lint preflight reservation precedes archive')
    check(raw(authority,LINT_NAMESPACE+'/CURRENT.json')==contents['AUTHORITY_CURRENT.json'],'lint exact preflight reservation bytes')
    check(raw(LINT_CHARTER_COMMIT,LINT_CHARTER_PATH)==contents['AUTHORITY_CHARTER.json'],'lint exact preflight charter bytes')
    reserved=json.loads(contents['AUTHORITY_CURRENT.json'])
    check(reserved['recovery_id']==LINT_EPOCH and reserved['branch']==CLEAN_BRANCH and reserved['status']=='LINT_PREFLIGHT_RESERVED','lint preflight reservation identity')
    check(reserved['source_basis']==LINT_PREDECESSOR and reserved['source_freeze'] is None,'lint preflight before source freeze')
    for name,value in dict(preflight_reserved=1,preflight_started=1,preflight_completed=0,local_reserved=0,local_started=0,local_completed=0,ci_activated=0,original_artifact_downloads=0).items():
        check(type(reserved['attempts'][name]) is int and reserved['attempts'][name]==value,'lint preflight reserved counter '+name)
    reservation=reserved['preflight_reservation']
    check(reservation['attempt_id']==LINT_EPOCH+'/preflight-01' and reservation['source_basis']==LINT_PREDECESSOR and reservation['prepared_patch_sha256']==LINT_PATCH_SHA256,'lint preflight reservation source')
    check(reservation['launcher_sha256']==digest(contents['run_lint_preflight.py']) and reservation['environment_helper_sha256']==digest(contents['environment.py']),'lint reserved preflight execution helpers')
    before_bytes=contents['source-before.json'];after_bytes=contents['source-after.json']
    check(before_bytes==after_bytes,'lint complete original preflight source restoration')
    check(digest(before_bytes)==receipt['source_before_manifest_sha256'] and digest(after_bytes)==receipt['source_after_manifest_sha256'],'lint preflight source snapshot hashes')
    before=json.loads(before_bytes)
    check(before['source_basis']==before['head']==LINT_PREDECESSOR and before['head_tree']==LINT_PREDECESSOR_TREE,'lint preflight immutable source base')
    patch_files={path:{'sha256':digest(content),'bytes':len(content)} for path,content in lint_proposal_contents().items()}
    expected_files=dict(json.loads(raw(LINT_PREDECESSOR,RECORD+'/CANDIDATE_PINS.json'))['bindings']);expected_files.update(patch_files)
    check(len(expected_files)==378 and before['files']==expected_files,'lint preflight all 376 original bindings plus exact two patch files')
    check(before['patch_files']==receipt['patch_files']==patch_files,'lint preflight exact prepared patch file bindings')
    check(before['status_porcelain_v1']==sorted([' M '+LINT_PRODUCTION_PATH,'?? '+LINT_TEST_PATH]) and before['untracked_paths']==[LINT_TEST_PATH],'lint preflight complete original source status')
    tracked=before['tracked_diff'].encode()
    check(digest(tracked)==before['tracked_diff_sha256'],'lint preflight complete tracked diff hash')
    patch=raw(LINT_CHECKPOINT,LINT_PATCH_PATH)
    production_patch=patch.split(('diff --git a/'+LINT_TEST_PATH+' b/'+LINT_TEST_PATH+'\n').encode())[0]
    tracked_lines=tracked.splitlines(keepends=True)
    check(len(tracked_lines)>2 and tracked_lines[1].startswith(b'index '),'lint preflight original Git diff index')
    hunk=b'@@ -868,6 +868,11 @@\n'
    check(production_patch.count(hunk)==1,'lint exact prepared production hunk')
    # Git emits this exact containing-object context; the reviewed patch omits it.
    expected_git_patch=production_patch.replace(hunk,b'@@ -868,6 +868,11 @@ object CardLinter {\n')
    check(tracked_lines[0]+b''.join(tracked_lines[2:])==expected_git_patch,'lint preflight only exact five-line tracked addition and containing-object context')
    old=raw(LINT_PREDECESSOR,LINT_PRODUCTION_PATH);new=lint_proposal_contents()[LINT_PRODUCTION_PATH]
    def blob_id(content):return hashlib.sha1(b'blob '+str(len(content)).encode()+b'\0'+content).hexdigest()
    index=tracked_lines[1].decode().strip().split(' ')
    check(len(index)==3 and index[0]=='index' and index[2]=='100644','lint preflight original regular-file diff mode')
    old_id,new_id=index[1].split('..')
    check(7<=len(old_id)<=40 and 7<=len(new_id)<=40 and blob_id(old).startswith(old_id) and blob_id(new).startswith(new_id),'lint preflight exact original Git blob IDs')
    baseline=json.loads(gzip.decompress(base64.b64decode(raw(LINT_PREDECESSOR,RECORD+'/ACCEPTED_INVENTORY.json.gz.b64'))))
    matrix=json.loads(raw(LINT_PREDECESSOR,RECORD+'/TEST_MATRIX.json'))
    registry=Counter(baseline['cards']['identities']);registry.update(row['identity'] for row in matrix['rows'] if row['family']=='registry')
    expected={'sdk':Counter(LINT_REGRESSION_IDENTITIES),'registry':registry}
    check(sum(registry.values())==401 and not baseline['cards']['skips'],'lint frozen preflight registry multiset')
    check(json.loads(contents['EXPECTED_IDENTITIES.json'])=={name:dict(rows) for name,rows in expected.items()},'lint exact archived preflight expectations')
    flags=['--no-build-cache','--no-daemon','--console=plain','--stacktrace','--max-workers=2','-PkotlinCompileParallelism=1']
    argv={'sdk':['scripts/gradle-locked',':mtg-sdk:cleanTest',':mtg-sdk:test','--tests','com.wingedsheep.sdk.serialization.SelfAlternativeChoiceLintTest']+flags,
          'registry':['scripts/gradle-locked',':mtg-sets:cleanTest',':mtg-sets:test']+flags}
    commands=json.loads(contents['COMMANDS.json'])
    check([entry['name'] for entry in commands]==['sdk','registry'],'lint exactly two preflight commands')
    for entry in commands:
        name=entry['name'];check(entry['argv']==argv[name] and entry['returncode']==0,'lint exact successful preflight command '+name)
    for name,wanted in expected.items():
        actual=[]
        reports=[member for member in files if member.startswith(name+'/') and Path(member).name.startswith('TEST-') and member.endswith('.xml')]
        check(bool(reports),'lint original preflight XML present '+name)
        for member in reports:
            relative=member[len(name)+1:]
            check('/build/test-results/' in relative,'lint preflight raw module layout')
            module=relative.split('/build/test-results/',1)[0]
            root=ET.fromstring(contents[member]);check(root.tag=='testsuite','lint original preflight XML suite')
            rows=list(root.iter('testcase'))
            check(int(root.get('tests','-1'))==len(rows),'lint original preflight XML row count')
            for attribute,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
                count=sum(row.find(tag) is not None for row in rows)
                check(int(root.get(attribute,'-1'))==count==0,'lint original preflight XML zero '+attribute)
            actual.extend('\t'.join([module,row.get('classname',''),row.get('name','')]) for row in rows)
        check(Counter(actual)==wanted,'lint exact original preflight identity multiplicity '+name)
        gate=receipt[name+'_gate']
        check(gate==json.loads(contents[name+'-CHECK.json']),'lint preserved original gate result '+name)
        check(gate['state']=='PASS' and gate['returncode']==0 and gate['exact_identity_multiplicities'] is True,'lint preflight exact successful gate '+name)
        check(gate['rows']==gate['passed']==sum(wanted.values()) and gate['failures']==gate['errors']==gate['skips']==0,'lint preflight exact gate counts '+name)
        check(gate['identities']==sorted(actual) and gate['raw_report_directory']==name,'lint preflight receipt matches original raw XML '+name)

def verify_lint_followup_authority(envelope):
    check('clean_room' not in envelope,'lint authority must not relabel the old recovery')
    c=envelope['lint_followup']
    check(c['recovery_id']==LINT_EPOCH and c['branch']==CLEAN_BRANCH,'lint followup identity')
    check(c['charter_commit']==LINT_CHARTER_COMMIT and c['charter_blob']==LINT_CHARTER_BLOB,'lint charter identity')
    check(type(c['attempt']) is int and c['attempt']==1 and type(envelope['attempt']) is int and envelope['attempt']==1,'lint single prospective attempt')
    check(c['lane'] in ('local','ci'),'lint execution lane')
    check(git('rev-parse',LINT_CHARTER_COMMIT+':'+LINT_CHARTER_PATH)==LINT_CHARTER_BLOB,'lint immutable charter blob')
    charter_bytes=raw(LINT_CHARTER_COMMIT,LINT_CHARTER_PATH)
    check(digest(charter_bytes)==LINT_CHARTER_SHA256,'lint immutable charter hash')
    charter=json.loads(charter_bytes)
    check(charter['authorization']['state']=='EXPLICIT_USER_GRANT','lint explicit fresh authority')
    check(charter['recovery_id']==LINT_EPOCH and charter['branch']==CLEAN_BRANCH,'lint actual charter recovery')
    check(charter['source_basis']==LINT_PREDECESSOR and charter['base_checkpoint']==LINT_CHECKPOINT,'lint frozen predecessor and checkpoint')
    check(charter['source_basis_tree']==LINT_PREDECESSOR_TREE,'lint frozen predecessor tree authority')
    permitted={RECORD+'/'+name for name in ['audit_reports.py','verify_dispatch.py','CANDIDATE_PINS.json']}|{LINT_PRODUCTION_PATH,LINT_TEST_PATH}
    check(set(charter['source_amendment']['paths'])==permitted,'lint exact five-path authority')
    amendment=charter['source_amendment']
    check(amendment['prepared_patch_commit']==LINT_CHECKPOINT and amendment['prepared_patch_path']==LINT_PATCH_PATH and amendment['prepared_patch_sha256']==LINT_PATCH_SHA256,'lint exact prepared patch authority')
    check(amendment['regression_identities']==list(LINT_REGRESSION_IDENTITIES),'lint exact eight-identity authority')
    budgets=charter['budgets']
    check(budgets['fresh_complete_local_qualifications']==1 and budgets['candidate03_allowed'] is False,'lint finite fresh allowance')
    check(budgets['precisely_authorized_lint_source_successors']==1,'lint one exact source successor')
    check(budgets['ci_activation_maximum']==budgets['ci_attempt']==budgets['original_artifact_receiving_maximum']==1,'lint bounded conditional CI')
    old_charter=json.loads(raw(CLEAN_CHARTER_COMMIT,CLEAN_CHARTER_PATH))
    required=dict(old_charter['required_gates'])
    required.update(source_bindings=378,ordinary_total=19429,ordinary_passed=19386)
    check(charter['required_gates']==required,'lint only additive inventory and binding gate changes')
    check(charter['safeguards']==old_charter['safeguards'],'lint all original safeguards preserved')
    first=envelope['first_parent']
    check(git('merge-base','--is-ancestor',LINT_CHECKPOINT,first)=='','lint preserved carrier checkpoint lineage')
    check(git('merge-base','--is-ancestor',LINT_CHARTER_COMMIT,first)=='','lint carrier charter lineage')
    check(git('merge-base','--is-ancestor',envelope['source'],first)=='','lint carrier contains the frozen source')
    check(raw(first,LINT_CHARTER_PATH)==charter_bytes,'lint current carrier charter unchanged')
    old_delta=git('diff','--name-only',LINT_CHECKPOINT,first,'--',CLEAN_NAMESPACE).splitlines()
    check(all(path.startswith(LINT_NAMESPACE+'/') for path in old_delta),'lint prior clean-room evidence and operational records unchanged')
    historical_ledger='experiments/manual-transmission/autonomy/CURRENT.json'
    check(raw(first,historical_ledger)==raw(HISTORICAL_CHECKPOINT,historical_ledger),'lint original historical ledger unchanged')
    previous=json.loads(raw(LINT_CHECKPOINT,CLEAN_NAMESPACE+'/CURRENT.json'))
    check(previous['status']=='LOCAL_FAILED_ARCHIVED_READBACK_VERIFIED' and previous['admitted'] is False,'lint preserved failed predecessor state')
    check(previous['attempts']==dict(local_reserved=1,local_started=1,local_completed=1,ci_activated=0,original_artifact_downloads=0),'lint predecessor attempt remains consumed and undispatched')
    prior_result=previous['local_result']
    check(prior_result['source']==LINT_PREDECESSOR and prior_result['state']=='FAILED_OR_INCOMPLETE_LOCAL_ORIGINAL_PRESERVED','lint original failed source result')
    check(prior_result['immutable_readback']=='PASS' and prior_result['qualification_pass'] is False,'lint preserved failure readback is not qualification success')
    current=json.loads(raw(first,LINT_NAMESPACE+'/CURRENT.json'))
    check(current['recovery_id']==LINT_EPOCH and current['branch']==CLEAN_BRANCH,'lint current ledger identity')
    check(current['charter']==LINT_CHARTER_PATH and current['charter_sha256']==LINT_CHARTER_SHA256,'lint current charter pin')
    check(current['source_basis']==LINT_PREDECESSOR and current['base_checkpoint']==LINT_CHECKPOINT and current['prior_failure']==charter['prior_failure'],'lint current immutable predecessor and failed evidence')
    check(current['owner']==LINT_OWNER and current['lease']['owner']==LINT_OWNER,'lint sole foreground owner')
    check(current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False,'lint foreground scope')
    check(current['lease']['writable_ref']=='refs/heads/'+CLEAN_BRANCH,'lint exclusive writable ref')
    check(current['status'] in ('QUALIFICATION_RESERVED','QUALIFICATION_RUNNING','CI_RESERVED'),'lint prospective launch state')
    check(current['safeguards']==charter['safeguards'],'lint current safeguards unchanged')
    check(current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0,'lint official counters remain zero')
    a=current['attempts']
    check(all(type(a[name]) is int and a[name]==1 for name in ['preflight_reserved','preflight_started','preflight_completed']),'lint successful preflight durably completed before full qualification')
    check(a['local_reserved']==a['local_started']==1,'lint separate fresh attempt durably consumed once')
    check(current['prior_candidate02_ci_activations']==0,'lint no historical CI allowance reset')
    verify_lint_preflight(current,first)
    if c['lane']=='local':
        check(envelope['local_execution'] is True and all(envelope[name] is None for name in ['event','validation','run']),'lint local metadata cannot claim a CI event')
        check(c['attempt_id']==LINT_EPOCH+'/local-01','lint local attempt identity')
        check(a['local_completed']==a['ci_activated']==a['original_artifact_downloads']==0,'lint local launch counts')
        check(current['status'] in ('QUALIFICATION_RESERVED','QUALIFICATION_RUNNING'),'lint local state')
    else:
        check(envelope.get('local_execution',False) is False and all(envelope.get(name) is not None for name in ['event','validation','run']),'lint CI metadata cannot claim local execution')
        check(c['attempt_id']==LINT_EPOCH+'/ci-01','lint CI attempt identity')
        check(a['local_completed']==a['ci_activated']==1 and a['original_artifact_downloads']==0,'lint CI one activation after local completion')
        check(current['status']=='CI_RESERVED','lint CI reserved state')
        receipt=current['local_result']
        check(receipt['state']=='ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED','lint CI requires every local gate')
        check(receipt['source']==envelope['source'] and receipt['immutable_readback']=='PASS','lint CI exact local source readback')
    q=envelope['qualification']
    check(q==current['source_freeze'],'lint exact prospective source freeze')
    check(q['candidate_index']=='02' and q['source']==envelope['source'],'lint Candidate02 source identity')
    check(q['source_basis']==LINT_PREDECESSOR and q['binding_count']==378,'lint frozen predecessor and 378 bindings')
    check(current['admitted'] is False,'lint pre-admission only')
    return current


# BEGIN EXACT HOST REQUALIFICATION AUTHORITY
# This is a new Candidate02 execution grant. The consumed local grant remains
# failed, and all historical proofs continue to read their immutable sources.
HOST_PREDECESSOR='82ec335573572edfd2a2e4d734d2f0d81801139f'
HOST_PREDECESSOR_TREE='923ec79e1c867eedf35b408153bf60988fbd26b6'
HOST_PREDECESSOR_PINS_BLOB='7e30df16c53a59358d507733b2a5443e2a3c9075'
HOST_PREDECESSOR_VERIFIER_SHA256='23ea2debb05adc95194f2fcc41fc6c84ad3070d76987f5b6b96af061d3ddabb7'
HOST_PREDECESSOR_PINS_SHA256='b0e8da912f98b75a35c44db4a3e9e3334d941af3891e0bb28281490ab01e7bbd'
HOST_AUDIT_SHA256='aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81'
HOST_CHECKPOINT='a6dd2a6d84a9b046a7f77ac426c5dcbe9bb47b76'
HOST_FAILED_READBACK='d53cbbd783d8252c03bd1747b031c791c0f45e07'
HOST_NAMESPACE=LINT_NAMESPACE+'/host-requalification-20261124'
HOST_CHARTER_PATH=HOST_NAMESPACE+'/CHARTER.json'
HOST_CHARTER_COMMIT='bd1110a0fafcfe4039a432a9ad9e955166dbc523'
HOST_CHARTER_BLOB='2b2a03f45c8d2b490720ce2c7ccb375b5157bcd5'
HOST_CHARTER_SHA256='5b4e30d8594b6c1e4a6d8f2ec2f4a0bdb0a6dba7e314e8deddffcad66b06f701'
HOST_EPOCH='manual-host-requalification-20261124-01'
HOST_OWNER='manual-host-requalification-foreground-20261124'
HOST_PLAN='B_HOSTED_ONCE'
HOST_WORKFLOW_PATH='.github/workflows/manual-host-requalification-surge-bushwhacker-20261124.yml'
HOST_READINESS_CONTRACT_PATH=HOST_NAMESPACE+'/READINESS_CONTRACT.json'
HOST_ATTEMPTS=dict(hosted_activations=1,readiness_reserved=1,readiness_started=0,
                   readiness_completed=0,qualification_reserved=1,qualification_started=0,
                   qualification_completed=0,original_artifact_downloads=0,
                   original_artifact_receives=0,local_started=0)
HOST_BUDGETS=dict(fresh_complete_local_qualifications=0,hosted_activation_maximum=1,
                  hosted_readiness_sequence_maximum=1,full_hosted_qualifications_maximum=1,
                  total_full_qualification_invocations_maximum=1,original_artifact_download_maximum=1,
                  original_artifact_receiving_maximum=1,precisely_authorized_source_successors=1,
                  candidate03_allowed=False,hosted_run_number=1,hosted_run_attempt=1,
                  readiness_failure_consumes_activation=True,qualification_starts_if_readiness_fails=0,
                  prior_unused_ci_allowance_is_additive=False,retry_after_failure_allowed=False)
HOST_Q_EXTENSIONS={'validation','validation_tree','workflow_path','workflow_blob','workflow_sha256',
                   'source_freeze_sha256','receiver_sha256','receiver_blob','readback_sha256','readback_blob',
                   'readiness_contract_sha256','readiness_helper_sha256','readiness_helper_blob'}
HOST_VERIFY_ROUTE_REPLACEMENTS=(
    ("    lint_followup='lint_followup' in envelope\n    if lint_followup:verify_lint_followup_authority(envelope)\n",
     "    lint_followup='lint_followup' in envelope\n    host_requalification='host_requalification' in envelope\n    if host_requalification:verify_host_requalification_authority(envelope)\n    elif lint_followup:verify_lint_followup_authority(envelope)\n"),
    ("    # Only historical proofs read S1; prospective authority and bindings read actual S2.\n    proof_source=LINT_PREDECESSOR if lint_followup else source\n    normalization_source=NORMALIZED_SOURCE if (clean_room or lint_followup) else source\n",
     "    # Historical proofs still read S1; actual source bindings read S2 or the authorized S3.\n    proof_source=LINT_PREDECESSOR if (lint_followup or host_requalification) else source\n    normalization_source=NORMALIZED_SOURCE if (clean_room or lint_followup or host_requalification) else source\n"),
    ("    if clean_room or lint_followup:amended_audit_sha=verify_m14_successor(proof_source)\n    if lint_followup:verify_lint_successor(source)\n",
     "    if clean_room or lint_followup or host_requalification:amended_audit_sha=verify_m14_successor(proof_source)\n    if lint_followup or host_requalification:verify_lint_successor(HOST_PREDECESSOR if host_requalification else source)\n    if host_requalification:verify_host_successor(source)\n"),
    ("    current=json.loads(raw(HISTORICAL_CHECKPOINT if (clean_room or lint_followup) else envelope['first_parent'],'experiments/manual-transmission/autonomy/CURRENT.json'))\n    if not (clean_room or lint_followup):\n",
     "    current=json.loads(raw(HISTORICAL_CHECKPOINT if (clean_room or lint_followup or host_requalification) else envelope['first_parent'],'experiments/manual-transmission/autonomy/CURRENT.json'))\n    if not (clean_room or lint_followup or host_requalification):\n"),
    ("    else:check(q['candidate_index']=='01','candidate cap')\n    return pins\n",
     "    else:check(q['candidate_index']=='01','candidate cap')\n    if host_requalification:verify_host_runtime_readiness(envelope)\n    return pins\n"),
)

def host_requalification_metadata():
    return dict(recovery_id=HOST_EPOCH,branch=CLEAN_BRANCH,selected_plan=HOST_PLAN,
                authorization_commit=HOST_CHARTER_COMMIT,charter_blob=HOST_CHARTER_BLOB,
                charter_sha256=HOST_CHARTER_SHA256,prior_source=HOST_PREDECESSOR,
                prior_source_tree=HOST_PREDECESSOR_TREE,prior_pins_blob=HOST_PREDECESSOR_PINS_BLOB,
                prior_verifier_sha256=HOST_PREDECESSOR_VERIFIER_SHA256,
                prior_pins_sha256=HOST_PREDECESSOR_PINS_SHA256,
                base_checkpoint=HOST_CHECKPOINT,failed_readback_checkpoint=HOST_FAILED_READBACK,
                source_paths=[RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json'],
                predecessor_binding_count=378,binding_count=378,
                only_changed_binding=RECORD+'/verify_dispatch.py',audit_sha256=HOST_AUDIT_SHA256,
                all_other_binding_values_unchanged=True,all_other_source_tree_entries_unchanged=True,
                historical_verifier_helpers_unchanged=True,all_semantic_gates_unchanged=True,
                execution_environment='github_hosted',local_pass=False,
                replaces_local_pass_before_ci=True,readiness_required_before_qualification=True,
                budgets=HOST_BUDGETS,candidate03_prohibited=True)

def verify_host_successor(source):
    check(git('rev-parse',HOST_PREDECESSOR+'^{tree}')==HOST_PREDECESSOR_TREE,'host immutable S2 tree')
    check(git('rev-parse',HOST_PREDECESSOR+':'+RECORD+'/CANDIDATE_PINS.json')==HOST_PREDECESSOR_PINS_BLOB,'host immutable S2 pins')
    previous_verifier=raw(HOST_PREDECESSOR,RECORD+'/verify_dispatch.py')
    previous_pins=raw(HOST_PREDECESSOR,RECORD+'/CANDIDATE_PINS.json')
    check(digest(previous_verifier)==HOST_PREDECESSOR_VERIFIER_SHA256,'host immutable S2 verifier')
    check(digest(previous_pins)==HOST_PREDECESSOR_PINS_SHA256,'host immutable S2 pins bytes')
    check(git('show','-s','--format=%P',source)==HOST_PREDECESSOR,'host exact sole S2 parent')
    permitted={RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json'}
    check(set(git('diff','--name-only',HOST_PREDECESSOR,source).splitlines())==permitted,'host exact two-file authority delta')
    for path in permitted:
        entry=git('ls-tree',source,'--',path).split('\t',1)
        check(len(entry)==2 and entry[0].startswith('100644 blob ') and entry[1]==path,'host regular source file '+path)
    check(digest(raw(source,RECORD+'/audit_reports.py'))==HOST_AUDIT_SHA256,'host frozen S2 raw auditor')
    verifier=raw(source,RECORD+'/verify_dispatch.py')
    start=b'# BEGIN EXACT HOST REQUALIFICATION AUTHORITY\n'
    end=b'# END EXACT HOST REQUALIFICATION AUTHORITY\n'
    check(verifier.count(start)==verifier.count(end)==1,'host one bounded added authority block')
    before,rest=verifier.split(start);added,after=rest.split(end)
    restored=before+after
    for old,new in HOST_VERIFY_ROUTE_REPLACEMENTS:
        check(restored.count(new.encode())==1,'host exact source routing replacement')
        restored=restored.replace(new.encode(),old.encode())
    check(restored==previous_verifier,'host all old verifier bytes preserved except explicit authority routing')
    old_pins=json.loads(previous_pins);new_pins=json.loads(raw(source,RECORD+'/CANDIDATE_PINS.json'))
    check(set(new_pins)==set(old_pins)|{'host_requalification'},'host one additive pin metadata object')
    check(all(new_pins[k]==v for k,v in old_pins.items() if k!='bindings'),'host historical pin metadata unchanged')
    check(set(new_pins['bindings'])==set(old_pins['bindings']) and len(new_pins['bindings'])==378,'host all 378 binding paths unchanged')
    changed={path for path in old_pins['bindings'] if old_pins['bindings'][path]!=new_pins['bindings'][path]}
    check(changed=={RECORD+'/verify_dispatch.py'},'host only verifier binding changed')
    check(new_pins['bindings'][RECORD+'/verify_dispatch.py']==dict(sha256=digest(verifier),bytes=len(verifier)),'host actual derived verifier binding')
    check(new_pins['host_requalification']==host_requalification_metadata(),'host exact selected-grant pin metadata')

def verify_host_requalification_authority(envelope):
    check(not ({'clean_room','lint_followup'}&set(envelope)),'host exclusive new authority route')
    c=envelope['host_requalification']
    expected=dict(recovery_id=HOST_EPOCH,branch=CLEAN_BRANCH,charter_commit=HOST_CHARTER_COMMIT,
                  charter_blob=HOST_CHARTER_BLOB,lane='ci',attempt=1,attempt_id=HOST_EPOCH+'/ci-01',
                  selected_plan=HOST_PLAN,execution_environment='github_hosted',local_pass=False)
    check(c==expected,'host exact hosted-only authority identity')
    check(type(c['attempt']) is int and type(envelope['attempt']) is int and envelope['attempt']==1,'host integer attempt one')
    check(c['local_pass'] is False and envelope.get('local_execution') is False,'host never claims local execution or pass')
    check(all(isinstance(envelope.get(k),str) and len(envelope[k])==40 and all(x in '0123456789abcdef' for x in envelope[k]) for k in ('source','first_parent','validation','event')),'host actual Git object identities')
    check(isinstance(envelope.get('run'),str) and envelope['run'].isdecimal() and int(envelope['run'])>0,'host actual run identity')
    check(type(envelope.get('job_id')) is int and envelope['job_id']>0,'host actual job identity')
    check(git('rev-parse',HOST_CHARTER_COMMIT+':'+HOST_CHARTER_PATH)==HOST_CHARTER_BLOB,'host immutable new charter blob')
    charter_bytes=raw(HOST_CHARTER_COMMIT,HOST_CHARTER_PATH)
    check(digest(charter_bytes)==HOST_CHARTER_SHA256,'host immutable new charter hash')
    charter=json.loads(charter_bytes)
    check(charter['authorization']['state']=='EXPLICIT_USER_GRANT','host explicit fresh selected authority')
    check(charter['recovery_id']==HOST_EPOCH and charter['repository']=='GodaPupa/argentum-batshit-test' and charter['branch']==CLEAN_BRANCH,'host new charter identity')
    check(charter['source_basis']==HOST_PREDECESSOR and charter['source_basis_tree']==HOST_PREDECESSOR_TREE and charter['base_checkpoint']==HOST_CHECKPOINT,'host new charter fixed predecessor and checkpoint')
    check(charter['selected_plan']==HOST_PLAN and charter['execution_environment']=='github_hosted' and charter['local_pass'] is False and charter['replaces_local_pass_before_ci'] is True,'host selected Option B only')
    permitted={RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json'}
    check(set(charter['source_amendment']['paths'])==permitted,'host exact two-path source authority')
    check(charter['budgets']==HOST_BUDGETS and all(type(charter['budgets'][k]) is type(v) for k,v in HOST_BUDGETS.items()),'host new finite allowance without additive old CI')
    old_charter=json.loads(raw(LINT_CHARTER_COMMIT,LINT_CHARTER_PATH))
    check(charter['required_gates']==old_charter['required_gates'],'host every frozen semantic gate retained')
    check(charter['safeguards']==old_charter['safeguards'],'host every historical safeguard retained')
    first=envelope['first_parent'];source=envelope['source'];validation=envelope['validation'];event=envelope['event']
    for ancestor in (HOST_CHECKPOINT,HOST_CHARTER_COMMIT,HOST_FAILED_READBACK,source):
        check(git('merge-base','--is-ancestor',ancestor,first)=='','host carrier immutable lineage '+ancestor)
    check(raw(first,HOST_CHARTER_PATH)==charter_bytes,'host carrier charter unchanged')
    delta=set(git('diff','--name-only',HOST_CHECKPOINT,first).splitlines())
    check(permitted<=delta and all(path in permitted or path.startswith(HOST_NAMESPACE+'/') for path in delta),'host earlier source and evidence unchanged outside new namespace')
    previous=json.loads(raw(HOST_CHECKPOINT,LINT_NAMESPACE+'/CURRENT.json'))
    check(previous['status']=='CLOSED_FAILED_HOST_PREREQUISITES_EVIDENCE_VERIFIED' and previous['admitted'] is False,'host old lint attempt stays closed failed')
    check(previous['attempts']==dict(preflight_reserved=1,preflight_started=1,preflight_completed=1,local_reserved=1,local_started=1,local_completed=1,ci_activated=0,original_artifact_downloads=0),'host consumed historical attempts retained')
    check(previous['local_result']['source']==HOST_PREDECESSOR and previous['local_result']['all_frozen_local_gates_pass'] is False and previous['local_result']['immutable_readback']=='PASS','host failed result readback is not local pass')
    check(charter['prior_failure']==previous['local_result'],'host immutable failed-evidence reference')
    check(raw(first,LINT_NAMESPACE+'/CURRENT.json')==raw(HOST_CHECKPOINT,LINT_NAMESPACE+'/CURRENT.json'),'host failed original ledger unchanged')
    current=json.loads(raw(first,HOST_NAMESPACE+'/CURRENT.json'))
    check(current['recovery_id']==HOST_EPOCH and current['branch']==CLEAN_BRANCH,'host current identity')
    check(current['charter']==HOST_CHARTER_PATH and current['charter_sha256']==HOST_CHARTER_SHA256,'host current charter pin')
    check(current['source_basis']==HOST_PREDECESSOR and current['base_checkpoint']==HOST_CHECKPOINT and current['prior_failure']==charter['prior_failure'],'host current frozen predecessor and failed evidence')
    check(current['selected_plan']==HOST_PLAN and current['execution_environment']=='github_hosted' and current['local_pass'] is False,'host current selected hosted-only plan')
    check(current['owner']==HOST_OWNER and current['lease']['owner']==HOST_OWNER,'host sole foreground owner')
    check(current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False and current['lease']['writable_ref']=='refs/heads/'+CLEAN_BRANCH,'host exclusive writable branch and owner')
    check(current['status']=='HOSTED_ACTIVATION_RESERVED','host one durably reserved activation')
    check(current['attempts']==HOST_ATTEMPTS and all(type(current['attempts'][k]) is int for k in HOST_ATTEMPTS),'host reservations do not fabricate in-job starts or completion')
    check(type(current['prior_candidate02_ci_activations']) is int and current['prior_candidate02_ci_activations']==0,'host no prior Candidate02 CI allowance reset')
    check(current['safeguards']==charter['safeguards'] and current['safeguards']['phase2_games']==current['safeguards']['phase2_seeds']==0,'host frozen safeguards and zero official counters')
    check(current['admitted'] is False,'host no advance admission')
    q=envelope['qualification']
    check(q==current['source_freeze'],'host exact carrier qualification freeze')
    check(q['source']==source and q['source_basis']==HOST_PREDECESSOR and q['candidate_index']=='02' and q['binding_count']==378,'host actual S3 source and unchanged binding count')
    check(q['validation']==validation and q['workflow_path']==HOST_WORKFLOW_PATH,'host frozen validation identity')
    check(git('show','-s','--format=%P',event).split()==[first,validation],'host exact ordered event parents')
    check(git('show','-s','--format=%P',validation)==source,'host validation sole S3 parent')
    check(git('diff','--name-only',source,validation)==HOST_WORKFLOW_PATH and git('diff','--name-only',first,event)==HOST_WORKFLOW_PATH,'host workflow-only validation and event deltas')
    check(git('rev-parse',source+'^{tree}')==q['source_tree'] and git('rev-parse',validation+'^{tree}')==q['validation_tree'],'host actual source and validation trees')
    check(git('rev-parse',source+':'+RECORD+'/CANDIDATE_PINS.json')==q['candidate_pins_blob'],'host actual S3 pins blob')
    workflow=raw(first,HOST_NAMESPACE+'/CI_ACTIVATION.yml')
    check(raw(event,HOST_WORKFLOW_PATH)==raw(validation,HOST_WORKFLOW_PATH)==workflow,'host same frozen workflow bytes in C V E')
    check(git('rev-parse',event+':'+HOST_WORKFLOW_PATH)==git('rev-parse',validation+':'+HOST_WORKFLOW_PATH)==q['workflow_blob'] and digest(workflow)==q['workflow_sha256'],'host actual workflow blob and hash')
    freeze_bytes=raw(first,HOST_NAMESPACE+'/SOURCE_FREEZE.json')
    check(digest(freeze_bytes)==q['source_freeze_sha256'],'host immutable base source freeze bytes')
    base=json.loads(freeze_bytes)['qualification']
    check(all(q[k]==v for k,v in base.items()) and set(q)==set(base)|HOST_Q_EXTENSIONS,'host exact base and operational freeze fields')
    for name,path in [('receiver','receive_hosted.py'),('readback','immutable_hosted_readback.py'),('readiness_helper','hosted_runtime.py')]:
        full=HOST_NAMESPACE+'/'+path;content=raw(first,full)
        check(digest(content)==q[name+'_sha256'] and git('rev-parse',first+':'+full)==q[name+'_blob'],'host exact pre-event '+name+' bytes')
    contract=raw(first,HOST_READINESS_CONTRACT_PATH)
    check(charter['readiness']['contract_path']==HOST_READINESS_CONTRACT_PATH and digest(contract)==charter['readiness']['contract_sha256']==q['readiness_contract_sha256'],'host immutable readiness contract without a pre-event result')
    check(raw(HOST_CHARTER_COMMIT,HOST_READINESS_CONTRACT_PATH)==contract,'host readiness contract fixed with charter')
    check(git('ls-tree','-r','--name-only',first,'--',HOST_NAMESPACE+'/HOSTED_READINESS.json',HOST_NAMESPACE+'/HOSTED_RUN.json',HOST_NAMESPACE+'/RECEIVED_CI_01')=='','host no fabricated pre-event runtime or receipt')
    return current

def verify_host_runtime_readiness(envelope):
    # The frozen qualifier calls verify() itself. This final prerequisite cannot
    # be disabled by a dispatch flag; authority-only controls never launch it.
    import importlib.util
    q=envelope['qualification'];first=envelope['first_parent']
    expected_env=dict(GITHUB_ACTIONS='true',GITHUB_REPOSITORY='GodaPupa/argentum-batshit-test',
                      GITHUB_REF='refs/heads/'+CLEAN_BRANCH,GITHUB_EVENT_NAME='push',
                      GITHUB_SHA=envelope['event'],GITHUB_RUN_ID=envelope['run'],
                      GITHUB_RUN_ATTEMPT='1',GITHUB_RUN_NUMBER='1',GITHUB_JOB='verify')
    check(all(os.environ.get(k)==v for k,v in expected_env.items()),'host actual one-shot GitHub execution environment')
    check(git('rev-parse','HEAD')==envelope['validation'],'host actual qualification checkout V')
    runtime=Path(os.environ['RUNNER_TEMP'])/'manual-host-requalification-runtime'
    helper=Path(os.environ['RUNNER_TEMP'])/'manual-host-requalification-tools'/'hosted_runtime.py'
    helper_bytes=raw(first,HOST_NAMESPACE+'/hosted_runtime.py')
    check(helper.is_file() and not helper.is_symlink() and helper.read_bytes()==helper_bytes and digest(helper_bytes)==q['readiness_helper_sha256'],'host loaded readiness auditor is exact C helper')
    contract_bytes=raw(first,HOST_READINESS_CONTRACT_PATH)
    check((runtime/'READINESS_CONTRACT.json').read_bytes()==contract_bytes,'host runtime uses exact frozen readiness contract')
    check(json.loads((runtime/'RECOVERY_EVENT.json').read_bytes())==envelope,'host runtime actual envelope equals qualification envelope')
    spec=importlib.util.spec_from_file_location('_manual_host_readiness_frozen',helper)
    check(spec is not None and spec.loader is not None,'host immutable readiness auditor import')
    module=importlib.util.module_from_spec(spec);sys.modules[spec.name]=module;spec.loader.exec_module(module)
    result=module.audit_readiness(runtime,json.loads(contract_bytes),envelope)
    check(isinstance(result,dict) and result.get('state')=='READINESS_AUDIT_PASS','host actual MockK and nine Flyway cases passed before qualification')
# END EXACT HOST REQUALIFICATION AUTHORITY
def verify(envelope):
    source=envelope['source'];q=envelope['qualification'];grant=envelope['grant']
    clean_room='clean_room' in envelope
    lint_followup='lint_followup' in envelope
    host_requalification='host_requalification' in envelope
    if host_requalification:verify_host_requalification_authority(envelope)
    elif lint_followup:verify_lint_followup_authority(envelope)
    elif clean_room:verify_clean_room_authority(envelope)
    # Historical proofs still read S1; actual source bindings read S2 or the authorized S3.
    proof_source=LINT_PREDECESSOR if (lint_followup or host_requalification) else source
    normalization_source=NORMALIZED_SOURCE if (clean_room or lint_followup or host_requalification) else source
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
            amended=raw(proof_source,RECORD+'/'+name)
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
    if clean_room or lint_followup or host_requalification:amended_audit_sha=verify_m14_successor(proof_source)
    if lint_followup or host_requalification:verify_lint_successor(HOST_PREDECESSOR if host_requalification else source)
    if host_requalification:verify_host_successor(source)
    for name,pin in manifest['files'].items():
        check(digest(raw(proposal,RECORD+'/'+name))==pin['sha256'],'immutable proposal history '+name)
        if audit_grant is not None and name=='audit_reports.py':
            check(digest(raw(proof_source,RECORD+'/'+name))==amended_audit_sha,'amended audit proposal binding')
        elif mapping_grant is not None and name in mapping_grant['mapping_pins']:
            check(digest(raw(proof_source,RECORD+'/'+name))==mapping_grant['mapping_pins'][name]['new_sha256'],'amended proposal binding '+name)
        else:check(digest(raw(proof_source,RECORD+'/'+name))==pin['sha256'],'immutable proposal changed '+name)
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
        check(git('merge-base','--is-ancestor',base,proof_source)=='','fixture successor source lineage')
        fixture_path='mtg-sets/2008-2016/tests/src/test/kotlin/com/wingedsheep/engine/scenarios/RecklessBushwhackerScenarioTest.kt'
        check(fixture_grant['source_delta_paths']==[fixture_path],'one fixture path authority')
        allowed={fixture_path,RECORD+'/verify_dispatch.py',RECORD+'/CANDIDATE_PINS.json',RECORD+'/TEST_MATRIX.json',RECORD+'/COMPILE_WITNESSES.json'}
        allowed.add(RECORD+'/audit_reports.py')
        check(allowed==set(mapping_grant['exact_delta_paths'])|{RECORD+'/audit_reports.py'},'exact authorized six paths')
        delta=set(git('diff','--name-only',base,proof_source).splitlines())
        check(delta==allowed,'exact fixture and mechanical binding delta')
        second_grant=json.loads(raw('7cd66adaa102d3f6aca33bbf238d86493a0bce9a','experiments/manual-transmission/autonomy/CURRENT.json'))['surge_bushwhacker_second_fixture_binding_authorization']
        check(second_grant['explicit_user_grant'] is True and second_grant['exact_registration']=='cardRegistry.register(com.wingedsheep.mtg.sets.definitions.m10.cards.DoomBlade)','second fixture authority')
        predecessor='6161f59565d51b7aff933a8af9f3c07f00da7e65'
        previous_pins=json.loads(raw(predecessor,RECORD+'/CANDIDATE_PINS.json'))
        for path,pin in previous_pins['bindings'].items():
            check(digest(raw(base,path))==pin['sha256'],'prior candidate02 correction preserved '+path)
            if path not in allowed:check(raw(proof_source,path)==raw(predecessor,path),'unchanged candidate02 source '+path)
        old_fixture=raw(predecessor,fixture_path)
        old_line=b'        cardRegistry.register(requireNotNull(cardRegistry.getCard("Doom Blade#M10-93")))\n'
        new_line=b'        cardRegistry.register(com.wingedsheep.mtg.sets.definitions.m10.cards.DoomBlade)\n'
        check(old_fixture.count(old_line)==1 and raw(proof_source,fixture_path)==old_fixture.replace(old_line,new_line),'exact second fixture replacement; assertions unchanged')
    else:
        delta=set(git('diff','--name-only',proposal,proof_source).splitlines())
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
    current=json.loads(raw(HISTORICAL_CHECKPOINT if (clean_room or lint_followup or host_requalification) else envelope['first_parent'],'experiments/manual-transmission/autonomy/CURRENT.json'))
    if not (clean_room or lint_followup or host_requalification):
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
    if host_requalification:verify_host_runtime_readiness(envelope)
    return pins
if __name__=='__main__':
    path=Path(sys.argv[1]) if len(sys.argv)>1 else Path(os.environ['RUNNER_TEMP'])/'RECOVERY_EVENT.json'
    envelope=json.loads(path.read_text());verify(envelope)
    print(json.dumps({'state':'SOURCE_BINDING_PASS','source':envelope['source'],'candidate_index':envelope['qualification']['candidate_index']}))
