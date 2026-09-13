# Pest Control — Game 15 lifegain-resource policy correction

## Scope and disposition

The executed Pest Control v1.0 Goldfish Sample #1 is formally rejected at remote head
`23da31d777e3e1cd45f9655295565f2fd1a59662`. Its 30-seed vector, SHA-256
`c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`, is permanently
disqualified from performance/baseline evidence and may never be used for sampling, optimization,
performance inference, or variant comparison.

This correction used deterministic scenario positions only. It did not execute any seed, rerun or
replace any rejected game, generate a replacement performance vector, construct the challenger,
optimize the control, or begin opponent self-play.

## Root cause

Game 15 exposed one general policy scope gap across three linked decisions:

1. Raw life has a positive board-evaluation value. The existing null-lifegain guard corrected that
   bias for structurally pure lifegain spells when no survival need, visible repeatable event payoff,
   or executable enhanced follow-up existed.
2. The guard accepted only spell-cast actions. A printed activated ability whose only resolving
   effect was life gain bypassed it, so spending a permanent and mana for irrelevant life could
   still outrank passing.
3. After Weather had already supplied the turn's enabling life event, the ungated Food activation
   consumed the remaining resources and displaced the enhanced Follow continuation. The later
   normal Follow was the downstream symptom of failing to preserve and complete the concrete line.

This is not a Weather, Food, Follow, or Pest Control card-name defect. It is a generic mismatch
between effect classification and the action types subjected to strategic result valuation.

## General correction

The intent catalog now classifies a printed activated ability as pure lifegain only when every leaf
of its resolving effect is life gain. The strategist applies the same general null-resource gate to
pure lifegain casts and activations:

- immediate survival pressure preserves an emergency activation or cast;
- a visible repeatable lifegain-event payoff preserves the event;
- a newly executable life-gained-this-turn enhancement preserves the enabling event;
- otherwise, the legal resource conversion is scored below passing;
- any spell or ability with a concrete non-life effect leaf remains outside the gate.

This makes opportunity cost explicit for cards, mana, and sacrificed permanents without prohibiting
Storm 0 or lifegain categorically. Existing general sequencing policy remains responsible for useful
pre-Storm spells and for following a concrete enabling event with the enhanced spell.

**SHARED ARGENTUM CHANGE: yes.** The production change is confined to the generic AI intent catalog
and strategist. Rules, engine, Gym, telemetry, cards, deck construction, and mulligan policy are
unchanged.

## Deterministic regression matrix

New coverage proves:

- an enabling pure life event is followed by the materially enhanced follow-up;
- once the enhancement is enabled, another pure lifegain activation does not displace it;
- a pure activated lifegain resource is preserved when life has no concrete utility;
- normal Follow remains correct when the life event cannot be profitably sequenced first; and
- pure activated lifegain is identified structurally while a draw activation is excluded.

Existing green coverage continues to prove:

- high-life/no-pressure/no-payoff Weather is held;
- survival-required Storm-0 Weather is cast;
- payoff-driven Storm-0 Weather is cast;
- a useful preceding spell is sequenced before Weather when its Storm copy matters; and
- an otherwise poor spell is not cast merely to increase Storm count.

The complete focused Pest Control decision suite, structural intent suite, and full AI suite pass.
No same-seed replay has been performed. Once full CI is green, the corrected policy is ready for a
same-seed regression replay only after separate explicit authorization.
