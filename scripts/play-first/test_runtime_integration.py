"""EXCLUDED integration only: unauthenticated CODE fixtures, no engine/run/slot.
The existing fake labels are reused; SQLite operations are real local transactions.
No fixture is a live owner, provider, host, independent clock or production anchor.
"""
import copy
from datetime import timedelta
import hashlib
import json
import multiprocessing
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import gameplay_authority as AUTH
import runtime_integration as INTEGRATION
import staged_runtime as RT
import strict_receiving as RECEIVER
from test_staged_runtime import SyntheticIdentityBoundary
from test_strict_receiving import fixture, encoded, sha


def parallel_worker(path, action, reservation, barrier, queue):
    try:
        raw,pins=fixture()
        bridge=INTEGRATION.RuntimeIntegration(RT.LocalLedger(path),
                    _synthetic_fixture_backend=SyntheticIdentityBoundary(raw['approved-case.json'],pins['run']))
        barrier.wait(20)
        row=getattr(bridge,action)('EXCLUDED_REQUEST',reservation)
        queue.put(dict(outcome='OK',state=row['state'],engineAllowed=row['engineAllowed'],pid=os.getpid()))
    except Exception as error:
        queue.put(dict(outcome='DENY',error=type(error).__name__,message=str(error),pid=os.getpid()))


class RuntimeIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.raw,self.pins=fixture();self.data=self.raw['approved-case.json'];self.run=self.pins['run']
        configured=os.environ.get('PLAY_FIRST_INTEGRATION_FIXTURE_ROOT')
        if configured:
            self.root=Path(configured)/self._testMethodName;self.root.mkdir(parents=True,exist_ok=False)
        else:
            temp=tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup);self.root=Path(temp.name)
        self.ledger=RT.LocalLedger(self.root/'EXCLUDED-local-ledger.sqlite3')
        self.backend=SyntheticIdentityBoundary(self.data,self.run)
        self.bridge=INTEGRATION.RuntimeIntegration(self.ledger,_synthetic_fixture_backend=self.backend)
        self.bridge.freeze('EXCLUDED_REQUEST',self.data)
        self.record=dict(contract=AUTH.CONTRACT,intentSha256=AUTH.sha(self.data),ownerDecisionId=1,
                         releaseDecisionId=2,holdRef='EXCLUDED_HOLD')

    def advance(self, state='RELEASE_VERIFIED'):
        for target,method,ref in [('OWNER_VERIFIED','owner','EXCLUDED_OWNER'),('HOLD','hold','EXCLUDED_HOLD'),
                                  ('RELEASE_VERIFIED','release','EXCLUDED_RELEASE')]:
            row=getattr(self.bridge,method)('EXCLUDED_REQUEST',ref)
            self.assertFalse(row['engineAllowed']);self.assertFalse(row['productionQualified'])
            if state==target:return row

    def correspondence(self,data=None,run=None,record=None):
        return self.bridge.release_correspondence('EXCLUDED_REQUEST',self.data if data is None else data,
                    self.run if run is None else run,self.record if record is None else record)

    def audit_count(self,state):
        with self.ledger.connection() as db:return db.execute('SELECT count(*) FROM audit WHERE new_state=?',(state,)).fetchone()[0]

    def concurrent(self,action,reservations):
        context=multiprocessing.get_context('spawn');barrier=context.Barrier(len(reservations));queue=context.Queue()
        children=[context.Process(target=parallel_worker,args=(str(self.ledger.path),action,r,barrier,queue)) for r in reservations]
        for child in children:child.start()
        results=[queue.get(timeout=30) for _ in children]
        for child in children:child.join(30);self.assertEqual(child.exitcode,0)
        self.assertEqual(len({x['pid'] for x in results}),len(children))
        (self.root/(action+'-actual-concurrency.json')).write_text(json.dumps(results,indent=2))
        queue.close();queue.join_thread();return results

    def receiving(self):
        manifest=[dict(path=k.removeprefix('case/'),bytes=len(v),sha256=sha(v)) for k,v in sorted(self.raw.items())
                  if k.startswith('case/') and k!='case/supervisor-manifest.json']
        self.raw['case/supervisor-manifest.json']=encoded(manifest);self.pins['manifestSha256']=sha(encoded(manifest))
        self.backend.anchor['facts']['manifestSha256']=self.pins['manifestSha256']
        self.backend.anchor['anchorDigest']=RT.digest(self.backend.anchor['facts'])
        directory=self.root/'EXCLUDED-fabricated-records';directory.mkdir(exist_ok=True)
        for name,data in self.raw.items():
            file=directory/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(data)
        return directory

    def test_immutable_intent_and_staged_decisions_cross_existing_contracts(self):
        self.advance();proof=self.correspondence()
        self.assertEqual(proof['claims']['caseSha256'],AUTH.sha(self.data));self.assertEqual(proof['claims']['run'],self.run)
        self.assertEqual(proof['provenance']['origin'],RT.SYNTHETIC)
        self.assertFalse(proof['engineAllowed']);self.assertFalse(proof['productionQualified'])

    def test_every_pre_release_state_and_synthetic_release_cannot_allow_engine(self):
        for state in ['INTENT','OWNER_VERIFIED','HOLD']:
            self.assertFalse(self.ledger.inspect('EXCLUDED_REQUEST')['engineAllowed'])
            self.assertRaises(ValueError,self.correspondence)
            if state=='INTENT':self.bridge.owner('EXCLUDED_REQUEST','EXCLUDED_OWNER')
            elif state=='OWNER_VERIFIED':self.bridge.hold('EXCLUDED_REQUEST','EXCLUDED_HOLD')
        self.bridge.release('EXCLUDED_REQUEST','EXCLUDED_RELEASE');self.assertFalse(self.correspondence()['engineAllowed'])
        self.assertFalse(hasattr(self.bridge,'launch'));self.assertFalse(hasattr(self.bridge,'create_original_trace'))

    def test_wrong_source_parent_is_rejected_before_freeze(self):
        intent=json.loads(self.data);intent['plan']['sourceParent']='0'*40
        self.assertRaises(ValueError,self.bridge.freeze,'EXCLUDED_OTHER',encoded(intent))

    def test_wrong_source_tree_and_exact_byte_substitution_deny_correspondence(self):
        self.advance();intent=json.loads(self.data);intent['plan']['sourceTree']='9'*40
        for data in [encoded(intent),self.data+b' ']:self.assertRaises(ValueError,self.correspondence,data=data)

    def test_wrong_owner_release_hold_or_run_reference_denies(self):
        self.advance()
        for key,value in [('ownerDecisionId',True),('ownerDecisionId',99),('releaseDecisionId',99),('holdRef','FORGED')]:
            record=dict(self.record,**{key:value});self.assertRaises(ValueError,self.correspondence,record=record)
        self.assertRaises(ValueError,self.correspondence,run=dict(self.run,runId='2'))

    def test_missing_owner_evidence_quarantines_staging(self):
        del self.backend.owner['decision'];self.assertRaises(ValueError,self.bridge.owner,'EXCLUDED_REQUEST','EXCLUDED_OWNER')
        self.assertEqual(self.ledger.inspect('EXCLUDED_REQUEST')['state'],'FAILED')

    def test_wrong_actual_owner_issuer_denies(self):
        self.backend.owner['decision']['user']['id']=0
        self.assertRaises(ValueError,self.bridge.owner,'EXCLUDED_REQUEST','EXCLUDED_OWNER')

    def test_missing_provider_and_host_identity_fail_closed(self):
        self.advance('OWNER_VERIFIED');del self.backend.hold['provider']['identityReference']
        self.assertRaises(ValueError,self.bridge.hold,'EXCLUDED_REQUEST','EXCLUDED_HOLD')
        self.assertEqual(self.ledger.inspect('EXCLUDED_REQUEST')['state'],'FAILED')

    def test_contradictory_host_source_fails_closed(self):
        self.advance('OWNER_VERIFIED');self.backend.hold['host']['sourceCommit']='9'*40
        self.assertRaises(ValueError,self.bridge.hold,'EXCLUDED_REQUEST','EXCLUDED_HOLD')

    def test_expired_release_denies_new_reservation(self):
        self.advance();self.backend.at+=timedelta(days=1)
        self.assertRaises(ValueError,self.bridge.reserve,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        self.assertEqual(self.ledger.inspect('EXCLUDED_REQUEST')['state'],'FAILED')

    def test_preexecution_revocation_quarantines_consumption(self):
        self.advance();self.bridge.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        revoked=copy.deepcopy(self.backend.client.release);revoked.update(id=3,body='REVOKED',created_at='2000-01-01T00:00:25Z',updated_at='2000-01-01T00:00:25Z')
        self.backend.release['history']['events'].append(revoked)
        self.assertRaises(ValueError,self.bridge.consume,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        self.assertEqual(self.ledger.inspect('EXCLUDED_REQUEST')['state'],'RECOVERY_REQUIRED')

    def test_out_of_order_release_persists_failure(self):
        self.assertRaises(ValueError,self.bridge.release,'EXCLUDED_REQUEST','EXCLUDED_RELEASE')
        self.assertEqual(self.ledger.inspect('EXCLUDED_REQUEST')['state'],'FAILED')

    def test_duplicate_same_request_is_idempotent_and_conflict_is_denied(self):
        self.assertEqual(self.bridge.freeze('EXCLUDED_REQUEST',self.data)['intent_hash'],AUTH.sha(self.data))
        self.assertRaises(ValueError,self.bridge.freeze,'EXCLUDED_REQUEST',self.data+b' ')
        self.assertRaises(ValueError,self.bridge.freeze,'EXCLUDED_OTHER',self.data)

    def test_genuine_concurrent_conflicting_reservations_commit_only_once(self):
        self.advance();results=self.concurrent('reserve',['EXCLUDED_RESERVATION_A','EXCLUDED_RESERVATION_B'])
        self.assertEqual(sum(x['outcome']=='OK' for x in results),1);self.assertEqual(self.audit_count('RESERVED'),1)

    def test_genuine_concurrent_idempotent_consumptions_have_one_effect(self):
        self.advance();self.bridge.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        results=self.concurrent('consume',['EXCLUDED_RESERVATION','EXCLUDED_RESERVATION'])
        self.assertTrue(all(x['outcome']=='OK' and not x['engineAllowed'] for x in results))
        self.assertEqual(self.audit_count('CONSUMED'),1)

    def test_consumed_original_cannot_be_released_or_cross_bound_again(self):
        self.advance();self.bridge.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION');self.bridge.consume('EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        self.assertRaises(ValueError,self.correspondence)
        self.assertRaises(ValueError,self.bridge.consume,'EXCLUDED_REQUEST','ALTERED_RESERVATION')
        self.assertRaises(ValueError,self.bridge.freeze,'EXCLUDED_OTHER',self.data)

    def test_cancel_after_reservation_requires_separate_recovery_disposition(self):
        self.advance();self.bridge.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        self.assertEqual(self.bridge.cancel('EXCLUDED_REQUEST'),'RECOVERY_REQUIRED')
        self.assertRaises(ValueError,self.bridge.consume,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')

    def test_pre_reservation_cancel_retains_non_reusable_key(self):
        self.assertEqual(self.bridge.cancel('EXCLUDED_REQUEST'),'CANCELLED')
        self.assertRaises(ValueError,self.bridge.freeze,'EXCLUDED_OTHER',self.data)

    def test_real_process_crash_after_reservation_is_quarantined(self):
        self.advance()
        script="""import os,sys
sys.path.insert(0,sys.argv[1])
import staged_runtime as RT, runtime_integration as I
from test_staged_runtime import SyntheticIdentityBoundary
from test_strict_receiving import fixture
raw,pins=fixture();bridge=I.RuntimeIntegration(RT.LocalLedger(sys.argv[2]),_synthetic_fixture_backend=SyntheticIdentityBoundary(raw['approved-case.json'],pins['run']))
bridge.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION');os._exit(88)
"""
        command=[sys.executable,'-B','-c',script,str(Path(__file__).parent),str(self.ledger.path)]
        result=subprocess.run(command,capture_output=True)
        (self.root/'crash.stdout').write_bytes(result.stdout);(self.root/'crash.stderr').write_bytes(result.stderr)
        (self.root/'crash.json').write_text(json.dumps({'command':command,'exitCode':result.returncode,'expected':88},indent=2))
        self.assertEqual(result.returncode,88);self.assertEqual(self.bridge.quarantine_interrupted('EXCLUDED_REQUEST'),'RECOVERY_REQUIRED')
        self.assertRaises(ValueError,self.bridge.consume,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')

    def test_local_rollback_does_not_claim_an_execution_time_anchor(self):
        self.advance()
        def failure(db):raise RuntimeError('EXCLUDED interruption')
        self.assertRaises(RuntimeError,self.bridge.reserve,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION',_before_commit=failure)
        self.assertEqual(self.audit_count('RESERVED'),0)
        self.assertFalse(self.ledger.inspect('EXCLUDED_REQUEST')['engineAllowed'])

    def test_integrated_receiving_after_expiry_is_only_synthetic_correspondence(self):
        directory=self.receiving();self.backend.at+=timedelta(days=1)
        report=RECEIVER.inspect_evidence(directory,self.pins,_runtime_integration=self.bridge)
        self.assertEqual(report['status'],'PINNED_RECORD_CORRESPONDENCE_ONLY',report)
        self.assertFalse(report['gameplayAuthorized']);self.assertFalse(report['authenticatedOrigin'])
        self.assertFalse(report['authorityDecisionCorrespondence']['productionQualified'])

    def test_post_execution_comment_does_not_replace_execution_time_receiving(self):
        directory=self.receiving();event=copy.deepcopy(self.backend.anchor['facts']['owner'])
        event.update(id=3,body='LATER COMMENT',created_at='2000-01-02T00:00:00Z',updated_at='2000-01-02T00:00:00Z')
        self.backend.anchor['facts']['history'].append(event);self.backend.anchor['history']['events'].append(copy.deepcopy(event))
        self.backend.anchor['anchorDigest']=RT.digest(self.backend.anchor['facts'])
        self.assertEqual(RECEIVER.inspect_evidence(directory,self.pins,_runtime_integration=self.bridge)['status'],'PINNED_RECORD_CORRESPONDENCE_ONLY')

    def test_absent_or_altered_independent_historical_anchor_is_denied(self):
        directory=self.receiving();self.backend.anchor['facts']['executedAt']='2000-01-02T00:00:00Z'
        self.assertEqual(RECEIVER.inspect_evidence(directory,self.pins,_runtime_integration=self.bridge)['status'],'INCOMPLETE')

    def test_self_declared_time_and_mixed_anchor_interfaces_deny(self):
        directory=self.receiving();self.pins['gameplayApproval']['executedAt']='2000-01-01T00:00:30Z'
        self.assertEqual(RECEIVER.inspect_evidence(directory,self.pins,_runtime_integration=self.bridge)['status'],'INCOMPLETE')
        self.assertRaises(ValueError,INTEGRATION.verify_historical,self.data,self.run,self.backend.record,'8'*64,_anchor=self.backend,_integration=self.bridge)

    def test_production_default_and_forged_production_origin_deny(self):
        unavailable=INTEGRATION.RuntimeIntegration(self.ledger)
        self.assertRaises(ValueError,unavailable.owner,'EXCLUDED_REQUEST','EXCLUDED_OWNER')
        self.backend.origin='TRUSTED_EXTERNAL_LAUNCH_RELEASE'
        self.assertRaises(ValueError,self.correspondence)

    def test_missing_ledger_cannot_establish_single_use(self):
        self.assertRaises(ValueError,INTEGRATION.RuntimeIntegration,None,_synthetic_fixture_backend=self.backend)

    def test_cli_cannot_install_synthetic_backend_from_environment_or_files(self):
        case=self.root/'intent.json';run=self.root/'run.json';record=self.root/'record.json'
        case.write_bytes(self.data);run.write_bytes(encoded(self.run));record.write_bytes(encoded(dict(self.record,authorized=True,origin=RT.SYNTHETIC)))
        env=os.environ.copy();env['PLAY_FIRST_GAMEPLAY_BACKEND']=RT.SYNTHETIC
        command=[sys.executable,'-I',str(Path(INTEGRATION.__file__)),'verify-release','--case',str(case),'--run-context',str(run),'--record',str(record)]
        result=subprocess.run(command,capture_output=True,env=env)
        (self.root/'cli.stdout').write_bytes(result.stdout);(self.root/'cli.stderr').write_bytes(result.stderr)
        self.assertEqual(result.returncode,1);proof=json.loads(result.stdout)
        self.assertFalse(proof['engineAllowed']);self.assertFalse(proof['productionQualified']);self.assertIn('UNQUALIFIED',proof['errors'][0])

    def test_b01_and_g08_still_excluded_at_integrated_freeze(self):
        for key,value in [('blockId','PLAY_FIRST_B01'),('gameId','G08')]:
            intent=json.loads(self.data);intent['case'][key]=value
            self.assertRaises(ValueError,self.bridge.freeze,'EXCLUDED_OTHER',encoded(intent))

    def test_python_and_kotlin_share_fixed_fail_closed_integration_boundary(self):
        root=Path(__file__).resolve().parents[2]
        kotlin=(root/'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt').read_text()
        self.assertIn('"scripts/play-first/runtime_integration.py", "verify-release"',kotlin)
        self.assertIn('check(it.getValue("productionQualified").jsonPrimitive.boolean)',kotlin)
        self.assertIn('put("sourceParent", "'+AUTH.SOURCE_PARENT+'")',kotlin)
        for marker in ['PlayFirstTimingJournal(','CardRegistry()','GameEnvironment.create(','env.reset(']:
            self.assertLess(kotlin.index('check(gate.exitValue() == 0)'),kotlin.index(marker))
        self.assertEqual(AUTH.SOURCE_PARENT,'e79adf5770a803568a81be73a83fa8627c48c862')


if __name__=='__main__':unittest.main(verbosity=2)
