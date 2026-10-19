# Manual Transmission mirror

Ordinary exploratory gameplay with AiProfile.CURRENT in all four seats. No Manual gear
policy is asserted. All players keep their seeded opening seven (no hand smoother).
Source deck stays byte-for-byte frozen; five explicit input aliases resolve registry names.

The named Commander ban snapshot was read from
https://magic.wizards.com/en/banned-restricted-list (Commander section) on 2026-10-19 UTC.
None of this deck's cards belongs to the additional ante, Conspiracy, or offensive-card
categories. Lutri's companion restriction is irrelevant to this no-companion deck.
Per-card compiled legality metadata is logged independently; missing metadata is disclosed.

The opt-in test writes registry audit, initial/final state, each submitted action and
its events, AI scored-choice insights, final result, and thrown exception stack to the
output directory. Action/runtime/turn caps mean an unfinished game is a failure, not a draw.
A successful initialization is checked before the same game proceeds to completion.

Run via `scripts/test-class ManualTransmissionMirrorTest -Dbenchmark=true` with
MANUAL_MIRROR_RUN=1, MANUAL_MIRROR_ROOT set to repository root, and
MANUAL_MIRROR_OUTPUT set to an empty output directory. Java 21 is required.
