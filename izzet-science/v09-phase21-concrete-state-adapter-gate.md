# v0.9 Phase 21 — Concrete Public-State Adapter Gate

Control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

Disposition: `V09_PHASE21_SEED_FREE_GATE`

## Question

Can the accepted Phase-20 ledger facts be derived from concrete, decision-time
state predicates rather than renamed Boolean declarations?

## Information boundary

Opponent-event inputs are restricted to public battlefield identity, target
legality characteristics, already-known damage/life-loss/poison consequences before
the next response opportunity, and restrictions that persist through the next own
main phase. The adapter contains no opponent hand, library, future choice, deck
identity, frequency, or sampled event.

The `primary_combo_lethal_now` predicate is necessarily different: it uses the
Izzet responder's own legally known hand, battlefield, and ready mana through the
existing `primary_combo_launch_feasible` function. It does not expose or inspect an
opponent hidden zone. This split corrects the information-boundary terminology
without changing the accepted Phase-19 response priority or Phase-20 ledger schema.

## Derived predicates

- `loss_before_next_response`: known damage plus life loss meets current life, or
  known poison reaches ten, before another response opportunity;
- `targets_own_guildmage`: the observed removal target is the player's battlefield
  Izzet Guildmage and retains commander identity;
- `primary_combo_lethal_now`: the existing exact primary-combo launch predicate is
  true on responder-owned state; and
- `prevents_next_main_plan`: a restriction lasting through the next main phase
  intersects a frozen required object or action tag.

Target class and Capsize legality are also derived from the observed permanent.
Unsupported self-targets, contradictory event kinds and consequences, invalid
thresholds, duplicate plan requirements, malformed identities, and any non-`DevState`
responder input fail closed.

## Gate and exclusions

The validator covers life and poison loss, object and action locks, commander and
ordinary opposing permanents, lethal and nonlethal Guildmage removal, hexproof target
illegality, an end-to-end multi-event response window, 192 exhaustive threshold
states, four plan-intersection states, and fourteen malformed inputs. It assigns no
experimental seed and consumes no paired iterator.

This phase does not define an opponent deck, opponent action selection, event
frequency, future choices, recast behavior, tempo, survival, politics, or win rate.
Success authorizes only a seed-free deterministic opponent action-policy adapter
over this frozen vocabulary. It authorizes no sampled pilot, card change, or
behavior-policy promotion. v0.7 remains the exact card control.
