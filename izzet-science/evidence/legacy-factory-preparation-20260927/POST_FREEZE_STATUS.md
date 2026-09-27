# Post-freeze visibility finding

The frozen recovery archive `4276c2f631306991fff1c8d6104383656f73e648f653ae2c231e32933c9cc728` retains the exact unready `67ba5871` draft and its then-current review history. It remains unchanged.

Independent Ferocity review subsequently identified that a `PermissionError` while observing processes could be mistaken for an empty, quiescent group. The exact finding `9e7188fb4362c12feb87c4dc030b06e7242e87fd3b41da529cfdcf08be77ff1e` and its pure in-memory reproducer are retained here. This prevents source acceptance of that draft; it does not invalidate or rewrite any engine result, because the factory differential has not run.

The separately staged successor collector is `ed3c72a1fa5494598f212d0981e37eb0f6cf1c9116bf61938f2fa8dca9ad88e8`. Only the process-observation, terminal-cleanup and caller-recording functions change. Readable group/session identifiers first exclude definite nonmembers; unreadable or malformed possible-member identities produce retained incomplete observations. Quiescence requires complete observations, no unresolved cleanup errors, no live observed members, and wrapper exit. Initial and subsequent cleanup records are written before consuming report bytes. Vanished process-entry races remain explicitly counted exclusions.

The source pair, three banks, eleven case executions, nine identities, unready gate `ba7a6d22`, and workflow `53165b8e` remain unchanged. Ten in-memory AST guards pass; no live process, signal, package, JVM, official allocation or gameplay action was performed for this correction. Earlier four live Python checks bind the preserved `67ba5871` draft and are not represented as successor execution.

The prerequisite package version and equal-version dependency were verified on the [Ubuntu Noble package page](https://packages.ubuntu.com/eu/noble/inn2). The exact Noble amd64 file-list and package-byte lookups were unavailable. The [Debian file list](https://packages.debian.org/trixie/amd64/inn2/filelist) is only a layout lead, and the [upstream manual](https://www.eyrie.org/~eagle/software/inn/docs/shlock.html) defines the utility's lock semantics. Actual Noble package bytes, metadata, extracted binary/libraries and acquire/contention/release checks remain prospective prerequisites that must pass before any JVM. No successful package acquisition or functioning CI semaphore is claimed here.

This successor remains held for Ferocity independent source review and root's final exact release and trigger checks. It is not an execution permit or a runtime/pilot/gameplay admission.
