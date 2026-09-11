# Batshit Economics Optimization Experiment E

Experiment E compared the frozen Variant C incumbent with exactly one candidate change:

- `-1 Unearth`
- `+1 Greedy Freebooter`

The permanent original Batshit Economics control was not modified. Variant C remains the
provisional incumbent. Experiment E is not promoted, is not selected for replication, and its seed
vector is permanently retired.

The structural finding is that Greedy Freebooter again produced the intended productive-body
behavior, but removing Unearth #4 materially reduced useful recursion, including Flamebreather,
Ghast, and Epicure returns. The net paired result was zero. No further Greedy Freebooter
substitution experiment is authorized at this time.

## Execution identity

- Accepted parent: `44222eef9796a28bae6cec06315ee5885a5219f2`
- Experiment commit: `59d8c5e9abce87c86a5e45e4c27ac0c785f86227`
- Workflow run: `34435159984` (first and only execution, success)
- Artifact: `batshit-optimization-experiment-e`, id `10137046085`
- Artifact SHA-256: `f2b35ca1b18ee68c7804eebc12ca6f9cc8cbf96129c5adea53248a1b5347c919`
- Frozen seed-vector SHA-256: `4f83d150cf29c49f625cf289652f5dc51a6333f02c6bb19a44cb2a96c4833f6f`
- Seed assignment: 100 unique seeds, 50 play and 50 draw, zero overlap with every prior
  seed CSV and the hard-coded development/smoke seeds.

The seed vector in `gym/src/test/resources/batshit-optimization-e-seeds.csv` is permanently
retired from all future development, regression, smoke, baseline, replication, and optimization
samples.

## Validation status

The opt-in workflow passed its deck-diff assertion, seed uniqueness/overlap/assignment gates, all
200 game-completion checks, non-empty-action checks, raw play/cast timestamp checks, and the
existing Flamebreather/Guttersnipe raw-trigger summary invariants.

Manual audit found one pre-existing telemetry-summary defect outside those automated assertions:
the printed `Mirkwood Bats triggers` total counts every `AbilityTriggeredEvent` whose source name is
Mirkwood Bats. It therefore included two NDAA rescue triggers on Mirkwood Bats in each arm. Printed
totals were 104 for incumbent C and 107 for E; the actual token-creation/sacrifice trigger totals
were 102 and 105. Raw-event lines are sufficient to correct the report, but the summary telemetry
does not exactly match its label. Experiment E is therefore telemetry-qualified and is not clean
promotion evidence without a later narrow telemetry fix. No rules/state failure was found.

## Primary paired result

| Outcome | Pairs |
|---|---:|
| Both win | 32 |
| Both lose | 64 |
| Incumbent C win → E loss | 2 |
| Incumbent C loss → E win | 2 |
| Net E paired flips | 0 |

Incumbent C and E each finished 34-66. C was 21-29 on the play and 13-37 on the draw; E was
20-30 on the play and 14-36 on the draw. This is frozen Argentum-agent paired self-play, not a
real-world matchup win rate.

E ended earlier in 6 pairs, on the same turn in 85, and later in 9. Mean ending-turn difference
was +0.01 half-turns (E minus C); median paired difference was zero. Mulligan counts were identical
within all 100 pairs. Both arms had Batshit mulligan distribution 89/9/2 games at zero/one/two and
Red distribution 70/21/9.

Ending-turn distributions:

- C: `7:1, 8:1, 9:3, 10:1, 11:10, 12:9, 13:13, 14:11, 15:10, 16:10, 17:10,
  18:5, 19:4, 20:2, 21:6, 22:1, 24:1, 27:1, 28:1` (mean 15.06, median 15).
- E: `7:1, 8:1, 9:3, 10:1, 11:11, 12:7, 13:14, 14:12, 15:7, 16:11, 17:11,
  18:4, 19:4, 20:2, 21:6, 22:3, 24:1, 28:1` (mean 15.07, median 14.5).

## Development and engine telemetry

| Metric | Incumbent C | Variant E |
|---|---:|---:|
| T1 creature development | 63 | 64 |
| Meaningful permanent by T2 | 84 | 85 |
| Mean first-development turn (games with development) | 1.612 | 1.606 |
| Games with no meaningful permanent | 2 | 1 |
| Unearth casts | 74 | 57 |
| Unearth stranded copies (copies / games) | 8 / 6 | 6 / 5 |
| Unearth → Glasswright | 10 | 9 |
| Unearth → Flamebreather | 27 | 21 |
| Unearth → Shambling Ghast | 12 | 8 |
| Unearth → Voldaren Epicure | 25 | 19 |
| Glasswright entries | 112 | 111 |
| Fresh Craft permissions from Unearthed Glasswrights | 10 | 9 |
| Craft casts | 80 | 80 |
| Batshit Flamebreather casts | 95 | 95 |
| Batshit Flamebreather triggers / resolved damage | 202 / 194 | 185 / 178 |
| NDAA casts / successful rescue triggers | 42 / 15 | 38 / 14 |
| Village Rites casts | 32 | 32 |
| Fanatical Offering casts | 73 | 72 |
| Makeshift Munitions casts / activations | 19 / 48 | 18 / 55 |

Across games containing an Unearth return of Flamebreather, subsequent aggregate Batshit
Flamebreather output was 73 created triggers/68 resolved damage for C and 53/51 for E. This is a
game-level post-return measure; the trace does not preserve creature-instance identity, so it must
not be interpreted as output uniquely caused by the returned copy.

After the first Unearth return of Shambling Ghast in a game, later Ghast death choices totaled
4 Treasure and 6 `-1/-1` modes for C, versus 3 Treasure and 6 `-1/-1` modes for E. This is likewise
a post-return association because multiple Ghasts are not instance-distinguishable in the text
trace.

