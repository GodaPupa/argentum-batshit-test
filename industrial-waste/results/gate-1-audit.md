# Industrial Waste Gate 1 audit

Disposition date: 2026-09-20

## Evidence accepted

The four-way screen and Pactdoll A replication completed on registered,
non-overlapping deterministic namespaces. Input hashes matched, all lists were
60/15, and no matchup or official promotion seeds were generated.

The JSON artifacts are model evidence only. They are admissible for deciding
where to spend implementation effort; they are inadmissible as match wins or as
proof of tournament strength.

## Decisions

### Turbo A — rejected at Gate 1

Four Crop Rotations did accelerate Tron in the screen, but the exact package
`-2 Giant's Boulder, +2 Crop Rotation` worsened mulligans, green and black access,
combo readiness, lethal rate, and fair-material output. The acceleration did not
justify its deckbuilding cost. This identity is preserved and may not be
silently rehabilitated.

### Recursive A — rejected at Gate 1

Blood Fountain plus Haunted Fengraf produced recursion access in 46.4% of runs,
but it did not improve conditional recovery after forced interaction and it
reduced Tron assembly while increasing colored failures. The package failed its
declared resilience objective in this model. This identity is preserved.

### Pactdoll A — advances, not promoted

The discovery screen and fresh-seed replication both favored the exact swap
`-2 Eviscerator's Insight, +2 Ichor Wellspring`. Replication retained higher
modeled lethal rate, non-infinite Pactdoll damage, and fair material; it reduced
mulligans and did not reduce Tron by turn 5.

This earns only an executable capability gate. Industrial Waste v1.0 remains
the permanent control and Pactdoll A remains a challenger.

## Capability inventory

Thirteen of the seventeen unique maindeck cards were already implemented in
Argentum. The four missing shared cards—Crop Rotation, Eviscerator's Insight,
Golem Foundry, and Ichor Wellspring—now have canonical definitions using
existing SDK primitives. The repository accepted their snapshots, affected
module compilation, canonical-printing checks, and Assay differentials in
GitHub Actions run 35533153870. Ichor Wellspring's C14 and C16 reprint rows were
also added to satisfy printing provenance.

## Gate 2 capability result

The first executable smoke attempt was rejected because the opt-in property was
not forwarded to the test worker; the second was rejected before game
initialization because it assumed the wrong working directory. Neither consumed
gameplay evidence. The corrected run, GitHub Actions 35534324422, executed all
eight games on namespace IW-G2-ENGINE-SMOKE-V1.

All eight games completed their bounded engine loops with zero exceptions and
zero rejected actions. Two Control games and two Pactdoll-A games reached lethal;
the remaining four reached the 12-turn-per-seat cap. Because the opponent was
60 Forest, mulligans were skipped, and the stock AI has no Industrial Waste
combo policy, these outcomes are capability evidence only. They authorize a
metric-aware executable goldfish harness; they do not promote Pactdoll-A and
cannot be cited as matchup results.

## Gate 3 paired goldfish result

The telemetry contract passed focused fixtures in GitHub Actions run
35535991166. Two later workflow attempts were rejected before gameplay: one
lacked the `just` executable and one compiled but explicitly skipped the
opt-in benchmark. Neither spent its namespace. Run 35536713445 then executed
all 24 games on `IW-G3-GOLDFISH-S1` and uploaded a complete, valid artifact.
The same setup commit incidentally reran the already-spent Gate 2 smoke namespace
in run 35536374431; that duplicate is reproducibility-only and is not merged into
any sample or counted as new evidence.

Pactdoll-A and Control each reached lethal in 9/12 games. Pactdoll-A recorded
fewer colored-mana-failure turns on average (2.50 vs 3.42) and more
non-infinite Pactdoll life loss (3.50 vs 2.92), while combat damage was nearly
flat (15.33 vs 15.83). The paired medians for all three differences were zero,
so these are weak directional signals rather than stable effects.

