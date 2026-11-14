compile:
    scripts/gradle-locked compileTestKotlin :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
cards:
    scripts/gradle-locked :mtg-sets:test --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
targeted:
    scripts/gradle-locked :rules-engine:test --tests 'com.wingedsheep.engine.scenarios.SurgeCostCompositionTest' --tests 'com.wingedsheep.engine.scenarios.SourceIdentityGroupExclusionTest' :mtg-sets:2008-2016:tests:test --tests 'com.wingedsheep.engine.scenarios.RecklessBushwhackerScenarioTest' :mtg-sets:test --tests 'com.wingedsheep.mtg.sets.RecklessBushwhackerRegistrationTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
client-install:
    cd web-client && npm ci
client-focused:
    cd web-client && npm test -- src/store/slices/ui/targetingSlice.test.ts src/store/slices/ui/pipelinePhases.test.ts src/store/slices/ui/publicTargetGroups.test.ts src/store/slices/liveInteraction.test.ts src/utils/surgeActionTransport.test.ts --reporter=json
client-full:
    cd web-client && npm test -- --reporter=json
client-typecheck:
    cd web-client && npm run typecheck
