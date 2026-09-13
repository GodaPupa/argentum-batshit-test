# Project Pest Control — Status

- Status: Goldfish Sample #2 independent vector frozen; pre-execution validation
- Laboratory: Project Pest Control
- Branch: `pest-control/lab`
- Validated base: `47882cd645caf126afee6cf13a65909806fa40ab`
- Accepted rules-complete remote head: `b08d8fad6e6709db4834e8e956e6853152efb3b5`
- Accepted Phase 3 remote head: `8dd4a429e0799b60e7449c214a540dd8414b1f1c`
- Accepted Weather regression replay remote head: `8a13f2d8f9322b85ba6c52de571c591fea966a31`
- Control version: Pest Control v1.0 (permanent, immutable)
- Candidate status: proposed Tier-1 architecture only; not approved, constructed, or run

## Goldfish Sample #2 independent replication authorization

Goldfish Sample #1 is formally accepted at
`4ccd4f097ade866a8eb3eff42e897229cd34e29f` and its vector is permanently retired and
hard-disabled. Pest Control v1.0 remains exact; the challenger remains audit-only and unconstructed.

An independent 30-seed Sample #2 vector was generated after a read-only audit of all 28 available
refs and 1,500 normalized seed-like values. It has zero overlap with every visible Pest Control,
Batshit, and Project X seed. The ordered vector was frozen before Game 1 with SHA-256
`1e4247fceaa9a7d2f438ab8fa29733782f624cbcde383ec858153de1367e522d`; the derivation and immutable
execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-2-seed-freeze.md`.

Sample #2 uses the exact Sample #1 deck, agent, engine, telemetry, mulligan behavior, horizon, and
acceptance criteria. Mana-role classification will be derived post-execution only from safely
observable bottleneck card names; it does not change telemetry or gameplay. No Sample #2 game had
been executed when this freeze was recorded.

## New untouched Goldfish Sample #1 authorization

The Game 8 pending-effect/redundant-resource correction is accepted as green at remote head
`aff4a349390d3c8ac4dfcbd3479fe69d56c481da`. **SHARED ARGENTUM CHANGE: yes.** The immutable Pest
Control v1.0 list remains exact, and the challenger remains audit-only and unconstructed.

Every earlier Pest Control goldfish vector and execution entry point remains permanently retired and
hard-disabled. A new 30-seed deterministic vector was generated only after a read-only collision audit
of all 28 available local/remote refs and 1,469 normalized numeric seed-like values. It has zero
overlap with the audit set and every prior Pest vector. The ordered vector was frozen before Game 1
with SHA-256 `f7012b5807621453692699055c90037bcf44526b4bc59edb37205c221aff9365`;
its derivation and immutable execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-1-untouched-seed-freeze.md`.

Pest-only observation telemetry was finalized before execution to snapshot payoff state, Follow
availability, survival pressure, pending stack sources, mana-plausible useful pre-Weather spells,
and pure-lifegain activations at decision time. This does not change engine, Gym production, agent
policy, cards, deck composition, mulligans, or gameplay semantics. No game had been executed when
this freeze was recorded.

### New untouched Goldfish Sample #1 result

Preflight CI run 211 was fully green at frozen remote head
`4e47607c435e280a4531b1d977fb176955bc8742`. The 30 frozen seeds then ran exactly once in order with
no reroll, replacement, exclusion, replay, or deck/policy/telemetry change. All automated invariants
reported zero errors, and manual review of every Weather, Food, Follow, removal, Thrall/Scion, Ent,
mana-bottleneck, and terminal decision found no clear defect.

The sample is **formally accepted as Pest Control v1.0 development/engine goldfish performance
evidence**, not matchup evidence. Complete raw JSON, the human-readable per-game report, and the
acceptance audit are preserved under `docs/experiments/pest-control/goldfish-sample-1-untouched-*`.
The vector is now hard-disabled and no Sample #2, challenger construction, optimization, or opponent
self-play was started. Pest Control v1.0 remains byte-for-byte exact.

## Laboratory boundary

This document and `docs/experiments/pest-control/` belong exclusively to Project Pest Control.
Batshit Economics/Affinity, `mayhem/project-x`, and their seed vectors and experiment protocols are
read-only dependencies and are outside this laboratory's write and workflow scope. Only explicitly
authorized Pest Control vectors have been executed: the permanently retired regression vector and the
separately frozen fresh Sample #1 vector described below. No challenger construction, opponent
self-play, optimization, or Sample #2 was performed.

## Fresh Goldfish Sample #1 disposition

The accepted regression gate is `27b82421d34cf2e61b7eb106b59ed2809bb50013`. Before fresh execution,
30 new deterministic seeds were derived and frozen with vector SHA-256
`50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8`. A read-only audit of all 27
available remote refs inspected 50,553 seed-related lines/files and excluded 1,711 numeric seed values;
the new vector had zero overlap. The exact deck assertion, seed checksum, focused Pest tests, and full
CI run 200 were green at preflight head `1ecb976cb41c369edf195681e4c9d80ddf7e894f`.

The vector then ran exactly once, in order, with no rerolls, replacements, exclusions, substitutions,
deck changes, policy changes, or telemetry changes. All 30 engine games completed and the automatic
audit reported no rules/state, mana-provenance, trigger/counter, Storm-copy, Thrall/Scion, mana-legality,
or terminal inconsistency. Manual agent-sanity audit nevertheless found a clear policy defect:

- Games 1, 21, and 23 cast Weather the Storm at Storm 0 with no Researcher/Mascot payoff, no opposing
  pressure, and no survival need. Game 21 did so at 35 life.
- Game 22 cast two Weather copies (Storm 0 then Storm 1) with no payoff or opposing pressure.
- These casts only increased life in a blank-opponent environment. They violate the validated general
  requirements not to maximize life automatically or spend Weather merely because it is executable.

Therefore the entire fresh Sample #1 is **formally rejected**. None of its win clock, engine rates, or
other aggregates are accepted as Pest Control baseline, matchup, optimization, or variant-comparison
evidence. All 30 games are preserved intact in
`docs/experiments/pest-control/goldfish-sample-1-fresh-rejected.{md,json}`; no affected game was removed,
rerun, or replaced. The subsequent approved correction does not authorize replay, a new vector,
Sample #2, challenger construction, optimization, or opponent self-play.

