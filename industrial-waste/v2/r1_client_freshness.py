"""Persistent holder-snapshot freshness under trusted nonrollback client storage.

No remote transport, service deployment, host attestation or admission. Keys and
initial revision/checkpoint are trusted provisioning, never discovered on receive.
"""
import fcntl
import hashlib
import hmac
import os
import stat
import threading

from r1_anchor_witness import _directory
from r1_checkpoint_holder import checkpoint, identity, verify_snapshot
from r1_receiver_evidence import Rejected, canonical
from r1_supervisor_channel import authenticate, seal

LIMIT = 8 * 1024 * 1024


class FreshnessClient:
    def __init__(self, path, client_key, holder_key, witness_key, binding,
                 initial_revision, initial_checkpoint, *, create=False):
        self._lock = threading.Lock()
        self._fd = None
        self._dead = False
        self._pid = os.getpid()
        self._active = False
        self._seq, self._size, self._tail = 0, 0, '0' * 64
        self._ordinal = 0
        self._pending = None
        keys = (client_key, holder_key, witness_key)
        if any(type(k) is not bytes or len(k) != 32 for k in keys) or len(set(keys)) != 3:
            raise Rejected('distinct provisioned keys required')
        if type(binding) is not dict or set(binding) != {'client', 'holder', 'witness', 'instance'}:
            raise Rejected('provisioned binding fields')
        self._binding = {k:identity(v) for k,v in binding.items()}
        self._context = hashlib.sha256(canonical(self._binding)).hexdigest()
        self._key, self._holder_key, self._witness_key = keys
        if type(initial_revision) is not int or initial_revision < 1:
            raise Rejected('trusted bootstrap revision required')
        checkpoint(witness_key, self._binding['witness'], initial_checkpoint)
        self._floor, self._checkpoint = initial_revision, initial_checkpoint
        self._bootstrap = {'binding': self._binding, 'revision': initial_revision,
                           'checkpoint': initial_checkpoint.decode('ascii')}
        parent, leaf = _directory(path)
        try:
            flags = os.O_RDWR | os.O_APPEND | os.O_NOFOLLOW
            if create:
                flags |= os.O_CREAT | os.O_EXCL
            self._fd = os.open(leaf, flags, 0o600, dir_fd=parent)
            meta = os.fstat(self._fd)
            if not stat.S_ISREG(meta.st_mode) or meta.st_nlink != 1:
                raise Rejected('client storage type/alias')
            fcntl.flock(self._fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            if create:
                os.fsync(parent)
                self._append('GENESIS', self._bootstrap)
            else:
                self._load()
            # Never revive an exposed request from an earlier process/session.
            self._active = False
        except BaseException:
            self.close()
            raise
        finally:
            os.close(parent)

    def _nonce(self, ordinal):
        return hmac.new(self._key, b'industrial-client-challenge-v1\0' +
                        canonical({'context':self._context,'ordinal':ordinal}), hashlib.sha256).hexdigest()

    def _validated(self, raw):
        b = self._binding
        value = verify_snapshot(self._holder_key, b['holder'], b['witness'], b['instance'], self._pending, raw)
        if value['pending'] is not None or type(value['checkpoint']) is not str:
            raise Rejected('holder pending uncertainty/checkpoint type')
        new_raw = value['checkpoint'].encode('ascii')
        old = checkpoint(self._witness_key, b['witness'], self._checkpoint)
        new = checkpoint(self._witness_key, b['witness'], new_raw)
        revision = value['revision']
        if revision < self._floor or new['rows'] < old['rows']:
            raise Rejected('revision/checkpoint regression')
        if revision == self._floor and new_raw != self._checkpoint:
            raise Rejected('same revision equivocation')
        if new['rows'] == old['rows'] and new_raw != self._checkpoint:
            raise Rejected('same checkpoint height fork')
        if revision > self._floor and new['rows'] == old['rows']:
            raise Rejected('advanced stable revision without witness transition')
        return revision, new_raw

    def _apply(self, kind, data):
        if self._seq == 0:
            if kind != 'GENESIS' or canonical(data) != canonical(self._bootstrap):
                raise Rejected('client bootstrap mismatch')
        elif kind == 'ISSUE':
            if (set(data) != {'ordinal','nonce'} or type(data['ordinal']) is not int or
                    data['ordinal'] != self._ordinal + 1 or self._pending is not None or
                    data['nonce'] != self._nonce(data['ordinal'])):
                raise Rejected('challenge order/reuse/pending')
            self._ordinal = data['ordinal']; self._pending = data['nonce']
        elif kind == 'ACCEPT':
            if set(data) != {'snapshot'} or type(data['snapshot']) is not str or self._pending is None:
                raise Rejected('unsolicited completion')
            revision, cp = self._validated(data['snapshot'].encode('ascii'))
            self._floor, self._checkpoint = revision, cp
            self._pending = None
        else:
            raise Rejected('unknown client transition')

    def _append(self, kind, data):
        raw = seal(self._key, 'freshness-client-record', self._context,
                   {'seq':self._seq,'previous':self._tail,'kind':kind,'data':data})
        if self._size + len(raw) + 1 > LIMIT:
            self._dead = True
            raise Rejected('client capacity')
        try:
            pending = raw + b'\n'
            while pending:
                n = os.write(self._fd, pending)
                if n <= 0:
                    raise OSError('zero client write')
                pending = pending[n:]
            os.fsync(self._fd)
            self._apply(kind, data)
            self._seq += 1; self._tail = hashlib.sha256(raw).hexdigest(); self._size += len(raw)+1
        except BaseException:
            self._dead = True
            raise

    def _load(self):
        size = os.fstat(self._fd).st_size
        if not 0 < size <= LIMIT:
            raise Rejected('client journal size')
        raw = os.pread(self._fd,size+1,0)
        if len(raw) != size or not raw.endswith(b'\n'):
            raise Rejected('partial client journal')
        for line in raw[:-1].split(b'\n'):
            row = authenticate(self._key,line,'freshness-client-record',self._context)
            if (set(row) != {'seq','previous','kind','data'} or type(row['seq']) is not int or
                    row['seq'] != self._seq or row['previous'] != self._tail):
                raise Rejected('client record order')
            self._apply(row['kind'],row['data'])
            self._seq += 1; self._tail = hashlib.sha256(line).hexdigest()
        self._size = size

    def _live(self):
        if self._dead or self._fd is None or os.getpid() != self._pid:
            raise Rejected('terminal/forked client')
        if os.fstat(self._fd).st_size != self._size:
            self._dead = True
            raise Rejected('client storage changed')

    def issue(self):
        with self._lock:
            self._live()
            if self._pending is not None:
                raise Rejected('pending request cannot be resumed/refilled')
            nonce = self._nonce(self._ordinal+1)
            self._append('ISSUE', {'ordinal':self._ordinal+1,'nonce':nonce})
            self._active = True
            return nonce

    def accept(self, snapshot):
        with self._lock:
            try:
                self._live()
                if not self._active or self._pending is None:
                    raise Rejected('no active fresh request')
                self._validated(snapshot)
                self._append('ACCEPT', {'snapshot':snapshot.decode('ascii')})
                self._active = False
                return {'revision':self._floor,'checkpoint':self._checkpoint,
                        'admission_authority':False}
            except BaseException:
                self._dead = True
                raise

    def close(self):
        with self._lock:
            self._dead = True
            if self._fd is not None:
                os.close(self._fd); self._fd = None
