# v0.9 Phase 19 — Opponent Event and Capsize Response Policy Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE19_SEED_FREE_GATE`

## Question

Can future opponent-aware testing make a deterministic, auditable decision to cast
Capsize without silently assigning unsupported value to every opposing permanent?

## Declared event classes

The caller must classify each event from public game state before response
selection. Phase 19 recognizes only:

1. `imminent_loss`: the declared permanent causes a loss before the next response
   opportunity unless answered;
2. `guildmage_combo_removal`: removal is targeting Izzet Guildmage while the primary
   combo is currently lethal;
3. `next_main_lock`: the declared permanent prevents the currently available plan
   from functioning at the next own main phase; and
4. `tempo_only`: every other legal permanent for which value would depend on an
   unfrozen tempo, recast, politics, or matchup model.

The response priority is imminent loss, then lethal-combo Guildmage rescue, then a
declared next-main lock. `tempo_only` never authorizes a cast. Illegal targets are
ignored. Equal-priority targets use stable event identity rather than iteration or
random order.

## Response semantics

- If one-shot Capsize is unavailable, pass.
- Select at most one qualified event in the frozen priority order.
- If buyback is already affordable in this one-response window, use buyback;
  otherwise cast normally.
- Buyback preference assigns no downstream recast value and makes no claim that
  holding six mana is strategically optimal.
- Opposing-commander identity alone creates no response; it must independently meet
  an actionable event class.
- The opposing commander's owner still chooses hand or command zone. Both choices
  preserve the same cast decision and resolution success.
- A countered Capsize or an illegal target at resolution produces neither bounce nor
  buyback retention.

## Gate and exclusions

The validator covers direct resolution fixtures, emergency priority, stable ties,
commander destinations, countered and illegal-at-resolution failures, 84 exhaustive
deterministic policy states, and eight malformed inputs. It consumes no paired
iterator and assigns no experimental seed.

This gate does not define how frequently any event occurs, how an opponent chooses
actions, whether a bounce creates tempo, whether the opponent can recast, how many
responses occur per turn, survival, politics, or match wins. Event classifications
are declarations to be supplied and validated by a later opponent model; they are
not inferred by this policy.

Success authorizes only a later seed-free event-ledger and opponent-policy contract.
It authorizes no sampled pilot, card change, or new behavior-policy promotion. The
accepted Phase-14 tutor policy remains unchanged, and v0.7 remains the exact card
control.