The screen did not exercise the central plan well enough to justify a larger
sample: neither list assembled Tron by turn 5 or reached Retriever-loop or
combo-ready state in any game. Control eventually assembled Tron in 3/12 games
and Pactdoll-A in 2/12, all on turns 6 or 7. The stock AI's fair-combat wins are
therefore useful as engine diagnostics but not as Altar Tron performance
evidence.

Decision: do not promote, reject, or statistically escalate Pactdoll-A from
this gate. Preserve its challenger status and spend the next implementation
budget on a deterministic Industrial Waste policy/line harness that can
actually tutor Tron and execute Retriever loops. Interaction recovery,
matchup wins, and sideboarding remain untested.

## Gate 3 policy calibration

The seed-free advisor fixtures passed in GitHub Actions run 35540194659. They prove deterministic
missing-Tron-piece selection for Expedition Map and Crop Rotation, Retriever-to-Retriever death
targeting, and Retriever selection for Ashnod's Altar when the payoff loop is already assembled.

Run 35540498081 then replayed the already-spent `IW-G3-GOLDFISH-S1` vector with the scoped policy.
The artifact is valid and explicitly non-promotional. Control and Pactdoll-A each moved from zero
to one Tron-by-turn-5 outcome; aggregate lethals increased from 18/24 to 19/24. Neither list reached
a Retriever-loop or combo-ready state. The simultaneously triggered stock-policy run 35540498101
is a reproducibility-only duplicate and is not counted or pooled.

Decision: the calibration clears the minimum bar for one fresh 16-pair policy screen. It does not
authorize matchup testing, sideboarding, or promotion. If the fresh screen again records no loop
or combo-ready states, stop sampling and diagnose the remaining line-policy failure.

## Gate 3 fresh policy screen

Run 35541012276 is rejected because its artifact mislabeled the fresh vector with the earlier Gate
3 digest. The implementation assertion confirms it played the intended namespace, but provenance
metadata is part of validity, so none of that artifact is accepted. After correcting the report
field, run 35541293028 executed all 32 outcomes on `IW-G3-POLICY-S1` with the registered digest,
zero exceptions, and zero rejected actions. The namespace is spent by this corrected run only.

Both lists reached Tron by turn 5 in 5/16 and lethal in 16/16, confirming the tutor policy is now
observable. Neither list ever reached Retriever-loop or combo-ready state, so the predeclared
harness gate failed. Pactdoll-A remains an unpromoted challenger; matchup and sideboard work remain
blocked.

Diagnosis: the v1 advisor only sacrificed a Retriever after another Retriever was already in the
graveyard. Against the inert opponent, no event seeded that first Retriever, making the loop policy
unreachable. The next permitted work is a seed-free v2 fixture for staging the loop from one
Retriever on the battlefield plus a second in hand or on the battlefield. No new experimental
namespace may be allocated until a replay-only calibration observes the line.

## Gate 3 policy-v2 calibration

The new seed-free sacrifice fixture passed in run 35541739181, proving v2 selects the battlefield
Retriever when another copy is in hand. Run 35542066782 replayed the original 12 pairs with v2 and
completed validly, but its summary was identical to v1 calibration and again recorded zero loop or
combo-ready states for both decks. No fresh namespace was allocated.

Decision: v2 fails calibration. The fixture began too late in the action sequence—it assumed Altar
had already been activated. Continue only with a deterministic action-choice fixture that presents
Altar activation against passing on a staged board. Matchups and further sampled screens remain
blocked.

## Gate 3 corrected policy-v2 calibration

The upstream action-choice fixture then failed in run 35542441270 and revealed that the generic
strategist discarded all mana abilities before advisor scoring. The correction is profile-gated
and defaults off, preserving every existing agent. Run 35542754981 passed the corrected fixture.
Corrected replay run 35543032055 subsequently recorded Pactdoll-A's first loop-available and
combo-ready state on turn 7, followed by lethal that turn; Control recorded none.

Decision: corrected v2 passes calibration and earns exactly one fresh 16-pair diagnostic screen.
The observation is not deck-promotion evidence and does not authorize a matchup gauntlet by itself.

## Gate 3 fresh policy-v2 screen

