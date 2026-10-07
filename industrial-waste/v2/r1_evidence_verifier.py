"""Offline evidence integrity/grammar checking under an independently trusted anchor.

Does not authenticate anchors, create authority, consume identities, or prove VM
coverage. Never obtain expected policy or anchor from the transcript being checked.
"""
import hashlib
import json
import re

HEX = re.compile(r'[0-9a-f]{64}\Z')
BINDING_KEYS = {'source', 'tree', 'runtime', 'process', 'channel', 'receiver', 'policy'}
MAX_BYTES = 8 * 1024 * 1024
MAX_ROWS = 10000


class InvalidEvidence(ValueError):
    pass


def encode(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True,
                      allow_nan=False).encode('ascii')


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def require(condition, reason):
    if not condition:
        raise InvalidEvidence(reason)


def hashes(value, keys):
    require(type(value) is dict and set(value) == keys, 'digest fields')
    require(all(type(v) is str and HEX.fullmatch(v) for v in value.values()), 'digest values')


def parse(raw):
    def unique(items):
        result = {}
        for k, v in items:
            require(k not in result, 'duplicate key')
            result[k] = v
        return result
    require(0 < len(raw) <= 16384, 'row limit')
    try:
        row = json.loads(raw.decode('ascii'), object_pairs_hook=unique,
                         parse_constant=lambda _: (_ for _ in ()).throw(InvalidEvidence('constant')))
        require(type(row) is dict and encode(row) == raw, 'noncanonical row')
        return row
    except (UnicodeError, ValueError, TypeError, RecursionError) as exc:
        raise InvalidEvidence('invalid JSON row') from exc


def verify(transcript, anchor, expected_bindings, expected_members):
    """Return conditional evidence consistency only; anchor authenticity is external.

    anchor contains independently retained launch/challenge/tail/transcript SHA-256
    and row count. Caller must not calculate it from the supplied transcript.
    All inputs are snapshotted before validation; no filesystem paths are accepted.
    """
    require(type(transcript) is bytes and 0 < len(transcript) <= MAX_BYTES, 'transcript size/type')
    try:
        # Copy nested policy/anchor data without aliases; disallow exotic objects.
        anchor = json.loads(encode(anchor))
        bindings = json.loads(encode(expected_bindings))
        members = json.loads(encode(expected_members))
    except (ValueError, TypeError, RecursionError) as exc:
        raise InvalidEvidence('input snapshot') from exc
    require(type(anchor) is dict and set(anchor) == {'launch', 'challenge', 'tail', 'sha256', 'rows'}, 'anchor fields')
    hashes({k: anchor[k] for k in ('launch', 'challenge', 'tail', 'sha256')}, {'launch', 'challenge', 'tail', 'sha256'})
    require(type(anchor['rows']) is int and 6 <= anchor['rows'] <= MAX_ROWS, 'anchor count')
    hashes(bindings, BINDING_KEYS)
    require(type(members) is dict and bool(members), 'policy required')
    for key, value in members.items():
        require(type(key) is str and HEX.fullmatch(key), 'member id')
        hashes(value, {'definition', 'loader', 'module', 'origin'})
    require(sha(encode(members)) == bindings['policy'], 'policy digest')
    require(sha(transcript) == anchor['sha256'], 'output substitution/truncation')
    require(transcript.endswith(b'\n'), 'unterminated transcript')
    lines = transcript[:-1].split(b'\n')
    require(len(lines) == anchor['rows'], 'row count')
    previous, state, pending, attempts = '0' * 64, 'NEW', None, set()
    for i, raw in enumerate(lines):
        row = parse(raw)
        require(set(row) == {'seq', 'previous', 'kind', 'data'}, 'row fields')
        require(type(row['seq']) is int and row['seq'] == i and row['previous'] == previous, 'chain order')
        previous = sha(raw)
        kind, data = row['kind'], row['data']
        require(type(kind) is str and type(data) is dict, 'event types')
        if state == 'NEW':
            require(kind == 'RESERVED' and set(data) == {'bindings', 'launch', 'challenge'}, 'reservation required')
            require(data == {'bindings': bindings, 'launch': anchor['launch'], 'challenge': anchor['challenge']}, 'reservation binding')
            state = 'RESERVED'
        elif state == 'RESERVED':
            require(kind == 'BOUND' and data == {}, 'binding required')
            state = 'RECORDING'
        elif state == 'RECORDING':
            if kind == 'ATTEMPT':
                require(pending is None, 'overlapping attempt')
                require(set(data) == {'id', 'member', 'family', 'definition', 'loader', 'module', 'origin'}, 'attempt fields')
                require(all(type(v) is str for v in data.values()), 'attempt types')
                require(HEX.fullmatch(data['id']) and data['id'] not in attempts, 'duplicate/invalid attempt')
                require(data['family'] == 'ordinary', 'hidden provenance absent')
                require(data['member'] in members and members[data['member']] ==
                        {k: data[k] for k in ('definition', 'loader', 'module', 'origin')}, 'out of policy')
                pending = data['id']
                attempts.add(pending)
            elif kind == 'RESULT':
                require(pending is not None and data == {'id': pending, 'result': 'DEFINED'}, 'attempt/result mismatch')
                pending = None
            elif kind == 'CUTOFF':
                require(data == {} and pending is None and bool(attempts), 'incomplete cutoff')
                state = 'CLOSED'
            else:
                raise InvalidEvidence('failed/unknown/reordered event')
        elif state == 'CLOSED':
            require(kind == 'CONSUMED_EVIDENCE' and data == {}, 'consumption required')
            state = 'CONSUMED'
        else:
            raise InvalidEvidence('late event')
    require(previous == anchor['tail'] and state == 'CONSUMED', 'incomplete/incorrect terminal anchor')
    return {'consistent_with_supplied_anchor': True, 'ordinary_attempts': len(attempts),
            'rows': len(lines), 'tail': previous, 'admission_authority': False,
            'anchor_authentication_established': False, 'vm_provenance_established': False}
