# Izzet Science v0.7 — Deck-Level Audit After Engine Requalification

Disposition: `V07_DECK_AUDIT_NO_CHANGE`

Accepted control: `izzet-science/v0.7-control.md`

Control SHA256:
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`

## Scope

This is a deck-construction review only. It does not consume an experimental seed,
initialize a game, expose an outcome, or authorize a card promotion. The purpose is
to ask whether the exact accepted 100 still makes structural sense for the original
Izzet Science objective before additional engine-coverage work continues.

## Objective retained

The deck remains a high-decision-density Izzet Pauper EDH combo-control deck built
around:

- deterministic Izzet Guildmage + Lava Spike + Desperate Ritual;
- Dramatic Reversal plus nonland mana as a secondary Guildmage engine;
- High Tide / Snap / untap utility;
- compact tutor connectivity;
- interactive permission and tempo;
- commander-independent backup capacity through Capsize, Murmuring Mystic, Rolling
  Thunder, and Kaervek's Torch;
- standalone utility outside compact deterministic necessities.

## Audit

### Mana

The 36-land / 8-nonland-mana structure remains coherent with High Tide, commander
deployment, Dramatic Reversal, and the five-mana primary launch. The accepted snow
retrofit materially improved Skred without changing the prior mana telemetry.

No rock should be replaced on theory alone. The tested Prismatic Lens challenger
improved Reversal readiness but failed its preregistered interaction/recovery gate
when substituted for Lose Focus; that result does not justify an untested promotion.

Fellwar Stone is matchup-dependent as colored fixing, but it still contributes
generic nonland mana to the deck's Reversal and launch economy. A one-opponent
Selesnya matchup is not sufficient reason to weaken the general multiplayer control.

### Combo and launch package

Lava Spike, Desperate Ritual, Dramatic Reversal, High Tide, Snap, Ideas Unbound, Eye
of Nowhere, and Seething Song all have defined engine or utility roles. Seething Song
was specifically promoted over Hidden Strings after a 100,000-pair confirmation and
improved launch conversion without violating mana guardrails.

No cut is justified here.

### Selection and tutors

The selection density is appropriate for a singleton combo-control deck. Pieces of
the Puzzle was promoted over Curate by accepted paired evidence and materially
improved pair assembly / deterministic lethal while preserving the broader shell.

The larger backup-plan limitation observed by the v0.9 readiness baseline is access,
not mana once the card is present. The accepted Capsize tutor policy already attacks
that problem without consuming a deck slot and materially improves modeled Capsize
availability and interaction readiness.

A future multifunctional access challenger may be warranted, but not as an
uncontrolled list edit.

### Interaction

Nine permission/protection cards plus ten removal/tempo cards is consistent with the
deck's combo-control identity. Existing challenger evidence argues against replacing
interaction with narrow cards merely because they look efficient:

- Pyroblast failed its frozen improvement threshold and lost conditional coverage.
- Prismatic Lens did not earn the interaction slot it challenged.
- Dive Down improved one protection surface but breached the red-execution guardrail.

The current broad interaction package therefore remains better supported than any
tested alternative.

### Backup plans

Murmuring Mystic, Rolling Thunder, Kaervek's Torch, and Capsize are not redundant
dead-card soup. Readiness testing showed that when present by T10, mana was usually
sufficient; access was the dominant bottleneck. Capsize's accepted tutor policy
substantially improves that access while preserving primary-combo guardrails.

Do not add another generic finisher before testing a card that improves
multifunctional access.

## Future challenger worth preregistering

`Step Through` is a plausible later challenger because Wizardcycling can increase
access to Wizard utility while the front half remains a two-creature tempo spell.
That hypothesis is not yet modeled by the engine and no cut has earned replacement.
It must be tested as a formally frozen one-slot challenger before any promotion.

## Decision

No card revisions are justified at this checkpoint.

Keep the exact v0.7 100 frozen and continue engine-coverage qualification. Card
changes remain evidence-gated.
