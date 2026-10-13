# Focused nonofficial admission — non-executing local review candidate

Controller 6137361282 explicitly accepts a reduced-assurance, one-owner-host model
for a future separately authorized nonofficial exploratory game. It authorizes
this local interface and excluded tests, not a game, launcher, seed, workflow,
credential or operational deployment. Acceptance 6137278797 remains limited to
its exact prior scope. The baseline is f98d850f3883493de5abf303da0e68c8902d16c6,
tree 0f048ad1101474f152a2edc63062392de12cfcf7, parent
e79adf5770a803568a81be73a83fa8627c48c862. This successor has that baseline as its
sole parent. The original ZIP ea389083b72cf94fdd6f44c72623fee25ef5abccbb4702baf130635e9f906f2b
and accepted Receiving V2 run 38278111122 / artifact 11759219913 are preserved.

## Implemented boundary

`focused_nonofficial_admission.py` adds a separate non-executing protocol. Existing
authority, receiver, integrated production entry point, Kotlin adapter, engine,
deck, pilot and supervision source remain byte-identical. No existing production
DENY is weakened. Every result has engineAllowed=false, gameplayAuthorized=false
and productionQualified=false; there is no engine callback or initializer. The
public CLI inspects immutable intent bytes or reports disabled activation. A JSON
flag, environment variable, comment ID or document cannot install a backend.

The module reuses `gameplay_authority` strict JSON/schema/positive issuer/content,
expiry and current-history helpers, plus `staged_runtime.LocalLedger` WAL/FULL,
BEGIN IMMEDIATE, original-key uniqueness, transaction and audit transitions.
Focused originals live in the **same attempts table and original-key namespace**
as the accepted prototype. A previously occupied logical scope/block/game cannot
be replayed by changing protocol, source, request, deck, pilot or serialization.
These tests write excluded prototype rows, never real gameplay slots or formal
allocations. Cancellation/refusal retain occupied keys, with no replacement.

## Immutable intent and noncircular decision sequence

Freeze exact bytes before any provider-assigned owner/release comment identifiers.
The focused intent contains exactly contract, case, identities and executor.
Case binds nonofficial scope, logical block/game, both seat assignments,
originalOnly=true and maxGames=1. Identities bind exact source commit/tree/sole
parent and SHA-256 manifests for engine artifacts/dependencies, both decks and
both pilots/configuration. Future owner-supplied manifests must enumerate all
relevant files and dependencies; accepting a digest proves correspondence to
those bytes, not semantic engine correctness. No seed or GitHub run is allocated
or required. Historical B01/G08 and official scopes are refused.

The focused sourceParent is the accepted integration commit f98d850f… because this
module is introduced in its direct successor. The unchanged Kotlin adapter and
legacy V2 intent retain their accepted identities; this module does not redirect
or activate that adapter. A future executable integration requires separate code
review and exact source binding. Synthetic source Git IDs are fixture labels,
not claims that such commits exist.

After the owner comment exists, read its actual provider ID, repository owner,
issuer, PR, complete current paginated conversation/reviews, body and updated
version through the fixed TLS GitHub client. The positive body prefix is
ARGENTUM_FOCUSED_OWNER_DECISION_V1, followed by strict JSON binding intentSha256,
executorId, ledgerBindingSha256, nonofficial scope, originalOnly, maxGames and
expiresAt. It contains no self comment ID or future run ID. Only an actual owner
decision can eventually authenticate authority; this local task creates none.
Current construction comments cannot satisfy this positive contract.

The hold follows that owner decision. Its owner-controlled record binds intent,
ledger registration, current local process/session and observed time, explicitly
engineStarted=false/originalSlotCreated=false. The final positive release is a
**later owner comment**, prefix ARGENTUM_FOCUSED_FINAL_RELEASE_V1. It binds the
same intent/executor/ledger plus now-known ownerDecisionId, requestId and
holdSha256, original-only scope and expiry. The hold digest is frozen before the
release is posted; no self-reference or not-yet-assigned provider ID is needed.

Every release/reservation/consumption rechecks issuer, exact original decision
version, complete current API view, expiry, fresh hold/release (60-second bound),
source identities, executor/ledger registration, and double-read correspondence.
All later owner comments are conservatively treated as supersession for a new
execution. This may refuse an innocuous comment and require a new disposition;
it never silently classifies unknown owner text as harmless authority.

The readback itself records LIVE_GITHUB_TLS_OWNER_FACTS only when the actual fixed
client is used. Injected CODE clients are always SYNTHETIC_TRUSTED_FIXTURE_NOT_AUTHENTICATED.
The overall prototype still refuses production and gameplay in either case.
There is no production executor verifier registered and none is exposed by CLI.

## One executor and exclusive persistent storage

The protocol requires POSIX and an explicitly injected executor inspector. In
this candidate only the labelled excluded fixture inspector is supported. No
actual host, storage volume, administrator or persistence evidence is qualified.
Default missing inspector refuses before creating any ledger.

Register executorId/storageId and canonical absolute ledger path, actual device
and inode. Require no ledger symlink, hardlink or noncanonical path; observe the
binding and declared configuration again at each stage. An adjacent NOFOLLOW
exclusive advisory flock permits one active local executor session per storage
root, across alternate database filenames. The shared root lock file retains an
fsync'd exact local executor/path/device/inode registration; a new empty database
cannot reset that registered root after a session closes. The registration does
not authenticate owner authority or physical storage: it is owner-controlled
coordination state, and deleting/copying the whole root remains outside the proof.
One actual designated root must be selected and qualified externally. Replaced
lock/ledger files, conflicting registrations and copied databases at other paths
are rejected in the demonstrated local model. Actual multiprocess tests exercise
contention. Flock is advisory and same-host: a malicious process can ignore it;
another host can lie about identities. No adversary-resistant or distributed
uniqueness is claimed.

