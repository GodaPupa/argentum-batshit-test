#!/usr/bin/env python3
"""POSIX child-process isolation; receiving checks are not execution authorization.

One fresh directory and process group per original. Never retries, resumes, or
assigns a winner from a timeout/exit status. Child code must not escape its process
group; this is not a hostile-process/container security boundary.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import signal
import subprocess
import sys
import time
from typing import Any


def append(path: Path, record: dict[str, Any]) -> None:
    with path.open('a', encoding='utf-8') as stream:
        stream.write(json.dumps(record, sort_keys=True) + '\n')
        stream.flush()
        os.fsync(stream.fileno())


def manifest(root: Path) -> None:
    entries = []
    for path in sorted(root.rglob('*')):
        if path.is_symlink():
            raise ValueError('Cannot freeze a symlink: ' + str(path))
        if path.is_file() and path.name != 'supervisor-manifest.json':
            data = path.read_bytes()
            entries.append(dict(path=path.relative_to(root).as_posix(), bytes=len(data),
                                sha256=hashlib.sha256(data).hexdigest()))
    with (root / 'supervisor-manifest.json').open('x', encoding='utf-8') as stream:
        json.dump(entries, stream, indent=2, sort_keys=True)
        stream.write('\n')


def supervise(command: list[str], cwd: Path, root: Path, seconds: float,
              grace: float = 2.0, identity: str = 'EXCLUDED_FIXTURE') -> dict[str, Any]:
    """Process lifetime is separate from engine outcome; output slot is create-once."""
    if os.name != 'posix':
        raise ValueError('POSIX process-group supervision required')
    if not command or any(not isinstance(x, str) or '\0' in x for x in command):
        raise ValueError('Explicit argv required; shell commands are not interpreted')
    if not math.isfinite(seconds) or not 0 < seconds <= 3600:
        raise ValueError('Invalid finite per-case budget')
    if not math.isfinite(grace) or not 0 < grace <= 10:
        raise ValueError('Invalid cleanup grace')
    root = Path(root).absolute()
    if root.parent.resolve() != root.parent or not root.parent.is_dir():
        raise ValueError('Parent must be an existing nonsymlink absolute directory')
    root.mkdir()  # refusal on an existing output, even if empty
    log = root / 'process.jsonl'
    append(log, dict(recordType='PROCESS_INTENT', identity=identity, command=command,
                     budgetSeconds=seconds, graceSeconds=grace, pid=os.getpid(),
                     monotonicNanos=time.monotonic_ns(), scope='NONOFFICIAL_OR_EXCLUDED_ONLY'))
    cancellation = {'signal': None}
    previous = {}
    child = None
    result: dict[str, Any] = dict(identity=identity, processStatus='INCOMPLETE', returnCode=None)
    def on_signal(number: int, _frame: Any) -> None:
        cancellation['signal'] = number
    def kill_group(number: int) -> bool:
        if child is None:
            return False
        try:
            os.killpg(child.pid, number)
            return True
        except ProcessLookupError:
            return False
    try:
        for number in (signal.SIGTERM, signal.SIGINT):
            previous[number] = signal.signal(number, on_signal)
        env = os.environ.copy()
        for key in list(env):
            if not (key.startswith('PLAY_FIRST') and key.endswith('ACK') and key != 'PLAY_FIRST_ISOLATED_ACK'):
                continue
            env.pop(key, None)
        env['PLAY_FIRST_ISOLATED_EVIDENCE_ROOT'] = str(root)
        with (root / 'child.log').open('xb') as output:
            started = time.monotonic()
            child = subprocess.Popen(command, cwd=cwd, env=env, stdout=output,
                                     stderr=subprocess.STDOUT, start_new_session=True)
            append(log, dict(recordType='PROCESS_STARTED', childPid=child.pid,
                             monotonicNanos=time.monotonic_ns()))
            last_beat = -1.0
            while child.poll() is None:
                elapsed = time.monotonic() - started
                if cancellation['signal'] is not None:
                    result['processStatus'] = 'CANCELLED'
                    break
                if elapsed >= seconds:
                    result['processStatus'] = 'TIMEOUT'
                    break
                if elapsed - last_beat >= 1.0:
                    append(log, dict(recordType='PROCESS_HEARTBEAT', elapsedSeconds=elapsed))
                    last_beat = elapsed
                time.sleep(min(0.05, seconds - elapsed))
            if result['processStatus'] in ('TIMEOUT', 'CANCELLED'):
                append(log, dict(recordType='TERMINATION_REQUESTED', status=result['processStatus'],
                                 receivedSignal=cancellation['signal']))
                kill_group(signal.SIGTERM)
                # Preserve a bounded grace for children, even if their parent exits first.
                time.sleep(grace)
                kill_group(signal.SIGKILL)
            code = child.wait(timeout=10)
            if result['processStatus'] == 'INCOMPLETE':
                result['processStatus'] = 'EXITED_ZERO' if code == 0 else 'EXITED_NONZERO'
            if kill_group(signal.SIGKILL):
                append(log, dict(recordType='RESIDUAL_GROUP_KILLED'))
                if result['processStatus'] == 'EXITED_ZERO':
                    result['processStatus'] = 'EXITED_WITH_RESIDUAL_GROUP'
            result.update(returnCode=code, elapsedSeconds=time.monotonic() - started,
                          cancellationSignal=cancellation['signal'])
    except BaseException as failure:
        result.update(processStatus='SUPERVISOR_ERROR', error=type(failure).__name__ + ': ' + str(failure))
        kill_group(signal.SIGKILL)
        if child is not None:
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                result['cleanupUncertain'] = True
    finally:
        for number, handler in previous.items():
            signal.signal(number, handler)
        append(log, dict(recordType='PROCESS_FINISHED', **result))
        with (root / 'process-result.json').open('x', encoding='utf-8') as stream:
            json.dump(result, stream, sort_keys=True, indent=2)
            stream.write('\n')
        manifest(root)
    return result


def inspect_case(root: Path, expected: dict[str, Any]) -> dict[str, Any]:
    """Read-only original receiving; no repository imports or re-execution."""
    summary: dict[str, Any] = dict(status='INCOMPLETE', engineTerminalObserved=False, errors=[])
    try:
        process = json.loads((root / 'process-result.json').read_text())
        rows = [json.loads(line) for line in (root / 'original.jsonl').read_text().splitlines()]
        pick = lambda kind: [r for r in rows if r.get('recordType') == kind]
        starts, init, intents, results, ends = [pick(k) for k in (
            'EXHIBITION_INTENT', 'INITIALIZED', 'ACTION_INTENT', 'ACTION_RESULT', 'EXHIBITION_TERMINAL')]
        assert len(starts) == len(init) == 1 and not init[0]['gameOver'], 'one initialization/intent'
        start = starts[0]
        for key in ('blockId', 'gameId', 'pair', 'pestSeat', 'fixtureSeedHex', 'sourceCommit', 'authorizationComment'):
            assert start[key] == expected[key], 'identity mismatch: ' + key
        assert start['formalSeedAllocation'] is False and start['officialExecutionAuthorized'] is False
        assert len(pick('MULLIGAN_COMPLETE')) == 1, 'mulligan completion'
        assert [r['sequence'] for r in intents] == [r['sequence'] for r in results] == list(range(1, len(intents) + 1))
        assert [r['recordType'] for r in rows if r['recordType'] in ('ACTION_INTENT', 'ACTION_RESULT')] == ['ACTION_INTENT', 'ACTION_RESULT'] * len(intents)
        assert results and all(r['accepted'] is True for r in results), 'recorded submission rejection'
        summary.update(actions=len(intents), rejected=0)
        assert len(ends) == 1 and rows[-1] == ends[0], 'no unique final terminal'
        end = ends[0]
        assert end['gameId'] == expected['gameId'] and end['pestSeat'] == expected['pestSeat']
        assert end['gameOver'] is True and end['actions'] == len(intents) and end['formalExperimentCount'] == 0
        assert results[-1]['gameOverAfter'] is True and end['lifeBySeat'] == results[-1]['lifeAfter']
        event = [e for e in results[-1]['emittedEvents'] if e.get('type') == 'GameEndedEvent']
        assert len(event) == 1 and (event[0].get('winnerId') or '') == end['winnerId']
        seat = end['winnerSeat']; assert seat in (-1, 0, 1)
        if seat >= 0:
            assert end['winnerId'] == init[0]['player' + str(seat)]
        else:
            assert end['winnerId'] == '', 'draw with nonempty winner'
        summary['engineTerminalObserved'] = True
        assert not pick('EXHIBITION_FAILED'), 'recorded case failure'
        timing = [json.loads(line) for line in (root / 'timing.jsonl').read_text().splitlines()]
        opened = {}; seen = set()
        for record in timing:
            span = record['span']; kind = record['recordType']
            assert record['identity']['gameId'] == expected['gameId']
            assert record['identity']['sourceCommit'] == expected['sourceCommit']
            if kind == 'CALL_START':
                assert span not in seen and span == len(seen) + 1
                seen.add(span); opened[span] = record
            else:
                prior = opened.pop(span)
                assert record['phase'] == prior['phase'] and record['context'] == prior['context']
                assert kind == 'CALL_END' and record['elapsedNanos'] >= 0
        assert timing and not opened, 'missing or unfinished timing boundary'
        assert process['processStatus'] == 'EXITED_ZERO', 'process did not exit cleanly'
        summary.update(status='RECORDED_ENGINE_TERMINAL', winnerSeat=seat)
    except Exception as failure:
        summary['errors'].append(type(failure).__name__ + ': ' + str(failure))
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='operation', required=True)
    run = sub.add_parser('run'); run.add_argument('--root', type=Path, required=True)
    run.add_argument('--seconds', type=float, required=True); run.add_argument('--identity', required=True)
    run.add_argument('command', nargs=argparse.REMAINDER)
    inspect = sub.add_parser('inspect'); inspect.add_argument('--root', type=Path, required=True)
    inspect.add_argument('--case', type=Path, required=True)
    args = parser.parse_args()
    if args.operation == 'inspect':
        report = inspect_case(args.root, json.loads(args.case.read_text()))
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0 if report['status'] == 'RECORDED_ENGINE_TERMINAL' else 1
    if os.environ.get('GITHUB_RUN_ATTEMPT') != '1':
        parser.error('Only original workflow attempts are accepted')
    if os.environ.get('PLAY_FIRST_ISOLATED_ACK') != 'NEW_NONOFFICIAL_ORIGINAL_ONLY':
        parser.error('No separately approved isolated case was supplied')
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    result = supervise(command, Path.cwd(), args.root, args.seconds, identity=args.identity)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result['processStatus'] == 'EXITED_ZERO' else 1


if __name__ == '__main__':
    raise SystemExit(main())
