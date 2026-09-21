# Pest Control Tier-1 Grixis — 12-game replication production freeze authorization

## Scope

This gate authorizes exactly one production freeze for the already-approved 12-game preboard Grixis
replication block. It authorizes seed generation and artifact freezing only. It does not authorize
game initialization, action submission, execution, outcome exposure, sideboarding, deck changes,
pilot changes, or protocol changes.

## One-shot discipline

The production workflow:

- is `workflow_dispatch` only;
- accepts no user-entered execution values;
- rejects GitHub reruns by requiring `GITHUB_RUN_ATTEMPT=1`;
- checks out the exact dispatch commit and records its computed tree;
- downloads and verifies the exact accepted four-game smoke freeze artifact;
- performs an entropy-free preflight first;
- makes exactly one `os.urandom(96)` request;
- quarantines the complete 96-byte draw before validating any seed;
- retires the entire draw on any zero, duplicate, or collision;
- provides no reroll, replacement, regeneration, or second-draw path;
- uploads only a frozen, outcome-free artifact.

The collision exclusion set is fixed at 538 retired seed identities: the prior complete 534-value Pest
set plus the four accepted Grixis smoke seeds.

## Required frozen result

A successful freeze must contain exactly 12 unique, nonzero seeds with zero overlap against the 538
retired identities and exact assignment balance:

- 6 Pest on the play / 6 on the draw;
- 6 Pest seat zero / 6 seat one;
- 3 observations in each seat × starting-deck cell.

The manifest must report `FROZEN_UNEXECUTED`, `runner_state=DISABLED`, zero initialized games,
zero submitted actions, zero outcome artifacts, and zero outcome exposure.

A separate reviewed provenance change is required before any execution gate may be created.

This document does not itself generate seeds or initialize a game.
