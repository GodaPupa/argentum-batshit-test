# Four-case combat browser gate

This is a prospective full-stack browser acceptance gate for the current combat assignment UI.
It has **zero executed browser cases** at source freeze and consumes no research game, pilot,
development, evaluation, or confirmation allocation.

The exact initializations, assertions, finite limits, and source observations are in
`PRE_EXECUTION_PLAN.json` and its explicit `PLAN_SUCCESSOR_01.json` deadline/assertion clarification. The three existing `trample-damage-assignment.spec.ts` bodies remain
byte-identical. The one new project case assigns one damage to each of two blockers when neither
assignment is lethal, then verifies the actual server accepts that legal division and resolves
both blockers' full damage. The old `multiple-blockers.spec.ts` expects an obsolete blocker-order
decision and is explicitly excluded, with its bytes retained in the source dependency record.

## Invocation and admission

The owner publishes the reviewed project spec, config, recorder, workflow and audit together with
the reviewed current engine/UI source. Opening or updating a project pull request targeting
`ferocity-recycling/archetype-research` starts **Ferocity Recycling combat browser qualification**
automatically. Manual `workflow_dispatch` remains available where the operator has that API.
The job checks out `${{ github.event.pull_request.head.sha || github.sha }}` and verifies it against
explicit `EXPECTED_HEAD`. It records the triggering event and `GITHUB_SHA` separately, so a pull
request merge-preview SHA is never mistaken for the actual checked-out head. It must not attach
to an existing server or run against another worker's tree.

The actual test command, after committed-lockfile provisioning, is:

```sh
cd e2e-scenarios
npx --no-install playwright test --config ferocity-combat.config.ts
```

The workflow uses a fresh isolated Gradle home and browser directory. It verifies the previously
archived Temurin21.0.12.1+1 and just1.58.0 tarball checksums. It runs `npm ci` for both
client and E2E projects, installs the lockfile-selected Chromium build, and builds the real client.
The existing scenario fixture starts two actual browser contexts and calls the real development
scenario endpoint. Only canonical corpus cards appear. There is no state-store injection, custom
card registration, public Ferocity implementation, or fabricated legacy decision.

The project config selects exactly four test bodies, one worker, zero retries, 60 seconds per body,
and a 45-minute total deadline including a cold server compile/startup budget. The enclosing
provisioning/CI job is capped at 90 minutes. The normal helper screenshots remain attached, and
Playwright is configured to retain traces plus failure screenshots/video. Actual generated
artifacts, including missing or incomplete reports, determine acceptance; merely configuring a
reporter is not evidence that its artifact exists.

`record_combat_browser.py before` pins the exact expected published head, a hash map of every tracked file under the actual production/harness/build/script directories,
the frozen UI/harness/lock inputs, and a clean tracked worktree. Engine sources may have a reviewed
successor before dispatch; the observed engine hashes in the prospective plan are not permission
to substitute an unreviewed source. The exact executed commit and newly captured hashes identify
that final engine boundary. The recorder checks every tracked modification after execution.

The runtime record identifies actual Node, npm, JDK release/binary, Playwright version, committed
locks, and the complete installed browser/FFmpeg file closure. This includes the headless-shell
binary that Chromium may actually launch. The server runs through the project just recipe and
the existing `scripts/gradle-locked` wrapper, with incremental and build-cache reuse disabled in
the isolated job. No local npm install, browser install, server build, or browser
execution was performed by the author.

Acceptance requires the browser command to exit zero, all three required workflow phases to
succeed, the exact four case names/files, exactly one passed result per case, no retry, no skip,
no global Playwright error, and unchanged source/runtime guards. Raw stdout/stderr, reports,
screenshots and any generated trace/video are uploaded even on failure. `NOT_ACCEPTED`, missing
receipts, provisioning errors, timeouts and cancellation do not become a draw or an inferred pass.
Any diagnosed successor preserves the first attempt and declares its new source boundary.

## Scope of the two UI components

The inspected current engine constructs `CombatResolutionDecision` for genuine combat division.
Repository search found no production constructor of `AssignDamageDecision`; that type remains
as a compatibility contract and `DecisionUI` retains its legacy modal branch. Accordingly these
four real browser cases cover **CombatResolutionBoard**, and the client build checks both
components' types. They do not claim browser coverage of an unreachable legacy modal, banding,
shared-blocker splits, deathtouch/trample combinations, prevention, hidden cards, or arbitrary
combat states. Those mechanics retain their separately bounded engine tests.

The scenario API exposes no caller-supplied seed field. These fixtures have fixed board states,
actions, and one identical basic library card per player. Backend-generated session/entity/routing
identities are not research allocations; the run is not labeled seed-free simulation evidence.
All scenario tokens are ephemeral credentials for that isolated local CI server. Before any
separate public evidence commit, inspect retained traces and logs under the project's publication
audit; uploading the job's declared debugging artifacts does not grant them gameplay validity.

## Locked package and producer observations

`PLAYWRIGHT_SOURCE_REVIEW.json` binds the exact npm-lock-selected Playwright1.60.0 archive and source inspection. The JSON reporter emits testDir-relative spec paths. Its instrumentation applies configured baseURL and trace hooks to the existing fixture's manually created contexts. This was an inert package-source read, with no npm install or browser execution.

The current engine producer still marks ordinary assignment `orderConstrained=true` and chooser inversion `false`; legality no longer enforces an ordinary blocker order. An initially reported false-banding concern was withdrawn after tracing this producer. Both combat UI components are unchanged, and CB04 explicitly checks that ordinary division displays no banding-inversion label.

The source snapshot uses no extension filter: Java, YAML, binary wrapper inputs and tracked resources
inside those directories are included alongside Kotlin/TypeScript. Untracked dependency and generated
directories are not declared source inputs; exact committed locks and the separately captured installed
browser/JDK/Node closure identify the recorded runtime. This is a source/input guard, not a claim that
every transient Gradle classpath artifact has been independently inventoried.
