# Lilysplash Mentor Competitive PDH Experiment

## Guardrails

- Preserve `submitted-v0.1.txt` exactly as the user's submitted deck.
- Preserve the legal submitted count: 99 deck cards plus the commander.
- Do not report gameplay performance from placeholder or partially implemented cards.
- Prove every combo component and win conversion with focused deterministic scenarios first.
- Freeze deck identities and ordered seed vectors before each execution.
- Compare control and challenger on identical seeds; no rerolls or replacement seeds.
- Keep card/rules implementation separate from agent-policy corrections.

## Readiness gate

The minimum executable combo suite is:

1. [x] Lilysplash Mentor activation, sorcery-speed restriction, exile/return, and counter placement.
2. [x] Peregrine Drake and Cloud of Faeries untap behavior.
3. [x] Ghostly Flicker targeting and simultaneous return behavior.
4. [x] Archaeomancer and Mnemonic Wall graveyard recursion.
5. [x] Sage's Row Denizen as deterministic mill conversion.
6. [x] A legal infinite-mana conversion through Sage's Row Denizen and the opponent's next draw.
7. [ ] Commander-zone setup, singleton deck validation, and commander color identity.

## Experimental stages

### Stage 1 — Control freeze

- Use the submitted 99 unchanged as the control.
- Freeze the exact list and hash it before execution.

### Stage 2 — Deterministic line tests

- [x] Lilysplash + Peregrine Drake produces two mana per cycle with five basic lands.
- [x] Lilysplash + Cloud of Faeries loses one land activation on basics, but produces one mana per
  cycle when New Horizons and Fertile Ground each make an untap target produce two mana.
- [x] Ghostly Flicker + Peregrine Drake + Archaeomancer/Mnemonic Wall recurs correctly.
- [x] The Archaeomancer line nets two mana and mills four cards per cycle with Sage's Row Denizen,
  then wins when the opponent next draws from the empty library.
- [ ] The Lilysplash-based mana loops each reach an explicit deterministic win condition.
- [x] Removing the only Lilysplash target in response makes the activation fizzle.
- [ ] Interaction tests cover counterspells and commander removal.

### Stage 3 — Paired goldfish preflight

Measure, per game:

- mulligans and opening colored sources;
- first commander-cast turn;
- first engine-value activation;
- first tutor/cantrip access to a missing combo role;
- first deterministic-win turn;
- mana bottleneck and stranded-card reason;
- protection available on the winning turn;
- dead or redundant cards drawn before the win.

Use a small fixed seed block to audit execution and telemetry. Reject the block if the agent misses an
executable materially superior line or if any card is approximated.

### Stage 4 — One-variable challengers

Test packages separately before combining them:

1. cheaper selection and tutors;
2. reduced four-mana Aura density;
3. additional untapper/Freed redundancy;
4. increased stack protection;
5. cleaner win-condition density;
6. land-base speed versus bounce-land/enhanced-land combo value.

Only promote a challenger when its paired traces show a general improvement rather than seed-specific
luck. The final optimized list is assembled from accepted packages and then receives its own fresh,
frozen validation sample.
