# Manual Transmission R4 — bounded same-state Animar choice forks

**Prerequisite proven:** identity R4 exact archival replay at commit 68a8e331c84ebf53575deab9951ec80ca75312a3, run 39454771346 SUCCESS, 1513/1513 original actions/events and full 64-action serialized checkpoint match. Exact recovered snapshots at 1153 and 1158.

This second stage is authorized only by the already-green fidelity gate. It downloads the exact R4 source receipt artifact 12280017350, SHA-256 d27a0b8bedc4377c1852cfde23f217e6b92c62a193bdf67972da7320be7ac492. No unapproved source reconstruction or new seeds.

For each state:
- Compare actual recorded chosen CastSpell vs the original scored Animar CastSpell.
- Require both actions directly accepted by ActionProcessor from precisely the same immutable original GameState. **No opponent responses are forced in these direct one-action submissions.**
- Separately gather a cheap GameSimulator quiet-boundary observation using automatic opponent passes. Explicitly label it NON-ADVERSARIAL and not opponent-responsive counterfactual proof.
- Show hand, zero pre-cast commander tax, Animar offer mana cost, whether Fblthp can be cast at the next quiet state if own-turn priority is recovered, and raw evaluator score when complete.
- Do not turn a static score or an auto-pass scenario into causal superiority, a win rate, or a demonstrated decision defect.

Zero new full games, zero modified cards, zero policy edits, zero CURRENT or main mutations, and zero automatic hardware adoption. Preserve v0.7, Cruise/Sport/Race Phase 1 hypothesis and accepted qualification, as well as R25 NO_PROVEN_DEFECT as reported discussion context.

If both legal forks finish but strategic superiority is not proven, result is INCONCLUSIVE, retain NO_PROVEN_DEFECT and propose opponent-responsive, bounded next-step evidence only when justified.
