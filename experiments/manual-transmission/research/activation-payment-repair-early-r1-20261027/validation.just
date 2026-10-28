compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

targeted:
    scripts/gradle-locked :rules-engine:test --tests com.wingedsheep.engine.scenarios.ActivatedPhyrexianPaymentDiagnosticTest --tests com.wingedsheep.engine.scenarios.ActivatedPhyrexianPaymentControlTest --tests com.wingedsheep.engine.scenarios.ActivatedPhyrexianPaymentBoundaryTest --rerun --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

controls:
    scripts/gradle-locked :rules-engine:test --tests com.wingedsheep.engine.scenarios.ActivatedPhyrexianPaymentControlTest --rerun --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
