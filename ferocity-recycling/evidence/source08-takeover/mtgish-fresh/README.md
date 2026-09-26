# Source08: fresh execution of the original cached mtgish147

The single prospectively reviewed software supplement ran once and produced the same 20 classes and 147 case identities as source08's original general-CI bank: **144 passes, three unchanged data-dependent skips, zero failures/errors**. The original cached results remain preserved. This supplement supplies fresh execution of those existing software cases; it does not add new deck-performance observations.

| Identity | Exact value |
|---|---|
| Source | `3a4f99a7653839506e96d19e6639f58d9e8c5ced` |
| Source tree | `56c6b8dd46dc112cdb70db496fd9d0c3e915e8a6` |
| Three-file control | `c0124122528ca66c598cfc27a7d6c3f9bad45af8` |
| Control tree | `050dc68bdfc8014058773e48bcd4e500c9fa6811` |
| Run / attempt / event | `36278648943` / `1` / branch-creation push |
| Job | `108506146945` |
| Original artifact | `10918016982`, 2,317,681 bytes |
| Original ZIP SHA-256 | `d97a0331a0de7f6490684c31a4f3cfed1800dcaff87290edddeea0c73fce691e` |

The original ZIP contains all 28 members: all three exact control files including the hidden workflow, 20 original XML files, command/exit records, the complete command log, both executable/read-input maps, and the runtime-produced audit. Every member's length, CRC and SHA-256 is retained in `raw-audit.json`. The connector-decoded complete job log is preserved as a gzip file, explicitly distinct from an original GitHub log ZIP.

The one test process returned zero after approximately 73.9 seconds. The command used `just test-class AsPermanentEntersCounterTest --tests=* --rerun --no-build-cache --info --stacktrace --max-workers=1` with the reviewed Kotlin/JVM limits. Its raw log shows the actual `:mtgish-tooling:test` task and one complete Gradle Test Executor start/finish pair on Java 21. Every XML timestamp is inside the command window. No required test task was FROM-CACHE, UP-TO-DATE, NO-SOURCE or SKIPPED. The three skips are the original opt-in Scryfall-data cases in `EmitterSmokeTest` and `DashboardTest`; they are not passes. The 22,743-entry before and after input maps are byte-identical. This is an audit of the emitted maps and bound immutable checkout, not a claim to have independently refetched all 22,743 source bodies.

The source control review is preserved in the preceding general-CI evidence directory as `root-one-use-mtgish-control-source-review.json` (SHA-256 `43db085d2434691d3eb692cb9659d9bac32839c86ff313eba17a34b1e5b3f70d`). Its activation receipt records the initial nonmutating update-ref 422 and subsequent single successful absent-branch creation. The workflow was not rerun. `audit_original.py` only reads these completed archives and metadata; it launches no Gradle, Java, resource probe or gameplay process. `raw-audit.json` records the source-author audit, and a separately attributed independent review is required for accepting this component.

Together with the original general-CI fresh modules, this bank provides fresh execution for **17,709 distinct actual case identities: 17,657 passes and 52 declared skips**, subject to the independent receiving review. The original raw count of 17,710 includes the preserved non-test `Gradle Test Run :mtg-sets:test / CardImageUriTest` reporting marker, which is counted as zero actual cases. The receiving token bank's original 16 plus separate four cases are subsets of general CI and are not added again.

This closes only a software-test freshness question when independently accepted. Source08 remains a candidate for complete runtime/gameplay admission. The dedicated gym binary is not asserted equivalent to this general-CI build. The offline card export and full runtime/policy/protocol binding still require their own receipts, and the old wrapper freeze mismatch remains unresolved. The recovered resource calibration sequence is exhausted; scope44 adoption and any prospective technical repair remain separate governing questions. No resource/export command, browser retry, seed allocation or official D2 game is authorized by this software result. No deck, policy, game outcome or performance evidence changed.
