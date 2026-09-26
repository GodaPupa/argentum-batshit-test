# Pest Control — Postboard flashback support batch

Status: **PROSPECTIVE SEED-FREE CAPABILITY; OFFICIAL GAMEPLAY BLOCKED**

This successor is explicitly downstream of accepted Postboard B on canonical main merge
`d0c78bb4cca79b7402ba65bd62b5cb621230a054`. The accepted Postboard B receipt remains
`docs/experiments/pest-control/tier-one-postboard-support-batch-b-accepted-7d3f0b3.json`.

## Bounded scope

This batch closes exactly two of the seven identities / sixteen physical sideboard slots
remaining after Postboard B:

- **Flaring Pain** — one Spy Combo sideboard slot. Reuses the already-qualified turn-scoped
  damage-prevention shutoff and flashback rails.
- **Acorn Harvest** — one Spy Combo sideboard slot. Reuses the already-qualified flashback rail,
  adds its printed three-life flashback additional cost, and creates two 1/1 green Squirrel tokens.

Expected remaining postboard queue after acceptance: **5 unique identities / 14 physical slots**:
Relic of Progenitus, Spreading Seas, Kaervek's Torch, Jack-o'-Lantern, and Faerie Macabre.

No frozen deck list changes. No boarding plan is frozen by this gate.

## Required deterministic qualification

- Flaring Pain normal cast sets the real turn-scoped prevention shutoff.
- Flaring Pain flashback pays its alternative mana cost, sets the same flag and exits to exile.
- Acorn Harvest normal cast creates exactly two Squirrel tokens and goes to graveyard.
- Acorn Harvest flashback pays {1}{G} plus three life, creates exactly two Squirrels and exits to exile.
- Acorn Harvest flashback is unavailable below three life and changes no state.
- Strict compiled snapshots remain exact with only the admitted JUD/TOR additions.
- Card lint remains green.
- The accepted Postboard B receipt must be present and must still report 81 historical games
  quarantined and zero official seeds/games/actions/outcomes.

The dedicated workflow is seed-free and may not initialize an official matchup, freeze a replacement
Monster claim, generate a seed, expose an outcome, or authorize postboard gameplay.
