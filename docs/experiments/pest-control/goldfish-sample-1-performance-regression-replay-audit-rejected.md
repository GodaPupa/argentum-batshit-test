# Pest Control v1.0 — same-seed regression replay audit

## Disposition

**REJECTED in full.** The replay corrected Game 15, and every automated invariant passed, but manual
review found a residual general pure-lifegain policy defect in Game 8. No game was removed, replaced,
rerolled, repaired, or replayed individually.

- Corrected-policy head: `132ffbc83473c950a74cff7800f9e8085a7c6e50`
- Original rejected-performance head: `23da31d777e3e1cd45f9655295565f2fd1a59662`
- Seed-vector SHA-256: `c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`
- Canonical machine-readable replay JSON SHA-256:
  `59a7fae5c52c50b3923846db23a3fe32ff94c7f960bef916cf145df664f139dd`
- Generated replay Markdown SHA-256 before the disposition banner:
  `c51f5464ccfa719e95a97c27e11e3c50c6b1c5f99f385627de305572094083ac`
- Execution: every frozen seed exactly once, unchanged and in original order
- Automated audit errors: none
- Deck, policy, telemetry, and seed changes during execution: none

The original Sample #1 remains formally rejected performance evidence. This replay is regression
evidence only and does not rehabilitate the original execution or any aggregate it produced.

## Primary target — Game 15

Game 15 (`0x4F16A05932EB13EE`) no longer follows the rejecting sequence. On turn five the agent casts
Weather the Storm at Storm 0 and 21 life, then immediately casts Follow the Lumarets in enhanced
mode. No Food is activated. Although no Researcher/Mascot or survival requirement exists, Weather
now has a concrete same-turn purpose: it creates the life-gained-this-turn state used by the
materially superior Follow mode. The original strategically null Weather → Food → later normal
Follow chain does not recur.

## Rejecting defect — Game 8

Game 8 (`0x156CDEC700E5B272`) exposes the same resource-valuation gap across a stack window:

1. On turn seven, at 21 life and with no Researcher/Mascot or opposing survival pressure, the agent
   casts Weather the Storm at Storm 0 while Follow is available.
2. Before Weather resolves, it activates and sacrifices Food for another pure lifegain event.
3. Food resolves first, then Weather, and Follow is subsequently cast in enhanced mode.

The event order is decisive: the trace records Food's life event before Weather's, consistent with
Food being activated above Weather on the stack. Weather already guaranteed the only relevant
state transition—enabling enhanced Follow. The Food event had no Researcher/Mascot payoff, no
survival role, no additional Follow enhancement, and no useful Storm contribution. Spending the
Food and mana therefore had no concrete strategic utility.

The correction at `132ffbc…` checks whether life has already been gained in the resolved state. It
does not recognize that a pending controlled life-gain object on the stack will supply that event,
so the Food activation is incorrectly credited as an enabler. This is a residual general policy
defect. No correction is attempted under this replay authorization.

## Complete audit

All Weather, Food/pure-lifegain, and Follow decisions were inspected. Apart from Game 8:

- Weather was supported by a visible Researcher/Mascot payoff, an immediately completed enhanced
  Follow line, or useful preceding-spell Storm sequencing;
- pure-lifegain activations supplied a visible payoff or a nonredundant enhanced-Follow enabler;
- normal Follow was used when Weather-first was not executable or worthwhile, including a line that
  preserved Weather for later Mascot-backed Storm value; and
- immediate and payoff-backed lifegain behavior remained intact.

The complete existing Pest Control audit was also clean:

- no Chainer's Edict or Bone Shards was spent against the empty opponent;
- every Weather original/copy produced the matching separate lifegain event;
- Researcher and Mascot counter triggers matched counters added in every game;
- every Carrier Thrall death created exactly one Scion;
- no Scion mana activation occurred, so provenance correctly reported no production, consumption,
  funded action, or unused mana;
- actionable bottlenecks were relevant and deduplicated;
- Generous Ent decisions were legal and consistent with mana development versus creature value;
- every game had clean mana legality and engine terminal reporting; and
- every serialized `auditErrors` list was empty.

## Gate

The replay is rejected and preserved only as regression-validation evidence. The exact vector is
permanently retired and may never be executed again or used for sampling, optimization, performance
inference, baseline evidence, or variant comparison. The laboratory is not ready to generate a new
Pest Control v1.0 Goldfish Sample #1 vector. Further policy work or execution requires separate
authorization.
