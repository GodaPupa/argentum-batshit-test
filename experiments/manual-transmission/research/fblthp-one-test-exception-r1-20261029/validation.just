compile:
    scripts/gradle-locked compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
compile-mutant:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
focused:
    scripts/gradle-locked :rules-engine:test --tests '*BecomesTargetPlayerAndAbilityAxesTest' --tests '*GenericRetargetContinuationTest' --tests '*EntryOriginRuntimeTest' :mtg-sets:2017-2022:tests:test --tests '*FblthpTheLostScenarioTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
axes:
    scripts/gradle-locked :rules-engine:test --tests '*BecomesTargetPlayerAndAbilityAxesTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
cards:
    scripts/gradle-locked :mtg-sets:test --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
