# First real Manual Transmission v0.7 four-player Commander game

VERIFIED COMPLETE. This is ordinary exploratory gameplay, with the existing general-purpose
`AiProfile.CURRENT` in all four seats. Cruise, Sport, and Race are not implemented or claimed.

Run: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39194066341
Source commit: `b9ee8c9f7af24c5696637d0bd503d50304384728`
Branch: `gameplay/manual-mirror-recovery-20261019` (not merged).
Seed: `202610190701`. Java 21.0.12; Gradle 9.6.1. Both focused tests passed.

## Result

| Seat (one-based) | Engine ID | Personal turns begun | Commander casts | Final life |
|---|---|---:|---:|---:|
| 1 | manual-mirror-0 | 12 | 0 | -7 |
| 2 | manual-mirror-1 | 15 | 1 | -3 |
| 3 | manual-mirror-2 | 19 | 0 | -4 |
| **4 — winner** | manual-mirror-3 | **19** | **0** | **14** |

The game ended during global player turn 65. There were 3,062 submitted and accepted
engine actions, zero rejections, and zero thrown exceptions. Runtime including registry
audit/setup: 47.845 seconds. Action types: 2,735 priority passes, 35 land plays,
69 spell casts, 65 ability activations, 89 decision responses, 47 attacker declarations,
21 blocker declarations, and one cycling action. There are 679 AI scored-choice records.

All seats used four independent copies of the unchanged frozen deck: 99 library cards
before opening draw, one designated Animar in the command zone, 40 life, Commander
format, and the normal engine's priority/combat/elimination processing. All four kept
seeded opening sevens; no hand smoothing or mulligan-strategy claim. Engine assertions
verified command identities, 99 library-plus-opening-hand cards, and starting life.
The same game passed 64 accepted-submission smoke coverage and continued to completion.

## Card and legality audit

Every one of the 100 physical cards (93 distinct names) resolved through the compiled
catalog. The five exact curly-apostrophe aliases are explicit in the input loader;
the frozen file was not changed. Count, commander eligibility, singleton exceptions,
and each compiled card's color identity passed. No deck name appears on the current
named Commander ban snapshot read from Wizards' official list. This deck does not
use ante, Conspiracy, offensive-category cards, or a companion.

All 93 compiled definitions had empty `legalFormats` metadata. Therefore format legality
was checked with the separately recorded official ban snapshot, not represented as a
Scryfall metadata pass. This successful game does not exercise every card or prove
complete rules fidelity for all possible interactions.

Frozen file SHA-256:
`2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1`

Official ban source: https://magic.wizards.com/en/banned-restricted-list
Snapshot: `tools/manual-mirror/commander-banned-20261019.txt`.

## Important actual decisions and weaknesses

- Seat 2 cast Animar from the command zone at action 819, global player turn 22.
  The trace records the actual three colored mana spent and command-to-stack transition.
- Seat 2 eliminated seat 1 through combat at action 2378. Seat 3 then eliminated seat 2
  through combat at action 2794. Seat 4 eliminated seat 3 through combat at action 3062;
  the engine emitted both the last loss and GameEndedEvent for seat 4.
- The final attack sent Frog Lizard Token, Fauna Shaman, Nest Invader, Cloud of Faeries,
  Coiling Oracle, and Amphin Mutineer at seat 3. The scored-choice record identifies it
  as the AI's heuristic seed attack, without local search.
- Commander deployment is a concrete strategic weakness. Seat 1 considered Animar in
  13 scored windows but never cast it; seat 3 in three, and seat 4 in six. For example,
  at global player turn 13 seat 1 selected Soul of the Harvest while a scored Animar
  cast was available. This is a decision-quality observation, not proof that a different
  cast would have won.
- No Ancestral Statue or Walking Ballista cast/activation appears. Temur Sabertooth,
  Peregrine Drake, and Purphoros do appear, but their presence is not counted as a
  deliberate combo attempt. No demonstrated infinite combo or combo-intent count is claimed.

## Preserved and independently checked evidence

GitHub artifact `12161685788`, `manual-mirror-39194066341-1`.
Original ZIP SHA-256:
`27823f20e05ad0d10e9803a2d4b3408417ade366cbc9392fb67678370e8eb1f4`.
The ZIP is preserved unchanged at `run-39194066341/original.zip`.

It contains complete action/result JSONL, scored-decision JSONL, initial and final
engine-state JSON, registry audit, setup metadata, source/deck identities, compiler/game
logs, smoke marker, summary, and test XML. `tools/verify_manual_mirror.py` checked stream
indices/counts, all accepted results, terminal winner consistency, commander-cast events,
and personal-turn counters against the final state's actual components. Verification
output and per-file hashes are in `run-39194066341/verification.json`.

The prior local commit `6f0793c8b42949497fde6cf877f6c1d6c9beb60c` was recovered locally.
Its actual report, source inventory, and audit script were published as contents in
`b9ee8c9...`; the old local commit itself is not claimed to exist on GitHub.
No historical result was changed.

## Independent opponent inventory

See `opponent-inventory.md` for every missing source identity. Provisional order of
least implementation work: Kinnan (39 missing definitions), Shorikai (44), Magda (50,
including Magda, Brazen Outlaw). These are source counts, not compiled opponent-pod
admission or demonstrated missing shared mechanics. Shorikai's commander permission
is also absent from its source Oracle text. The mirror did not wait for this inventory.