GitHub Actions run 35543607190 completed 32 valid games on the fresh
`IW-G3-POLICY-V2-S1` vector. Control recorded two loop-available/combo-ready games and Pactdoll-A
recorded one; every observed combo-ready state converted to lethal on the same turn. Control reached
lethal in 14/16 games and Pactdoll-A in 13/16, so the predeclared aggregate gate passed.

This result validates enough policy coverage for a minimal preboard matchup pilot, but it does not
promote Pactdoll-A. Control led loop incidence and lethal rate in the diagnostic sample;
Pactdoll-A's directional positives were fewer colored-mana-failure turns and a one-turn advantage
in the pair where both lists assembled the loop. Both lists remain candidates for the pilot.

## Gate 4 Madness Burn capability pilot

The opponent is Davide Canevazzi's Top 8 Madness Burn list from the 99-player 43rd Super Ingenio
on 2026-09-12. GitHub Actions run 35544249719 completed all 16 games on
`IW-G4-MADNESS-BURN-PILOT-V1` with play/draw swaps, zero exceptions, zero rejected actions, and no
draws. Control and Pactdoll-A each went 4-4, split evenly between wins on the play and draw. Neither
list reached combo-ready state.

Decision: the two-sided capability gate passes, but the pilot shows no challenger advantage.
Execute the one predeclared eight-seed fresh replication before adding another opponent. The pilot
remains non-promotional and must not be pooled with the replication.

## Gate 4 Madness Burn replication

GitHub Actions run 35544615045 completed all 32 fresh games on `IW-G4-MADNESS-BURN-R1` with zero
exceptions, zero rejected actions, and no draws. Control and Pactdoll-A each went 13-3. Control
recorded four combo-ready games to Pactdoll-A's three; each assembled Tron by turn 5 in 3/16, and
their mean colored-mana-failure turns were effectively flat at 2.00 versus 2.06.

Decision: do not escalate sampled gameplay. The opponent won only 3/16 against each list, missing
the predeclared four-win two-sided threshold, and Pactdoll-A again produced no win advantage over
Control. Preserve both artifacts separately and return to deterministic opponent-policy diagnosis
before spending another matchup namespace. Industrial Waste v1.0 Control remains the immutable
baseline; no challenger is promoted.

## Gate 4 Madness Burn policy audit

The seed-free audit found one material frozen-v0 failure: with exactly two Mountains and Fireblast
against an opponent at four life, v0 passes priority. It correctly executed the four other audited
identity lines. GitHub Actions run 35548082721 supplied the conclusive failure detail; run
35548397874 then passed a regression that records the v0 miss and confirms the production-candidate
profile casts lethal Fireblast through its self-alternative cost.

Decision: quarantine the Madness Burn pilot and replication win rates from all competitive and
promotion claims. They remain capability artifacts only. No new seeds were spent on diagnosis, and
no further Burn sampling is authorized under v0. Control remains frozen and Pactdoll-A remains
unpromoted.

## Gate 4 Madness Burn production-profile replay

The exact spent `IW-G4-MADNESS-BURN-R1` vector was replayed with only the Burn opponent changed to
`PRODUCTION_CANDIDATE_EXPIRING`. Runtime limits required a non-overlapping adaptive partition. The
canonical merge accepts only clean-success jobs from runs 35550602282, 35551201558, and 35551633513;
timed-out artifacts are preserved but excluded. The accepted partitions cover all 32 expected game
keys exactly once, with zero exceptions, rejected actions, or unapproved draws.

Burn won 14/16 against Control and 14/16 against Pactdoll-A, clearing the 4/16 pressure floor for
both lists. Twenty-two of 32 results changed from the frozen v0 artifact, clearing the observable
policy-change gate. Control and Pactdoll-A each won 2/16; this equality is replay-only diagnostic
evidence and not a matchup-rate estimate.

Decision: calibration passes and authorizes exactly one fresh Burn namespace under the production
opponent profile. It does not promote Pactdoll-A, unquarantine the old v0 win rates, or authorize
postboard work. Industrial Waste v1.0 Control remains frozen.
