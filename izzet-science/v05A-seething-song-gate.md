# v0.5-A Gate — Seething Song Launch Challenger

Parent control: Izzet Science?! v0.4 exact 99.

Single-slot change:
-1 Hidden Strings
+1 Seething Song

Rules/model gate:
- The baseline Spike + spliced Desperate Ritual + cast Desperate Ritual route
  requires five initial mana including three red.
- Seething Song may resolve before the combo sequence. Paying 2R produces RRRRR,
  exactly funding the two pre-copy combo casts; the cast Ritual then funds the first
  Guildmage activation.
- Goblin Electromancer, if already on the battlefield, reduces Seething Song's
  generic cost as well as both generic portions of the baseline combo casts.
- Izzet Signet activation and Izzet Boilerworks' fixed U+R output remain included
  in the launch payer.

Paired execution:
- Control: `izzet-science/challengers/v04A-strategic-planning.md`
- Challenger: `izzet-science/challengers/v05A-seething-song.md`
- Samples: 100000 per deck
- Seed: `0x1A22E7001`
- Horizon: T1-T10
- No rerolls or seed replacement.

Promotion gate:
- Primary: cumulative lethal and first-lethal increments T3-T10.
- Supporting: pair+commander-ready to lethal conversion.
- Guardrails: pair acquisition, commander deployment, U/R/UU execution,
  High Tide productivity, and Dramatic Reversal thresholds.
- A run with empty output, failing regressions, identity mismatch, missing artifact,
  or non-green workflow is rejected.

Disposition: V05A_AUTHORIZED_NOT_EXECUTED
