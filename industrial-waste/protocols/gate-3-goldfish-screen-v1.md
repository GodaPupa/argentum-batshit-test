# Gate 3B: paired metric goldfish screen v1

Status: authorized small diagnostic screen; not promotion evidence.

## Design

- Namespace: `IW-G3-GOLDFISH-S1`
- 12 fresh deterministic seeds paired across frozen Control and Pactdoll-A
- 24 games total; Industrial Waste is always seat 0
- Opponent: 60 Forest with the same inexpensive v0 agent
- London mulligans enabled
- Cap: 12 turns per seat or 4,000 submitted actions

This is the smallest executable sample capable of checking whether the structural-model signal
survives real draws, mulligans, mana payment, triggers, and combat. It cannot estimate matchup win
rate and cannot promote a deck.

## Metric definitions

- Tron turn: first own turn with Mine, Power Plant, and Tower controlled.
- Colored-mana failure turn: first quiet precombat-main snapshot of an own turn where a colored
  spell in hand is unaffordable at its printed cost but affordable after replacing its pips with
  the same amount of generic mana.
- Retriever loop availability: Altar plus one Retriever on the battlefield and another in the
  graveyard.
- Combo-ready: Retriever loop availability plus Pactdoll Terror or Golem Foundry on the battlefield.
- Redundant payoff draw: a post-opening draw of Pactdoll Terror or Golem Foundry while another
  payoff is already in hand or on the battlefield.
- Non-infinite Pactdoll life loss: opponent life loss while Pactdoll is present and the Retriever
  loop is not available. This attribution is valid only because the opponent has no life-loss cards.
- Fair-game proxy: combat damage dealt to the inert opponent. This is not a fair-game win metric.

Recovery after interaction and actual fair-game win capability are deferred to opponent-bearing
gates; both are reported as unmeasured rather than inferred from goldfish data.

## Validity and escalation

Any exception, rejected action, non-cap wedge, seed drift, or list drift invalidates the run. A
valid sample is reviewed only for an escalation signal. Pactdoll-A may earn a larger fresh-seed
goldfish replication if it preserves Control's assembly/loop profile while improving at least one
of mulligan burden, colored-mana failures, lethal conversion, or non-infinite Pactdoll output.
No result from this 12-pair screen can promote the challenger.
