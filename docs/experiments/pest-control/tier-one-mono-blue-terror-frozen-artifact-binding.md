# Pest Control — frozen Mono-Blue Terror binding and artifact verification

## Accepted input

PR #106 sealed provenance and removed the production freeze trigger. Its accepted merge is
`f294908dc35d0e7a3d1dd86013d104207c52c298`.

The sole production freeze remains run `35819861075`, artifact `10733086089`, generated at
`eb140403cceff8e930afdfb2874972c6e44f77e7`. The vector remains `FROZEN_UNEXECUTED`.
This change does not modify the artifact, its provenance, the authorization record, any deck,
any historical construction proof, or the seed registry. It introduces no workflow or command.

## Combined gate

Two complementary checks are submitted together rather than creating separate digest-only gates.

`PestControlTierOneMonoBlueTerrorFrozenVectorBinding.inspect` binds the accepted four-cell plan,
qualified runner and frozen artifact identities. Its canonical SHA-256 is
`b67ed0fc7ff831114bc8bb095dfcfb19fada628213ec7d772a0ab066d7f0f4f6`.
A successful result is `VECTOR_FROZEN_EXECUTION_NOT_AUTHORIZED`; failed input explicitly returns
`VECTOR_BINDING_REJECTED`. Inherited readiness checks may initialize the existing fixed,
nonexperimental opening fixtures. They never use an official seed or submit an action.

`PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier.inspect` accepts an already supplied ZIP
byte array, snapshots it, and checks its pinned archive hash before decompression. It verifies
exactly the five approved member names and each member's pinned hash, including the checksum
inventory. It rejects duplicates, missing or additional members, directory/path entries,
malformed ZIP data, altered content and input exceeding the 64 KiB compressed / 32 KiB expanded
limits. It performs no network access, filesystem extraction, seed generation, game initialization
or action submission. It returns digests and errors, not seed values or assignment objects.

Each production facade exposes only `inspect`. The ZIP primitive's configurable pins are internal
and exist to exercise synthetic byte fixtures; the public verifier accepts no replacement pins.

## Validation evidence

Before submitting this change, the exact verifier source was compiled with the installed Kotlin
compiler and Java 21. Eighteen local checks passed: fourteen synthetic/structural checks, read-only
verification of the original accepted ZIP, and rejection of its one-byte modification, truncation
and appended-byte variants. No game was initialized by this standalone verifier run.

- Verifier source SHA-256: `15b8a4f3c4f34ee182f5e4a003e3408de7a1cdd2210df7a84859edf3c8fc4a98`
- Matching repository blob: `4f93ef567a4a1f02eeefec80309008b4140ccbe8`
- Accepted ZIP SHA-256: `bbf9f20e834f27f838e37de78c905c213a8917651818367360a8e8a140914961`

The repository tests add fourteen synthetic ZIP checks and six binding/provenance checks.
Their CI result must be observed before this gate is accepted; the standalone verifier result is
not a substitute for compiling the complete engine-backed binding.

## Execution boundary and next work

Four official seeds and four assignments already exist. This change creates none and consumes
none for gameplay. Official games, submitted actions and outcome exposure remain zero.
Both the initializer and runner remain disabled, regeneration remains prohibited, and no manual
workflow action is needed for this gate.

After acceptance, the next useful work is typed assignment decoding and durable-attempt integration
against this same verified artifact, with synthetic ordering/error fixtures before any gameplay.
No extra metadata-only wrapper is required. A separate recorded execution decision remains necessary
before an official game can initialize. These checks supply no new matchup-performance evidence.
