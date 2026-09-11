# Project Mayhem / Project X Laboratory status

## Workspace boundary

This laboratory owns Project X work only. Batshit Economics/Affinity branches, seeds, decks, reports, and workflows are read-only from this workspace and may be inspected only when a shared Argentum change requires context.

Before every repository write or workflow trigger, verify that the target is `mayhem/project-x` or another explicitly authorized Project X branch.

## Current state

- Current validated gameplay/execution head: `9875ae913ed5f2ec686dcf0179f697a039e97665`
- Readiness head: `bb93886c838f7fa54ce867bbfd49f1c3bb374aaf`
- Experiment A seed freeze: `03ec58f134a9a05da5f3dff9bfe9cb57edfd2fa1`
- Active branch: `mayhem/project-x`
- Sideboard: none

## Frozen Project X v0.2 control

```text
4 Carrion Feeder
4 Safehold Elite
4 Ivy Lane Denizen
4 Wirewood Herald
4 Evolution Witness
4 Nettle Sentinel
4 Birchlore Rangers
2 Falkenrath Noble
1 Essence Warden
1 Masked Vandal
2 Quirion Ranger
4 Winding Way
4 Lead the Stampede
9 Forest
7 Swamp
1 Khalni Garden
1 Haunted Mire
```

This list is frozen exactly. Do not add Giant's Boulder or a sideboard.

## Current variant

Optimization Experiment A Variant A differs from v0.2 only by:

```text
-1 Falkenrath Noble
-1 Masked Vandal
+2 Llanowar Elves
```

All four Evolution Witness remain. Variant A has not been promoted.

## Current experiment and seeds

- Experiment: Project X Optimization Experiment A
- Official vector: `gym/src/test/resources/project-x-optimization-a-seeds.csv`
- Size: 30 paired deterministic seeds, identical seed/opening conditions for control and variant
- Execution: completed exactly once in Argentum Validation run `34547803872`
- Retirement: the vector is permanently frozen and retired from replacement, reroll, or future independent performance sampling
- Result artifacts: `docs/validation/project-x-optimization-a.md` and `docs/validation/project-x-optimization-a-audit.md`

## Current blocker

The existing-artifact Birchlore audit demonstrated a general solitaire mana-activation policy defect. Birchlore receives elevated priority whenever Carrion Feeder or Falkenrath Noble is in hand, even when producing black does not unlock an executable spell or immediate combo line. Pairs 3 and 20 account for all 32 net Birchlore-event difference; most of those activations did not fund a cast. The current `birchloreManaContribution` label also conflates successful activation with productive mana contribution.

The separate Witness reporting qualification remains: the redundant `successfulRecursions.counterSource` field has event-queue lag. Canonical counter-placement and Witness-trigger telemetry remains complete and source-attributed.

## Next authorized action

Await approval for a general reusable-mana policy correction and focused regression coverage before replication. No replication seed vector has been generated. Do not promote Variant A, generate replication seeds, test other accelerators or Giant's Boulder, optimize the deck, or begin matchup self-play before that gate is resolved.

## Relevant general Argentum changes

- Reusable SDK-level Adapt facade with resolution-time condition checking and existing +1/+1 counter placement.
- Project X solitaire policy uses actual card-color properties for Nettle Sentinel/Birchlore sequencing.
- General acceleration valuation prefers a mana creature only when it advances the earliest relevant future cast.
- No general engine, Gym, UI, or unrelated agent behavior was changed for Experiment A execution.

## Latest validation

- Argentum Validation run `34547803872`: PASS
- Compile: PASS
- Full engine suite: PASS
- Gym suite: PASS
- Gym trainer suite: PASS
- Frozen 30-pair Experiment A block: PASS
- Harness audit errors: 0/60
- Executor-rejected casts: 0
- Temporary `mayhem/project-x` push trigger: removed in the preservation/cleanup commit
