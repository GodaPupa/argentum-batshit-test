# v0.9 Phase 15 — Capsize Interaction Semantics Accepted

Disposition: `V09_PHASE15_SEED_FREE_ACCEPTED`

## Provenance

- Control: `izzet-science/v0.7-control.md`
- Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`
- Experimental source: `70f14a6b7e89f200f7e421febb9529673a42d64a`
- Experimental source tree: `c8f6a7b6df463783297bdca9ce2d47957924a51a`
- Interaction module SHA256: `a8c8be234958c32f7c2c58869a2f6bbe52e40313137a82b2c3609c911020a14e`
- Validator SHA256: `5b2afa73fb9635f6ef4d171a129d63b93ffc4a8a7d449560f21933408eac566f`
- Workflow runner: `f4f65c19c26b5731bcffe41d444e5dfd118c440a`
- Workflow tree: `8f46b199091ce2514604ef67a52e2aa07e6cd808`
- Workflow SHA256: `976edd45125156677915b464aecf7d85aa64e7c94c17599b64317ecc76e0f8e9`
- GitHub Actions run: [35552812250](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35552812250), attempt 1
- Job: `106190593492`, `izzet-v09-capsize-interaction`
- Ordinary CI run: [35552816003](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/35552816003), success
- Artifact: `10619535197`, `izzet-v09-phase15-seed-free`, 984 bytes
- Artifact ZIP SHA256: `7bb94dd2ef4ac5e5ae386accb5a801830b3742908ddb97e77a98f07e13bc56d4`
- Reproduced validation transcript SHA256: `757cc1d0d1b6b03666e89d6eb840bcfbf8bee409b6c009460566c0bf4820c01d`
- Samples: 0
- Experimental seeds assigned or consumed: 0

## Result

The exact frozen source passed Python compilation, all twelve Capsize interaction
fixture groups, and the adjacent tutor-policy, unified-payment, and repeated-
acquisition contract validators. Every workflow step completed successfully. The
artifact upload recorded exactly two nonempty files and GitHub reported the ZIP
digest above; the complete job log exposes the validation transcript and manifest
construction inputs. A fresh local reproduction produced the transcript digest
recorded above.

The accepted semantics now distinguish legal and illegal targets, normal and
buyback payment, retained and lost buyback, countered and target-illegal outcomes,
self-targeting, and separate commander-to-hand and commander-to-command-zone
branches. Izzet Signet, Prismatic Lens, Star Compass, and Goblin Electromancer are
covered explicitly.

## Decision

Accept Phase 15 as deterministic infrastructure only. It establishes legal Capsize
resolution semantics but no opponent frequency, tempo, survival, backup-plan win,
or match-win claim. No card or new behavior policy is promoted. The accepted
Phase-14 tutor policy remains available for future stateful models, and v0.7 remains
the exact card control.

Phase 16 may freeze fixed-event paired estimands under the limits in the Phase-15
gate. No sample or experimental seed is authorized until that output contract and
its malformed-output rejection suite pass seed-free.
