# Rally forced-exposure disposition — 2026-09-20

## Decision

Unmodified Rally sequencing under Forge profile v1 is rejected. A narrow, versioned opponent-pilot correction is justified for diagnostic validation. No matchup, challenger, postboard, or qualification block is authorized by this result.

## Preserved evidence

- Workflow run: `35536158084`
- Artifact: `10613266060`
- Artifact digest: `sha256:7f0a35799d6e10e9fc6ef1859022d10e379c81a6b71cf7a52ae526dafa86330d`
- Seeds: `720153`–`720160`, all permanently retired and diagnostic only
- Games: 8 terminal, 0 runtime flagged
- Joint Rally/Bushwhacker turns: 6
- Ordering violations: 1

Seed `720156` cast a kicked Goblin Bushwhacker and resolved its team pump before casting Rally at the Hornburg in the same precombat main phase. The Rally tokens therefore did not receive Bushwhacker's +1/+0 effect. This is material opponent-pilot contamination and independently confirms the sequencing concern observed in rejected Stage 1 evidence.

## Authorized remediation scope

Profile v2 may add only a sequencing guard that defers Goblin Bushwhacker when Rally at the Hornburg is in hand, Rally has not yet been cast that turn, and at least four mana sources are available. It does not modify any frozen deck list. A passing remediation fixture must be followed by independent fresh-seed forced-exposure replication before Rally is simulator-eligible.
