# Grixis Affinity Regression Gate

## Purpose
Validate Batshit Economics Variant C against the existing Grixis Affinity opponent by repairing only demonstrated agent-policy regressions before Sample #1 can be considered matchup evidence.

## Frozen control and variant
- Permanent Batshit Economics control: locked; no changes authorized.
- Provisional incumbent: Variant C; frozen exactly for this gate.
- Opponent: existing Grixis Affinity deck; frozen exactly for this gate.
- Reckoner's Bargain policy: unchanged after focused audit; outside the active correction scope.

## Seed rules
- Use the existing exact 100-seed Grixis preboard regression vector.
- Preserve seed values, order, play/draw assignment, deck configuration, and harness semantics exactly.
- Do not generate replacement or additional seeds.
- Do not start a fresh Affinity sample until this regression gate is fully clean.
- The vector remains active and unretired until the gate is explicitly closed.

## Authorized policy scope
Only diagnose/correct demonstrated policy defects involving:
1. Galvanic Blast.
2. Nihil Spellbomb.
3. Krark-Clan Shaman.

Any proposed correction must be general game-state/card-property policy where possible and must not special-case Batshit Economics, Variant C, individual frozen seeds, or Sample #1 outcomes.

## Metrics / evidence
For the same frozen vector, audit at minimum:
- completion of all 100 games without regression assertion/failure;
- Galvanic Blast targeting/timing decisions relevant to the known gate;
- Nihil Spellbomb activation/timing decisions relevant to the known gate;
- Krark-Clan Shaman activation/sacrifice decisions relevant to the known gate;
- deterministic focused tests added for every policy defect corrected;
- full CI / Argentum Validation status before accepting the replay;
- preservation of control, Variant C, opponent deck, and seed vector.

## Stop conditions
Stop and request authorization if:
- the required fix is outside Blast/Spellbomb/Shaman policy;
- the fix would change either deck, Variant C, the permanent control, the frozen vector, play/draw mapping, or Bargain policy;
- a shared engine/rules defect is discovered that requires behavior beyond the authorized policy correction;
- same-seed replay exposes a materially different blocker outside this gate.

## Prohibited changes
- No Project X writes, commits, pushes, seeds, or workflow triggers.
- No fresh Affinity sample or new seed generation.
- No deck-list changes.
- No Variant C changes.
- No permanent-control changes.
- No Reckoner's Bargain policy changes.
- No outcome-driven seed filtering, replacement, or retirement.
- No unrelated agent/engine cleanup bundled into this gate.

## Current baseline
- Validated head entering gate: `51d2446d2f9c0f0595574f8f489d10a8485be8e4`.
- Argentum Validation #130: passed.
- Grixis Affinity Sample #1 run `34534520319`: failed during `Run 100 frozen-seed preboard games`; artifact upload succeeded.
- Sample #1 remains rejected as matchup evidence until this gate is clean.
