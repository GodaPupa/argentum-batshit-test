# Pest Control Tier-1 coverage — Mono-Blue Terror freeze pre-entropy incident

## Disposition

Workflow run `35814874094` is permanently classified as
`REJECTED_PRE_ENTROPY_DISPATCH_GUARD_INCIDENT`.

It is **not** a seed-generation attempt and must never be counted as a frozen-vector attempt.

## Observed boundary

The run was a manual `workflow_dispatch` on `main`, workflow attempt `1`, with the exact
acknowledgement:

`GENERATE_TIER_ONE_MONO_BLUE_TERROR_SMOKE_4_NO_GAMEPLAY`

The job failed in the initial fail-closed dispatch guard before repository checkout.

The following steps were skipped:

- repository checkout;
- frozen source audit;
- the `Draw and quarantine exactly once; no gameplay` step; and
- therefore every call capable of requesting entropy.

The evidence-upload step then failed only because no output directory existed. The run produced no
GitHub artifact.

## Provenance consequence

- `os.urandom(32)` calls: `0`
- official seeds drawn: `0`
- quarantined seeds: `0`
- retired seeds from this incident: `0`
- official vector: absent
- games initialized: `0`
- actions submitted: `0`
- outcome exposure: `0`

The run does not consume the one authorized production entropy draw.

## Root cause and correction

The artifact-absence condition used an inline `gh api ... --jq` shell expression. Direct audit of
the dispatch metadata proves the branch, run-attempt, and acknowledgement guards were correct, and
the three prior Terror freeze workflow runs contain zero artifacts. The failure is therefore isolated
to the artifact-absence query implementation.

The corrected workflow:

- validates the same repository artifact query during pull requests;
- uses Python standard-library HTTPS/JSON parsing for both PR and production artifact checks;
- keeps production restricted to `main`, workflow attempt `1`, and the exact acknowledgement; and
- uploads evidence after the freeze step only when that step was actually entered, while still
  preserving evidence from a post-quarantine invalid draw.

A fresh manual production dispatch may be considered only after the corrected guard passes full PR
validation and is merged. No automatic rerun is authorized by this incident document.
