# Prospective correction of software fixture sequences

The original six-case diagnostic `36282117960` retains four failures. The two proposed card-specific test-file changes address three of those failures; the unchanged baseline remains a separate unresolved assertion. No new cases, generic helper behavior, engine change, frozen pilot choice or experiment budget are proposed.

Current official rules were checked on 2026-09-27 via the [Wizards rules page](https://magic.wizards.com/en/rules) and its [September 25, 2026 Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt). CR 603.3b gives a controller the ordering choice for their simultaneous triggers. CR 115.1d and 603.3d require triggered-ability targets when the ability is put on the stack. CR 603.5 places an optional effect's choice during resolution. These rules support the observed trigger-order and target-before-May decisions; they do not amend the experimental protocol.

Arcane Denial's original test stops at its explicit ordering question. The candidate chooses the first of exactly two options for that fixture, asserts two stack objects and no remaining placement decision, then retains the original draw-amount and final hand/trigger assertions. This is explicit fixture setup, not an installed pilot policy.

Mnemonic Wall's original tests call the Yes/No helper while a mandatory target question is pending. The candidate chooses the existing graveyard Lightning Bolt, resolves the targeted trigger to the optional effect, and answers Yes or No. The old assertion that the Yes response pauses for a later target is prospectively corrected: target placement succeeds, resolution pauses for the optional choice, then Yes succeeds. All original final card-location assertions remain. The prior sequence and its failed raw evidence remain preserved.

Only static source/API inspection has occurred. Exact reviewed source publication and a fresh bounded qualification remain required. This candidate does not claim that any corrected case passed, that the baseline golden may change, or that the combined runtime is admitted.
