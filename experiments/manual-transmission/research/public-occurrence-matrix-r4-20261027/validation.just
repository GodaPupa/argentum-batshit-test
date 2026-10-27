compile:
    scripts/gradle-locked :rules-engine:compileKotlin :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

targeted:
    scripts/gradle-locked :rules-engine:test --tests "*PublicOccurrenceMatrixTest" --rerun-tasks --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

wrong-occurrence:
    scripts/gradle-locked :rules-engine:test --tests "*PublicOccurrenceMatrixTest" --rerun-tasks --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

division-mirror:
    scripts/gradle-locked :rules-engine:test --tests "*PublicOccurrenceMatrixTest" --rerun-tasks --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

dropped-queue:
    scripts/gradle-locked :rules-engine:test --tests "*PublicOccurrenceMatrixTest" --rerun-tasks --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

stale-context:
    scripts/gradle-locked :rules-engine:test --tests "*PublicOccurrenceMatrixTest" --rerun-tasks --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
