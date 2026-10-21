# ARGENTUM — Manual Transmission decision-policy preparation

**Disposition:** `SOURCE_PREPARATION_ONLY` — no policy implementation, no gameplay, no promotion.

**Source identity:** dedicated preparation branch from R9 bounded GREEN commit
`3ea58cae2cb087379a64608f0eb7373793aff2ed`;
ManaSolver blob `d02fded3fd9656aa2d032c49ce8312e1d6baaf93`.
Verified R9 run [39622338973](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39622338973), artifact ID `12356303078`, original ZIP SHA-256
`009507befd259f6ea09ec1545e45e6355495932d86ba80c439d4ff3a1f3fe321`.
Independent retained JUnit: 8 new multipip + 38 R9 + 43 protected R5 + 3 initializer
= **92/92 passing test cases** (23 unique XML suites; 0 failures/errors/skips).
This is bounded mechanics qualification, not competitive win-rate qualification.

An additional, independent neighboring source-regression workflow was launched at
[39625031640](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39625031640)
to corroborate Hidden Grotto, City of Brass, Ashiok and fetch/shock entry
against the **unchanged** R9 solver. Its results are not inherited or assumed.

## Protected authorities

- `main` remains pinned to `37a603617a03dfd2d81b39dae004f7f336a70dfc`.
- Frozen Manual Transmission v0.7 CONTROL blob
  `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`.
- Challenger A land-only blob `7984a2e87d8ddbe023d45620414d25a61691a040`.
- `AiProfile.CURRENT` blob `180918b2d7a59309e7b0617f2579fb2a6d06b7d5`.
- `ManualTransmissionR5Candidate.kt` blob
  `ef5ba9dacb02bbf2a00303c9ee4724565769385a`.
- R5 accepted opt-in Pact/Ballista/Statue/Encore/payment functionality remains
  the baseline. Original v0.7 opponent-matrix and RACE-FINAL-R1 accepted
  qualifications are separate historical artifacts: do not overwrite or conflate
  them with these **new proposed** CRUISE/SPORT/RACE decision tiers.
- Official Precision matched-game budget: R1 4 + R6 6 = 10/12; **2 reserved**.
  There were zero new full games in the R9 repairs. This preparation authorizes
  **0 additional games**. No merges, CONTROL/A promotions, production CURRENT
  changes, new seeds, or official verdict changes.

## Source-bound AI integration facts

1. `ai/.../engine/ManualTransmissionR5Candidate.kt` composes an explicit opt-in
   profile atop R4; `ManualTransmissionR4Candidate.kt` opts into productive
   Animar development and Ballista continuation. New lanes must derive from the
   **same immutable R5 comparator**, one independently attributable policy
   intervention at a time; never silently change the baseline.
2. `ai/.../engine/advisor/modules/ManualAnimarDevelopmentAdvisorModule.kt`
   already measures real same-turn creature follow-up value and rejects
   unproductive/imaginary continuation credit. Do not replace its measured
   behavior with unconditional commander bonuses.
3. Actual bot mulligans are implemented in
   `ai/.../engine/EngineAiPlayerController.kt` under `decideMulligan` and
   `chooseBottomCards`, including deterministic color/early-development checks,
   London bottoming and a Momir-only exception. The controller currently creates
   an `AiProfile.PRODUCTION_CANDIDATE_EXPIRING` AI for actions, independently of
   R5's opt-in profile. A policy change made only to `AiProfile` without an
   exercised controller/harness seam would not demonstrate a mulligan improvement.
   Confirm the **actual matched-pilot controller path** before modifying it.
   `DecisionResponder.respondSelectCards` also ranks generic selections but
   should NOT be assumed to be the production `decideMulligan` path.
4. Source-bound tests already exist:
   - `EngineAiMomirMulliganTest.kt`, and independent Pest Control
     mulligan diagnostics for broad anti-regression behavior.
   - `ManualAnimarDevelopmentPolicyTest.kt`,
     `ManualAnimarProductiveContinuationTest.kt`, `ManualR16AnimarValuationTest.kt`,
     `ManualR17AnimarProductiveWitnessSearchTest.kt`.
   - R5 archived Pact, Ballista, Statue, Encore, and payment regressions.
   R17 cites two real originally archived productive Animar windows (action
   indices **930** Eternal Witness and **1613** Imperial Recruiter); its own
   source also names negative-control checkpoints **1336, 1497, 1998**.
   Confirm original artifact hashes and exact replay results before reusing
   those snapshots; naming them here does not imply those replays were run
   in this branch.

