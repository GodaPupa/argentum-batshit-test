compile:
    scripts/gradle-locked compileTestKotlin :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
focused:
    scripts/gradle-locked :rules-engine:test --tests '*BecomesTargetPlayerAndAbilityAxesTest' --tests '*GenericRetargetContinuationTest' --tests '*EntryOriginRuntimeTest' --tests '*BoltBendCostPredicateTest' --tests '*BoltBendCostProjectionTest' :mtg-sets:2017-2022:tests:test --tests '*FblthpTheLostScenarioTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
cards:
    scripts/gradle-locked :mtg-sets:test --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
corpus:
    just assay corpus --refresh
assay-war:
    just assay-differential --set WAR --top 10000
