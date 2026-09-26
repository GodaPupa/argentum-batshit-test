# Sphinx's Approach — implementation checkpoint

Status: ATOMIC_TRANSACTION_IMPLEMENTATION_READY
Branch: `sphinx-approach/reconstruction-stage-e`
Base feature contract: `ATOMIC_TRANSACTION_FEATURE_CONTRACT.md`

## Source audit completed

The engine's zone module and continuation architecture have been inspected at the exact branch state.

Confirmed reusable surfaces:
- `ZonesExecutors` is the correct registration module for the new effect.
- `ZoneTransitionService.moveToZone` is the authoritative movement path.
- resolution-time exact card choices use `DecisionHandler.createCardSelectionDecision`.
- paused selections resume through serializable `AnswerContinuation` records and a continuation-resumer module.
- existing `ExileMultiZoneContinuation` demonstrates the required decision/resume shape.
- `CardSource.Self` already provides a generic reference to the resolving source card.

No AI, stack-resolver, seed, assignment, or gameplay-driver modification is required for the primitive.

## Implementation boundary

The first implementation patch is limited to:
1. one serializable generic SDK effect;
2. one zone executor;
3. one serializable selection continuation;
4. one continuation-resumer registration/handler;
5. executor-coverage and deterministic atomicity tests.

The executor must validate all source/candidate preconditions before moving anything. If a selection pause
is required, the resumer must revalidate source zone, selected count, selected membership, current source
zone for every selected card, and the filter before committing any move. Stale or invalid responses fail
without partial movement.

Only after this generic gate passes will the branch add Sphinx's Approach, Goliath Sphinx, and Snap.

## Experimental counters

Official Stage-E seeds generated/consumed: 0 / 0
Official Stage-E games initialized: 0
Outcome exposure: 0
