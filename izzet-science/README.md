# Izzet Science?! — PDH Experimental Lab

Commander: Izzet Guildmage

## Objective
Build a high-decision-density Izzet Pauper EDH combo-control deck that feels mechanically distinct from blink/ETB, graveyard, combat, and artifact-Tron projects.

## v0.1 hypotheses
- 36 lands is the initial control.
- Blue-heavy mana base to support High Tide.
- ~10 nonland mana sources so Dramatic Reversal can function as a real engine.
- Primary deterministic kill: Izzet Guildmage + Lava Spike + Desperate Ritual.
- Secondary engine: Izzet Guildmage + Dramatic Reversal + sufficient nonland mana production.
- High Tide/Snap/untap package remains experimental.
- Avoid Peregrine Drake/Ghostly Flicker as a primary package to keep this deck distinct from Lilysplash.

## Design rule
Except for compact deterministic combo necessities, cards should have useful standalone roles. Avoid dead-card combo soup.

## Core candidates
### Arcane / Ritual
- Desperate Ritual
- Lava Spike
- Ideas Unbound
- Eye of Nowhere

### Mana engine
- Dramatic Reversal
- High Tide
- Snap
- Hidden Strings

### Search
- Muddle the Mixture
- Merchant Scroll
- Dizzy Spell
- Drift of Phantasms

### Selection
- Ponder
- Preordain
- Brainstorm
- Consider
- Opt
- Impulse
- Treasure Cruise

### Mana infrastructure
- Everflowing Chalice
- Fellwar Stone
- Star Compass
- Sky Diamond
- Mind Stone
- Network Terminal
- Bonder's Ornament
- Ur-Golem's Eye
- Sisay's Ring

## Test plan
1. Produce exact legal 100-card v0.1.
2. Compare 35/36/37 land configurations.
3. Compare nonland-mana counts around the 10-source control.
4. Measure opening-hand keepability and colored-source availability.
5. Track effective mana turns 3–6.
6. Track dead combo-piece frequency and tutor connectivity.
7. Track earliest and median assembled win opportunities.
8. Repeat with Guildmage effectively unavailable to measure commander independence.
9. Preserve v0.1 before testing challengers.

## Status

Accepted control: `izzet-science/v0.7-control.md`, SHA256
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
It preserves the v0.6 shell and replaces its 26 ordinary basics with Snow-Covered
equivalents after a paired 100,000-game confirmation.

Latest gate: v0.8-A Mizzium Skin (`-1 Turn Aside, +1 Mizzium Skin`) was rejected as
inadmissible after its sole pilot exposed outcomes but failed before provenance
artifact completion and revealed two ability-event modeling defects. It cannot be
rerun or rehabilitated. A subsequent seed-free deterministic gate proved that a
legal primary-combo launch can distribute 29 retargeted copies plus the original
across three 30-life opponents. This adds no sampled performance claim and changes
no cards. v0.7 remains the accepted control.

Current gate: v0.9 Phase 0 validated seed-free commander-independent readiness
semantics for Murmuring Mystic, Rolling Thunder, Kaervek's Torch, and buyback
Capsize. No sampled baseline, challenger, or backup-plan win-rate claim is yet
authorized.

Phase 1 now freezes passive T1–T10 readiness telemetry for one control-only
10,000-game pilot. Run #181 passed with complete audited artifacts. At T10, Mystic
was castable in 16.00% of trajectories, Rolling Thunder had positive X in 16.85%,
Kaervek's Torch in 16.42%, and Capsize had buyback mana in 15.39%. Conditional on
being present, all four were mana-ready at least 90.96% of the time, making access
the clearer next bottleneck. This is readiness evidence, not a backup-plan win rate;
v0.7 remains the accepted control.
