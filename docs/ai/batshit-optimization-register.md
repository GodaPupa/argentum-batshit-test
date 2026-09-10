# Batshit Economics optimization register

The original Batshit Economics list at validated baseline
`49c2efa0677f5a39b9e317b08e65b7d217d8b85e` is the permanent control. Optimization variants do
not replace it without a separate promotion decision.

## Experiment D

- Status: **not promoted; not selected for replication**.
- Incumbent: Variant C (`-1 Village Rites`, `+1 Shambling Ghast` relative to the permanent
  control). Variant C remains the provisional incumbent and is not written back into the permanent
  control.
- Variant D difference from incumbent C: `-1 Not Dead After All`, `+1 Greedy Freebooter`.
- Paired result: 29 both win, 60 both lose, 5 incumbent-win/variant-loss, 6
  incumbent-loss/variant-win; net D flips **+1**.
- Disposition: Greedy Freebooter successfully produced the hypothesized cheap-permanent and
  death-value behavior when observed, but removing NDAA #3 also removed materially valuable
  engine-rescue lines. The net paired result does not justify promotion.
- Seed retirement: all 100 seeds in
  `gym/src/test/resources/batshit-optimization-d-seeds.csv` are permanently retired from future
  development, regression, smoke, baseline, replication and optimization samples.
- Preserved workflow: GitHub Actions run
  [34422779381](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34422779381), artifact
  `batshit-optimization-experiment-d` (artifact id `10132876584`, SHA-256
  `09661a90bb31e8d41bd947642da01f439de78f42ce3d8a384b4557a58407a8ff`). The artifact contains
  `paired-results.log`, `paired-results.jsonl`, and a copy of the frozen seed vector.

No Experiment D replay or replication is authorized by this disposition.

## Experiment E

- Status: **completed; not promoted; telemetry-qualified**.
- Incumbent: Variant C (`-1 Village Rites`, `+1 Shambling Ghast` relative to the permanent
  control), which remains the provisional incumbent.
- Variant E difference from incumbent C: `-1 Unearth`, `+1 Greedy Freebooter`.
- Paired result: 32 both win, 64 both lose, 2 incumbent-win/variant-loss, 2
  incumbent-loss/variant-win; net E flips **0**.
- Validation qualification: coded rules/state/raw-trace and Flamebreather/Guttersnipe telemetry
  assertions passed, but manual audit found that the printed Mirkwood Bats summary included two
  NDAA rescue triggers in each arm. Correct token-transaction totals remain recoverable from the
  raw trace.
- Seed retirement: all 100 seeds in
  `gym/src/test/resources/batshit-optimization-e-seeds.csv` are permanently retired from future
  development, regression, smoke, baseline, replication and optimization samples.
- Preserved workflow: GitHub Actions run
  [34435159984](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/34435159984), artifact
  `batshit-optimization-experiment-e` (artifact id `10137046085`, SHA-256
  `f2b35ca1b18ee68c7804eebc12ca6f9cc8cbf96129c5adea53248a1b5347c919`).
- Full audit: `docs/ai/batshit-optimization-experiment-e.md`.

No Experiment E replay or replication is authorized by this report.
