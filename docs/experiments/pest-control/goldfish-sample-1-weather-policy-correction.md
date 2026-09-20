# Pest Control fresh Sample #1 — Weather-policy correction

## Disposition

Fresh Goldfish Sample #1 remains formally rejected. Its 30 frozen seeds and complete results are
preserved unchanged as diagnosis evidence only. They were not replayed, substituted, rerolled, or
used to infer performance while making this correction.

## Reproduction and root cause

Deterministic reproductions of the null Weather the Storm behavior seen in games 1, 21, 22, and 23
showed that the one-ply strategist's ordinary board score rewarded raw life even when the opponent
presented no pressure and no object consumed the individual lifegain events. The actions were legal,
but spending a card and mana for otherwise unused life was strategically null.

## General correction

The agent now identifies a pure lifegain spell from its selected face's resolving effect leaves. It
holds the spell below passing when all of these are absent:

- visible lethal pressure requiring life immediately;
- a repeatable permanent triggered by its controller gaining life; and
- an affordable follow-up newly enhanced by life having been gained this turn.

The classification is intentionally strict. Any draw, recursion, token, drain, or unknown rider means
the spell is not pure lifegain and therefore is not suppressed by this policy. Storm changes the
number of otherwise pure resolutions; it does not manufacture a strategic payoff by itself.

The payoff query now uses a general intent tag derived from a permanent's controller-lifegain trigger.
It consequently recognizes counter payoffs and opponent-life-loss payoffs without naming Blood
Researcher, Pest Mascot, Marauding Blight-Priest, Weather the Storm, or Pest Control.

**SHARED ARGENTUM CHANGE: yes.** The implementation changes only generic agent intent and action
valuation. Production rules, card definitions, Gym behavior, telemetry, and the frozen deck are not
changed.

## Deterministic coverage

- hold pure lifegain at low life when the opponent has no pressure and no payoff is visible;
- hold redundant Storm lifegain with no pressure or event payoff;
- cast pure lifegain when Blood Researcher converts the event;
- preserve existing Researcher/Mascot and Blight-Priest Storm-event/lethal behavior;
- cast immediately when visible opposing power makes life necessary for survival;
- cast lifegain when it unlocks an executable enhanced Follow the Lumarets; and
- do not suppress Pulse of Murasa when its recursion rider is concrete.

Direct intent tests pin the shared lifegain-payoff tag for Blood Researcher, Pest Mascot, and
Marauding Blight-Priest, verify Essence Warden is an engine rather than a payoff, and distinguish pure
Weather the Storm from rider-bearing Pulse of Murasa.

## Gate

No seed execution is part of this correction gate. All 46 focused Pest Control agent decisions, the
full structural-intent analyzer suite, and the full local AI suite passed. The exact frozen-deck/vector
assertion passed while every opt-in goldfish execution was skipped. Remote CI run 202 is fully green
on implementation head `6c13b8fd100040d60a0a0b33b66d7b484281e9d7` across frontend, engine,
all scenario partitions, content, tools, server, and the aggregate backend gate.

The named Argentum Validation workflow is configured for `main` pushes or manual dispatch and could
not be dispatched through the available branch connection. Its monolithic local task graph is blocked
before test execution because this offline container lacks Byte Buddy 1.10.9 and
kotlinx-serialization-core 1.9.0. Production code and test semantics were not changed for that
container-specific limitation; the clean remote CI environment is authoritative.

The laboratory stops awaiting separate authorization. It does not construct the challenger, optimize
v1.0, run opponent self-play, generate Sample #2, or automatically replay either rejected vector.
