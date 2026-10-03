# Replicate: triggered spell-copy lifetime prerequisite

Base: ae209a6f0897f79c5379257091bd36663e0fcf00 (51 registered / 4 missing).
No card definition, production source, or inventory contract is changed by this isolated diagnostic.

The existing reusable rail is `StormCopyEffect` -> `StormCopyEffectExecutor` ->
`StackResolver.putSpellCopy`. Conspire and Casualty already use it. A fixed synthetic
copy count tests that rail; it is not a substitute for announcing and paying replicate.

The focused four-case probe includes zero-copy, two-copy/X/no-cast-event, and
independent-copy-counter controls. Its required counter-before-trigger case calls the
real `StackResolver.counterSpell`, then executes the same copy effect. The original
is legitimately gone from the stack while its independently resolving trigger remains.

## Source trace

- `CastSpellHandler` creates Conspire/Casualty/Storm pending copy triggers with source
  object references and `StormCopyEffect` containing count, effect, requirements, name.
- That SDK effect has no complete copiable spell snapshot (X, modal choices, selected
  target identity, spliced effects, alternative/additional-cost choices).
- `StackResolver.counterSpell` removes `SpellOnStackComponent` and `TargetsComponent`.
- `StackResolver.putSpellCopy` requires those live source components and errors when
  the original is no longer a spell on the stack.
- `StormCopyTargetContinuation` / modal continuation also retain a source ID rather
  than a complete historical spell. Fixing only the no-target branch would not repair
  copy retargeting after source departure.

The generalized repair must retain the copiable spell state of the correct stack
object through trigger creation, countering/other stack exits, serialization, and all
copy-target continuations. It must not resurrect the original, copy a later object
with the same entity ID, or turn spell copies into triggered abilities. This is a
separate requirement from the already-demonstrated repeated optional-cost boundary.

## Remaining replicate contract

Repeated payments must be announced before total-cost determination; each replicate
instance needs its own durable count. The count feeds an independent cast trigger,
whose copies reuse the corrected spell-copy rail and may retain or change targets.
Original spell and copies remain independently counterable; copies are not cast.

Authoritative rules consulted: Wizards Comprehensive Rules effective 2026-09-25,
702.56 (replicate), 707.10 and 707.10c (copied decisions and optional retargeting).
https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt

The repeated-cost failures remain preserved in run 37108062427; they are not rerun here.
This diagnostic cannot admit Lose Focus or change 51/4. Official deltas remain
0 allocations / 0 claims / 0 games / 0 outcomes.
