"""Excluded infrastructure fixtures only. No owner approval, seed, run or game.
Comment1/2 and G99 are unauthenticated fixture labels. SQLite/flock/process deaths
are actual local operations, never original gameplay slots or engine execution.
"""
import copy
from datetime import timedelta
import hashlib
import json
import multiprocessing
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest

import focused_nonofficial_admission as F
import gameplay_authority as A
import runtime_integration as INTEGRATION
import staged_runtime as R

AT = A.utc('2026-10-10T12:00:10Z')


def encoded(value): return R.canonical(value).encode()


def intent(path):
    return dict(contract=F.CONTRACT,
                case=dict(scope=A.SCOPE, blockId='PLAY_FIRST_EXCLUDED_SYNTHETIC', gameId='G99',
                          seats=dict(pest=0, monster=1), originalOnly=True, maxGames=1),
                identities=dict(sourceCommit='a'*40, sourceTree='b'*40, sourceParent=F.SOURCE_PARENT,
                    engineManifestSha256=A.sha(b'EXCLUDED synthetic engine manifest; no engine'),
                    deckManifestSha256=A.sha(b'EXCLUDED synthetic deck manifest; no selected deck'),
                    pilotManifestSha256=A.sha(b'EXCLUDED synthetic pilot manifest')),
                executor=dict(executorId='EXCLUDED_SYNTHETIC_EXECUTOR', storageId='EXCLUDED_SYNTHETIC_STORAGE', ledgerPath=str(path)))


class SyntheticExecutor:
    origin = F.SYNTHETIC
    def __init__(self, value):
        self.at = AT
        self.facts = dict(value['executor'], persistent=True, exclusive=True, posix=True, identities=copy.deepcopy(value['identities']))
    def inspect(self): return copy.deepcopy(self.facts)
    def now(self): return self.at


def comment(number, contract, claims, at=AT):
    return dict(id=number, user=dict(id=A.OWNER_ID, login=A.OWNER_LOGIN), author_association='OWNER',
                issue_url='https://api.github.com/repos/'+A.REPOSITORY+'/issues/231',
                html_url='https://github.com/'+A.REPOSITORY+'/pull/231#issuecomment-'+str(number),
                created_at=at.strftime('%Y-%m-%dT%H:%M:%SZ'), updated_at=at.strftime('%Y-%m-%dT%H:%M:%SZ'),
                body=contract+'\n'+R.canonical(dict(claims, expiresAt=(at+timedelta(seconds=50)).strftime('%Y-%m-%dT%H:%M:%SZ'))))


class SyntheticGitHub:
    """CODE fixture; never a TLS-authenticated decision or real comment."""
    def __init__(self): self.comments = {}; self.events = []; self.calls = []; self.replacements = {}
    def get(self, path):
        self.calls.append(path)
        if path == '': return dict(owner=dict(id=A.OWNER_ID, login=A.OWNER_LOGIN))
        if path.startswith('issues/comments/'):
            number = int(path.rsplit('/',1)[-1])
            value = self.comments[number]
            if number in self.replacements:
                self.comments[number] = self.replacements.pop(number)
            return copy.deepcopy(value)
        if path.startswith('issues/231/comments?'): return copy.deepcopy(list(self.comments.values())+self.events)
        if path.startswith('pulls/231/comments?') or path.startswith('pulls/231/reviews?'): return []
        raise ValueError('no synthetic fixture endpoint '+path)


class SyntheticArchive:
    origin = F.SYNTHETIC
    def __init__(self, receipt, data): self.receipt=copy.deepcopy(receipt); self.data=data
    def read_original(self, ref):
        if ref!='EXCLUDED_ORIGINAL':raise ValueError('missing archived original')
        return copy.deepcopy(self.receipt)
    def intent_bytes(self, ref): return self.data


def stages(session, client, request, data, target='CONSUMED', reservation='EXCLUDED_RESERVATION', before_commit=None):
    session.freeze(request, data)
    frozen=F.freeze_intent(data)
    client.comments[1]=comment(1,F.OWNER_CONTRACT,session._expected(frozen,F.OWNER_CONTRACT))
    session.owner_approved(request,1)
    if target=='OWNER_VERIFIED':return
    session.hold(request)
    if target=='HOLD':return
    with session.ledger.connection() as db: evidence=A.decode(session._row(db,request)['evidence'])
    expected=session._expected(frozen,F.RELEASE_CONTRACT,ownerDecisionId=1,requestId=request,holdSha256=R.digest(evidence['hold']))
    client.comments[2]=comment(2,F.RELEASE_CONTRACT,expected)
    session.final_release(request,2)
    if target=='RELEASE_VERIFIED':return
    session.reserve(request,reservation,_before_commit=before_commit)
    if target=='RESERVED':return
    session.consume(request,reservation)


