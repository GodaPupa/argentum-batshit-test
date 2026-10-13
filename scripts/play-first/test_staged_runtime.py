"""EXCLUDED infrastructure tests: no authentic provider/owner/host/time claims.

All case/game/source/run labels reuse the previously excluded fixture. No network,
engine, gameplay slot, credential, signer, allocation or workflow is instantiated.
SQLite multiprocessing tests perform genuine overlapping OS-process transactions.
"""
import copy
from datetime import timedelta
import hashlib
import json
import multiprocessing
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import gameplay_authority as AUTH
import staged_runtime as RT
from test_gameplay_authority import NOW, SyntheticTrustedGitHub, synthetic_trusted_fixture
from test_strict_receiving import fixture


class SyntheticIdentityBoundary:
    """Test CODE fixture, never authenticated owner/provider/host/time evidence.

    References are looked up in this separate normative fixture, not accepted as
    authority from a caller artifact. Returning a JSON snapshot is not a production
    implementation. Mutation hooks are only adversarial unit-test seams.
    """
    origin = RT.SYNTHETIC

    def __init__(self, data, run):
        self.data, self.run, self.at = data, copy.deepcopy(run), NOW
        self.client = SyntheticTrustedGitHub(data, run)
        self.owner_history = self.history([self.client.decision])
        self.complete_history = self.history(self.client.timeline)
        self.provider = dict(origin=self.origin, repository=AUTH.REPOSITORY,
                             run=copy.deepcopy(run), sourceVerified=True,
                             identityReference='EXCLUDED_SYNTHETIC_PROVIDER_REFERENCE',
                             engineStarted=False, originalSlotCreated=False)
        self.host = dict(origin=self.origin, hostReference='EXCLUDED_SYNTHETIC_HOST_REFERENCE',
                         codeReference='EXCLUDED_SYNTHETIC_CODE_REFERENCE', codeClean=True,
                         engineStarted=False, originalSlotCreated=False,
                         **{k: run[k] for k in ('runId', 'sourceCommit', 'sourceTree', 'validationCommit', 'validationTree')})
        self.owner = dict(origin=self.origin, decision=self.client.decision, history=self.owner_history)
        self.hold = dict(origin=self.origin, intentSha256=AUTH.sha(data), provider=self.provider,
                         host=self.host, observedAt='2000-01-01T00:00:10Z')
        self.release = dict(origin=self.origin, owner=self.client.decision, release=self.client.release,
                            history=self.complete_history, provider=self.provider, host=self.host)
        self.record, anchor, _ = synthetic_trusted_fixture(data, run)
        self.anchor = dict(origin=self.origin, immutableReference='EXCLUDED_SYNTHETIC_IMMUTABLE_REFERENCE',
                           timeReference='EXCLUDED_SYNTHETIC_TIME_REFERENCE', timeIndependentlyVerified=True,
                           facts=anchor.facts, history=self.history(anchor.facts['history']), anchorDigest=RT.digest(anchor.facts))
        self.calls = []

    def history(self, events):
        return dict(origin=self.origin, events=copy.deepcopy(events), completeThrough='2000-01-01T00:00:30Z',
                    versionHistoryComplete=True, orderingVerified=True,
                    historyReference='EXCLUDED_SYNTHETIC_VERSION_HISTORY_REFERENCE')

    def now(self):
        return self.at

    def _get(self, name, reference, accepted):
        self.calls.append((name, reference))
        if reference != accepted:
            raise RT.Denied('unrecognized/forged independently verified reference')
        return copy.deepcopy(getattr(self, name))

    def authenticate_owner(self, reference):
        return self._get('owner', reference, 'EXCLUDED_OWNER')

    def authenticate_hold(self, reference):
        return self._get('hold', reference, 'EXCLUDED_HOLD')

    def authenticate_release(self, reference):
        return self._get('release', reference, 'EXCLUDED_RELEASE')

    def authenticate_historical(self, reference):
        return self._get('anchor', reference, 'EXCLUDED_SYNTHETIC_ANCHOR_REFERENCE')


def process_worker(path, data, run, action, request, reservation, start, queue):
    """Top-level spawn target: each process has its own genuine SQLite connection."""
    try:
        ledger = RT.LocalLedger(path)
        runtime = RT.StagedRuntime(ledger, _synthetic_fixture_backend=SyntheticIdentityBoundary(data, run))
        start.wait(20)
        if action == 'begin':
            result = ledger.begin(request, data)
        elif action == 'reserve':
            result = runtime.reserve(request, reservation)
        elif action == 'consume':
            result = runtime.consume(request, reservation)
        elif action == 'cancel':
            result = {'state':ledger.cancel(request)}
        else:
            raise AssertionError(action)
        queue.put(('OK', result['state'], os.getpid()))
    except Exception as error:
        queue.put(('DENY', type(error).__name__, os.getpid()))


