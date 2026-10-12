# Positive authority contract: local review candidate only

This source repair grants no gameplay permission. The adopted Receiving V2 source,
qualification, run 38278111122 and artifact 11759219913 remain frozen. No new
workflow or game identity is selected here. G08 stays incomplete; B01 cannot replay.

## Separate external verification boundary

`scripts/play-first/gameplay_authority.py` is a read-only external gate that must
run from a separately reviewed, trusted source/launcher/host. Neither a supplied
comment ID, case hash, executionAuthorized boolean, environment variable nor
generated record authenticates itself. `issue` obtains live GitHub facts; `verify`
requires a record but independently obtains those facts again. There is no offline,
fixture, alternate-host, credential override or environment-based authentication
mode in the CLI. Transport uses the fixed GitHub HTTPS endpoint, normal certificate
verification, and refuses redirects. API errors and incomplete pagination fail closed.

An eventual owner decision must begin `ARGENTUM_GAMEPLAY_AUTHORITY_V1` followed by
a newline and exactly one strict JSON object. Its required fields are `contract`,
`decisionId`, `issuerId`, `issuerLogin`, `caseSha256`, `blockId`, `gameId`, `scope`,
`executionAuthorized`, `originalOnly`, `maxGames`, `run`, and `expiresAt`.
The source defines the exact schema; duplicate keys and nonfinite values are refused.
No actual positive decision or executable case is provided in this candidate.

The gate verifies the actual repository owner and actual decision issuer against
the established GodaPupa account (326515697), the decision's controlling PR and URL,
complete live decision contents, the exact approved case bytes, source/validation
commit trees and sole parents, workflow blob, and the actual GitHub original push
run. The `run` object binds every field in `RUN_KEYS`, including an exact run ID,
run number 1, attempt 1, ref and workflow path. Execution also verifies the actual
clean local Git checkout and requires the approved run to be in progress.

All issue comments, inline review comments and review submissions on PR #231 are
searched with bounded complete pagination. Any subsequent owner discussion,
including an unstructured revocation or consumed-case notice, invalidates the
decision conservatively. A fresh, explicit owner disposition is then required.
This avoids pretending that an incomplete word/ID denylist can detect revocations.
The owner-specified expiry and a 60-second record age must also be satisfied.

The emitted record contains the exact live claims, decision-body SHA-256, official
decision URL, observed update time and owner-tail identity, verification time and
transport origin. These are provenance and correspondence fields, not signatures.
Record fields are compared with fresh independently fetched facts on each use.
The test-only injected transport is marked
`SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED`; it is not selectable by CLI and Kotlin
refuses that origin. No signing key or issuer attestation is invented.

## Matching consumers

The Python strict receiver requires `gameplayApproval` in the separately supplied
pins, invokes the same gate, and checks the adapter's `authorityContract` and
`authorityDecisionBodySha256` trace fields. Successful receiving still reports
`PINNED_RECORD_CORRESPONDENCE_ONLY`, `gameplayAuthorized: false`,
`authenticatedOrigin: false`, and `engineReplayed: false`. Owner-decision
correspondence does not authenticate engine execution, decks, pilots, provenance,
outcome equivalence or formal experiment admission.

Kotlin requires `PLAY_FIRST_GAMEPLAY_AUTHORITY_RECORD` and
`PLAY_FIRST_GAMEPLAY_RUN_CONTEXT` as claim-file locations, compares platform run
context, and invokes the fixed separate gate using isolated Python before creating
an original slot, journal, registry, environment or game. Gate timeout or refusal
fails closed. Case bytes are checked again after verification. The original
exclusive trace slot and supervisor behavior are retained. The sourceParent emitted
by this direct successor is 7b2b3afc84bdea530bfd69e0fc2691a4c837ca23.

## Unqualified and future owner-supplied requirements

The owner must separately supply a real positive gameplay decision, exact approved
case bytes and digest, new reviewed source identity, game identity, approved
validation/workflow/ref identities, exact original run ID, expiry and scoped
publication/activation permission. None is allocated or authorized here.

The gate authenticates remote owner facts under the trusted-host/TLS assumption.
It does **not** prove that a local process is the named GitHub job, validate compiled
JVM bytecode provenance, provide a trusted distributed single-use ledger, or exclude
copies across hosts/slots. Environment correspondence and public run lookup are
not runner attestation. A future independently reviewed trusted launcher must
enforce runtime provenance and single original consumption outside untrusted case
inputs; its concrete owner-supplied integration remains absent and unqualified.
The existing CREATE_NEW slot prevents a duplicate in that trusted local slot only.
Discussion/decision verification also has a finite observation window, not a
transactional guarantee against a revocation after the last read. Live transport,
revocation races, actual launcher integration and gameplay are not qualified by
synthetic tests. Kotlin compilation is not claimed when tooling/modules are absent.

Independent Reviewer B must assess this exact successor and its retained evidence.
No author-side check constitutes independent acceptance or controller adoption.
