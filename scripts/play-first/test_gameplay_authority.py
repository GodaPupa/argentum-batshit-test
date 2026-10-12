"""EXCLUDED synthetic trusted-code fixtures; NEVER real issuer/host provenance.
Reuse existing fake labels. No seed allocation, key, signer, network or engine.
These fixtures exercise contracts, not implementation of a trusted service.
"""
import copy
from datetime import datetime,timedelta,timezone
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import gameplay_authority as AUTH

NOW=datetime(2000,1,1,0,0,30,tzinfo=timezone.utc)
ORIGIN='SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED'

def synthetic_comment(ident,contract,claims,created):
    return dict(id=ident,user={'id':AUTH.OWNER_ID,'login':AUTH.OWNER_LOGIN},author_association='OWNER',
                issue_url='https://api.github.com/repos/'+AUTH.REPOSITORY+'/issues/231',
                html_url='https://github.com/'+AUTH.REPOSITORY+'/pull/231#issuecomment-'+str(ident),
                body=contract+'\n'+json.dumps(dict(claims,expiresAt='2000-01-01T00:01:30Z'),sort_keys=True),
                created_at=created,updated_at=created)

class SyntheticTrustedGitHub:
    """In-memory transport ONLY; no actual owner identity authentication."""
    def __init__(self,data,run):
        h=AUTH.sha(data);self.run=copy.deepcopy(run)
        self.decision=synthetic_comment(1,AUTH.OWNER_CONTRACT,AUTH.owner_expected(h),'2000-01-01T00:00:00Z')
        self.release=synthetic_comment(2,AUTH.RELEASE_CONTRACT,AUTH.release_expected(h,1,run),'2000-01-01T00:00:20Z')
        self.timeline=[self.decision,self.release];self.calls=[]
        self.actual_run=dict(id=int(run['runId']),head_sha=run['validationCommit'],head_branch=run['ref'].removeprefix('refs/heads/'),event='push',run_attempt=1,run_number=1,path=run['workflowPath'],repository={'full_name':AUTH.REPOSITORY},actor={'id':AUTH.OWNER_ID},status='in_progress')
    def get(self,path):
        self.calls.append(path)
        if path=='':return {'owner':{'id':AUTH.OWNER_ID,'login':AUTH.OWNER_LOGIN}}
        if path=='issues/comments/1':return copy.deepcopy(self.decision)
        if path=='issues/comments/2':return copy.deepcopy(self.release)
        if path.startswith('issues/231/comments?'):return copy.deepcopy(self.timeline)
        if path.startswith(('pulls/231/comments?','pulls/231/reviews?')):return []
        if path=='git/commits/'+self.run['sourceCommit']:return dict(sha=self.run['sourceCommit'],tree={'sha':self.run['sourceTree']},parents=[{'sha':AUTH.SOURCE_PARENT}])
        if path=='git/commits/'+self.run['validationCommit']:return dict(sha=self.run['validationCommit'],tree={'sha':self.run['validationTree']},parents=[{'sha':self.run['sourceCommit']}])
        if path.startswith('contents/'):return {'sha':self.run['workflowBlob']}
        if path=='actions/runs/'+self.run['runId']:return copy.deepcopy(self.actual_run)
        raise AssertionError('Unplanned synthetic request; NEVER network fallback: '+path)

