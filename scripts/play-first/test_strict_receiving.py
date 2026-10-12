"""EXCLUDED synthetic bytes only. No repository engine, initializer, or game runs.

The fixture's numeric labels are parser edge cases, never allocated game inputs.
Every positive result is correspondence to deliberately fabricated unit records.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('strict_receiving', HERE / 'strict_receiving.py')
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)


def encoded(value):
    return (json.dumps(value, sort_keys=True) + '\n').encode()


def jsonl(value):
    return b''.join(encoded(r) for r in value)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def fixture():
    run = dict(runId='1', runAttempt=1, runNumber=1, event='push', ref='refs/heads/EXCLUDED_SYNTHETIC_ONLY',
               sourceCommit='1' * 40, sourceParent='7b2b3afc84bdea530bfd69e0fc2691a4c837ca23', sourceTree='2' * 40, validationCommit='3' * 40,
               validationTree='4' * 40, workflowPath='.github/workflows/EXCLUDED.yml', workflowBlob='5' * 40)
    case = dict(blockId='PLAY_FIRST_EXCLUDED_RECEIVER_UNIT_ONLY', gameId='G999', pair=1, pestSeat=0,
                fixtureSeedHex='0x0000000000000001', authorizationComment=1,
                sourceCommit=run['sourceCommit'], scope=MOD.SCOPE, executionAuthorized=True, intendedBlockGames=1)
    pins = dict(run=run, caseSha256=sha(encoded(case)), runSourceSha256=sha(encoded(run)),
                command=['EXCLUDED_COMMAND_NOT_EXECUTED'], budgetSeconds=10, graceSeconds=1,
                pestMainSha256='6' * 64, monsterTronMainSha256='7' * 64)
    binding = dict(GITHUB_RUN_ID='1', GITHUB_RUN_ATTEMPT='1', GITHUB_SHA=run['validationCommit'], GITHUB_REF=run['ref'],
                   PLAY_FIRST_ISOLATED_SOURCE_SHA=run['sourceCommit'], PLAY_FIRST_ISOLATED_CASE_SHA256=pins['caseSha256'])
    process = dict(identity='G999', runBinding=binding, processStatus='EXITED_ZERO', returnCode=0,
                   cleanupUncertain=False, residualGroupKilled=False, cancellationSignal=None, elapsedSeconds=1.0)
    process_log = [dict(recordType='PROCESS_INTENT', identity='G999', runBinding=binding,
                        command=pins['command'], budgetSeconds=10, graceSeconds=1, pid=1, monotonicNanos=10),
                   dict(recordType='PROCESS_STARTED', childPid=2, monotonicNanos=20),
                   dict(recordType='PROCESS_HEARTBEAT', elapsedSeconds=0.5),
                   dict(recordType='PROCESS_FINISHED', **process)]
    start = dict(recordType='EXHIBITION_INTENT', **{k: v for k, v in case.items() if k != 'executionAuthorized'},
                 sourceParent=run['sourceParent'], caseInputSha256=pins['caseSha256'], formalSeedAllocation=False, officialExecutionAuthorized=False,
                 experimentalResult=False, intendedGamesThisCase=1, startingSeat=0, maxActions=12000,
                 maxTurns=60, maxActionsPerTurn=500, seat0Deck='Pest Control v1.0', seat1Deck='mehanske Monster Tron',
                 pestMainSha256=pins['pestMainSha256'], monsterTronMainSha256=pins['monsterTronMainSha256'])
    trace = [start, dict(recordType='INITIALIZED', gameId='G999', gameOver=False, player0='fixture0', player1='fixture1')]
    planned = [('REGISTRY_CREATE', {}), ('ENVIRONMENT_CREATE', {}), ('INITIALIZE', {})]
    action_types = ['KeepHand', 'TakeMulligan', 'KeepHand', 'BottomCards', 'PassPriority', 'SubmitDecision']
    actors = ['fixture0', 'fixture1', 'fixture1', 'fixture1', 'fixture0', 'fixture1']
    for seq, (kind, actor) in enumerate(zip(action_types, actors), 1):
        if seq == 5:
            trace.append(dict(recordType='MULLIGAN_COMPLETE', gameId='G999', actions=4))
            planned.append(('AGENTS_CREATE', {}))
        pending = 'EXCLUDED_DECISION' if seq == 6 else ''
        step = 'PRECOMBAT_MAIN'
        trace.append(dict(recordType='ACTION_INTENT', sequence=seq, actor=actor, turn=1, step=step,
                          pendingDecision=pending, selectedAction=dict(type=kind, playerId=actor)))
        trace.append(dict(recordType='ACTION_RESULT', sequence=seq, accepted=True, rejectionReason='',
                          gameOverAfter=seq == 6, turnAfter=1, lifeAfter=[1, 0] if seq == 6 else [20, 20],
                          emittedEvents=[dict(type='GameEndedEvent', winnerId='fixture0')] if seq == 6 else []))
        phase = 'MULLIGAN_SELECT' if seq < 4 else 'BOTTOM_SELECT' if seq == 4 else 'CHOOSE_ACTION' if seq == 5 else 'RESPOND_DECISION'
        ctx = dict(actor=actor, nextSequence=str(seq))
        if seq >= 5:
            ctx.update(seat=str(int(actor[-1])), turn='1', step=step, pendingDecision=pending)
        planned.extend([(phase, ctx), ('ENGINE_SUBMIT', dict(sequence=str(seq), actor=actor, turn='1', step=step))])
    trace.append(dict(recordType='EXHIBITION_TERMINAL', gameId='G999', pair=1, pestSeat=0, gameOver=True,
                      actions=6, turn=1, formalExperimentCount=0, scope=MOD.SCOPE, lifeBySeat=[1, 0], winnerId='fixture0', winnerSeat=0))
    identity = {k: str(case[k]) for k in ('blockId', 'gameId', 'sourceCommit', 'authorizationComment')}
    identity['caseInputSha256'] = pins['caseSha256']
    timing = []
    for span, (phase, context) in enumerate(planned, 1):
        common = dict(span=span, phase=phase, context=context, identity=identity, threadId=1)
        timing.extend([dict(recordType='CALL_START', monotonicNanos=span * 10, **common),
                       dict(recordType='CALL_END', monotonicNanos=span * 10 + 1, elapsedNanos=1, **common)])
    raw = {'approved-case.json': encoded(case), 'run-source.json': encoded(run),
           'case/original.jsonl': jsonl(trace), 'case/timing.jsonl': jsonl(timing),
           'case/process-result.json': encoded(process), 'case/process.jsonl': jsonl(process_log),
           'case/child.log': b'EXCLUDED SYNTHETIC BYTES; NO CHILD OR ENGINE WAS RUN\n'}
    from test_gameplay_authority import synthetic_trusted_fixture
    proof, _, _ = synthetic_trusted_fixture(raw['approved-case.json'], run)
    pins['gameplayApproval'] = proof
    trace[0]['authorityContract'] = proof['contract']
    trace[0]['authorityDecisionBodySha256'] = proof['decisionBodySha256']
    raw['case/original.jsonl'] = jsonl(trace)
    return raw, pins


class StrictReceivingTests(unittest.TestCase):
    def setUp(self):
        configured = os.environ.get('PLAY_FIRST_STRICT_FIXTURE_ROOT')
        if configured:
            self.root = Path(configured).resolve() / self._testMethodName
            self.root.mkdir(parents=True, exist_ok=False)
        else:
            self.tmp = tempfile.TemporaryDirectory()
            self.addCleanup(self.tmp.cleanup)
            self.root = Path(self.tmp.name)
        self.raw, self.pins = fixture()
        from test_gameplay_authority import synthetic_trusted_fixture
        _, self.authority_client, self.authority_now = synthetic_trusted_fixture(self.raw['approved-case.json'], self.pins['run'])

    def change(self, path, edit, lines=False):
        value = [json.loads(x) for x in self.raw[path].splitlines()] if lines else json.loads(self.raw[path])
        edit(value)
        self.raw[path] = jsonl(value) if lines else encoded(value)

    def write(self, reseal=True):
        if reseal:
            manifest = [dict(path=k.removeprefix('case/'), bytes=len(v), sha256=sha(v))
                        for k, v in sorted(self.raw.items()) if k.startswith('case/') and k != 'case/supervisor-manifest.json']
            self.raw['case/supervisor-manifest.json'] = encoded(manifest)
            self.pins['manifestSha256'] = sha(encoded(manifest))
        for k, data in self.raw.items():
            path = self.root / k
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        return self.root

    def inspect(self, reseal=True):
        report = MOD.inspect_evidence(self.write(reseal), self.pins, _authority_client=self.authority_client, _authority_now=self.authority_now)
        (self.root / 'inspection-report.json').write_bytes(encoded(report))
        return report

    def refused(self, reseal=True):
        report = self.inspect(reseal)
        self.assertEqual(report['status'], 'INCOMPLETE', report)
        self.assertTrue(report['errors'])
        return report

    def test_synthetic_positive_never_authenticates_game(self):
        report = self.inspect()
        self.assertEqual(report['status'], 'PINNED_RECORD_CORRESPONDENCE_ONLY', report)
        self.assertEqual((report['actions'], report['timingCalls']), (6, 16))
        self.assertFalse(report['gameplayAuthorized'])
        self.assertFalse(report['authenticatedOrigin'])
        self.assertFalse(report['engineReplayed'])

    def test_fixed_width_leading_zero_seed_preserved(self):
        self.assertEqual(json.loads(self.raw['approved-case.json'])['fixtureSeedHex'], '0x0000000000000001')
        self.assertEqual(self.inspect()['status'], 'PINNED_RECORD_CORRESPONDENCE_ONLY')

    def test_adapter_game_body_remains_frozen_except_authority_stamp_and_parent(self):
        path = HERE.parents[1] / 'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt'
        data = path.read_bytes()
        original = subprocess.check_output(['git', '-C', str(HERE.parents[1]), 'show',
            '7b2b3afc84bdea530bfd69e0fc2691a4c837ca23:gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt'])
        self.assertEqual(hashlib.sha1(b'blob ' + str(len(original)).encode() + b'\0' + original).hexdigest(),
                         '0006735c9f482168e8c19ad7796cb5bb9abc4d55')
        anchor = b'        PlayFirstTimingJournal('
        body = data[data.index(anchor):].replace(
            b'put("sourceParent", "7b2b3afc84bdea530bfd69e0fc2691a4c837ca23")',
            b'put("sourceParent", "9a4230e0d2acbc38b1e8a7d3e6c3cda5dd41e459")')
        body = body.replace(b'                put("authorityContract", "ARGENTUM_GAMEPLAY_AUTHORITY_V1")\n', b'').replace(
            b'                put("authorityDecisionBodySha256", authority.getValue("decisionBodySha256").jsonPrimitive.content)\n', b'')
        self.assertEqual(body, original[original.index(anchor):])

    def test_missing_positive_external_approval_is_rejected(self):
        del self.pins['gameplayApproval']
        self.refused()

    def test_non_gameplay_live_decision_is_rejected(self):
        self.authority_client.decision['body'] = 'SOURCE REPAIR ONLY. NO GAMEPLAY.'
        self.refused()

    def test_self_asserted_positive_approval_is_rejected(self):
        self.pins['gameplayApproval'] = {'executionAuthorized': True, 'externalVerified': True}
        self.refused()

    def test_adapter_authority_contract_disagreement_is_rejected(self):
        self.change('case/original.jsonl', lambda records: records[0].update(authorityContract='OLD_DENYLIST_ONLY'), True)
        self.refused()

    def test_adapter_authority_decision_body_disagreement_is_rejected(self):
        self.change('case/original.jsonl', lambda records: records[0].update(authorityDecisionBodySha256='0' * 64), True)
        self.refused()

    def test_supervisor_records_supplied_run_binding(self):
        spec = importlib.util.spec_from_file_location('supervisor_v2', HERE / 'isolated_case.py')
        supervisor = importlib.util.module_from_spec(spec); spec.loader.exec_module(supervisor)
        binding = dict(GITHUB_RUN_ID='1', GITHUB_RUN_ATTEMPT='1', GITHUB_SHA='3' * 40,
                       GITHUB_REF='refs/heads/EXCLUDED_SYNTHETIC_ONLY',
                       PLAY_FIRST_ISOLATED_SOURCE_SHA='1' * 40,
                       PLAY_FIRST_ISOLATED_CASE_SHA256=self.pins['caseSha256'])
        with patch.dict(os.environ, binding):
            result = supervisor.supervise([sys.executable, '-S', '-c', 'pass'], self.root,
                                          self.root / 'supervised', 2, identity='EXCLUDED_NO_ENGINE')
        self.assertEqual(result['runBinding'], binding)
        self.assertEqual(result['processStatus'], 'EXITED_ZERO')
        self.assertIs(result['cleanupUncertain'], False)
        self.assertIs(result['residualGroupKilled'], False)
        intent = json.loads((self.root / 'supervised/process.jsonl').read_text().splitlines()[0])
        self.assertEqual(intent['runBinding'], binding)

    def test_missing_external_pins(self):
        del self.pins['caseSha256']; self.refused()

    def test_changed_case_bytes(self):
        self.raw['approved-case.json'] += b' '; self.refused()

    def test_changed_run_bytes(self):
        self.raw['run-source.json'] += b' '; self.refused()

    def test_live_run_pin_mismatch(self):
        self.pins['run']['runId'] = '2'; self.refused()

    def test_run_attempt_two(self):
        self.pins['run']['runAttempt'] = 2
        self.raw['run-source.json'] = encoded(self.pins['run'])
        self.pins['runSourceSha256'] = sha(self.raw['run-source.json']); self.refused()

    def test_process_nonzero_return_despite_status(self):
        self.change('case/process-result.json', lambda p: p.update(returnCode=1)); self.refused()

    def test_process_boolean_return_is_not_zero(self):
        self.change('case/process-result.json', lambda p: p.update(returnCode=False)); self.refused()

    def test_process_cleanup_uncertain(self):
        self.change('case/process-result.json', lambda p: p.update(cleanupUncertain=True)); self.refused()

    def test_process_residual_group(self):
        self.change('case/process-result.json', lambda p: p.update(residualGroupKilled=True)); self.refused()

    def test_process_cancelled(self):
        self.change('case/process-result.json', lambda p: p.update(cancellationSignal=15)); self.refused()

    def test_process_missing_cleanup_field(self):
        self.change('case/process-result.json', lambda p: p.pop('cleanupUncertain')); self.refused()

    def test_process_run_binding_mismatch(self):
        self.change('case/process-result.json', lambda p: p['runBinding'].update(GITHUB_RUN_ID='2')); self.refused()

    def test_process_finished_record_contradiction(self):
        self.change('case/process.jsonl', lambda p: p[-1].update(returnCode=1), True); self.refused()

    def test_process_command_substitution(self):
        self.change('case/process.jsonl', lambda p: p[0].update(command=['UNAPPROVED']), True); self.refused()

    def test_process_budget_substitution(self):
        self.change('case/process.jsonl', lambda p: p[0].update(budgetSeconds=100), True); self.refused()

    def test_process_timeout_even_with_terminal(self):
        self.change('case/process-result.json', lambda p: p.update(processStatus='TIMEOUT')); self.refused()

    def test_missing_all_timing(self):
        self.raw['case/timing.jsonl'] = b''; self.refused()

    def test_sparse_balanced_timing(self):
        self.change('case/timing.jsonl', lambda p: p.__delitem__(slice(2, None)), True); self.refused()

    def test_missing_initialization_timing(self):
        self.change('case/timing.jsonl', lambda p: p.__delitem__(slice(4, 6)), True); self.refused()

    def test_missing_selection_timing(self):
        self.change('case/timing.jsonl', lambda p: p.__delitem__(slice(24, 26)), True); self.refused()

    def test_missing_engine_submission_timing(self):
        self.change('case/timing.jsonl', lambda p: p.__delitem__(slice(30, 32)), True); self.refused()

    def test_wrong_timing_operation_with_same_count(self):
        self.change('case/timing.jsonl', lambda p: p[0].update(phase='OTHER'), True); self.refused()

    def test_wrong_timing_sequence_context(self):
        self.change('case/timing.jsonl', lambda p: p[6]['context'].update(nextSequence='2'), True); self.refused()

    def test_wrong_timing_case_digest(self):
        self.change('case/timing.jsonl', lambda p: p[0]['identity'].update(caseInputSha256='0' * 64), True); self.refused()

    def test_timing_thread_changes(self):
        self.change('case/timing.jsonl', lambda p: p[-1].update(threadId=2), True); self.refused()

    def test_timing_elapsed_contradiction(self):
        self.change('case/timing.jsonl', lambda p: p[-1].update(elapsedNanos=2), True); self.refused()

    def test_timing_call_error(self):
        self.change('case/timing.jsonl', lambda p: p[-1].update(recordType='CALL_ERROR'), True); self.refused()

    def test_timing_unfinished(self):
        self.change('case/timing.jsonl', lambda p: p.pop(), True); self.refused()

    def test_timing_duplicate_span(self):
        self.change('case/timing.jsonl', lambda p: p[2].update(span=1), True); self.refused()

    def test_missing_terminal(self):
        self.change('case/original.jsonl', lambda p: p.pop(), True); self.refused()

    def test_wrong_winner(self):
        self.change('case/original.jsonl', lambda p: p[-1].update(winnerId='fixture1'), True); self.refused()

    def test_rejected_submission(self):
        self.change('case/original.jsonl', lambda p: p[3].update(accepted=False), True); self.refused()

    def test_unknown_trace_record(self):
        self.change('case/original.jsonl', lambda p: p.insert(2, dict(recordType='EXHIBITION_FAILED')), True); self.refused()

    def test_action_sequence_gap(self):
        self.change('case/original.jsonl', lambda p: p[2].update(sequence=2), True); self.refused()

    def test_action_actor_mismatch(self):
        self.change('case/original.jsonl', lambda p: p[2]['selectedAction'].update(playerId='fixture1'), True); self.refused()

    def test_minimal_seed_serialization_is_rejected(self):
        self.change('case/original.jsonl', lambda p: p[0].update(fixtureSeedHex='0x1'), True); self.refused()

    def test_wrong_deck_pin(self):
        self.change('case/original.jsonl', lambda p: p[0].update(pestMainSha256='0' * 64), True); self.refused()

    def test_wrong_source_parent(self):
        self.change('case/original.jsonl', lambda p: p[0].update(sourceParent='0' * 40), True); self.refused()

    def test_wrong_terminal_turn(self):
        self.change('case/original.jsonl', lambda p: p[-1].update(turn=2), True); self.refused()

    def test_mulligan_completion_count_mismatch(self):
        self.change('case/original.jsonl', lambda p: p[10].update(actions=3), True); self.refused()

    def test_duplicate_json_key(self):
        self.raw['case/process-result.json'] = b'{"returnCode":0,"returnCode":1}\n'; self.refused()

    def test_nonfinite_json(self):
        self.raw['case/process-result.json'] = b'{"elapsedSeconds":NaN}\n'; self.refused()

    def test_partial_jsonl(self):
        self.raw['case/timing.jsonl'] = self.raw['case/timing.jsonl'][:-1]; self.refused()

    def test_payload_integrity_change(self):
        self.write(); self.raw['case/child.log'] += b'altered'; self.refused(False)

    def test_extra_unmanifested_file(self):
        self.write(); self.raw['case/extra'] = b'not listed'; self.refused(False)

    def test_manifest_pin_change(self):
        self.write(); self.raw['case/supervisor-manifest.json'] += b' '; self.refused(False)

    def test_manifest_duplicate_path(self):
        self.write()
        entries = json.loads(self.raw['case/supervisor-manifest.json']); entries.append(entries[0])
        self.raw['case/supervisor-manifest.json'] = encoded(entries)
        self.pins['manifestSha256'] = sha(encoded(entries)); self.refused(False)

    def test_manifest_parent_escape(self):
        self.write()
        entries = json.loads(self.raw['case/supervisor-manifest.json']); entries[0]['path'] = '../escape'
        self.raw['case/supervisor-manifest.json'] = encoded(entries)
        self.pins['manifestSha256'] = sha(encoded(entries)); self.refused(False)

    def test_symlink_refused(self):
        self.write(); (self.root / 'case/link').symlink_to(self.root / 'approved-case.json')
        self.assertEqual(MOD.inspect_evidence(self.root, self.pins, _authority_client=self.authority_client, _authority_now=self.authority_now)['status'], 'INCOMPLETE')

    def test_change_during_inspection(self):
        self.write(); actual = MOD.snapshot(self.root); changed = dict(actual); changed['case/child.log'] += b'changed'
        with patch.object(MOD, 'snapshot', side_effect=[actual, changed]):
            self.assertEqual(MOD.inspect_evidence(self.root, self.pins, _authority_client=self.authority_client, _authority_now=self.authority_now)['status'], 'INCOMPLETE')

    def test_optimized_python_keeps_rejection(self):
        self.change('case/process-result.json', lambda p: p.update(returnCode=2)); self.write()
        path = self.root / 'external-pins.json'; path.write_bytes(encoded(self.pins))
        for flag in ('-O', '-OO'):
            run = subprocess.run([sys.executable, flag, str(HERE / 'strict_receiving.py'), 'inspect',
                                  '--root', str(self.root), '--trusted-pins', str(path)], capture_output=True, text=True)
            self.assertEqual(run.returncode, 1, run.stderr)
            self.assertEqual(json.loads(run.stdout)['status'], 'INCOMPLETE')
            (self.root / ('optimized' + flag + '.log')).write_text(run.stdout + run.stderr)

    def test_required_child_output_file_cannot_be_silently_omitted(self):
        # The supervisor always creates child.log, even when the child prints nothing.
        # Re-sealing the supervisor manifest must not conceal its complete absence.
        del self.raw['case/child.log']
        self.refused()

    def test_engine_result_cannot_reverse_recorded_turn(self):
        # Deliberately fabricated negative fixture: the selected action is
        # recorded on turn 42, yet the returned engine state claims turn 1.
        # Matching the separate timing context must not make it acceptable.
        def modify(items):
            for record in items:
                if record.get('recordType') == 'ACTION_INTENT' and record['sequence'] == 1:
                    record['turn'] = 42
                    break
        self.change('case/original.jsonl', modify, True)
        timing = [json.loads(x) for x in self.raw['case/timing.jsonl'].splitlines()]
        for record in timing:
            if record['phase'] == 'ENGINE_SUBMIT' and record['context'].get('sequence') == '1':
                record['context']['turn'] = '42'
        self.raw['case/timing.jsonl'] = jsonl(timing)
        self.refused()


class ActivationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        self.git('init', '-q'); self.git('config', 'user.name', 'Excluded Fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'commit.gpgsign', 'false')
        (self.repo / 'README').write_text('EXCLUDED UNIT GIT OBJECTS ONLY\n')
        self.git('add', '.'); self.git('commit', '-qm', 'excluded source')
        self.approved = dict(sourceCommit=self.git('rev-parse', 'HEAD'), sourceTree=self.git('rev-parse', 'HEAD^{tree}'))
        self.workflow = '.github/workflows/EXCLUDED.txt'
        p = self.repo / self.workflow; p.parent.mkdir(parents=True); p.write_text('INACTIVE EXCLUDED BYTES\n')
        self.git('add', '.'); self.git('commit', '-qm', 'excluded validation')
        self.approved.update(validationCommit=self.git('rev-parse', 'HEAD'), validationTree=self.git('rev-parse', 'HEAD^{tree}'),
                             workflowPath=self.workflow, addedFiles={self.workflow: self.git('rev-parse', 'HEAD:' + self.workflow)})

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.repo), *args], text=True, stderr=subprocess.DEVNULL).strip()

    def test_exact_head_tree_delta(self):
        MOD.verify_checkout(self.repo, self.approved)

    def test_wrong_head(self):
        self.approved['validationCommit'] = self.approved['sourceCommit']
        with self.assertRaises(ValueError): MOD.verify_checkout(self.repo, self.approved)

    def test_wrong_tree(self):
        self.approved['validationTree'] = self.approved['sourceTree']
        with self.assertRaises(ValueError): MOD.verify_checkout(self.repo, self.approved)

    def test_wrong_workflow_blob(self):
        self.approved['addedFiles'][self.workflow] = '0' * 40
        with self.assertRaises(ValueError): MOD.verify_checkout(self.repo, self.approved)

    def test_extra_unreviewed_delta(self):
        (self.repo / 'EXTRA').write_text('unapproved'); self.git('add', '.'); self.git('commit', '--amend', '--no-edit', '-q')
        self.approved.update(validationCommit=self.git('rev-parse', 'HEAD'), validationTree=self.git('rev-parse', 'HEAD^{tree}'))
        with self.assertRaises(ValueError): MOD.verify_checkout(self.repo, self.approved)

    def test_modified_predecessor(self):
        (self.repo / 'README').write_text('modified'); self.git('add', '.'); self.git('commit', '--amend', '--no-edit', '-q')
        self.approved.update(validationCommit=self.git('rev-parse', 'HEAD'), validationTree=self.git('rev-parse', 'HEAD^{tree}'))
        with self.assertRaises(ValueError): MOD.verify_checkout(self.repo, self.approved)

    def test_dirty_checkout(self):
        (self.repo / 'UNTRACKED').write_text('dirty')
        with self.assertRaises(ValueError): MOD.verify_checkout(self.repo, self.approved)


if __name__ == '__main__':
    unittest.main(verbosity=2)
