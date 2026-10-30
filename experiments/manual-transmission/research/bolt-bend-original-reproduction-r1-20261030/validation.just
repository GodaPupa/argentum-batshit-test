set shell := ["bash", "-euo", "pipefail", "-c"]

compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

reproduce:
    scripts/gradle-locked :rules-engine:test --tests '*BoltBendCostPredicateTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
