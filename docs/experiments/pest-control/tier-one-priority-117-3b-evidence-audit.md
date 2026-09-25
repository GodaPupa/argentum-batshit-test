# Pest Control: retrospective post-resolution priority audit

## Current disposition

**QUARANTINED_PENDING_PROTOCOL_DISPOSITION — all five historically accepted Red, Grixis and
Mono-Blue sampled blocks listed below.** Their original result documents, raw artifacts, deck and
pilot identities, seed retirement and observed outcomes remain immutable historical records.
They cannot presently supply clean competitive-capability or Tier-1 inference while this confirmed
rules defect is unresolved in their evidence. No corrected winner or replacement result is assigned.

This supplement records a source and trace defect discovered during new seed-free postboard
qualification. It does not reopen a closed opponent, authorize additional sampling, salvage selected
games, or allow reuse of a retired seed. The existing whole-block integrity boundaries must govern
any final disposition; no games are selected for retention based on who won or which side benefited.

## Defect and source applicability

Effective Comprehensive Rules 117.3b gives the active player priority after a spell or non-mana
ability resolves. The archived September 25, 2026 rules document has SHA-256
`8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca` and is available from
[the official rules source](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt).
This is not a new Bestow rule or a retrospective change to the format.

The official execution source of every audited block contains the same Git blob
`3b1d4608694a7ba6fc314e1b66d90ff60312bfef` at
`rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/priority/PassPriorityHandler.kt`.
Lines 167–175 explicitly select the resolved stack item's caster/controller and incorrectly cite
117.3c. Both completed-resolution return paths at lines 285 and 291 assign priority to that player.
When that controller is not the active player, the next priority decision can occur in the wrong
order. Merely choosing actions accepted by this same engine does not establish compliance.

The frozen source and raw trace exhibit the defect directly. This audit does not re-run old seeds,
modify the engine under old games, or simulate hypothetical corrected outcomes.

## Audited blocks and conservative trace findings

| Historical block | Exact execution source | Official run / artifact | Historical record | Games with confirmed wrong priority | Confirmed transitions |
|---|---|---|---|---:|---:|
| Mono-Red V2 qualification 50 | `8d3895de38189ca16f18017eb84b5e5a3f060b3b` | 35536805887 / 10613973303 | 32–18 | 37 / 50 | 114 |
| Grixis smoke 4 | `d7051af65b30a6d5d2e35780e60ea5b5b4d5e055` | 35632206268 / 10655603968 | 3–1 | 1 / 4 | 1 |
| Grixis salvage 11 | `9e23b9cff24752f3278d8826ca2dfd4aba4ac6a6` | 35670905674 / 10673238745 | 4–7 | 5 / 11 | 6 |
| Mono-Blue smoke 4 | `d85ecb9281fc2c7993d9651e72484311af89b4d9` | 35894018961 / 10766612589 | 4–0 | 3 / 4 | 8 |
| Mono-Blue primary replication 12 | `73b98896eeb4f47c4ed3ec97a10a9a3aeb5dc62e` | 35927279604 / 10780871469 | 11–1 | 7 / 12 | 19 |

All five downloaded archive SHA-256 values reproduce their original accepted result documents.
Live GitHub run metadata independently confirms each execution source and attempt 1. The Red
historical Markdown appends an extra trailing `c` to its commit text; the actual 40-character
source above is taken from the live run and matches every raw game's source/execution fields.
That transcription discrepancy is recorded here without altering the historical document.
The companion inputs JSON binds each block to its exact source, workflow, artifact, archive digest,
game count and original result document. The findings JSON identifies each witness by original game
number, archive member and digest, action sequence, active player, observed next priority player,
resolution event and next action. It includes no newly generated seeds or counterfactual outcomes.

These are **lower bounds**, not a census of every possible affected interaction. The whole-block
quarantine covers all 81 historical games across these five blocks; it does not retain the games
without a detected witness as clean evidence. The conservative audit detects 53 games with at least
one witness and 148 transitions. Historical game counts and recorded winners remain unchanged.

### Witness method

For the full Mono-Red evidence, an accepted PassPriority action resolves a spell or ability and
its explicit `afterAuditState` gives priority to a player other than the active player, with no
pending decision. A following accepted action confirms that the game continued. This is direct
state evidence, including ability resolutions for which the reduced formats do not retain a full
stack-controller snapshot.

