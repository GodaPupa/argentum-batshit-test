# Manual Transmission — final experimental conclusion

Date: 2026-09-24

Status: **COMPLETE — KEEP_V07**

Manual Transmission v0.7 is retained as the frozen hardware. The experiment supports the project's
core hypothesis at the bounded policy/timing level: the same 100-card Animar deck can expose
materially different public-state decision surfaces through Cruise, Sport, and Race pilot policy
without changing a card.

This is not a cEDH game-win-rate claim and does not claim a quantitative Cruise < Sport < Race win
percentage gradient.

## Frozen hardware

- Control: Manual Transmission v0.7
- SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`
- Cards: 100
- Game Changers: 0
- Hardware disposition: `KEEP_V07`
- Card changes authorized by the final gate: **0**

No accepted evidence supports a v0.8 hardware promotion.

## Accepted Race overlays

The final Race policy incorporates all seven independently qualified opponent overlays:

- R3-HT — Hashaton
- R3-M — Magda
- R3-BF — Blue Farm
- R3-SH — Shorikai
- R3-SY — Sisay
- R3-RS — RogSi
- R3-KB — Kinnan/Basalt

The final Race policy protocol is `MT_FINAL_RACE_POLICY_R1_2026_09_24`, accepted from dedicated
audit run **36031552516** at source
`9530ff2de311534e0f7f80137938aff20ed41cc4`.

## Same-hardware elasticity qualification

Protocol: `MT_SAME_HARDWARE_ELASTICITY_R1_2026_09_24`

Accepted source:
`ee3b9249a0f53e6a2eac6eacf155784782b96ba8`

Runs at that exact source:

- Manual Transmission Same-Hardware Elasticity: **36037314707 — SUCCESS**
- Manual Transmission Qualification: **36037314257 — SUCCESS**
- broad CI: **36037320714 — SUCCESS**

Artifact:

- name: `manual-transmission-same-hardware-elasticity-r1`
- ID: **10824458364**
- ZIP SHA-256:
  `a6aac951676b03f3d141802692d3a4ee3a0c4c20b27b3716fab0a5556977a421`
- independent download reproduced that exact ZIP digest
- manifest source SHA equals the accepted source
- manifest hardware SHA equals the frozen v0.7 SHA
- imported low-gear checkpoint SHA-256:
  `a8f95c2f3ea91ed7138a252204fdf8996732a426a511c95f4c6db2193114543a`

The imported D2-P repository copy initially lacked the source file's final newline. Commit
`ee3b9249a0f53e6a2eac6eacf155784782b96ba8` restored the exact imported byte provenance rather
than changing the predeclared expected hash.

## Elasticity result

The audit returned:

`PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07`

Policy surface sizes:

- Cruise-R1: 1 generic layer
- Sport-R1: 3 generic layers
- Race-R1: 3 generic layers + 7 accepted opponent overlays = 10 policy surfaces

Across all seven exact Race-axis enumerations:

- Race candidate errors: **0**
- Sport/reference errors: **13,133** total
- same hardware: **true**
- card changes authorized: **false**

Per-axis Race candidates remained clean:

- Hashaton: 2,950 states, candidate errors 0
- Magda: 960 states, candidate errors 0
- Blue Farm: 144 states, candidate errors 0
- Shorikai: 120 states, candidate errors 0
- Sisay: 220,972 states, candidate errors 0
- RogSi: 444 states, candidate errors 0
- Kinnan/Basalt: 74 states, candidate errors 0

The positive reference-error counts establish that the extra Race overlays are doing real
classification/timing work on the modeled axes; they are not empty labels.

## Defensible conclusion

**KEEP Manual Transmission v0.7.**

The experiment does not justify changing the 100-card hardware. It does justify retaining the
three-gear operating concept:

- **Cruise** — minimal generic policy surface for lower-gear fair development.
- **Sport** — broader generic interaction policy without axis-specific Race overlays.
- **Race** — the accepted high-gear public-state timing/resource policy with all seven qualified
  opponent overlays.

The final evidence supports policy elasticity on one fixed deck. It does **not** establish real-game
Commander win percentages, tournament conversion rates, or a numeric power-rating delta between
gears. Those claims remain outside this experiment.

## Closure

No further experiment is scientifically required for the stated Manual Transmission hypothesis.
Reopening hardware work would require new evidence that identifies an actual card-level deficiency,
not merely a desire to make Race resemble stock cEDH Animar.
