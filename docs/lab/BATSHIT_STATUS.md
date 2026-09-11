# Batshit Economics Laboratory Status

> Workspace scope: Batshit Economics and opponent-validation/optimization only. Project X branches/resources are read-only only when required to understand a shared Argentum engine/agent change. Do not write to, generate seeds for, or trigger workflows on Project X.

- **Active branch:** `affinity/grixis-policy-regression`
- **Current validated head:** `51d2446d2f9c0f0595574f8f489d10a8485be8e4`
- **Permanent control:** Existing locked Batshit Economics permanent control. No control mutation is authorized by the active regression gate.
- **Provisional incumbent:** Variant C, frozen for the active Grixis Affinity gate.
- **Current opponent:** Grixis Affinity.
- **Current experiment/regression gate:** Same-seed policy regression validation for Galvanic Blast, Nihil Spellbomb, and Krark-Clan Shaman. Reckoner's Bargain remains unchanged after its focused audit.
- **Frozen seed vector / retirement status:** Existing exact 100-seed Grixis preboard regression vector is frozen and active; it is not retired. No new seed generation, substitution, reordering, or fresh Affinity sample is authorized until this gate is clean.
- **Current blocker:** Preserved Sample #1 artifact shows the workflow did run the frozen games, but report generation then crashed with `NumberFormatException: For input string: "0; Craft casts: 0"` in `humanSampleReport` / `summaryPair` at `BatshitGrixisAffinitySampleOneTest.kt:157`. This is a report-parser/harness defect, not yet evidence of a Galvanic Blast, Nihil Spellbomb, or Krark-Clan Shaman policy defect. Sample #1 remains rejected as matchup evidence.
- **Next authorized action:** Await authorization to make the minimal report-parser/harness correction needed to recover the preserved Sample #1 report, then inspect the already-preserved same-seed traces for the three authorized policy behaviors. Do not generate fresh seeds or a fresh sample.
- **Relevant general Argentum changes:** Track only shared engine/agent changes relevant to this gate. Project X may be inspected read-only only if such a shared change must be understood.
- **Latest CI / validation:** Argentum Validation #130 passed at `51d2446d2f9c0f0595574f8f489d10a8485be8e4`; Grixis Affinity Sample #1 run `34534520319` failed after the 100-game execution while constructing the human report. Artifact `batshit-grixis-affinity-preboard-sample-1` uploaded successfully and preserves the raw traces.
- **Coordination-only commits:** `9277392564e786326449b159537d7ef1fcaf60dd` added this status file; `832e65bbb2d6617f2959e2292a7a6e7956c7c335` added the frozen regression protocol. These are not validated gameplay heads.
