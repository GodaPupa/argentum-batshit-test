# v0.7 Phase 1 — Frozen Pilot Execution Request

Parent: `a013243fa30e118e74a2e0d5eac25d482f9c4d49`
Deck: `izzet-science/v0.6-control.md`
Deck SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Seed: `0x1A22E7001`
Samples: 10,000
Horizon: T1–T10

This is one instrumentation pilot on the unchanged control. It is not a challenger,
does not change deck identity, and cannot promote a card.

Fixed events:

1. An instant noncreature spell counters the original combined Lava Spike.
2. An instant noncreature spell targets Guildmage for removal in response to the
   first copy activation.
3. Guildmage is removed before the combo turn, returned to the command zone, and the
   next cast includes one commander-tax increment.

Threat mana value is fixed at 2 for the conditional `Prohibit` row. `Spell Pierce`
and `Lose Focus` are readiness-only rows conditioned on the opponent not paying 2.
They are never counted as guaranteed protection.

Required integrity checks:

- the default 100,000-game output remains byte-identical to accepted v0.6;
- exactly ten interaction rows are emitted;
- every protected/recovery opportunity is a subset of legal solitaire lethal;
- removal protection is at least stack protection because `Turn Aside` can answer
  the targeted-removal event but not the stack-counter event;
- all loss fields are nonnegative;
- artifact and manifest are nonempty and preserve source, run, seed, sample, deck,
  deck hash, and output digest.

No replacement seed, rerun, pooled estimate, or 100,000-game confirmation is
authorized by this request.

Disposition: `V07_PHASE1_PILOT_FROZEN_PENDING_EXECUTION`
