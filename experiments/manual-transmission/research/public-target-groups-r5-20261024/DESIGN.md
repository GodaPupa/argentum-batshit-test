# Public target groups R5: frozen recorder compatibility

This successor repairs the diagnostic recording incompatibility proved by the original R4 failure receipt fc3ba6a0e5ca7394eb075b55c013dfcd117305d5. It does not change engine behavior, the frozen golden, its assertions, original failed evidence, or prior tests.

The only executable change is in TableGameRunner's test-side recorder. CastSpell with both new provenance fields null retains the historic diagnostic bytes by removing their exact terminal suffix. Non-null groups (including empty groups), non-null server history, other actions, and embedded text remain fully recorded. No target layout is inferred and no resolver code changes. The existing frozen hash and outcome assertions are the behavioral regression oracle; four new boundary tests enforce the compatibility domain.

Qualification starts with exact source/protected-byte bindings, client34 and normal engine compilation. The original FrozenBaselineTest and four new recorder cases execute early, followed by actor39, targeted20, normal/mutant compilation and the unchanged executable visit-loss control (20 rows,15 passes, five source-pinned assertion failures). Restore and rebind before focused820, broad14883, ordinary18502 passes with the exact43 baseline skips, and unchanged Assay gates. Ordinary receiving subtracts only the four added ai identities before checking the unchanged original baseline counter. The frozen test file is explicitly SHA256-bound to R4.

Source parent is R4 e4efe8573086090bf39aa52a1a0ce6196c121914. Recovery delta is exactly the modified recorder, new four-case test, and these three protocol/design/gate records. All previous implementation, assertions, mutation frames and protocol records remain intact. Green workflow is not acceptance: original artifact bytes, manifest, raw identities/counts, executable failures, restoration, protected bytes and every declared gate still require one receiving audit.

The inherited scope remains bounded public target-group transport and original server capture. It does not admit occurrence-aware resolution, retarget execution, Spellskite, multiplayer or official games. Frozen v0.7, main,144 bindings,23 goldens,82 legacy fixtures, protected StackResolver, statistical design, budgets and both reserved Precision games remain unchanged. Official games/seeds:0.

This is a same-owner source review, not independent review. The existing single qualification concurrency group, lock recipes, resource limits and admission process are retained.