## Fresh Sample #1 Weather-policy correction

The rejected games exposed a general one-ply valuation gap: raw life increased the board score even
when a pure lifegain spell had no opposing pressure, visible life-event payoff, or executable enhanced
follow-up. The correction classifies pure lifegain structurally from the resolving effect tree and
floors such legal-but-null casts below passing. It preserves casting when life is needed against
visible lethal pressure, when a permanent listens to its controller's lifegain events, or when the
event makes an affordable life-gained-this-turn spell meaningfully better. Spells with any concrete
non-life rider are excluded from the hold policy.

Payoff recognition is likewise structural: repeatable permanents triggered by their controller
gaining life receive a general intent tag. This covers Blood Researcher, Pest Mascot, and Marauding
Blight-Priest despite their different results, and does not tag Essence Warden merely because it
produces life. No Pest Control or card-name heuristic was introduced.

Focused deterministic coverage proves:

- pure lifegain is held with no pressure/payoff, including low-life and nonzero-Storm states;
- emergency Weather remains cast under visible lethal pressure;
- Weather remains cast for a visible Researcher/Mascot/Blight-Priest event payoff;
- lifegain may precede an executable enhanced Follow the Lumarets;
- Pulse of Murasa remains usable for its concrete recursion rider; and
- the existing Storm sequencing, independent-event, and deterministic-lethal cases remain green.

**SHARED ARGENTUM CHANGE: yes.** This is a general effect/result and trigger-property policy. The
permanent v1.0 list, mulligan policy, Weather rules, Follow rules, telemetry, engine, Gym, and card
definitions are unchanged. The rejected fresh vector has not been replayed, replaced, or used for
performance inference during the correction. Its one authorized regression replay is documented
below; any new sample requires separate authorization.

Focused validation passed all 46 Pest Control agent decisions plus the full structural-intent
analyzer suite. The full local AI suite is green. The frozen-deck/vector test passed with all three
opt-in goldfish executions skipped. The monolithic offline local `test` entry point remains blocked
before execution by the previously documented missing Byte Buddy 1.10.9 and
kotlinx-serialization-core 1.9.0 artifacts; no accommodation was made. Remote CI run 202 is fully
green on implementation head `6c13b8fd100040d60a0a0b33b66d7b484281e9d7`, including frontend,
engine, every scenario partition, content, tools, server, and the aggregate backend gate. The named
Argentum Validation workflow remains unavailable on this non-main branch without manual dispatch.

## Corrected fresh-vector regression gate

After explicit authorization, the exact rejected fresh Sample #1 vector was replayed once from
accepted Weather-policy head `1528ad2906e2b8e85b50f9672efb964622739f71`. All 30 seeds ran once,
unchanged and in their original CSV order. The vector checksum remained
`50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8`.

All automated invariants and the manual suspicious-decision audit are clean. Every Weather had a
visible Researcher/Mascot payoff or enabled enhanced Follow; all Storm copies and separate life events
matched; the original null Weather behavior in Games 1, 21, 22, and 23 did not recur. Chainer's Edict
and Bone Shards remained held against the empty opponent, Thrall/Scion and Warden event counts matched,
Researcher/Mascot triggers equaled counters, Scion provenance balanced, actionable bottlenecks were
relevant and deduplicated, Follow modes and Ent decisions were legal, and every game had clean mana
and terminal reporting. The detailed cast-by-cast audit is preserved in
`docs/experiments/pest-control/goldfish-sample-1-fresh-regression-replay-accepted.md`.

The corrected replay is **accepted only as regression-validation evidence**. The original fresh
Goldfish Sample #1 remains permanently rejected as performance/baseline evidence, and neither run's
aggregates may support performance or variant inference. The exact vector is permanently retired and
must never be executed again, optimized against, sampled, or compared. No replacement Sample #1 was
generated.

**SHARED ARGENTUM CHANGE: yes.** This records the already accepted general Weather-policy correction;
the replay introduced no new policy, engine, Gym, telemetry, card, or deck change.

## Fresh performance Sample #1 authorization and freeze

The corrected same-seed Weather replay is formally accepted as clean at remote head
`8a13f2d8f9322b85ba6c52de571c591fea966a31`. **SHARED ARGENTUM CHANGE: yes.** Its exact seed vector
is permanently retired and must never be reused for performance, optimization, variant comparison,
or sampling.

A new, separately identified 30-seed performance vector was derived and frozen before execution.
Its SHA-256 is `c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`.
A read-only audit of 28 local/remote refs inspected 135,029 seed-related lines/file contents, 1,019
seed-path instances, and 2,495 distinct numeric values; overlap was zero. The vector, derivation,
scope, and immutable execution contract are preserved in
`docs/experiments/pest-control/goldfish-sample-1-performance-seed-freeze.md`.

Before freezing, Pest-owned report telemetry was extended to expose opening-color, counter,
all-three-coexistence, Weather/payoff, and counterfactual-Warden totals. The counterfactual now counts
only creature entries while Researcher/Mascot is present and no Essence Warden is present, and
reports potential Researcher and Mascot counters separately. This is descriptive test telemetry
only; no engine, production agent, deck, mulligan, card, Weather, Follow, or gameplay policy changed.
Execution remained blocked until the frozen vector and telemetry passed preflight validation.

### Fresh performance Sample #1 result

Preflight CI run 205 was fully green at `9f3f3c1cc8270c3bbd55b8017c0935dbbee7771f`. The frozen vector
then executed exactly once, in order, without a reroll, replacement, exclusion, substitution, or
deck/policy/telemetry change. All 30 games completed and all automated rules/state, Storm-copy,
lifegain-event, counter, Thrall/Scion, provenance, mana-legality, and terminal invariants passed.

Manual audit found a clear residual agent-policy defect in Game 15: the agent cast Weather at Storm 0
and 21 life with no payoff, survival pressure, or same-turn enhanced Follow, then spent Food for more
irrelevant life. On turn 6 it had another Weather, Follow, and four lands but cast normal Follow
without first enabling the enhanced mode. The entire sample is therefore **rejected** and none of its
clock or engine aggregates are accepted as baseline, optimization, matchup, or variant evidence.