def concurrent_worker(path, request, barrier, queue):
    value=intent(Path(path)); executor=SyntheticExecutor(value);client=SyntheticGitHub()
    barrier.wait(30)
    try:
        with F.FocusedAdmission(path,_executor=executor,_client=client) as session:
            row=session.freeze(request,encoded(value))
            if row['state']=='CONSUMED':
                queue.put(dict(outcome='EXISTING',request=request,pid=os.getpid(),record=row));return
            stages(session,client,request,encoded(value))
            queue.put(dict(outcome='OK',request=request,pid=os.getpid(),record=session.inspect(request)))
    except Exception as exc: queue.put(dict(outcome='DENY',request=request,pid=os.getpid(),error=type(exc).__name__,reason=str(exc)))


def crash_worker(path, after):
    value=intent(Path(path));executor=SyntheticExecutor(value);client=SyntheticGitHub()
    with F.FocusedAdmission(path,_executor=executor,_client=client) as session:
        stages(session,client,'EXCLUDED_REQUEST',encoded(value),target='RESERVED',
               before_commit=None if after else lambda db: os._exit(88))
        os._exit(89)


def consumed_crash_worker(path):
    value=intent(Path(path));executor=SyntheticExecutor(value);client=SyntheticGitHub()
    with F.FocusedAdmission(path,_executor=executor,_client=client) as session:
        stages(session,client,'EXCLUDED_REQUEST',encoded(value));os._exit(90)


