# Turn-Sequence v1 Pre-Execution Audit — Blocker

Harness commit audited: e0ad9d04c384bfc728f426addcb59e7bce6588aa

Blocking defect found before execution:
development_turn() calls untap_step(state), while simulate_one() calls state.begin_turn(draw) and then development_turn(). This is currently only one untap per turn, but mana payment and color metrics are recorded AFTER the policy casts a mana permanent. Therefore guildmage_castable is measuring leftover mana after development spending, not whether Guildmage could have been cast at any legal point that turn.

A second policy issue is present: choose_mana_permanent() tests affordability with can_pay_simple(), but the mutating caster pays generic mana only from lands. This intentionally excludes already-deployed rocks from funding later rocks, so the current policy underestimates development and Reversal thresholds from turns 3–6.

Required correction before accepting results:
1. implement a unified mana-payment engine that can spend untapped lands and eligible nonland sources;
2. preserve Signet input/output semantics and summoning sickness;
3. record both pre-development and post-development Guildmage readiness;
4. add regressions showing a Mind Stone can fund part of a later mana permanent and that spending it taps it;
5. only then execute the frozen sample.

No numerical results accepted.

Disposition: PRE_EXECUTION_PAYMENT_AND_METRIC_BLOCKER
