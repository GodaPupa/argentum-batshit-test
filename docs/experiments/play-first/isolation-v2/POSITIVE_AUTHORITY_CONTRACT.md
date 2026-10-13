# Positive authority V2: staged release and historical correspondence

This local source implements the prior contracts and local integration decision
6135339059 on PR #231. It is
an excluded contract candidate for independent Reviewer B review. It is not a
qualified launcher, executable gameplay decision, controller adoption, or proof
of engine correctness. Its sole parent is
e79adf5770a803568a81be73a83fa8627c48c862; the local integration successor emits that sourceParent.
Accepted source 7b2b3afc84bdea530bfd69e0fc2691a4c837ca23 and original qualification
run 38278111122 / artifact 11759219913 remain unchanged.

## Trust roots and production refusal

A comment ID, digest, approval JSON, environment variable, ACK, runner claim,
or artifact cannot authenticate itself. The fixed TLS GitHub reader obtains
actual repository-owner facts, PR #231 comments, issuer ID/login/OWNER
association, actual contents, complete controlling discussion, Git parents and
trees, workflow blob and original run metadata. It follows no redirects and has
no record-selected host, credential or CLI fixture mode.

GitHub metadata alone cannot authenticate the executing host, an engine-free
hold, atomic cross-host original consumption or actual execution time.
Production release and historical-anchor backends are deliberately ABSENT.
The CLI supplies neither backend. The adapter's fixed verify-release subprocess
cannot succeed in this candidate. Files and environment cannot install a
backend. Historical receiving without independently authenticated provenance
returns INCOMPLETE with an explicit UNQUALIFIED error even if supplied hashes
and approval claims match.

The underscored trusted-code injection seams are exclusively excluded tests.
Their service facts and transport are labeled
SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED. They are not genuine owner approval,
TLS authentication, certified hosts, trusted timestamps or a cross-host ledger.
Deserialized JSON, an arbitrary supplied object or a record-selected module
cannot be deployed as a trusted backend. The adapter refuses synthetic origin.

## Four separately bounded stages

1. Immutable prelaunch intent. Freeze the exact bytes of an envelope containing
   exactly contract, case and plan. Contract is ARGENTUM_GAMEPLAY_INTENT_V2.
   Case contains blockId, gameId, pair, pestSeat, fixtureSeedHex, sourceCommit,
   scope and intendedBlockGames. Plan contains sourceCommit, sourceParent,
   sourceTree, validationCommit, validationTree, workflowPath, workflowBlob,
   ref, push event, runNumber 1 and runAttempt 1. No future comment ID, runId,
   executionAuthorized boolean or execution timestamp is in these bytes.
   freeze_intent returns the byte SHA-256 and engineAllowed false; it allocates
   nothing and issues no authority.
2. Owner decision. After GitHub assigns a comment ID, verify_owner_decision
   retrieves the actual owner comment and contents through fixed TLS transport.
   Its body starts ARGENTUM_GAMEPLAY_OWNER_DECISION_V2 plus newline and strict
   JSON with contract, intentSha256, scope, originalOnly true, maxGames 1 and
   expiresAt. It contains neither its own ID nor a future run ID. The decision
   binds the immutable intent. Complete issue/inline/review discussion is
   searched, and exact source/workflow objects are checked without requesting
   any run. This stage cannot release an engine or create an original slot.
3. Non-gameplay hold. A future independently implemented and reviewed trusted
   launcher may obtain a provider-assigned run ID in an engine-free hold.
   Authenticated hold facts must bind intent digest, the complete run/head/ref/
   workflow identity, independently observed time, engineStarted false and
   originalSlotCreated false. JSON booleans are not host authentication. No
   workflow, hold service or run is created here. Every launcher path, including
   interruption and cancellation, must remain before any engine or slot.
4. Run-specific release. A separate owner release comment, created after the
   hold, starts ARGENTUM_GAMEPLAY_RUN_RELEASE_V2 plus newline and strict JSON:
   contract, intentSha256, already-assigned ownerDecisionId, complete run identity
   including now-assigned runId, scope, originalOnly true, maxGames 1, expiresAt.
   The body does not contain its own future release ID. verify_release requires
   an authenticated hold backend, fresh live owner/release contents, complete
   supersession checks, actual provider run/head/ref and exact source/workflow
   objects, original attempt/number, in-progress status and correct ordering.
   The trusted service must atomically reserve exactly one original, recheck
   authority and host/code, and prepare durable anchoring before returning.
   Missing, altered, stale, revoked, consumed, cross-bound, interrupted or
   incomplete stages fail closed. Earlier stages cannot enable initialization.

For existing evidence snapshot tooling, approved-case.json remains the filename;
its V2 contents are now the immutable intent envelope. caseSha256 and the
supervisor's PLAY_FIRST_ISOLATED_CASE_SHA256 pin those exact intent bytes.
The actual owner comment ID is added to trace/timing identity only AFTER trusted
verification, outside that digest. Trace stamps bind the V2 contract, owner
decision body SHA-256, run-release body SHA-256 and case-input SHA-256. Kotlin
delegates strict schema/plan checks to the same fixed Python gate before an
original slot, timing journal or engine call. B01, G08 and previously frozen
historical fixture labels remain rejected.

## Historical receiving at execution time

verify_historical has a different interface. It does not call the live release
gate, obtain now, demand a fresh inspection record, or require the old approval
to remain currently executable. The supplied historical record contains only
contract, intentSha256, ownerDecisionId, releaseDecisionId and anchorRef.
The reference is untrusted until a separately configured external service
authenticates it. A self-declared timestamp or unsigned snapshot is rejected.

authenticate_historical must independently retrieve immutable provenance for
the actual original execution, with these authenticated normative facts:

