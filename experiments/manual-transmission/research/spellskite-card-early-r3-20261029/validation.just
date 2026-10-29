compile:
    scripts/gradle-locked :mtg-sets:compileTestKotlin :rules-engine:compileTestKotlin --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

normal:
    scripts/gradle-locked :mtg-sets:test --tests '*SpellskiteRegistrationTest' :rules-engine:test --tests '*SpellskiteScenarioTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

registration:
    scripts/gradle-locked :mtg-sets:test --tests '*SpellskiteRegistrationTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

export:
    scripts/gradle-locked :mtg-sets:test --tests '*SpellskiteSnapshotExportTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