The complete raw JSON and human report are preserved as
`docs/experiments/pest-control/goldfish-sample-1-performance-rejected.{json,md}`. The manual audit and
descriptive rejected-run metrics are in
`docs/experiments/pest-control/goldfish-sample-1-performance-audit-rejected.md`. No game was removed
or replayed, and no correction was made. A separate approval is required before investigation or any
same-seed validation. The permanent v1.0 list remains exact and the challenger remains unconstructed.

Descriptive rejected-run facts, retained for diagnosis only: 9/30 games mulliganed (11 total);
meaningful deployment reached 6/14/27 games by T1/T2/T3; all 30 modeled terminals were combat lethal
(median T7; 0/2/9/21 by T4/T5/T6/T7); 140 separate lifegain events gained 250 life; Researcher and
Mascot recorded 60/60 and 77/77 trigger/counter totals; 27 Weather casts had exact Storm-copy counts
(`0`: 12, `1`: 15); four Thrall deaths created four Scions; no Scion was sacrificed for mana; and the
functional-state classifier reported 26 engine-functional and four fair-creature-functional games.

The rejection is formally accepted at remote head
`23da31d777e3e1cd45f9655295565f2fd1a59662`. The complete 30-seed vector (SHA-256
`c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`) is permanently
disqualified from performance/baseline evidence. It may not be used for sampling, optimization,
performance inference, or variant comparison. No game was rerun, removed, replaced, or repaired,
and no replacement vector was generated.

### Game 15 lifegain-resource correction

The deterministic reproduction separated Game 15's three linked decisions without executing its
retired seed: a high-life pure lifegain spell with no survival need or event payoff; a subsequent
pure lifegain activation after life had already been gained; and an executable life-event into an
enhanced life-gained-this-turn follow-up.

The complete root cause was a scope mismatch in the general null-lifegain policy. Pure lifegain
spells were classified structurally and held unless survival, a visible repeatable payoff, or an
affordable enhanced follow-up made the event concrete. Printed activated abilities bypassed that
classification entirely, so sacrificing a Food for three strategically irrelevant life points
could outrank passing and interrupt the already-enabled Follow line. The next greedy decision then
saw the enhanced line only after the enabling event had been wasted; Game 15 later took normal
Follow instead of the independently available materially superior Weather-to-enhanced-Follow line.

The correction adds a strict structural pure-lifegain classifier for printed activated abilities
and routes both casts and activations through the same general utility gate. A pure lifegain
resource is held below passing when it has no survival role, visible repeatable event payoff, or
executable enhanced follow-up. Abilities and spells with another effect leaf remain outside this
hold rule. Existing general sequencing continues to prefer an enabling life event followed by the
enhanced spell when that two-action line is executable, while normal Follow remains correct when
the enabler cannot profitably precede it. The survival, payoff-driven Storm-0, useful Storm setup,
and anti-junk-Storm cases remain green.

**SHARED ARGENTUM CHANGE: yes.** This is a general effect/resource policy correction in the AI
intent catalog and strategist. No card-name, Game 15, or Pest Control heuristic was added. No rules,
engine, Gym, telemetry, mulligan, deck, Weather, Follow, or card-definition behavior changed.

Focused deterministic coverage is green for the four new Game 15 chain scenarios and the existing
high-life hold, survival Weather, payoff-driven Weather, useful Storm setup, and anti-junk-Storm
regressions. The structural intent suite and complete AI test suite are green locally. The rejected
vector has not been replayed, and a same-seed regression replay is ready only for separate explicit
authorization after full CI is green.

### Game 15 same-seed regression replay

The separately authorized replay executed all 30 performance-sample seeds exactly once, unchanged
and in their original order, from corrected-policy remote head
`132ffbc83473c950a74cff7800f9e8085a7c6e50`. No reroll, replacement, exclusion, deck change, seed
change, telemetry change, or policy change occurred during execution. The first opt-in command was
reported `UP-TO-DATE` by Gradle and executed no games; the subsequent forced task was the sole actual
replay execution.

Game 15's original chain is corrected. Its turn-five Storm-0 Weather at high life now has the
concrete purpose of enabling Follow the Lumarets, which is cast immediately in enhanced mode. Food
is not activated, and the original null Weather/Food/later-normal-Follow sequence does not recur.

The replay is nevertheless **rejected in full**. Game 8 casts Weather at Storm 0 with no
Researcher/Mascot or survival need, then activates Food while Weather is still pending, and finally
casts enhanced Follow. The LIFO resolution order records Food's life event before Weather's. The
Food event has no payoff and cannot improve Follow beyond the enhancement already guaranteed by the
pending Weather, so it is a strategically null resource expenditure. The corrected policy observes
resolved life-gained-this-turn state but does not account for a guaranteed pending life event on the
stack when valuing another pure-lifegain activation.

All automated rules/state and telemetry invariants passed, and the remainder of the manual audit
found no additional clear defect. Every Weather copy and separate life event matched; all other
Weather and pure-lifegain decisions had a visible payoff, enabled Follow, or represented useful
Storm sequencing. Normal Follow decisions were either not profitably preceded by Weather or
preserved Weather for a later payoff line. Edict and Bone Shards stayed unspent against the empty
opponent; Researcher/Mascot trigger-to-counter accounting, Thrall-to-Scion creation, Scion
provenance, actionable bottleneck deduplication, Ent decisions, mana legality, and terminal reports
were internally consistent.

The rejected original and rejected replay are preserved separately. Replay artifacts:

- `goldfish-sample-1-performance-regression-replay-rejected.json`
- `goldfish-sample-1-performance-regression-replay-rejected.md`
- `goldfish-sample-1-performance-regression-replay-audit-rejected.md`

The vector is now permanently retired from any further execution. It remains disqualified from
sampling, optimization, performance inference, baseline evidence, and variant comparison. This
replay does not make the laboratory ready for a new Sample #1 vector; further correction or
execution requires separate authorization.

### Game 8 pending-effect / redundant-resource correction