class FocusedTests(unittest.TestCase):
    def setUp(self):
        base=os.environ.get('PLAY_FIRST_FOCUSED_FIXTURE_ROOT')
        if base:
            self.root=Path(base)/self._testMethodName;self.root.mkdir(exist_ok=False)
        else:
            temp=tempfile.TemporaryDirectory();self.addCleanup(temp.cleanup);self.root=Path(temp.name)
        self.path=self.root/'EXCLUDED-local-ledger.sqlite3';self.value=intent(self.path);self.data=encoded(self.value)
        self.executor=SyntheticExecutor(self.value);self.client=SyntheticGitHub()
        self.session=F.FocusedAdmission(self.path,_executor=self.executor,_client=self.client)
        self.addCleanup(self.session.close)
        self.request='EXCLUDED_REQUEST'

    def advance(self,state='CONSUMED'):
        stages(self.session,self.client,self.request,self.data,state)

    def evidence(self):
        with self.session.ledger.connection() as db: return A.decode(self.session._row(db,self.request)['evidence'])

    def receipt(self, interrupted=False):
        self.advance()
        self.session.observe_process(self.request,'START',pid=os.getpid())
        self.executor.at+=timedelta(seconds=1)
        self.session.observe_process(self.request,'INTERRUPTED' if interrupted else 'FINISH',pid=os.getpid(),exit_code=None if interrupted else 0)
        self.session.observe_process(self.request,'CLEANUP',pid=os.getpid())
        root=self.root/'EXCLUDED-owner-controlled-original';root.mkdir()
        (root/'child.log').write_bytes(b'EXCLUDED fixture log: no child engine executed\n')
        (root/'partial-failure.log').write_bytes(b'EXCLUDED interruption' if interrupted else b'EXCLUDED completed process observation')
        result=self.session.receipt(self.request,root)
        (self.root/'receipt.json').write_text(json.dumps(result,indent=2))
        return result,root,SyntheticArchive(result,self.data)

    def test_synthetic_stages_never_grant_gameplay(self):
        self.advance()
        row=self.session.inspect(self.request)
        self.assertEqual(row['stage'],'ORIGINAL_CONSUMED')
        for key in ('engineAllowed','gameplayAuthorized','productionQualified','authenticatedProductionOrigin'):self.assertIs(row[key],False)
        self.assertFalse(hasattr(self.session,'launch'));self.assertFalse(hasattr(self.session,'initialize_engine'))

    def test_frozen_intent_has_no_future_comment_or_run_identifier(self):
        frozen=F.freeze_intent(self.data);self.assertEqual(frozen['intentSha256'],A.sha(self.data))
        self.assertNotIn('decisionId',self.data.decode());self.assertNotIn('runId',self.data.decode())
        self.assertNotIn('fixtureSeedHex',self.data.decode())

    def test_owner_comment_gets_provider_id_after_immutable_intent(self):
        self.session.freeze(self.request,self.data);f=F.freeze_intent(self.data)
        expected=self.session._expected(f,F.OWNER_CONTRACT)
        self.assertNotIn('ownerDecisionId',expected)
        self.client.comments[1]=comment(1,F.OWNER_CONTRACT,expected)
        self.session.owner_approved(self.request,1)

    def test_missing_real_executor_backend_is_denied_before_ledger_creation(self):
        p=self.root/'other.sqlite3'
        self.assertRaises(ValueError,F.FocusedAdmission,p)
        self.assertFalse(p.exists())

    def test_plain_json_executor_cannot_authenticate_itself(self):
        self.assertRaises(ValueError,F.FocusedAdmission,self.path,_executor=self.executor.facts)

    def test_self_asserted_production_origin_is_not_a_backend(self):
        self.executor.origin='TRUSTED_OWNER_EXECUTOR'
        self.assertRaises(ValueError,F.FocusedAdmission,self.path,_executor=self.executor)

    def test_conflicting_active_executor_is_refused(self):
        self.assertRaises(ValueError,F.FocusedAdmission,self.path,_executor=self.executor,_client=self.client)

    def test_changed_executor_identity_conflicts_with_persistent_registration(self):
        self.session.close();self.executor.facts['executorId']='EXCLUDED_OTHER_EXECUTOR'
        self.assertRaises(ValueError,F.FocusedAdmission,self.path,_executor=self.executor,_client=self.client)

    def test_copied_database_cannot_become_a_second_authoritative_ledger(self):
        self.session.close();other=self.root/'EXCLUDED-copy.sqlite3';shutil.copyfile(self.path,other)
        self.executor.facts['ledgerPath']=str(other)
        self.assertRaises(ValueError,F.FocusedAdmission,other,_executor=self.executor,_client=self.client)

    def test_symlink_ledger_is_refused(self):
        link=self.root/'EXCLUDED-link.sqlite3';link.symlink_to(self.path)
        self.executor.facts['ledgerPath']=str(link)
        self.assertRaises(ValueError,F.FocusedAdmission,link,_executor=self.executor,_client=self.client)

    def test_hardlinked_ledger_is_refused(self):
        self.session.close();other=self.root/'EXCLUDED-hardlink.sqlite3';os.link(self.path,other)
        self.assertRaises(ValueError,F.FocusedAdmission,self.path,_executor=self.executor,_client=self.client)

    def test_source_changed_after_hold_is_refused(self):
        self.advance('HOLD');self.executor.facts['identities']['engineManifestSha256']='c'*64
        self.assertRaises(ValueError,self.session.final_release,self.request,2)

    def test_request_idempotency_does_not_create_another_original(self):
        self.session.freeze(self.request,self.data);self.session.freeze(self.request,self.data)
        with self.session.ledger.connection() as db:self.assertEqual(db.execute('SELECT count(*) FROM attempts').fetchone()[0],1)

    def test_conflicting_repeated_request_is_refused(self):
        self.session.freeze(self.request,self.data);value=copy.deepcopy(self.value);value['case']['seats']={'pest':1,'monster':0}
        self.assertRaises(ValueError,self.session.freeze,self.request,encoded(value))

    def test_shared_legacy_original_key_refuses_protocol_switch_replay(self):
        original=R.digest({k:self.value['case'][k] for k in ('scope','blockId','gameId')})
        with self.session.ledger.transaction() as db:
            db.execute('INSERT INTO attempts VALUES(?,?,?,?,?,?,?,?)',('EXCLUDED_OTHER_PROTOCOL',original,'x',b'EXCLUDED','CONSUMED','{}',None,''))
        self.assertRaises(sqlite3.IntegrityError,self.session.freeze,self.request,self.data)

    def test_conflicting_case_source_cannot_reuse_logical_original(self):
        self.session.freeze(self.request,self.data);other=copy.deepcopy(self.value);other['identities']['sourceTree']='e'*40
        self.executor.facts['identities']=other['identities']
        self.assertRaises(sqlite3.IntegrityError,self.session.freeze,'EXCLUDED_OTHER_REQUEST',encoded(other))

    def test_consume_before_release_and_reservation_denied(self):
        self.session.freeze(self.request,self.data)
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')
        self.assertFalse(self.session.inspect(self.request)['engineAllowed'])

    def test_out_of_order_release_persists_refusal(self):
        self.session.freeze(self.request,self.data)
        self.assertRaises(ValueError,self.session.final_release,self.request,2)
        self.assertEqual(self.session.inspect(self.request)['stage'],'REFUSED')

    def test_no_process_observation_before_consumption(self):
        self.advance('RELEASE_VERIFIED')
        self.assertRaises(ValueError,self.session.observe_process,self.request,'START',pid=os.getpid())
        self.assertEqual(self.evidence()['observations'],[])

    def test_no_original_payload_or_trace_directory_is_created_by_admission(self):
        before={p.name for p in self.root.iterdir()};self.advance();after={p.name for p in self.root.iterdir()}
        self.assertEqual(before,after)
        self.assertFalse(any(p.is_dir() for p in self.root.iterdir()))

    def test_cancel_before_release_is_terminal_and_no_replacement(self):
        self.session.freeze(self.request,self.data);self.session.cancel(self.request)
        self.assertEqual(self.session.inspect(self.request)['stage'],'CANCELLED')
        self.assertRaises(ValueError,self.session.owner_approved,self.request,1)
        self.assertRaises(sqlite3.IntegrityError,self.session.freeze,'EXCLUDED_REPLACEMENT',self.data)

    def test_cancel_after_reservation_quarantines(self):
        self.advance('RESERVED');self.session.cancel(self.request)
        self.assertEqual(self.session.inspect(self.request)['stage'],'QUARANTINED')
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')

    def test_interrupt_is_separate_from_owner_cancellation(self):
        self.session.freeze(self.request,self.data);self.session.interrupt(self.request)
        row=self.session.inspect(self.request);self.assertEqual(row['stage'],'REFUSED');self.assertIn('interrupted',row['reason'])

    def test_new_session_cannot_resume_a_committed_reservation(self):
        self.advance('RESERVED');self.session.close()
        with F.FocusedAdmission(self.path,_executor=self.executor,_client=self.client) as session:
            self.assertEqual(session.inspect(self.request)['stage'],'QUARANTINED')
            self.assertRaises(ValueError,session.consume,self.request,'EXCLUDED_RESERVATION')

    def test_new_session_refuses_interrupted_hold(self):
        self.advance('HOLD');self.session.close()
        with F.FocusedAdmission(self.path,_executor=self.executor,_client=self.client) as session:
            self.assertEqual(session.inspect(self.request)['stage'],'REFUSED')

    def test_reservation_duplicate_is_idempotent_and_conflict_refused(self):
        self.advance('RESERVED');self.assertEqual(self.session.reserve(self.request,'EXCLUDED_RESERVATION')['state'],'RESERVED')
        self.assertRaises(ValueError,self.session.reserve,self.request,'EXCLUDED_DIFFERENT_RESERVATION')

    def test_duplicate_consumption_returns_record_without_new_capability(self):
        self.advance();row=self.session.consume(self.request,'EXCLUDED_RESERVATION')
        self.assertFalse(row['newConsumption']);self.assertFalse(row['engineAllowed'])
        with self.session.ledger.connection() as db:self.assertEqual(db.execute("SELECT count(*) FROM audit WHERE new_state='CONSUMED'").fetchone()[0],1)

    def test_last_owner_readback_precedes_local_consumption(self):
        self.advance('RESERVED');self.client.calls=[];self.session.consume(self.request,'EXCLUDED_RESERVATION')
        self.assertEqual(self.client.calls[-2:],['issues/comments/1','issues/comments/2'])
        self.assertIn('finalPrelaunchReadback',self.evidence())

    def test_preconsumption_revocation_quarantines(self):
        self.advance('RESERVED');self.client.events.append(comment(3,'EXCLUDED_REVOCATION',{}))
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')
        self.assertEqual(self.session.inspect(self.request)['stage'],'QUARANTINED')

    def test_expired_release_cannot_be_consumed(self):
        self.advance('RESERVED');self.executor.at+=timedelta(seconds=51)
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')

    def test_comment_changed_between_initial_and_final_readback_is_refused(self):
        self.session.freeze(self.request,self.data);f=F.freeze_intent(self.data)
        self.client.comments[1]=comment(1,F.OWNER_CONTRACT,self.session._expected(f,F.OWNER_CONTRACT))
        changed=copy.deepcopy(self.client.comments[1]);changed['body']='EXCLUDED non-gameplay construction'
        self.client.replacements[1]=changed
        self.assertRaises(ValueError,self.session.owner_approved,self.request,1)

    def test_concurrent_conflicting_process_admissions_consume_exactly_once(self):
        self.session.close();ctx=multiprocessing.get_context('spawn');barrier=ctx.Barrier(6);queue=ctx.Queue()
        children=[ctx.Process(target=concurrent_worker,args=(str(self.path),'EXCLUDED_REQUEST_'+str(i),barrier,queue)) for i in range(6)]
        for p in children:p.start()
        records=[queue.get(timeout=40) for p in children]
        for p in children:p.join(30);self.assertEqual(p.exitcode,0)
        queue.close();queue.join_thread()
        self.assertEqual(len({r['pid'] for r in records}),6);self.assertEqual(sum(r['outcome']=='OK' for r in records),1)
        with R.LocalLedger(self.path).connection() as db:
            self.assertEqual(db.execute("SELECT count(*) FROM attempts WHERE state='CONSUMED'").fetchone()[0],1)
        (self.root/'actual-multiprocess-results.json').write_text(json.dumps(records,indent=2))

    def crash(self,after):
        self.session.close();ctx=multiprocessing.get_context('spawn');p=ctx.Process(target=crash_worker,args=(str(self.path),after));p.start();p.join(30)
        self.assertEqual(p.exitcode,89 if after else 88)
        with R.LocalLedger(self.path).connection() as db:
            self.assertEqual(db.execute('SELECT state FROM attempts').fetchone()[0],'RESERVED' if after else 'RELEASE_VERIFIED')
        with F.FocusedAdmission(self.path,_executor=self.executor,_client=self.client) as session:
            self.assertEqual(session.inspect(self.request)['stage'],'QUARANTINED' if after else 'REFUSED')
            self.assertRaises(ValueError,session.consume,self.request,'EXCLUDED_RESERVATION')
        (self.root/'actual-process-death.json').write_text(json.dumps(dict(exitCode=p.exitcode,afterCommit=after,replayAllowed=False)))

    def test_process_death_before_reservation_commit_rolls_back_then_refuses_resume(self):self.crash(False)
    def test_process_death_after_reservation_commit_quarantines_without_replay(self):self.crash(True)

    def test_transaction_exception_rolls_back_original_reservation(self):
        self.advance('RELEASE_VERIFIED')
        def fail(db):raise RuntimeError('EXCLUDED simulated unknown reservation return')
        self.assertRaises(RuntimeError,self.session.reserve,self.request,'EXCLUDED_RESERVATION',_before_commit=fail)
        with self.session.ledger.connection() as db:self.assertEqual(self.session._row(db,self.request)['state'],'RELEASE_VERIFIED')
        self.session.interrupt(self.request);self.assertEqual(self.session.inspect(self.request)['stage'],'REFUSED')

    def test_unknown_consumption_outcome_never_allows_activation(self):
        self.advance('RESERVED')
        def fail(db):raise RuntimeError('EXCLUDED unknown outcome')
        self.assertRaises(RuntimeError,self.session.consume,self.request,'EXCLUDED_RESERVATION',_before_commit=fail)
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')
        self.assertRaises(ValueError,F.deny_activation)
        self.session.interrupt(self.request);self.assertEqual(self.session.inspect(self.request)['stage'],'QUARANTINED')

    def test_original_logs_payloads_and_identity_receipt_preserved(self):
        receipt,root,archive=self.receipt();self.assertEqual(receipt['intentSha256'],A.sha(self.data))
        self.assertEqual(len(receipt['transitions']),6)
        self.assertTrue(any(r['path']=='child.log' for r in receipt['payloadInventory']))
        self.assertIn('NO_PHYSICAL_HOST_ATTESTATION',receipt['provenanceLimitations'])

    def test_prelaunch_failure_preserves_verifier_log_without_inventing_child(self):
        self.session.freeze(self.request,self.data);self.session.interrupt(self.request)
        (self.root/'verifier.log').write_bytes(b'EXCLUDED prelaunch failure')
        receipt=self.session.receipt(self.request,self.root)
        self.assertEqual(receipt['stage'],'REFUSED');self.assertEqual(receipt['evidence']['observations'],[])

    def test_historical_receiving_after_expiry_and_later_comments_cannot_launch(self):
        receipt,root,archive=self.receipt();self.executor.at+=timedelta(days=2)
        self.client.events.append(comment(3,'EXCLUDED_LATER_NONREVOKING_COMMENT',{},self.executor.at))
        proof=F.inspect_historical(receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)
        self.assertEqual(proof['status'],'OWNER_CONTROLLED_RECORD_CORRESPONDENCE_ONLY')
        self.assertFalse(proof['permissionToLaunch']);self.assertFalse(proof['independentExecutionTimeAttestation'])
        self.assertRaises(ValueError,F.deny_activation,proof)

    def test_interrupted_process_evidence_remains_historically_inspectable(self):
        receipt,root,archive=self.receipt(True)
        self.assertEqual(F.inspect_historical(receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)['status'],'OWNER_CONTROLLED_RECORD_CORRESPONDENCE_ONLY')

    def test_historical_missing_separate_owner_archive_denied(self):
        receipt,root,archive=self.receipt();self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root)

    def test_historical_self_declared_timestamp_does_not_authenticate_itself(self):
        receipt,root,archive=self.receipt();receipt['evidence']['finalPrelaunchReadback']['verificationAt']='2026-10-10T12:00:00Z'
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_historical_replayed_game_identity_refused(self):
        receipt,root,archive=self.receipt();receipt['intent']['case']['gameId']='G98'
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_historical_tampered_payload_is_refused(self):
        receipt,root,archive=self.receipt();(root/'child.log').write_bytes(b'EXCLUDED altered')
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_historical_missing_cleanup_is_refused_even_if_archive_agrees(self):
        receipt,root,archive=self.receipt();receipt['evidence']['observations'].pop();archive.receipt=copy.deepcopy(receipt)
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_historical_preexecution_revocation_is_refused(self):
        receipt,root,archive=self.receipt();receipt['evidence']['finalPrelaunchReadback']['history'].append(comment(3,'EXCLUDED_REVOKED',{}));archive.receipt=copy.deepcopy(receipt)
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_process_observation_after_expiry_not_new_execution_permission(self):
        receipt,root,archive=self.receipt();receipt['evidence']['observations'][0]['observedAt']='2026-10-11T12:00:10Z';archive.receipt=copy.deepcopy(receipt)
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_missing_original_log_is_refused(self):
        receipt,root,archive=self.receipt();(root/'child.log').unlink()
        self.assertRaises(ValueError,self.session.receipt,self.request,root)

    def test_payload_symlinks_refused(self):
        self.session.freeze(self.request,self.data);(self.root/'verifier.log').symlink_to(self.path)
        self.assertRaises(ValueError,self.session.receipt,self.request,self.root)

    def test_payload_size_bound_refused(self):
        self.session.freeze(self.request,self.data);(self.root/'verifier.log').write_bytes(b'x'*(1024*1024+1))
        self.assertRaises(ValueError,self.session.receipt,self.request,self.root)

    def test_cli_inspects_intent_but_never_enables_execution(self):
        file=self.root/'EXCLUDED-intent.json';file.write_bytes(self.data)
        p=subprocess.run([sys.executable,'-I','-B',str(Path(F.__file__)),'inspect-intent','--intent',str(file)],capture_output=True)
        self.assertEqual(p.returncode,0);self.assertFalse(A.decode(p.stdout)['gameplayAuthorized'])
        p=subprocess.run([sys.executable,'-I','-B',str(Path(F.__file__)),'activation-status'],capture_output=True)
        self.assertEqual(p.returncode,1);self.assertFalse(A.decode(p.stdout)['engineAllowed'])

    def test_environment_flags_cannot_enable_focused_activation(self):
        env=os.environ.copy();env['executionAuthorized']='true';env['ARGENTUM_FOCUSED_ACTIVATE']='true'
        p=subprocess.run([sys.executable,'-I','-B',str(Path(F.__file__)),'activation-status'],env=env,capture_output=True)
        self.assertEqual(p.returncode,1);self.assertFalse(A.decode(p.stdout)['productionQualified'])

    def test_accepted_integration_default_deny_remains_unchanged(self):
        p=subprocess.run([sys.executable,'-I','-B',str(Path(INTEGRATION.__file__)),'verify-release','--case','EXCLUDED-missing','--run-context','EXCLUDED-missing','--record','EXCLUDED-missing'],capture_output=True)
        self.assertEqual(p.returncode,1);self.assertFalse(A.decode(p.stdout)['engineAllowed'])

    def test_missing_owner_comment_is_not_authenticated_by_its_id(self):
        self.session.freeze(self.request,self.data)
        self.assertRaises(ValueError,self.session.owner_approved,self.request,1)

    def test_deleted_owner_comment_before_consumption_quarantines(self):
        self.advance('RESERVED');del self.client.comments[1]
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')
        self.assertEqual(self.session.inspect(self.request)['stage'],'QUARANTINED')

    def test_lock_file_replacement_is_detected_at_final_boundary(self):
        self.advance('RESERVED');lock=self.session.lock_path;lock.unlink();lock.write_bytes(b'EXCLUDED substitute lock')
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')

    def test_stale_hold_rejected_even_with_long_nonexpired_owner_and_release(self):
        self.advance('RESERVED')
        for number in (1,2):
            self.client.comments[number]['body']=self.client.comments[number]['body'].replace('2026-10-10T12:01:00Z','2026-10-12T12:00:00Z')
        # Replace initial saved owner version consistently for this stale-time
        # unit fixture; it still does not qualify a real approval or history.
        with self.session.ledger.transaction() as db:
            row=self.session._row(db,self.request);e=A.decode(row['evidence']);e['ownerRecord']['owner']=copy.deepcopy(self.client.comments[1])
            db.execute('UPDATE attempts SET evidence=? WHERE request=?',(R.canonical(e),self.request))
        self.executor.at+=timedelta(seconds=61)
        self.assertRaises(ValueError,self.session.consume,self.request,'EXCLUDED_RESERVATION')

    def test_duplicate_and_contradictory_process_observations_refused(self):
        self.advance();self.session.observe_process(self.request,'START',pid=os.getpid())
        self.assertRaises(ValueError,self.session.observe_process,self.request,'START',pid=os.getpid())
        self.assertRaises(ValueError,self.session.observe_process,self.request,'FINISH',pid=os.getpid()+1,exit_code=0)

    def test_partial_owner_record_is_preserved_on_refusal(self):
        self.advance('OWNER_VERIFIED');self.client.comments[1]['body']='EXCLUDED revoked'
        self.assertRaises(ValueError,self.session.hold,self.request)
        (self.root/'verifier.log').write_bytes(b'EXCLUDED hold refusal stderr preserved')
        receipt=self.session.receipt(self.request,self.root)
        self.assertIn('ownerRecord',receipt['evidence']);self.assertEqual(receipt['stage'],'REFUSED')

    def test_concurrent_duplicate_requests_never_create_a_second_consumption(self):
        self.session.close();ctx=multiprocessing.get_context('spawn');barrier=ctx.Barrier(6);queue=ctx.Queue()
        children=[ctx.Process(target=concurrent_worker,args=(str(self.path),self.request,barrier,queue)) for i in range(6)]
        for p in children:p.start()
        records=[queue.get(timeout=40) for p in children]
        for p in children:p.join(30);self.assertEqual(p.exitcode,0)
        queue.close();queue.join_thread()
        self.assertEqual(sum(r['outcome']=='OK' for r in records),1)
        for r in records:
            if r['outcome']=='EXISTING':self.assertFalse(r['record']['newConsumption']);self.assertFalse(r['record']['engineAllowed'])
        with R.LocalLedger(self.path).connection() as db:
            self.assertEqual(db.execute('SELECT count(*) FROM attempts').fetchone()[0],1)
            self.assertEqual(db.execute("SELECT count(*) FROM audit WHERE new_state='CONSUMED'").fetchone()[0],1)
        (self.root/'actual-duplicate-contention.json').write_text(json.dumps(records,indent=2))

    def test_process_death_after_consumption_does_not_make_original_replayable(self):
        self.session.close();ctx=multiprocessing.get_context('spawn');p=ctx.Process(target=consumed_crash_worker,args=(str(self.path),));p.start();p.join(30)
        self.assertEqual(p.exitcode,90)
        with F.FocusedAdmission(self.path,_executor=self.executor,_client=self.client) as session:
            row=session.consume(self.request,'EXCLUDED_RESERVATION')
            self.assertEqual(row['stage'],'ORIGINAL_CONSUMED');self.assertFalse(row['newConsumption']);self.assertFalse(row['engineAllowed'])
            self.assertRaises(sqlite3.IntegrityError,session.freeze,'EXCLUDED_REPLAY',self.data)

    def test_historical_forged_owner_issuer_in_original_record_is_refused(self):
        receipt,root,archive=self.receipt();receipt['evidence']['finalPrelaunchReadback']['owner']['user']['id']=999
        archive.receipt=copy.deepcopy(receipt)
        self.assertRaises(ValueError,F.inspect_historical,receipt,'EXCLUDED_ORIGINAL',root,_owner_archive=archive)

    def test_parallel_fresh_ledger_cannot_split_one_designated_storage_root(self):
        other=self.root/'EXCLUDED-independent-empty.sqlite3';value=intent(other)
        self.assertRaises(ValueError,F.FocusedAdmission,other,_executor=SyntheticExecutor(value),_client=SyntheticGitHub())

    def test_new_ledger_cannot_reset_registered_storage_after_session_closes(self):
        self.session.close();other=self.root/'EXCLUDED-reset-empty.sqlite3';value=intent(other)
        self.assertRaises(ValueError,F.FocusedAdmission,other,_executor=SyntheticExecutor(value),_client=SyntheticGitHub())


