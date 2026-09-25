# Monster Tron operational stack: qualification repair

Protocol: `PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`.

The operational stack is still seed-free validation. It exposes no official runner or automatic
execution workflow. The accepted four-game seed vector and deck identities remain unchanged.

## Exact opponent policy reaches production

The original policy audit instantiated `PestMonsterTronAdvisorModule` from test sources, while the
operational driver instantiated the generic production profile for both decks. Consequently, the
five qualified Monster Tron choices were not present in the production driver.

The same advisor definitions now live once in AI production sources. `PestMonsterTronPolicy.profile`
preserves the qualified profile ID and advisor composition; both the original five policy scenarios
and the operational driver use this exact value. The driver assigns it only to Monster Tron, in
either seat. Pest Control keeps its existing production profile. No global AI baseline changes.

## Rejected attempts retain evidence

A resource cap, rejected action, or engine exception rejects execution. The driver now carries its
observed transcript, mulligan-action count, actual terminal state, and failure reason in a typed
failure. A processor exception records the action that was attempted with an execution-error field
and no invented or stale emitted events. No winner is assigned to an unfinished game.

The coordinator retains these failed raw bytes separately from completed games. The durable journal
can persist the failed raw bytes and their digest with the rejection marker. Write errors propagate;
existing evidence is never overwritten, and an invalid or failed transition permanently closes that
journal instance. A later official wrapper must wire this rejected evidence into its artifact contract.

## Validation required before integration

- The original synthetic operational and read-only official artifact checks.
- All five existing Monster Tron policy scenarios against the production profile.
- Production profile selection for both Monster Tron seats.
- Action-cap and engine-exception transcript preservation.
- Coordinator stop after the first failed synthetic assignment.
- Duplicate durable claims, out-of-order transitions, failed raw persistence, and no-clobber writes.

These are deterministic regression fixtures, never official sampled games. Local Kotlin execution
requires the repository's existing build tools; authoritative qualification runs use the existing
GitHub Actions workflows. A green result qualifies the repaired stack only. Official gameplay still
requires the separately reviewed one-shot execution surface described by the accepted authorization.

The future official entry must bind the full assignments to the exact pinned archive loader,
recompute the vector digest, verify the freeze source, and wire the durable attempt journal before
initialization. Synthetic construction primitives alone are not an official execution boundary.
