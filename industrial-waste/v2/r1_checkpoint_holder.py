"""Prospective single-authority checkpoint holder; evidence only.

Trusted local calls, independently provisioned keys and non-rollback storage are
PRECONDITIONS. No network service, distributed consensus, key custody or admission.
An INTENT is permanent if completion is uncertain; there is no resume/reset API.
"""
import fcntl
import hashlib
import os
import stat
import threading

from r1_anchor_witness import AnchorWitness, _directory
from r1_receiver_evidence import Rejected, HEX
from r1_supervisor_channel import seal, authenticate

LIMIT = 8 * 1024 * 1024


def identity(value):
    if type(value) is not str or not HEX.fullmatch(value):
        raise Rejected('identity digest required')
    return value


def checkpoint(key, witness, raw):
    value = authenticate(key, raw, 'witness-checkpoint', witness)
    if (set(value) != {'rows', 'tail', 'witness'} or value['witness'] != witness or
            type(value['rows']) is not int or value['rows'] < 1):
        raise Rejected('checkpoint grammar')
    identity(value['tail'])
    return value


def verify_snapshot(key, holder, witness, instance, nonce, raw):
    """Caller supplies a fresh unpredictable nonce and never reuses it.

    Correlation proves response freshness only against an honest live authority;
    callers must retain their revision floor separately across restarts.
    """
    value = authenticate(key, raw, 'checkpoint-holder-snapshot', identity(holder))
    if (set(value) != {'witness', 'instance', 'nonce', 'revision', 'checkpoint', 'pending',
                       'admission_authority'} or value['witness'] != identity(witness) or
            value['instance'] != identity(instance) or value['nonce'] != identity(nonce) or
            type(value['revision']) is not int or value['revision'] < 1 or
            value['admission_authority'] is not False):
        raise Rejected('snapshot binding')
    return value


