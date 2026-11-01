compile:
    scripts/gradle-locked compileTestKotlin :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
focused:
    scripts/gradle-locked :mtg-sets:2008-2016:tests:test --tests 'com.wingedsheep.engine.scenarios.OpalPalaceScenarioTest' --tests 'com.wingedsheep.engine.scenarios.OpalRiderAdditionalTest' --tests 'com.wingedsheep.engine.scenarios.OpalRiderBaselineTest' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessControlsTest' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP01Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP02Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP03Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP04Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP05Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP06Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP07Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP08Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP09Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP10Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP11Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP12Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP13Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP14Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP15Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP17Test' --tests 'com.wingedsheep.engine.scenarios.OpalWitnessOP18Test' --tests 'com.wingedsheep.engine.scenarios.PyromancersGogglesScenarioTest' :mtg-sets:2017-2022:tests:test --tests 'com.wingedsheep.engine.scenarios.CarnelianOrbOfDragonkindScenarioTest' --tests 'com.wingedsheep.engine.scenarios.FblthpTheLostScenarioTest' :rules-engine:test --tests 'com.wingedsheep.engine.mana.BoltBendCostPredicateTest' --tests 'com.wingedsheep.engine.mana.BoltBendCostProjectionTest' --tests 'com.wingedsheep.engine.scenarios.EntryOriginRuntimeTest' --tests 'com.wingedsheep.engine.scenarios.GenericRetargetContinuationTest' --tests 'com.wingedsheep.engine.triggers.BecomesTargetPlayerAndAbilityAxesTest' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
cards:
    scripts/gradle-locked :mtg-sets:test --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
corpus:
    just assay corpus --refresh
assay-war:
    just assay-differential --set WAR --top 10000
assay-c13:
    just assay-differential --set C13 --top 10000
