"""Exact accepted sibling composition; authenticated evidence read only.

The exchange callback is an untrusted bytes carrier, not a qualified network.
Trusted keys/bootstrap/storage remain prerequisites. No admission authority.
"""
import threading

from r1_client_freshness import FreshnessClient
from r1_holder_read_transport import binding_context, encode_request, decode_response
from r1_receiver_evidence import Rejected


class VerifiedHolderRead:
    def __init__(self, path, client_key, transport_key, holder_key, witness_key,
                 binding, initial_revision, initial_checkpoint, *, create=False):
        self._lock = threading.Lock()
        self._closed = False
        # Check all four local key roles before creating client storage. Separation
        # from other protocol keys remains a trusted provisioning responsibility.
        keys = (client_key, transport_key, holder_key, witness_key)
        if any(type(k) is not bytes or len(k) != 32 for k in keys) or len(set(keys)) != 4:
            raise Rejected('four distinct provisioned keys required')
        binding_context(binding)
        self._binding = dict(binding)
        self._transport_key = transport_key
        client_binding = {k:v for k,v in self._binding.items() if k != 'source'}
        self._client = FreshnessClient(path, client_key, holder_key, witness_key,
                                       client_binding, initial_revision, initial_checkpoint,
                                       create=create)

    def read(self, exchange):
        """One attempt; never retry a carrier call or expose unchecked evidence.

        ISSUE is durable before exchange receives bytes. Outer response validation
        precedes nested holder/witness authentication and durable ACCEPT. A failure
        at any stage closes this object. The unchanged client journal governs
        subsequent reopen: outstanding ISSUE cannot resume; durable ACCEPT retains
        its floor even if the return/acknowledgement was lost.
        """
        with self._lock:
            if self._closed:
                raise Rejected('closed read composition')
            try:
                nonce = self._client.issue()
                request = encode_request(self._transport_key, self._binding, nonce)
                response = exchange(request)
                snapshot = decode_response(self._transport_key, self._binding, request, response)
                # accept independently authenticates holder and witness keys,
                # rejects pending state/regression/equivocation, then fsyncs ACCEPT.
                return self._client.accept(snapshot)
            except BaseException:
                self._closed = True
                self._client.close()
                raise

    def close(self):
        with self._lock:
            self._closed = True
            self._client.close()
