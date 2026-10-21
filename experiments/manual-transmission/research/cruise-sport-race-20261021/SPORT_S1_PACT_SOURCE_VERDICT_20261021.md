# ARGENTUM — Manual Transmission SPORT S1 Pact source observations

**Status: GREEN retrospective source inventory; NO policy or rules-engine promotion.**

Evidence: [GitHub Actions run 39672563388](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39672563388), source commit `682e97260a193ab64657db78ecab6dccde4f59aa`, artifact ID `12379948485` (`manual-sport-s1-pact-original-events-39672563388`), ZIP SHA-256 `24b3b343c2748bf328a196850b9b2311b96506bd1b5da3384188a5eed67e049e`.

The audit independently verified each of the **ten original R1/R6 ZIP hashes**, all **33,440 accepted actions**, and **13/13 Pact of Negation casts, resolved delayed triggers, affirmative five-mana {3}{U}{U} payments on the controller's next actual upkeep**. All counted payments included at least two blue mana. No historical unpaid-Pact defect was demonstrated in this set of dependent original mirror games.

**Distinct discrepancy:** All thirteen `AbilityTriggeredEvent` descriptions say **End Step**, although the frozen Pact definition's oracle and trigger specify **UPKEEP**. The actual decision and payment were observed at the next own upkeep. Do not interpret these text mismatches as automatically proving that the debt resolved at the wrong time.

A focused source inspection identified that `TriggerDetector.detectDelayedTriggers(state, step)` matches the real `delayed.fireAtStep == step` and respects `fireOnPlayerId`, but the synthetic `TriggeredAbility.create` used for event/description metadata hardcodes `EventPattern.StepEvent(Step.END, Player.Each)` even for the correctly detected UPKEEP case. This is a **bounded presentation/metadata defect candidate**, not grounds to change the original Pact payment policy.

**Next step:** One-variable, isolated SPORT S2 source fix to synthesize the actual received `step` in that metadata; test UPKEEP positive plus END-step negative, retain all previously protected scenarios, and make no full-game or production changes. Do not merge into main or the R9-qualified source without an independent integration review. No new full games; original Precision use remains **10/12**, two reserved.
