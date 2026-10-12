"""EXCLUDED in-memory trusted-transport fixtures, NEVER real owner approvals.

Reuses existing synthetic case/run labels. No allocated seed, new game identity,
signing key, network request, engine initializer or adapter invocation.
"""
import copy
from datetime import datetime, timedelta, timezone
import hashlib
import json
import unittest
from unittest.mock import patch
import gameplay_authority as AUTH

NOW = datetime(2000, 1, 1, 0, 0, 30, tzinfo=timezone.utc)

class SyntheticTrustedGitHub:
    """Explicit trusted fixture seam; this is not issuer authentication."""
    def __init__(self, case_bytes, run):
        case = json.loads(case_bytes)
        claims = dict(contract=AUTH.CONTRACT, decisionId=case['authorizationComment'],
                      issuerId=AUTH.OWNER_ID, issuerLogin=AUTH.OWNER_LOGIN,
                      caseSha256=hashlib.sha256(case_bytes).hexdigest(), blockId=case['blockId'],
                      gameId=case['gameId'], scope=AUTH.SCOPE, executionAuthorized=True,
                      originalOnly=True, maxGames=1, run=copy.deepcopy(run), expiresAt='2000-01-01T00:01:30Z')
        self.decision = dict(id=case['authorizationComment'], user={'id':AUTH.OWNER_ID,'login':AUTH.OWNER_LOGIN},
                             author_association='OWNER', issue_url='https://api.github.com/repos/'+AUTH.REPOSITORY+'/issues/231',
                             html_url='https://github.com/'+AUTH.REPOSITORY+'/pull/231#issuecomment-'+str(case['authorizationComment']),
                             body=AUTH.CONTRACT+'\n'+json.dumps(claims, sort_keys=True),
                             created_at='2000-01-01T00:00:00Z', updated_at='2000-01-01T00:00:00Z')
        self.run = copy.deepcopy(run)
        self.timeline = [self.decision]
        self.actual_run = dict(id=int(run['runId']),head_sha=run['validationCommit'],
                               head_branch=run['ref'].removeprefix('refs/heads/'),event='push',run_attempt=1,run_number=1,
                               path=run['workflowPath'],repository={'full_name':AUTH.REPOSITORY},
                               actor={'id':AUTH.OWNER_ID},status='in_progress')
        self.calls=[]
    def get(self, path):
        self.calls.append(path)
        if path=='':return {'owner':{'id':AUTH.OWNER_ID,'login':AUTH.OWNER_LOGIN}}
        if path.startswith('issues/comments/'):return copy.deepcopy(self.decision)
        if path.startswith('issues/231/comments?'):return copy.deepcopy(self.timeline)
        if path.startswith(('pulls/231/comments?','pulls/231/reviews?')):return []
        if path=='git/commits/'+self.run['sourceCommit']:
            return dict(sha=self.run['sourceCommit'],tree={'sha':self.run['sourceTree']},parents=[{'sha':AUTH.SOURCE_PARENT}])
        if path=='git/commits/'+self.run['validationCommit']:
            return dict(sha=self.run['validationCommit'],tree={'sha':self.run['validationTree']},parents=[{'sha':self.run['sourceCommit']}])
        if path.startswith('contents/'):return {'sha':self.run['workflowBlob']}
        if path=='actions/runs/'+self.run['runId']:return copy.deepcopy(self.actual_run)
        raise AssertionError('Unplanned synthetic API request; no network fallback: '+path)

def synthetic_trusted_fixture(case_bytes, run):
    client=SyntheticTrustedGitHub(case_bytes,run)
    record=AUTH.verify_live(case_bytes,run,_client=client,_now=NOW)
    return record,client,NOW

