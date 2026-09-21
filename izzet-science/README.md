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

Phase 2 audits the accepted control's existing tutor connectivity to those backup
cards without changing tutor-use policy. It is a seed-free legal-semantics gate:
Dizzy Spell can find either X-spell, Drift of Phantasms and Merchant Scroll can find
Capsize, and no current tutor can find Murmuring Mystic. The seed-free validator
passed with zero policy changes, samples, seeds, or outcome claims; no card change
is authorized by this audit.

Phase 3 freezes passive `targetable`, `payable`, and primary-combo-`uncontested`
tutor-opportunity fields. It is an instrumentation preflight only: tutors remain
unspent and the existing policy remains unchanged. Deterministic trajectory
equivalence and aggregate invariants passed with zero samples or seeds. No sampled
pilot or fresh seed is yet authorized.

Phase 4 freezes a single 10,000-game control-only pilot contract for both readiness
and passive tutor-opportunity telemetry. Run #184 passed with complete independently
verified artifacts. At T10, payable tutor opportunity was 3.05% for Rolling Thunder,
2.88% for Kaervek's Torch, and 24.34% for Capsize; Capsize's entire opportunity was
primary-combo-uncontested. This remains passive evidence, not executed acquisition.
Seed `0x1A22E700F` is consumed and no rerun is authorized. v0.7 remains the control.

Phase 5 freezes executable but dormant Capsize tutor semantics. The existing
primary-combo tutor action has global precedence; otherwise Merchant Scroll is
preferred over Drift of Phantasms when Capsize is in the library and exact ready
mana can pay the search. Deterministic execution, failure atomicity, and adjacent
validators passed with no sampled games or seeds. The default simulator remains
unchanged, and v0.7 remains the accepted control.

Phase 6 adds an explicit opt-in activation path and four fail-closed Capsize tutor
event fields. A fixed ordered-deck fixture acquires Capsize exactly once on turn two;
the disabled path remains exactly identical to the default. No sampling is yet
authorized: the batch simulator's shared random stream could let an extra policy
shuffle perturb later games. Per-game paired RNG isolation is the next required
seed-free gate. v0.7 remains the accepted control.
