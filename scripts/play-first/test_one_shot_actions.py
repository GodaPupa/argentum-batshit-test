"""Excluded synthetic contract/infrastructure tests; NEVER owner/game authority."""
from copy import deepcopy
from datetime import datetime, timezone, timedelta
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import one_shot_actions as shot
import gameplay_authority as auth

ROOT=Path(__file__).resolve().parents[2]
NOW=datetime(2026,10,13,12,0,0,tzinfo=timezone.utc)

def fixture_intent():
    # Clearly fictional labels and digests, not a selected prospective game.
    return dict(contract=shot.INTENT,
      source=dict(commit='1'*40,tree='2'*40,parent=shot.PARENT,
                  engineSha256='3'*64,deckSha256='4'*64,pilotSha256='5'*64),
      workflow=dict(path=shot.WORKFLOW,ref=shot.REF,blob='6'*40,sha256='7'*64),
      case=dict(caseId='SYNTHETIC_CASE_NOT_EXECUTABLE',gameId='SYNTHETIC_GAME_NOT_EXECUTABLE',
                pestSeat=0,monsterSeat=1,scope=auth.SCOPE,originalOnly=True,maxGames=1,
                permittedInputsSha256='8'*64))

def encode(value):return json.dumps(value,sort_keys=True).encode()

def comment(comment_id,contract,expected,stamp='2026-10-13T11:58:00Z'):
    claims=expected|{'expiresAt':'2026-10-13T12:30:00Z'}
    return dict(id=comment_id,user=dict(id=auth.OWNER_ID,login=auth.OWNER_LOGIN),
      author_association='OWNER',issue_url='https://api.github.com/repos/'+auth.REPOSITORY+'/issues/231',
      html_url='https://github.com/'+auth.REPOSITORY+'/pull/231#issuecomment-'+str(comment_id),
      created_at=stamp,updated_at=stamp,body=contract+'\n'+json.dumps(claims,sort_keys=True))

class SyntheticProtection:
    """Unauthenticated CODE-only fixture, not a configurable production backend."""
    def __init__(self):
        self.value=dict(origin=shot.SYNTHETIC,authenticatedProductionOrigin=False,
          observedAt='2026-10-13T12:00:00Z',
          policy=dict(id=90,name='SYNTHETIC_NOT_AN_ACTUAL_ENVIRONMENT',ownerReviewerId=auth.OWNER_ID,
                      allowSelfReview=True,deploymentBranch=shot.BRANCH),
          environment=dict(id=90,name='SYNTHETIC_NOT_AN_ACTUAL_ENVIRONMENT',can_admins_bypass=False,
            deployment_branch_policy=dict(protected_branches=False,custom_branch_policies=True),
            protection_rules=[dict(type='required_reviewers',prevent_self_review=False,
               reviewers=[dict(type='User',reviewer=dict(id=auth.OWNER_ID,login=auth.OWNER_LOGIN))])],
            verified_branch_policies=[dict(type='branch',name=shot.BRANCH)]))
        self.calls=[]
    def verify(self,stage):self.calls.append(stage);return deepcopy(self.value)

