# Conditional recipes only. Not dispatchable before source/export/receipt binding.
compile:
    scripts/gradle-locked :mtg-sets:compileTestKotlin :rules-engine:compileTestKotlin :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
cards:
    scripts/gradle-locked :mtg-sets:test --rerun-tasks --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
ordinary:
    scripts/gradle-locked build :ai:test --tests '*Test' --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
printing:
    just check-card-printing "Spellskite"
assay-parse:
    just assay parse "Spellskite"
assay-explain:
    just assay explain "Spellskite"
assay-compile:
    just assay compile "Spellskite"
assay-nph:
    just assay-differential --set NPH --top 10000
assay-clb:
    just assay-differential --set CLB --top 10000
