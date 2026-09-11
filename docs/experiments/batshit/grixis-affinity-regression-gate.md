# Grixis Affinity Regression Gate

## Purpose
Validate Batshit Economics Variant C against the existing Grixis Affinity opponent by repairing only demonstrated agent-policy regressions before any fresh Affinity sample can begin. The frozen replay is regression evidence only, regardless of record.

## Frozen control and variant
- Permanent Batshit Economics control: locked; no changes authorized.
- Provisional incumbent: Variant C; frozen exactly for this gate.
- Opponent: existing Grixis Affinity deck; frozen exactly for this gate.
- Reckoner's Bargain/Wellspring policy: unchanged except where Wellspring is incidental compensation inside the specifically authorized self-only Krark-Clan Shaman sweep guard; no general Bargain/Wellspring change is authorized.

## Seed rules
- Use the existing exact 100-seed Grixis preboard regression vector.
- Preserve seed values, order, play/draw assignment, deck configuration, and harness semantics exactly.
- Do not generate replacement or additional seeds.
- No rerolls, exclusions, substitutions, or outcome-driven retirement.
- Replay the vector at most once after the current policy candidate passes focused tests, full CI, and Argentum Validation.
- Do not start a fresh Affinity sample until this regression gate is fully clean.
- The vector remains active and unretired until the gate is explicitly closed.

## Authorized policy scope
Only diagnose/correct demonstrated policy defects involving:
1. **Galvanic Blast** — prevent speculative reduced-rate face conversion while preserving justified lethal/race conversion. General factors include current/prospective lethal math, damage efficiency, higher-value creature targets, future conditional/metalcraft value, and interaction opportunity cost.
2. **Nihil Spellbomb** — prospective graveyard value must arise from concrete visible/reasonable recursion, graveyard synergies, immediate stack interaction, intrinsic graveyard functionality, or comparable strategic utility; generic card/creature presence alone is not value.
3. **Krark-Clan Shaman** — a sacrifice-paid sweep that damages/removes only its controller's creatures is not justified merely by incidental compensation such as a Wellspring draw. Productive opposing-creature sweeps remain evaluator-controlled.

Any correction must remain general game-state/card-property policy where possible and must not special-case Batshit Economics, Variant C, individual frozen seeds, or Sample #1 outcomes.

## Focused deterministic regression requirements
- Reduced-rate conditional burn is held when the face shot is speculative.
- Reduced-rate conditional burn remains available when conservative visible follow-up completes lethal.
- Generic creatures/cards in a graveyard do not by themselves justify spending graveyard hate.
- Concrete opposing recursion already using a graveyard on the stack still justifies Spellbomb activation without requiring a draw.
- Existing Spellbomb tests must continue to cover correct hold, activation-without-draw, activation-with-draw, and graveyard-target polarity.
- Wellspring draw alone does not justify a self-only Krark-Clan Shaman sweep.
- Krark-Clan Shaman still cashes a productive artifact when the sweep affects opposing creatures.

## Metrics / evidence
For the same frozen vector, audit at minimum:
- completion of all 100 games without regression assertion/failure;
- Galvanic Blast Games 16, 18, 60, 62, 90, and 100;
- Nihil Spellbomb Games 5, 8, 41, 44, and 83, while preserving sane Games 1 and 98;
- Krark-Clan Shaman Game 81;
- preservation of previously correct Shaman behavior in Games 31, 36, 47, 49, 50, and 97;
- any new regression elsewhere in the full replay;
- focused deterministic tests for each corrected policy defect;
- full CI and Argentum Validation before replay;
- preservation of control, Variant C, opponent deck, seed vector, seed order, and play/draw mapping.

## Stop conditions
Stop and request authorization or report the blocker if:
- the required fix is outside Blast/Spellbomb/Shaman policy;
- the fix would change either deck, Variant C, the permanent control, the frozen vector, play/draw mapping, or Bargain/Wellspring generally;
- a shared engine/rules defect is discovered that requires behavior beyond the authorized policy correction;
- focused/full validation is not green;
- Argentum Validation cannot be run on the policy candidate;
- same-seed replay exposes a materially different blocker outside this gate.

## Prohibited changes
- No Project X writes, commits, pushes, seeds, or workflow triggers.
- No fresh Affinity sample or new seed generation.
- No postboard testing.
- No deck-list changes or optimization.
- No Variant C changes.
- No permanent-control changes.
- No general Reckoner's Bargain/Wellspring policy changes without a separately demonstrated defect.
- No outcome-driven seed filtering, replacement, reroll, exclusion, or retirement.
- No unrelated agent/engine cleanup bundled into this gate.
- No mid-replay policy changes.

## Current implementation and gates
- Validated gameplay baseline entering this work: `51d2446d2f9c0f0595574f8f489d10a8485be8e4`; Argentum Validation #130 passed there.
- Parser-only correction: `e68b83d049b79ebb0103e98137c3f3be9c2f65e7`; `humanSampleReport` now handles suffixed slash-pair values such as `0/0; Craft casts: 0`.
- Current policy candidate: `e4b7921488674ae51335e710fe25275f485bba7d`.
- Full CI #168 (`34552925773`) is green on the policy candidate, including the focused policy regressions and all normal CI groups.
- Argentum Validation has **not** been run on the policy candidate. Its workflow is manual (`workflow_dispatch`), and the current GitHub connector available in this Work session has no workflow-dispatch action.
- The frozen 100-seed vector has **not** been replayed after these corrections.

## Current blocker / next action
The regression replay is blocked on Argentum Validation for the policy candidate. Obtain/run Argentum Validation on the current policy candidate (or a coordination-only descendant with identical code). Only if it is green may the exact existing 100-seed vector be replayed once, unchanged and in order, followed by the full audit above.
