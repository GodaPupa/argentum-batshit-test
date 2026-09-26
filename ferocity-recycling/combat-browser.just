# Invoked with --working-directory at the repository root. The ordinary shared lock
# remains in the path; CI itself provisions one isolated job and one server build.
server:
    scripts/gradle-locked --no-daemon --no-build-cache --max-workers=2 -Pkotlin.incremental=false :game-server:bootRun
