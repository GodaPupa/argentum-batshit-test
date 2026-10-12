set shell := ["bash", "-euo", "pipefail", "-c"]

compile:
    scripts/gradle-locked :gym:testClasses --no-daemon --no-build-cache --no-configuration-cache --console=plain

timing:
    scripts/gradle-locked :gym:test --tests com.wingedsheep.gym.PlayFirstTimingJournalTest --no-daemon --no-build-cache --no-configuration-cache --console=plain