Before future deployment the owner must separately identify the exact physical or
durable virtual POSIX host, operator, mount/volume and canonical ledger location;
qualify SQLite locking/WAL/fsync/FULL semantics and durability through actual
restarts; exclude ephemeral containers, disposable workspaces, shared/network
filesystems with unqualified locking, concurrent backup clones and parallel
executor copies; establish exclusive access/permissions and monitored recovery.
Only that host may admit the approved case. A current container fixture claiming
persistent=true is not this qualification. Backup/restore or lost consumption
state requires explicit disposition; it cannot create a replacement original.

## States, admission and crashes

Public sequence: INTENT_FROZEN → OWNER_APPROVED → ENGINE_FREE_HOLD →
FINAL_RELEASE_VERIFIED → ORIGINAL_CONSUMED. Internally the existing RELEASE_VERIFIED
and RESERVED states both map to FINAL_RELEASE_VERIFIED. Reservation is an explicit
durable audit event, never a game evidence slot or engine start. Consumption is a
local prototype database transition, not actual execution or admission.

Reserve and consume reauthenticate current owner/release facts inside the SQLite
write transaction. The last controllable prototype check precedes the consumption
commit. This code never initializes anything afterward. A future reviewed launcher
must place its final actual verification immediately before initialization and
prove its code ordering. Network readback, commit and physical initialization are
not one atomic event; no physical host attestation or elimination of that interval
is claimed. The owner must approve the narrower actual deployment assumptions.

Exact same immutable freeze/reservation/consumption requests return their existing
record where supported. Duplicate consumption has newConsumption=false and never
returns a launch capability. Contradictory requests/reservations are refused.
Intermediate stage calls are strict sequenced operations, not universally
idempotent launch APIs; out-of-order repetitions are denied. All terminals remain
occupied. Cancellation before reservation is CANCELLED; after reservation it is
QUARANTINED. Interruptions before reservation are REFUSED; after reservation they
are QUARANTINED. No automatic reset, lease expiry, retry or replacement exists.

New sessions cannot resume approval/hold/release/reservation: abandoned prelaunch
states become REFUSED and committed reservations become QUARANTINED. A committed
consumption remains consumed after process death. Actual os._exit before commit
shows rollback; after commit shows persistence and quarantine. Unknown IO/COMMIT
or caller return poisons the current session, refusing further admission even if
the outcome cannot be durably recorded. Original errors are preserved. Read-only
inspection and explicit refusal/quarantine observations do not authorize replay.
Remote read calls under the local transaction are bounded by the existing client
timeout/pagination limit; future service deadlines and operational locking still
need qualification. Tests use only in-memory fixture clients, no live hold.

## Original evidence and historical correspondence

Receipt generation is read-only. Preserve immutable intent bytes separately,
source/engine/deck/pilot/seat identities, owner decision/readback versions, hold,
release and final prelaunch readback, transaction audit/reservation, process start,
finish or interruption, cleanup, original raw logs and partial failures. Payloads
are bounded to128 regular files,1MiB each/8MiB total, with relative path/size/SHA256;
symlinks are refused. A prelaunch refusal requires its original verifier.log and
does not invent a child log. When process observations exist, preserve child.log
even on failure. No logs, artifacts or snapshots are overwritten or recreated.
Process observations are supplied owner-controlled observations, not evidence
that this prototype invoked a child/engine. No process initializer is implemented.

Historical inspection retrieves the separately archived original receipt and exact
intent bytes through an excluded CODE archive seam, compares all payload hashes,
and checks owner/release validity at the **owner-recorded process start**, sequence,
consumption and cleanup. It does not require permissions to remain executable
today. Post-record expiry/later comments do not retrospectively erase the captured
record. A mismatched archive, forged issuer, self-substituted timestamp, replay,
missing original log, invalid execution-time expiry/revocation or altered payload
is refused. A missing real archive verifier is unavailable/default DENY.

The result is OWNER_CONTROLLED_RECORD_CORRESPONDENCE_ONLY with permissionToLaunch,
gameplayAuthorized, authenticatedProductionOrigin, productionQualified and
independentExecutionTimeAttestation all false. The archive/time/history are
owner-controlled and may be mutable, not independent authentication. GitHub current
metadata cannot prove deleted/edited revocation completeness. This satisfies only
the proposed reduced-assurance record interface; it does **not** satisfy the prior
high-assurance historical receiver's independently authenticated execution-time
anchor requirement. That receiver and its DENY remain unchanged.

## Review and still-unqualified gates

Independent Reviewer B must review the exact successor/diff/manifest, preservation
of all baseline bytes and production DENY, genuine local concurrency/crash evidence,
positive issuer/content verification, noncircular release ordering, idempotency,
session poisoning/recovery and explicit limitations. Synthetic successes must not
be presented as real GitHub owner authority, physical execution or trusted time.

Before any new exploratory game: independent technical review; controller acceptance
of the exact source; actual owner identification and qualification of executor and
exclusive persistent storage; distinct case-specific owner approval naming source,
engine/decks/pilots/game/seats/scope and one original; a separately implemented and
reviewed launcher/activation path; actual live prelaunch checks and source-bound
hold/final release. No generic source approval licenses an unspecified game.
This candidate supplies no real decision, backend, host, run, seed, credential,
operational activation or independently trusted timestamp. Reduced assurance is
not formal experimental reproducibility, engine correctness or admission.

Monster Tron stays7 wins, Pest Control2 wins, G08 incomplete; formal counters
unchanged. No B01 replay, G08 continuation, official allocation/claim/marker,
Receiving V2 rerun, GitHub write, workflow, provisioning, self-review or adoption.
