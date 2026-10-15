#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$1" && pwd)
shift
here=$(cd "$(dirname "$0")" && pwd)
cd "$root"
test "$(just --version)" = 'just 1.59.0'
# Same launcher JDK vendor/major as proposed workflow; exact patch recorded in evidence.
java -XshowSettings:properties -version 2>&1 | grep -q 'java.vendor = Microsoft'
java -XshowSettings:properties -version 2>&1 | grep -Eq 'java.specification.version = 21$'
exec just test-class R5R6DecisionDifferentialTest -x :ai:test :ai:compileTestKotlin --no-daemon --max-workers=2 --no-configuration-cache -PkotlinCompileParallelism=1 -Dorg.gradle.jvmargs=-Xmx1g -Pkotlin.daemon.jvmargs=-Xmx4g -I "$here/compile-only.init.gradle" --info "$@"
