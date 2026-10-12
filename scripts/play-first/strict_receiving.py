#!/usr/bin/env python3
"""V2 read-only correspondence checks. Trusted pins MUST come from separate review.

No engine imports, replay, run dispatch, engine-origin proof, or global uniqueness.
A positive synthetic packet is still synthetic. Never use the legacy v1 inspector
as a substitute. All semantic checks below survive Python -O and -OO.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
from typing import Any
from gameplay_authority import CONTRACT as AUTHORITY_CONTRACT, freeze_intent, verify_historical

SCOPE = 'NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY'
HEX40 = re.compile(r'[0-9a-f]{40}')
HEX64 = re.compile(r'[0-9a-f]{64}')
MAX_BYTES = 256 * 1024 * 1024
MAX_FILES = 4096


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def integer(value: Any, low: int = 0) -> bool:
    return type(value) is int and value >= low


def finite(value: Any) -> bool:
    return type(value) in (int, float) and math.isfinite(value) and value >= 0


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in result, 'duplicate JSON key: ' + key)
        result[key] = value
    return result


def decode(data: bytes) -> Any:
    def invalid(value: str) -> None:
        raise ValueError('nonfinite JSON: ' + value)
    return json.loads(data.decode('utf-8'), object_pairs_hook=object_pairs,
                      parse_constant=invalid)


def rows(data: bytes) -> list[dict[str, Any]]:
    require(data.endswith(b'\n'), 'partial JSONL / missing final LF')
    result = [decode(line) for line in data.splitlines()]
    require(result and all(type(r) is dict for r in result), 'JSONL object records required')
    return result


def relative(path: Any) -> str:
    require(type(path) is str and bool(path), 'empty/non-string member path')
    p = PurePosixPath(path)
    require(not p.is_absolute() and '\\' not in path and '\0' not in path and
            p.as_posix() == path and all(x not in ('', '.', '..') for x in p.parts),
            'noncanonical member path: ' + path)
    return path


def regular_bytes(path: Path) -> bytes:
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode), 'not a regular file: ' + str(path))
    require(info.st_size <= MAX_BYTES, 'oversize input')
    return path.read_bytes()


def snapshot(evidence: Path) -> dict[str, bytes]:
    root = evidence.absolute()
    require(root.resolve(strict=True) == root, 'symlink/alias in evidence root')
    require(root.is_dir(), 'evidence root is not a directory')
    case = root / 'case'
    require(stat.S_ISDIR(case.lstat().st_mode), 'case must be a real directory')
    result = {name: regular_bytes(root / name) for name in ('approved-case.json', 'run-source.json')}
    total = sum(map(len, result.values()))
    for path in sorted(case.rglob('*')):
        mode = path.lstat().st_mode
        require(not stat.S_ISLNK(mode), 'symlink in case')
        if stat.S_ISDIR(mode):
            continue
        key = relative(path.relative_to(root).as_posix())
        data = regular_bytes(path)
        total += len(data)
        require(total <= MAX_BYTES and len(result) < MAX_FILES, 'evidence resource limit')
        result[key] = data
    return result


def check_manifest(raw: dict[str, bytes], pin: str) -> None:
    name = 'case/supervisor-manifest.json'
    require(digest(raw[name]) == pin, 'supervisor manifest external pin mismatch')
    manifest = decode(raw[name])
    require(type(manifest) is list, 'manifest list required')
    inventory = {}
    for entry in manifest:
        require(type(entry) is dict and set(entry) == {'path', 'bytes', 'sha256'}, 'manifest entry shape')
        path = 'case/' + relative(entry['path'])
        require(path != name and path not in inventory, 'duplicate/self manifest entry')
        require(integer(entry['bytes']) and type(entry['sha256']) is str and
                bool(HEX64.fullmatch(entry['sha256'])), 'manifest size/digest type')
        inventory[path] = entry
    payloads = {k for k in raw if k.startswith('case/') and k != name}
    require(set(inventory) == payloads, 'manifest missing/extra payload')
    for path, entry in inventory.items():
        require(len(raw[path]) == entry['bytes'] and digest(raw[path]) == entry['sha256'],
                'payload integrity mismatch: ' + path)


def check_case(case: dict[str, Any], pins: dict[str, Any]) -> None:
    keys = {'blockId', 'gameId', 'pair', 'pestSeat', 'fixtureSeedHex',
            'sourceCommit', 'scope', 'intendedBlockGames'}
    require(type(case) is dict and set(case) == keys, 'case schema')
    require(type(case['blockId']) is str and bool(re.fullmatch(r'PLAY_FIRST_[A-Z0-9_]{1,80}', case['blockId']))
            and 'B01' not in case['blockId'], 'block identity')
    require(type(case['gameId']) is str and bool(re.fullmatch(r'G[0-9]{2,4}', case['gameId']))
            and case['gameId'] != 'G08', 'game identity / no G08 continuation')
    require(integer(case['pair'], 1) and type(case['pestSeat']) is int and case['pestSeat'] in (0, 1), 'pair/seat')
    require(integer(case['intendedBlockGames'], 1) and case['intendedBlockGames'] <= 100, 'block size')
    # Immutable intent deliberately excludes future decision/run IDs. Historical
    # authority is bound separately by an authenticated execution-time anchor.
    seed = case['fixtureSeedHex']
    require(type(seed) is str and bool(re.fullmatch(r'0x[0-7][0-9a-f]{15}', seed)) and int(seed, 16) > 0,
            'fixed-width lowercase positive signed-int64 seed label required')
    require(seed not in ('0x504c415946495253', '0x504c4159424c3031', '0x504c4159424c3032',
                         '0x504c4159424c3033', '0x504c4159424c3034'), 'historical original fixture')
    require(case['scope'] == SCOPE, 'nonofficial case declaration')
    require(case['sourceCommit'] == pins['run']['sourceCommit'], 'case/source correspondence')


def check_run(run: Any, pins: dict[str, Any]) -> None:
    require(type(run) is dict and run == pins['run'], 'run/source external pins mismatch')
    for key in ('sourceCommit', 'sourceParent', 'sourceTree', 'validationCommit', 'validationTree', 'workflowBlob'):
        require(type(run[key]) is str and bool(HEX40.fullmatch(run[key])), 'invalid Git identity: ' + key)
    require(type(run['runId']) is str and bool(re.fullmatch(r'[1-9][0-9]*', run['runId'])), 'run ID')
    require(type(run['runAttempt']) is int and run['runAttempt'] == 1, 'not original attempt')
    require(integer(run['runNumber'], 1) and run['event'] == 'push', 'run counter/event')
    require(type(run['ref']) is str and run['ref'].startswith('refs/heads/'), 'branch ref')
    require(relative(run['workflowPath']).startswith('.github/workflows/'), 'workflow path')


def check_process(raw: dict[str, bytes], case: dict[str, Any], pins: dict[str, Any]) -> None:
    # A launched supervisor always creates child.log, even if it is empty.
    # An otherwise resealed manifest must not erase that required output.
    require('case/child.log' in raw, 'missing captured child output')
    process = decode(raw['case/process-result.json'])
    log = rows(raw['case/process.jsonl'])
    run = pins['run']
    binding = {'GITHUB_RUN_ID': run['runId'], 'GITHUB_RUN_ATTEMPT': '1',
               'GITHUB_SHA': run['validationCommit'], 'GITHUB_REF': run['ref'],
               'PLAY_FIRST_ISOLATED_SOURCE_SHA': run['sourceCommit'],
               'PLAY_FIRST_ISOLATED_CASE_SHA256': pins['caseSha256']}
    require(type(process) is dict and process.get('identity') == case['gameId'], 'process identity')
    require(process.get('runBinding') == binding, 'process run/source/case binding')
    require(process.get('processStatus') == 'EXITED_ZERO' and type(process.get('returnCode')) is int
            and process['returnCode'] == 0, 'process status/return code')
    require(process.get('cleanupUncertain') is False and process.get('residualGroupKilled') is False
            and process.get('cancellationSignal') is None and 'error' not in process, 'process cleanup/cancellation')
    require(finite(process.get('elapsedSeconds')), 'elapsed process time')
    require(len(log) >= 3 and log[0]['recordType'] == 'PROCESS_INTENT' and
            log[1]['recordType'] == 'PROCESS_STARTED', 'process start ordering')
    intent = log[0]
    require(intent.get('identity') == case['gameId'] and intent.get('runBinding') == binding, 'process intent binding')
    require(intent.get('command') == pins['command'] and type(pins['command']) is list and
            bool(pins['command']) and all(type(x) is str and '\0' not in x for x in pins['command']), 'command pin')
    for key in ('budgetSeconds', 'graceSeconds'):
        require(finite(intent.get(key)) and 0 < intent[key] == pins[key], 'budget pin: ' + key)
    require(intent['budgetSeconds'] <= 3600 and intent['graceSeconds'] <= 10, 'supported budgets')
    require(integer(intent.get('pid'), 1) and integer(log[1].get('childPid'), 1), 'process PID fields')
    require(type(intent.get('monotonicNanos')) is int and type(log[1].get('monotonicNanos')) is int
            and log[1]['monotonicNanos'] >= intent['monotonicNanos'], 'process clock order')
    require(log[-1] == dict(recordType='PROCESS_FINISHED', **process), 'process result/log contradiction')
    last = -1.0
    for beat in log[2:-1]:
        require(beat['recordType'] == 'PROCESS_HEARTBEAT' and finite(beat.get('elapsedSeconds')),
                'unexpected process event / cleanup record')
        require(last <= beat['elapsedSeconds'] <= process['elapsedSeconds'], 'heartbeat order')
        last = beat['elapsedSeconds']


def check_trace(trace: list[dict[str, Any]], case: dict[str, Any], pins: dict[str, Any], authority: dict[str, Any]) -> tuple[int, list[tuple[str, dict[str, str]]]]:
    require(len(trace) >= 6 and trace[0]['recordType'] == 'EXHIBITION_INTENT' and
            trace[1]['recordType'] == 'INITIALIZED' and trace[-1]['recordType'] == 'EXHIBITION_TERMINAL',
            'trace boundary ordering')
    start, init, end = trace[0], trace[1], trace[-1]
    require(start.get('authorityContract') == AUTHORITY_CONTRACT and
            start.get('authorityDecisionBodySha256') == authority['decisionBodySha256'] and
            start.get('authorityReleaseBodySha256') == authority['releaseBodySha256'],
            'adapter/receiver positive authority contract disagreement')
    for key in ('blockId', 'gameId', 'pair', 'pestSeat', 'fixtureSeedHex', 'sourceCommit',
                'authorizationComment', 'intendedBlockGames'):
        require(type(start.get(key)) is type(case[key]) and start[key] == case[key], 'trace case: ' + key)
    require(start.get('sourceParent') == pins['run']['sourceParent'], 'trace source parent')
    require(start.get('caseInputSha256') == pins['caseSha256'], 'trace case-byte pin')
    require(start.get('scope') == SCOPE and start.get('formalSeedAllocation') is False and
            start.get('officialExecutionAuthorized') is False and start.get('experimentalResult') is False,
            'trace scope flags')
    require(start.get('intendedGamesThisCase') == 1 and start.get('startingSeat') == 0 and
            (start.get('maxActions'), start.get('maxTurns'), start.get('maxActionsPerTurn')) == (12000, 60, 500),
            'trace game configuration')
    for key in ('pestMainSha256', 'monsterTronMainSha256'):
        require(type(pins[key]) is str and bool(HEX64.fullmatch(pins[key])) and start.get(key) == pins[key], 'deck pin')
    names = ['Pest Control v1.0', 'mehanske Monster Tron']
    if case['pestSeat'] == 1:
        names.reverse()
    require([start.get('seat0Deck'), start.get('seat1Deck')] == names, 'deck/seat mapping')
    require(init.get('gameId') == case['gameId'] and init.get('gameOver') is False, 'initialization')
    players = [init['player0'], init['player1']]
    require(all(type(p) is str and p for p in players) and len(set(players)) == 2, 'player identities')
    spans: list[tuple[str, dict[str, str]]] = [(p, {}) for p in ('REGISTRY_CREATE', 'ENVIRONMENT_CREATE', 'INITIALIZE')]
    i, count, mulligans_done, last_result = 2, 0, False, None
    last_turn_after = 0
    while i < len(trace) - 1:
        item = trace[i]
        if item['recordType'] == 'MULLIGAN_COMPLETE':
            require(not mulligans_done and item.get('gameId') == case['gameId'] and
                    type(item.get('actions')) is int and item['actions'] == count, 'mulligan boundary')
            mulligans_done = True
            spans.append(('AGENTS_CREATE', {}))
            i += 1
            continue
        require(i + 1 < len(trace) - 1 and item['recordType'] == 'ACTION_INTENT' and
                trace[i + 1]['recordType'] == 'ACTION_RESULT', 'action alternation / unknown trace record')
        result = trace[i + 1]
        count += 1
        require(count <= 12000 and type(item.get('sequence')) is int and type(result.get('sequence')) is int
                and item['sequence'] == result['sequence'] == count, 'action sequence')
        require(result.get('accepted') is True and result.get('rejectionReason', '') == '', 'rejected action')
        actor = item['actor']; require(actor in players and integer(item.get('turn'), 1) and item['turn'] <= 60, 'action actor/turn')
        require(integer(result.get('turnAfter'), 1) and result['turnAfter'] >= item['turn'] >= last_turn_after,
                'turn moved backward across recorded action/engine result')
        last_turn_after = result['turnAfter']
        require(type(item.get('step')) is str and type(item.get('pendingDecision')) is str, 'action context')
        action = item['selectedAction']; require(type(action) is dict and action.get('playerId') == actor
                                               and type(action.get('type')) is str and bool(action['type']), 'selected action actor/type')
        context = {'actor': actor, 'nextSequence': str(count)}
        if not mulligans_done:
            phase = {'KeepHand': 'MULLIGAN_SELECT', 'TakeMulligan': 'MULLIGAN_SELECT', 'BottomCards': 'BOTTOM_SELECT'}.get(action.get('type'))
            require(phase is not None, 'non-mulligan action before completion')
        else:
            phase = 'RESPOND_DECISION' if item['pendingDecision'] else 'CHOOSE_ACTION'
            require((action.get('type') == 'SubmitDecision') == bool(item['pendingDecision']), 'decision/action kind')
            context.update(seat=str(players.index(actor)), turn=str(item['turn']), step=item['step'], pendingDecision=item['pendingDecision'])
        spans.append((phase, context))
        spans.append(('ENGINE_SUBMIT', {'sequence': str(count), 'actor': actor, 'turn': str(item['turn']), 'step': item['step']}))
        require(type(result.get('gameOverAfter')) is bool and type(result.get('emittedEvents')) is list, 'response fields')
        if i + 2 < len(trace) - 1:
            require(result['gameOverAfter'] is False and not any(e.get('type') == 'GameEndedEvent' for e in result['emittedEvents']),
                    'premature engine terminal')
        last_result = result
        i += 2
    require(mulligans_done and last_result is not None, 'missing completion/actions')
    require(end.get('gameId') == case['gameId'] and type(end.get('pestSeat')) is int and
            end['pestSeat'] == case['pestSeat'] and end.get('pair') == case['pair'], 'terminal identity')
    require(end.get('gameOver') is True and last_result['gameOverAfter'] is True and
            type(end.get('actions')) is int and end['actions'] == count and
            type(end.get('formalExperimentCount')) is int and end['formalExperimentCount'] == 0 and
            end.get('scope') == SCOPE, 'terminal flags/count')
    require(integer(end.get('turn'), 1) and type(last_result.get('turnAfter')) is int
            and end['turn'] == last_result['turnAfter'] and end['turn'] <= 60, 'terminal turn correspondence')
    require(end.get('lifeBySeat') == last_result.get('lifeAfter') and type(end.get('lifeBySeat')) is list
            and len(end['lifeBySeat']) == 2 and all(type(n) is int for n in end['lifeBySeat']), 'terminal life')
    events = [e for e in last_result['emittedEvents'] if e.get('type') == 'GameEndedEvent']
    require(len(events) == 1 and (events[0].get('winnerId') or '') == end.get('winnerId'), 'engine terminal event')
    seat = end.get('winnerSeat')
    require(type(seat) is int and seat in (-1, 0, 1), 'winner seat')
    require(end.get('winnerId') == (players[seat] if seat >= 0 else ''), 'winner identity')
    return seat, spans


def check_timing(timing: list[dict[str, Any]], spans: list[tuple[str, dict[str, str]]], case: dict[str, Any], pins: dict[str, Any]) -> None:
    require(len(timing) == 2 * len(spans), 'missing/extra timing operation coverage')
    identity = {k: str(case[k]) for k in ('blockId', 'gameId', 'sourceCommit', 'authorizationComment')}
    identity['caseInputSha256'] = pins['caseSha256']
    thread, previous = None, None
    for number, (phase, context) in enumerate(spans, 1):
        begin, end = timing[2 * number - 2:2 * number]
        for record, kind in ((begin, 'CALL_START'), (end, 'CALL_END')):
            require(record.get('recordType') == kind and type(record.get('span')) is int and record['span'] == number,
                    'timing order/span/error')
            require(record.get('identity') == identity and record.get('phase') == phase and
                    record.get('context') == context, 'timing identity/operation context')
            require(integer(record.get('threadId'), 1) and type(record.get('monotonicNanos')) is int, 'timing clock/thread')
            thread = record['threadId'] if thread is None else thread
            require(record['threadId'] == thread, 'timing thread changed')
            require(previous is None or record['monotonicNanos'] >= previous, 'timing clock order')
            previous = record['monotonicNanos']
        require(integer(end.get('elapsedNanos')) and end['elapsedNanos'] == end['monotonicNanos'] - begin['monotonicNanos'],
                'timing elapsed contradiction')


def inspect_evidence(evidence: Path, pins: dict[str, Any], *, _historical_anchor=None) -> dict[str, Any]:
    report: dict[str, Any] = {'status': 'INCOMPLETE', 'errors': [], 'gameplayAuthorized': False,
                               'authenticatedOrigin': False, 'engineReplayed': False}
    try:
        for key in ('caseSha256', 'manifestSha256', 'runSourceSha256'):
            require(type(pins[key]) is str and bool(HEX64.fullmatch(pins[key])), 'missing/invalid external pin: ' + key)
        raw = snapshot(evidence)
        require(digest(raw['approved-case.json']) == pins['caseSha256'], 'case external pin mismatch')
        require(digest(raw['run-source.json']) == pins['runSourceSha256'], 'run-source byte pin mismatch')
        check_manifest(raw, pins['manifestSha256'])
        # Filename retained for evidence tooling; its V2 bytes are immutable
        # case-intent, not an executable approval or a provider-ID-bearing case.
        intent = freeze_intent(raw['approved-case.json'])['intent']
        case, run = dict(intent['case']), decode(raw['run-source.json'])
        check_run(run, pins)
        check_case(case, pins)
        check_process(raw, case, pins)
        authority = verify_historical(raw['approved-case.json'], run, pins.get('gameplayApproval'),
                                      pins['manifestSha256'], _anchor=_historical_anchor)
        case['authorizationComment'] = authority['claims']['decisionId']
        trace = rows(raw['case/original.jsonl'])
        seat, spans = check_trace(trace, case, pins, authority)
        check_timing(rows(raw['case/timing.jsonl']), spans, case, pins)
        require(raw == snapshot(evidence), 'evidence changed during read-only inspection')
        report.update(status='PINNED_RECORD_CORRESPONDENCE_ONLY', winnerSeat=seat,
                      actions=(len(spans) - 4) // 2, timingCalls=len(spans),
                      caseSha256=pins['caseSha256'], manifestSha256=pins['manifestSha256'],
                      authorityDecisionCorrespondence=authority)
    except Exception as error:
        report['errors'].append(type(error).__name__ + ': ' + str(error))
    return report


def verify_checkout(repo: Path, approved: dict[str, Any]) -> None:
    """Read Git objects only. Approval comes from OUTSIDE the candidate workflow."""
    def git(*args: str) -> str:
        return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()
    for key in ('sourceCommit', 'sourceTree', 'validationCommit', 'validationTree'):
        require(type(approved[key]) is str and bool(HEX40.fullmatch(approved[key])), 'activation Git pin: ' + key)
    require(git('rev-parse', 'HEAD') == approved['validationCommit'], 'exact validation HEAD mismatch')
    require(git('rev-parse', 'HEAD^{tree}') == approved['validationTree'], 'exact validation tree mismatch')
    require(git('rev-list', '--parents', '-n', '1', 'HEAD').split() ==
            [approved['validationCommit'], approved['sourceCommit']], 'sole source parent mismatch')
    require(git('rev-parse', approved['sourceCommit'] + '^{tree}') == approved['sourceTree'], 'source tree mismatch')
    additions = approved['addedFiles']
    require(type(additions) is dict and bool(additions), 'explicit additive delta required')
    require(approved['workflowPath'] in additions and approved['workflowPath'].startswith('.github/workflows/'), 'approved workflow addition')
    expected = []
    for path, blob in additions.items():
        relative(path)
        require(type(blob) is str and bool(HEX40.fullmatch(blob)), 'added blob pin')
        expected.append('A\t' + path)
        require(git('ls-tree', 'HEAD', '--', path) == '100644 blob ' + blob + '\t' + path, 'added file mode/blob mismatch')
    require(git('diff', '--name-status', '--no-renames', approved['sourceCommit'], 'HEAD').splitlines() == sorted(expected),
            'unreviewed source delta')
    require(git('status', '--porcelain=v1', '--untracked-files=all') == '', 'checkout is not clean')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('inspect', 'activation'))
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--trusted-pins', type=Path, required=True)
    args = parser.parse_args()
    try:
        pins = decode(args.trusted_pins.read_bytes())
        if args.operation == 'activation':
            verify_checkout(args.root, pins)
            report = {'status': 'EXACT_CHECKOUT_CORRESPONDENCE_ONLY', 'gameplayAuthorized': False}
        else:
            report = inspect_evidence(args.root, pins)
    except Exception as error:
        report = {'status': 'INCOMPLETE', 'errors': [type(error).__name__ + ': ' + str(error)]}
    print(json.dumps(report, indent=2, sort_keys=True))
    return 1 if report['status'] == 'INCOMPLETE' else 0


if __name__ == '__main__':
    raise SystemExit(main())
