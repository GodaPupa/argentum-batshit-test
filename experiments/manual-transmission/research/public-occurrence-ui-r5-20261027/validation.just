compile:
    scripts/gradle-locked :rules-engine:compileTestKotlin :game-server:classes --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

fixtures:
    scripts/gradle-locked :rules-engine:test --tests '*PublicOccurrenceBrowserFixtureTest' --rerun-tasks --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

server:
    scripts/gradle-locked :game-server:bootRun --no-daemon --console=plain --max-workers=2 -PkotlinCompileParallelism=1

client-typecheck:
    cd web-client && npm run typecheck

browser-typecheck:
    cd e2e-scenarios && npx tsc --noEmit --project manual-occurrence.tsconfig.json

browser GREP:
    cd e2e-scenarios && npx playwright test --config manual-occurrence.config.ts --grep '{{GREP}}'
