"""Excluded successor correspondence tests. Never execute gameplay or call live APIs."""
from copy import deepcopy
import base64, json, os, subprocess, sys, unittest
from pathlib import Path
import gameplay_authority as auth
import one_shot_actions as shot
import one_shot_launch as launch
import successor_protocol as successor
from test_one_shot_launch import LaunchFixture, encoded, NOW
import test_one_shot_actions as old

ROOT=Path(__file__).resolve().parents[2]

class SuccessorTests(unittest.TestCase):
    def setUp(self): self.f=LaunchFixture()
    def verify(self, hold=None, context=True):
        return launch.verify(encoded(self.f.request),ROOT,context=self.f.context if context else None,
            hold=hold,_client=self.f,_checkout=lambda _:None,_now=NOW)
    def release(self):
        hold=launch.hold_record(self.verify())
        frozen=shot.freeze_intent(base64.b64decode(self.f.request['intentBytesBase64']))
        self.f.release=old.comment(12,shot.RELEASE,launch.release_expected(self.f.request,frozen,
            self.f.context,shot.digest(shot.canonical(hold)),shot.digest(self.f.environment['body'].encode())),
            stamp='2026-10-13T12:00:00Z')
        return hold
    def assertDenied(self):
        with self.assertRaises((ValueError,KeyError,TypeError)): self.verify()
    def rebind_case(self):
        self.f.intent['case'].update(gameId=self.f.case['case']['gameId'],
            caseId=self.f.case['case']['blockId']+'-'+self.f.case['case']['gameId'],
            permittedInputsSha256=shot.digest(encoded(self.f.case)))
        self.f.request.update(caseBytesBase64=base64.b64encode(encoded(self.f.case)).decode(),
            intentBytesBase64=base64.b64encode(encoded(self.f.intent)).decode())
    def test_complete_synthetic_chain_never_promotes(self):
        result=self.verify(self.release())
        self.assertFalse(result['engineAllowed']);self.assertFalse(result['authenticatedOwnerDecision'])
        self.assertEqual(result['origin'],shot.SYNTHETIC)
        self.assertEqual(result['successor']['protocol'],successor.PROTOCOL)
    def test_fresh_allocation_is_required(self):
        self.f.request.pop('allocationDecisionId');self.assertDenied()
    def test_wrong_allocation_body_hash_refuses(self):
        self.f.request['allocationBodySha256']='0'*64;self.assertDenied()
    def test_wrong_allocation_issuer_refuses(self):
        self.f.allocation['user']['id']=1;self.assertDenied()
    def test_expired_allocation_refuses(self):
        self.f.allocation['body']=self.f.allocation['body'].replace('12:30:00Z','11:59:00Z');self.assertDenied()
    def test_allocation_before_original_completion_refuses(self):
        self.f.predecessor['updated_at']='2026-10-13T11:56:30Z';self.assertDenied()
    def test_environment_before_allocation_refuses(self):
        self.f.allocation.update(created_at='2026-10-13T11:57:30Z',updated_at='2026-10-13T11:57:30Z');self.assertDenied()
    def test_old_contract_cannot_be_renamed_without_new_allocation(self):
        self.f.request['contract']='ARGENTUM_ONE_SHOT_REQUEST_V1';self.assertDenied()
    def test_old_comment_ids_refused_in_each_role(self):
        for key in ('allocationDecisionId','ownerDecisionId','environmentVerificationId'):
            for ident in successor.OLD_COMMENTS:
                with self.subTest(role=key,ident=ident):
                    self.f=LaunchFixture();self.f.request[key]=ident;self.assertDenied()
    def test_old_seed_refuses_even_with_recomputed_case_hash(self):
        self.f.case['case']['fixtureSeedHex']=successor.OLD_SEED;self.rebind_case();self.assertDenied()
    def test_old_game_refuses_even_with_new_block(self):
        self.f.case['case']['gameId']='G09';self.rebind_case();self.assertDenied()
    def test_old_block_refuses_even_with_new_game(self):
        self.f.case['case']['blockId']='PLAY_FIRST_ONESHOT_20261013';self.rebind_case();self.assertDenied()
    def test_old_environment_refuses(self):
        self.f.request['environmentPolicy']['id']=24201675024
        self.f.request['environmentFacts']['id']=24201675024;self.assertDenied()
    def test_wrong_workflow_refuses(self):
        self.f.run['path']=successor.PREDECESSOR['workflow'];self.assertDenied()
    def test_old_workflow_id_cannot_be_renamed(self):
        self.f.run['workflow_id']=successor.PREDECESSOR['workflowId'];self.assertDenied()
    def test_wrong_provider_source_refuses(self):
        self.f.run['head_sha']=successor.PREDECESSOR['source'];self.assertDenied()
    def test_wrong_original_number_refuses(self):
        self.f.run['run_number']=2;self.assertDenied()
    def test_wrong_attempt_refuses(self):
        self.f.run['run_attempt']=2;self.assertDenied()
    def test_multiple_successor_originals_refuse(self):
        self.f.history_runs.append(deepcopy(self.f.run));self.f.total=2;self.assertDenied()
    def test_replacement_predecessor_attempt_refuses(self):
        self.f.predecessor['run_attempt']=2;self.assertDenied()
    def test_additional_predecessor_run_refuses(self):
        extra=deepcopy(self.f.predecessor);extra['id']+=1
        self.f.predecessor_history.append(extra);self.assertDenied()
    def test_predecessor_reclassified_success_refuses(self):
        self.f.predecessor['conclusion']='success';self.assertDenied()
    def test_missing_original_evidence_refuses(self):
        self.f.original_artifacts.clear();self.assertDenied()
    def test_modified_original_artifact_digest_refuses(self):
        self.f.original_artifacts[11912278351]['digest']='sha256:'+'0'*64;self.assertDenied()
    def test_expired_original_artifact_refuses(self):
        self.f.original_artifacts[11912278351]['expired']=True;self.assertDenied()
    def test_missing_release_refuses(self):
        hold=launch.hold_record(self.verify())
        with self.assertRaises(ValueError):self.verify(hold)
    def test_previous_release_contract_refuses(self):
        hold=self.release();self.f.release['body']=self.f.release['body'].replace(shot.RELEASE,'ARGENTUM_ONE_SHOT_ACTIONS_RELEASE_V1')
        with self.assertRaises(ValueError):self.verify(hold)
    def test_modified_hold_refuses(self):
        hold=self.release();hold['intentSha256']='0'*64
        with self.assertRaises(ValueError):self.verify(hold)
    def test_missing_environment_protection_refuses(self):
        self.f.request['environmentFacts']['protection_rules']=[];self.assertDenied()
    def test_revocation_after_allocation_before_environment_refuses(self):
        self.f.extra=[dict(id=13,user=dict(id=auth.OWNER_ID),updated_at='2026-10-13T11:56:30Z',body='REVOKED')]
        self.assertDenied()
    def test_old_request_variable_is_not_fallback(self):
        env={k:v for k,v in os.environ.items() if not k.startswith(('GITHUB_','GH_','ARGENTUM_','PLAY_FIRST_'))}
        env['ARGENTUM_ONE_SHOT_REQUEST_JSON']=encoded(self.f.request).decode()
        p=subprocess.run([sys.executable,'-I',str(ROOT/'scripts/play-first/one_shot_launch.py'),'gate'],env=env,capture_output=True)
        self.assertEqual(p.returncode,78);self.assertIn(b'missing/bounded',p.stderr)
    def test_accepted_launcher_resolution_remains_byte_identical(self):
        path=launch.ADAPTER
        baseline=subprocess.check_output(['git','show',successor.ACCEPTED_REPAIR['commit']+':'+path],cwd=ROOT,text=True)
        current=(ROOT/path).read_text()
        self.assertEqual(baseline.split('internal object OneShotGateCommand {')[1],current.split('internal object OneShotGateCommand {')[1])
    def test_gameplay_body_remains_byte_identical(self):
        baseline=subprocess.check_output(['git','show',successor.ACCEPTED_REPAIR['commit']+':'+launch.ADAPTER],cwd=ROOT,text=True)
        current=(ROOT/launch.ADAPTER).read_text()
        # The preceding evidence header must report the new sole parent. Engine
        # initialization, deck/pilot setup and every gameplay operation stay exact.
        self.assertEqual(baseline[baseline.index('CardRegistry()'):],current[current.index('CardRegistry()'):])
    def test_legacy_workflow_bytes_preserved(self):
        path=successor.PREDECESSOR['workflow']
        baseline=subprocess.check_output(['git','show',successor.ACCEPTED_REPAIR['commit']+':'+path],cwd=ROOT)
        self.assertEqual(baseline,(ROOT/path).read_bytes())
    def test_new_workflow_no_dispatch_or_retry_shortcut(self):
        wf=json.loads((ROOT/shot.WORKFLOW).read_text())
        self.assertEqual(wf['on'],{'push':{'branches':[shot.BRANCH]}})
        self.assertEqual(wf['permissions'],{'contents':'read','actions':'read'})
        for job in wf['jobs'].values():
            self.assertIn('github.run_number == 1',job['if']);self.assertIn('github.run_attempt == 1',job['if'])
        self.assertIn('environment',wf['jobs']['game'])
        self.assertEqual(wf['env']['ARGENTUM_SUCCESSOR_REQUEST_JSON'],'${{ vars.ARGENTUM_SUCCESSOR_REQUEST_JSON }}')

if __name__=='__main__':unittest.main()
