# Project X Optimization Experiment A protocol

## Frozen treatments

Control is Project X v0.2 exactly, with no sideboard. Variant A makes exactly these changes and no others:

```text
-1 Falkenrath Noble
-1 Masked Vandal
+2 Llanowar Elves
```

All four Evolution Witness are preserved. Variant A must not be promoted automatically.

## Structural hypothesis

Two one-mana Elf accelerators may reduce exactly-three-mana Ivy Lane Denizen stalls and increase coexistence of Carrion Feeder, Safehold Elite, and Ivy Lane Denizen without removing Evolution Witness recursion value. One Falkenrath Noble remains to preserve deterministic Noble lethal.

## Seed rules

- Exactly 30 completely new deterministic seeds, frozen before execution.
- The official vector is `gym/src/test/resources/project-x-optimization-a-seeds.csv` at seed-freeze commit `03ec58f134a9a05da5f3dff9bfe9cb57edfd2fa1`.
- Each seed is used once by control and once by Variant A under identical opening/play conditions.
- Zero overlap with prior Project X/Batshit development, regression, smoke, baseline, performance, matchup, or optimization seeds.
- No rerolls, replacements, exclusions, substitutions, or new vector may be generated.

## Fixed execution conditions

Control and variant use the same seed, starting-player assignment, opening conditions, mulligan policy, solitaire policy, engine, Gym path, horizon, and telemetry definitions. No deck or policy change is permitted after execution begins.

## Primary paired outcomes

- First Ivy Lane Denizen cast turn and paired delta
- Exactly-three-mana Ivy stalls
- Primary Carrion Feeder/Safehold Elite/Ivy Lane Denizen engine assembly
- Any validated infinite assembly
- Combo available before ordinary lethal
- Actual winning-turn delta

## Required supporting telemetry

- Llanowar casts, legal activations, mana produced, and exact funded spells
- Witness casts, Adapt activations, counter source, successful recursion, returned identity, and subsequent deployment
- Noble casts/availability, engine-without-Noble windows, and lost Noble-kill opportunities
- Herald tutors and targets
- Winding Way modes/yields and Lead the Stampede yields
- Birchlore, Nettle, and Quirion contribution
- Combo-role coexistence
- Fair-board size and combat damage
- Corrected color bottlenecks
- Khalni Garden and Haunted Mire tempo events

## Audit and stop conditions

Audit every pair for rules/state, telemetry, mana legality, Winding Way representation, combo recognition, and solitaire-agent sanity. Preserve the raw output and document any reporting qualification. Do not promote Variant A regardless of result.

Stop after preserving and reporting Experiment A. Do not test Llanowar copies three or four, Boreal Druid, Arbor Elf, Giant's Boulder, or matchup self-play.

## Execution record

- Readiness: `bb93886c838f7fa54ce867bbfd49f1c3bb374aaf`
- Seed freeze: `03ec58f134a9a05da5f3dff9bfe9cb57edfd2fa1`
- Execution head: `9875ae913ed5f2ec686dcf0179f697a039e97665`
- GitHub Actions run: `34547803872`
- Outcome: all standard validation stages and all 30 paired games passed
