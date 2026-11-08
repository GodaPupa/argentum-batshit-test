compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
behavior:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.SelfAlternativePriorSpellEligibilityDiagnosticTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