class SyntheticGitHub:
    """No credentials/network/signatures. Fictional IDs ONLY fixture lookups."""
    def __init__(self,data,protection):
        self.calls=[];self.owner_id=11;self.release_id=12
        f=shot.freeze_intent(data);s=f['intent']['source']
        self.context=dict(repository=auth.REPOSITORY,event='push',ref=shot.REF,sha=s['commit'],
             runId=9001,runNumber=1,runAttempt=1,workflowRef=auth.REPOSITORY+'/'+shot.WORKFLOW+'@'+shot.REF)
        self.run=dict(id=9001,run_number=1,run_attempt=1,event='push',head_sha=s['commit'],
          head_branch=shot.BRANCH,path=shot.WORKFLOW,workflow_id=9002,status='in_progress',conclusion=None,
          repository=dict(full_name=auth.REPOSITORY),head_repository=dict(full_name=auth.REPOSITORY))
        self.commit=dict(sha=s['commit'],tree=dict(sha=s['tree']),parents=[dict(sha=shot.PARENT)])
        self.owner=comment(11,shot.OWNER,shot.owner_expected(f,protection.value['policy']))
        self.release=None;self.extra=[];self.history_runs=[deepcopy(self.run)];self.total=1
    def get(self,path):
        self.calls.append(path)
        if path=='':return dict(owner=dict(id=auth.OWNER_ID,login=auth.OWNER_LOGIN))
        if path=='issues/comments/11':return deepcopy(self.owner)
        if path=='issues/comments/12' and self.release:return deepcopy(self.release)
        if path=='actions/runs/9001':return deepcopy(self.run)
        if path.startswith('git/commits/'):return deepcopy(self.commit)
        if path.startswith('actions/workflows/9002/runs?'):return dict(total_count=self.total,workflow_runs=deepcopy(self.history_runs))
        if path.startswith('issues/231/comments?'):return deepcopy([self.owner]+([self.release] if self.release else [])+self.extra)
        if path.startswith('pulls/231/'):return []
        raise ValueError('missing synthetic API fixture: '+path)

