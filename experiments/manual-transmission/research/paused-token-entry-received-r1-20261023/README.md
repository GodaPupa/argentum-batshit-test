# Paused-token R1 receiver preparation

**Prepared; no qualification is claimed.** These scripts receive the original artifact of run `39943006929`, attempt 1, for source `e1a8943913cf36cc5e6b76f6c6f4df2279188b53` and validation `7f5559650fd960e42792a3728ec7c6d900390e64`. No artifact has been downloaded or examined by this preparation task.

`RECEIVER.json` pins ten local inputs copied directly from immutable Git objects, their byte lengths and SHA-256 values, both Git trees, the exact source manifest, and expected gate counts. The original source protocol supplies all 70 individual baseline verdicts and first assertion frames. The 48 original controls and 22 supplemental controls remain separately retained. Scripts adapt the previously received Counter/Metamorph original-byte audit and differential parser.

Required receiving inputs, supplied by the execution owner after the run finishes:

- `ARTIFACT.json`: original GitHub artifact metadata, including actual numeric `id`, `name`, `size_in_bytes`, `digest`, and `workflow_run` identity. Add exactly one storage locator: `zip_file` with the local original ZIP filename, or `parts` with ordered `{name, bytes, sha256}` records. Every byte is rechecked against GitHub's archive digest. Preserve all other API fields; do not infer missing metadata.
- The original ZIP, or every byte-exact chunk named by `parts`. Retain the original archive even for failed execution. The verifier reads it in memory without extracting or executing payloads.
- `RUN.json`: complete original GitHub run response with a `jobs` array containing actual job IDs, statuses, conclusions, and all step results. The sole job's ID is deliberately awaiting observed metadata.
- `ALL_RUNS.json`: complete `{total_count, workflow_runs}` inventory for this exact validation SHA, including the original run and every event type. `DUPLICATE_RUNS.json`: an array of all other runs with their expanded `jobs` and steps; use an empty array only after confirming no others exist. Every secondary run must be verified skipped by the one-shot guard.
- A local Git repository containing both pinned commits, baseline `418cdfdacd5191549e9fc8e4a071330120b071b7`, and their immutable files. `--repo` selects it explicitly; no checkout mutation occurs.

Write the receiving records once, then reproduce them read-only (replace the repository path if moved):

```sh
python3 audit_git_source.py --repo /workspace/scratch/8a30921d19aa/paused-token --write
python3 compare_differential.py --write
python3 verify.py --repo /workspace/scratch/8a30921d19aa/paused-token --write
python3 audit_git_source.py --repo /workspace/scratch/8a30921d19aa/paused-token
python3 compare_differential.py
python3 verify.py --repo /workspace/scratch/8a30921d19aa/paused-token
```

Only after inspecting `RECEIVING_AUDIT.json`, require qualification explicitly:

```sh
python3 verify.py --repo /workspace/scratch/8a30921d19aa/paused-token --require-qualified
```

A read-only reproduction can succeed for a truthful `QUALIFICATION_INCOMPLETE` receipt. This does **not** qualify the component. `--require-qualified` fails whenever any gate remains failed or unverified. Missing stages, compilation/setup exceptions, changed negative frames, failed run/job/steps, malformed payloads, and incomplete metadata remain explicit blockers. Original XML, errors, logs, skips, and corpus stay in the original archive; all payload hashes and observed failing cases enter the receipt. Do not rerun the qualification automatically.

The required counts are predictions until original reports satisfy them:

| Gate | Required observation |
|---|---|
| Original + supplemental baseline | 70 rows, 44 exact expected assertion failures, 26 passes, no errors/skips, 5 classes |
| Focused | 686 passes, exact inherited and new case identities, 6 modules |
| Broad | 14,753 fresh passes; engine 3,978 and all nine card eras, 10 modules |
| Ordinary | 18,361 passes, unchanged 43 skip identities, 20 modules |
| Source | 16 manifest paths, 144 protected bindings, 23 unchanged protected goldens, original frozen deck blob/plaintext |
| Assay | 2,000-card touchstone, required zero error metrics, retained and hashed raw corpus |
| Differential | All original 51 block interiors unchanged under the preserved parser; block-edge whitespace trimming is explicitly disclosed |

`GIT_SOURCE_AUDIT.json`, `DIFFERENTIAL_COMPARISON.json`, and `RECEIVING_AUDIT.json` are generated only by explicit `--write`. No script dispatches Actions, runs a build/test/game, generates an official seed, modifies source, commits, pushes, or calls the network. Read-only commands disable Python bytecode writes.

This is a separate receiving pass by the same assistant team, **not external independent review**. A qualified result admits only the bounded source/target-copy paused-entry component. Complete token producers/replacements/continuations, actual UI interaction, complete cards, multiplayer composition, pilots, and competitive claims remain outside this receipt. Zero official games/seeds; both Precision reservations remain protected. Registry evidence is inherited source-specific evidence and is not recounted here.

When publishing, retain the original artifact bytes and API metadata, these exact scripts and frozen inputs, and the generated reports on an isolated branch. `BASELINE_DIFFERENTIAL.log` must be explicitly force-added if the repository's `*.log` ignore rule applies. Do not describe this preparation directory or an unreceived artifact as a qualified checkpoint.
