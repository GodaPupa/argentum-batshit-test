"""Configuration-frozen journal domain for the adopted verified read composition.

Provisioning labels are trusted input, not runtime measurements. No migration,
rotation, reset, enrollment, or deployment admission is implemented here.
"""
import hashlib
import hmac

from r1_checkpoint_holder import identity
from r1_holder_read_transport import binding_context
from r1_receiver_evidence import Rejected, canonical
from r1_verified_holder_read import VerifiedHolderRead

DOMAIN = b'industrial-bound-holder-read-v1\0'


class BoundHolderRead:
    def __init__(self, path, client_key, transport_key, holder_key, witness_key,
                 binding, deployment, initial_revision, initial_checkpoint, *, create=False):
        # Validate original role separation before derivation can hide an alias.
        keys = (client_key, transport_key, holder_key, witness_key)
        if any(type(k) is not bytes or len(k) != 32 for k in keys) or len(set(keys)) != 4:
            raise Rejected('four distinct provisioned root keys required')
        binding_context(binding)
        frozen = dict(binding)
        config = {'schema': 'industrial-bound-holder-read-v1',
                  'deployment': identity(deployment), 'binding': frozen,
                  'key_ids': {role: hashlib.sha256(DOMAIN + role.encode('ascii') + b'\0' + key).hexdigest()
                              for role, key in zip(('client','transport','holder','witness'), keys)}}
        # Every journal MAC and deterministic challenge is now keyed by the exact
        # configuration. Existing history under any other configuration refuses
        # authentication; there is no second file or cross-file commit window.
        journal_key = hmac.new(client_key, DOMAIN + canonical(config), hashlib.sha256).digest()
        if journal_key in keys:
            raise Rejected('derived key aliases a provisioned key')
        self._reader = VerifiedHolderRead(path, journal_key, transport_key, holder_key,
                                          witness_key, frozen, initial_revision,
                                          initial_checkpoint, create=create)

    def read(self, exchange):
        return self._reader.read(exchange)

    def close(self):
        self._reader.close()
