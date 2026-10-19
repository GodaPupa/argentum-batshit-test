# Manual Transmission gameplay attempt — 2026-10-19 UTC

Result: BLOCKED BEFORE INITIALIZATION. Zero games initialized or completed; no winner,
turn count, action count, commander casts, combo attempts, or mode comparison exists.

Source: PR #218 head `0e143f26e4a4a61c6de6d5b276c04ac232346b2c`.
Isolated branch: `gameplay/manual-transmission-pod-20261019`.
Frozen v0.7 file is unchanged. `source-audit.json` records its actual byte digest;
the SHA printed inside the deck's header is a declaration, not a verified file digest.

## Deck and registry audit

The file contains 100 cards, 93 distinct names, one Animar, and no repeated nonbasic
names. Forest/Island/Mountain account for the seven additional copies. All names
have static source candidates after resolving curly apostrophes to ASCII.
Five exact deck strings do not match the source names: Emrakul’s Hatcher,
Tishana’s Tidebinder, Garruk’s Uprising, Lifecrafter’s Bestiary, and An Offer You
Can’t Refuse. `CardRegistry.getCard` uses exact strings and does not normalize them.
A new gameplay loader must resolve these aliases without editing the frozen list.
This is source discovery, not proof that the compiled catalog loads each definition.

No card behavior was executed. No incorrectly implemented card is established by
this attempt. Color-identity, current ban-list, and complete Oracle behavior validation
remain unverified; the count/singleton check is not full Commander legality approval.
Disciple has a composed modal spell/land definition; its presence alone does not prove
both faces work. Animar has protection, cast-counter, and generic cost-reduction data.

The exact frozen opponent lists exist, each containing commander plus 99 cards.
Static candidate gaps: Kinnan 39 distinct names; Magda 50; Shorikai 44. These counts
include missing commander definitions where applicable: Magda, Brazen Outlaw is absent.
The JSON contains every missing name and every found candidate path. Do not initialize
these lists by dropping unknown cards or manufacturing vanilla placeholders.
No substitute pod was executed or verified.

## What Phase 2 actually supplies

- `ManualPhaseTwoFixtureRunner`: loops over caller-supplied telemetry and policies;
  it does not initialize the real deck or construct complete pilots.
- `ManualPhaseTwoOpeningActionPolicy`: plays one uniquely offered land or passes;
  rejects pending decisions, opponent stacks, and ordinary strategic forks.
- `ManualPhaseTwoUniqueCommanderCastPolicy`: casts Animar only when its cast is the
  sole affordable strategic action. Cruise/Sport/Race take the same action here.
- `ManualPhaseTwoPriorityConservationPolicy`: narrow Sport/Race counter-stack case;
  other decision windows are rejected.
- `ManualPhaseTwoRaceOverlayRouter`: returns opponent-axis overlay identities;
  chooses no actions. Overlay labels do not demonstrate an active Race strategy.
- `ManualStoredFixtureReplayIntegrationTest`: synthetic named commanders and 99
  Forests; one keep action and a resource cap. This is not Manual gameplay.

The generic `ai/.../AIPlayer.kt` supplies action and pending-decision entry points.
It is a plausible baseline driver dependency, not a demonstrated implementation of
the three Manual gears. An ordinary baseline game must be labeled accordingly.

## Execution blocker and smallest next implementation

Command attempted: `scripts/test-class CommanderSetupTest` (the recipe underlying
`just test-class`; `just` is not installed). Exit 1 before compilation. The repository's
Gradle wrapper requested `https://services.gradle.org/distributions/gradle-9.6.1-bin.zip`
and failed with `java.net.SocketException: Network is unreachable`. No cached
distribution was available. Installed Java reports OpenJDK 17.0.20; buildSrc and
the JVM convention request toolchain 21. No privileged work was attempted.

A clean worktree was created after the initial clone checkout to keep the final source
inventory separate from untracked checkout remnants. The failed command above ran
in the initial PR-head checkout; it never reached source compilation.

No missing core Commander rules capability has been demonstrated. Source and tests
exist for four-seat setup, 40 life, designated command-zone commanders, tax, commander
damage, and zone choices, but they were not executed here.

The smallest verified input blocker is exact-name alias resolution. The larger missing
application capability is a live pod initializer/driver that loads all real definitions,
uses 99 cards plus a separately designated commander, handles mulligans and pending
decisions through the existing AI, and records every submitted action/result and stop.
First enable the standard JDK 21/Gradle environment; then validate the setup test and
that driver with fully registered single-commander substitute decks. Do not infer an
engine rule must be added before that attempt exposes a concrete rule failure.

## Preserved output and validation

`tools/manual_gameplay_source_audit.py` is a reproducible, read-only Python source
inventory. It ran successfully in the clean worktree and generated `source-audit.json`.
Its regex is intentionally limited to literal `card`/`basicLand` declarations; candidate
presence is weaker than runtime registration and absence requires confirmation before
implementing a card. The script does not execute historical protocols or change records.
No Kotlin changes, policy claims, merges, gameplay traces, or historical evidence edits.
