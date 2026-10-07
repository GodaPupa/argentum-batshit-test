"""Durable evidence witness with external-checkpoint-conditioned reconciliation.

Trusted independent checkpoint retention is REQUIRED; never discover expected
checkpoint from this journal. No storage-root rollback resistance, admission,
experimental claim, restart continuation of retained anchors, or operational keys.
"""
import fcntl
import hashlib
import json
import os
import stat
import threading

from r1_receiver_evidence import canonical, decode, digest, Rejected, HEX
from r1_supervisor_channel import seal, authenticate, verify_export

MAX_JOURNAL = 8 * 1024 * 1024


def _directory(path):
    path = os.fspath(path)
    if not os.path.isabs(path) or path.startswith('//') or os.path.normpath(path) != path:
        raise Rejected('canonical absolute path required')
    parent, leaf = os.path.split(path)
    if not leaf:
        raise Rejected('journal leaf required')
    fd = os.open('/', os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        for part in parent.split('/')[1:]:
            if part:
                child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
                os.close(fd)
                fd = child
        return fd, leaf
    except BaseException:
        os.close(fd)
        raise


class AnchorWitness:
    def __init__(self, path, witness_key, supervisor_key, witness_id, checkpoint=None):
        self._mutex = threading.Lock()
        self._fd = None
        self._failed = False
        self._pid = os.getpid()
        self._seq, self._tail = 0, '0' * 64
        self._anchors = {}
        self._size = 0
        if (type(witness_key) is not bytes or len(witness_key) != 32 or
            type(supervisor_key) is not bytes or len(supervisor_key) != 32 or witness_key == supervisor_key):
            raise Rejected('distinct provisioned witness/supervisor keys required')
        if type(witness_id) is not str or not HEX.fullmatch(witness_id):
            raise Rejected('witness identity')
        self._key, self._supervisor_key, self._id = witness_key, supervisor_key, witness_id
        parent, leaf = _directory(path)
        try:
            flags = os.O_RDWR | os.O_APPEND | os.O_NOFOLLOW
            if checkpoint is None:
                flags |= os.O_CREAT | os.O_EXCL
            self._fd = os.open(leaf, flags, 0o600, dir_fd=parent)
            metadata = os.fstat(self._fd)
            if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
                raise Rejected('journal type/alias')
            fcntl.flock(self._fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if checkpoint is None:
                os.fsync(parent)
                self._append('GENESIS', {'witness': self._id})
            else:
                self._load()
                self._check(checkpoint)
                # A restart never revives an acknowledged but unconsumed anchor.
                self._append('RECOVER', {'abandoned': self._retained()})
        except BaseException:
            self._failed = True
            self.close()
            raise
        finally:
            os.close(parent)

    def _retained(self):
        return sorted(k for k, v in self._anchors.items() if v['state'] == 'RETAINED')

    def _reduce(self, kind, data):
        if type(data) is not dict:
            raise Rejected('record data')
        if kind == 'GENESIS' and self._seq == 0 and data == {'witness': self._id}:
            return
        if self._seq == 0:
            raise Rejected('missing genesis')
        if kind == 'RETAIN':
            if set(data) != {'launch', 'context', 'anchor', 'bindings_sha256'}:
                raise Rejected('retention fields')
            for k in ('launch', 'context', 'bindings_sha256'):
                if type(data[k]) is not str or not HEX.fullmatch(data[k]):
                    raise Rejected('retention digest')
            if data['launch'] in self._anchors or type(data['anchor']) is not str:
                raise Rejected('duplicate launch/anchor')
            anchor = authenticate(self._supervisor_key, data['anchor'].encode('ascii'),
                                  'receiver-anchor', data['context'])
            if anchor.get('launch') != data['launch']:
                raise Rejected('retention launch')
            self._anchors[data['launch']] = dict(data, state='RETAINED')
        elif kind == 'CONSUME':
            if set(data) != {'launch', 'context', 'anchor_sha256'}:
                raise Rejected('consumption fields')
            a = self._anchors.get(data['launch'])
            if (a is None or a['state'] != 'RETAINED' or a['context'] != data['context'] or
                hashlib.sha256(a['anchor'].encode('ascii')).hexdigest() != data['anchor_sha256']):
                raise Rejected('replay/wrong anchor/context')
            a['state'] = 'CONSUMED'
        elif kind == 'RECOVER':
            if data != {'abandoned': self._retained()}:
                raise Rejected('recovery set')
            for launch in data['abandoned']:
                self._anchors[launch]['state'] = 'ABANDONED'
        else:
            raise Rejected('unknown/reordered journal record')

    def _load(self):
        size = os.fstat(self._fd).st_size
        if not 0 < size <= MAX_JOURNAL:
            raise Rejected('journal size')
        raw = os.pread(self._fd, size + 1, 0)
        if len(raw) != size or not raw.endswith(b'\n'):
            raise Rejected('partial/changed journal')
        for line in raw[:-1].split(b'\n'):
            row = decode(line)
            if (set(row) != {'seq', 'previous', 'kind', 'data'} or type(row['seq']) is not int or
                row['seq'] != self._seq or row['previous'] != self._tail):
                raise Rejected('journal chain/order')
            self._reduce(row['kind'], row['data'])
            self._seq += 1
            self._tail = hashlib.sha256(line).hexdigest()
        self._size = size

    def _live(self):
        if self._failed or self._fd is None or os.getpid() != self._pid:
            raise Rejected('terminal/forked witness')
        if os.fstat(self._fd).st_size != self._size:
            self._failed = True
            raise Rejected('journal changed')

    def _append(self, kind, data):
        row = canonical({'seq': self._seq, 'previous': self._tail, 'kind': kind, 'data': data})
        if len(row) > 16384 or self._size + len(row) + 1 > MAX_JOURNAL:
            raise Rejected('journal capacity')
        framed = row + b'\n'
        try:
            while framed:
                n = os.write(self._fd, framed)
                if n <= 0:
                    raise OSError('zero write')
                framed = framed[n:]
            os.fsync(self._fd)
            self._reduce(kind, data)
            self._seq += 1
            self._tail = hashlib.sha256(row).hexdigest()
            self._size += len(row) + 1
        except BaseException:
            self._failed = True
            raise

    def _check(self, checkpoint):
        value = authenticate(self._key, checkpoint, 'witness-checkpoint', self._id)
        if canonical(value) != canonical({'rows': self._seq, 'tail': self._tail, 'witness': self._id}):
            raise Rejected('stale/rollback/uncertain checkpoint')

    def checkpoint(self):
        with self._mutex:
            self._live()
            return seal(self._key, 'witness-checkpoint', self._id,
                        {'rows': self._seq, 'tail': self._tail, 'witness': self._id})

    def retain(self, checkpoint, context, signed_anchor, transcript, expected_bindings, expected_members):
        with self._mutex:
            try:
                self._live()
                self._check(checkpoint)
                if type(context) is not str or not HEX.fullmatch(context):
                    raise Rejected('context digest')
                expected_bindings = json.loads(canonical(expected_bindings))
                expected_members = json.loads(canonical(expected_members))
                verify_export(self._supervisor_key, context, signed_anchor, transcript,
                              expected_bindings, expected_members)
                anchor = authenticate(self._supervisor_key, signed_anchor, 'receiver-anchor', context)
                if anchor['launch'] in self._anchors:
                    raise Rejected('launch already retained/consumed/abandoned')
                self._append('RETAIN', {'launch': anchor['launch'], 'context': context,
                                       'anchor': signed_anchor.decode('ascii'),
                                       'bindings_sha256': digest(expected_bindings)})
                return {'launch': anchor['launch'], 'state': 'RETAINED', 'admission_authority': False}
            except BaseException:
                self._failed = True
                raise

    def consume(self, checkpoint, launch, context, anchor_sha256):
        with self._mutex:
            try:
                self._live()
                self._check(checkpoint)
                a = self._anchors.get(launch)
                if (a is None or a['state'] != 'RETAINED' or a['context'] != context or
                    hashlib.sha256(a['anchor'].encode('ascii')).hexdigest() != anchor_sha256):
                    raise Rejected('unknown/replay/abandoned/substituted anchor')
                self._append('CONSUME', {'launch': launch, 'context': context, 'anchor_sha256': anchor_sha256})
                return {'launch': launch, 'state': 'CONSUMED', 'admission_authority': False}
            except BaseException:
                self._failed = True
                raise

    def close(self):
        with self._mutex:
            self._failed = True
            if self._fd is not None:
                os.close(self._fd)
                self._fd = None