The corrected same-seed replay is formally preserved and rejected at remote head
`538666b012fe9577ecb8e261c95e1357f43767e3`. The original Sample #1 and replay remain separate
rejected artifacts. Their 30-seed vector is permanently retired from all further execution; the
correction did not execute it or any other Pest Control seed. The opt-in performance runner is now
unconditionally disabled to enforce that disposition.

The residual root cause was generic pending-condition accounting. The pure-lifegain resource gate
saw only resolved life-gained-this-turn state, so Food was credited as the enhanced-Follow enabler
even though a controlled Weather already pending on the stack guaranteed that condition. The Food
event added no survival, repeatable-payoff, threshold, or additional-line value.

The general intent catalog now recognizes only fixed, unconditional, controller-directed lifegain
on stack objects as guaranteed. The strategist includes that pending amount in survival and
follow-up-unlock valuation, while conservatively declining certainty for conditional, optional,
dynamic, targeted, mixed, counter-exposed, or hidden-response-exposed effects. Additional lifegain
remains usable when pending life is insufficient for survival or a repeatable payoff values each
event. Non-lifegain resource actions remain outside the suppression rule.

**SHARED ARGENTUM CHANGE: yes.** Only generic AI intent/action valuation changed. Rules, engine,
Gym behavior, telemetry, cards, the permanent control, and the challenger are unchanged.

Six focused regressions cover redundant pending lifegain, insufficient survival gain, additional
repeatable-payoff value, no-pending Follow enablement, disruptable pending effects, and an
independently valuable non-lifegain action. The complete deterministic Game 8 reconstruction now
passes priority with Food intact, resolves Weather, and casts enhanced Follow while preserving the
resource. The full Pest decision suite, complete AI suite, frozen-control assertion, and Pest
telemetry regressions are green locally. All opt-in goldfish cases were skipped. Full remote CI is
green in run 209 on implementation head `cbff8feaa80489fc3f2d37b0e9d92f57f93346cf`, including
frontend, engine, every scenario partition, content, tools, server, and the aggregate backend gate.
The named Argentum Validation workflow is main/manual-only and is not dispatchable on this branch
through the available connection; run 209 executes its compile/test/Gym coverage and the broader CI
matrix. The laboratory stops at fresh Sample #1 seed-readiness. No new vector is authorized or
generated by this correction.

## Rejected Sample #1 disposition and correction scope

Goldfish Sample #1 is formally and permanently rejected as performance, baseline, matchup, or
optimization evidence. Its exact 30-seed vector (SHA-256
`87d8624e407286bfa9b1c9d4629fd29163ae8bbec4b98ab51ace7f9c9e1d764f`) is frozen solely as a
regression vector. It must never be used to tune the deck or agent, select changes, or infer future
Pest Control performance.

Three narrowly scoped general corrections are implemented and validated:

- pure opponent-directed forced-sacrifice spells are held when their resolved result sacrifices no
  opposing permanent;
- sacrifice-for-mana permanents require a newly executable follow-up whose resolved state is
  strategically productive, and generic provenance records source, activation, production,
  consumption, funded action, and unused mana; and
- actionable mana telemetry waits until legal land play has been considered, uses the authoritative
  mana solver (including sacrifice sources), separates total/color/tapland constraints, excludes
  strategically irrelevant interaction, and deduplicates a card's unresolved constraint.

**SHARED ARGENTUM CHANGE: yes.** These are generic agent-policy and Gym telemetry corrections. No
Pest Control card-name heuristic or deck change is involved.

Game 25 provenance was resolved before the correction: its Scion was sacrificed on turn 13,
produced one colorless mana, consumed zero mana, funded no action, and expired with one unused mana.
The corrected isolated reproduction preserves the Scion and reports no activation or provenance
error.

## Correction replay audit and final regression gate

Two exact-order replay attempts used the same frozen vector, without rerolls, replacements,
exclusions, deck changes, or seed substitutions. Both are invalid regression evidence and none of
their performance or engine aggregates may be used:

1. The first attempt exposed opponent-dependent removal being counted as actionable against the
   blank opponent merely because it controlled a land.
2. After that relevance fix and green CI run 195, the second attempt held Chainer's Edict in games
   9, 13, 19, and 27 and preserved the Game 25 Scion, but manual audit found 21 false Bone Shards
   bottlenecks. Bone Shards' nonmodal `ModalEffect` cost fork is intentionally opaque to the
   historical whole-card intent scorer, so telemetry had failed to inspect its removal modes.

The follow-up correction reads every mode structurally, uses the authoritative target finder to
require a legal opposing permanent, and leaves historical card scoring unchanged. Ten focused Gym
telemetry regressions now cover both the null and valid-target modal-removal cases, along with the
requested land, total-mana, color, tapland, Scion-mana, provenance, and deduplication cases. CI run
196 is fully green on remote correction head `c7f84ad8799b21a5b651492073273d0682e48465`.

After explicit renewed authorization, the final replay ran all 30 seeds unchanged and in their
original order from the fully green correction head. Every game passed the harness audit and the
manual cross-check found no rules/state, telemetry, mana-legality, terminal, or agent-sanity error:

- Chainer's Edict remained held in games 9, 13, 19, and 27;
- Game 25 created one Scion and preserved it, with no mana activation or provenance record;
- no opponent-dependent interaction appeared as an actionable mana bottleneck and no bottleneck
  key was counted twice;
- every Weather copy count matched its Storm count and every original/copy produced its own
  three-life event;
- Researcher and Mascot trigger totals exactly matched their counters added, including independent
  fan-out when multiple payoff permanents were present;
- every Carrier Thrall death created exactly one Scion, with all Scion provenance balanced;
- Follow used only its engine-reported normal or enhanced mode, Ent actions were legal, all life
  events had a positive amount and known source, and every terminal was engine-reported; and
- the replay contained all 30 distinct frozen seeds in the exact CSV order.

The corrected replay is accepted solely as regression-validation evidence. Goldfish Sample #1
remains permanently rejected for performance, baseline, matchup, and optimization purposes. The
30-seed vector is now permanently retired and must never be executed again or used for optimization
or future performance inference. The laboratory is ready for a separately authorized fresh Sample
#1, but no fresh seeds or games were generated automatically.