def invalid_intent(name, mutate):
    def test(self):
        value=copy.deepcopy(self.value);mutate(value)
        self.assertRaises(ValueError,F.freeze_intent,encoded(value))
    test.__name__='test_intent_rejects_'+name
    setattr(FocusedTests,test.__name__,test)


for name,mutate in {
 'B01':lambda v:v['case'].update(blockId='PLAY_FIRST_B01'),
 'G08':lambda v:v['case'].update(gameId='G08'),
 'official_scope':lambda v:v['case'].update(scope='OFFICIAL'),
 'parallel_games':lambda v:v['case'].update(maxGames=2),
 'boolean_game_count':lambda v:v['case'].update(maxGames=True),
 'replay':lambda v:v['case'].update(originalOnly=False),
 'same_seats':lambda v:v['case'].update(seats={'pest':0,'monster':0}),
 'boolean_seat':lambda v:v['case'].update(seats={'pest':False,'monster':1}),
 'wrong_source_parent':lambda v:v['identities'].update(sourceParent='c'*40),
 'malformed_source_commit':lambda v:v['identities'].update(sourceCommit='not-a-commit'),
 'malformed_engine':lambda v:v['identities'].update(engineManifestSha256='not-a-sha'),
 'future_run_id':lambda v:v.update(runId='EXCLUDED_UNASSIGNED'),
 'future_owner_id':lambda v:v.update(ownerDecisionId=1),
 'self_authorized_flag':lambda v:v.update(executionAuthorized=True),
 'empty_executor':lambda v:v['executor'].update(executorId=''),
 'relative_ledger':lambda v:v['executor'].update(ledgerPath='relative.sqlite3'),
}.items():invalid_intent(name,mutate)


