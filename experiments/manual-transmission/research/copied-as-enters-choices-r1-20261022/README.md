# Copied as-enters choices on resolving spells

Prepared for qualification: not yet compiled, tested, adopted or a card qualification. The carrier prerequisite passed and its original artifact was independently audited in checkpoint `8ad878688eab3a3e28bcb76913ca4a0759bd4edc`.

The copy resumer currently calls battlefield entry directly and bypasses the copied definition's
EntersWithChoice replacements. This slice routes those choices through the existing spell-choice
suspensions, retaining the original stack object until the last choice completes. A graveyard-copy
exile rider is composed as an existing EffectContinuation beneath the choice, with Self bound to the
original selected object. A new zone visit cannot be exiled by that saved binding. The shared entry
emitter retains the original printed name for copied spells that finish through the choice resumer.

Seven additional generic engine scenarios pin fresh color choices (battlefield and graveyard sources,
with and without full-state serialization), chained color/type choices, decline-with-own-choice, and
a stale graveyard object across save/resume. The donor10 and SDK2 remain untouched. The unchanged new
seven cases must fail for the pinned baseline's missing choices and pass for the repaired source;
compilation failure does not count as a negative control.

## Layer trace

- SDK: no change to data, constructor shapes or serializers.
- Engine: copied characteristics precede the existing choice handler; battlefield entry is deferred
  across the same suspension chain already used for printed choices. Projected state reads stay in
  the established handlers; no new projection or enumeration work is added.
- Events/triggers: no entry event before the choices; one entry and one resolved event after them.
  The copied ETB ability is asserted to resolve once. Original and current zone-object identities are
  checked in the entry event.
- Continuations: existing spell-choice and EffectContinuation types; both copy-selection and subsequent
  choice pauses round-trip the whole GameState. The exile rider retains an exact object binding.
- Server/UI: existing ChooseColorDecision/ChooseOptionDecision routing, enrichment and masking apply.
  DecisionUI renders the existing color/option controls by decision type; they submit the active
  decision ID. No new DTO, action, decision type or frontend control is introduced. This source trace
  is not a claim that an interactive browser session has been tested.
- Cleanup: the previously qualified CopyOfComponent reset remains unchanged; this slice adds no
  duration state. The regression checks that all continuation frames drain.

The relevant rules were checked against the official September 25, 2026 Comprehensive Rules:
copied entry replacements apply, choices are made afresh, and choices precede battlefield entry
(707.5, 707.6, 614.12a). This is engineering research, not official-game rules admission.

Direct land placement, simultaneous entrants, other copied replacement families, tokens and exact
Metamorph qualification remain separate work. Registry counts and all official-game counters remain
unchanged. No v0.7 edits, official seed/game generation or reserved Precision use are authorized here.
