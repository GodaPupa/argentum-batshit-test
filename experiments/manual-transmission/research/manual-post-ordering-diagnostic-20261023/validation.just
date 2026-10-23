diagnostic:
    scripts/gradle-locked :rules-engine:test --tests '*EntryChoiceR4PostOrderingDiagnosticTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
