# ARGENTUM C4 — V6.3 explicit source-bound release prerequisite (NOT an authorization)

**Current decision: PREPARED / LOCAL-ONLY / NO PUBLICATION.**

Prior source review of V6.2 identified the target GitHub runner as UNKNOWN_NOT_PROBED. That review did not grant hosted or operational permission. The later generic “Proceed” advanced this local candidate preparation only. No approval is forged here.

Before any GitHub source publication or push activation of the staged child, the controller must obtain an explicit owner authorization that identifies **all** of:

1. Frozen V6.3 source SHA/tree/parent and complete ZIP SHA-256.
2. Separately staged exact workflow-child SHA/tree/sole parent, path, workflow Git blob and artifact schema.
3. Branch `infra/argentum-c4-v63-readonly-once-20261015`, event `push`, original-only run_number=1 and run_attempt=1, no reruns, fixed ubuntu-24.04 GitHub-hosted runner, timeout, concurrency and `permissions: contents: read`.
4. The exact SHA-256 of both embedded read-only Python sources, the whitelist of `/proc`/`/sys` metadata reads, and an independent source review of this exact child.
5. A separate *publication* decision and a separate *one-original activation* decision. Neither is contained in this document, the user’s generic “Proceed”, earlier source reviews, nor an environment variable.

If any prerequisite is absent, **stop**. Do not create a branch, move a ref, publish a workflow, dispatch Actions, ask a privileged daemon to run, or execute a protected compiler. On an eventual failure, preserve the original and mark INCOMPLETE without rerun or replacement. No host disclosure may be promoted to D2 qualification or C4 PASS.
