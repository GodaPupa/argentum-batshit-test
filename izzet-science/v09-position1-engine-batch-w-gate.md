# v0.9 Position 1 — Engine Coverage Batch W Gate

Purpose: qualify Lose Focus by adding Replicate to the generic repeatable optional-cost rail and
reusing the already-qualified spell-copy/retargeting infrastructure, without changing Izzet Science v0.7.

## Frozen boundaries

- Izzet Science v0.7 remains byte-identical; SHA-256:
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- Card-control disposition remains KEEP_V07 / no card changes.
- Accepted Batches A through V remain unchanged.
- Batch V is accepted from Actions run 35926252699 and audited artifact
  `izzet-v09-position1-engine-batch-v` (SHA-256 digest
  `1b3140168c37bf892311a93d76fd9b158b0e6ae96c7923e5fac5f6b29cb0064e`).
- Batch V accepted source SHA is `3124c6c9eaa7083889342a70ec5487183eaaa233`;
  acceptance record HEAD is `876a7c90e82476321c94a25b1282ba64488d24fa`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.

## Batch W triage

Lose Focus is the final unresolved Izzet identity.

The engine already has:
- the Batch-V generic repeat-count rail for repeatable optional mana costs;
- durable cast-choice identity/count storage;
- real spell copies on the stack;
- StormCopyEffect with per-copy independent retargeting;
- counter-unless-pays semantics.

Batch W adds only:
- Keyword.REPLICATE and a distinct ChoiceSlot.REPLICATED;
- KeywordAbility.replicate(cost) on the existing repeatable optional-cost rail;
- a generic self-cast Replicate trigger whose copy count is the paid repeat count;
- Lose Focus expressed entirely from those reusable primitives.

No Lose Focus-specific executor is justified.

## Acceptance

1. Lose Focus is {1}{U}, Instant, Replicate {U}.
2. Its base effect is counter target spell unless its controller pays {2}.
3. Replicate payments use ChoiceSlot.REPLICATED and never count as kicker payments.
4. A repeat count N adds N copies of the replicate mana cost.
5. Public legal actions surface every affordable positive Replicate count while preserving the spell's target metadata.
6. Casting a Replicate spell creates one Replicate triggered ability even when N=0.
7. Resolving that trigger creates exactly N real spell copies on the stack.
8. Replicate copies are not cast and do not increment spell-cast counts.
9. Each copy may independently choose a new legal target.
10. Existing Storm copy, Conspire, Casualty, and Multikicker behavior remains unchanged.
11. Canonical MH2 snapshot is reblessed through a fail-closed workflow.
12. Full golden card snapshots pass.
13. Expected post-implementation unresolved count is exactly 30.
14. Lose Focus is absent from unresolved output; no Izzet identity remains unresolved.
15. Official games/seeds/outcomes remain 0/0/0 and the exact v0.7 control remains unchanged.

Infrastructure, compilation, fixture, snapshot, or semantic failures are qualification history only,
not evidence about deck strength.
