# Pest excluded pin issuer / collector correspondence contract v1

Authority: PR #216 adoption 6059673055 of independent receipt 6059653099.
Parent: 94559a1c987dd096bd50a24be29d3e05e84f3a36. This bounded additive
component changes no accepted fixture, checkout, submission, verifier, deck,
vector, claim-helper or workflow bytes. It must stop at fresh independent review.

## What this contract supplies

The accepted consumer verifies transcript consistency using caller-supplied pins.
This successor makes that caller boundary explicit: a typed immutable comparison
anchor independently names the excluded issuer/receipt and collector/collection
and pins BOTH canonical receipt digests. Neither receipt may select its own anchor.
The caller remains responsible for acquiring and accepting those values separately.

Issuance contains an exact excluded purpose/realm, issuer and receipt IDs, accepted
consumer head and SHA-256, all five producer/evidence comparison pins, complete
manifest digest and all-null official bindings. The collector record must match
that issuance digest/identity, profile digest, manifest digest and exact 126-file
count; provenance_authenticated, historical_execution_proven and execution_authorized
must all be false. Exact schema and canonical-byte comparison reject extra fields,
Boolean/integer substitutions, duplicate keys and scope escalation. Consumer
on-disk bytes must match the accepted verifier digest before it is called.

Only then does the wrapper invoke the unchanged semantic verifier on the existing
fixture directory. Its returned manifest must also match the issued/collected
manifest. No producer is invoked and no file is written by the verification path.
The distinct issuer/collector labels are schema roles, NOT proof of distinct people,
independence or authenticated channels. Missing trust input cannot be reconstructed
from a successful transcript or its metadata. No network trust discovery exists.

## Explicit non-authority

FixtureTrustAnchor is a caller-selected comparison record, not a credential or
root of trust established by this code. Fixture receipts use EXCLUDED_* identifiers
and do not enroll any real issuer, collector, reviewer or controller. No signatures,
keys, entropy, remote transport, timestamps/freshness, revocation or receipt-chain
authentication is implemented. Unknown signature/authority fields are refused.
A fully coherent relabelled receipt pair with substituted caller pins can pass;
qualification deliberately demonstrates that its result still asserts no provenance,
historical execution or permission. Byte-identical reconstruction is not detectable.

The returned immutable object is an ordinary Python result, not tamper-proof
permission. Official loading remains unconditional refusal even after success.
Trusted local OS/interpreter and disk reads retain all inherited point-in-time
limitations. Checking verifier file bytes does not attest already loaded code.
No actual source remeasurement, fsync chronology, remote/physical durability,
real engine submission, historical event or game outcome is established.

## Qualification

Use excluded deterministic receipt documents and copies of the exact accepted
126-file submission evidence. No fixture Git commits, producer replay or official
seed allocation is needed. The positive case must preserve all input bytes.

```sh
PEST_SEALED_SOURCE_EVIDENCE=/absolute/original/fixture-evidence PEST_PIN_TEST_OUTPUT=/absolute/new/pin-fixtures PYTHONDONTWRITEBYTECODE=1 python3 scripts/experiments/pest-control/test_monster_tron_replication_pin_contract.py
```

Cases include issuer/receipt/collector/collection or external digest mismatch,
stale collection, missing anchor/pins, same role labels, consumer identity/disk
drift, authority/signature/official-binding escalation, wrong count/profile/manifest,
source mismatch, incomplete evidence and semantically forged rehashed evidence.
A positive coherent-relabel case demonstrates the non-authentication limit.

All fixtures/logs are preserved outside source. First failure must retain exact
bytes before a justified successor; never rerun unchanged failed source. The
accepted 27-test consumer suite is additionally exercised on fresh excluded copies;
older 93-test producer/checkout records remain historical evidence, not new runs.
A separate invocation from the frozen checkout verifies the exact fixed receipt
pair against the original accepted input, without writing/replaying that input.

## Remaining project inputs and gates

Actual issuer/collector identity enrollment, an authenticated way to obtain the
external pins, authorized purpose/scope policy and freshness/revocation semantics
remain explicit project-input/review gaps. This contract supplies their shape and
fail-closed correspondence checks, not their resolution. Official exclusions,
source/activation, runtime integration, remote evidence preservation, rules/legal
freshness and fresh execution authority remain separately blocking. The October 12
boundary is not extended. Accepted W/W/L/W smoke remains separate 3-1 prior evidence
with permanently consumed dispatch/claim authority. No retries or replacements.

NO ENTROPY, OFFICIAL SEEDS/VECTORS, CLAIM, MARKER, ACTIVATION, DISPATCH,
INITIALIZATION, GAMEPLAY OR OUTCOME. Freeze and request Reviewer A; do not self-adopt.
