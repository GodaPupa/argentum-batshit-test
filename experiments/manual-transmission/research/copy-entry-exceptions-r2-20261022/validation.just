targeted:
    scripts/gradle-locked :mtg-sdk:test --tests '*EntersAsCopyExceptionsSerializationTest' :rules-engine:test --tests '*EntersAsCopyExceptionsScenarioTest' :mtg-sets:test --tests '*CardDefinitionSnapshotTest' --tests '*CardLintTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

assay-build:
    scripts/gradle-locked :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

assay:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay gate --limit 2000 --top 100