## Pilot policy study design (no games)

### CRUISE — consistency / early Animar

**Question:** Can an isolated opening-hand or early-development policy find
*actually legal* earlier Animar deployments without over-mulliganing, assigning
phantom colors, or losing profitable noncommander plays?

Candidate surfaces: exact `EngineAiPlayerController.decideMulligan` +
`chooseBottomCards` integration seam if proven in the matched harness; existing
Animar advisor and `Strategist` action sequencing.

Prepare a predeclared source-bound fixture matrix covering real 7-card keep,
mulligan and London bottoming; missing green/blue/red; fetch/shock entry choices;
two vs three deterministic land sources; 1-land with actual searchable typecycling;
commander tax; a real same-turn hand-creature follow-up; and a counterexample
where skipping Animar for an immediate lethal is correct. No speculative
draw outcomes or library knowledge. Require actual action legality and
early development ability. Metrics: initial keeps, bottom choices, first legal
Animar opportunity, chosen-vs-legal deployment, actual counted counters, true
same-turn follow-up, illegal/phantom payment, rejected actions.

### SPORT — protective interaction / tempo

**Question:** Does a threat-aware, opponent-aware decision policy improve survival
without spending Pact before upkeep is payable, misassigning counters, or
holding a winning line for an imagined answer?

Comparator: R5 Pact-debt and Ballista/Statue accepted modules unchanged.
Existing seven accepted opponent-axis classifications are external fixed controls,
not new matchup win rates. Prepare exact saved-state windows with (a) fatal Pact
upkeep, (b) opponent threatening a real lethal, (c) real winning counterwar,
(d) safe development versus hold, (e) different opponent response timing.
Measure legal survival choice, Pact liability, actual mana, opponent threat
time, rejected actions, and false-live/false-stop classifications. Never assume
the opponent passes unless the replay's historical action stream proves it.

### RACE — executable combo conversion

**Question:** When a legal Statue/Ballista/Palinchron/Purphoros payoff is present,
does an isolated policy convert with fewer unnecessary steps and avoid loops
without manufacturing lethal through blockers or target protections?

Comparator: R5 accepted combo and payoff tests. Fixtures must include
repeatable/terminal vs counter-only Statue, actual Ballista X casting/payment,
real Purphoros damage, finite self-return exit, hostile defenses, dead payoffs,
and opponent interaction. Measure whether the chosen chain was legal, actual
terminal status, time/actions-to-payoff, unnecessary iterations, cost payment,
and stopping condition. Never score a provisional `GameSimulator` quiet state
that auto-passes opponents as independently proven interactive victory.

## Attribution and acceptance

- Three **independent opt-in lanes** from the **same R5 comparator**. Initially
  make only one policy-variable deviation per lane; no cumulative CRUISE→SPORT→RACE
  inheritance until separately measured/authorized.
- SOURCE/PRE-FLIGHT before optimization: catalog exact relevant source, identify
  true production/matched-harness dispatch path, register immutable witness IDs,
  failing/positive controls, original archive digest/SHAs and allowed changed
  files. Lock fixture selection **before** seeing candidate action scores.
- Every proposed improvement must demonstrate a distinct baseline failure or
  measurable gain in a **real source-bound state**; merely writing a preference
  function or obtaining a compile GREEN is insufficient.
- Targeted, archived-state and zero-game preflights first. Preserve original
  state/action/event hashes; count accepted/rejected actions and report
  divergent action indices; no cherry-picked replay truncations.
- A source-level policy GREEN is **not** a head-to-head win-rate verdict and
  cannot spend the two reserved Precision full-game slots without a distinct
  explicit approval.
- If a lane produces new illegal actions, archived R5 regressions, reliance on
  hidden cards, fabricated combo paths, or unverifiable evidence, HOLD and
  revert/contain only that opt-in lane. Keep every other source immutable.

## Next implementation decision

This commit is **planning only**. Before authoring policy logic, author a
source-identity audit and a fixed fixture manifest for the CRUISE opener path
(the highest-leverage hypothesized area), then run true baseline decisions and
preserve JUnit/trace evidence. Do not assume this source-only plan itself ran
any AI actions.

**Final intended sequence:** CRUISE source-bound pilot → SPORT source-bound pilot
→ RACE source-bound pilot → separate composition audit → optional user-authorized
gameplay qualification. Do not promote decks or profiles at any intermediate gate.
