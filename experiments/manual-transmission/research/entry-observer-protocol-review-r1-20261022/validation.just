negative:
    scripts/gradle-locked :rules-engine:test --tests '*EntryObserverBoundaryScenarioTest' --tests '*EntryObservationLifetimeScenarioTest' --tests '*PendingEntryObserverScenarioTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

focused:
    scripts/gradle-locked :rules-engine:test --tests '*EntryCounterBoundaryScenarioTest' --tests '*FaceDownEntryCounterScenarioTest' --tests '*CopiableEntryValueScenarioTest' --tests '*CopiableFaceDownWardScenarioTest' --tests '*PendingEntryReferenceScenarioTest' --tests '*BattlefieldEntrySnapshotTest' --tests '*EntryEventTimeScenarioTest' --tests '*PendingCopyEntryScenarioTest' --tests '*CopiedAsEntersChoiceScenarioTest' --tests '*EntersAsCopyExceptionsScenarioTest' --tests '*LegacySuspensionMigrationTest' --tests '*SuspensionTraceTest' --tests '*BattlefieldEntryProjectionTest' --tests '*EntryObserverBoundaryScenarioTest' --tests '*EntryObservationLifetimeScenarioTest' --tests '*PendingEntryObserverScenarioTest' --tests '*StackResolutionPriorityTest' --tests '*PausedSpellTriggerFinalizationTest' --tests '*TriggerOrderingScenarioTest' --tests '*CastPrioritySbaTest' :mtg-sets:2023:tests:test --tests '*SpelunkingScenarioTest' :mtg-sdk:test --tests '*EntersAsCopyExceptionsSerializationTest' :mtg-sets:test --tests '*CardDefinitionSnapshotTest' --tests '*CardLintTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

broad:
    scripts/gradle-locked :rules-engine:test :mtg-sets:scenarioTest --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

assay-build:
    scripts/gradle-locked :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

assay:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay gate --limit 2000 --top 100

assay-differential:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay differential --top 1000