class SyntheticTrustedAnchor:
    """Normative fake service facts, independent of documents passed to verify.
    Mutable attributes let tests corrupt service facts intentionally. This is
    NOT TLS, signed time, real host attestation or a cross-host atomic ledger.
    """
    origin=ORIGIN
    def __init__(self,data,run,client):
        self.client=client;h=AUTH.sha(data)
        self.hold=dict(intentSha256=h,run=copy.deepcopy(run),observedAt='2000-01-01T00:00:10Z',engineStarted=False,originalSlotCreated=False)
        self.facts=dict(intentSha256=h,run=copy.deepcopy(run),owner=client.decision,release=client.release,history=client.timeline,historyCompleteThrough='2000-01-01T00:00:30Z',executedAt='2000-01-01T00:00:30Z',hold=self.hold,sourceVerified=True,manifestSha256='8'*64,originalConsumption=dict(intentSha256=h,runId=run['runId'],executedAt='2000-01-01T00:00:30Z',count=1))
        self.consumed=False;self.reservations=0
    @property
    def decision(self):return self.client.decision
    def authenticate_historical(self,reference):
        if reference!='EXCLUDED_SYNTHETIC_ANCHOR_REFERENCE':raise ValueError('Unknown/forged historical provenance reference')
        return copy.deepcopy(self.facts)
    def authenticate_hold(self,reference):
        if reference!='EXCLUDED_SYNTHETIC_HOLD_REFERENCE':raise ValueError('Unknown/forged hold provenance reference')
        return copy.deepcopy(self.hold)
    def reserve_original(self,h,run,owner,release,now):
        if self.consumed:raise ValueError('Original already consumed; cross-host enforcement NOT qualified')
        self.consumed=True;self.reservations+=1
        return dict(intentSha256=h,run=copy.deepcopy(run),originalCount=1,anchorReady=True,reservedAt=now.strftime('%Y-%m-%dT%H:%M:%SZ'))

def synthetic_trusted_fixture(data,run):
    client=SyntheticTrustedGitHub(data,run);anchor=SyntheticTrustedAnchor(data,run,client)
    record=dict(contract=AUTH.CONTRACT,intentSha256=AUTH.sha(data),ownerDecisionId=1,releaseDecisionId=2,anchorRef='EXCLUDED_SYNTHETIC_ANCHOR_REFERENCE')
    return record,anchor,NOW