Correct raw Mirkwood Bats activity was:

| Metric | Incumbent C | Variant E |
|---|---:|---:|
| Token-creation triggers | 53 | 54 |
| Token-sacrifice triggers | 49 | 51 |
| Resolved Bats life-loss events | 100 | 103 |
| Created but unresolved at game end | 2 | 2 |

Opposing engine activity was 130/129 Flamebreather casts, 336/346 created Flamebreather triggers,
329/339 resolved Flamebreather damage, 69/67 Guttersnipe casts, 101/104 created Guttersnipe
triggers, and 99/102 resolved Guttersnipe damage (C/E respectively).

## Greedy Freebooter telemetry

E cast Freebooter 18 times. It had 13 deaths: 6 as a sacrifice cost, 6 in combat, and 1 to direct
removal. Twelve death triggers completed their scry-and-Treasure sequence; the thirteenth death was
combat damage after lethal damage had already ended Pair 30, so its trigger did not resolve.

- Scry outcomes: 12 keep, 0 bottom, 1 unresolved after game end.
- Treasure created: 12.
- Freebooter attacks: 17; blocks: 2.
- Opposing removal targeted Freebooter twice: one Lava Dart killed it and one Fiery Temper was
  answered by sacrificing it to Fanatical Offering.
- Sacrifice-cost uses: 2 Village Rites, 2 Fanatical Offering, 2 Makeshift Munitions.
- No Treasure mana activation was logged.
- Three Freebooter Treasure creations generated Mirkwood Bats creation triggers. One immediately
  traceable Freebooter Treasure sacrifice generated a Bats sacrifice trigger; those four triggers
  resolved for four life loss. Later token provenance is not retained once Treasures become a
  fungible battlefield pool, so no additional sacrifice was attributed speculatively.

All 12 visible scry decisions kept the top card. The raw trace records only an opaque object id for
the viewed card, so legality is demonstrated but strategic card-quality review is not possible from
this artifact. This is an observability limitation, not evidence that zero-card selection failed.

## Recursion-versus-body opportunity lines

C made 17 more Unearth casts than E across 16 pairs. Because copies are not instance-labeled and
the arms can diverge downstream, these are line-availability proxies rather than proof that each
cast was specifically physical copy #4. E cast Freebooter in 18 pairs: 15 retained the same winner,
one was an E gain (Pair 54), and two were E regressions (Pairs 33 and 78). The other E gain, Pair 8,
never cast Freebooter.

## Gorge telemetry

| Metric | Incumbent C | Variant E |
|---|---:|---:|
| Gorge appearances | 98 | 97 |
| Entered tapped | 49 | 49 |
| Entered untapped | 49 | 48 |
| Confirmed material sequencing defects | 0 | 0 |

Conservative candidate windows in Pairs 39 and 61 (both arms) and Pair 67 (E) were manually
checked. None reproduced the fixed defect: the trace did not show an available untapped substitute
capable of deploying the delayed one-drop, or meaningful development had already occurred.

## Discordant-pair audit

### Pair 8 — C loss → E win (questionable but defensible; not card-causal)

Both arms had identical visible opening hands and neither cast Freebooter. The first divergence was
Red's Lava Dart timing: E's opponent fired it at end of turn 5, while C's opponent held it until the
turn-6 response window. Later sacrifice and combat ordering diverged, and E won by repeated
Mirkwood Bats attacks on turn 22 versus C dying to Lava Dart flashback on turn 19. The experiment
card never appeared, so the trace does not support attributing this flip to E.

### Pair 33 — C win → E loss (strategically sane; recursion line is causal-looking)

The arms matched through turn 12. C then Unearthed Voldaren Epicure on turn 13, gaining the
Flamebreather trigger, Epicure ping, body, and Blood; E instead developed another Flamebreather and
later Freebooter. C won on turn 21 at 5 life; E died on turn 22 with Red at 1. Freebooter later
blocked and produced a Treasure, but the earlier Unearth value is a direct causal-looking
divergence. Exact counterfactual causation cannot be proven because subsequent choices diverged.

### Pair 54 — C loss → E win (strategically sane; Freebooter line is causal-looking)

The opening hand directly contained Unearth for C and Freebooter for E. E cast Freebooter on turn
10, attacked for one, then sacrificed it to Munitions on turn 12; its Treasure produced a Bats
creation trigger and was immediately sacrificed to Munitions for a Bats sacrifice trigger. That
package supplied five trace-visible damage/life-loss points. E won on turn 16 at 8 life; C used two
Unearth lines but lost on turn 27. This is the strongest E-positive mechanism in the discordant set.

### Pair 78 — C win → E loss (agent-policy problem)

The arms matched through turn 11. E cast Freebooter on turn 12; C retained the corresponding
resource. When Red Bolted Kessig Flamebreather on turn 13, C used NDAA and preserved the engine.
E had black mana and ultimately stranded two NDAA plus one Village Rites, but did not rescue the
same Flamebreather; it instead blocked with Freebooter, received the scry/Treasure, and died on turn
17. C converted the rescued engine and later Bats sacrifice chain into a turn-18 win. The missing
Unearth was not the proximate cost—C itself ended with an Unearth stranded. The failure to use an
available rescue is a strategically suspicious agent-policy decision, not a rules/state failure.

## Disposition boundary

Experiment E produced net zero paired flips and a telemetry-summary failure was found. Per the
experiment declaration, E is not promoted, no replication or Experiment F is started, and Variant C
remains only the provisional incumbent. The permanent original control is unchanged.
