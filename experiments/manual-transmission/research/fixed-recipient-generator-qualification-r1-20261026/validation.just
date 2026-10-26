compile:
    scripts/gradle-locked :mtgish-tooling:compileTestKotlin --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

targeted:
    scripts/gradle-locked :mtgish-tooling:test --tests '*FixedRecipientRetargetEmitterTest' --rerun-tasks --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

full-module:
    scripts/gradle-locked :mtgish-tooling:test --rerun-tasks --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1