class PositiveGameplayAuthorityTests(unittest.TestCase):
    def setUp(self):
        from test_strict_receiving import fixture
        self.raw,self.pins=fixture()
        self.case=self.raw['approved-case.json'];self.run=self.pins['run']
        self.record,self.client,self.now=synthetic_trusted_fixture(self.case,self.run)
    def verify(self, record=None, **kwargs):
        return AUTH.verify_record(self.case,self.run,self.record if record is None else record,
                                  _client=self.client,_now=self.now,**kwargs)
    def change_live_claim(self,key,value):
        claims=json.loads(self.client.decision['body'].split('\n',1)[1]);claims[key]=value
        self.client.decision['body']=AUTH.CONTRACT+'\n'+json.dumps(claims,sort_keys=True)
    def test_synthetic_positive_is_never_authenticated_owner_approval(self):
        proof=self.verify()
        self.assertEqual(proof['provenance']['origin'],'SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED')
        self.assertEqual(proof['claims']['caseSha256'],hashlib.sha256(self.case).hexdigest())
    def test_missing_record_fails_before_transport(self):
        self.client.calls=[]
        with self.assertRaises(ValueError):AUTH.verify_record(self.case,self.run,None,_client=self.client,_now=self.now)
        self.assertEqual(self.client.calls,[])
    def test_self_asserted_boolean_cannot_authenticate(self):
        with self.assertRaises(ValueError):self.verify({'executionAuthorized':True,'externalVerified':True})
    def test_non_gameplay_owner_comment_rejected(self):
        self.client.decision['body']='ARGENTUM — AUTHORIZE SOURCE REPAIR AND EXCLUDED LOCAL QUALIFICATION ONLY. NO GAMEPLAY.'
        with self.assertRaisesRegex(ValueError,'no positive gameplay'):self.verify()
    def test_four_historical_and_new_construction_comment_contents_rejected(self):
        for comment in (6081552107,6084057209,6084643413,6086416012,6126409033,6129783458,6129923079):
            with self.subTest(comment=comment):
                case=json.loads(self.case);case['authorizationComment']=comment;data=json.dumps(case).encode()
                record,client,now=synthetic_trusted_fixture(data,self.run)
                client.decision['body']='EXCLUDED CONSTRUCTION/NON-GAMEPLAY DECISION'
                with self.assertRaises(ValueError):AUTH.verify_record(data,self.run,record,_client=client,_now=now)
    def test_forged_actual_issuer_rejected(self):
        self.client.decision['user']['id']=1
        with self.assertRaisesRegex(ValueError,'issuer'):self.verify()
    def test_owner_association_is_required(self):
        self.client.decision['author_association']='CONTRIBUTOR'
        with self.assertRaises(ValueError):self.verify()
    def test_decision_must_be_on_controlling_pr(self):
        self.client.decision['issue_url']=self.client.decision['issue_url'].replace('/231','/230')
        with self.assertRaises(ValueError):self.verify()
    def test_case_bytes_cannot_authenticate_themselves(self):
        with self.assertRaises(ValueError):AUTH.verify_record(self.case+b' ',self.run,self.record,_client=self.client,_now=self.now)
    def test_forged_provenance_rejected(self):
        self.record['provenance']['decisionUrl']='https://attacker.invalid/claims'
        with self.assertRaisesRegex(ValueError,'provenance'):self.verify()
    def test_stale_external_record_rejected(self):
        self.record['provenance']['verifiedAt']='1999-12-31T23:59:00Z'
        with self.assertRaisesRegex(ValueError,'stale'):self.verify()
    def test_future_external_record_rejected(self):
        self.record['provenance']['verifiedAt']='2000-01-01T00:00:31Z'
        with self.assertRaises(ValueError):self.verify()
    def test_expired_live_decision_rejected(self):
        self.change_live_claim('expiresAt','2000-01-01T00:00:29Z')
        with self.assertRaisesRegex(ValueError,'stale'):self.verify()
    def test_revocation_and_any_superseding_owner_record_rejected(self):
        for body in ('REVOKE GAMEPLAY', 'ARGENTUM_GAMEPLAY_REVOCATION_V1', 'Original case consumed', 'New controller disposition'):
            self.client.timeline=[self.client.decision,dict(id=2,user={'id':AUTH.OWNER_ID},body=body,updated_at='2000-01-01T00:00:10Z')]
            with self.subTest(body=body),self.assertRaisesRegex(ValueError,'superseding'):self.verify()
    def test_missing_discussion_cannot_prove_no_revocation(self):
        self.client.timeline=[]
        with self.assertRaises(ValueError):self.verify()
    def test_actual_run_retry_rejected(self):
        self.client.actual_run['run_attempt']=2
        with self.assertRaises(ValueError):self.verify()
    def test_actual_run_source_or_workflow_mismatch_rejected(self):
        self.client.actual_run['head_sha']='0'*40
        with self.assertRaises(ValueError):self.verify()
    def test_completed_original_cannot_be_reexecuted(self):
        self.client.actual_run['status']='completed'
        with self.assertRaises(ValueError):self.verify(purpose='execution')
    def test_identity_scope_and_original_policy_mismatches(self):
        for key,value in [('gameId','G08'),('blockId','PLAY_FIRST_B01'),('caseSha256','0'*64),('scope','NON_GAMEPLAY_ONLY'),('issuerLogin','forged'),('maxGames',2),('originalOnly',False),('executionAuthorized',False)]:
            record,client,now=synthetic_trusted_fixture(self.case,self.run)
            claims=json.loads(client.decision['body'].split('\n',1)[1]);claims[key]=value
            client.decision['body']=AUTH.CONTRACT+'\n'+json.dumps(claims)
            with self.subTest(key=key),self.assertRaises(ValueError):AUTH.verify_record(self.case,self.run,record,_client=client,_now=now)
    def test_source_identity_and_run_context_mismatches(self):
        for key,value in [('sourceCommit','0'*40),('sourceTree','0'*40),('validationCommit','0'*40),('workflowBlob','0'*40),('runNumber',2),('runAttempt',2)]:
            run=copy.deepcopy(self.run);run[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):AUTH.verify_record(self.case,run,self.record,_client=self.client,_now=self.now)
    def test_unavailable_transport_fails_closed(self):
        with patch.object(self.client,'get',side_effect=OSError('EXCLUDED synthetic unavailable transport')):
            with self.assertRaises(OSError):self.verify()
    def test_duplicate_owner_json_key_rejected(self):
        self.client.decision['body']=AUTH.CONTRACT+'\n'+ '{"contract":"x","contract":"y"}'
        with self.assertRaises(ValueError):self.verify()
    def test_record_contract_disagreement_rejected(self):
        self.record['contract']='ARGENTUM_GAMEPLAY_AUTHORITY_UNREVIEWED'
        with self.assertRaises(ValueError):self.verify()
    def test_synthetic_provenance_cannot_be_promoted_to_live_authentication(self):
        self.record['provenance']['origin']='LIVE_GITHUB_TLS_OWNER_FACTS'
        with self.assertRaises(ValueError):self.verify()
    def test_historical_b01_and_g08_cases_rejected(self):
        for key,value in [('blockId','PLAY_FIRST_B01'),('gameId','G08')]:
            case=json.loads(self.case);case[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):AUTH.verify_record(json.dumps(case).encode(),self.run,self.record,_client=self.client,_now=self.now)
    def test_inline_owner_revocation_is_searched(self):
        original=self.client.get
        def read(path):
            if path.startswith('pulls/231/comments?'):
                return [dict(id=2,user={'id':AUTH.OWNER_ID},body='REVOKED',updated_at='2000-01-01T00:00:01Z')]
            return original(path)
        with patch.object(self.client,'get',side_effect=read),self.assertRaises(ValueError):self.verify()
    def test_actual_git_tree_and_parent_mismatch_rejected(self):
        original=self.client.get
        def read(path):
            result=original(path)
            if path=='git/commits/'+self.run['sourceCommit']:result['tree']['sha']='0'*40
            return result
        with patch.object(self.client,'get',side_effect=read),self.assertRaises(ValueError):self.verify()
    def test_self_asserted_cli_record_and_environment_fail_without_network(self):
        import os,subprocess,sys,tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'case').write_bytes(self.case);(root/'run').write_text(json.dumps(self.run));(root/'record').write_text('{"executionAuthorized":true,"externalVerified":true}')
            environment=dict(os.environ,PLAY_FIRST_EXECUTION_AUTHORIZED='true',PLAY_FIRST_TRUSTED_APPROVAL='true')
            result=subprocess.run([sys.executable,'-I',str(Path(AUTH.__file__)),'verify','--case',str(root/'case'),'--run-context',str(root/'run'),'--record',str(root/'record')],env=environment,capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,1)
            self.assertIn('missing/forged external',json.loads(result.stdout)['errors'][0])
    def test_execution_local_source_is_not_authenticated_by_environment(self):
        with patch.object(AUTH.subprocess,'check_output',return_value='unapproved-checkout\n'):
            with self.assertRaises(ValueError):AUTH.verify_local_execution_source(self.run)
    def test_record_numeric_original_only_cannot_impersonate_boolean(self):
        self.record['claims']['originalOnly']=1
        with self.assertRaises(ValueError):self.verify()
    def test_record_boolean_run_attempt_cannot_impersonate_integer(self):
        self.record['claims']['run']['runAttempt']=True
        with self.assertRaises(ValueError):self.verify()
    def test_record_cannot_choose_authority_transport_host_or_path(self):
        for path in ('https://attacker.invalid','../issues/231','//attacker.invalid#fragment'):
            with self.subTest(path=path),self.assertRaises(ValueError):AUTH.LiveGitHub().get(path)
    def test_tls_transport_mock_uses_exact_repository_endpoint_without_redirect(self):
        # Only a transport-shaped synthetic fixture; no live owner authentication.
        from unittest.mock import MagicMock
        endpoint='https://api.github.com/repos/'+AUTH.REPOSITORY
        response=MagicMock();response.status=200;response.geturl.return_value=endpoint;response.read.return_value=b'{"fixture":"EXCLUDED_SYNTHETIC_TRANSPORT_ONLY"}'
        response.__enter__.return_value=response
        opener=MagicMock();opener.open.return_value=response
        with patch.object(AUTH.urllib.request,'build_opener',return_value=opener):
            self.assertEqual(AUTH.LiveGitHub().get('')['fixture'],'EXCLUDED_SYNTHETIC_TRANSPORT_ONLY')
            self.assertEqual(opener.open.call_args.args[0].full_url,endpoint)
        with self.assertRaises(ValueError):AUTH.NoRedirect().redirect_request(None,None,None,None,None,None)

if __name__=='__main__':unittest.main()
