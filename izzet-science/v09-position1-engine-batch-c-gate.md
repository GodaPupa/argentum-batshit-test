# v0.9 Position 1 — Engine Coverage Batch C Reconciled Gate

Purpose: qualify a seed-free shared-mechanic coverage batch after accepted Boreal
Druid and Llanowar Visionary coverage.

## Concurrency reconciliation

During construction, two compatible engine-support paths landed on the same research
branch before Batch C had executed or exposed any outcome:

- Veteran Beastrider: Owlbear.
- Izzet Science: Archaeomancer and Mnemonic Wall.

No official seed was revealed, no game was initialized, and no outcome was exposed
before this reconciliation. Treat the three-card set as one Batch C qualification
rather than accepting competing Batch-C identities.

## Frozen boundaries

- Izzet Science v0.7 control remains byte-identical; SHA-256 must remain
  `726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`.
- No official seed may be revealed or consumed.
- No official game may be initialized.
- No outcome may be exposed.
- Card-control audit disposition remains KEEP_V07 / no card changes.
- Historical Batch A and Batch B evidence remains unchanged.

## Batch C hypothesis

Using already-supported or directly reusable engine primitives:

- Owlbear adds creature characteristics, trample, and ETB draw.
- Archaeomancer adds mandatory ETB instant/sorcery recursion.
- Mnemonic Wall adds defender plus optional ETB instant/sorcery recursion.

The combined batch should reduce live unresolved real-engine coverage from 53 to 50
without modifying either frozen deck identity or the five PDH execution blockers.

## Acceptance

1. Owlbear resolves as a 4/4 with trample and one ETB draw trigger.
2. Archaeomancer resolves and its deterministic scenario returns a targeted instant
   or sorcery from the controller's graveyard.
3. Mnemonic Wall resolves and deterministic scenarios prove both accepting and
   declining its optional recursion.
4. Full golden snapshots pass after a fail-closed canonical rebless limited to
   AFR.json, M13.json, and ROE.json.
5. Live unresolved count is exactly 50.
6. Boreal Druid, Llanowar Visionary, Owlbear, Archaeomancer, and Mnemonic Wall are
   absent from unresolved output.
7. Official counters remain zero.
8. The exact v0.7 100 remains unchanged.
