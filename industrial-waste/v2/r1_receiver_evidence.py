"""Receiver-owned synthetic evidence interlock; NEVER an admission/VM authority.

Trust assumptions: exclusive trusted receiver process, private immutable storage,
trusted policy and post-exec binding supplied by a future authenticated supervisor.
No reopening/recovery, rollback protection, VM hook, signature or R1 permit here.
"""
import hashlib
import json
import os
import re
import secrets
import threading

MAX_FRAME = 16384
HEX = re.compile(r'[0-9a-f]{64}\Z')
BINDINGS = frozenset(('source', 'tree', 'runtime', 'process', 'channel', 'receiver', 'policy'))


class Rejected(ValueError):
    pass


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True,
                      allow_nan=False).encode('ascii')


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def decode(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise Rejected('duplicate key')
            result[key] = value
        return result
    if type(raw) is not bytes or not 0 < len(raw) <= MAX_FRAME:
        raise Rejected('frame size/type')
    try:
        value = json.loads(raw.decode('ascii'), object_pairs_hook=pairs,
                           parse_constant=lambda _: (_ for _ in ()).throw(Rejected('constant')))
        if type(value) is not dict or canonical(value) != raw:
            raise Rejected('noncanonical frame')
        return value
    except (UnicodeError, ValueError, TypeError, RecursionError) as exc:
        raise Rejected('invalid frame') from exc


def _hashes(value, keys):
    if type(value) is not dict or set(value) != set(keys):
        raise Rejected('binding fields')
    if any(type(v) is not str or not HEX.fullmatch(v) for v in value.values()):
        raise Rejected('binding digest')
    return dict(value)


class ReceiverEvidence:
    """New directory per transcript. Public methods return evidence, never permits.

    Parent path must have no symlink components. OS/kernel/storage authenticity
    and protection against malicious concurrent path replacement remain external.
    An existing transcript is never reopened or resumed, even after a crash.
    """
    def __init__(self, path, bindings, ordinary_members):
        self._lock = threading.Lock()
        self._state = 'INITIALIZING'
        self._fd = None
        self._seq = 0
        self._tail = '0' * 64
        self._attempts = set()
        self._pending = None
        self._bindings = _hashes(bindings, BINDINGS)
        # Member IDs are prospective policy IDs, never learned from event rows.
        if type(ordinary_members) is not dict or not ordinary_members:
            raise Rejected('empty policy')
        self._members = {}
        for key, value in ordinary_members.items():
            if type(key) is not str or not HEX.fullmatch(key):
                raise Rejected('member id')
            self._members[key] = _hashes(value, ('definition', 'loader', 'module', 'origin'))
        if digest(self._members) != self._bindings['policy']:
            raise Rejected('policy binding')
        path = os.fspath(path)
        if not os.path.isabs(path) or os.path.normpath(path) != path:
            raise Rejected('noncanonical path')
        parent, leaf = os.path.split(path)
        if not leaf:
            raise Rejected('missing leaf')
        # Resolve each directory through an open fd; O_NOFOLLOW forbids aliases.
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
        parent_fd = os.open('/', flags)
        try:
            for part in parent.split('/')[1:]:
                if not part:
                    continue
                next_fd = os.open(part, flags, dir_fd=parent_fd)
                os.close(parent_fd)
                parent_fd = next_fd
            os.mkdir(leaf, 0o700, dir_fd=parent_fd)  # O_EXCL-equivalent reservation
            self._dir_fd = os.open(leaf, flags, dir_fd=parent_fd)
            os.fsync(parent_fd)
            self._fd = os.open('events.jsonl', os.O_WRONLY | os.O_CREAT | os.O_EXCL |
                               os.O_NOFOLLOW, 0o600, dir_fd=self._dir_fd)
            os.fsync(self._dir_fd)
            self._launch = secrets.token_hex(32)
            self._challenge = secrets.token_hex(32)
            self._append('RESERVED', {'bindings': self._bindings,
                                      'launch': self._launch, 'challenge': self._challenge})
            self._state = 'RESERVED'
        except BaseException:
            self._state = 'FAILED'
            self.close()
            raise
        finally:
            os.close(parent_fd)

    def _append(self, kind, data):
        row = {'seq': self._seq, 'previous': self._tail, 'kind': kind, 'data': data}
        raw = canonical(row)
        framed = raw + b'\n'
        try:
            while framed:
                n = os.write(self._fd, framed)
                if n <= 0:
                    raise OSError('short write')
                framed = framed[n:]
            os.fsync(self._fd)
        except BaseException:
            self._state = 'FAILED'
            raise
        self._tail = hashlib.sha256(raw).hexdigest()
        self._seq += 1

    def challenge(self):
        with self._lock:
            if self._state != 'RESERVED':
                raise Rejected('challenge unavailable')
            return {'launch': self._launch, 'challenge': self._challenge,
                    'bindings': dict(self._bindings), 'seq': self._seq, 'previous': self._tail}

    def accept(self, raw):
        with self._lock:
            if self._state in ('FAILED', 'CONSUMED', 'DISPOSED'):
                raise Rejected('terminal transcript')
            try:
                event = decode(raw)
                if set(event) != {'launch', 'challenge', 'bindings', 'seq', 'previous', 'kind', 'data'}:
                    raise Rejected('envelope fields')
                if (event['launch'] != self._launch or event['challenge'] != self._challenge or
                    type(event['seq']) is not int or event['seq'] != self._seq or
                    event['previous'] != self._tail or event['bindings'] != self._bindings):
                    raise Rejected('envelope binding/order')
                kind, data = event['kind'], event['data']
                if type(kind) is not str or type(data) is not dict:
                    raise Rejected('event type')
                if kind == 'BIND' and self._state == 'RESERVED' and data == {}:
                    self._append('BOUND', {})
                    self._state = 'RECORDING'
                elif kind == 'ATTEMPT' and self._state == 'RECORDING' and self._pending is None:
                    if set(data) != {'id', 'member', 'family', 'definition', 'loader', 'module', 'origin'}:
                        raise Rejected('attempt fields')
                    if any(type(v) is not str for v in data.values()):
                        raise Rejected('attempt types')
                    if not HEX.fullmatch(data['id']) or data['id'] in self._attempts:
                        raise Rejected('duplicate/invalid attempt id')
                    # Persist structurally valid attempts BEFORE membership decision, even denials.
                    self._append('ATTEMPT', data)
                    self._attempts.add(data['id'])
                    expected = self._members.get(data['member'])
                    actual = {k: data[k] for k in ('definition', 'loader', 'module', 'origin')}
                    if data['family'] != 'ordinary' or expected is None or actual != expected:
                        raise Rejected('unknown definition/provenance; hidden substrate absent')
                    self._pending = data['id']
                elif kind == 'RESULT' and self._state == 'RECORDING':
                    if set(data) != {'id', 'result'} or self._pending is None or data['id'] != self._pending:
                        raise Rejected('missing/reordered attempt')
                    if data['result'] != 'DEFINED':
                        raise Rejected('definition did not succeed')
                    self._append('RESULT', data)
                    self._pending = None
                elif kind == 'CUTOFF' and self._state == 'RECORDING' and data == {}:
                    if self._pending is not None or not self._attempts:
                        raise Rejected('incomplete evidence')
                    self._append('CUTOFF', {})
                    self._state = 'CLOSED'
                elif kind == 'CONSUME_EVIDENCE' and self._state == 'CLOSED' and data == {}:
                    self._append('CONSUMED_EVIDENCE', {})
                    self._state = 'CONSUMED'
                else:
                    raise Rejected('lifecycle violation')
                return {'state': self._state, 'seq': self._seq, 'previous': self._tail,
                        'admission_authority': False}
            except BaseException as exc:
                was_failed = self._state == 'FAILED'
                self._state = 'FAILED'
                if not was_failed:
                    # Raw input digest records malformed/denied attempts without trusting their schema.
                    self._append('FAILED', {'input_sha256': hashlib.sha256(raw).hexdigest()
                                           if type(raw) is bytes else None,
                                           'reason': type(exc).__name__})
                raise

    def close(self):
        """Dispose without continuation. An incomplete file remains unusable evidence."""
        with self._lock:
            if self._state not in ('FAILED', 'CONSUMED'):
                self._state = 'DISPOSED'
            if self._fd is not None:
                os.close(self._fd)
                self._fd = None
            if hasattr(self, '_dir_fd'):
                os.close(self._dir_fd)
                del self._dir_fd
