"""Unauthenticated synthetic fixture correspondence. No games/real approvals."""
from copy import deepcopy
from datetime import datetime,timezone,timedelta
import base64,json,os
from pathlib import Path
import subprocess,sys,tempfile,unittest,urllib.error
import one_shot_actions as shot
import one_shot_launch as launch
import successor_protocol as successor
import gameplay_authority as auth
import test_one_shot_actions as old

ROOT=Path(__file__).resolve().parents[2]
NOW=datetime(2026,10,13,12,0,0,tzinfo=timezone.utc)

def encoded(value):return json.dumps(value,sort_keys=True).encode()

class LaunchFixture(old.SyntheticGitHub):
    """All provider IDs, labels, input bytes and readbacks are fake test data."""
    def __init__(self):
        self.protection=old.SyntheticProtection();self.intent=old.fixture_intent()
        # G99/seed label are unauthenticated preexisting synthetic-fixture
        # examples only, NEVER an allocated future case/game/entropy sample.
        self.intent['case'].update(caseId='PLAY_FIRST_SYNTHETIC_ONLY-G99',gameId='G99')
        s=self.intent['source'];w=self.intent['workflow']
        self.case=dict(contract=launch.ENGINE_CASE,
          case=dict(blockId='PLAY_FIRST_SYNTHETIC_ONLY',gameId='G99',pair=1,pestSeat=0,
            fixtureSeedHex='0x0000000000000099',sourceCommit=s['commit'],scope=auth.SCOPE,intendedBlockGames=1),
          plan=dict(sourceCommit=s['commit'],sourceParent=shot.PARENT,sourceTree=s['tree'],validationCommit=s['commit'],
            validationTree=s['tree'],workflowPath=w['path'],workflowBlob=w['blob'],ref=shot.REF,event='push',runNumber=1,runAttempt=1))
        self.intent['case']['permittedInputsSha256']=shot.digest(encoded(self.case));data=encoded(self.intent)
        super().__init__(data,self.protection);self.run['created_at']='2026-10-13T11:59:00Z';self.history_runs=[deepcopy(self.run)]
        self.request=dict(contract=launch.REQUEST,intentBytesBase64=base64.b64encode(data).decode(),
            caseBytesBase64=base64.b64encode(encoded(self.case)).decode(),ownerDecisionId=11,environmentVerificationId=10,
            environmentPolicy=self.protection.value['policy'],environmentFacts=self.protection.value['environment'])
        frozen=shot.freeze_intent(data)
        self.allocation=old.comment(9,successor.ALLOCATION,successor.allocation_expected(frozen),stamp='2026-10-13T11:56:00Z')
        self.request.update(allocationDecisionId=9,allocationBodySha256=shot.digest(self.allocation['body'].encode()))
        self.environment=old.comment(10,launch.ENVIRONMENT,launch.environment_expected(self.request,frozen),stamp='2026-10-13T11:57:00Z')
        self.owner=old.comment(11,shot.OWNER,launch.owner_expected(self.request,frozen,shot.digest(self.environment['body'].encode())))
        self.absent=True
        p=successor.PREDECESSOR
        self.predecessor=dict(id=p['runId'],workflow_id=p['workflowId'],run_number=1,run_attempt=1,head_sha=p['source'],head_branch=p['ref'].removeprefix('refs/heads/'),path=p['workflow'],event='push',status='completed',conclusion='failure',repository={'full_name':auth.REPOSITORY},head_repository={'full_name':auth.REPOSITORY},updated_at='2026-10-13T11:55:00Z')
        self.predecessor_history=[deepcopy(self.predecessor)]
        self.original_artifacts={p[k+'ArtifactId']:dict(id=p[k+'ArtifactId'],expired=False,digest='sha256:'+p[k+'Sha256'],workflow_run=dict(id=p['runId'],head_sha=p['source'])) for k in ('preflight','failure')}
    def get(self,path):
        p=successor.PREDECESSOR
        if path=='issues/comments/9':self.calls.append(path);return deepcopy(self.allocation)
        if path=='actions/runs/'+str(p['runId']):self.calls.append(path);return deepcopy(self.predecessor)
        if path.startswith('actions/workflows/'+str(p['workflowId'])+'/runs?'):self.calls.append(path);return dict(total_count=len(self.predecessor_history),workflow_runs=deepcopy(self.predecessor_history))
        if path=='git/commits/'+p['source']:self.calls.append(path);return dict(sha=p['source'],tree=dict(sha=p['tree']),parents=[dict(sha=p['parent'])])
        if path.startswith('actions/artifacts/'):self.calls.append(path);return deepcopy(self.original_artifacts[int(path.split('/')[-1])])
        if path=='issues/comments/10':self.calls.append(path);return deepcopy(self.environment)
        if path.startswith('issues/231/comments?'):
            self.calls.append(path);return deepcopy([self.allocation,self.environment,self.owner]+([self.release] if self.release else [])+self.extra)
        if path.startswith(('git/ref/heads/','actions/workflows/play-first-')):
            self.calls.append(path)
            if self.absent:raise urllib.error.HTTPError('https://api.github.com/SYNTHETIC',404,'synthetic missing',{},None)
            return {'id':9002}
        if path.startswith('actions/runs?branch='):self.calls.append(path);return {'total_count':0,'workflow_runs':[]}
        return super().get(path)

