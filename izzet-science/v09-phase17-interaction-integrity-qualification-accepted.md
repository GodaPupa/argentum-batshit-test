# v0.9 Phase 17 — Interaction Integrity Qualification Accepted

Disposition: `V09_PHASE17_INTERACTION_INVARIANT_SWEEP_ACCEPTED`

Accepted card control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Frozen identities

- Experimental source commit: `a9800ae035a6664078a3ae0caba9f7ef5c447414`
- Experimental source tree: `d0dd0a3f90970893db999bc3128eb1e96e9345cb`
- Workflow runner commit: `cf6fe2f1005f7f1ad85885ea67e26f99909df9b1`
- Workflow runner tree: `2a429bb17a1d6d96381ab0e0f766fd8fca679551`
- Temporary workflow SHA256: `f7345ba1dec195a286f9d47542ed8f04fd363c8b04c3972f1057937bb6b1e6e2`
- Qualification validator SHA256: `aa8f92cab4b06e6117fd45b458ddaa9983582b9cfdb559fc72cfc96da791f215`
- Interaction contract SHA256: `136b8b43d1c7e42352b783fa2058f706bcd2737228d674ff76c10e14c1460c9e`
- Mana harness SHA256: `9d4eab04e694acba246528419a2d5667c31e056348d58f0bf5c8628d7d35d470`

## GitHub execution

- Qualification run: `35554539657`
- Qualification job: `106195496277`
- Artifact: `10620031225`, `izzet-v09-phase17-interaction-integrity`
- Artifact size: 1,695 bytes
- Artifact ZIP SHA256: `5dec0c423f2a27d26347b22a0bd9778b9ed4cf39acce1fe20b515869b3c63b0d`
- Ordinary CI on source commit: `35554435429` — success
- Ordinary CI on workflow runner: `35554543306` — success

Every qualification step completed successfully: exact detached checkout, source and
control identity verification, Phase-15 semantics, Phase-16 contract validation,
the 1,024-pair invariant sweep, manifest construction, and artifact upload.

## Independent artifact audit

The downloaded ZIP digest matched GitHub's recorded digest. It contained exactly
four expected files. All three receipt hashes matched the manifest:

- Phase-15 validation: `1cf902cd09a78600c508bbc07ef416e210a274ae183575cc4568f1c5cb2673a0`
- Phase-16 validation: `034ed36626018b2a9f5123ea6f1e93172bd0ffe5b50c4cf46066c24111e22628`
- Phase-17 validation: `bf7de4a0e88ca16030eae4e22d1825695b71caa9f7afc24a1cd70ab14ac974eb`

The Phase-17 receipt records 1,024 pairs / 2,048 trajectories, public regression
coordinate `1`, zero emitted outcome fields, zero assigned or consumed experimental
seeds, and no pilot authorization. Search of the extracted artifact found no
serialized interaction outcome assignment.

## Decision

Accept the qualification as reachability and invariant evidence only. It supports
no effect estimate, card change, opponent-frequency assumption, tempo value,
survival claim, or win-rate claim. The accepted Phase-14 tutor policy is unchanged,
and v0.7 remains the exact accepted card control.

The temporary workflow is removed and ordinary CI restored byte-for-byte. The next
eligible work is a seed-free freeze of the exact official interactive-pilot runner,
sample size, seed identity, and artifact contract; no pilot is authorized by this
acceptance alone.