## Goldfish Sample #1 original audit result

The exact frozen 30-seed vector was executed once, in order, without rerolls, replacements,
exclusions, deck changes, policy changes, or seed substitutions. The run completed all 30 games,
but **Sample #1 is rejected and is not a Pest Control performance/engine baseline**.

Three independent audit blockers were found:

1. The solitaire agent cast Chainer's Edict into an empty opposing battlefield in games 9, 13, 19,
   and 27. This is a genuine general agent-policy defect, not ordinary interaction stranded in hand.
2. In game 25, a Carrier Thrall Scion was sacrificed for mana, but no spell recorded that Scion as
   a mana source. This is an unresolved agent-sanity or mana-provenance telemetry defect.
3. The generated “genuine mana bottleneck” metric samples pre-land-drop and repeated priority
   states. It therefore reports false positives (including missing green with a Forest in the kept
   hand) and cannot support the requested bottleneck aggregate.

The rules/state portions that can be audited from the run were internally consistent: 142 separate
life-gain events gained 234 life; Researcher recorded 41 triggers and 41 counters; Mascot recorded
71 triggers and 71 counters; all 23 Weather casts had the expected Storm-copy count; four Carrier
Thrall deaths created four Scions; all 30 terminals were engine-reported combat lethal. These facts
do not cure the invalidating defects above.

The complete rejected-run record is preserved under `docs/experiments/pest-control/`. The vector is
not a performance sample; only the explicitly authorized correction replay may execute it again.
The permanent control remains exact and the challenger remains audit-only and unconstructed.

## Phase 3 deterministic agent-validation result

Thirty-seven deterministic positions now validate the requested Pest Control strategy surface
through the live production-candidate agent and rules engine. They are board-state probes, not deck
construction, seeds, goldfish games, matchup samples, or optimization data.

Existing generic policy passed unchanged for supported Blood Researcher/Pest Mascot deployment,
survival-first Weather the Storm, refusing irrelevant Storm inflation, Blight-Priest drain lethal,
Carrier Thrall blocking, all four Bone Shards sacrifice/discard comparisons, removal target value,
hexproof/ward use of Chainer's Edict, flashback, removal patience, ordinary-cost Snuff Out at low
life, race-sensitive Snuff Out, productive Carrier Thrall death, Follow the Lumarets setup and
immediate normal-mode use, late-game Ent/Wildling casting, non-lifegain winning lines, and general
pending-lethal recognition.

The deterministic coverage also demonstrated genuine general gaps, corrected without card-name
heuristics:

- positive +1/+1-counter effects now advertise a lasting `PUMP` payoff to structural intent;
- life-gain-enhanced selection and basic-land tutor faces have explicit structural intent tags;
- land typecycling is recognized from the cycling mechanic's search metadata, independently from
  an Omen face;
- useful pre-Storm spells receive bounded option value for the additional event and visible
  repeatable counter/drain payoffs, while a spell that is already worse than passing receives none;
- a non-mana alternative cost receives bounded tempo value only when the preserved mana makes a
  relevant follow-up executable;
- early land-tutor/typecycle actions receive bounded development value, while feasible late-game
  bodies retain their normal board value;
- sacrifice-for-mana actions are considered only when their resulting mana unlocks an executable
  spell, and are not used when the spell is already executable or no productive use exists;
- targeted protection is held until opposing targeting or committed combat creates a credible
  window; and
- `SacrificeSelf` mana abilities now expose their irreversible payment in generic legal-action
  metadata, making the existing meaningful-action policy usable for Scions, Spawn, Treasure-like
  resources, and future equivalents.

**SHARED ARGENTUM CHANGE: yes.** The changes above are general intent, sequencing, protection-window,
and legal-action metadata corrections. No Pest Control card-name heuristic, Gym change, seed vector,
or opponent policy was added. No requested strategic behavior remains unresolved at this gate.

The frozen Pest Control v1.0 main deck and sideboard blocks below remain byte-for-byte identical to
the accepted rules-complete head. The proposed Tier-1 challenger remains audit-only and has not been
constructed.

## Phase 2 rules-complete result

The approved shared addition is complete: `PredefinedTokens.EldraziScion` is the authoritative,
reusable 1/1 colorless Creature — Eldrazi Scion definition with the mana ability “Sacrifice this
creature: Add `{C}`.” `Effects.CreateEldraziScion` provides fixed- and dynamic-count creation
facades. Generic SDK and rules-engine tests pin its characteristics, colorlessness, creature types,
cost payment, zone departure, ordinary use of the resulting mana, and visibility of both creature-
entry and death events to normal triggers.

The eight previously missing cards are individually implemented and registered:

| Card | Implemented rules | Focused proof |
|---|---|---|
| Carrier Thrall | Dies trigger creates exactly one predefined Eldrazi Scion | Direct death/token scenario plus Pest engine integration |
| Bone Shards | Choose sacrifice or discard as an additional cost; destroy target creature or planeswalker | Both additional-cost branches |
| Nature's Claim | Destroy target artifact or enchantment; its controller gains 4 | Target removal and controller life gain |
| Snuff Out | Conditional `{0}` alternative cost while controlling a Swamp, pay 4 life, nonblack-creature restriction, no regeneration | Alternative-cost cast and life payment |
| Suffocating Fumes | Opposing creatures get -1/-1 until end of turn; cycling `{2}` | One-sided characteristic change; cycling supplied by the shared primitive |
| Pulse of Murasa | Return target creature or land card from a graveyard to its owner's hand; gain 6 | Graveyard return and life gain |
| Nihil Spellbomb | Tap/sacrifice to exile target player's graveyard; battlefield-to-graveyard `{B}` may-pay draw trigger | Graveyard exile, sacrifice, optional mana payment, and draw |
| Masked Vandal | Changeling; ETB may exile a creature card from your graveyard to exile an opponent's artifact/enchantment | Targeting, optional graveyard payment, both exile movements |

The generated/approximate markers were removed only after human review of Chainer's Edict,
Marauding Blight-Priest, and Unearth. Chainer's Edict now uses canonical target-player sacrifice
wording and its flashback path is directly tested. Marauding Blight-Priest's one trigger per life-
gain event is directly tested. Unearth's existing return scenario remains green after review.