class CommentEndpointSchemaTests(unittest.TestCase):
    """Only excluded in-memory provider-shape fixtures. No GitHub writes/engine."""
    @staticmethod
    def pair():
        detail=dict(id=10,body='reviewed owner record',user={'id':auth.OWNER_ID,'login':auth.OWNER_LOGIN},
            issue_url='https://api.github.com/repos/'+auth.REPOSITORY+'/issues/231',
            created_at='2026-10-13T11:57:00Z',updated_at='2026-10-13T11:57:00Z',
            minimized=None,pin=None,
            performed_via_github_app={'id':29,'slug':'synthetic-provider-app'})
        listed=deepcopy(detail)
        listed.pop('pin')
        listed['performed_via_github_app']['client_id']='synthetic-list-only-client-id'
        return detail,listed

    def test_exact_provider_shape_difference_is_accepted(self):
        detail,listed=self.pair()
        self.assertFalse(auth.same_json(detail,listed))
        self.assertTrue(launch.same_comment_list_detail(detail,listed))

    def test_baseline_same_comment_still_matches(self):
        detail,listed=self.pair()
        self.assertTrue(launch.same_comment_list_detail(detail,detail))
        self.assertTrue(launch.same_comment_list_detail(listed,listed))

    def test_only_permitted_direction_of_pin_difference(self):
        detail,listed=self.pair()
        reverse_detail=deepcopy(listed)
        reverse_list=deepcopy(detail)
        self.assertFalse(launch.same_comment_list_detail(reverse_detail,reverse_list))

    def test_altered_critical_fields_still_refused(self):
        mutations={
            'id': lambda e: e.update(id=11),
            'body': lambda e: e.update(body='forged body'),
            'created_at': lambda e:e.update(created_at='2026-10-13T11:56:00Z'),
            'updated_at': lambda e:e.update(updated_at='2026-10-13T11:58:00Z'),
            'owner':lambda e:e['user'].update(id=999),
            'issuer':lambda e:e['user'].update(login='forged'),
            'issue_url':lambda e:e.update(issue_url='https://api.github.com/repos/forged/example/issues/231'),
            'minimized':lambda e:e.update(minimized=True),
            'pin':lambda e:e.update(pin={'id':1}),
            'other_app_field':lambda e:e['performed_via_github_app'].update(slug='other'),
            'client_id_type':lambda e:e['performed_via_github_app'].update(client_id=123),
        }
        for name,mutate in mutations.items():
            with self.subTest(name=name):
                detail,listed=self.pair();mutate(listed)
                self.assertFalse(launch.same_comment_list_detail(detail,listed))

    def test_two_client_ids_must_match_when_both_returned(self):
        detail,listed=self.pair()
        detail['performed_via_github_app']['client_id']='another-client-id'
        self.assertFalse(launch.same_comment_list_detail(detail,listed))

    def test_non_dict_and_missing_ids_refused(self):
        detail,listed=self.pair()
        self.assertFalse(launch.same_comment_list_detail(detail,[]))
        self.assertFalse(launch.same_comment_list_detail(None,listed))
        listed.pop('id')
        self.assertFalse(launch.same_comment_list_detail(detail,listed))


