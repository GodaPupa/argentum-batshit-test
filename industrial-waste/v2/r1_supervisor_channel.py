"""Evidence-only authenticated supervisor channel using provisioned HMAC keys.

The trusted receiver and supervisor share a dedicated 256-bit key, never supplied
by the worker or wire. MACs establish possession of that provisioned key, not VM
provenance, nonrepudiation, descriptor custody, or continuous lifetime identity.
No admission/launch API. Existing accepted components remain unchanged.
"""
import hashlib
import hmac
import json
import os
import secrets
import socket
import stat
import struct
import threading
import time

from r1_receiver_evidence import ReceiverEvidence, Rejected, canonical, decode, digest, HEX
from r1_peer_identity import sample_peer
from r1_evidence_verifier import verify, MAX_BYTES

DOMAIN = b'industrial-supervisor-evidence-v1\x00'
MAX_WIRE = 16384


def _key(key):
    if type(key) is not bytes or len(key) != 32:
        raise Rejected('dedicated 256-bit provisioned key required')
    return key


def seal(key, role, context, payload):
    """Trusted endpoint encoder; callers must own the independently provisioned key."""
    _key(key)
    body = {'role': role, 'context': context, 'payload': payload}
    tag = hmac.new(key, DOMAIN + canonical(body), hashlib.sha256).hexdigest()
    raw = canonical(dict(body, mac=tag))
    if len(raw) > MAX_WIRE:
        raise Rejected('wire size')
    return raw


def authenticate(key, raw, role, context):
    _key(key)
    value = decode(raw)
    if set(value) != {'role', 'context', 'payload', 'mac'}:
        raise Rejected('authenticated envelope fields')
    if value['role'] != role or value['context'] != context:
        raise Rejected('role/context substitution')
    tag = value.pop('mac')
    expected = hmac.new(key, DOMAIN + canonical(value), hashlib.sha256).hexdigest()
    if type(tag) is not str or not HEX.fullmatch(tag) or not hmac.compare_digest(tag, expected):
        raise Rejected('MAC mismatch')
    if type(value['payload']) is not dict:
        raise Rejected('payload type')
    return value['payload']


def _read_frame(sock, timeout):
    deadline = time.monotonic() + timeout
    def exact(size):
        parts = []
        while size:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError('frame deadline')
            sock.settimeout(remaining)
            chunk = sock.recv(size)
            if not chunk:
                raise Rejected('EOF/truncated frame')
            parts.append(chunk)
            size -= len(chunk)
        return b''.join(parts)
    size = struct.unpack('!I', exact(4))[0]
    if not 0 < size <= MAX_WIRE:
        raise Rejected('wire length')
    return exact(size)


def _send_frame(sock, raw, timeout):
    sock.settimeout(timeout)
    sock.sendall(struct.pack('!I', len(raw)) + raw)


