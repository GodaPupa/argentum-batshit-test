compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

diagnose:
    scripts/gradle-locked :rules-engine:test --tests com.wingedsheep.engine.scenarios.ActivatedPhyrexianPaymentDiagnosticTest --rerun --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
