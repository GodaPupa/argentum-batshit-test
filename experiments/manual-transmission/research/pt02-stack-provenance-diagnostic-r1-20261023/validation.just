diagnostic:
    scripts/gradle-locked :rules-engine:test --tests '*PausedTokenStackProvenanceDiagnosticTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
