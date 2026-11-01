compile:
    scripts/gradle-locked :mtg-sets:2024:tests:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
observe:
    scripts/gradle-locked :mtg-sets:2024:tests:test --tests com.wingedsheep.engine.scenarios.DiscipleOfFreyaliseScenarioTest --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
