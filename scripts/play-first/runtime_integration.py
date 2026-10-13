"""Bounded local glue for existing authority, SQLite staging and receiving.

No production backend is registered. The only injected CODE seam is explicitly
unauthenticated synthetic infrastructure. Neither documents nor environment
variables can select it. Every integrated result has engineAllowed=False: there
is no launcher, game trace, real hold, signer or cross-host production ledger.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import sys

# -I removes even the script directory. Resolve only these frozen co-located
# source modules, never an environment-supplied verifier/backend or PYTHONPATH.
if sys.flags.isolated:
    sys.path.insert(0, str(Path(__file__).resolve().parent))

import gameplay_authority as authority
import staged_runtime as runtime

INTEGRATION_CONTRACT = 'ARGENTUM_LOCAL_RUNTIME_INTEGRATION_V1'


class RuntimeIntegration:
    """Compose adopted interfaces without upgrading their trust assumptions."""
    def __init__(self, ledger, *, _synthetic_fixture_backend=None):
        self.coordinator = runtime.StagedRuntime(ledger, _synthetic_fixture_backend=_synthetic_fixture_backend)
        self.ledger = ledger

    def _bounded(self, row):
        result = dict(row)
        result.update(integrationContract=INTEGRATION_CONTRACT, engineAllowed=False,
                      productionQualified=False, authenticatedProductionOrigin=False,
                      localLedgerAuthenticatesTime=False)
        return result

    def freeze(self, request, intent_bytes):
        return self._bounded(self.ledger.begin(request, intent_bytes))

    def owner(self, request, reference):
        return self._bounded(self.coordinator.owner_verified(request, reference))

    def hold(self, request, reference):
        return self._bounded(self.coordinator.hold(request, reference))

    def release(self, request, reference):
        return self._bounded(self.coordinator.release(request, reference))

    def release_correspondence(self, request, intent_bytes, run, record):
        """Fresh correspondence only, never operational engine permission.

        Reuses the existing staged validator and positive decision contracts.
        The record is just a reference; independently fetched CODE-fixture facts
        and the durable staged intent must agree. A real production backend is
        still unavailable, not installed by this object or any supplied field.
        """
        at = self.coordinator._trusted()
        frozen = authority.freeze_intent(intent_bytes)
        authority.check_run(frozen['intent'], run)
        runtime.strict_keys(record, ('contract', 'intentSha256', 'ownerDecisionId',
                                    'releaseDecisionId', 'holdRef'), 'integrated release reference')
        runtime.require(record['contract'] == authority.CONTRACT and
                        record['intentSha256'] == frozen['intentSha256'], 'release intent correspondence')
        row = self.ledger.inspect(request)
        runtime.require(row['state'] == 'RELEASE_VERIFIED' and bytes(row['intent']) == intent_bytes and
                        row['intent_hash'] == frozen['intentSha256'], 'interrupted/replayed/substituted staged intent')
        evidence = row['evidence']
        boundary = self.coordinator.boundary
        held = boundary.authenticate_hold(record['holdRef'])
        runtime.require(authority.same_json(held, evidence['hold']), 'hold reference cross-bound to another original')
        released = boundary.authenticate_release(evidence['releaseReference'])
        observed_run = runtime.validate_release(released, frozen, evidence['owner'], held, at)
        runtime.require(authority.same_json(observed_run, run) and
                        type(record['ownerDecisionId']) is int and
                        type(record['releaseDecisionId']) is int and
                        record['ownerDecisionId'] == released['owner']['id'] and
                        record['releaseDecisionId'] == released['release']['id'], 'cross-bound owner/release/run identity')
        _, owner_hash = authority.decision_claims(released['owner'], record['ownerDecisionId'],
                authority.OWNER_CONTRACT, authority.owner_expected(frozen['intentSha256']), at)
        _, release_hash = authority.decision_claims(released['release'], record['releaseDecisionId'],
                authority.RELEASE_CONTRACT, authority.release_expected(frozen['intentSha256'], record['ownerDecisionId'], run), at)
        proof = authority.proof(frozen, run, record['ownerDecisionId'], record['releaseDecisionId'],
                owner_hash, release_hash, runtime.SYNTHETIC, 'LOCAL_INTEGRATED_RELEASE_CORRESPONDENCE_ONLY')
        return self._bounded(proof)

    def reserve(self, request, reservation, *, _before_commit=None):
        return self._bounded(self.coordinator.reserve(request, reservation, _before_commit=_before_commit))

    def consume(self, request, reservation):
        return self._bounded(self.coordinator.consume(request, reservation))

    def cancel(self, request):
        return self.ledger.cancel(request)

    def quarantine_interrupted(self, request):
        return self.ledger.recover_interrupted(request)

    def inspect_historical(self, intent_bytes, run, record, manifest_sha256):
        # This independently authenticated fixture envelope is NOT built from
        # ledger audit times, runner claims, current expiry or supplied snapshots.
        proof = runtime.inspect_historical(intent_bytes, run, record, manifest_sha256,
                    _synthetic_fixture_backend=self.coordinator.boundary)
        return self._bounded(proof)


def verify_historical(intent_bytes, run, record, manifest_sha256, *, _anchor=None, _integration=None):
    """Receiving dispatch; production default remains the unavailable boundary.

    The old underscored normalized-anchor seam is retained for adopted excluded
    regressions. Neither seam is exposed by CLI, variables or artifact contents.
    New integrated tests use the stronger existing staged envelope normalization.
    """
    runtime.require(not (_anchor is not None and _integration is not None), 'ambiguous historical trust interfaces')
    if _integration is not None:
        runtime.require(type(_integration) is RuntimeIntegration, 'unqualified integration object')
        return _integration.inspect_historical(intent_bytes, run, record, manifest_sha256)
    return authority.verify_historical(intent_bytes, run, record, manifest_sha256, _anchor=_anchor)


def main(argv=None):
    parser = argparse.ArgumentParser(description='Fail-closed integrated authority boundary; no production backend')
    parser.add_argument('operation', choices=('verify-release', 'verify-historical'))
    parser.add_argument('--case', type=Path, required=True)
    parser.add_argument('--run-context', type=Path, required=True)
    parser.add_argument('--record', type=Path, required=True)
    parser.add_argument('--manifest-sha256')
    parser.parse_args(argv)
    # No backend-selection flag, JSON/env registration, ledger construction or
    # launch callback. Fixture infrastructure can NEVER make this CLI succeed.
    print(json.dumps(dict(status='REFUSED', integrationContract=INTEGRATION_CONTRACT,
          engineAllowed=False, productionQualified=False,
          errors=['UNQUALIFIED: authenticated production provider/owner/host/time/launcher and cross-host reservation unavailable']), sort_keys=True))
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