class RuntimeTests(unittest.TestCase):
    def setUp(self):
        raw, pins = fixture()
        self.data, self.run = raw['approved-case.json'], pins['run']
        fixture_root = os.environ.get('PLAY_FIRST_RUNTIME_FIXTURE_ROOT')
        if fixture_root:
            base = Path(fixture_root) / self.id().rsplit('.', 1)[-1]
            base.mkdir(parents=True, exist_ok=True)
            self.directory = base
        else:
            self.temp = tempfile.TemporaryDirectory()
            self.addCleanup(self.temp.cleanup)
            self.directory = Path(self.temp.name)
        self.path = self.directory / 'ledger.sqlite3'
        self.ledger = RT.LocalLedger(self.path)
        self.boundary = SyntheticIdentityBoundary(self.data, self.run)
        self.runtime = RT.StagedRuntime(self.ledger, _synthetic_fixture_backend=self.boundary)
        self.ledger.begin('EXCLUDED_REQUEST', self.data)

    def advance(self, state='RELEASE_VERIFIED'):
        for target, call in (
                ('OWNER_VERIFIED', lambda: self.runtime.owner_verified('EXCLUDED_REQUEST', 'EXCLUDED_OWNER')),
                ('HOLD', lambda: self.runtime.hold('EXCLUDED_REQUEST', 'EXCLUDED_HOLD')),
                ('RELEASE_VERIFIED', lambda: self.runtime.release('EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')),
                ('RESERVED', lambda: self.runtime.reserve('EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')),
                ('CONSUMED', lambda: self.runtime.consume('EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION'))):
            call()
            if target == state:
                break

    def state(self):
        return self.ledger.inspect('EXCLUDED_REQUEST')['state']

    def history(self):
        with self.ledger.connection() as db:
            return [dict(row) for row in db.execute('SELECT * FROM audit ORDER BY seq')]

    def test_all_allowed_positive_transitions_are_engine_free_and_synthetic_only(self):
        self.advance('CONSUMED')
        self.assertEqual([r['new_state'] for r in self.history()],
                         ['INTENT', 'OWNER_VERIFIED', 'HOLD', 'RELEASE_VERIFIED', 'RESERVED', 'CONSUMED'])
        result = self.ledger.inspect('EXCLUDED_REQUEST')
        self.assertFalse(result['engineAllowed'])
        self.assertTrue(result['evidence']['localConsumptionOnly'])
        self.assertTrue(result['evidence']['timeIsNotAttested'])
        self.assertEqual(result['evidence']['release']['origin'], RT.SYNTHETIC)

    def test_all_transition_pairs_are_explicit_and_terminal_states_have_no_exit(self):
        for old in RT.STATES:
            for new in RT.STATES:
                self.assertEqual(new in RT.ALLOWED[old],
                                 (old, new) in {('INTENT','OWNER_VERIFIED'),('INTENT','CANCELLED'),('INTENT','FAILED'),
                                  ('OWNER_VERIFIED','HOLD'),('OWNER_VERIFIED','CANCELLED'),('OWNER_VERIFIED','FAILED'),
                                  ('HOLD','RELEASE_VERIFIED'),('HOLD','CANCELLED'),('HOLD','FAILED'),
                                  ('RELEASE_VERIFIED','RESERVED'),('RELEASE_VERIFIED','CANCELLED'),('RELEASE_VERIFIED','FAILED'),
                                  ('RESERVED','CONSUMED'),('RESERVED','RECOVERY_REQUIRED')})

    def test_sql_transition_rejects_every_prohibited_pair(self):
        for old in RT.STATES:
            for target in RT.STATES:
                if target in RT.ALLOWED[old]:
                    continue
                with self.subTest(old=old, target=target), self.ledger.transaction() as db:
                    db.execute('UPDATE attempts SET state=? WHERE request=?', (old, 'EXCLUDED_REQUEST'))
                    row = self.ledger.row(db, 'EXCLUDED_REQUEST')
                    self.assertRaises(RT.Denied, RT.LocalLedger.transition, db, row, target, {})

    def test_sql_transition_accepts_every_allowed_pair(self):
        for old, targets in RT.ALLOWED.items():
            for target in targets:
                with self.subTest(old=old,target=target),self.ledger.transaction() as db:
                    db.execute('UPDATE attempts SET state=? WHERE request=?',(old,'EXCLUDED_REQUEST'))
                    row=self.ledger.row(db,'EXCLUDED_REQUEST')
                    RT.LocalLedger.transition(db,row,target,{})
                    self.assertEqual(self.ledger.row(db,'EXCLUDED_REQUEST')['state'],target)

    def test_missing_trusted_backend_denies_and_quarantines_before_provider_calls(self):
        runtime = RT.StagedRuntime(self.ledger)
        self.assertRaises(RT.Denied, runtime.owner_verified, 'EXCLUDED_REQUEST', 'EXCLUDED_OWNER')
        self.assertEqual(self.state(), 'FAILED')

    def test_missing_ledger_denies(self):
        self.assertRaises(RT.Denied, RT.StagedRuntime, None)

    def test_document_cannot_install_provider_identity_backend(self):
        runtime = RT.StagedRuntime(self.ledger, _synthetic_fixture_backend={'origin':RT.SYNTHETIC,'authenticated':True})
        self.assertRaises(RT.Denied, runtime.owner_verified, 'EXCLUDED_REQUEST', 'EXCLUDED_OWNER')
        self.assertEqual(self.state(), 'FAILED')

    def test_self_declared_production_origin_cannot_promote_synthetic_backend(self):
        self.boundary.origin = 'TRUSTED_EXTERNAL_LAUNCH_RELEASE'
        self.assertRaises(RT.Denied, self.runtime.owner_verified, 'EXCLUDED_REQUEST', 'EXCLUDED_OWNER')

    def test_missing_or_non_gameplay_owner_decision_denies(self):
        for decision in (None, {'id':1,'body':'LOCAL NON-GAMEPLAY CONSTRUCTION APPROVED'}):
            with self.subTest(decision=decision):
                self.assertRaises(ValueError, RT.validate_owner,
                                  dict(self.boundary.owner, decision=decision), AUTH.sha(self.data), NOW)

    def test_wrong_owner_actual_issuer_denies(self):
        self.boundary.owner['decision']['user']['id'] += 1
        self.assertRaises(RT.Denied, self.runtime.owner_verified, 'EXCLUDED_REQUEST', 'EXCLUDED_OWNER')

    def test_owner_wrong_intent_or_revocation_denies(self):
        self.boundary.owner_history['events'].append(dict(id=3, user={'id':AUTH.OWNER_ID},
                                                         updated_at='2000-01-01T00:00:25Z',body='REVOKED'))
        self.assertRaises(RT.Denied, self.runtime.owner_verified, 'EXCLUDED_REQUEST', 'EXCLUDED_OWNER')

    def test_unknown_owner_reference_denies(self):
        self.assertRaises(RT.Denied, self.runtime.owner_verified, 'EXCLUDED_REQUEST', 'SELF_ASSERTED_OWNER')

    def test_hold_requires_owner_stage_and_out_of_order_is_terminal(self):
        self.assertRaises(RT.Denied, self.runtime.hold, 'EXCLUDED_REQUEST', 'EXCLUDED_HOLD')
        self.assertEqual(self.state(), 'FAILED')
        self.assertRaises(RT.Denied, self.runtime.owner_verified, 'EXCLUDED_REQUEST', 'EXCLUDED_OWNER')

    def test_release_before_hold_denies(self):
        self.advance('OWNER_VERIFIED')
        self.assertRaises(RT.Denied, self.runtime.release, 'EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')
        self.assertEqual(self.state(), 'FAILED')

    def test_reservation_before_run_specific_release_denies(self):
        self.advance('HOLD')
        self.assertRaises(RT.Denied, self.runtime.reserve, 'EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')
        self.assertEqual(self.state(), 'HOLD')

    def test_missing_provider_or_host_evidence_denies(self):
        self.advance('OWNER_VERIFIED')
        self.boundary.hold['host'] = None
        self.assertRaises(RT.Denied, self.runtime.hold, 'EXCLUDED_REQUEST', 'EXCLUDED_HOLD')
        self.assertEqual(self.state(), 'FAILED')

    def test_ambiguous_extra_identity_field_denies(self):
        self.boundary.provider['selfAuthenticated'] = True
        self.advance('OWNER_VERIFIED')
        self.assertRaises(RT.Denied, self.runtime.hold, 'EXCLUDED_REQUEST', 'EXCLUDED_HOLD')

    def test_contradictory_provider_run_or_head_denies(self):
        self.advance('OWNER_VERIFIED')
        self.boundary.provider['run']['validationCommit'] = 'f'*40
        self.assertRaises(RT.Denied, self.runtime.hold, 'EXCLUDED_REQUEST', 'EXCLUDED_HOLD')

    def test_contradictory_host_code_tree_denies(self):
        self.advance('OWNER_VERIFIED')
        self.boundary.host['validationTree'] = 'f'*40
        self.assertRaises(RT.Denied, self.runtime.hold, 'EXCLUDED_REQUEST', 'EXCLUDED_HOLD')

    def test_engine_initialized_or_slot_created_hold_denies(self):
        for field in ('engineStarted', 'originalSlotCreated'):
            with self.subTest(field=field):
                provider = copy.deepcopy(self.boundary.provider)
                provider[field] = True
                self.assertRaises(RT.Denied, RT.validate_provider, provider,
                                  AUTH.freeze_intent(self.data)['intent'], held=True)

    def test_hold_before_owner_decision_denies(self):
        self.advance('OWNER_VERIFIED')
        self.boundary.hold['observedAt']='1999-12-31T23:59:59Z'
        self.assertRaises(RT.Denied, self.runtime.hold, 'EXCLUDED_REQUEST', 'EXCLUDED_HOLD')

    def test_interrupted_or_stale_release_denies(self):
        self.advance('HOLD')
        self.boundary.at += timedelta(seconds=61)
        self.assertRaises(RT.Denied, self.runtime.release, 'EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')

    def test_wrong_release_intent_or_run_denies(self):
        self.advance('HOLD')
        self.boundary.release['provider']['run']['runId'] = '2'
        self.assertRaises(RT.Denied, self.runtime.release, 'EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')

    def test_provider_host_substitution_after_hold_denies(self):
        self.advance('HOLD')
        self.boundary.host['hostReference'] += '_SUBSTITUTED'
        self.assertRaises(RT.Denied, self.runtime.release, 'EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')

    def test_preexecution_revocation_denies_release(self):
        self.advance('HOLD')
        self.boundary.complete_history['events'].append(dict(id=3,user={'id':AUTH.OWNER_ID},
                                                            updated_at='2000-01-01T00:00:25Z',body='REVOKE'))
        self.assertRaises(RT.Denied, self.runtime.release, 'EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')

    def test_revocation_between_release_and_reservation_denies(self):
        self.advance()
        self.boundary.complete_history['events'].append(dict(id=3,user={'id':AUTH.OWNER_ID},
                                                            updated_at='2000-01-01T00:00:25Z',body='REVOKE'))
        self.assertRaises(RT.Denied, self.runtime.reserve, 'EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')
        self.assertEqual(self.state(), 'FAILED')

    def test_revocation_between_reservation_and_consumption_quarantines(self):
        self.advance('RESERVED')
        self.boundary.complete_history['events'].append(dict(id=3,user={'id':AUTH.OWNER_ID},
                                                            updated_at='2000-01-01T00:00:25Z',body='REVOKE'))
        self.assertRaises(RT.Denied, self.runtime.consume, 'EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')
        self.assertEqual(self.state(), 'RECOVERY_REQUIRED')

    def test_missing_or_wrong_reservation_cannot_be_consumed(self):
        self.advance('RESERVED')
        self.assertRaises(RT.Denied, self.runtime.consume, 'EXCLUDED_REQUEST', 'WRONG')
        self.assertEqual(self.state(), 'RESERVED')

    def test_already_consumed_same_request_is_idempotent_without_second_effect(self):
        self.advance('CONSUMED')
        count = len(self.history())
        self.runtime.consume('EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')
        self.assertEqual(len(self.history()), count)
        self.assertRaises(RT.Denied, self.runtime.reserve, 'EXCLUDED_REQUEST', 'NEW')

    def test_replay_new_request_for_same_original_denies(self):
        self.advance('CONSUMED')
        self.assertRaises(RT.Denied, self.ledger.begin, 'SECOND_REQUEST', self.data)

    def test_whitespace_variant_does_not_bypass_original_uniqueness(self):
        variant = json.dumps(json.loads(self.data), indent=2).encode()
        self.assertNotEqual(AUTH.sha(variant), AUTH.sha(self.data))
        self.assertRaises(RT.Denied, self.ledger.begin, 'SECOND_REQUEST', variant)

    def test_new_workflow_ref_cannot_replay_same_logical_original(self):
        changed=json.loads(self.data)
        changed['plan']['ref']='refs/heads/EXCLUDED_REPLAY_VARIANT'
        self.assertRaises(RT.Denied,self.ledger.begin,'SECOND_REQUEST',json.dumps(changed).encode())

    def test_source_revision_cannot_replay_same_logical_original(self):
        changed=json.loads(self.data)
        changed['case']['sourceCommit']=changed['plan']['sourceCommit']='a'*40
        self.assertRaises(RT.Denied,self.ledger.begin,'SECOND_REQUEST',json.dumps(changed).encode())

    def test_same_request_altered_bytes_denies(self):
        self.assertRaises(RT.Denied, self.ledger.begin, 'EXCLUDED_REQUEST', self.data+b' ')

    def test_duplicate_submission_is_idempotent(self):
        first = len(self.history())
        self.ledger.begin('EXCLUDED_REQUEST', self.data)
        self.assertEqual(len(self.history()), first)

    def test_rollback_before_reservation_commit_has_no_effect(self):
        self.advance()
        count = len(self.history())
        def fail(db):
            raise RuntimeError('EXCLUDED injected interruption before commit')
        self.assertRaises(RuntimeError, self.runtime.reserve, 'EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION', _before_commit=fail)
        self.assertEqual(self.state(), 'RELEASE_VERIFIED')
        self.assertEqual(len(self.history()), count)
        self.runtime.reserve('EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')
        self.assertEqual(self.state(), 'RESERVED')

    def test_trusted_backend_disappearing_after_reservation_quarantines_consumption(self):
        self.advance('RESERVED')
        self.runtime.boundary=RT.UnavailableBoundary()
        self.assertRaises(RT.Denied,self.runtime.consume,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        self.assertEqual(self.state(),'RECOVERY_REQUIRED')

    def test_stale_release_at_consumption_quarantines_original(self):
        self.advance('RESERVED')
        self.boundary.at+=timedelta(seconds=61)
        self.assertRaises(RT.Denied,self.runtime.consume,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')
        self.assertEqual(self.state(),'RECOVERY_REQUIRED')

    def test_committed_reservation_survives_restart_but_explicit_recovery_quarantines(self):
        self.advance('RESERVED')
        ledger = RT.LocalLedger(self.path)
        self.assertEqual(ledger.inspect('EXCLUDED_REQUEST')['state'], 'RESERVED')
        self.assertEqual(ledger.recover_interrupted('EXCLUDED_REQUEST'), 'RECOVERY_REQUIRED')
        self.assertEqual(ledger.recover_interrupted('EXCLUDED_REQUEST'), 'RECOVERY_REQUIRED')
        self.assertRaises(RT.Denied, self.runtime.consume, 'EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')
        self.assertRaises(RT.Denied, ledger.begin, 'REPLAY', self.data)

    def test_pre_reservation_cancel_is_terminal_idempotent_and_not_reusable(self):
        self.advance('HOLD')
        self.assertEqual(self.ledger.cancel('EXCLUDED_REQUEST'), 'CANCELLED')
        self.assertEqual(self.ledger.cancel('EXCLUDED_REQUEST'), 'CANCELLED')
        self.assertRaises(RT.Denied, self.runtime.release, 'EXCLUDED_REQUEST', 'EXCLUDED_RELEASE')
        self.assertRaises(RT.Denied, self.ledger.begin, 'REPLAY', self.data)

    def test_ambiguous_reserved_cancel_requires_separate_disposition(self):
        self.advance('RESERVED')
        self.assertEqual(self.ledger.cancel('EXCLUDED_REQUEST'), 'RECOVERY_REQUIRED')
        self.assertRaises(RT.Denied, self.runtime.consume, 'EXCLUDED_REQUEST', 'EXCLUDED_RESERVATION')

    def test_cancel_after_consumption_cannot_erase_original(self):
        self.advance('CONSUMED')
        self.assertRaises(RT.Denied, self.ledger.cancel, 'EXCLUDED_REQUEST')
        self.assertEqual(self.state(), 'CONSUMED')

    def test_empty_missing_or_symlink_database_path_denies(self):
        symlink = self.directory/'link.sqlite3'
        symlink.symlink_to(self.path)
        self.assertRaises(RT.Denied, RT.LocalLedger, symlink)
        self.assertRaises(RT.Denied, RT.LocalLedger, self.directory/'missing'/'db')

    def test_database_integrity_and_actual_durability_settings(self):
        self.advance('CONSUMED')
        with self.ledger.connection() as db:
            self.assertEqual(db.execute('PRAGMA integrity_check').fetchone()[0], 'ok')
            self.assertEqual(db.execute('PRAGMA journal_mode').fetchone()[0], 'wal')
            self.assertEqual(db.execute('PRAGMA synchronous').fetchone()[0], 2)
        self.assertEqual(self.ledger.inspect('EXCLUDED_REQUEST')['origin'], RT.LOCAL_LEDGER)

    def test_sql_unique_reservation_rejects_cross_bound_second_logical_row(self):
        self.advance('RESERVED')
        with self.ledger.transaction() as db:
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('INSERT INTO attempts(request,original_key,intent_hash,intent,state,evidence,reservation) VALUES(?,?,?,?,?,?,?)',
                           ('EXCLUDED_SQL_ONLY_ROW','EXCLUDED_SQL_ONLY_KEY',AUTH.sha(self.data),self.data,
                            'RESERVED','{}','EXCLUDED_RESERVATION'))

    def parallel(self, action, requests, reservations):
        context = multiprocessing.get_context('spawn')
        # The parent joins only after every child has opened its own connection.
        # This prevents fast workers from completing before slow workers are ready.
        event, queue = context.Barrier(len(requests)+1), context.Queue()
        actions=action if isinstance(action,list) else [action]*len(requests)
        processes = [context.Process(target=process_worker,args=(str(self.path),self.data,self.run,a,r,s,event,queue))
                     for a,r,s in zip(actions,requests,reservations)]
        for process in processes:
            process.start()
        event.wait(45)
        results = [queue.get(timeout=45) for _ in processes]
        for process in processes:
            process.join(45)
            self.assertEqual(process.exitcode, 0)
        self.assertEqual(len({item[2] for item in results}), len(processes))
        label='mixed' if isinstance(action,list) else action
        (self.directory/('concurrency-'+label+'.json')).write_text(json.dumps(dict(processes=len(processes),startMethod='spawn',synchronizedStartBarrier=True,actions=actions,results=results),indent=2)+'\n')
        return results

    def test_genuine_concurrent_distinct_requests_cannot_reserve_same_original(self):
        # A fresh store has no pre-existing winner: eight simultaneous contenders.
        self.path = self.directory/'race.sqlite3'
        self.ledger = RT.LocalLedger(self.path)
        results = self.parallel('begin', ['EXCLUDED_RACE_'+str(i) for i in range(8)], ['unused']*8)
        self.assertEqual(sum(item[0]=='OK' for item in results),1)
        with self.ledger.connection() as db:
            self.assertEqual(db.execute('SELECT COUNT(*) FROM attempts').fetchone()[0],1)

    def test_genuine_concurrent_same_request_duplicate_reservation_is_idempotent(self):
        self.advance()
        results = self.parallel('reserve', ['EXCLUDED_REQUEST']*8, ['EXCLUDED_RESERVATION']*8)
        self.assertEqual(sum(item[0]=='OK' for item in results),8)
        self.assertEqual(sum(row['new_state']=='RESERVED' for row in self.history()),1)

    def test_genuine_concurrent_conflicting_reservations_one_winner(self):
        self.advance()
        results = self.parallel('reserve', ['EXCLUDED_REQUEST']*8, ['EXCLUDED_R_'+str(i) for i in range(8)])
        self.assertEqual(sum(item[0]=='OK' for item in results),1)
        self.assertEqual(sum(row['new_state']=='RESERVED' for row in self.history()),1)

    def test_genuine_concurrent_duplicate_consumption_only_one_transition(self):
        self.advance('RESERVED')
        results = self.parallel('consume', ['EXCLUDED_REQUEST']*8, ['EXCLUDED_RESERVATION']*8)
        self.assertEqual(sum(item[0]=='OK' for item in results),8)
        self.assertEqual(sum(row['new_state']=='CONSUMED' for row in self.history()),1)

    def test_genuine_concurrent_cancellation_vs_consumption_has_one_terminal_disposition(self):
        self.advance('RESERVED')
        self.parallel(['consume','cancel'],['EXCLUDED_REQUEST']*2,['EXCLUDED_RESERVATION']*2)
        self.assertIn(self.state(),('CONSUMED','RECOVERY_REQUIRED'))
        terminal=[r for r in self.history() if r['new_state'] in ('CONSUMED','RECOVERY_REQUIRED')]
        self.assertEqual(len(terminal),1)
        self.assertRaises(RT.Denied,self.ledger.begin,'REPLAY',self.data)

    def test_process_death_before_commit_rolls_back_actual_sqlite_transaction(self):
        self.advance()
        script = '''import os,sys\nfrom pathlib import Path\nsys.path.insert(0,sys.argv[1])\nimport staged_runtime as RT\nfrom test_staged_runtime import SyntheticIdentityBoundary\nfrom test_strict_receiving import fixture\nraw,pins=fixture();ledger=RT.LocalLedger(sys.argv[2]);runtime=RT.StagedRuntime(ledger,_synthetic_fixture_backend=SyntheticIdentityBoundary(raw['approved-case.json'],pins['run']))\nruntime.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION',_before_commit=lambda db:os._exit(88))\n'''
        result = subprocess.run([sys.executable,'-B','-c',script,str(Path(__file__).parent),str(self.path)],capture_output=True)
        (self.directory/'crash.stdout.log').write_bytes(result.stdout)
        (self.directory/'crash.stderr.log').write_bytes(result.stderr)
        (self.directory/'crash-result.json').write_text(json.dumps(dict(exitCode=result.returncode,expected=88),indent=2)+'\n')
        self.assertEqual(result.returncode,88)
        self.assertEqual(self.state(),'RELEASE_VERIFIED')
        self.assertEqual(sum(r['new_state']=='RESERVED' for r in self.history()),0)
        self.runtime.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION')

    def test_process_death_after_commit_preserves_reservation_and_requires_recovery(self):
        self.advance()
        script='''import os,sys\nsys.path.insert(0,sys.argv[1])\nimport staged_runtime as RT\nfrom test_staged_runtime import SyntheticIdentityBoundary\nfrom test_strict_receiving import fixture\nraw,pins=fixture();ledger=RT.LocalLedger(sys.argv[2]);runtime=RT.StagedRuntime(ledger,_synthetic_fixture_backend=SyntheticIdentityBoundary(raw['approved-case.json'],pins['run']))\nruntime.reserve('EXCLUDED_REQUEST','EXCLUDED_RESERVATION');os._exit(88)\n'''
        result=subprocess.run([sys.executable,'-B','-c',script,str(Path(__file__).parent),str(self.path)],capture_output=True)
        (self.directory/'postcommit-crash.stdout.log').write_bytes(result.stdout)
        (self.directory/'postcommit-crash.stderr.log').write_bytes(result.stderr)
        (self.directory/'postcommit-crash-result.json').write_text(json.dumps(dict(exitCode=result.returncode,expected=88),indent=2)+'\n')
        self.assertEqual(result.returncode,88)
        self.assertEqual(self.state(),'RESERVED')
        self.assertEqual(self.ledger.recover_interrupted('EXCLUDED_REQUEST'),'RECOVERY_REQUIRED')
        self.assertRaises(RT.Denied,self.runtime.consume,'EXCLUDED_REQUEST','EXCLUDED_RESERVATION')


class HistoricalInfrastructureTests(unittest.TestCase):
    def setUp(self):
        raw,pins=fixture()
        self.data,self.run=raw['approved-case.json'],pins['run']
        self.backend=SyntheticIdentityBoundary(self.data,self.run)
        self.record=self.backend.record

    def inspect(self):
        return RT.inspect_historical(self.data,self.run,self.record,'8'*64,_synthetic_fixture_backend=self.backend)

    def update_anchor_hash(self):
        self.backend.anchor['anchorDigest']=RT.digest(self.backend.anchor['facts'])

    def test_valid_synthetic_historical_correspondence_is_never_production_time(self):
        result=self.inspect()
        self.assertFalse(result['engineAllowed'])
        self.assertFalse(result['independentlyTrustedProductionOrigin'])
        self.assertFalse(result['localLedgerAuthenticatesTime'])
        self.assertEqual(result['provenance']['origin'],RT.SYNTHETIC)

    def test_no_backend_denies_unsigned_correct_snapshot(self):
        self.assertRaises(RT.Denied,RT.inspect_historical,self.data,self.run,self.record,'8'*64)

    def test_forged_snapshot_and_self_declared_time_are_not_backend(self):
        self.assertRaises((RT.Denied,AttributeError),RT.inspect_historical,self.data,self.run,self.record,'8'*64,
                          _synthetic_fixture_backend=self.backend.anchor)
        self.record['executedAt']='2000-01-01T00:00:30Z'
        self.assertRaises(RT.Denied,self.inspect)

    def test_unknown_or_self_authenticating_anchor_reference_denies(self):
        self.record['anchorRef']='SHA256_IS_NOT_EXTERNAL_ATTESTATION'
        self.assertRaises(RT.Denied,self.inspect)

    def test_missing_immutable_or_time_anchor_denies(self):
        for field in ('immutableReference','timeReference'):
            original=self.backend.anchor[field]
            self.backend.anchor[field]=''
            self.assertRaises(RT.Denied,self.inspect)
            self.backend.anchor[field]=original

    def test_local_hash_chain_does_not_authenticate_time(self):
        self.backend.anchor['timeIndependentlyVerified']=False
        self.assertRaises(RT.Denied,self.inspect)

    def test_forged_timestamp_alters_immutable_anchor_digest(self):
        self.backend.anchor['facts']['executedAt']='2000-01-01T00:00:29Z'
        self.assertRaisesRegex(RT.Denied,'altered',self.inspect)

    def test_missing_complete_version_history_denies(self):
        self.backend.anchor['history']['versionHistoryComplete']=False
        self.assertRaises(RT.Denied,self.inspect)

    def test_missing_history_ordering_authentication_denies(self):
        self.backend.anchor['history']['orderingVerified']=False
        self.assertRaises(RT.Denied,self.inspect)

    def test_actual_history_out_of_order_denies(self):
        self.backend.anchor['history']['events'].reverse()
        self.assertRaises(RT.Denied,self.inspect)

    def test_duplicate_conflicting_history_versions_deny(self):
        event=copy.deepcopy(self.backend.anchor['history']['events'][0])
        event['body']='REVOKED AT SAME INDEPENDENT TIME'
        self.backend.anchor['history']['events'].insert(1,event)
        self.assertRaises(RT.Denied,self.inspect)

    def test_history_not_complete_through_actual_execution_denies(self):
        self.backend.anchor['history']['completeThrough']='2000-01-01T00:00:29Z'
        self.assertRaises(RT.Denied,self.inspect)

    def test_preexecution_revocation_denies(self):
        event=dict(id=3,user={'id':AUTH.OWNER_ID},updated_at='2000-01-01T00:00:25Z',body='REVOKE')
        self.backend.anchor['history']['events'].append(event)
        self.backend.anchor['facts']['history'].append(event)
        self.update_anchor_hash()
        self.assertRaises(ValueError,self.inspect)

    def test_postexecution_expiry_and_nonrevoking_comments_do_not_erase_original(self):
        self.backend.at+=timedelta(days=365)
        event=dict(id=3,user={'id':AUTH.OWNER_ID},updated_at='2000-01-02T00:00:00Z',body='LATER RECEIPT')
        self.backend.anchor['history']['events'].append(event)
        self.backend.anchor['facts']['history'].append(event)
        self.update_anchor_hash()
        with patch.object(self.backend,'now',side_effect=AssertionError('Historical must not demand fresh execution')):
            self.assertFalse(self.inspect()['engineAllowed'])

    def test_replayed_cross_bound_manifest_run_or_consumption_denies(self):
        for field in ('manifestSha256','run','originalConsumption'):
            facts=copy.deepcopy(self.backend.anchor['facts'])
            if field=='manifestSha256':facts[field]='9'*64
            elif field=='run':facts[field]['runId']='2'
            else:facts[field]['count']=2
            original=self.backend.anchor['facts']
            self.backend.anchor['facts']=facts
            self.update_anchor_hash()
            self.assertRaises(ValueError,self.inspect)
            self.backend.anchor['facts']=original
            self.update_anchor_hash()

    def test_repeat_receiving_same_original_is_read_only_idempotent(self):
        original=copy.deepcopy(self.backend.anchor)
        self.assertEqual(self.inspect(),self.inspect())
        self.assertEqual(self.backend.anchor,original)

    def test_integration_preserves_engine_body_and_production_denial(self):
        root=Path(__file__).resolve().parents[2]
        adapter='gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt'
        baseline=subprocess.check_output(['git','-C',str(root),'show','4d5d91bbff7ec5a6e75d5832eff0ee9ebd7debb6:'+adapter])
        current=(root/adapter).read_bytes().replace(AUTH.SOURCE_PARENT.encode(),b'c02505119423b526759d97afe5a501aa949b31f4')
        marker=b'        PlayFirstTimingJournal('
        self.assertEqual(current[current.index(marker):],baseline[baseline.index(marker):])
        with self.assertRaisesRegex(ValueError,'UNQUALIFIED'):
            AUTH.verify_historical(self.data,self.run,self.record,'8'*64)

    def test_prototype_has_no_engine_launcher_or_trace_creation_interface(self):
        source=Path(RT.__file__).read_text()
        self.assertNotIn('ProcessBuilder(',source)
        self.assertNotIn('initializeGame(',source)
        self.assertNotIn('PlayFirstTimingJournal(',source)
        self.assertNotIn('create_original_trace(',source)
        self.assertFalse(hasattr(RT.StagedRuntime,'launch'))


if __name__=='__main__':
    unittest.main(verbosity=2)