class OneShotTests(unittest.TestCase):
    def setUp(self):
        self.intent=fixture_intent();self.data=encode(self.intent);self.protection=SyntheticProtection()
        self.client=SyntheticGitHub(self.data,self.protection);self.context=self.client.context
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
    def preflight(self):
        return shot.verify_preflight(self.data,11,self.context,_client=self.client,_environment=self.protection,_now=NOW)
    def prepare_release(self):
        self.hold=self.preflight()['hold'];f=shot.freeze_intent(self.data)
        self.client.release=comment(12,shot.RELEASE,
          shot.release_expected(f,self.protection.value['policy'],11,self.context,shot.digest(shot.canonical(self.hold))),
          stamp='2026-10-13T12:00:00Z')
    def final(self,now=NOW):
        return shot.verify_final(self.data,11,12,self.context,self.hold,
               _client=self.client,_environment=self.protection,_now=now)
    def test_synthetic_preflight_is_engine_free_not_authenticated_owner_approval(self):
        value=self.preflight();self.assertEqual(value['stage'],'ENGINE_FREE_HOLD')
        for k in ('engineAllowed','gameplayAuthorized','productionQualified','authenticatedProductionOrigin'):
            self.assertIs(value[k],False)
        self.assertEqual(value['origin'],shot.SYNTHETIC)
    def test_separate_run_specific_synthetic_release_corresponds_but_never_launches(self):
        self.prepare_release();value=self.final()
        self.assertEqual(value['stage'],'FINAL_RELEASE_CONTRACT_CORRESPONDENCE_ONLY')
        self.assertFalse(value['engineAllowed']);self.assertFalse(value['persistentCrossWorkflowSingleUse'])
        self.assertFalse(value['physicalLaunchOrderingQualified'])
    def test_no_owner_comment_or_future_run_id_in_immutable_intent(self):
        self.assertEqual(set(self.intent),{'contract','source','workflow','case'})
        self.assertNotIn('ownerDecisionId',self.data.decode());self.assertNotIn('runId',self.data.decode())
        frozen=shot.freeze_intent(self.data);self.assertEqual(frozen['intentSha256'],hashlib.sha256(self.data).hexdigest())
    def test_owner_comment_binds_frozen_digest_without_own_provider_id(self):
        claims=auth.decode(self.client.owner['body'].split('\n',1)[1])
        self.assertEqual(claims['intentSha256'],shot.digest(self.data));self.assertNotIn('ownerDecisionId',claims)
        self.assertNotIn('runId',claims)
    def test_missing_external_environment_backend_denies_before_any_network(self):
        with self.assertRaisesRegex(ValueError,'verification unavailable'):
            shot.verify_preflight(self.data,11,self.context,_client=self.client,_now=NOW)
        self.assertEqual(self.client.calls,[])
    def test_forged_production_origin_cannot_promote_a_fixture(self):
        self.protection.value.update(origin='REAL_TRUSTED_PRODUCTION',authenticatedProductionOrigin=True)
        with self.assertRaises(ValueError):self.preflight()
    def test_owner_readback_is_live_double_read_not_supplied_json_flag(self):
        self.preflight();self.assertGreaterEqual(self.client.calls.count('issues/comments/11'),2)
        self.assertIn('issues/231/comments?per_page=100&page=1',self.client.calls)
    def test_non_gameplay_construction_comment_is_refused(self):
        self.client.owner['body']='AUTHORIZE LOCAL ENGINEERING ONLY'
        with self.assertRaises(ValueError):self.preflight()
    def test_owner_execution_flag_cannot_authenticate_approval(self):
        self.client.owner['body']='{"executionAuthorized":true}'
        with self.assertRaises(ValueError):self.preflight()
    def test_revocation_before_preflight_refuses(self):
        self.client.extra=[dict(id=13,user=dict(id=auth.OWNER_ID),updated_at='2026-10-13T11:59:00Z',body='REVOKED')]
        with self.assertRaises(ValueError):self.preflight()
    def test_expired_owner_refuses_new_execution(self):
        self.client.owner['body']=self.client.owner['body'].replace('12:30:00Z','11:59:00Z')
        with self.assertRaises(ValueError):self.preflight()
    def test_altered_owner_between_readbacks_refuses(self):
        get=self.client.get;reads=0
        def changing(path):
            nonlocal reads
            if path=='issues/comments/11':
                reads+=1
                if reads==2:self.client.owner['body']+=' '
            return get(path)
        self.client.get=changing
        with self.assertRaises(ValueError):self.preflight()
    def test_empty_provider_run_id_not_required_when_freezing_but_missing_after_hold_refuses(self):
        self.assertFalse(shot.freeze_intent(self.data)['engineAllowed'])
        self.context['runId']=None
        with self.assertRaises(ValueError):self.preflight()
    def test_second_workflow_run_refuses_even_if_current_original_flags_claim_one(self):
        extra=deepcopy(self.client.run);extra['id']=9003
        self.client.history_runs.append(extra);self.client.total=2
        with self.assertRaises(ValueError):self.preflight()
    def test_history_missing_page_refuses(self):
        self.client.total=2
        with self.assertRaises(ValueError):self.preflight()
    def test_history_duplicate_provider_id_refuses(self):
        self.client.history_runs.append(deepcopy(self.client.run));self.client.total=2
        with self.assertRaises(ValueError):self.preflight()
    def test_no_cross_workflow_or_persistent_storage_guarantee_claimed(self):
        p=shot.verify_original_history([self.client.run],self.client.run)
        self.assertFalse(p['persistentCrossWorkflowSingleUse']);self.assertFalse(p['hostAttested'])
    def test_missing_hold_prevents_final_release(self):
        self.hold={}
        with self.assertRaises(ValueError):self.final()
    def test_stale_hold_cannot_resume_or_replace_an_original(self):
        self.prepare_release()
        with self.assertRaises(ValueError):self.final(NOW+timedelta(seconds=61))
    def test_edited_intent_after_hold_refuses(self):
        self.prepare_release();self.intent['source']['deckSha256']='9'*64;self.data=encode(self.intent)
        with self.assertRaises(ValueError):self.final()
    def test_hold_digest_or_run_substitution_refuses(self):
        self.prepare_release();self.hold['run']['runId']=9004
        with self.assertRaises(ValueError):self.final()
    def test_missing_release_cannot_be_replaced_by_owner_decision(self):
        self.prepare_release();self.client.release=None
        with self.assertRaises(ValueError):self.final()
    def test_owner_decision_cannot_serve_as_its_own_run_specific_release(self):
        self.prepare_release()
        with self.assertRaises(ValueError):
            shot.verify_final(self.data,11,11,self.context,self.hold,_client=self.client,_environment=self.protection,_now=NOW)
    def test_post_hold_prelaunch_revocation_refuses(self):
        self.prepare_release();self.client.extra=[dict(id=13,user=dict(id=auth.OWNER_ID),updated_at='2026-10-13T12:00:00Z',body='REVOKED')]
        with self.assertRaises(ValueError):self.final()
    def test_no_engine_commands_or_environment_backend_override_in_helper(self):
        text=(ROOT/'scripts/play-first/one_shot_actions.py').read_text()
        for marker in ('GameEnvironment.create','CardRegistry(','env.reset(',"os.environ[",'--fixture','--client'):
            self.assertNotIn(marker,text)
        self.assertIn('engineAllowed=False',text)
    def test_inactive_config_all_future_game_inputs_unfilled(self):
        value=auth.decode((ROOT/shot.CONFIG).read_bytes());self.assertEqual(shot.inspect_inactive(value)['stage'],'INACTIVE_LOCAL_REVIEW_CANDIDATE')
        for k in ('sourcePins','protectedEnvironment','ownerDecision','caseIntent','permittedInputs','launcher'):
            self.assertIsNone(value[k])
    def test_cli_preflight_refuses_even_with_self_asserted_activation_environment(self):
        env=os.environ.copy();env.update(ARGENTUM_GAMEPLAY_AUTHORIZED='true',ARGENTUM_ENVIRONMENT_PROTECTED='true')
        cmd=[sys.executable,'-I',str(ROOT/'scripts/play-first/one_shot_actions.py'),'preflight','--root',str(ROOT)]
        p=subprocess.run(cmd,env=env,capture_output=True)
        self.assertEqual(p.returncode,78);self.assertIn(b'false',p.stdout);self.assertIn(b'REFUSED',p.stderr)
    def test_cli_final_boundary_is_permanently_refused_in_candidate(self):
        p=subprocess.run([sys.executable,'-I',str(ROOT/'scripts/play-first/one_shot_actions.py'),'final-boundary','--root',str(ROOT)],capture_output=True)
        self.assertEqual(p.returncode,78)
    def test_inventory_preserves_first_failure_and_partial_logs(self):
        root=Path(self.temp.name);(root/'verifier.log').write_bytes(b'first attempt refused\n')
        first=shot.inventory(root);before=first.read_bytes();(root/'partial.log').write_bytes(b'interrupted\n')
        second=shot.inventory(root)
        self.assertNotEqual(first,second);self.assertEqual(first.read_bytes(),before)
        self.assertEqual(len(auth.decode(second.read_bytes())['payloads']),2)
    def test_inventory_idempotent_without_overwrite(self):
        root=Path(self.temp.name);(root/'stderr.log').write_bytes(b'FAIL\n')
        self.assertEqual(shot.inventory(root),shot.inventory(root))
    def test_inventory_symlink_refused_without_reading_target(self):
        root=Path(self.temp.name);(root/'bad').symlink_to('/not-an-evidence-payload')
        with self.assertRaises(ValueError):shot.inventory(root)
    def test_original_manifest_named_failure_log_is_not_silently_omitted(self):
        root=Path(self.temp.name);(root/'manifest-failure.json').write_bytes(b'{"firstFailure":true}\n')
        manifest=auth.decode(shot.inventory(root).read_bytes())
        self.assertEqual(manifest['payloads'][0]['path'],'manifest-failure.json')
    def test_invalid_reserved_inventory_name_refuses_instead_of_hiding_payload(self):
        root=Path(self.temp.name);(root/('manifest-'+'a'*64+'.json')).write_bytes(b'{"forged":true}')
        with self.assertRaises(ValueError):shot.inventory(root)
    def test_frozen_accepted_python_kotlin_gates_remain_byte_identical(self):
        for path in ['scripts/play-first/runtime_integration.py','scripts/play-first/gameplay_authority.py',
                     'scripts/play-first/focused_nonofficial_admission.py','scripts/play-first/strict_receiving.py',
                     'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt']:
            old=subprocess.check_output(['git','show',shot.PARENT+':'+path],cwd=ROOT)
            self.assertEqual((ROOT/path).read_bytes(),old)
    def test_final_history_accepts_only_the_separately_validated_release_comment(self):
        self.prepare_release();self.assertFalse(self.final()['engineAllowed'])
        self.client.extra=[dict(id=14,user=dict(id=auth.OWNER_ID),updated_at='2026-10-13T12:00:00Z',body='CONSUMED')]
        with self.assertRaises(ValueError):self.final()
    def test_release_expired_before_launch_refuses(self):
        self.prepare_release();self.client.release['body']=self.client.release['body'].replace('12:30:00Z','11:59:00Z')
        with self.assertRaises(ValueError):self.final()
    def test_run_completed_during_environment_hold_cannot_replay(self):
        self.prepare_release();self.client.run['status']='completed';self.client.run['conclusion']='success'
        with self.assertRaises(ValueError):self.final()
    def test_run_retry_after_hold_refuses(self):
        self.prepare_release();self.client.run['run_attempt']=2
        with self.assertRaises(ValueError):self.final()
    def test_unprotected_environment_after_owner_hold_refuses(self):
        self.prepare_release();self.protection.value['environment']['protection_rules']=[]
        with self.assertRaises(ValueError):self.final()
    def test_release_body_changed_during_final_readback_refuses(self):
        self.prepare_release();get=self.client.get;reads=0
        def changing(path):
            nonlocal reads
            if path=='issues/comments/12':
                reads+=1
                if reads==2:self.client.release['body']+=' '
            return get(path)
        self.client.get=changing
        with self.assertRaises(ValueError):self.final()
    def test_environment_has_exactly_owner_required_reviewer_not_a_team_substitute(self):
        rule=self.protection.value['environment']['protection_rules'][0]
        rule['reviewers'].append(dict(type='User',reviewer=dict(id=1,login='other')))
        with self.assertRaises(ValueError):self.preflight()
    def test_unreviewed_self_review_setting_refuses(self):
        self.protection.value['environment']['protection_rules'][0]['prevent_self_review']=True
        with self.assertRaises(ValueError):self.preflight()
    def test_duplicate_intent_keys_cannot_hide_game_identity(self):
        with self.assertRaises(ValueError):shot.freeze_intent(b'{"contract":"one","contract":"two"}')
    def test_workflow_only_pushes_unique_ref_and_hard_disables_game(self):
        workflow=json.loads((ROOT/shot.WORKFLOW).read_text())
        self.assertEqual(workflow['on'],{'push':{'branches':[shot.BRANCH]}})
        for job in workflow['jobs'].values():self.assertIn('github.run_number == 1 && github.run_attempt == 1',job['if'])
        self.assertIn("needs.preflight.outputs.activation_verified == 'true'",workflow['jobs']['game']['if'])
        self.assertEqual(workflow['jobs']['game']['environment']['name'],'${{ needs.preflight.outputs.protected_environment }}')
        self.assertEqual(set(workflow['on']),{'push'})
    def test_workflow_no_retry_or_engine_command_can_be_reached(self):
        workflow=json.loads((ROOT/shot.WORKFLOW).read_text());pre=workflow['jobs']['preflight'];game=workflow['jobs']['game']
        for step in pre['steps']:
            for marker in ('just test','gradlew','GameEnvironment','PLAY_FIRST_ONE_SHOT_ACK'):
                self.assertNotIn(marker,json.dumps(step))
        self.assertFalse(workflow['concurrency']['cancel-in-progress'])
        for job in (pre,game):
            self.assertNotIn('strategy',job)
            for step in job['steps']:
                self.assertNotIn('continue-on-error',step);self.assertNotIn('--rerun',json.dumps(step))
        preserve=[s for j in (pre,game) for s in j['steps'] if s.get('if')=='always()']
        self.assertEqual(len(preserve),4)
        for step in preserve:
            if 'uses' in step:self.assertFalse(step['with']['overwrite'])
    def test_workflow_actions_are_immutable_commit_pins(self):
        workflow=json.loads((ROOT/shot.WORKFLOW).read_text())
        actions=[s['uses'] for j in workflow['jobs'].values() for s in j['steps'] if 'uses' in s]
        self.assertEqual(len(actions),8)
        for action in actions:self.assertRegex(action,r'^[A-Za-z0-9_-]+/[A-Za-z0-9_/-]+@[0-9a-f]{40}$')
    def test_inactive_flags_cannot_be_replaced_by_true_or_case_fixture(self):
        config=auth.decode((ROOT/shot.CONFIG).read_bytes());config['active']=True
        with self.assertRaises(ValueError):shot.inspect_inactive(config)
        config['active']=False;config['caseIntent']=self.intent
        with self.assertRaises(ValueError):shot.inspect_inactive(config)
    def test_false_run_and_release_proof_cannot_enter_existing_kotlin_gate(self):
        text=(ROOT/'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt').read_text()
        self.assertIn('"TRUSTED_EXTERNAL_LAUNCH_RELEASE"',text)
        self.assertIn('check(it.getValue("engineAllowed").jsonPrimitive.boolean)',text)
        self.prepare_release();self.assertFalse(self.final()['engineAllowed'])
    def test_release_rechecks_current_run_history_and_owner_at_last_contract_boundary(self):
        self.prepare_release();self.client.calls=[];self.final()
        self.assertIn('actions/runs/9001',self.client.calls)
        self.assertIn('actions/workflows/9002/runs?per_page=100&page=1',self.client.calls)
        self.assertEqual(self.client.calls[-1],'issues/comments/11')
    def test_later_unrelated_owner_comment_conservatively_blocks_new_execution(self):
        self.client.extra=[dict(id=15,user=dict(id=auth.OWNER_ID),updated_at='2026-10-13T11:59:00Z',body='LATER COMMENT')]
        with self.assertRaises(ValueError):self.preflight()
    def test_old_focused_historical_correspondence_does_not_enable_new_actions(self):
        import focused_nonofficial_admission as focused
        self.assertEqual(focused.SOURCE_PARENT,'f98d850f3883493de5abf303da0e68c8902d16c6')
        with self.assertRaises(ValueError):focused.deny_activation()
        self.assertFalse(shot.inspect_inactive(auth.decode((ROOT/shot.CONFIG).read_bytes()))['executableActivation'])

