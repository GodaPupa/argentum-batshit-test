# v0.9 Position 1 — Engine Coverage Batch T Gate

Purpose: qualify Rolling Thunder by composing the already-qualified X-cost and divided-damage rails,
adding only the smallest reusable target/count and cast-validation support required for an X-sized
division among any number of targets.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through S remain unchanged.
- Batch S is accepted from Actions run 35893967649 and audited artifact
  `izzet-v09-position1-engine-batch-s` (SHA-256 digest
  `4c9ef6e365a749de6df88d172825ac9db4966face304ebdad83d82630582ce9d`).
- Batch S source SHA is `fd9e05fc64614613d27f4937e14f4bac41acaa86`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Batch T triage

Rolling Thunder is the smallest remaining reusable engine surface.

- X-cost selection and `DynamicAmount.XValue` already exist and are qualified.
- `DividedDamageEffect` already preserves announced shares and drops only illegal-target shares.
- Generic target validation already understands the `TargetRequirement.unlimited` contract.
- The missing target-side seam is exposing that existing unlimited contract on `AnyTarget`.
- The missing cast-side seam is validating `DividedDamageEffect.dynamicTotal` from the announced
  cast context rather than comparing every distribution to the fixed `totalDamage` fallback.
- No card-specific executor is justified.

The other Izzet blockers require broader new machinery: repeat-count persistence for Multikicker
(Everflowing Chalice), a replicate cast/copy rail (Lose Focus), or a spell-on-stack source that taxes
opposing spells targeting it (Kaervek's Torch).

## Acceptance

1. Rolling Thunder is {X}{R}{R}, Sorcery, with current Oracle behavior.
2. It uses the existing X-cost rail and `DynamicAmount.XValue`.
3. `AnyTarget(unlimited = true)` accepts an unbounded target count and has minimum zero.
4. A cast with more than three targets is legal when the X allocation is legal.
5. For multiple targets, the announced distribution must match the exact chosen X.
6. Every chosen target receives at least 1 damage.
7. X=0 with no targets is legal; X=0 with a chosen target is rejected.
8. Resolution preserves the announced shares and does not redistribute a share whose target becomes illegal.
9. Canonical TMP snapshot is reblessed through a fail-closed workflow.
10. Full golden card snapshots pass.
11. Live unresolved count is exactly 33.
12. Rolling Thunder is absent from unresolved output.
13. Official games/seeds/outcomes remain 0/0/0 and the exact v0.7 control remains unchanged.

Infrastructure, fixture, compilation, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
