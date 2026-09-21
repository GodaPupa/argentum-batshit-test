# Undercity diagnostic auto-trigger provenance note

Date: 2026-09-20
Branch: `face-value/lab`

Commit `c4b523c6f38a62c4275bab745a025f135b7759e4` updated the profile-v6 remediation script. Because the diagnostic workflow was then path-triggered by that script, GitHub Actions run `35567509954` auto-launched before the workflow's diagnostic vector was rotated.

That auto-run inherited already-retired diagnostic seeds `740921-740928`.

Disposition: **INVALID_DIAGNOSTIC_REUSE — NO EVIDENTIARY AUTHORITY**.

It may not be used for capability acceptance, qualification, tuning, structural diagnosis, challenger design, or promotion. The seeds were already retired by run `35567026138` and remain retired.

The workflow trigger was corrected so future profile-script edits do not auto-launch the diagnostic before seed rotation. Fresh diagnostic seeds `740941-740948` were frozen in commit `8d665125ddd91d6239559a4ce00f848ead03e15d`.
