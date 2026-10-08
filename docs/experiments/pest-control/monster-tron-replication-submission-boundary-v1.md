# Pest seed-free submission boundary v1

Construction authority: PR #216 adoption 6051678250 of canonical independent review
6051675337 (12591 bytes, SHA-256 56e250a749dd98a4a90ab7416a4ae14163cee7168c3c7c81852ee6222679f83f).
Parent 4b43db4dfe5e09d7a43d40080fcd1b48f05cc4b9 remains frozen. This additive
three-file candidate changes none of the accepted fixture, checkout, smoke,
workflow, claim-helper, deck or frozen-vector bytes. Fresh independent review is
required before adopting this construction. No execution authority is requested.

## Purpose and exact boundary

The accepted coordinator permits callers to emit excluded evidence tokens. This
successor supplies a narrower fixture submission interface that orders a built-in
deterministic transformation between durable INTENT and durable raw-result/RESULT
records. It is not a game adapter. It imports no MTG engine and provides no external
engine/callback injection API. Its evaluator merely hashes a fixture label, token
and sequence; it returns outcome=null and no_game_played=true.

A canonical, digest-bound fixture profile supplies exact source head/tree, three
component SHA-256s, fixture-input digest, attempt=1, twelve slots, two fixture tokens
per slot, the excluded claim label and all-null official bindings. The two-token
limit is a synthetic qualification budget, not an official game action limit.
The accepted fixture/checkout component hashes are fixed. The new component hash
is supplied prospectively in the profile. Full-checkout measurement validates the
profile head/tree, while component bytes in the checkout and the modules' on-disk
paths must match the profile. No pins grant authority or attest already loaded
code memory. The wrapper delegates official loading to unconditional refusal.

The only ACK is VALIDATE_EXCLUDED_SUBMISSION_BOUNDARY_ONLY. Validation-only ACKs
for other surfaces do not authorize this interface or official execution. The
profile and inherited fixture inputs/receipt must pass before evidence creation.
The profile itself is durably written into the inherited evidence inventory.

## Ordered fixture submission

For each of twelve fixed slots, begin_slot records ATTEMPT, INITIALIZATION_ENTRY,
and FIXTURE_INITIALIZED tokens. Those markers do not initialize anything.
submit_token accepts the exact next excluded token, writes/forces INTENT, performs
the deterministic token transformation, writes/forces fixture-raw-SS-NN.json,
and only then records/forces RESULT. It returns a raw-byte digest, never a winner.
Two matched token submissions permit end_slot. Twelve complete slots permit finish,
which invokes accepted checkout remeasurement and complete evidence verification.
Profile, checkout measurement and all raw fixture results are checksum-covered.

Any ordering error or operation exception poisons the boundary. INTENT write
failure prevents transformation. Transformation or raw-write failure leaves the
available intent prefix unresolved. RESULT failure retains available raw evidence
and unresolved intent. No public retry, resume, overwrite, replacement or recovery
path exists. Previously created evidence roots remain permanently refused. These
are program-level single-owner guarantees, not host security or crash-proof storage.

## Qualification

Use fresh deterministic output directories for each suite, preserve every log,
exit and injected-failure prefix, and never rerun an unchanged failed source:

```sh
PEST_CHECKOUT_TEST_OUTPUT=/absolute/new/submission-fixtures PYTHONDONTWRITEBYTECODE=1 python3 scripts/experiments/pest-control/test_monster_tron_replication_submission.py
```

Run the accepted 41-test fixture and 29-test checkout suites in separate fresh
output directories as regression qualification. The new suite copies the exact
three component files into excluded deterministic local Git fixtures. Test commits
use fixed author/date/content and are not official refs or allocated seeds.

New tests exercise profile/source/component/receipt/count/order/cell/ACK refusal;
durable intent-before-transform and raw-before-result ordering; injected intent,
transform, raw, RESULT and profile failures; token budget/sequence guards; corrupt
profile/raw records; source drift; incomplete blocks; and no retry/resume/extra slot.
No game is played. Fault injection does not prove physical or external durability.
A separate frozen-candidate invocation should bind actual source head/tree and
component digests while recording twelve excluded slots and twenty-four token
transformations. This is component evidence, not runtime replication qualification.

## Trust and remaining gates

Expected profile/identity digests must come from a separately reviewed receipt.
This component does not verify who supplied them. Trusted local Git/OS/interpreter,
single owner, point-in-time checks and callable Python internals retain the accepted
limits. On-disk module equality is not loaded-memory attestation. The wrapper does
not prevent direct use of the inherited fixture APIs and is not a security sandbox.
No actual engine submission, game/event semantics, physical durability, remote
preservation or always-upload behavior is established. Official source/loader,
exclusions, entropy/quarantine, vector freeze, claim reservation, legal freshness,
budgets and activation still require independent reviewed successors and authority.

Accepted smoke 37676510705 W/W/L/W remains separate prior 3-1 evidence, with consumed
dispatch and claim authorities. Historical failures stay preserved. The October 12
freshness boundary is not extended. Stop at independent Reviewer A review:
NO ENTROPY, SEEDS, OFFICIAL VECTOR FREEZE, CLAIM, DISPATCH OR GAMEPLAY.
