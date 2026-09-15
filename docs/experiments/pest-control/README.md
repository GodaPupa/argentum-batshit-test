# Project Pest Control experiments

This directory is reserved exclusively for Project Pest Control experiment protocols.

Pest Control versus SoterX Mono Red Madness matchup Block A executed once and is formally rejected
as `REJECTED_OUTCOME_RELEVANT_TARGET_SELECTION_DEFECTS`. All 50 Block A seeds are permanently
retired. The descriptive 35–15 result is inadmissible for acceptance, pooling, or tuning. Preserved
provenance and the controlling disposition are recorded in
`matchup-block-a-rejection-audit.md`; no replacement vector or gameplay is authorized.

Goldfish Sample #1 was authorized as a 30-game development/engine baseline candidate using the
permanent Pest Control v1.0 control and the validated Phase 3 solitaire policy. Its seed vector was
frozen in `goldfish-sample-1-seed-freeze.md` before execution. The sample was rejected by audit and
is not a baseline. The complete rejected report and raw telemetry are retained for investigation.
After the approved general corrections, a clean exact-order replay was accepted solely as
regression evidence; its audit is preserved in `goldfish-sample-1-regression-accepted.md`.

The original 30 seeds are permanently retired. They must not be rerun, optimized against, or used for
future performance inference.

A separately authorized fresh Sample #1 froze 30 new, collision-audited seeds and executed them once
from fully green preflight head `1ecb976cb41c369edf195681e4c9d80ddf7e894f`. The automated rules,
state, provenance, and accounting audit passed, but manual agent-sanity audit found strategically null
Weather the Storm casts with no payoff and no survival pressure. The entire fresh sample is formally
rejected and preserved intact in `goldfish-sample-1-fresh-rejected.md` and `.json`; none of its clock or
engine aggregates are accepted as baseline evidence.

The approved general Weather-policy correction is documented in
`goldfish-sample-1-weather-policy-correction.md`. Its one authorized exact-order replay is clean and
preserved separately as `goldfish-sample-1-fresh-regression-replay-accepted.{md,json}`. The replay is
regression evidence only; the original remains rejected as performance evidence and this vector is
permanently retired. This does not authorize challenger construction, opponent self-play, another
fresh Sample #1, Sample #2, further replay, or optimization. The permanent Pest Control v1.0 control
in `docs/lab/PEST_CONTROL_STATUS.md` must not be silently changed.

After formal acceptance of that Weather regression replay at
`8a13f2d8f9322b85ba6c52de571c591fea966a31`, a separately identified fresh performance Sample #1
was authorized. Its 30 completely new seeds, collision audit, immutable execution constraints, and
pre-execution state are recorded in `goldfish-sample-1-performance-seed-freeze.md`. This authorization
does not permit challenger construction, optimization, Sample #2, or opponent self-play.

That vector executed once from green preflight head `9f3f3c1cc8270c3bbd55b8017c0935dbbee7771f`.
The automated audit was clean, but Game 15 exposed a strategically null Storm-0 Weather cast and a
missed next-turn Weather-before-Follow enhanced line. The entire performance sample is rejected and
preserved without exclusions in `goldfish-sample-1-performance-rejected.{md,json}`; the controlling
manual audit is `goldfish-sample-1-performance-audit-rejected.md`. Its vector is permanently
disqualified from performance/baseline evidence.

The separately authorized focused correction is documented in
`game-15-lifegain-policy-correction.md`. It extends the general null-lifegain resource policy to
structurally pure activated abilities and validates the complete Weather/Food/Follow decision chain.
Its separately authorized same-seed replay is preserved as
`goldfish-sample-1-performance-regression-replay-rejected.{json,md}` with the controlling audit in
`goldfish-sample-1-performance-regression-replay-audit-rejected.md`. Game 15 was corrected, but Game
8 exposed a pending-stack form of redundant pure lifegain, so the replay is rejected. The vector is
permanently retired from any further execution and remains unusable for performance, optimization,
sampling, or comparison. No replacement vector, challenger construction, optimization, Sample #2,
or opponent self-play is authorized.
