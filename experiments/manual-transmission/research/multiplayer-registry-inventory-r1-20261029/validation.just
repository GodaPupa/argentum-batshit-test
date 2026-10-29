compile:
    scripts/gradle-locked :gym:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
inventory:
    scripts/gradle-locked :gym:test --tests 'com.wingedsheep.gym.ManualTransmissionPhaseTwoRegistryAuditTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
