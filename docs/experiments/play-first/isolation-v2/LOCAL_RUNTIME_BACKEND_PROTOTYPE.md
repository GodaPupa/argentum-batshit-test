# Local runtime backend prototype — no operational authorization

This standalone successor adds executable local infrastructure and excluded tests.
It does not integrate or enable the Kotlin adapter, change gameplay authority or
receiving, create a workflow/hold, or modify original qualification evidence.
The adapter and shared V2 contract retain their predecessor source correspondence.
The successor adapter has **not** been source-bound for execution. No prototype
method launches an engine, creates a game trace, selects a case, or allocates a seed.

## Exact trust boundary

`IdentityBoundary` defines separately installed CODE methods that obtain provider
run/head/ref/source identities, authenticate actual owner decisions, measure host
and code identity, retrieve complete decision VERSION history, and authenticate
immutable execution-time provenance. Caller JSON, comment IDs, environment flags,
record hashes, timestamps, ledger rows and asserted `authenticated` fields do not
install or authenticate this boundary. The default is `UnavailableBoundary` and
DENY. No production implementation, signer, credential or provider integration is
present. No public CLI or environment option installs one.

The only injectable implementation is an underscored excluded-test fixture seam.
It accepts only the explicit origin
`SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED`. Labels identifying synthetic owner,
provider, host and time inputs **never become production authentication**. Public
validation helpers check contract correspondence; they do not establish trust.

## Engine-free state machine

| State | Permitted next states |
|---|---|
| INTENT | OWNER_VERIFIED, CANCELLED, FAILED |
| OWNER_VERIFIED | HOLD, CANCELLED, FAILED |
| HOLD | RELEASE_VERIFIED, CANCELLED, FAILED |
| RELEASE_VERIFIED | RESERVED, CANCELLED, FAILED |
| RESERVED | CONSUMED, RECOVERY_REQUIRED |
| CONSUMED | none |
| CANCELLED | none |
| FAILED | none |
| RECOVERY_REQUIRED | none |

Intent is immutable before GitHub assigns comment or run IDs. Owner verification
uses separately retrieved actual issuer/content. Hold checks the provider-assigned
run, exact head/ref/source/workflow, clean host/code and explicit engine/slot absence.
Release rechecks owner, run/host correspondence, decision ordering, completeness and
freshness. Reservation and consumption each reauthenticate release facts inside
the local transaction. Pre-execution revocation, expiry, substitution, missing
identity and unexpected ordering deny. These are excluded logical ledger states,
not original gameplay slots. `engineAllowed` remains false even after CONSUMED.

Missing or out-of-order owner/hold/release verification is quarantined in FAILED.
Reservation before release is denied while preserving the pre-release state.
Ambiguous committed reservation cancellation, interrupted recovery or failed
consumption moves to RECOVERY_REQUIRED; no transition leaves it. A consumed state
cannot be erased by cancellation. No automatic lease expiry, rollback of a committed
reservation, retry of an original, or database-based recovery disposition exists.

## Actual local transactional guarantees

`LocalLedger` uses genuine SQLite transactions with `BEGIN IMMEDIATE`, WAL journaling,
`synchronous=FULL`, a UNIQUE canonical scope/block/game key and UNIQUE reservation ID.
Competing OS processes opening **the same file on a supported local filesystem**
serialize writes. A different request cannot reserve the same logical original,
including reencoded bytes, changed workflow/ref or changed source identity. This
key is an excluded database reservation, not a gameplay trace slot or allocation.
Same request plus exact bytes is idempotent;
same request with altered bytes denies. Duplicate committed reservation/consumption
responses produce no additional audit transition or external effect.

Uncommitted writes and audit entries roll back on exceptions and actual process
death. Committed reservations survive process death; ambiguous recovery quarantines
them. Audit sequence numbers establish local transaction order only. The tests use
eight genuinely concurrent spawned processes for duplicate/distinct requests,
reservation races and consumption, plus a cancellation/consumption race and real
`os._exit(88)` before/after commit. There is no gameplay or launch callback.

Guarantees exclude malicious hosts/database tampering, copied/deleted databases,
unsafe network filesystems, cross-host transactional enforcement, power-loss/filesystem
semantics not provided by SQLite/storage, and production launch integration. A local
hash chain, mutable database and local UTC clock are **not independently trusted time**.

## Execution-time correspondence

`inspect_historical` accepts an opaque external anchor reference. A separately
trusted backend must independently authenticate immutable facts, actual execution
time, decision versions, complete ordered history through execution, source/run,
artifact manifest, and one original consumption. Integrity digests detect alteration
relative to the authenticated anchor; they cannot authenticate their own authority.
Missing immutable/time references, incomplete or inconsistent history, altered
provenance, pre-execution revocation, cross-bound runs/manifests and replay deny.

Historical receiving validates owner/release validity and ordering at the anchor's
independently established execution time. It never reads the current clock or
requires fresh execution permission. Later expiry or comments do not erase the
original. Repeat inspection of the same immutable original is read-only. Success
in the tests is synthetic record correspondence only, never proof of engine
correctness, real gameplay authority, or admission to formal experiments.

## Remaining independently reviewed production work

1. Owner-provided actual immutable intent and positive staged gameplay decisions;
   no such future decisions or selected case have been created here.
2. A supported provider hold/identity implementation and live complete authority
   verification, with actual assigned IDs and no pre-release engine/slot creation.
3. Independent host/code attestation, trusted immutable execution-time evidence and
   authenticated complete decision-version history; no signer/time service exists.
4. Cross-host authoritative single-use service, production concurrency/failure
   semantics, launcher integration and separately authorized recovery dispositions.
5. New source-bound adapter integration and its compilation/qualification/review.
   The original adapter remains unchanged and unintegrated with this prototype.
6. Independent Reviewer B review, separate controller adoption and any later
   qualification/publication/gameplay authority. This packet establishes none.

Accepted Receiving V2 source, qualification run 38278111122/artifact 11759219913,
Monster Tron 7 wins, Pest Control 2 wins, G08 incomplete and formal counters remain
unchanged. No remote writes or accepted qualification reruns occur in these tests.
