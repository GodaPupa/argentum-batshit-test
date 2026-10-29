compile:
    scripts/gradle-locked :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
printing:
    just check-card-printing "Spellskite"
corpus:
    just assay corpus --refresh
parse:
    just assay parse "Spellskite"
explain:
    just assay explain "Spellskite"
assay-compile:
    just assay compile "Spellskite"
assay-nph:
    just assay-differential --set NPH --top 10000
assay-clb:
    just assay-differential --set CLB --top 10000
