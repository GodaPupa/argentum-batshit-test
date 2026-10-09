# Isolation receiving v2 — INACTIVE operational proposal

Construction authority: PR #231 comment 6086416012 and the owner's subsequent
Proceed. This is a successor proposal, not approval of another game or workflow.
Source parent: e11e9f2bdd5e33de5a8d2f8b3139b1f34a150241. Frozen v1/R2 branches,
B01, G08, previous qualifications, engine, decks and pilots remain unchanged.

## Exact bounded delta

- The adapter writes the already validated fixed-width seed label verbatim.
  Positive signed-int64 labels with leading zeros no longer lose those zeros.
  The source-parent label advances to this successor's exact parent. No engine input, random operation, policy, game budget or case schedule changes.
- Supervisor lifecycle is unchanged. PROCESS_INTENT, result and final log now
  preserve the supplied run/head/ref/source/case-digest binding and explicit
  cleanupUncertain/residualGroupKilled booleans. These are observations of caller
  inputs, not authentication of a GitHub actor or proof of clean host execution.
- Legacy v1 shape checks are retained for fixture compatibility but their asserts
  become unconditional checks. They are NOT the v2 operational receiving path.
- New strict_receiving.py verifies externally pinned case bytes, run-source bytes,
  full supervisor-manifest coverage and payload hashes; reconciles process status,
  integer return code, cleanup flags, identity, command, budget, cancellation and
  the complete process log; requires the exact expected timing-call sequence for
  the pinned adapter's recorded initializer, mulligan, bottoming, agent creation,
  selection/decision and submission operations. Missing, extra, changed or partial
  evidence returns INCOMPLETE. Positive status is PINNED_RECORD_CORRESPONDENCE_ONLY.
- verify_checkout verifies actual HEAD, tree, sole source parent, source tree,
  explicit addition-only delta, all added Git blob/mode identities and a clean
  checkout against approval pins supplied OUTSIDE the candidate workflow.

## Receiving inputs and trust boundary

Read-only input layout:

    evidence/approved-case.json
    evidence/run-source.json
    evidence/case/process.jsonl
    evidence/case/process-result.json
    evidence/case/original.jsonl
    evidence/case/timing.jsonl
    evidence/case/child.log
    evidence/case/supervisor-manifest.json

Additional case files must be fully inventoried and hash-matched. Reject symlinks,
nonregular files, duplicate JSON keys, nonfinite numbers, unsafe member paths,
partial JSONL and changing input snapshots. Limits: 256 MiB total input and 4096
snapshot files. These limits may conservatively refuse a large future original;
never rerun a game to fit a receiver limit. Snapshot checks are not hostile-writer
race protection, independent archival custody or a host-loss guarantee.

The separately supplied trusted-pins JSON must include caseSha256,
runSourceSha256, manifestSha256, command (explicit argv), budgetSeconds,
graceSeconds, pestMainSha256, monsterTronMainSha256, and a run object containing
runId (decimal string), runAttempt (1), runNumber, event (push), ref,
sourceCommit/sourceParent/sourceTree, validationCommit/validationTree, workflowPath/workflowBlob.
The run-source file must match that exact run object. Never derive trusted pins
from the same untrusted packet and describe the comparison as authentication.
A reviewer must independently fetch the original GitHub run/artifact, verify the
original outer ZIP/provider digest and exact source/activation, and retain the
pins from that receiving step. This module does not fetch GitHub, verify an outer
ZIP, perform engine replay, certify rule correctness, or enroll an authority.

## Exact future activation: required separately reviewed stages

1. A future source/fixture/workflow proposal must be separately authorized. This
   change selects no fixture or seed and creates no workflow under .github/workflows.
2. Freeze the future validation commit first, without triggering it. Obtain its
   exact commit, tree, sole source parent/tree and addition-only path/blob mapping.
   Review all those bytes, including the workflow and any added case documents.
3. Publish source-bound approval OUTSIDE that commit, specifying validationCommit,
   validationTree, sourceCommit, sourceTree, workflowPath and addedFiles mapping.
   The executing controller must reconcile that approval with live owner authority
   before any launch. A workflow cannot safely establish its own authorization.
   Do not hard-code its final SHA into its own contents: that is self-referential.
4. Before activation, a trusted external controller compares the exact ref/head
   and the full approved addition set. The runtime may additionally run:

       python3 scripts/play-first/strict_receiving.py activation \
         --root /path/to/clean/checkout --trusted-pins /outside/approved-activation.json

   This local verifier's success is correspondence only, not permission to launch.
   An altered workflow could bypass its own verifier; external prelaunch inspection
   remains mandatory. Run-attempt/ref/owner checks are also external requirements.
5. Any subsequently approved executable matrix must use fail-fast:false and one
   case per runner. Compile with an explicit :gym:testClasses task before the
   per-case supervisor starts. Use the explicit fully qualified :gym:test selection,
   not recursive test discovery through an evidence directory. Supply only the
   dedicated opt-in, exact reviewed case bytes/hash, and matching run/source vars.
   Preserve run-source.json and approved-case.json before the child begins.
6. Always preserve each original, including timeouts/partial logs. Capture finalized
   supervisor bytes before manifest/ZIP inspection. Independently receive the
   original archive and supply the trusted pins; write inspection output OUTSIDE
   the frozen case tree. Use strict_receiving.py inspect, never the legacy inspector.
   No automatic retry, resumption, changed allowance or replacement after failure.

## Qualification and remaining restrictions

All new local fixtures are explicitly synthetic and contain no actual gameplay.
The tiny integer seed label used by parser/serialization tests is not an allocated
or selected future-game seed. The positive fixture deliberately has fabricated
records and proves only correspondence to its pins. Python normal/-O/-OO variants
must exercise the same semantic refusals. Git activation tests create only local
synthetic Git objects, not remote workflows. No test invokes the game adapter.

This candidate still cannot prove every internal engine action has been timed;
it checks full expected call coverage corresponding to the recorded outer adapter
operations. Synchronous timing I/O can change runtime or fail after an engine
operation. Game-outcome invariance remains unproven. Trusted POSIX supervision
is not hostile-child/setsid-escape containment, rollback protection or worldwide
unique launch. No source label or authorization integer authenticates its issuer.
A favorable review would not authorize games; separate controller disposition
and new owner sample authority remain required. G08 remains INCOMPLETE.
