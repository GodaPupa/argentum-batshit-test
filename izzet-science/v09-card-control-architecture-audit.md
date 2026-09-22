# v0.9 Card-Control Architecture Audit

Disposition: `V09_CARD_CONTROL_ARCHITECTURE_AUDIT_KEEP_V07`

Accepted control: `izzet-science/v0.7-control.md`

Control SHA256: `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Question

Before continuing Position-1 engine qualification, does the exact accepted 100 still make
sense for the stated Izzet Science objective, or is a card revision justified now?

## Objective checked

The accepted objective remains a high-decision-density Izzet Pauper EDH combo-control
deck built around:

- Izzet Guildmage + Lava Spike + Desperate Ritual as the compact deterministic primary kill;
- Dramatic Reversal plus nonland mana as a secondary engine;
- High Tide / Snap as a distinct untap package;
- interaction and selection dense enough to operate as a control deck;
- commander-independent conversion through Capsize, Rolling Thunder, Kaervek's Torch,
  and Murmuring Mystic;
- useful standalone roles outside compact deterministic combo necessities.

## Evidence reviewed

- Seething Song was promoted only after improving launch conversion while preserving
  mana, tutor, interaction, and commander guardrails.
- Pieces of the Puzzle was promoted only after improving pair acquisition and lethal
  timing with small, temporary deployment costs.
- Snow basics were promoted only after paired confirmation preserved all prior
  telemetry while materially improving Skred.
- Pyroblast, Prismatic Lens, and Dive Down challengers were rejected when their gains
  did not justify the interaction or color-execution cost.
- Accepted v0.9 backup-line work identifies access as the larger observable bottleneck
  once backup cards are present; raw mana readiness is usually sufficient.
- The accepted Capsize acquisition policy materially improves Capsize access and
  interaction readiness without requiring a card change.
- Post-rebless current-head requalification run 35688661684 passed snapshots, real
  engine coverage, frozen-control identity, zero official-seed/outcome assertions,
  manifest construction, and artifact upload.

## Deck-level findings

The exact v0.7 100 remains internally coherent.

The apparently redundant recursion creatures are not treated as automatic cuts before
real-game calibration: they provide resilience and repeated access to the unusually
high instant/sorcery density while remaining distinct from the prohibited
Peregrine-Drake/Ghostly-Flicker blink shell.

Eight dedicated mana-infrastructure cards plus ritual/untap acceleration remain
consistent with the Dramatic Reversal secondary engine. Prior challenger evidence
does not justify trading interaction for another mana source.

The interaction suite remains intentionally broad. Existing challenger evidence shows
that apparently efficient substitutions can improve one readiness metric while
damaging conditional protection or color execution.

The current backup package is sufficiently redundant for first real-opponent
calibration. The observed bottleneck is acquisition/availability, not a demonstrated
need for a fourth damage finisher or another generic rock.

## Revision decision

No card is cut, added, or promoted.

Changing the card control immediately before the first real-opponent calibration
would erase the clean experimental baseline without replicated evidence that a
specific replacement is superior.

## Future challenger hypothesis

`Step Through` is retained as a future challenger hypothesis, not a promotion.
Wizardcycling could convert a flexible bounce spell into direct access to several
Wizard utility/backup creatures and therefore attacks the observed access bottleneck.
It should not enter the control until typecycling is implemented in the engine and a
formal paired challenger identifies an exact cut and preregistered guardrails.

## Next gate

Continue Position-1 engine coverage with a low-risk shared-mechanic batch:
`Archaeomancer` + `Mnemonic Wall`.

Both are already in the accepted 100 and currently unresolved. Their core ETB
instant/sorcery-recursion behavior reuses the already-qualified Izzet Chronarch
mechanic; Mnemonic Wall additionally requires its printed optional return semantics.

Official experimental counters remain untouched:
- official seeds consumed: 0
- games initialized: 0
- outcome exposure: 0