Sagu Wildling was corrected from an Adventure approximation to the existing Omen primitive. Its
Roost Seek face now searches for a basic land and shuffles the card into its owner's library. It is
not modeled as landcycling. Generous Ent's actual Forestcycling is directly tested.

## Deterministic Pest Control interactions

The rules suite now proves:

1. Essence Warden creates one life-gain event for each qualifying creature entry, including an
   opponent's entry.
2. Bogwater Lumaret creates life-gain events only for qualifying entries under its controller.
3. Blood Researcher receives exactly one +1/+1 counter per separate life-gain event.
4. Pest Mascot follows its implemented Oracle text and receives one +1/+1 counter per separate
   life-gain event.
5. Multiple Wardens and Lumarets create independent triggers, not an aggregated gain.
6. Weather the Storm's original and Storm copies resolve into separate gain-3 events; each produces
   independent Researcher, Mascot, and Blight-Priest triggers.
7. Marauding Blight-Priest produces one opponent-life-loss trigger per qualifying gain event.
8. Carrier Thrall creates exactly one Scion on death, and that Scion's entry participates normally
   in the Warden/Lumaret engine.
9. Sacrificing the Scion removes it, produces usable `{C}`, and creates no false life-gain event.
10. Follow the Lumarets' existing direct scenario distinguishes its normal maximum-one behavior from
    its life-gained-this-turn maximum-two behavior.
11. Bone Shards separately validates sacrifice and discard additional costs.
12. Chainer's Edict validates sacrifice and flashback, including exile after flashback resolution.
13. Snuff Out validates its alternative cost and four-life payment.
14. Generous Ent validates actual Forestcycling.
15. Sagu Wildling validates actual Roost Seek/Omen resolution and shuffle-back behavior.

No new production rules-engine primitive was needed beyond the approved reusable Scion token and
creation facade. No Gym or agent-policy behavior was changed.

## Validation record

- Focused Scion SDK/rules tests: green.
- Focused card and Pest interaction scenarios: green.
- Card-definition golden snapshots: regenerated through the authoritative exporter and green,
  including JSON round trips.
- Full CI: run 188 on accepted remote head `b08d8fad6e6709db4834e8e956e6853152efb3b5`
  is the authoritative green validation for the rules-complete gate.
- Argentum Validation workflow-equivalent local run: compilation completed; all Pest-relevant,
  engine, AI, and Gym tests reached in the run were green. The monolithic `test` task was ultimately
  red only because this container forbids Byte Buddy's dynamic self-attachment used by unrelated
  server mocking tests; the same server suite is green in CI run 187. A separate local `:gym:test`
  completed green. The GitHub connection available to this laboratory does not expose
  `workflow_dispatch`, so the named workflow itself could not be dispatched on this non-main branch.
- Phase 3 focused validation: 37 Pest agent decisions, the full structural-intent analyzer suite,
  and the generic mana-ability enumerator suite are green.
- Phase 3 full local engine and AI suites: green. The monolithic offline `test` entry point cannot
  construct `:mtg-search:testRuntimeClasspath` in this container because its cache lacks Byte Buddy
  1.10.9 and kotlinx-serialization-core 1.9.0. Separately, this container forbids Byte Buddy dynamic
  self-attachment in the unrelated server mocking tests. Neither environment limitation is being
  accommodated by production changes or altered test semantics.
- Phase 3 full CI: run 189 on implementation commit
  `7ba1c8568ca68e64c6e0230fa90cad7d560a9417` is the authoritative green full-matrix gate. Frontend,
  engine, all scenario partitions, tools, server, content, and the aggregate backend job passed.
- Pest correction focused validation: all 41 deterministic agent decisions and all 10 actionable
  mana/provenance telemetry regressions are green.
- Pest correction full CI: run 196 on remote head
  `c7f84ad8799b21a5b651492073273d0682e48465` is fully green across frontend, engine, all scenario
  partitions, content, tools, server, and the aggregate backend gate.
- The named Argentum Validation workflow cannot be dispatched on this non-main branch through the
  available GitHub connection. Its exact local task graph is blocked before execution because this
  offline container lacks Byte Buddy 1.10.9 and kotlinx-serialization-core 1.9.0. No production code
  or test semantics were changed to accommodate that environment limitation; CI run 196 supplies
  the authoritative clean dependency environment and full backend coverage.

The laboratory stops at the fresh-sample-readiness gate. Challenger construction, new seeds,
gameplay sampling, matchup self-play, and optimization remain prohibited pending separate approval.

## 1. Branch and base

The dedicated branch `pest-control/lab` was created directly from
`47882cd645caf126afee6cf13a65909806fa40ab` (`Replay frozen Affinity vector on residual policy
candidate`). At inspection time this was the current `main` head and the latest head with completed,
successful CI, Argentum Validation, and Frozen Grixis Affinity Regression Replay checks. A later E2E
Nightly run on the same SHA was cancelled; it did not supersede or fail those completed validation
gates. The previously shared green head `004ccc27...` was eight commits behind this head.

## 2. Permanent Pest Control v1.0 control

The following list is frozen exactly. It must never be silently updated or replaced.

### Main deck — 60 cards

```text
4 Essence Warden
4 Carrier Thrall
4 Blood Researcher
4 Pest Mascot
4 Fierce Witchstalker
3 Generous Ent
4 Follow the Lumarets
4 Weather the Storm
4 Cast Down
2 Bone Shards
2 Chainer's Edict
10 Forest
7 Swamp
4 Jungle Hollow
```

Count verification: 23 creatures + 16 noncreature spells + 21 lands = 60.

### Sideboard — 15 cards

```text
4 Tamiyo's Safekeeping
3 Nature's Claim
3 Snuff Out
3 Suffocating Fumes
2 Pulse of Murasa
```

Count verification: 4 + 3 + 3 + 3 + 2 = 15.

## Proposed Tier-1 challenger (audit-only)

The proposed challenger also counts to 60 cards with a 15-card sideboard. It remains a candidate
architecture only. It has not been built, seeded, played, or approved as a replacement.

