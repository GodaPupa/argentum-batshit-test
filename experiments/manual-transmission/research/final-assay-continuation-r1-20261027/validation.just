assay-build:
    scripts/gradle-locked :oracle-assay:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1

assay:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay gate --limit 2000 --top 100

assay-differential:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay differential --top 1000
