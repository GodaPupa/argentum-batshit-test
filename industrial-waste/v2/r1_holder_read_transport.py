"""Authenticated read-only holder message boundary, not a deployed network service.

Trusted provisioned endpoint/client/key/source binding. Replay exclusion is scoped
only to this live endpoint instance; persistent client freshness is independent.
"""
import hashlib
import threading

from r1_checkpoint_recovery import RecoveryHolder
from r1_checkpoint_holder import identity
from r1_receiver_evidence import Rejected, canonical
from r1_supervisor_channel import authenticate, seal

MAX_REQUESTS = 4096


def binding_context(binding):
    if type(binding) is not dict or set(binding) != {'client','holder','witness','instance','source'}:
        raise Rejected('trusted transport binding fields')
    return hashlib.sha256(canonical({k:identity(v) for k,v in binding.items()})).hexdigest()


def encode_request(key, binding, nonce):
    return seal(key,'holder-read-request',binding_context(binding),
                {'operation':'SNAPSHOT','nonce':identity(nonce)})


def decode_response(key, binding, request, response):
    context = binding_context(binding)
    req = authenticate(key,request,'holder-read-request',context)
    if set(req) != {'operation','nonce'} or req['operation'] != 'SNAPSHOT':
        raise Rejected('request grammar')
    identity(req['nonce'])
    value = authenticate(key,response,'holder-read-response',context)
    if (set(value) != {'request_sha256','nonce','snapshot','admission_authority'} or
            value['request_sha256'] != hashlib.sha256(request).hexdigest() or
            value['nonce'] != req['nonce'] or type(value['snapshot']) is not str or
            value['admission_authority'] is not False):
        raise Rejected('response binding')
    # Caller separately verifies the holder-signed snapshot and persistent floor.
    return value['snapshot'].encode('ascii')


class HolderReadEndpoint:
    def __init__(self, holder, key, binding):
        if type(holder) is not RecoveryHolder:
            raise Rejected('exact accepted recovery holder required')
        if type(key) is not bytes or len(key) != 32:
            raise Rejected('provisioned transport key')
        self._context = binding_context(binding)
        self._binding = dict(binding)
        if (key == holder._key or key in holder._registry.values() or
                binding['holder'] != holder._id or binding['witness'] not in holder._registry):
            raise Rejected('transport key/holder binding')
        self._holder, self._key = holder, key
        self._seen = set()
        self._lock = threading.Lock()

    def handle(self, request):
        with self._lock:
            value = authenticate(self._key,request,'holder-read-request',self._context)
            if set(value) != {'operation','nonce'} or value['operation'] != 'SNAPSHOT':
                raise Rejected('read-only request grammar')
            nonce = identity(value['nonce'])
            if nonce in self._seen or len(self._seen) >= MAX_REQUESTS:
                raise Rejected('live endpoint replay/capacity')
            # Burn before reading: exceptions never restore a nonce in this instance.
            self._seen.add(nonce)
            b = self._binding
            snapshot = self._holder.snapshot(b['witness'],b['instance'],nonce)
            return seal(self._key,'holder-read-response',self._context,
                        {'request_sha256':hashlib.sha256(request).hexdigest(),'nonce':nonce,
                         'snapshot':snapshot.decode('ascii'),'admission_authority':False})