```text
4 Essence Warden
4 Bogwater Lumaret
4 Blood Researcher
4 Pest Mascot
3 Marauding Blight-Priest
3 Sagu Wildling
4 Follow the Lumarets
4 Unearth
3 Cast Down
3 Snuff Out
2 Tamiyo's Safekeeping
2 Weather the Storm
6 Forest
5 Swamp
4 Jungle Hollow
3 Illegitimate Business
2 Khalni Garden
```

```text
Sideboard
3 Duress
3 Nihil Spellbomb
3 Suffocating Fumes
2 Masked Vandal
2 Tamiyo's Safekeeping
2 Weather the Storm
```

## Phase 1 audit record (superseded)

Sections 3–7 below preserve the accepted Phase 1 audit as a historical record. Their “missing” and
“recommended” labels describe the pre-implementation state and are superseded by the Phase 2 result
and validation record above.

## 3. Card coverage

Registry legality means the repository legality registry contains `PAUPER`; it does not imply that a
production definition exists or is authoritative. Every one of the 28 unique names across both lists
(including Forest and Swamp) is Pauper-legal as represented by the registry. Cast Down and Chainer's
Edict have uncommon canonical source definitions but are Pauper-legal through common printings
represented by that registry.

| Card | Deck use | Definition | Scenario coverage | Audit result |
|---|---|---|---|---|
| Essence Warden | Both | Present | None direct | Hand-authored trigger watches every other creature entering under any player's control. Needs multiplayer-side and event-multiplicity tests. |
| Carrier Thrall | Control | **Missing** | None | Registry-legal. Dies-to-Eldrazi-Scion needs a new shared predefined token/facade; this is the one identified shared SDK vocabulary gap. |
| Blood Researcher | Both | Present | None direct | Hand-authored `YouGainLife` trigger adds one +1/+1 counter per trigger resolution. Separate-event interaction is unpinned. |
| Pest Mascot | Both | Present | None direct | Hand-authored 2/3 trample with the same per-lifegain counter trigger. It is not a Pest token. Hunt for Specimens tests the separate 1/1 Pest token's death lifegain only. |
| Fierce Witchstalker | Control | Present | None direct | Hand-authored ETB creates Food. Card-specific ETB/token coverage is absent. |
| Generous Ent | Control | Present | None direct | Hand-authored ETB creates Food and Forestcycling `{1}`. No direct ETB or typecycling scenario. |
| Follow the Lumarets | Both | Present | **Direct** | Direct scenario covers maximum one without prior lifegain and maximum two after lifegain, including hand/bottom movement. |
| Weather the Storm | Both | Present | None direct | Hand-authored Storm plus gain 3. Generic Storm copying exists, but no scenario pins each copy as a separate lifegain event or its trigger fan-out. |
| Cast Down | Both | Present | Gym smoke only | Hand-authored nonlegendary-creature target and destruction. No one-card scenario. |
| Bone Shards | Control | **Missing** | None | Registry-legal. Existing choice, sacrifice, discard, and destroy primitives appear sufficient; needs both additional-cost branches and target tests. |
| Chainer's Edict | Control | **Generated / approximate** | None direct | Generated definition composes forced sacrifice and flashback `{5}{B}{B}`. Requires human review and hand-authored normal/flashback scenarios. |
| Forest | Both | Built-in basic | Generic | Baseline land behavior is broadly covered. |
| Swamp | Both | Built-in basic | Generic | Baseline land behavior is broadly covered. |
| Jungle Hollow | Both | Present | Partial direct | Direct scenario pins entering tapped from hand and off-stack. ETB gain 1 and both mana abilities are not card-specifically pinned. |
| Tamiyo's Safekeeping | Both | Present | None direct | Hand-authored hexproof + indestructible until EOT, then gain 2. Needs response-window and lifegain-trigger scenarios. |
| Nature's Claim | Control | **Missing** | None | Registry-legal. Existing artifact/enchantment destruction and target-controller lifegain primitives appear sufficient. |
| Snuff Out | Both | **Missing** | None | Registry-legal. Existing conditional self-alternative-cost, pay-life, color filtering, and destruction primitives appear sufficient; needs normal/alternate path and AI life-payment tests. |
| Suffocating Fumes | Both | **Missing** | None | Registry-legal. Existing opponent-creature group modification and cycling primitives appear sufficient. |
| Pulse of Murasa | Control | **Missing** | None | Registry-legal. Existing graveyard target, return-to-hand, and gain-life primitives appear sufficient. |
| Bogwater Lumaret | Challenger | Present | **Direct** | Direct scenario covers its own ETB, another controlled creature's ETB, and the opponent-creature negative case. |
| Marauding Blight-Priest | Challenger | **Generated / approximate** | None direct | Generated definition drains each opponent once for each `YouGainLife` trigger. Requires human review and separate-event/fan-out tests. |
| Sagu Wildling | Challenger | Present | Partial direct | Direct scenario covers cast/ETB gain 3. Its Roost Seek Omen basic-land search and later creature recast are untested. This card has an Omen, not landcycling. |
| Unearth | Challenger | **Generated / approximate** | Partial direct | Direct scenario exercises a special prepared/Craft return plus agent target choice elsewhere. Ordinary mana-value ≤3, ownership, invalid-target, and cycling boundaries remain unpinned. |
| Illegitimate Business | Challenger | Present | None direct | Hand-authored enters tapped, gain 1, and black/green mana abilities. Needs land sequencing plus lifegain-trigger coverage. |
| Khalni Garden | Challenger | Present | None direct | Hand-authored enters tapped, creates a 0/1 Plant, and taps for green. Needs token/trigger and sequencing coverage. |
| Duress | Challenger SB | Present | None direct | Hand-authored reveal/select noncreature nonland/discard composition. Needs legal-choice and whiff scenarios. |
| Nihil Spellbomb | Challenger SB | **Missing** | None | Registry-legal. Existing targeted graveyard exile, tap/sacrifice, leaves-battlefield, optional mana payment, and draw primitives appear sufficient. |
| Masked Vandal | Challenger SB | **Missing** | None | Registry-legal. Changeling is implemented and tested generically. Existing graveyard exile and artifact/enchantment exile primitives appear sufficient, but the optional “if you do” ETB chain needs a focused composition proof. |