class CheckpointHolder:
    def __init__(self, path, holder_key, holder_id, registry, *, create=False):
        # Registry is trusted provisioning, never receiving observations. One key
        # per witness. Holding the same keys in a clone does not authenticate a host.
        self._lock = threading.Lock()
        self._fd = None
        self._dead = False
        self._pid = os.getpid()
        self._states, self._instances, self._launches = {}, set(), set()
        self._seq, self._tail, self._size = 0, '0' * 64, 0
        self._id = identity(holder_id)
        if type(holder_key) is not bytes or len(holder_key) != 32 or not registry:
            raise Rejected('holder key/registry')
        self._key = holder_key
        self._registry = dict(registry)
        keys = [holder_key]
        for wid, key in self._registry.items():
            identity(wid)
            if type(key) is not bytes or len(key) != 32 or key in keys:
                raise Rejected('distinct provisioned keys')
            keys.append(key)
        parent, leaf = _directory(path)
        try:
            flags = os.O_RDWR | os.O_APPEND | os.O_NOFOLLOW
            if create:
                flags |= os.O_CREAT | os.O_EXCL
            self._fd = os.open(leaf, flags, 0o600, dir_fd=parent)
            meta = os.fstat(self._fd)
            if not stat.S_ISREG(meta.st_mode) or meta.st_nlink != 1:
                raise Rejected('storage alias/type')
            fcntl.flock(self._fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if create:
                os.fsync(parent)
                self._append('GENESIS', {'registry': sorted(self._registry)})
            else:
                self._load()
        except BaseException:
            self.close()
            raise
        finally:
            os.close(parent)

    def _apply(self, kind, data):
        if self._seq == 0:
            if kind != 'GENESIS' or data != {'registry': sorted(self._registry)}:
                raise Rejected('holder genesis/registry')
            return
        if kind == 'ENROLL':
            if set(data) != {'witness', 'instance', 'checkpoint'}:
                raise Rejected('enrollment fields')
            wid, instance = identity(data['witness']), identity(data['instance'])
            if wid not in self._registry or wid in self._states or instance in self._instances:
                raise Rejected('unknown/duplicate enrollment')
            cp = checkpoint(self._registry[wid], wid, data['checkpoint'].encode('ascii'))
            if cp['rows'] != 1:
                raise Rejected('enrollment requires genesis checkpoint')
            self._states[wid] = dict(data, revision=1, pending=None)
            self._instances.add(instance)
        elif kind == 'INTENT':
            if set(data) != {'witness', 'instance', 'revision', 'launch', 'anchor_sha256'}:
                raise Rejected('intent fields')
            state = self._cas(data['witness'], data['instance'], data['revision'])
            identity(data['anchor_sha256']); identity(data['launch'])
            if data['launch'] in self._launches:
                raise Rejected('launch already attempted')
            self._launches.add(data['launch'])
            state['pending'] = dict(data)
            state['revision'] += 1
        elif kind == 'PUBLISH':
            if set(data) != {'witness', 'checkpoint'} or data['witness'] not in self._states:
                raise Rejected('publication fields')
            wid = data['witness']; state = self._states[wid]
            old = checkpoint(self._registry[wid], wid, state['checkpoint'].encode('ascii'))
            new = checkpoint(self._registry[wid], wid, data['checkpoint'].encode('ascii'))
            if state['pending'] is None or new['rows'] != old['rows'] + 1 or new['tail'] == old['tail']:
                raise Rejected('publication ordering')
            state['checkpoint'] = data['checkpoint']
            state['pending'] = None
            state['revision'] += 1
        else:
            raise Rejected('unknown holder transition')

    def _cas(self, wid, instance, revision):
        state = self._states.get(wid)
        if (state is None or state['instance'] != instance or type(revision) is not int or
                state['revision'] != revision or state['pending'] is not None):
            raise Rejected('enrollment/CAS/pending uncertainty fence')
        return state

    def _live(self):
        if self._dead or self._fd is None or self._pid != os.getpid():
            raise Rejected('terminal/forked holder')
        if os.fstat(self._fd).st_size != self._size:
            self._dead = True
            raise Rejected('holder storage changed')

    def _append(self, kind, data):
        raw = seal(self._key, 'checkpoint-holder-record', self._id,
                   {'seq': self._seq, 'previous': self._tail, 'kind': kind, 'data': data})
        if self._size + len(raw) + 1 > LIMIT:
            raise Rejected('holder capacity')
        try:
            pending = raw + b'\n'
            while pending:
                n = os.write(self._fd, pending)
                if n <= 0:
                    raise OSError('zero holder write')
                pending = pending[n:]
            os.fsync(self._fd)
            self._apply(kind, data)
            self._seq += 1; self._tail = hashlib.sha256(raw).hexdigest()
            self._size += len(raw) + 1
        except BaseException:
            self._dead = True
            raise

    def _load(self):
        size = os.fstat(self._fd).st_size
        if not 0 < size <= LIMIT:
            raise Rejected('holder journal size')
        raw = os.pread(self._fd, size + 1, 0)
        if len(raw) != size or not raw.endswith(b'\n'):
            raise Rejected('partial holder journal')
        for line in raw[:-1].split(b'\n'):
            row = authenticate(self._key, line, 'checkpoint-holder-record', self._id)
            if (set(row) != {'seq', 'previous', 'kind', 'data'} or type(row['seq']) is not int or
                    row['seq'] != self._seq or row['previous'] != self._tail):
                raise Rejected('holder chain')
            self._apply(row['kind'], row['data'])
            self._seq += 1; self._tail = hashlib.sha256(line).hexdigest()
        self._size = size

    def enroll(self, witness, instance, initial_checkpoint):
        with self._lock:
            self._live()
            if witness not in self._registry or witness in self._states or instance in self._instances:
                raise Rejected('unknown/duplicate enrollment')
            identity(instance)
            cp = checkpoint(self._registry[witness], witness, initial_checkpoint)
            if cp['rows'] != 1:
                raise Rejected('initial checkpoint required')
            self._append('ENROLL', {'witness': witness, 'instance': instance,
                                   'checkpoint': initial_checkpoint.decode('ascii')})

    def snapshot(self, witness, instance, nonce):
        with self._lock:
            self._live(); identity(nonce)
            state = self._states.get(witness)
            if state is None or state['instance'] != instance:
                raise Rejected('snapshot enrollment')
            return seal(self._key, 'checkpoint-holder-snapshot', self._id,
                        dict(state, nonce=nonce, admission_authority=False))

    def retain(self, witness, instance, revision, local_witness, context, signed_anchor,
               transcript, expected_bindings, expected_members):
        """The sole publication path; no resume of pending intent after exceptions.

        Direct access to the local witness bypasses this protocol and is outside
        its safety contract. Deployment must withhold that access from workers.
        """
        with self._lock:
            self._live()
            state = self._cas(witness, instance, revision)
            if type(local_witness) is not AnchorWitness:
                raise Rejected('exact local witness implementation required')
            before = local_witness.checkpoint()
            if before != state['checkpoint'].encode('ascii'):
                raise Rejected('local witness/checkpoint substitution')
            # Independently authenticate the launch to tombstone before any retain.
            anchor = authenticate(local_witness._supervisor_key, signed_anchor, 'receiver-anchor', context)
            launch = identity(anchor['launch'])
            if launch in self._launches:
                raise Rejected('duplicate launch')
            self._append('INTENT', {'witness': witness, 'instance': instance, 'revision': revision,
                                   'launch': launch, 'anchor_sha256': hashlib.sha256(signed_anchor).hexdigest()})
            # From this point EVERY exception leaves the externally durable intent
            # pending. Even genuine loss of the local RETAIN bytes cannot reissue it.
            result = local_witness.retain(before, context, signed_anchor, transcript,
                                          expected_bindings, expected_members)
            after = local_witness.checkpoint()
            self._append('PUBLISH', {'witness': witness, 'checkpoint': after.decode('ascii')})
            return dict(result, holder_revision=self._states[witness]['revision'])

    def close(self):
        # Constructor error cleanup also calls close; no lock is held there.
        with self._lock:
            self._dead = True
            if self._fd is not None:
                os.close(self._fd); self._fd = None