def add_release_mutation(name,key,value):
    def test(self):
        self.prepare_release();claims=auth.decode(self.client.release['body'].split('\n',1)[1]);claims[key]=value
        self.client.release['body']=shot.RELEASE+'\n'+json.dumps(claims,sort_keys=True)
        with self.assertRaises(ValueError):self.final()
    setattr(OneShotTests,'test_release_'+name,test)

for name,key,value in [
 ('wrong_intent','intentSha256','a'*64),('wrong_owner','ownerDecisionId',13),
 ('substituted_hold','holdSha256','a'*64),('wrong_scope','scope','OFFICIAL'),
 ('replay_permitted','originalOnly',False),('two_originals','maxGames',2),
 ('boolean_original_count','maxGames',True),('run_cross_bound','run',{'runId':9005}),
 ('forged_environment','environment',{'name':'SELF_DECLARED'})]:add_release_mutation(name,key,value)

# Distinct IDs for every excluded negative contract case; all assert explicit
# exceptions (no Python assert), so normal/-O/-OO test the same safety checks.
def add_mutation_test(name, target, key, value):
    def test(self):
        if target=='intent':self.intent[key]=value;self.data=encode(self.intent)
        elif target.startswith('intent.'):
            self.intent[target.split('.')[1]][key]=value;self.data=encode(self.intent)
        elif target=='context':self.context[key]=value
        elif target=='run':self.client.run[key]=value;self.client.history_runs=[deepcopy(self.client.run)]
        elif target=='owner':self.client.owner[key]=value
        elif target=='protection':self.protection.value[key]=value
        elif target=='environment':self.protection.value['environment'][key]=value
        elif target=='policy':self.protection.value['policy'][key]=value
        else:raise RuntimeError('unknown test mutation')
        with self.assertRaises((ValueError,KeyError,TypeError)):self.preflight()
    setattr(OneShotTests,'test_'+name,test)

