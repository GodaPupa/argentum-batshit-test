set shell := ["bash", "-euo", "pipefail", "-c"]
compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
tests:
    scripts/gradle-locked :rules-engine:test --tests '*BoltBendCostPredicateTest' --tests '*BoltBendCostProjectionTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
