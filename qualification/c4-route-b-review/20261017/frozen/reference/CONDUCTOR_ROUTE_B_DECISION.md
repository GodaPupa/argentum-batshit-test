# ARGENTUM C4 — Conductor V6 compatibility decision

**Decision date:** 2026-10-15 (America/Los_Angeles)  
**Repository:** `GodaPupa/argentum-batshit-test`  
**Disposition:** `ROUTE_B_SELECTED__V6_LOCAL_ONLY_SOURCE_AUTHORIZED__BLOCKED_HOST_PARENT__D2_BLOCKED__C4_INCOMPLETE`

## Receiving basis and independence

The original, unmodified `Argentum_C4_V5_Host_Parent_Feasibility_2026-10-15.zip` is the input to this decision. Its 6,259,417 original bytes hash to SHA-256 `a3aa979c430af5b7e9f1bbee0f464d31e6b95868770c4fc789ce496cf5484234`. Its ZIP CRC is clean and its seven member hashes all verify. Inside it: the original V5 ZIP is 6,231,043 bytes, SHA-256 `2351acd3035ef411e154553e5710ed935f899f450a64e29905c2d07c3acc2d70`, all 81/81 package members verified. The two nested previous evidence packages and original action artifacts had clean CRC/manifests (see the frozen feasibility report's `INTEGRITY.json`). The feasibility report itself is source-bound *engineering design*, not an independent host capability verdict.

Frozen V5 commit `1e968451789bf724999e327a1978fa0dbd9b0331`, tree `6fd6f69a940a01b37d3e065e46541442433d7659`, parent `defc04acf75a9fa040ed7c43068d3b75cb730ec4`. Fixed V5 ELF SHA-256 `5c94b6671b4be6e2fbfcf46fc20fc17229ed15912a650362bfd0b1ec78dcd9b6`; source C SHA-256 `449429b3137d20f4f470f434877879e245c810a9876e89413c5aaae3327c73e6`. Existing V5 local code/test acceptance and independent source review are narrow; no runtime enforcement proven. V5 never publishes, runs an ELF, or launches a compiler under this decision.

Original C4 cold-compile run `38780475153` remains permanently **INCOMPLETE**, despite recorded compiler exits of zero, because the required authoritative OOM/memory counters were absent. Completed read-only metadata run `38815770299` and earlier cgroup discovery run `38798018237` do not demonstrate privilege, delegation or runtime enforcement. Main repository head at last live reconciliation is `37a603617a03dfd2d81b39dae004f7f336a70dfc`. No V5 remote commit or new hosted run was observed.

## Exact selected compatibility approach

**SELECT ROUTE B: a new, separately versioned and independently reviewed V6 adversarial helper, using a manager-created delegated transient service and normal host cgroup view.** Keep the original V5 C/ELF immutable as historical source. A different helper is a new object, never an execution of frozen V5.

Reasons: avoid adding a second privileged cgroup namespace, a private cgroup2 mount and host↔namespace path equivalence proof purely to accommodate V5's hardcoded `/sys/fs/cgroup/argentum-c4-v5`. The smaller topology reduces privileged code and compatibility uncertainty, even though it incurs new deterministic ELF build and independent review. The V5 `adversarial_helper.c` hardcoded `PARENT` and `MEMBER` values have been directly inspected in the frozen source.

This is a **source design selection, not evidence that the hosted runner supports it**. The design tentatively requests a *single transient service* with a fixed, reviewed root-controlled `ExecStart` supervisor, `Delegate=memory`, `DelegateSubgroup=supervisor` (documented since systemd v254), no automatic restart or lifecycle hooks, fixed bounded stop/kill policy, and no generic command parameters. The exact systemd manager version, `DelegateSubgroup` support, authority to create that one unit, manager-mediated upstream controller side effects, and effective runtime properties have NOT been verified. Any unsupported/mismatched precondition stops as `BLOCKED_HOST_PARENT`; do not silently fall back to Route A or change systemd-managed ancestors.

Logical hierarchy:

- `D`: fresh transient service delegated boundary from **authenticated PID1 ControlGroup readback**, not invented from a pathname; no unrelated process may be present.
- `S`: root-protected supervisor leaf `supervisor` established by `DelegateSubgroup=supervisor` **only if effective manager behavior is verified**.
- `C`: separate, newly created, exclusive measured child cgroup below D, never occupied by S. All helper descendants remain in C through bounded drain.

Systemd/PID1 retains ownership of D; the reviewed trusted supervisor owns only its expressly delegated descendants. Memory delegation at D and S/C topology must be verified by FD-backed reads. Worker must have no power to modify `cgroup.procs`, ancestors or sibling control files. No cgroup writes to the mount root, `system.slice`, `hosted-compute-agent.service`, or any guessed systemd ancestor are permitted. If PID1 must enable controllers above D as a side effect, the exact pre/post effects need separately reviewed authority and audit. Metadata-only observations are not proof of their safety.

The NEW V6 helper must not accept user-supplied executable paths or untrusted scope/target strings. It may derive its own membership **for diagnostics only**; the kernel/manager-derived D/S/C identities verified by the privileged supervisor are authoritative. Its protected migration negative control should target the already-established root-owned sibling S, not the empty domain node D (where EBUSY can be confused with permission denial). If S is not visible, absent, or does not match the independently held D/S identity, the case is INCOMPLETE, never PASS. All apparent denials also require independent parent-side observations and no escape; printed helper JSON alone is not authentication.

## Construction authorization and hard stops

**Authorize local-only, source-only V6 construction and synthetic/ordinary nonprivileged validation** on an isolated workspace, with exact parent frozen V5; no upload to the public repo. The implementing session may write local C/Python/tests, construct deterministic local ELF bytes, create synthetic receipts and a complete ZIP manifest. Do not execute the privileged supervisor or adversarial ELF against real host containment until a new, specifically reviewed approval. Preserve V4/V5 bytes, unrelated files, frozen R5/R6 source, original run identities and C4 timing/resource requirements unchanged.

**Explicitly NOT authorized:** GitHub branch/commit/PR writes; Actions workflow registration or dispatch; `sudo`, systemd unit/service creation, delegation or cgroup mutations; real kernel containment/migration/privilege tests; unrestricted process tracing; arbitrary host PIDs; JDK/Just/Gradle task execution; fresh R5/R6 cold compiles; behavioral differential; G11; games or official outcome claims. No new original is consumed by preparing V6 locally.

Next after local freeze: materially independent, source-bound V6 Reviewer B assessment of exact source, helper binary, unit manifest/permission design, supervisor enforcement/observation and negative tests. After that **only by fresh owner authorization** consider one noncompiler hosted enforcement original. Compiler integration and cold compiles are later, separate gates.

## Documentation basis (not new host capability evidence)

- systemd cgroup ownership/delegation: https://systemd.io/CGROUP_DELEGATION/
- Ubuntu 24.04 `Delegate`/`DelegateSubgroup`: https://manpages.ubuntu.com/manpages/noble/man5/systemd.resource-control.5.html
- Linux cgroup v2: https://docs.kernel.org/admin-guide/cgroup-v2.html

No active host/kernel/systemd change or repository write was performed by this conductor decision.
