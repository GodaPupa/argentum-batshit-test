# Oracle Assay repair: received source and controls, ordinary build pending

Source `6cf30d14d4be570bc1786e48079549cf850fa16a`; validation-only child `0317a91b0bdbbebf386d1d035936e1e26c67e627`; original [Actions run 39787740747](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39787740747), attempt 1. The user explicitly authorized repair of the four existing printer failures. Original failed whole-build evidence remains at `f57d961780ab196f2c6d74269a9c9f9d752de80e`.

The SDK's search facade emits `AuthorizedLibrarySearch`; the old printer required `FromZone`. The repair changes and shares the extraction helper while preserving complete recipe reconstruction. Original test files are byte-identical, and six additional cases cover SDK-model-first printing/parsing and refusal of semantic changes.

| Evidence | Verified result |
|---|---|
| Old-production negative control, job `119855227444` | 31 cases: 24 passed, exactly 7 expected failed, no errors/skips. Four original failures plus three new positive regressions fail; all three new refusal controls pass. This job succeeds because the expected failures were received, not because all tests passed. |
| Repaired source, job `119855227240` | 568/568 Assay cases, 39 XML classes, zero failures/errors/skips. |
| Full corpus touchstone | 35,085 cards, 35,985 faces, 65,129 ability lines; 28,539 exact round trips, 1,990 variants, 34,600 declines; zero ambiguity, print mismatch, normalization/inverse failure, or redundant reading. 9,645 cards fully read. |
| Full golden differential | 13,223 definitions; 6,667 compared: 6,616 confirmed and 51 divergent. 5,790 grammar declines, 140 unmodelled-script, 70 non-folding, 339 multi-face, 2 absent current Oracle entries, 215 text mismatches; zero undecodable goldens. Findings classified in `CLASSIFICATION.md`, none suppressed. |
| Ordinary whole-project build, job `119857846056` | Running at this checkpoint. No success claimed; copy-entry remains held pending its qualified result. |

Both original ZIPs are preserved verbatim. `verify.py` checks API archive size/hash, unique members/CRC, complete internal manifests, actual XML counts, unchanged original test blobs, frozen-deck identity, source pins, expected production mutation in the control, and all gate exits. `VERIFIED.json` is this agent's receiving audit, not an independent human/agent review. Independence comes from separate negative-control execution and model-first tests.

Current corpus SHA-256: `b8c26b2741efdb30d07f188bdbe97064d73bd777c6241a57d703c0286cab1c8e`. Corpus cache bytes are identified in the original artifact but are not included in this checkpoint. These current-source grammar observations are not an adopted official-game rules/card snapshot.

Next: receive and audit the original ordinary-build artifact. Resume the preserved copy-entry work only after success, then qualify remaining frozen cards, opponent coverage, multiplayer mechanics, pilots and full-game replay/telemetry/admission. The mission registry remains Manual92/100 and overall484/800; the 214/214 focused mission count is unchanged. Official capability0/36, primary0/864; no official seeds or games created. Precision remains 10 used / 2 reserved. Frozen v0.7 and official protocols remain unchanged.
