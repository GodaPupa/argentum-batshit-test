# Shared copy-entry exception carrier: verified receipt

Status: **COPY_EXCEPTION_CARRIER_QUALIFIED** on an isolated continuation source; no merge into main,
card registration, multiplayer admission or competitive result is implied.

The held draft was recovered only after the authorized ordinary-build repairs passed. Its archive
SHA-256 and all ten original file hashes match. The unchanged generic donor's ten cases and the two
saved SDK serialization cases now compile and pass. The shared CopyExceptions carrier reaches both
existing copy-entry continuations and their existing shared applier. Legacy payloads omit empty
exceptions and card snapshot goldens remain unchanged.

## Identity and original evidence

- Source: `31e1267ef8f0398bd74f1ed1fcaa3c609113e5ce`, tree `138ca9c72631212073f65c9fe4c2af40389bb4a7`.
- Parent repair receipt: `82e78c7a8796960ac11f082ee0ccf71baf2d95a9`.
- Validation-only child: `4ead9a279306493023bb48d41619fcbbcb7b6fe8`, tree `492e836788eebd2cfbdb167860f159c1f8fe3247`.
- Run [39802649445](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39802649445), job `119907307204`: completed SUCCESS. Every stage, collector, post-binding and upload succeeded.
- Original artifact `12437803115`, `4,534,108` bytes, SHA-256 `7806821c0e87bfd39b6a3f36898783327a5bd14518ba63ce943c28a362d7dc25`.
- The original ZIP is preserved byte-for-byte in 19 ordered parts. `ARTIFACT.json` retains the API metadata, digest and ordered part sizes/hashes. `verify.py` reconstructs it in memory, checks ZIP CRC, every outer/nested manifest entry, source binding, and every XML result. Run `python verify.py` here; it runs no games or build.
- Independent artifact recount: 4,675 outer manifest entries and 4,658 original XML reports verified. This is independent of the workflow's summary counters, performed by the same assistant; no separate human/agent review is claimed.

## Receiving results

| Stage | Passed | Failed | Errors | Skipped | XML classes |
|---|---:|---:|---:|---:|---:|
| Focused carrier10 + SDK2 + unchanged snapshots/lint | 355 | 0 | 0 | 0 | 4 |
| Ordinary whole-project build + routine AI tests | 18,147 | 0 | 0 | 43 | 4,654 |

The focused cases recur in the ordinary suite; these are separate stages, not additive unique tests.
The ordinary stage has 18,190 recorded case rows. Its 43 skip identities exactly match the verified
repair baseline, including disabled official execution and seed-generation tests. SDK has 529 passes,
engine 3,764, and Assay 568. All 20 modules are present:

| Module | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| `ai` | 741 | 0 | 0 | 1 |
| `game-server` | 557 | 0 | 0 | 4 |
| `gym-server` | 11 | 0 | 0 | 0 |
| `gym-trainer` | 23 | 0 | 0 | 0 |
| `gym` | 493 | 0 | 0 | 35 |
| `mtg-sdk` | 529 | 0 | 0 | 0 |
| `mtg-search` | 163 | 0 | 0 | 0 |
| `mtg-sets/1993-1999/tests` | 713 | 0 | 0 | 0 |
| `mtg-sets/2000-2002/tests` | 612 | 0 | 0 | 0 |
| `mtg-sets/2003-2007/tests` | 1432 | 0 | 0 | 0 |
| `mtg-sets/2008-2016/tests` | 644 | 0 | 0 | 0 |
| `mtg-sets/2017-2022/tests` | 1085 | 0 | 0 | 0 |
| `mtg-sets/2023/tests` | 1283 | 0 | 0 | 0 |
| `mtg-sets/2024/tests` | 2072 | 0 | 0 | 0 |
| `mtg-sets/2025/tests` | 1606 | 0 | 0 | 0 |
| `mtg-sets/2026/tests` | 1328 | 0 | 0 | 0 |
| `mtg-sets` | 379 | 0 | 0 | 0 |
| `mtgish-tooling` | 144 | 0 | 0 | 3 |
| `oracle-assay` | 568 | 0 | 0 | 0 |
| `rules-engine` | 3764 | 0 | 0 | 0 |

The focused Gradle stage completed in 5m49s and ordinary in 14m56s. Commands were executed through
`just --working-directory "$GITHUB_WORKSPACE" --justfile "$RECORD/validation.just"` and
`scripts/gradle-locked` with no build cache, no daemon, two workers and Kotlin compile parallelism 1.
The original Gradle logs and process exit files are inside the ZIP. Ubuntu lacked `shlock`; the wrapper
reported its unlocked fallback. No machine-global semaphore protection is claimed.

Ordinary command: `scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1`.

## Assay touchstone

The same source's CLI built and `gate --limit 2000 --top 100` exited 0. It checked 2,000 cards,
2,042 faces and 3,727 ability lines: 1,647 byte-exact, 125 normalized, 1,955 declined. Ambiguity,
print mismatch, non-invertible normalization, failed full inverse and redundant readings are all zero.
578/2,000 cards were fully covered. Declined lines remain grammar gaps; this is not full card support.
Corpus gzip SHA-256 `b8c26b2741efdb30d07f188bdbe97064d73bd777c6241a57d703c0286cab1c8e`, 25,080,589 bytes.

## Safeguards and next dependency

All 17 protected file bindings, all 23 golden hashes and all ten recovered draft hashes passed before
and after execution. Original assertions and historical R1 workflow bytes/locks remain intact.
Frozen v0.7 remains blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`.

This qualifies the shared carrier only. Pre-entry publication, simultaneous-entry eligibility, copied
as-enters choices, token/X/face-down coverage and exact Phyrexian Metamorph qualification remain.
A separate local copied-choice draft is being prepared; this artifact does not qualify that draft.
Next authorized action is to prove and repair copied choices with unchanged baseline negative-control
cases, then continue the remaining entry boundaries and frozen card coverage.

Registry remains Manual92/100, opponents392/700, combined484/800; the earlier focused mission bank
remains214/214. Capability0/36, primary0/864, Precision10used/2reserved. No official seeds/games,
competitive reruns or deck changes occurred. The full mission remains incomplete.
