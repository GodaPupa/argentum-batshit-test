# ARGENTUM C4 — Route B frozen partial-source review only

**Status: REVIEW-ONLY, NOT RUNTIME QUALIFIED, NOT DEPLOYABLE, NO GAMEPLAY AUTHORITY.**

- Frozen upstream package: `ARGENTUM_C4_Route_B_Integration_Successor_2026-10-17.zip`.
- Exact package SHA-256: `5fa0bf2525ab87e3d35161ef30610a53554c1f4d91a65ab5a67f496fb8be53f3`.
- Existing GitHub main at preparation: `37a603617a03dfd2d81b39dae004f7f336a70dfc`.
- Review-only candidate parent directory: `qualification/c4-route-b-review/20261017/`.
- All 56 extracted frozen-package files (55 checksum-covered members plus the `SHA256SUMS` file) are byte-for-byte copied under `frozen/`, without renaming or mutation.
- The nested parent ZIP under `frozen/inputs/` and the original test evidence under `frozen/evidence/` are part of the unchanged frozen source.
- `frozen/README.md`, `frozen/REVIEWER_B_REQUEST.md`, and `frozen/NEXT_AUTHORIZATION_REQUIREMENTS.md` control the technical review scope.

## What this is, and is not

This is an intentionally inert snapshot for independent review. It is **not** merged into the live `qualification/c4-preflight` runtime source, contains **no** new or altered `.github/workflows` files, makes **no** executable release decision, and does not change accepted source, original receipts, branch protections or experiment authorization.

The frozen source reports 60 focused synthetic tests passed and 16 of 16 weakening mutants detected, including an initial surviving mutant fixed before final freeze. These are historical packaged observations, **not** new runtime results or independently reproduced acceptance. Kernel producer/loader, trusted supervisor installation and launch, external watchdog service binding, pinned runtime artifacts and target host/operator remain unresolved. The five existing runtime barriers remain intentionally sealed.

## Verify frozen members without running qualification

From this `20261017` folder, run:

```bash
cd frozen
python3 -B freeze.py --check
```

That command verifies the original 55-file SHA256SUMS and source syntax; it does not invoke a protected compiler, operational kernel code, or gameplay. Running the inherited synthetic tests remains optional only if separately justified and reviewed; don't mistake them for enforcement evidence.

## Independent review request

Read `frozen/REVIEWER_B_REQUEST.md`. Disclose material prior involvement before claiming independence. Assess the source as **partial**, preserve the evidence boundaries, and never treat source approval as deployment or gameplay authorization.

## GitHub publication boundary

This tree is prepared locally. Remote publication and creation of a draft PR require explicit subsequent approval, including acknowledgement that the repository's `.github/workflows/ci.yml` triggers on `pull_request` **opened** even for a draft PR. No promise of zero Actions runs is made. Keep the planned ref separate from the consumed Decision B ref. A CI-suppression marker, if used later, must not be relied on as a guarantee against all workflows.
