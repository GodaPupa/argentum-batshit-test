# Stage E narrow feature design — atomic exact-card exile action

Status: DESIGN_FROZEN_BEFORE_IMPLEMENTATION
Evidence class: seedless implementation/readiness
Official seeds/games/outcomes: 0 / 0 / 0

## Discovery

The SDK already exposes `CardSource.Self`: a gatherable reference to the ability/spell source regardless
of its current zone. During spell resolution this permits the resolving spell card itself to participate
in a pipeline without inventing a target.

The generic pipeline also documents a critical semantic: `SelectionMode.ChooseExactly(N)` clamps when
fewer than N eligible cards exist. That behavior is correct for ordinary instructions but cannot implement
an all-or-nothing payment/action such as Sphinx's Approach.

Therefore no Sphinx-specific stack executor is justified.

## Smallest reusable vocabulary

Add/reuse a generic all-or-nothing exact-card action with these semantics:

- inspect a source collection/zone using a filter and required exact count;
- if fewer than the required count are eligible, the action is infeasible and moves nothing;
- if feasible, the decision-maker chooses exactly the required count when multiple choices exist;
- after the choice, all selected cards move as one successful action;
- the action exposes success to the existing `Gate.DoAction` / success-criterion machinery;
- no downstream payoff executes after decline, infeasibility, or incomplete movement.

Sphinx's Approach can then compose:

1. mandatory DrawCards(2);
2. a MayDecide gate;
3. inside the accepted branch, an all-or-nothing action that:
   - gathers `CardSource.Self` and moves the resolving source to exile;
   - chooses exactly four cards named Sphinx's Approach from the controller's graveyard and moves them
     to exile;
   - is atomic with respect to feasibility, so the source is never exiled if four graveyard copies
     cannot also be paid;
4. only on successful action, search library for a Sphinx creature -> battlefield -> shuffle.

Implementation may choose a single generalized effect rather than a pipeline if atomic rollback cannot be
guaranteed by existing continuations. It MUST remain generic over zone/filter/count and must not encode the
name Sphinx's Approach in engine code.

## Required regression behavior

- 3 eligible graveyard cards: no partial exile, no search.
- 4 eligible: exact four + source can be exiled.
- 5+ eligible: player chooses exactly four; unchosen copies remain.
- source never counts toward graveyard requirement.
- draw-two occurs before feasibility.
- draw-two can change target availability but not the graveyard count retroactively.
- decline moves none of the optional-action cards.
- interruption/decision continuation resumes the same resolution with no priority injection.
- successful source exile prevents normal graveyard placement after resolution.
- failed/declined optional action leaves normal spell cleanup intact.
- search may find zero cards and still shuffles after a successful action.
- Tolarian Terror generic reduction observes the post-exile graveyard immediately.
- zone conservation and event ordering are deterministic.

This design freeze consumes no experimental seed and exposes no gameplay outcome.