Summary: 18 named nonbasic cards have production definitions, 8 named nonbasic cards are missing,
and Forest/Swamp are built-ins. Three present definitions are explicitly generated/approximate and
require human review: Chainer's Edict, Marauding Blight-Priest, and Unearth.

## 4. Rules and test gaps

No new general rules-engine mechanic is presently proven necessary for the lifegain core. The engine
already emits a gain-life event per resolved gain-life effect; Storm copies are distinct stack
objects, so Weather the Storm should produce separately resolving gain-life effects. The required
proof is missing, however. Before any games, add deterministic scenarios that demonstrate:

1. One and multiple Essence Warden triggers, including opponent creature entry.
2. Blood Researcher and Pest Mascot receiving one counter for each separate lifegain event, and no
   extra counter merely for the amount of life gained.
3. Weather the Storm's original plus Storm copies resolving as separate lifegain events and producing
   the expected Warden/Researcher/Mascot/Blight-Priest trigger fan-out.
4. Follow the Lumarets before and after any earlier life-gain event in the turn.
5. Marauding Blight-Priest draining separately for separate events and each opponent where relevant.
6. Unearth's ordinary target boundaries, cycling, and resolution when the target becomes illegal.
7. Bone Shards' sacrifice and discard choices as additional costs, including nonrefundable costs on
   counter/illegal resolution and sensible automatic payment choices.
8. Chainer's Edict normal cast, opponent sacrifice choice, flashback permission/cost, and exile after
   flashback resolution.
9. Snuff Out's Swamp condition, black-creature exclusion, normal mana cost, four-life alternative
   cost, and low-life legality/agent behavior.
10. Generous Ent Forestcycling; Sagu Wildling separately through its actual Roost Seek Omen flow.
11. Jungle Hollow, Illegitimate Business, and Khalni Garden entering tapped, generating ETB life/token
    events, and supporting black/green sequencing.
12. Each sideboard card's legal targets, costs, zone movements, and negative cases.

## 5. Generic agent-policy coverage and gaps

| Concern | Existing generic coverage | Remaining gap before experiments |
|---|---|---|
| Lifegain-engine deployment | Simulation observes resulting life totals; gain-life/drain effects receive card-intent tags. | Creature permanents do not receive an explicit engine-synergy deployment prior. Add deterministic choice tests first; change policy only with separate approval. |
| +1/+1-counter payoff valuation | Board evaluation values current counters and projected power/toughness. | It does not explicitly value future counters from expected lifegain density. |
| Repeated/separate lifegain events | Rules simulation can expose each trigger and resulting state within its rollout. | No explicit multiplicity/combo policy or regression pins Warden/Researcher/Mascot/Blight-Priest fan-out. |
| Storm sequencing | Generic legal-action simulation can cast spells and Storm copies are engine-supported. | No Storm-count-aware hold/order policy or Weather-specific agent test was found. |
| Removal targeting | Removal/sweeper intent tags, target simulation, hold policy, and removal patience already exist. | Add Pest-list target regressions, especially Cast Down restrictions and Bone Shards payment choice. |
| Protection timing | Hexproof/indestructible/prevention intent tags and response-window hold logic already exist. | Add a Tamiyo's Safekeeping regression; do not assume its gain 2 is valued as engine synergy. |
| Recursion | Graveyard-to-battlefield movement is tagged as recursion; target simulation exists; an Unearth target-choice test exists. | Ordinary Unearth target bounds and timing/value choices need broader deterministic coverage. |
| Landcycling / land search | Cycling/typecycling actions are enumerated and evaluated; generic land sequencing exists. | No dedicated Forestcycling-versus-cast policy test. Sagu Wildling is an Omen and needs its own search/recast sequencing test. |
| Alternate-cost removal | Alternative costs and automatic pay-life/sacrifice/discard payments are enumerated. | No general life-preservation policy proves when Snuff Out should use mana versus four life; Bone Shards branch selection also needs proof. |
| Tapland mana sequencing | Generic usable-mana land ordering and board-presence land sequencing tests exist. | ETB lifegain/token synergy across the three Pest lands is not explicitly valued or pinned. |

## 6. Shared Argentum changes requiring approval

One shared SDK/mtg-sets vocabulary addition appears required to implement Carrier Thrall faithfully:
an authoritative 1/1 colorless Eldrazi Scion predefined token with “Sacrifice this creature: Add
`{C}`,” plus its creation facade and SDK/scenario tests. The repository currently exposes an Eldrazi
Spawn helper, not the required Scion behavior. This audit stops before that change, as required.

No other production engine/SDK change is currently indicated. The other seven missing cards appear
composable from existing primitives, but that conclusion must be confirmed by one-card scenario work.
Any failure that demonstrates a general engine/SDK gap must return for approval rather than being
worked around in a Pest-only definition.

No Gym or agent-policy change is approved or included. The policy gaps above should first be tested
against current generic behavior; any proposed general policy change is a separate approval boundary.

## 7. Recommended validation sequence

1. Obtain approval for the shared Eldrazi Scion SDK/mtg-sets addition; implement and validate it in
   isolation if approved.
2. Human-review and replace or explicitly certify the generated definitions for Chainer's Edict,
   Marauding Blight-Priest, and Unearth, each with a direct scenario.
3. Add the eight missing card definitions one card at a time, with one authoritative scenario file per
   card and no deck construction.
4. Add focused interaction scenarios for separate lifegain events, Storm copies, trigger fan-out,
   counters, drains, Follow the Lumarets, and Pest/tapland entry sequencing.
5. Add deterministic current-agent decision tests for deployment, removal/payment choices,
   protection, recursion, Forestcycling/Omen sequencing, Storm ordering, and alternate costs. Report
   failures before changing production agent behavior.
6. Run card/scenario verification, then the standard shared validation gates. Do not generate seeds or
   run games as part of these steps.
7. Only after an explicit later approval, materialize the challenger as a deck candidate; then stage
   goldfish and matchup self-play as separate, opt-in experimental phases. The frozen control remains
   Pest Control v1.0 regardless of challenger results.