class SupervisorChannel:
    def __init__(self, path, key, supervisor_id, pidfd, peer, expected_peer,
                 bindings, members, timeout=2.0):
        self._lock = threading.Lock()
        self._failed = False
        self._done = False
        self._started = False
        self._receiver = None
        self._peer = None
        self._pidfd = None
        self._key = _key(key)
        if type(supervisor_id) is not str or not HEX.fullmatch(supervisor_id):
            raise Rejected('supervisor identity')
        if type(timeout) not in (float, int) or not 0 < timeout <= 10:
            raise Rejected('bounded timeout required')
        self._timeout = timeout
        self._expected = json.loads(canonical(expected_peer))
        self._members = json.loads(canonical(members))
        try:
            self._pidfd = os.dup(pidfd)
            self._peer = peer.dup()
            sample_peer(self._pidfd, self._peer, self._expected)
            # These are receiver-established bindings, never message-selected fields.
            bound = json.loads(canonical(bindings))
            if set(bound) != {'source', 'tree', 'runtime', 'receiver', 'policy'}:
                raise Rejected('configuration fields')
            bound['process'] = digest(self._expected)
            bound['channel'] = digest({'protocol': 'industrial-supervisor-evidence-v1',
                                      'supervisor': supervisor_id, 'receiver': bound['receiver'],
                                      'process': bound['process'], 'nonce': secrets.token_hex(32)})
            self._receiver = ReceiverEvidence(path, bound, self._members)
            self._challenge = self._receiver.challenge()
            self._bindings = bound
            self._context = digest({'supervisor': supervisor_id, 'challenge': self._challenge})
            self._last = None
        except BaseException:
            self.close()
            raise

    def start(self):
        with self._lock:
            if self._failed or self._started or self._done:
                raise Rejected('session already started/terminal')
            try:
                sample_peer(self._pidfd, self._peer, self._expected)
                self._started = True
                raw = seal(self._key, 'receiver-hello', self._context,
                           {'challenge': self._challenge, 'peer_expected': self._expected,
                            'admission_authority': False})
                _send_frame(self._peer, raw, self._timeout)
                return self._context  # public context; not an authentication secret
            except BaseException:
                self._fail()
                raise

    def _fail(self):
        self._failed = True
        if self._receiver is not None:
            try:
                self._receiver.accept(b'')  # durable terminal failure if still nonterminal
            except (ValueError, OSError):
                pass  # uncertainty never grants an acknowledgement or anchor

    def step(self):
        """Receive one bounded authenticated event on the owned sampled socket."""
        with self._lock:
            if self._failed or self._done or not self._started:
                raise Rejected('inactive/terminal channel')
            try:
                sample_peer(self._pidfd, self._peer, self._expected)
                raw = _read_frame(self._peer, self._timeout)
                frame = authenticate(self._key, raw, 'supervisor-event', self._context)
                sample_peer(self._pidfd, self._peer, self._expected)
                result = self._receiver.accept(canonical(frame))
                # Receiver has fsynced its transition before any authenticated ACK.
                ack = seal(self._key, 'receiver-ack', self._context,
                           {'event_sha256': hashlib.sha256(raw).hexdigest(), 'receipt': result})
                _send_frame(self._peer, ack, self._timeout)
                self._last = result
                self._done = result['state'] == 'CONSUMED'
                return dict(result)
            except BaseException:
                self._fail()
                raise

    def export(self):
        """Return transcript and authenticated receiver-generated anchor once.

        Authentication is symmetric key possession only. External durable retention
        and rollback-resistant one-time use of this anchor are still required.
        """
        with self._lock:
            if self._failed or not self._done:
                raise Rejected('no completed evidence')
            try:
                sample_peer(self._pidfd, self._peer, self._expected)
                fd = os.open('events.jsonl', os.O_RDONLY | os.O_NOFOLLOW,
                             dir_fd=self._receiver._dir_fd)
                with os.fdopen(fd, 'rb') as stream:
                    metadata = os.fstat(stream.fileno())
                    if not stat.S_ISREG(metadata.st_mode) or metadata.st_nlink != 1:
                        raise Rejected('transcript type/alias')
                    raw = stream.read(MAX_BYTES + 1)
                anchor = {'launch': self._challenge['launch'], 'challenge': self._challenge['challenge'],
                          'rows': self._last['seq'], 'tail': self._last['previous'],
                          'sha256': hashlib.sha256(raw).hexdigest()}
                verify(raw, anchor, self._bindings, self._members)
                signed = seal(self._key, 'receiver-anchor', self._context, anchor)
                self._failed = True  # export itself is single-use; never restart/reissue
                return raw, signed
            except BaseException:
                self._fail()
                raise

    def close(self):
        with self._lock:
            self._failed = True
            if self._receiver is not None:
                self._receiver.close()
            if self._peer is not None:
                self._peer.close()
                self._peer = None
            if self._pidfd is not None:
                os.close(self._pidfd)
                self._pidfd = None


def verify_export(key, context, signed_anchor, transcript, expected_bindings, expected_members):
    """Verify a received anchor MAC, then invoke accepted conditional verifier.

    key/context/bindings/policy must be retained independently of exported bytes.
    Does not consume an external identity or prove rollback resistance.
    """
    anchor = authenticate(key, signed_anchor, 'receiver-anchor', context)
    result = verify(transcript, anchor, expected_bindings, expected_members)
    return dict(result, provisioned_key_mac_valid=True, external_rollback_resistance=False)