For Grixis and Mono-Blue, the smaller raw format does not contain after-state snapshots. The audit
therefore requires all of the following: an accepted PassPriority action actually emits a
ResolvedEvent; its entity is bound to a recorded SpellCastEvent by a nonactive caster; the next
recorded action is an accepted PassPriority by the nonactive player during the same turn; and neither
side of that transition is waiting for a decision. PassPriority's validation requires that the actor
has priority in these two-player, non-team games. An accepted Concede, combat declaration or other
action that might not require priority is never used as a reduced-trace witness.

Active-player identity starts from the frozen seat/starting-deck assignment and is updated from
recorded TurnChangedEvent entries; the Red snapshots independently provide it. Paused resolution,
unmapped ability cases in reduced traces, final terminal transitions without a following action,
and ambiguous cases are excluded from this lower bound. The absence of a detected witness is not
proof that a game is unaffected.

Representative witnesses:

- Red game 1, sequence 156: Carrier Thrall's death ability resolves on `e1`'s turn; the explicit
  after-state and next accepted action give priority to `e0`.
- Grixis smoke game 2, sequence 268: `e1`'s Reckoner's Bargain resolves on `e0`'s turn; the next
  accepted PassPriority at sequence 269 is by `e1`.
- Mono-Blue smoke game 1, sequence 93: `e1`'s Thought Scour resolves on `e0`'s turn; the next
  accepted PassPriority at sequence 94 is by `e1`.

## Materiality and limits

Receiving priority first can change whether a player casts a spell, activates an ability or waits
before the active player can act. Even when the observed first action is a pass, the pilot made
that decision at an incorrect rules boundary. The trace proves an execution defect, not how many
wins would change under a repaired engine. No effect size, adjusted win rate or synthetic winner
can be recovered from this audit.

A separately qualified canonical priority repair is required before any new gameplay source can
be admitted where this path applies. That repair does not repair historical raw results. The
Monster Tron attempt of run 36085093386 initialized zero games and submitted zero actions before
its separately audited command-path failure; none of its nonexistent gameplay can be affected by
this priority defect. Its four seeds and one consumed claim remain retired for the earlier reason.
The prospective replacement proposal must qualify the corrected engine through its own prescribed
gates and does not itself authorize another run.

The closed Red, Grixis and Mono-Blue opponents remain closed to further preboard sampling under the
bounded five-axis protocol. Pest's deck is unchanged. Spy and required postboard capability work
may proceed seed-free while the canonical repair and evidence disposition are reviewed. No
sixth opponent or outcome-conditioned extension is introduced.

## Reproduction and current support checkpoint

Run the standard-library-only script `audit_priority_117_3b.py` against the exact input manifest,
with the five named downloaded ZIPs anywhere under an explicitly supplied archive directory:

```bash
python3 audit_priority_117_3b.py tier-one-priority-117-3b-inputs.json \
  --archive-root /path/to/downloaded/artifacts \
  --output reproduced-findings.json
```

The script checks archive digests, exact game counts, each game's source identity, sequential actions,
and selected-action player/recorded-actor agreement before inspecting original records. It
never invokes an engine, downloads a file, initializes a game, or writes into an input ZIP. The
separate focused tests challenge invalid reduced witnesses and tampered inputs.

Independent of these historical outcomes, **Spy Batch B has been accepted** at source
`7a3f1429c027925c30ed7c1e329c3879e674574a`, merged in PR #148 as
`c6953354b1b799fe4506eb9e44ff25523a132d8e`. Dedicated run **36087086060**, artifact
**10843959361**, ZIP SHA-256
`1c670bb133fc26e5ddbbabde6a7cfc3d2b32cecde8e23a1b3b08dae23de50d9f`, independently retained
362 passing JUnit cases with zero failures/skips, including 338 strict snapshot cases. All five
required workflows passed. That seed-free acceptance reduces the Spy main queue from five to one
identity: Nyxborn Hydra ×2. It does not provide gameplay evidence. Canonical Bestow/Nyxborn PR #151
remains a separate candidate until its own exact-source qualification and audit pass.
