# Rally sequencing remediation discovery — 2026-09-20

## Result

Forge profile v2 passed the diagnostic discovery gate. This is not qualification or matchup evidence and does not by itself make the profile acceptable; independent fresh-seed replication is required.

- Workflow run: `35536530615`
- Artifact: `10613192706`
- Artifact digest: `sha256:070919e9e145791dae23cb5ce47bfc3708c6cf10a580ee59a60f2cd3193ec7f4`
- Seeds: `720161`–`720168`, all permanently retired and diagnostic only
- Games: 8 terminal, 0 runtime flagged
- Joint Rally/Bushwhacker turns: 6
- Ordering violations: 0

The profile v2 delta defers Goblin Bushwhacker only when Rally at the Hornburg is in hand, Rally has not been cast that turn, and at least four mana sources are available. The frozen control and representative opponent list are unchanged.

## Gate

Independent forced-exposure replication must use profile v2 unchanged and fresh seeds. Failure rejects profile v2 for Rally simulation; success establishes sequencing capability only.