for name,target,key,value in [
 ('wrong_source_parent','intent.source','parent','a'*40),
 ('wrong_source_commit_owner_binding','intent.source','commit','a'*40),
 ('wrong_source_tree_owner_binding','intent.source','tree','a'*40),
 ('wrong_engine','intent.source','engineSha256','a'*64),
 ('wrong_deck','intent.source','deckSha256','a'*64),
 ('wrong_pilot','intent.source','pilotSha256','a'*64),
 ('wrong_workflow_blob','intent.workflow','blob','a'*40),
 ('wrong_workflow_bytes','intent.workflow','sha256','a'*64),
 ('wrong_workflow_ref','intent.workflow','ref','refs/heads/main'),
 ('wrong_workflow_path','intent.workflow','path','.github/workflows/ci.yml'),
 ('wrong_game','intent.case','gameId','SYNTHETIC_DIFFERENT_GAME'),
 ('wrong_case','intent.case','caseId','SYNTHETIC_DIFFERENT_CASE'),
 ('wrong_inputs','intent.case','permittedInputsSha256','a'*64),
 ('wrong_seat','intent.case','pestSeat',1),
 ('boolean_seat','intent.case','pestSeat',False),
 ('wrong_scope','intent.case','scope','OFFICIAL'),
 ('replay_b01','intent.case','caseId','PLAY_FIRST_B01'),
 ('continue_g08','intent.case','gameId','G08'),
 ('allow_retries','intent.case','originalOnly',False),
 ('two_games','intent.case','maxGames',2),
 ('boolean_max_games','intent.case','maxGames',True),
 ('future_comment_id_forbidden','intent','ownerDecisionId',11),
 ('future_run_id_forbidden','intent','runId',9001),
 ('context_fork','context','repository','unknown/fork'),
 ('context_dispatch','context','event','workflow_dispatch'),
 ('context_wrong_ref','context','ref','refs/heads/main'),
 ('context_wrong_head','context','sha','a'*40),
 ('context_second_run','context','runNumber',2),
 ('context_retry','context','runAttempt',2),
 ('context_boolean_run','context','runNumber',True),
 ('context_wrong_workflow','context','workflowRef','foreign/workflow'),
 ('provider_wrong_branch','run','head_branch','main'),
 ('provider_wrong_head','run','head_sha','a'*40),
 ('provider_wrong_workflow','run','path','.github/workflows/ci.yml'),
 ('provider_retry','run','run_attempt',2),
 ('provider_completed_original','run','status','completed'),
 ('provider_cancelled','run','conclusion','cancelled'),
 ('provider_missing_workflow_id','run','workflow_id',None),
 ('provider_fork','run','head_repository',dict(full_name='unknown/fork')),
 ('forged_owner_issuer','owner','user',dict(id=1,login='forged')),
 ('wrong_owner_association','owner','author_association','CONTRIBUTOR'),
 ('wrong_owner_pr','owner','issue_url','https://api.github.com/repos/'+auth.REPOSITORY+'/issues/230'),
 ('owner_self_declared_id','owner','id',True),
 ('missing_environment_id','environment','id',None),
 ('unprotected_environment','environment','protection_rules',[]),
 ('admin_bypass_enabled','environment','can_admins_bypass',True),
 ('environment_name_alone','environment','protection_rules',None),
 ('environment_replaced','environment','id',91),
 ('wrong_environment_name','environment','name','unreviewed'),
 ('unrestricted_deployment','environment','deployment_branch_policy',None),
 ('wrong_deployment_branch','environment','verified_branch_policies',[dict(type='branch',name='main')]),
 ('unreviewed_policy','policy','ownerReviewerId',1),
 ('stale_environment_facts','protection','observedAt','2026-10-13T11:58:00Z'),
 ('future_environment_facts','protection','observedAt','2026-10-13T12:01:00Z'),
 ('self_authenticating_environment_facts','protection','authenticatedProductionOrigin',True),
]:add_mutation_test(name,target,key,value)

if __name__=='__main__':unittest.main()