def wrong_observation(key,value):
    def test(self):
        self.executor.facts[key]=value
        self.assertRaises(ValueError,self.session.freeze,self.request,self.data)
    setattr(FocusedTests,'test_executor_rejects_'+key,test)
for key,value in [('persistent',False),('exclusive',False),('posix',False),('ledgerPath','/EXCLUDED-other.sqlite3'),('storageId','EXCLUDED_OTHER_STORAGE'),('executorId','EXCLUDED_OTHER_EXECUTOR')]:wrong_observation(key,value)


def mismatched_identity(key):
    def test(self):
        self.executor.facts['identities'][key]='c'*(64 if key.endswith('Sha256') else 40)
        self.assertRaises(ValueError,self.session.freeze,self.request,self.data)
    setattr(FocusedTests,'test_observed_identity_mismatch_'+key,test)
for key in ['sourceCommit','sourceTree','engineManifestSha256','deckManifestSha256','pilotManifestSha256']:mismatched_identity(key)


def forged_owner(name,mutate):
    def test(self):
        self.session.freeze(self.request,self.data);f=F.freeze_intent(self.data)
        c=comment(1,F.OWNER_CONTRACT,self.session._expected(f,F.OWNER_CONTRACT));mutate(c)
        self.client.comments[1]=c
        self.assertRaises((ValueError,KeyError),self.session.owner_approved,self.request,1)
    setattr(FocusedTests,'test_owner_rejects_'+name,test)
