# v0.9 Commander-Independent Readiness — Phase 0 Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

What can the accepted deck demonstrably do when Izzet Guildmage is unavailable?

This phase freezes exact backup-plan observations before any sampled baseline or
challenger is authorized. It does not label a hand or trajectory a win.

## Phase-0 semantics

At the post-land, pre-spend action window, report only:

1. whether Murmuring Mystic is in hand and castable for `3U`;
2. the maximum legal `X` payable for an in-hand Rolling Thunder at `XR`;
3. the maximum legal `X` payable for an in-hand Kaervek's Torch at `XR`;
4. whether an in-hand Capsize can be cast with buyback for `4UU`.

Goblin Electromancer reduces the generic portion of each instant or sorcery by one.
The exact ready-source enumerator must account for fixed Izzet Boilerworks output,
Izzet Signet conversion, colored rocks and creatures, colorless sources, and summoning
sickness where applicable.

## Required deterministic regressions

- Six ready mana including red casts either `XR` finisher for `X=5`.
- Goblin Electromancer raises that same ceiling to `X=6`.
- Four ready blue sources plus two other sources cast Mystic and buyback Capsize.
- Three ready mana including red casts Rolling Thunder for `X=2`, but cannot cast
  Mystic or buyback Capsize.
- A finisher absent from hand has zero reported damage capacity.
- The accepted control identity and hash validate before the gate runs.

## Prohibited claims

Phase 0 does not model or claim:

- Mystic token creation, survival, attacks, or table damage;
- damage already dealt to opponents;
- Reversal recursion or infinite noncommander mana;
- priority, interaction, prevention, or multiplayer politics;
- a commander-independent win rate.

Any future sampled instrumentation must be separately frozen, preserve all legacy
telemetry, use a fresh preregistered seed, and remain a readiness baseline rather
than a win-rate claim until stateful backup-line execution exists.

## Validation result

The dedicated validator passed with the accepted control hash intact, all declared
regressions green, zero sampled games, and zero outcome claims. The earlier
multiplayer-kill validator also remained green after the harness extension.

Disposition: `V09_PHASE0_SEED_FREE_VALIDATED`
