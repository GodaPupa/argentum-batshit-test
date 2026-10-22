negative:
    scripts/gradle-locked :rules-engine:test --tests '*CopiedAsEntersChoiceScenarioTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

focused:
    scripts/gradle-locked :rules-engine:test --tests '*CopiedAsEntersChoiceScenarioTest' --tests '*EntersAsCopyExceptionsScenarioTest' :mtg-sdk:test --tests '*EntersAsCopyExceptionsSerializationTest' :mtg-sets:test --tests '*CardDefinitionSnapshotTest' --tests '*CardLintTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

broad:
    scripts/gradle-locked :rules-engine:test :mtg-sets:scenarioTest --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