for name,mutate in {
 'wrong_issuer':lambda c:c['user'].update(id=999),
 'wrong_pr':lambda c:c.update(issue_url='https://api.github.com/repos/'+A.REPOSITORY+'/issues/232'),
 'construction_comment':lambda c:c.update(body='EXCLUDED construction authorization; no gameplay'),
 'unverified_comment_flag':lambda c:c.update(body='executionAuthorized: true'),
 'mismatched_digest':lambda c:c.update(body=c['body'].replace('intentSha256','substitutedCaseSha256')),
 'expired':lambda c:c.update(body=c['body'].replace('12:01:00Z','12:00:00Z')),
}.items():forged_owner(name,mutate)


def case_substitution(name,mutate):
    def test(self):
        original=self.session._expected(F.freeze_intent(self.data),F.OWNER_CONTRACT)
        value=copy.deepcopy(self.value);mutate(value)
        self.executor.facts['identities']=copy.deepcopy(value['identities'])
        self.session.freeze(self.request,encoded(value))
        self.client.comments[1]=comment(1,F.OWNER_CONTRACT,original)
        self.assertRaises(ValueError,self.session.owner_approved,self.request,1)
    setattr(FocusedTests,'test_case_specific_owner_refuses_substituted_'+name,test)
for name,mutate in {
 'source':lambda v:v['identities'].update(sourceTree='d'*40),
 'engine':lambda v:v['identities'].update(engineManifestSha256='d'*64),
 'deck':lambda v:v['identities'].update(deckManifestSha256='d'*64),
 'pilot':lambda v:v['identities'].update(pilotManifestSha256='d'*64),
 'game':lambda v:v['case'].update(gameId='G98'),
 'seat':lambda v:v['case'].update(seats={'pest':1,'monster':0}),
}.items():case_substitution(name,mutate)


def cross_bound_release(name,key,value):
    def test(self):
        self.advance('HOLD');f=F.freeze_intent(self.data);e=self.evidence()
        expected=self.session._expected(f,F.RELEASE_CONTRACT,ownerDecisionId=1,requestId=self.request,holdSha256=R.digest(e['hold']))
        expected[key]=value;self.client.comments[2]=comment(2,F.RELEASE_CONTRACT,expected)
        self.assertRaises(ValueError,self.session.final_release,self.request,2)
    setattr(FocusedTests,'test_final_release_refuses_cross_bound_'+name,test)
for name,key,value in [('intent','intentSha256','c'*64),('owner','ownerDecisionId',3),('request','requestId','EXCLUDED_OTHER_REQUEST'),
                       ('hold','holdSha256','c'*64),('ledger','ledgerBindingSha256','c'*64),('scope','scope','OFFICIAL'),
                       ('original','originalOnly',False),('count','maxGames',2)]:cross_bound_release(name,key,value)


if __name__=='__main__':unittest.main(verbosity=2)
