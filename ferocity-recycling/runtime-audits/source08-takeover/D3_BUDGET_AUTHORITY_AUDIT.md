# Ferocity D3 budget reconciliation — authority audit

Record cut: 2026-09-26 UTC. Inspected source: `3a4f99a7653839506e96d19e6639f58d9e8c5ced`, tree `56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6`.

**The effective D3 ceiling is 240 games. This audit changes no protocol or budget.** The detailed `protocols/RESEARCH_PROTOCOL.md` retains its historical 480-game draft. The already-adopted `protocols/RECONCILIATION_AMENDMENT.md` explicitly takes precedence over that passage, and `protocols/ACTIVE_CONTRACT.json` binds the original protocol, detailed design, amendment and initial-list manifest.

The amendment was published in commit `901729f259d044dfa157f745b8d764cb454922ef` on 2026-09-26 at 06:51:07 UTC, with original initialization `3afd83c8741c5b3d89f231543e951c5f204ca4ef` as its parent. The live Git comparison confirms `901729f2` is an ancestor of source08; no later change to the amendment appeared in its path history. The independently recorded adopted-amendment addendum in `reviews/concurrent-protocol-review.md` verifies this same 720/240 design and the original hash bindings.

All four bindings in ACTIVE_CONTRACT were recomputed from the fetched immutable Git bytes and matched:

| Input | SHA-256 |
|---|---|
| Original PROTOCOL.md | 69a4018e9831fbd2d163ee441752d387adc4f8af9354b18f88285f4fe9a01699 |
| Historical detailed RESEARCH_PROTOCOL.md | ab663b6b5f3d4761469bb2a9fcfd73a0bffd74efd9bfdb80b2de94640b59cee5 |
| Operative RECONCILIATION_AMENDMENT.md | 2829d4623e142cdf427339b4e19f8bc8f96747410eeccb1abe1287986b35458f |
| initial-search-manifest.json | ef75f5cec859dcc9f765de0feacb7cb7696037f4c7035d4b68b6bdfccee1c87d |

The active-contract bytes hash to `8d0551bb3fc5e4a9492540412fec17284bb8311d3501d0baf014da3969cc15d4`, matching the adopted-amendment review.

D3 admits at most two surviving candidate/comparator pairs. Each side receives at most two **new** variants and 30 games per new list, six per opponent with three play/three draw. Thus 2 survivors × 2 variants × 2 sides × 30 = 240 games. Incumbents retain their D2 exploratory records without resampling. Unequal D2/D3 precision stays explicit; unused capacity never transfers. The amendment's symmetric one-third pressure-cell regression rule and post-D3 stopping criterion remain binding. Sideboard development S precedes E and the final 75s/policies freeze before evaluation.

The current 240 entry in Ferocity's status table is therefore consistent with effective authority, although its surrounding implementation status is stale. The handoff's concern has been reconciled through prior authority, not resolved by selecting a convenient sample size. This audit supplies no gameplay permission. The inspected seed ledger remains empty with zero randomized seeds, attempts, completions and exposed outcomes.
