# Pest Control — Game 8 pending-lifegain policy correction

## Scope and disposition

The corrected same-seed replay is formally rejected at remote head
`538666b012fe9577ecb8e261c95e1357f43767e3`. The original Sample #1 and this replay remain
separate rejected artifacts. Their exact 30-seed vector is permanently retired from every future
execution, including regression, sampling, optimization, performance inference, baseline evidence,
and variant comparison.

This correction used only the preserved Game 8 trace and deterministic scenario construction. It
did not execute a Pest Control seed. The frozen Pest Control v1.0 list is unchanged, and the proposed
challenger remains audit-only and unconstructed.

## Root cause

The general null-lifegain policy evaluated only conditions already present in resolved game state.
When a controlled Weather the Storm was pending, `LifeGainedThisTurnComponent` did not yet exist, so
the one-ply evaluation credited a Food activation with newly enabling the enhanced Follow line.
That ignored the pending Weather resolution, which already guaranteed the same condition. With no
survival need, repeated-event payoff, threshold, extra line, or other result, the Food activation's
marginal strategic utility was zero.

## General correction

The intent catalog can now conservatively identify a fixed, unconditional, controller-directed
lifegain effect carried by a spell or ability on the stack. Conditional, optional, dynamic,
targeted, mixed, or unrecognized effects are never claimed as guaranteed.

Before a pure-lifegain cast or activation is credited as a condition enabler, the strategist totals
only controlled pending lifegain that is genuinely safe to rely on. It declines certainty if an
opponent has hidden hand resources, exposes a counter-capable permanent, or has placed a counter
effect above the lifegain object. The pending amount is also included in the survival calculation:
another life event remains available when pending life alone is insufficient. Repeatable event
payoffs continue to give every separate lifegain event independent value. Non-lifegain actions do
not enter this policy gate.

**SHARED ARGENTUM CHANGE: yes.** The production correction is limited to generic agent intent and
action valuation. No rules engine, Gym, telemetry, card definition, deck, mulligan, or laboratory
strategy changed.

## Deterministic regressions

Focused scenarios prove:

1. Pending guaranteed lifegain already enabling an enhanced follow-up holds the redundant pure-life
   resource.
2. Pending lifegain insufficient for survival permits the additional lifegain action.
3. A repeatable Researcher/Mascot-style event payoff preserves the value of an additional separate
   lifegain event.
4. With no pending lifegain, a pure-life resource can correctly enable enhanced Follow.
5. Disruptable or otherwise uncertain pending lifegain is not treated as certain.
6. An independently valuable non-lifegain resource action remains selectable while a related effect
   is pending.

The complete Game 8 reconstruction starts at 21 life with Weather pending, Follow executable, Food
available, and no survival or repeatable payoff need. The corrected agent passes priority with Food
intact; after Weather resolves, it casts Follow in enhanced mode and still preserves Food.

## Validation gate

The focused Pest Control decision suite, structural intent checks, complete AI suite, frozen-control
assertion, and existing Pest telemetry regressions pass locally. Every opt-in Pest goldfish execution
is disabled and skipped; the retired vector was not executed. Full CI run 209 is green on
implementation head `cbff8feaa80489fc3f2d37b0e9d92f57f93346cf`. The laboratory is at the fresh
Sample #1 seed-readiness gate; generating or executing a new vector requires separate authorization.
