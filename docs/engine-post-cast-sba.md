# State-based actions before post-cast priority

Status: **SOURCE REVIEWED; RUNTIME QUALIFICATION PENDING**.

This prospective successor extends the canonical priority implementation at
`21e9f2a82ea9d5292054b937456396e00060eb8d` ([PR #154](https://github.com/GodaPupa/argentum-batshit-test/pull/154)).
It preserves that source's post-resolution repair, accepted-main integration, 17 focused
resolution cases and six reviewed fixture corrections. The separate source manifest records
the prior manifest digest and new exact source hashes; past artifacts keep their original pins.

## Observed defect and required behavior

Pest postboard B's exact Gut Shot scenario paid two life from a total of two, completed the cast,
and returned priority without losing the game. The first rejected source was
`059bf9f548813201762e8579fe66018d02494526`, run
[36089250333](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36089250333),
artifact `10845845066`, ZIP SHA-256
`e0dfaa26360ec85e503558befe1f2cc4610ca212288e98adb572a8fe1de31dc7`.
The source and retained failure audit remain in [PR #152](https://github.com/GodaPupa/argentum-batshit-test/pull/152).
This failure is a rules defect, not an official deck loss. No sampled game was executed by this gate.

The effective [Comprehensive Rules](https://magic.wizards.com/en/rules) distinguish completing a
cast from giving the caster priority. Rule 117.3c preserves the acting player as the recipient;
117.5 and 704.3 require state-based actions, waiting trigger placement, and repeat checks first.
The archived 2026-09-25 document is bound by URL, size and SHA-256 in
`engine-priority-after-resolution-sources.json`. The workflow independently downloads those
bytes, verifies their identity and refuses a future-effective date.

## Implementation and review decisions

`CastPriorityProcessor` owns ordinary completed casts. It captures cost/cast triggers before
state-based actions, waits through any state-based choice, combines the waiting batches in
active-player/nonactive-player order, places them, and repeats the checks before returning the
saved caster's priority. Triggers stay in `PendingCastPriority` while a state-based choice is
pending; they cannot drain prematurely beneath that choice's continuation. Existing processed-event
flags prevent re-detection of events that a resumed cast already captured.

The appended nullable state payload is omitted from ordinary serialized states. It retains the
caster and captured triggers across serialization. Creature-type casting choices retain earlier
cost triggers in an appended, default-omitted continuation field before entering the same boundary.
Completed synchronous casts keep their existing event behavior. A resumed boundary emits exactly
one actual `PriorityChangedEvent` when it completes.

An enclosing stack resolution continues to own its priority boundary. A free cast made during
that resolution does not run an intervening post-cast state-based check; later effects in the same
resolution can therefore restore life before the next priority boundary. The two state markers
have distinct ownership, and terminal completion clears both.

Concession during a pending post-cast question first runs the existing decision-free team-loss,
immediate departure and game-end checks. This avoids asking a commander-zone question again before
the departing owner has been processed. A surviving question, its continuation frames and captured
triggers remain intact. A departed chooser's abandoned question can finish settling for the
surviving caster. Terminal concession clears pending work and priority while preserving the
existing end-of-game board snapshot. Global state-based-action ordering is unchanged.

These changes were reviewed independently against the complete source and distinguishing
fixtures. Review corrected combined-batch ordering, consistent commander ownership, preservation
of an unrelated pending question, and terminal cleanup before publication. This review is a
prerequisite for CI, not a runtime pass.

## Deterministic qualification contract

The **12 focused runtime cases** exercise:

- Actual Phyrexian payment at two life and at three life, with no spell resolution before priority.
- A last-life caster losing before a cast-trigger target question can be offered.
- A cost-sacrificed toughness provider causing a second death before trigger targeting, with both
  death triggers and the cast trigger retained exactly once.
- A nonactive caster's trigger and the active player's state-based death trigger entering one
  correctly ordered waiting batch.
- A commander choice answered by a different owner, abandoned by that owner's concession, or
  retained through a third player's concession, including serialization and the saved caster.
- A terminal concession during a commander question, clearing frames and both markers while
  retaining the established ending board and emitting each loss/end event exactly once.
- Creature-type casting completion after the caster paid their last life.
- A nested modal life payment recovering during the remainder of its outer resolution.
- An unaffordable life payment rejected atomically and omission of the new field from default state.

The existing dedicated workflow retains all **16 predecessor stages** and adds eight stages:
the new suite, free casting, modal additional costs, modal cost enumeration, revealed-card costs,
commander-zone choices, Two-Headed Giant team loss and Team vs. Team. Every stage retains its XML
and exit status before the next invocation, including failures. All 24 stages and full repository
CI must qualify the exact candidate. The inherited Spy card-support gates remain enabled.

No assertion is disabled, golden reblessing mode enabled, or source guard skipped. Any unexpected
fixture failure must be audited against its actual state and event trace. The original Gut Shot
last-life assertion remains a required independent integration check in postboard B.

One inherited commander interaction remains unqualified: the global checker orders the optional
commander-zone choice before departure and game-end processing. A cast that both pays the player's
last life and sacrifices a commander therefore needs its own distinguishing qualification before
an exact multiplayer pilot can rely on that combination. The scoped concession path above does
not change this global order. The present fixtures establish the listed component behaviors and
do not establish complete Commander or Pauper Commander readiness.

## Experimental boundary

This source changes rules execution. Each project must qualify its exact decks and policies on
the successor before freezing official gameplay; no old result is transferred or requalified.
The 81 historical Pest games remain under their recorded quarantine disposition. Monster's
original consumed claim and four retired assignments remain unchanged.

The accompanying [replacement R1 proposal](experiments/pest-control/tier-one-monster-tron-replacement-smoke-r1-proposal.md)
is prospective protocol review only. It creates no vector, claim, C2 execution source or A2
activation, and requires separately recorded gates before any new official execution.

All official counters for this source qualification are **zero**: seeds generated, game
initializations, submitted sampled actions and outcome exposure. Runtime evidence and project
integration remain pending; no program stopping rule is satisfied by this source proposal.
