# Mono-Blue Terror — verified assignment decoding and durable attempt records

## Accepted starting point

PR #107 passed CI run 35823617832 (CI #967) and merged at
`791a1b6b13d188a1811996966d4d84ecd3cdbb8c`.
The original frozen ZIP, deck identities, vector, provenance, authorization and seed registry
remain unchanged. This gate introduces no workflow, generator, game initializer or runner.

## Implemented boundary

The internal assignment decoder snapshots the caller's ZIP, passes it through the accepted pinned
archive/member verifier, then decodes strict UTF-8/LF vector and CSV text. It requires four distinct
nonzero canonical signed Long values, their matching hexadecimal forms, exact row order, every
protocol/deck/runner field, and the fixed seat/play-draw cells. It returns an unmodifiable list of
typed assignments. Decoding does not consume seeds for gameplay or authorize execution.

The internal durable-attempt journal exclusively creates the canonical block directory under an
existing evidence root. A claim binds source commit, qualified runner and actual ordered-vector
hash. CREATE_NEW writes plus file and parent-directory force calls precede each ledger transition.
The successful sequence is durable attempt, initialization-entry intent, then durable result bytes
and a digest receipt. The initialization-entry method records intent only; it invokes no engine.
Any ordering or write exception blocks the object. Partial files remain, existing claims cannot be
reopened, and no method deletes, overwrites or resumes a block. Explicit rejection is terminal.

The claim prevents repeats only within the supplied evidence root; a future production coordinator
must pin the authoritative root, provide cross-run persistence and verify execution authorization.
This primitive is not a distributed lock and cannot by itself prove that an engine was called.
Directory forcing was exercised on the Linux/Java 21 filesystem; unsupported filesystem operations
fail rather than silently downgrading durability. Power-loss behavior was not experimentally tested.

## Executed local validation

Thirty-four deterministic synthetic scenarios and one read-only check of the original frozen ZIP
passed in a standalone Kotlin/Java 21 harness. The exact new decoder and journal sources were
compiled with the accepted verifier source (blob `4f93ef567a4a1f02eeefec80309008b4140ccbe8`).
Existing simple model types/constants were mirrored in local-only stubs; full repository CI is
still required. The stubs and standalone launcher are not committed.

The scenarios cover all fourteen CSV columns, decimal/hex/Long boundaries, duplicates, ordering,
UTF-8 errors, immutable return data, initialization without an attempt, replay, restart, rejection,
and an injected partial-write conflict. All journal writes use temporary synthetic fixtures.
The real four assignments were decoded read-only and were never supplied to a journal or engine.
The committed Kotest wrapper exposes the thirty-four synthetic scenarios as separate tests.

## Scientific state and next gate

Official seeds remain 4, newly generated seeds 0, official gameplay attempts 0, official games
initialized 0, actions submitted 0 and matchup outcomes 0. There is no deck-performance result.
After this gate passes CI, the next task is integrating these primitives with the actual engine
initializer and evidence publisher, testing that integration without official gameplay, and
recording the execution decision before the fixed four-game smoke may run. No manual workflow
trigger is requested from the user.
