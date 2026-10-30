compile:
    scripts/gradle-locked :oracle-assay:installDist :mtgish-tooling:installDist --no-build-cache --no-daemon --console=plain --stacktrace --max-workers=2 -PkotlinCompileParallelism=1
reckless-bushwhacker-parse:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay parse "Reckless Bushwhacker"
reckless-bushwhacker-explain:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay explain "Reckless Bushwhacker"
reckless-bushwhacker-assay-compile:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay compile "Reckless Bushwhacker"
reckless-bushwhacker-emit:
    mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling fidelity --emit "Reckless Bushwhacker"
amphin-mutineer-parse:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay parse "Amphin Mutineer"
amphin-mutineer-explain:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay explain "Amphin Mutineer"
amphin-mutineer-assay-compile:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay compile "Amphin Mutineer"
amphin-mutineer-emit:
    mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling fidelity --emit "Amphin Mutineer"
disciple-of-freyalise-parse:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay parse "Disciple of Freyalise"
disciple-of-freyalise-explain:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay explain "Disciple of Freyalise"
disciple-of-freyalise-assay-compile:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay compile "Disciple of Freyalise"
disciple-of-freyalise-emit:
    mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling fidelity --emit "Disciple of Freyalise"
veil-of-summer-parse:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay parse "Veil of Summer"
veil-of-summer-explain:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay explain "Veil of Summer"
veil-of-summer-assay-compile:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay compile "Veil of Summer"
veil-of-summer-emit:
    mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling fidelity --emit "Veil of Summer"
opal-palace-parse:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay parse "Opal Palace"
opal-palace-explain:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay explain "Opal Palace"
opal-palace-assay-compile:
    oracle-assay/build/install/oracle-assay/bin/oracle-assay compile "Opal Palace"
opal-palace-emit:
    mtgish-tooling/build/install/mtgish-tooling/bin/mtgish-tooling fidelity --emit "Opal Palace"
