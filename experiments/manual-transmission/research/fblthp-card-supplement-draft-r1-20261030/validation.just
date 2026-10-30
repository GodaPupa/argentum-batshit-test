compile:
    scripts/gradle-locked :oracle-assay:installDist :mtgish-tooling:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
printing:
    just check-card-printing "Fblthp, the Lost"
corpus:
    just assay corpus --refresh
parse:
    just assay parse "Fblthp, the Lost"
explain:
    just assay explain "Fblthp, the Lost"
assay-compile:
    just assay compile "Fblthp, the Lost"
assay-war:
    just assay-differential --set WAR --top 10000
current-ir:
    just coverage-fidelity --emit "Fblthp, the Lost"
