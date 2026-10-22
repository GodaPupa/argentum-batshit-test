# Manual continuation: authorized blockers repaired and ordinary build verified

Status: **ORDINARY_BUILD_PASSED. Copy-entry may now resume as a separate qualification.**

The user authorized the three facade substitutions and the exact historical R1 surface reconciliation,
then reaffirmed continuation during this run. Source `ebb1e2892c258dde060b27113aa113fba65dcd66`
contains the approved fixes. Its production/test changes are byte-identical to first repair source
`f0675c80f7ce6e00599c377869e2ead5df86a9be`; the later source only fixes receiving infrastructure
and preserves its failed pre-Gradle attempt. Main remains unchanged.

| Evidence | Identity |
|---|---|
| Repaired source | `ebb1e2892c258dde060b27113aa113fba65dcd66` |
| Source tree | `e1e478f3ab5f2077b16ae979606e94ef4b773063` |
| Validation-only child | `9a35cf2813f9f4ff24823e00a3b5d0b5fe01f45d` |
| Run / attempt | [39796775391 / 1](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39796775391) |
| Successful job | `119885854024` |
| Original artifact | `12434848067`, 4,531,891 bytes |
| ZIP SHA-256 | `d435d46f62ad0f8b7aedeb8b589f7667f14e4328e85935541ffbe2a6e7ff5fdf` |

The original ZIP is preserved byte-for-byte in nineteen ordered parts. Its API size and SHA-256,
ZIP CRCs, complete 4,671-entry outer inventory and both nested stage inventories were independently
checked. All 4,660 XML reports were parsed again, including eight targeted reports and 4,652 ordinary
reports. The complete named skips and required-case identities are in `INDEPENDENT_AUDIT.json`.

Targeted verification passed **359/359**, zero failures, errors or skips. It retains the original facade,
Monster Tron surface, Arcane Denial (3), Pact of Negation (3), unpaid Pact (1), snapshot and lint
assertions, plus the four new exact-pin regressions. The original tests were not edited or suppressed.

The ordinary build passed **18,135 cases**, with **0 failures, 0 errors and 43 explicitly retained skips**
(18,178 recorded cases). This includes the full rules-engine suite and every card era; those suites were
not reached by the previous failed ordinary build. The 43 skipped cases match the previous build's
skip identities and remain skips, including official execution and seed-generator opt-ins.

| Module | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| ai | 741 | 0 | 0 | 1 |
| game-server | 557 | 0 | 0 | 4 |
| gym-server | 11 | 0 | 0 | 0 |
| gym-trainer | 23 | 0 | 0 | 0 |
| gym | 493 | 0 | 0 | 35 |
| mtg-sdk | 527 | 0 | 0 | 0 |
| mtg-search | 163 | 0 | 0 | 0 |
| mtg-sets/1993-1999/tests | 713 | 0 | 0 | 0 |
| mtg-sets/2000-2002/tests | 612 | 0 | 0 | 0 |
| mtg-sets/2003-2007/tests | 1432 | 0 | 0 | 0 |
| mtg-sets/2008-2016/tests | 644 | 0 | 0 | 0 |
| mtg-sets/2017-2022/tests | 1085 | 0 | 0 | 0 |
| mtg-sets/2023/tests | 1283 | 0 | 0 | 0 |
| mtg-sets/2024/tests | 2072 | 0 | 0 | 0 |
| mtg-sets/2025/tests | 1606 | 0 | 0 | 0 |
| mtg-sets/2026/tests | 1328 | 0 | 0 | 0 |
| mtg-sets | 379 | 0 | 0 | 0 |
| mtgish-tooling | 144 | 0 | 0 | 3 |
| oracle-assay | 568 | 0 | 0 | 0 |
| rules-engine | 3754 | 0 | 0 | 0 |

The targeted Gradle invocation reported 5m49s; the ordinary invocation reported 15m23s. The ordinary
success was logged at 2026-10-22 14:57:11 UTC (07:57:11 America/Los_Angeles). The ordinary command,
invoked through the version-compatible just recipe, was:

```sh
scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
```

This is the ordinary graph with the established routine AI filter, not unfiltered benchmarks.
The Linux runner lacked shlock, so the repository wrapper reported its unlocked fallback; no
machine-global semaphore protection is claimed. Only one Gradle invocation ran at a time in this job.
The tracked working tree was clean after each stage. All thirteen original-file bindings and
all twenty-three golden files were rechecked after execution; no reblessing occurred.

The two card definitions now use the existing list facade with identical ordering/defaults. The
pure Monster Tron audit recognizes only the exact historical R1 path/hash. Original workflow bytes,
source/branch/attempt gates, expired admission date, claims, retention and callable-method checks
remain unchanged. Its result remains disabled with zero official counters.

First attempt 39795634532 failed before Gradle because just 1.21.0 does not accept the newer
working-directory setting. Its original artifact and diagnosis are retained in the source protocol.
Revision 2 uses the supported CLI option and fixes the collector's mtgish-tooling path name.
No sampled games were retried. No official seeds or games were created or run.

The saved copy-entry draft archive was reverified again: all ten hashes match, and its seven existing
base files are byte-identical at this repaired source. It was still unapplied when the ordinary gate
passed. The next qualification may recover that exact carrier slice and unchanged ten-case donor;
it must not treat this build as proof of new copy-entry code, pre-entry/batch semantics or Metamorph.

Frozen v0.7 remains `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`; Precision remains 10 used / 2 reserved.
Registry counts remain Manual92/100, opponents392/700, combined484/800; prior focused bank214/214.
Capability0/36 and primary0/864 remain unexecuted. The earlier Assay differential's 51 retained
divergences are not resolved by these repairs. The overall mission remains incomplete.

Run `python3 verify.py` from any directory to reproduce the archive, manifests, XML recount and
source/safeguard checks. This receiver executes no builds or games. This is an independent artifact
recount, not a claim of a second human or agent review.
