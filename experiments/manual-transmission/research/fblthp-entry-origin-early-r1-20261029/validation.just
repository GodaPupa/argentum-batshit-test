compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin :mtg-sets:2017-2022:tests:compileTestKotlin :mtg-sets:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

normal:
    scripts/gradle-locked :rules-engine:test --tests '*EntryOriginRuntimeTest' :mtg-sets:2017-2022:tests:test --tests '*FblthpTheLostScenarioTest' --continue --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

export:
    scripts/gradle-locked :mtg-sets:test --tests '*FblthpSnapshotExportTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