class StagedAndHistoricalAuthorityTests(unittest.TestCase):
    def setUp(self):
        from test_strict_receiving import fixture
        raw,pins=fixture();self.data=raw['approved-case.json'];self.run=pins['run']
        self.record,self.anchor,self.now=synthetic_trusted_fixture(self.data,self.run);self.client=self.anchor.client
        self.release_record=dict(contract=AUTH.CONTRACT,intentSha256=AUTH.sha(self.data),ownerDecisionId=1,releaseDecisionId=2,holdRef='EXCLUDED_SYNTHETIC_HOLD_REFERENCE')
    def historical(self,**kw):
        return AUTH.verify_historical(self.data,self.run,self.record,'8'*64,_anchor=self.anchor,**kw)
    def execute(self,**kw):
        return AUTH.verify_release(self.data,self.run,self.release_record,_trusted=self.anchor,_client=self.client,_now=self.now,**kw)
    def claim(self,comment,key,value):
        contract,body=comment['body'].split('\n',1);claims=json.loads(body);claims[key]=value;comment['body']=contract+'\n'+json.dumps(claims)
    def event(self,when,body='REVOKE'):
        return dict(id=3,user={'id':AUTH.OWNER_ID},body=body,updated_at=when)
    def test_synthetic_historical_positive_is_correspondence_only(self):
        p=self.historical();self.assertEqual(p['provenance']['origin'],ORIGIN);self.assertFalse(p['engineAllowed']);self.assertEqual(p['stage'],'HISTORICAL_AT_EXECUTION_CORRESPONDENCE')
    def test_synthetic_release_positive_is_not_genuine_owner_or_host_approval(self):
        p=self.execute();self.assertEqual(p['provenance']['origin'],ORIGIN);self.assertEqual(p['stage'],'RUN_SPECIFIC_RELEASE_VERIFIED')
    def test_missing_historical_backend_rejects_even_correct_document(self):
        with self.assertRaisesRegex(ValueError,'UNQUALIFIED'):AUTH.verify_historical(self.data,self.run,self.record,'8'*64)
    def test_missing_release_backend_rejects_before_live_transport(self):
        with patch.object(AUTH.LiveGitHub,'get',side_effect=AssertionError('No network')):
            with self.assertRaisesRegex(ValueError,'UNQUALIFIED'):AUTH.verify_release(self.data,self.run,self.release_record)
    def test_missing_approval_record_fails_closed(self):
        for r in (None,{}, {'executionAuthorized':True,'externalVerified':True}):
            with self.subTest(record=r),self.assertRaises(ValueError):AUTH.verify_historical(self.data,self.run,r,'8'*64,_anchor=self.anchor)
    def test_unsigned_snapshot_cannot_be_used_as_trusted_backend(self):
        with self.assertRaises(AttributeError):AUTH.verify_historical(self.data,self.run,self.record,'8'*64,_anchor=self.anchor.facts)
    def test_forged_anchor_reference_rejected(self):
        self.record['anchorRef']='SELF_ASSERTED';self.assertRaises(ValueError,self.historical)
    def test_self_declared_execution_time_rejected(self):
        self.record['executedAt']='2000-01-01T00:00:30Z';self.assertRaises(ValueError,self.historical)
    def test_expired_approval_rejected_for_new_execution(self):
        self.now+=timedelta(days=1);self.assertRaises(ValueError,self.execute)
    def test_owner_expiry_rejected_while_hold_still_fresh(self):
        self.claim(self.client.decision,'expiresAt','2000-01-01T00:00:29Z');self.assertRaises(ValueError,self.execute)
    def test_release_expiry_rejected_while_hold_still_fresh(self):
        self.claim(self.client.release,'expiresAt','2000-01-01T00:00:29Z');self.assertRaises(ValueError,self.execute)
    def test_historical_after_expiry_never_demands_current_time_or_live_api(self):
        with patch.object(AUTH,'datetime') as clock,patch.object(AUTH.LiveGitHub,'get',side_effect=AssertionError('No current API')):
            clock.strptime=datetime.strptime
            p=self.historical();self.assertEqual(p['executedAt'],'2000-01-01T00:00:30Z');clock.now.assert_not_called()
    def test_historical_later_nonrevoking_receipt_does_not_invalidate(self):
        self.anchor.facts['history'].append(self.event('2000-01-02T00:00:00Z','LATER RECEIPT'))
        self.assertEqual(self.historical()['stage'],'HISTORICAL_AT_EXECUTION_CORRESPONDENCE')
    def test_historical_later_consumption_does_not_revoke_original(self):
        self.anchor.facts['history'].append(self.event('2000-01-01T00:00:31Z','ORIGINAL CONSUMED'))
        self.assertFalse(self.historical()['engineAllowed'])
    def test_historical_later_revocation_cannot_rewrite_original_time(self):
        self.anchor.facts['history'].append(self.event('2000-01-01T00:00:31Z','REVOKE FUTURE EXECUTION'))
        self.assertFalse(self.historical()['engineAllowed'])
    def test_live_execution_cannot_ignore_owner_event_newer_than_clock(self):
        self.client.timeline.append(self.event('2000-01-01T00:00:31Z','REVOKED'))
        self.assertRaises(ValueError,self.execute)
    def test_preexecution_owner_revocation_rejected_in_both_paths(self):
        self.anchor.facts['history'].append(self.event('2000-01-01T00:00:25Z'))
        self.assertRaises(ValueError,self.historical);self.assertRaises(ValueError,self.execute)
    def test_preexecution_consumption_rejected(self):
        self.anchor.facts['history'].append(self.event('2000-01-01T00:00:25Z','CONSUMED ORIGINAL'))
        self.assertRaises(ValueError,self.historical)
    def test_changed_decision_version_before_execution_rejected(self):
        changed=copy.deepcopy(self.client.decision)
        changed.update(body='REVOKED BEFORE EXECUTION',updated_at='2000-01-01T00:00:25Z')
        self.anchor.facts['history'].append(changed)
        self.assertRaises(ValueError,self.historical)
    def test_later_decision_edit_does_not_rewrite_execution_time_version(self):
        changed=copy.deepcopy(self.client.decision)
        changed.update(body='LATER OWNER NOTE',updated_at='2000-01-01T00:00:31Z')
        self.anchor.facts['history'].append(changed)
        self.assertFalse(self.historical()['engineAllowed'])
    def test_authenticated_time_outside_approval_interval_rejected(self):
        self.anchor.facts['executedAt']='2000-01-02T00:00:00Z';self.assertRaises(ValueError,self.historical)
    def test_incomplete_execution_time_history_rejected(self):
        self.anchor.facts['historyCompleteThrough']='2000-01-01T00:00:29Z';self.assertRaises(ValueError,self.historical)
    def test_incomplete_anchor_attestation_rejected(self):
        del self.anchor.facts['sourceVerified'];self.assertRaises(ValueError,self.historical)
    def test_anchor_source_not_verified_rejected(self):
        self.anchor.facts['sourceVerified']=False;self.assertRaises(ValueError,self.historical)
    def test_anchor_binding_mismatches_rejected(self):
        for k,v in [('intentSha256','0'*64),('manifestSha256','0'*64),('run',dict(self.run,runId='2'))]:
            old=self.anchor.facts[k];self.anchor.facts[k]=v
            with self.subTest(key=k),self.assertRaises(ValueError):self.historical()
            self.anchor.facts[k]=old
    def test_replayed_or_cross_bound_execution_anchor_rejected(self):
        for k,v in [('count',2),('count',True),('runId','2'),('intentSha256','0'*64),('executedAt','2000-01-01T00:00:29Z')]:
            old=self.anchor.facts['originalConsumption'][k];self.anchor.facts['originalConsumption'][k]=v
            with self.subTest(key=k),self.assertRaises(ValueError):self.historical()
            self.anchor.facts['originalConsumption'][k]=old
    def test_same_original_historical_reinspection_is_readonly(self):
        self.assertEqual(self.historical(),self.historical());self.assertEqual(self.anchor.reservations,0)
    def test_owner_comment_id_not_embedded_in_immutable_intent(self):
        intent=json.loads(self.data);self.assertNotIn('authorizationComment',intent['case']);self.assertNotIn('decisionId',intent['case']);self.assertNotIn('runId',intent['plan'])
        self.assertEqual(AUTH.freeze_intent(self.data)['intentSha256'],AUTH.sha(self.data))
    def test_owner_body_has_no_self_id_or_future_run_dependency(self):
        claims=json.loads(self.client.decision['body'].split('\n',1)[1]);self.assertNotIn('decisionId',claims);self.assertNotIn('run',claims)
    def test_prelaunch_freeze_does_not_require_run_id(self):
        p=AUTH.freeze_intent(self.data);self.assertEqual(p['stage'],'INTENT_FROZEN');self.assertFalse(p['engineAllowed'])
    def test_owner_decision_stage_is_nonexecuting(self):
        self.client.timeline=[self.client.decision]
        p=AUTH.verify_owner_decision(self.data,1,_client=self.client,_now=self.now)
        self.assertFalse(p['engineAllowed']);self.assertEqual(p['origin'],ORIGIN)
        self.assertFalse(any(path.startswith('actions/') for path in self.client.calls))
    def test_owner_intent_stage_checks_exact_source_without_run_id(self):
        self.client.timeline=[self.client.decision]
        original=self.client.get
        def read(path):
            value=original(path)
            if path=='git/commits/'+self.run['sourceCommit']:value['tree']['sha']='0'*40
            return value
        with patch.object(self.client,'get',side_effect=read),self.assertRaises(ValueError):
            AUTH.verify_owner_decision(self.data,1,_client=self.client,_now=self.now)
    def test_release_is_impossible_without_assigned_provider_run(self):
        run=dict(self.run);del run['runId']
        with self.assertRaises(ValueError):AUTH.verify_release(self.data,run,self.release_record,_trusted=self.anchor,_client=self.client,_now=self.now)
    def test_future_provider_ids_in_intent_rejected(self):
        for area,key in [('case','authorizationComment'),('plan','runId')]:
            intent=json.loads(self.data);intent[area][key]=1
            with self.subTest(area=area),self.assertRaises(ValueError):AUTH.freeze_intent(json.dumps(intent).encode())
    def test_substituted_intent_bytes_rejected(self):
        with self.assertRaises(ValueError):AUTH.verify_historical(self.data+b' ',self.run,self.record,'8'*64,_anchor=self.anchor)
    def test_cross_bound_run_or_plan_rejected(self):
        for key,value in [('runAttempt',True),('runNumber',2),('sourceParent','0'*40),('sourceTree','0'*40),('ref','refs/heads/WRONG')]:
            run=dict(self.run);run[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):AUTH.verify_historical(self.data,run,self.record,'8'*64,_anchor=self.anchor)
    def test_non_gameplay_comments_rejected_without_denylist(self):
        self.client.decision['body']='AUTHORIZE SOURCE/QUALIFICATION ONLY. NO GAMEPLAY.';self.assertRaises(ValueError,self.historical);self.assertRaises(ValueError,self.execute)
    def test_non_gameplay_release_rejected(self):
        self.client.release['body']='NON-GAMEPLAY HOLD ONLY';self.assertRaises(ValueError,self.execute)
    def test_forged_actual_owner_issuer_rejected(self):
        self.client.decision['user']['id']=1;self.assertRaises(ValueError,self.historical)
    def test_wrong_pr_or_owner_association_rejected(self):
        self.client.release['author_association']='CONTRIBUTOR';self.assertRaises(ValueError,self.execute)
    def test_scope_original_policy_and_case_hash_mismatches_rejected(self):
        for key,value in [('scope','NON_GAMEPLAY_ONLY'),('originalOnly',1),('maxGames',True),('maxGames',2),('intentSha256','0'*64)]:
            old=self.client.release['body'];self.claim(self.client.release,key,value)
            with self.subTest(key=key),self.assertRaises(ValueError):self.execute()
            self.client.release['body']=old
    def test_historical_b01_g08_and_old_seed_labels_rejected(self):
        for key,value in [('blockId','PLAY_FIRST_B01'),('gameId','G08'),('fixtureSeedHex','0x504c415946495253')]:
            intent=json.loads(self.data);intent['case'][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):AUTH.freeze_intent(json.dumps(intent).encode())
    def test_missing_or_forged_hold_rejected(self):
        self.release_record['holdRef']='FORGED';self.assertRaises(ValueError,self.execute)
    def test_hold_cannot_initialize_engine_or_create_original_slot(self):
        for key in ('engineStarted','originalSlotCreated'):
            self.anchor.hold[key]=True
            with self.subTest(key=key),self.assertRaises(ValueError):self.execute()
            self.anchor.hold[key]=False
    def test_hold_wrong_head_ref_or_intent_rejected(self):
        self.anchor.hold['run']['ref']='refs/heads/WRONG';self.assertRaises(ValueError,self.execute)
    def test_case_game_and_source_identity_mismatches_rejected(self):
        for key,value in [('gameId','G998'),('sourceCommit','0'*40),('scope','OFFICIAL')]:
            intent=json.loads(self.data);intent['case'][key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):
                AUTH.verify_historical(json.dumps(intent).encode(),self.run,self.record,'8'*64,_anchor=self.anchor)
    def test_stale_release_rejected_even_before_expiry(self):
        self.client.release['updated_at']='2000-01-01T00:00:20Z';self.now+=timedelta(seconds=51)
        self.anchor.hold['observedAt']='2000-01-01T00:00:10Z';self.assertRaises(ValueError,self.execute)
    def test_interrupted_stage_order_rejected(self):
        self.anchor.hold['observedAt']='2000-01-01T00:00:25Z';self.assertRaises(ValueError,self.execute)
    def test_incomplete_hold_attestation_rejected(self):
        del self.anchor.hold['originalSlotCreated'];self.assertRaises(ValueError,self.execute)
    def test_original_already_consumed_rejected(self):
        self.anchor.consumed=True;self.assertRaises(ValueError,self.execute)
    def test_synthetic_singleuse_contract_rejects_second_release(self):
        self.execute();self.assertRaises(ValueError,self.execute);self.assertEqual(self.anchor.reservations,1)
    def test_incomplete_atomic_reservation_rejected(self):
        with patch.object(self.anchor,'reserve_original',return_value={'originalCount':1}):self.assertRaises(ValueError,self.execute)
    def test_actual_retry_or_completed_run_rejected(self):
        for key,value in [('run_attempt',2),('run_number',True),('status','completed'),('head_sha','0'*40)]:
            old=self.client.actual_run[key];self.client.actual_run[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):self.execute()
            self.client.actual_run[key]=old
    def test_missing_history_rejected(self):
        self.anchor.facts['history']=[];self.assertRaises(ValueError,self.historical)
    def test_inline_live_revocation_searched(self):
        original=self.client.get
        def read(path):
            if path.startswith('pulls/231/comments?'):return [self.event('2000-01-01T00:00:25Z')]
            return original(path)
        with patch.object(self.client,'get',side_effect=read):self.assertRaises(ValueError,self.execute)
    def test_unavailable_live_transport_fails_closed(self):
        with patch.object(self.client,'get',side_effect=OSError('EXCLUDED UNAVAILABLE')):self.assertRaises(OSError,self.execute)
    def test_live_decision_change_during_read_rejected(self):
        original=self.client.get;count=0
        def read(path):
            nonlocal count
            result=original(path)
            if path=='issues/comments/2':
                count+=1
                if count>1:result['body']='REVOKED'
            return result
        with patch.object(self.client,'get',side_effect=read):self.assertRaises(ValueError,self.execute)
    def test_duplicate_and_nonfinite_intent_json_rejected(self):
        for raw in (b'{"contract":"x","contract":"y"}',b'{"contract":NaN}'):
            with self.subTest(raw=raw),self.assertRaises(ValueError):AUTH.freeze_intent(raw)
    def test_cli_environment_and_unsigned_document_cannot_enable_execution(self):
        import os
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'case').write_bytes(self.data);(root/'run').write_text(json.dumps(self.run));(root/'record').write_text(json.dumps(self.record))
            env=dict(os.environ,PLAY_FIRST_TRUSTED_APPROVAL='true',PLAY_FIRST_EXECUTION_AUTHORIZED='true')
            p=subprocess.run([sys.executable,'-I',AUTH.__file__,'verify-historical','--case',str(root/'case'),'--run-context',str(root/'run'),'--record',str(root/'record'),'--manifest-sha256','8'*64],env=env,capture_output=True,text=True,timeout=10)
            self.assertEqual(p.returncode,1);self.assertIn('UNQUALIFIED',p.stdout)
    def test_cli_has_no_external_service_injection_option(self):
        p=subprocess.run([sys.executable,'-I',AUTH.__file__,'--help'],capture_output=True,text=True,timeout=10)
        self.assertEqual(p.returncode,0)
        for key in ('--anchor','--trusted','--client','--fixture','--host'):self.assertNotIn(key,p.stdout)
    def test_local_checkout_not_authenticated_by_environment(self):
        with patch.object(AUTH.subprocess,'check_output',return_value='unapproved\n'):self.assertRaises(ValueError,AUTH.verify_local_execution_source,self.run)
    def test_record_cannot_choose_live_transport_host(self):
        for path in ('https://attacker.invalid','../issues/231','//attacker.invalid#fragment'):
            with self.subTest(path=path),self.assertRaises(ValueError):AUTH.LiveGitHub().get(path)
    def test_synthetic_origin_cannot_be_promoted_by_document(self):
        self.record['origin']='TRUSTED_EXTERNAL_EXECUTION_TIME_ANCHOR';self.assertRaises(ValueError,self.historical)

if __name__=='__main__':unittest.main()