class OneShotLaunchTests(unittest.TestCase):
    def setUp(self):
        self.f=LaunchFixture();self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.path=Path(self.temp.name)
    def verify(self,context=True,hold=None,now=NOW):
        return launch.verify(encoded(self.f.request),ROOT,context=self.f.context if context else None,hold=hold,
            _client=self.f,_checkout=lambda f:None,_now=now)
    def release(self):
        pre=self.verify();self.hold=launch.hold_record(pre)
        frozen=shot.freeze_intent(base64.b64decode(self.f.request['intentBytesBase64']))
        expected=launch.release_expected(self.f.request,frozen,self.f.context,shot.digest(shot.canonical(self.hold)),
            shot.digest(self.f.environment['body'].encode()))
        self.f.release=old.comment(12,shot.RELEASE,expected,stamp='2026-10-13T12:00:00Z')
    def list_only_app_metadata(self, mutate_list=None):
        """Synthetic reproduction of the two real GitHub response differences."""
        for comment in (self.f.environment,self.f.owner):
            comment['pin']=None
            comment['performed_via_github_app']={'id':999,'slug':'synthetic-github-app'}
        original_get=self.f.get
        def get(path):
            value=original_get(path)
            if path.startswith('issues/231/comments?'):
                for item in value:
                    if item.get('id') in (10,11):
                        item.pop('pin',None)
                        item['performed_via_github_app']['client_id']='synthetic-list-metadata-only'
                        if mutate_list is not None:
                            mutate_list(item)
            return value
        self.f.get=get

    def test_prepublication_passes_matching_list_detail_provider_shapes(self):
        self.list_only_app_metadata()
        proof=self.verify(context=False)
        self.assertEqual(proof['stage'],'PREPUBLICATION_OWNER_CORRESPONDENCE')
        self.assertFalse(proof['engineAllowed'])
        self.assertFalse(proof['authenticatedOwnerDecision'])

    def test_provider_shape_variant_keeps_final_synthetic_engine_denied(self):
        self.list_only_app_metadata()
        self.release()
        proof=self.verify(hold=self.hold)
        self.assertEqual(proof['stage'],'RUN_SPECIFIC_RELEASE_VERIFIED')
        self.assertFalse(proof['engineAllowed'])
        self.assertEqual(proof['origin'],shot.SYNTHETIC)

    def test_list_only_provider_shape_with_tampered_body_refuses(self):
        self.list_only_app_metadata(lambda item:item.update(body='substituted owner comment') if item['id']==11 else None)
        with self.assertRaisesRegex(ValueError,'authenticated decision missing from history|contradictory current decision'):
            self.verify(context=False)

    def test_list_only_provider_shape_with_tampered_owner_refuses(self):
        self.list_only_app_metadata(lambda item:item['user'].update(id=999) if item['id']==11 else None)
        with self.assertRaises(ValueError):self.verify(context=False)

    def test_list_only_provider_shape_with_later_revocation_refuses(self):
        self.list_only_app_metadata()
        self.f.extra=[dict(id=13,user={'id':auth.OWNER_ID,'login':auth.OWNER_LOGIN},
            updated_at='2026-10-13T11:59:30Z',body='REVOKED')]
        with self.assertRaisesRegex(ValueError,'supersession'):
            self.verify(context=False)

    def test_list_only_provider_shape_missing_selected_history_refuses(self):
        self.list_only_app_metadata()
        original_get=self.f.get
        def missing(path):
            result=original_get(path)
            if path.startswith('issues/231/comments?'):
                result=[x for x in result if x.get('id')!=11]
            return result
        self.f.get=missing
        with self.assertRaisesRegex(ValueError,'authenticated decision missing from history'):
            self.verify(context=False)

    def test_list_only_provider_shape_with_contradictory_duplicate_refuses(self):
        self.list_only_app_metadata()
        original_get=self.f.get
        def duplicate(path):
            result=original_get(path)
            if path.startswith('issues/231/comments?'):
                bad=deepcopy(result[1]);bad['body']='contradictory historical owner comment'
                result.append(bad)
            return result
        self.f.get=duplicate
        with self.assertRaisesRegex(ValueError,'contradictory current decision'):
            self.verify(context=False)

    def test_prepublication_needs_no_future_run_or_comment_ids_inside_intent(self):
        value=self.verify(context=False);self.assertEqual(value['stage'],'PREPUBLICATION_OWNER_CORRESPONDENCE')
        self.assertFalse(value['engineAllowed']);self.assertNotIn('runId',self.f.intent)
    def test_synthetic_owner_environment_readback_is_not_admin_api_authentication(self):
        value=self.verify();self.assertEqual(value['environmentProtectionProvenance'],'AUTHENTICATED_OWNER_SETTINGS_READBACK_NOT_ADMINISTRATION_API_READ')
        self.assertFalse(value['authenticatedOwnerDecision']);self.assertEqual(value['origin'],shot.SYNTHETIC)
    def test_synthetic_final_release_cannot_enable_adapter(self):
        self.release();value=self.verify(hold=self.hold)
        self.assertEqual(value['stage'],'RUN_SPECIFIC_RELEASE_VERIFIED');self.assertFalse(value['engineAllowed'])
        self.assertFalse(value['productionQualified']);self.assertFalse(value['hostAttested'])
    def test_owner_readback_and_case_decision_are_separate_actual_comment_contracts(self):
        self.verify();self.assertGreaterEqual(self.f.calls.count('issues/comments/10'),3);self.assertGreaterEqual(self.f.calls.count('issues/comments/11'),3)
    def test_missing_or_unprotected_environment_refuses_before_api(self):
        self.f.request['environmentFacts']['protection_rules']=[]
        with self.assertRaises(ValueError):self.verify()
        self.assertEqual(self.f.calls,[])
    def test_unverified_environment_name_cannot_authenticate_protection(self):
        self.f.environment['body']='OWNER SAID IT IS FINE'
        with self.assertRaises(ValueError):self.verify()
    def test_missing_owner_case_decision_refuses(self):
        self.f.owner['body']='LOCAL WORKFLOW PREPARATION AUTHORIZED ONLY'
        with self.assertRaises(ValueError):self.verify()
    def test_forged_environment_issuer_refuses(self):
        self.f.environment['user']={'id':1,'login':'forged'}
        with self.assertRaises(ValueError):self.verify()
    def test_changed_environment_facts_cannot_reuse_owner_readback_hash(self):
        self.f.request['environmentFacts']['extra']='altered'
        with self.assertRaises(ValueError):self.verify()
    def test_existing_destination_or_workflow_refuses_prepublication(self):
        self.f.absent=False
        with self.assertRaises(ValueError):self.verify(context=False)
    def test_unknown_permission_error_is_not_branch_absence(self):
        get=self.f.get
        def denied(path):
            if path.startswith('git/ref/'):raise urllib.error.HTTPError('https://api.github.com/SYNTHETIC',403,'synthetic denied',{},None)
            return get(path)
        self.f.get=denied
        with self.assertRaises(ValueError):self.verify(context=False)
    def test_owner_environment_readback_after_push_is_too_late(self):
        self.f.run['created_at']='2026-10-13T11:56:00Z';self.f.history_runs=[deepcopy(self.f.run)]
        with self.assertRaises(ValueError):self.verify()
    def test_later_revocation_refuses(self):
        self.f.extra=[dict(id=13,user=dict(id=auth.OWNER_ID),updated_at='2026-10-13T11:59:30Z',body='REVOKED')]
        with self.assertRaises(ValueError):self.verify()
    def test_owner_approval_expired_at_execution_refuses(self):
        self.f.owner['body']=self.f.owner['body'].replace('12:30:00Z','11:59:59Z')
        with self.assertRaises(ValueError):self.verify()
    def test_missing_run_specific_release_refuses(self):
        self.hold=launch.hold_record(self.verify())
        with self.assertRaises(ValueError):self.verify(hold=self.hold)
    def test_consumed_or_cancelled_original_run_cannot_replay(self):
        self.release();self.f.run.update(status='completed',conclusion='cancelled')
        with self.assertRaises(ValueError):self.verify(hold=self.hold)
    def test_retry_attempt_refuses(self):
        self.release();self.f.run['run_attempt']=2
        with self.assertRaises(ValueError):self.verify(hold=self.hold)
    def test_provider_endpoint_metadata_difference_is_not_an_execution_identity_difference(self):
        self.f.history_runs[0]['providerExtraObservation']='synthetic endpoint detail'
        self.assertFalse(self.verify()['engineAllowed'])
    def test_provider_history_changed_execution_identity_is_still_refused(self):
        self.f.history_runs[0]['head_sha']='a'*40
        with self.assertRaises(ValueError):self.verify()
    def test_synthetic_evidence_cannot_self_promote_to_live_authentication(self):
        self.f.origin='LIVE_GITHUB_OWNER_AND_PROVIDER_READBACK';self.f.authenticatedOwnerDecision=True
        self.release();value=self.verify(hold=self.hold)
        self.assertEqual(value['origin'],shot.SYNTHETIC);self.assertFalse(value['engineAllowed'])
    def test_partial_original_manifest_keeps_refusal_and_interruption(self):
        launch.write_original(self.path/'preflight-refusal.log',b'first attempt refused')
        first=shot.inventory(self.path)
        launch.write_original(self.path/'interruption.log',b'owner-controlled interrupted observation; no engine')
        later=shot.inventory(self.path)
        self.assertTrue(first.exists());self.assertTrue(later.exists());self.assertNotEqual(first,later)
        self.assertEqual(len(auth.decode(later.read_bytes())['payloads']),2)
    def test_duplicate_run_specific_release_is_ambiguous_refused(self):
        self.release();other=deepcopy(self.f.release);other['id']=13;self.f.extra=[other]
        with self.assertRaises(ValueError):self.verify(hold=self.hold)
    def test_altered_hold_is_not_authenticated_by_its_own_hash(self):
        self.release();self.hold['ownerBodySha256']='a'*64
        with self.assertRaises(ValueError):self.verify(hold=self.hold)
    def test_owner_wait_does_not_treat_old_hold_as_a_fresh_authority_token(self):
        self.release();value=self.verify(hold=self.hold,now=NOW+timedelta(minutes=5))
        self.assertFalse(value['engineAllowed']);self.assertEqual(value['stage'],'RUN_SPECIFIC_RELEASE_VERIFIED')
    def test_expiry_after_original_does_not_become_new_execution_permission(self):
        self.release()
        with self.assertRaises(ValueError):self.verify(hold=self.hold,now=NOW+timedelta(hours=1))
    def test_original_evidence_write_is_idempotent_only_for_identical_bytes(self):
        p=self.path/'failure.log';launch.write_original(p,b'first failure');launch.write_original(p,b'first failure')
        with self.assertRaises(ValueError):launch.write_original(p,b'replacement')
        self.assertEqual(p.read_bytes(),b'first failure')
    def test_interrupted_preflight_has_no_original_game_trace(self):
        launch.write_original(self.path/'verifier.log',b'rejected before hold')
        shot.inventory(self.path);self.assertFalse((self.path/'original.jsonl').exists());self.assertFalse((self.path/'timing.jsonl').exists())
    def test_case_race_does_not_match_pinned_inputs(self):
        self.f.request['caseBytesBase64']=base64.b64encode(encoded(self.f.case)+b' ').decode()
        with self.assertRaises(ValueError):self.verify()
    def test_cli_absent_request_denies_without_live_network_or_engine(self):
        env=os.environ.copy();env.pop('ARGENTUM_SUCCESSOR_REQUEST_JSON',None)
        p=subprocess.run([sys.executable,'-I',str(ROOT/'scripts/play-first/one_shot_launch.py'),'preflight'],env=env,capture_output=True)
        self.assertEqual(p.returncode,78);self.assertIn(b'REFUSED',p.stderr)
    def test_environment_activation_flag_is_never_a_trust_root(self):
        env=os.environ.copy();env.update(ARGENTUM_SUCCESSOR_REQUEST_JSON='{}',ARGENTUM_GAMEPLAY_AUTHORIZED='true',ARGENTUM_ENVIRONMENT_PROTECTED='true')
        p=subprocess.run([sys.executable,'-I',str(ROOT/'scripts/play-first/one_shot_launch.py'),'gate'],env=env,capture_output=True)
        self.assertEqual(p.returncode,78)
    def test_new_adapter_gate_precedes_every_original_slot_and_initializer(self):
        text=(ROOT/launch.ADAPTER).read_text();gate=text.index('check(gate.exitValue() == 0)')
        for marker in ('PlayFirstTimingJournal(', 'Files.writeString(trace','CardRegistry()', 'GameEnvironment.create(', 'env.reset('):self.assertLess(gate,text.index(marker))
        self.assertIn('OneShotGateCommand.build(',text)
        self.assertIn('"python3", "-I", launcher.toString(), "gate", "--root", root.toString()',text)
        self.assertIn('protectionDomain.codeSource.location.toURI()',text)
        self.assertIn('Files.readAllBytes(launcher).contentEquals(git("show", "HEAD:$relative"))',text)
        self.assertIn(shot.PARENT,text);self.assertNotIn('runtime_integration.py',text)
    def test_legacy_adapter_and_qualification_default_deny_unchanged(self):
        path='gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt'
        original=subprocess.check_output(['git','show',shot.PARENT+':'+path],cwd=ROOT)
        self.assertEqual((ROOT/path).read_bytes(),original)
        env=os.environ.copy();env.pop('ARGENTUM_SUCCESSOR_REQUEST_JSON',None)
        p=subprocess.run([sys.executable,'-I',str(ROOT/'scripts/play-first/runtime_integration.py'),'verify-release'],env=env,capture_output=True)
        self.assertNotEqual(p.returncode,0)
    def test_workflow_only_game_step_has_ack_and_fresh_adapter_gate(self):
        wf=json.loads((ROOT/shot.WORKFLOW).read_text());pre=wf['jobs']['preflight'];game=wf['jobs']['game']
        self.assertNotIn('PLAY_FIRST_ONE_SHOT_ACK',json.dumps(pre));self.assertNotIn('PLAY_FIRST_ONE_SHOT_ACK',game['env'])
        steps=game['steps'];engine=[s for s in steps if 'just test-class' in s.get('run','')]
        self.assertEqual(len(engine),1);self.assertIn('PLAY_FIRST_ONE_SHOT_ACK',engine[0]['env'])
        self.assertIn('PlayFirstOneShotPestMonsterTronTest',engine[0]['run'])
        final=next(i for i,s in enumerate(steps) if 'one_shot_launch.py final' in s.get('run',''))
        self.assertLess(final,steps.index(engine[0]));self.assertIn('environment',game)
    def test_exact_checkout_refusal_precedes_any_owner_api_read(self):
        def denied(frozen):raise ValueError('synthetic exact source mismatch')
        with self.assertRaises(ValueError):
            launch.verify(encoded(self.f.request),ROOT,context=self.f.context,_client=self.f,_checkout=denied,_now=NOW)
        self.assertEqual(self.f.calls,[])
    def test_unavailable_live_api_never_falls_back_to_self_declared_approval(self):
        def unavailable(path):raise urllib.error.URLError('synthetic unavailable backend')
        self.f.get=unavailable
        with self.assertRaises(urllib.error.URLError):self.verify()
    def test_tracked_source_manifest_bytes_are_checked_without_engine_execution(self):
        repo=self.path/'identity-fixture';repo.mkdir()
        for name in ('mtg-sdk/identity-fixture.txt','gym/src/main/kotlin/com/wingedsheep/gym/matchup/identity-fixture.txt','ai/src/main/identity-fixture.txt'):
            p=repo/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text('UNAUTHENTICATED SYNTHETIC SOURCE IDENTITY FIXTURE; NOT ENGINE CODE\n')
        env=os.environ.copy();env.update(GIT_AUTHOR_NAME='Synthetic Excluded Fixture',GIT_AUTHOR_EMAIL='fixture@example.invalid',GIT_COMMITTER_NAME='Synthetic Excluded Fixture',GIT_COMMITTER_EMAIL='fixture@example.invalid')
        for args in (['init','-q'],['add','.'],['commit','-q','-m','Synthetic identity fixture, not a provider source']):subprocess.run(['git',*args],cwd=repo,env=env,check=True,capture_output=True)
        value=launch.source_manifests(repo);self.assertEqual(set(value),{'engineSha256','deckSha256','pilotSha256'})
        self.assertEqual(value,launch.source_manifests(repo))
        (repo/'mtg-sdk/identity-fixture.txt').write_text('ALTERED SYNTHETIC SOURCE')
        with self.assertRaises(ValueError):launch.source_manifests(repo)

def case_mutation(name,key,value):
    def test(self):
        self.f.case['case'][key]=value
        self.f.request['caseBytesBase64']=base64.b64encode(encoded(self.f.case)).decode()
        with self.assertRaises((ValueError,TypeError)):self.verify()
    setattr(OneShotLaunchTests,'test_case_'+name,test)
for name,key,value in [('b01','blockId','PLAY_FIRST_B01'),('g08','gameId','G08'),('official_scope','scope','OFFICIAL'),
    ('wrong_seat','pestSeat',1),('boolean_seat','pestSeat',False),('multiple_games','intendedBlockGames',2),
    ('wrong_source','sourceCommit','a'*40),('historical_seed','fixtureSeedHex','0x504c415946495253'),
    ('zero_input','fixtureSeedHex','0x0000000000000000'),('missing_seed','fixtureSeedHex',None)]:case_mutation(name,key,value)

if __name__=='__main__':unittest.main()
