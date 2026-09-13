# Project Pest Control experiments

This directory is reserved exclusively for Project Pest Control experiment protocols.

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

This does not authorize challenger construction, opponent self-play, another fresh Sample #1, Sample
#2, policy correction, replay, or optimization. The permanent Pest Control v1.0 control in
`docs/lab/PEST_CONTROL_STATUS.md` must not be silently changed.
