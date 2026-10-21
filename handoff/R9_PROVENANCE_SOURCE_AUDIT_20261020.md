# ARGENTUM — R9 Provenance Source Audit (2026-10-21 UTC)

## Source-bound authority

- **Isolated repair under review:** `3594259d81212801f3f8286bcc74b7bbcb26b60c`, tree `ee72b54698e7cd5d2ab5cf4fbb0c922e63d58e42`. No merge or promotion.
- **Original immutable RED:** `62a322af07881f948e66b70e654787ccba0271b2`, run [39527875084](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39527875084), four failing real-engine witnesses, artifact `12314290600` SHA-256 `8cad805f5fefecf4b6cff8f31eef7f155bcb5c7490d1662045cb95f72b14886e`.
- **Isolated GREEN on unchanged original witness blob** `34e336056c0fee43d9849c1a1791fcf902f68e2f`: [39530385489](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39530385489), 82/82 original JUnit passed, zero failures/errors/skips, 36 mana/provenance tests plus 46 AI/initializer; artifact `12316001197` SHA-256 `b9f680da6131dc3d519b815feccf30bc72c5c006677eaed86e861212a1bd918a`. ZIP independently downloaded and parsed.

## Source review: what the code establishes

1. `ManaSolver.kt` now records selected auto-payable `ActivatedAbility` per color on `ManaSource` (including statically granted abilities) and per actual used source on `ManaSolution.activatedManaAbilities`. This closes the four original witnesses at source and execution level. Actual selected abilities are used for both printed-life preflight and side-effect execution.
2. `ManaAbilitySideEffectExecutor.kt` distinguishes missing internal production from produced colorless mana, and consumes `activatedManaAbilities` where present. It avoids the historical unrelated printed colorless ability selection and handles selected free granted abilities separately from printed pay-life modes.
3. The `ManaSolver` internal-activation funding pass rejects candidate source modes that themselves require another mana activation payment (`additional == 0` gate). This is **fail-closed**, **not general recursive-funding support**.
4. The source review does not establish correctness of arbitrary same-color multi-mode aggregation, untested mana-amount/bonus/restriction combinations, or positive recursively funded activation chains. They remain out of scope; do not infer that 82/82 certifies the general solver.

## Final independent regression supplements

- **FINAL SOURCE-BOUND REGRESSION SUPPLEMENTS GREEN:** [39533632153](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39533632153), branch `research/manual-precision-r9-final-qualification-20261020`, workflow-only source `f723f60e6fbe97582fd76d0a3480070dff8f5d2d`, same production engine blobs as `3594259d`. GitHub two jobs SUCCESS; downloaded source-bound JUnit ZIPs confirmed **25/25 fetched-shock** (artifact `12316193204`, SHA-256 `bc5a3cace62154b03cc82ab21579955f2852e776344620745911c90599d7e48a`) plus **16/16 restrictions/granted/provenance** (artifact `12316746939`, SHA-256 `7f72b9860c04e6b5ec47e11822f47ef3ce3179c5c56040b012b465cb83ae5ad1`); zero failed/errors/skipped. Scope 123/123 original+supplement JUnit across source-equivalent research branches; no promotion.
- **NEW source-audit generic activation-cost RED — integration-review BLOCKER:** [39534031813](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39534031813), source `b2c7d2fa3a43775d8ef49b8ca5a18882d61d82d8` descended from `3594259d` with ONLY a new test file and no-game workflow. **2/3 new tests failed**, positive Forest control passed. Single `Mossfire Valley` ({1}, {T}: Add {R}{G}) was wrongly allowed to fund spell generic {1} without paying its activation cost; both direct ManaSolver and real cast path reproduced. Original JUnit artifact `12317356263`, ZIP SHA-256 `90862396415a842175da2ff1b444f13c93dff63cf6f327a1e7bb25c46105cfcd`, independently verified. Original 82/82+41/41 regressions remain valid but do NOT cover this separate defect.
- **Isolated opt-in minimal fail-closed generic repair LAUNCHED, not proven:** branch `research/manual-precision-r9-generic-activation-repair-20261020`, commit `b943528be65975003be79aadd0081faa3fc2aee3`, tree `915dd678e68a629b28314e1c74498b244bd4a03c`, [run 39534983181](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39534983181). Refuses mana abilities carrying an unpaid activation-mana cost in late generic-payment pass; preserves free-mode alternatives, all original four provenance witnesses, and immutable RED. Three independent no-game jobs cover core/R5, fetched shocks, and restrictions/grants. **Not GREEN until original JUnit, source, and artifacts are verified.**

## Qualification decision

**Current status: ISOLATED GREEN 82/82 on repair source plus 41/41 source-equivalent supplements, but GENERAL ENGINE INTEGRATION REVIEW ON HOLD on newly proven generic-pass RED. New fail-closed opt-in repair b943528b has no verified JUnit receipt yet.**

- Preserve R9 v2 `ab660bd77fe451716c5126e09cdf14b30b81879b`, the affordability alternative `c5075f5762d38276cd4c621894a612630a8fe10e`, and the provenance-preserving alternative `ab864c6ada83a7ea2a1c78f5821af9c3727c4e3a` independently.
- No main, CONTROL, CURRENT, accepted R5, frozen v0.7, decklist, or official gameplay qualification changes.
- Full-game budget unchanged: **10/12 consumed, 2 reserved**. All listed checks use deterministic no-game engine tests.
- This audit records source-reviewed boundaries only. It does not authorize merges, promotions, or additional full games.
