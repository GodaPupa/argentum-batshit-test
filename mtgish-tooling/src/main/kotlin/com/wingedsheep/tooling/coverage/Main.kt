package com.wingedsheep.tooling.coverage

import com.wingedsheep.tooling.coverage.dashboard.Dashboard
import kotlin.system.exitProcess

/**
 * Dispatch entrypoint for the mtgish coverage tooling. The first token selects one of the tools
 * (probe / fidelity / autogen / fixtures / dashboard); the rest are that tool's own flags.
 *
 *   probe     --set CODE | --card "NAME" | --calibrate CODE   [--free|--blocked|--all] [--refresh]
 *   fidelity  --set CODE [--list TIER] | --all | --emit "NAME" | --gate CODE
 *   autogen   --set CODE [--gaps|--write|--emit-all|--write-all] [--list CAT] [--out DIR]
 *             --all --gaps [--list CAT] [--unique]
 *   fixtures  [CODE...]       regenerate the vendored emitter-regression fixtures (default POR)
 *   dashboard                 interactive TUI over the same analysis (set / card / cross-set views)
 *
 * Wired into the justfile `coverage*` recipes and the mtg-sets `verifyGeneratedCards` gate.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("usage: <probe|fidelity|autogen|fixtures|dashboard> [args...]")
        exitProcess(2)
    }
    val rest = args.drop(1)
    val code = when (args[0]) {
        "probe" -> Probe.run(rest)
        "fidelity" -> Fidelity.run(rest)
        "autogen" -> Autogen.run(rest)
        "fixtures" -> Fixtures.run(rest)
        "dashboard" -> Dashboard.run(rest)
        else -> {
            System.err.println("unknown tool '${args[0]}' — expected probe | fidelity | autogen | fixtures | dashboard")
            2
        }
    }
    exitProcess(code)
}
