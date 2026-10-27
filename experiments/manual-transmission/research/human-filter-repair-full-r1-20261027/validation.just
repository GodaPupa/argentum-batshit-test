full-module:
    scripts/gradle-locked :mtgish-tooling:test --rerun-tasks --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

install:
    scripts/gradle-locked :mtgish-tooling:installDist --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

compile SET:
    scripts/gradle-locked :mtg-sets:verifyGeneratedCards -Pset={{SET}} --console=plain --rerun-tasks --no-daemon --max-workers=2 -PkotlinCompileParallelism=1

fidelity SET:
    mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling fidelity --gate {{SET}}
