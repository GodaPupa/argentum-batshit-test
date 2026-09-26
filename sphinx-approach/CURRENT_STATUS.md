# Sphinx's Approach — Current status

Status: STAGE_E_EXACT_CARD_REMOTE_QUALIFICATION_REQUIRED
Branch: `sphinx-approach/reconstruction-stage-e`

Reconstructed remote source: `4a97265559e989df937e8c09f9a42d58a4e9d79d`.
Qualification changes are published on the separate `lab/sphinx-next-gate` branch;
the first reviewed source is `a315d091a2c212f5fe43ae7d0dac7bfffa9dd530` (PR #180).
This record reports progress; it does not supersede frozen protocols or grant gameplay admission.

## Durable state

The reconstruction lineage is public. The original unpublished A-D Git/evidence bytes remain unrecovered;
see `RECONSTRUCTION_PROVENANCE.md`.

Historical selections carried forward without rerunning A-D:
- reconstructed v0.1 identity to be re-frozen prospectively;
- reconstructed Approach/Tolarian Terror hybrid identity to be re-frozen prospectively.

## Live repository leverage

A complete Mono-Blue Terror preboard 60 and production gameplay infrastructure already exist under the
Pest Control Tier-1 program. Stage E will reuse validated engine/pilot infrastructure patterns while
keeping Sphinx seeds, assignments, outcomes, and evidence in a new namespace.

## Counters

- New Stage-E official seeds generated: 0
- New Stage-E official seeds consumed: 0
- New Stage-E interactive games initialized: 0
- New Stage-E outcomes exposed: 0
- Confirmation games: 0
- Postboard games: 0

## Exact next-game blockers

1. Qualify the implemented Approach, Goliath Sphinx and Snap card identities through their exact
   deterministic scenarios, atomic continuation/serialization checks, corpus lint and ordinary snapshots.
2. Review the exact generated FRA/ULG/WWK card records and qualify the combined receiving source before
   accepting the card-support change. Required independent acceptance remains required.
3. Qualify candidate, closest no-Approach comparator and benchmark pilots on actor-authorized observations,
   including all reachable card decisions and interactions. The old full-state Terror production driver
   is not an admitted Sphinx pilot. Ferocity's in-progress actor boundary is not yet accepted for reuse here.
4. Integrate and qualify the canonical post-block-declaration priority repair. Source inspection confirms
   this receiving source retains defender priority after the final triggerless block declaration;
   that window is reachable in these decks and cannot be excluded through pilot behavior.
5. Prospectively freeze reconstructed deck identities, opponents, equivalent pilot-development budgets,
   matched-randomness procedure, sample size, endpoints and stopping rules; only then create Stage-E seeds.

The two reconstructed 60s, the required prospective closest no-Approach comparator, and the unchanged
source-backed Serpico Terror 60 have pending row hashes in `STAGE_E_DECK_RECONSTRUCTION.json`.
These are configuration records, not qualified decks or replacements for the lost A-D bytes.

## Current validation checkpoint

- Remote parent CI `36242920611` failed. Inspected engine and content logs both stop at unsupported
  `MetadataBuilder.scryfallId` and `MetadataBuilder.releaseDate` assignments in SphinxsApproach.kt.
  The qualification patch removes those two assignments and retains printing provenance in the manifest.
- The existing atomic effect's `storeMovedAs` field lacked the linter's required collection-writer
  registration; the patch adds that registration and a distinguishing linter regression.
- Exact scenario bank prepared: 14 Approach, 4 Snap, 3 Goliath fixtures. **None has passed yet.**
- Focused remote run `36244909681` tested combined runtime `e8a7b04f00879b402848a801f3754f01abfc3840`
  (tree `381bb59d683b682559f673d28ce831c9e9670745`) and failed before fixtures: the 2026 scenario
  module lacks the serialization library on its test compile classpath. The successor adds the existing
  pinned `libs.kotlinxSerialization` dependency; the saved-state fixture remains intact. Failure artifact
  `10907003075`, exact log and source bindings are preserved under `evidence/remote-36244909681*`.
  Broad CI `36244909644` also failed its 2025-26 scenario shard with the same dependency diagnostics.
- The local combined attempt exited before compilation because the filesystem ran out of inodes.
  Its failure log and the parent CI engine log are preserved under `evidence/`.
- `.github/workflows/sphinx-stage-e-exact.yml` runs the focused bank, ordinary snapshot generation,
  unrelated-drift rejection and generated-byte verification on GitHub. It creates no gameplay allocation.

No new deck-performance evidence, official seed, initialized game, or outcome has been produced.
