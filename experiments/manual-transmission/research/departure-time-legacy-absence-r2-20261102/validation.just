compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
normal:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.SourceDeparturePaidChoiceDiagnosticTest' --tests 'com.wingedsheep.engine.scenarios.SourceDepartureCharacteristicPreservationTest' --tests 'com.wingedsheep.engine.scenarios.SourceDepartureChoiceRepairTest' --tests 'com.wingedsheep.engine.scenarios.SourceDepartureChoiceNegativeWitnessTest' --tests 'com.wingedsheep.engine.scenarios.DepartureTimeChoiceCaptureTest' --tests 'com.wingedsheep.engine.scenarios.DepartureTimeContinuationNegativeWitnessTest' --tests 'com.wingedsheep.engine.scenarios.DepartureTimeExistingLkiNegativeWitnessTest' --tests 'com.wingedsheep.engine.scenarios.LegacyAbsenceChoiceCaptureTest' --tests 'com.wingedsheep.engine.state.LegacySuspensionMigrationTest' --tests 'com.wingedsheep.engine.state.SuspensionTraceTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
drop-departure-choice:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.SourceDeparturePaidChoiceDiagnosticTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
borrow-later-generation:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.SourceDepartureChoiceNegativeWitnessTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
omit-paused-stamping:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.DepartureTimeContinuationNegativeWitnessTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
early-full-snapshot:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.SourceDepartureCharacteristicPreservationTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
overwrite-genuine-lki:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.DepartureTimeExistingLkiNegativeWitnessTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
restore-absent-to-empty-fallback:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.LegacyAbsenceChoiceCaptureTest' --tests 'com.wingedsheep.engine.state.LegacySuspensionMigrationTest' --tests 'com.wingedsheep.engine.state.SuspensionTraceTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
