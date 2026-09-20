# Gate 2: Argentum engine smoke v1

Status: authorized diagnostic capability screen; not promotion evidence.

## Question

Can the exact frozen Control and Pactdoll-A maindecks complete bounded Argentum
game loops without engine exceptions or rejected AI actions?

## Design

- Namespace: IW-G2-ENGINE-SMOKE-V1
- Four fresh deterministic seeds, paired across both deck identities
- Eight games total
- Industrial Waste always occupies seat 0
- Opponent: 60 Forest, using the same inexpensive v0 agent
- Mulligans skipped
- Cap: 12 turns per seat or 4,000 submitted actions

The opponent and AI are capability instruments, not a metagame model. A win,
loss, or turn count from this gate must not be cited as deck-strength evidence.

## Acceptance

Every game must finish its bounded loop with:

1. no engine exception; and
2. no rejected action recovered by the arena fallback.

Incomplete games at the turn cap are recorded rather than treated as deck
losses. Any exception, rejection, seed drift, list drift, or missing card
definition invalidates the run. Passing authorizes design of a metric-aware
goldfish harness; it does not promote Pactdoll-A.
