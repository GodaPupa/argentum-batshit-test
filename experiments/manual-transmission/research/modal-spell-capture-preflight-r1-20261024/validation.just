compile:
    scripts/gradle-locked :rules-engine:compileKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

targeted:
    scripts/gradle-locked :rules-engine:test --tests '*ModalSpellCaptureTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
