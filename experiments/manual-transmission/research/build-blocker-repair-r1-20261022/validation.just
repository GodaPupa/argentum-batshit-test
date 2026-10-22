targeted:
    scripts/gradle-locked :mtg-sets:test --tests '*FacadeBoundaryTest' --tests '*CardDefinitionSnapshotTest' --tests '*CardLintTest' :gym:test --tests '*PestControlTierOneMonsterTronRunnerSurfacePreflightTest' --tests '*PestControlTierOneMonsterTronR1SurfacePinTest' :mtg-sets:1993-1999:tests:test --tests '*ArcaneDenialScenarioTest' :mtg-sets:2003-2007:tests:test --tests '*PactOfNegationScenarioTest' --tests '*PactOfNegationUnpaidScenarioTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