- Exact intent digest and complete source/run/workflow identity, with separately
  established source correspondence.
- Actual execution instant from a trusted host/provider mechanism, not case
  JSON, runner-generated time, approval claims or the inspected artifact.
- Actual owner and release decision VERSIONS valid at execution, including
  independently authenticated issuer and complete contents.
- Complete versioned issue/inline/review history through execution, including
  edits, deletions and revocations hidden by today's mutable snapshots.
  Incomplete history or contradictory same-comment versions before execution
  fail closed. Today's TLS read alone cannot reconstruct that history.
- Authenticated engine-free hold and staged ordering, expiry and release
  freshness AT EXECUTION, no pre-execution revocation, supersession or prior
  consumption. During the authorization interval, unrelated owner events
  conservatively close authority; authenticated later events are excluded.
- Original single-use consumption bound to intent, run and trusted instant,
  exactly one original, and original payload manifest SHA-256. The manifest
  pins complete child logs, process, trace and timing payloads.

Subsequent expiry, non-revoking comments, consumption receipts or revocation of
future execution do not erase a proven original. Re-inspection of the same
original is read-only and idempotent. Substituted intent/run/manifest or replayed
consumption cannot reuse its anchor. Missing/forged provenance, self-declared
time, incomplete attestation and pre-execution revocation fail closed.

The backend facts are normative because of independently reviewed
authentication, not because a dictionary says sourceVerified true. No
operational implementation of that authentication exists here. No signer, key,
future gameplay decision, workflow, allocation or attestation was manufactured.

The receiver continues reporting PINNED_RECORD_CORRESPONDENCE_ONLY.
gameplayAuthorized, authenticatedOrigin and engineReplayed stay false.
Correspondence proves neither engine correctness nor official experimental
admission. Historical verification never grants another execution release.

## External implementation and independent review gates

The owner must separately supply a frozen reviewed intent/source/workflow and
actual positive owner decision. The current repair authorization is not gameplay
authority. A launcher/hold service needs separate implementation and independent
review before a real hold exists. Trusted host/code attestation, live owner
verification, durable cross-host atomic single use, last-moment revocation
checks at reservation and immediately before initialization, and execution-time
anchoring remain UNQUALIFIED. Reservation alone cannot attest that execution
occurred: record the actual launch instant independently and bind the subsequent
frozen manifest through authenticated provider evidence. Interrupted
reservations must never become reusable originals or false execution anchors.

The external service must establish authenticated reference retrieval, immutable
version retention, edit/deletion history, host and artifact/provider origin,
trusted time, revocation race handling and cross-host exclusion. No supplied
URL, module, credential, signer, key or artifact may define that trust root.
Deployment and any real hold/engine activation need distinct exact controller
authority and independent review.

Kotlin test-source compilation is OPEN: just, kotlinc and full materialized build
modules are unavailable in this identity-only checkout. Static parity checks and
frozen unrelated engine-body comparisons are not compilation or operational
qualification. The Python contract fixtures qualify only excluded parsing,
correspondence and fail-closed behavior.

## Excluded reproduction and preservation

Run these five Python unittest modules in normal, -O and -OO modes:
test_isolated_case, test_isolated_receiving, test_authority_parity,
test_strict_receiving, test_gameplay_authority. Do not invoke the Kotlin adapter,
engine suite or accepted GitHub qualification. Tests use only reused fabricated
labels, synthetic records and tiny excluded process-supervision children.

The packet preserves c025 and its complete original review ZIP, baseline RED for
both findings, every failed corrective attempt with source-at-failure, and final
per-mode GREEN logs and exact test IDs. Positive fixture success is not genuine
GitHub provenance. Independent Reviewer B must review the frozen source, both
baseline findings, staged/historical contracts and unresolved external services.
The implementing worker does not self-review, accept or adopt this candidate.

Record unchanged: Monster Tron 7 wins, Pest Control 2 wins, G08 incomplete;
formal counters unchanged. No publication or gameplay authorization is implied.


## Bounded local integration successor

`runtime_integration.py` composes the existing authority validators, engine-free
`StagedRuntime`, and real single-file `LocalLedger`. The immutable stored intent,
independently fetched synthetic owner/hold/release facts, and provider/source/run
identities must agree before a release correspondence result. Reservation and
consumption delegate to the existing transactional coordinator, which revalidates
facts and retains cancelled, consumed or ambiguous keys. These are excluded local
database states, never gameplay allocations or original trace slots.

The receiver dispatches an explicitly injected integration object through the
existing staged historical-envelope verifier. Its immutable/time references and
complete ordered decision-version history are fetched from the separately
supplied CODE fixture, not inferred from SQLite audit timestamps, case JSON or
runner claims. Later expiry and comments do not erase at-execution correspondence.
The old normalized-anchor underscored seam is retained for adopted synthetic
regressions. Mixing these seams is rejected. No seam is exposed by CLI or JSON.

The adapter calls the fixed integration CLI under isolated Python and additionally
requires `productionQualified` before any journal, trace or engine initialization.
That CLI has no production backend registration and always returns DENY. No file,
environment variable, origin string or synthetic fixture can make it succeed.
The isolated import bootstrap resolves only frozen co-located source modules.
Every local integrated result has `engineAllowed: false`, `productionQualified:
false`, and no authenticated production-origin or independently trusted time claim.

The preceding Lane B prototype remains the implementation of local transactions;
this glue does not upgrade its synthetic identity interfaces, local clock or local
storage into production trust. Future live owner/provider authentication, trusted
host/code hold, cross-host atomic enforcement, durable externally authenticated
execution-time anchors, launcher integration and independent review remain separate
gates. Workstream B feasibility materials are not automatically integrated.
