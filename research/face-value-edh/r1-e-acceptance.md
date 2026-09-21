# Face Value EDH — R1-E Reconstruction Acceptance

Status: ACCEPTED FOR RECONSTRUCTED-IDENTITY DEVELOPMENT; OFFICIAL HISTORICAL EXECUTION NOT AUTHORIZED
Date: 2026-09-21

## Evidence admitted
- R1-A fail-closed fixture contract: PASS
- R1-B five mandatory synthetic surfaces: PASS
- R1-C deterministic replay: PASS
- R1-D recovered card identity: 100 cards; 1 commander / 51 creatures / 15 noncreatures / 33 lands
- Reconstructed canonical SHA-256: 9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca
- Historical artifact SHA-256: 1548264c2199d2f5c376022d63200ed80a325fa0058dafaa4bfb4b4eb3d9b026
- Historical byte-equivalence: NOT DEMONSTRATED
- Reconstruction freeze run: 35654314494 (PASS)
- Reconstruction artifact digest: sha256:f41dce8150743dd32ee3d194e660dea3b30c99c7da417f156a8eb86b6c646538

## Decision
The reconstructed identity is accepted as the canonical R1 development identity for future synthetic qualification and newly defined experiments.

It MUST NOT be represented as byte-identical to the lost historical artifact.

The original historical 12-game experiment remains permanently unexecuted unless the historical artifact is recovered and verified against its historical SHA-256.

Any future competitive screen using the reconstructed identity MUST use a new experiment identity, new seed vector, and explicit provenance. It must not consume, reuse, infer, or expose the historical official vector.

## Historical experiment ledger
- historical official games executed: 0/12
- historical official seeds consumed: 0
- historical official outcomes exposed: 0
- deck changes during reconstruction: 0
- historical experiment contamination: 0

## Next gate
R2: define a fresh reconstructed-v0.11 qualification experiment under SHA-256
9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca.

R2 begins with protocol design and synthetic readiness. No official R2 seeds exist yet.
