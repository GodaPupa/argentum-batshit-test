compile:
    scripts/gradle-locked :mtgish-tooling:compileTestKotlin --rerun-tasks --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

targeted:
    scripts/gradle-locked :mtgish-tooling:test --tests 'com.wingedsheep.tooling.coverage.emitter.GeneratorCalibrationRepairTest' --rerun-tasks --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1
