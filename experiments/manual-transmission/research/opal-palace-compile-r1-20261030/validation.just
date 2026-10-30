set shell := ["bash", "-euo", "pipefail", "-c"]
compile:
    scripts/gradle-locked compileTestKotlin :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
snapshot-export:
    scripts/gradle-locked :mtg-sets:test --tests '*OpalPalaceSnapshotExportTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
