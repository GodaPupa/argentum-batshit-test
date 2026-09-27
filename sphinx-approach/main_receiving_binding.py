"""Exact external controls for two existing actor banks; never gameplay admission."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import time

CONTEXT = "sphinx-main-actor-external-22cf-v1"
MANIFEST = "sphinx-approach/STAGE_E_MAIN_RECEIVING_BINDING.json"
CANONICAL_DESCRIPTOR = "sphinx-approach/STAGE_E_CANONICAL_RECEIVING_BINDING.json"
CANONICAL_DESCRIPTOR_SHA256 = "1316e5dc9ad26db03e655d4fd5aa96427309eb04700d929e6433e64786f50682"
CANONICAL_HELPER_SHA256 = "a014e1e4ebf37db57bf3a77d5a4f1963476710ebe76692b1a213e60cb7f7f300"
RUNTIME = "22cf67791fda69a65d7e1eeb76c2ae369407cb01"
RUNTIME_TREE = "3b4e50dd7a0fe484b796bb191bd848b048bea12b"
CANONICAL = "8ed9787ad75b8bc6c2438a8c0b526ff822aa776e"
CANONICAL_TREE = "4693352c4a22d9f3b35eb47048c8edaabe8157f1"
MAIN = "ff34ac8fa2efbd41f233e877cb2908c2cab5929a"
MAIN_TREE = "88967f3a9ba0479661f02150f82ddcc9a2066e42"
WORKFLOW = ".github/workflows/sphinx-main-actor-receiving-qualification.yml"
BRANCH = "lab/sphinx-main-actor-external-20260927"
REPORTS = {"shared_actor_50": "shared-actor-input", "sphinx_actor_136": "sphinx-stage-e-actor"}
CONTROL_FILES = [MANIFEST, "sphinx-approach/main_receiving_binding.py",
                 "sphinx-approach/collect-stage-e-actor.py",
                 "lab-coordinator/shared-capabilities/collect-actor-input.py", WORKFLOW]
RUNTIME_AUTHORITIES = [CANONICAL_DESCRIPTOR, "sphinx-approach/canonical_receiving_binding.py",
                       "sphinx-approach/evidence/canonical-receiving-binder-source-review.json",
                       "lab-coordinator/shared-capabilities/actor-input-extraction.json",
                       "sphinx-approach/STAGE_E_ACTOR_FIXTURE_BUDGET.json",
                       "sphinx-approach/STAGE_E_ACTOR_RECEIVING_SCOPE.json"]


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def fingerprint(value: object) -> str:
    return sha(json.dumps(value, sort_keys=True, separators=(",", ":")).encode())


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def relative(root: Path, name: str) -> Path:
    path = Path(name)
    require(not path.is_absolute() and ".." not in path.parts, "Unsafe relative source path")
    result = root / path
    require(result.resolve().is_relative_to(root.resolve()), "Source path leaves its checkout")
    return result


def write_json(path: Path, value: object) -> None:
    raw = (json.dumps(value, indent=2) + "\n").encode()
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("wb") as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)
    descriptor = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def control_identity(control: Path, manifest: dict, manifest_raw: bytes) -> dict:
    digest = sha(manifest_raw)
    require(os.environ.get("SPHINX_MAIN_BINDING_SHA256") == digest,
            "Manifest does not match the externally frozen workflow binding")
    require(set(manifest["control_source_sha256"]) == set(CONTROL_FILES) - {MANIFEST, WORKFLOW},
            "Control source inventory changed")
    observed = {path: sha(relative(control, path).read_bytes()) for path in CONTROL_FILES}
    for path, expected in manifest["control_source_sha256"].items():
        require(observed[path] == expected, "Control source changed: " + path)
    workflow = relative(control, WORKFLOW).read_bytes()
    needle = ("SPHINX_MAIN_BINDING_SHA256: '" + digest + "'").encode()
    require(workflow.count(needle) == 1, "Workflow must carry the one exact manifest digest")
    normalized = workflow.replace(needle, b"SPHINX_MAIN_BINDING_SHA256: '__BINDING_SHA256__'")
    require(sha(normalized) == manifest["normalized_workflow_sha256"], "Workflow control changed")
    head = git(control, "rev-parse", "HEAD")
    require(head == os.environ.get("GITHUB_SHA"), "Actual control commit differs from event SHA")
    require(git(control, "rev-list", "--parents", "-n", "1", head).split() == [head, CANONICAL],
            "Control must be one isolated child of the accepted canonical source")
    changed = set(git(control, "diff", "--name-only", CANONICAL, head).splitlines())
    require(changed == set(CONTROL_FILES), "Control commit changes files outside the exact five-file release")
    require(not git(control, "status", "--porcelain", "--untracked-files=all"), "Control checkout is dirty")
    return {"head": head, "tree": git(control, "rev-parse", "HEAD^{tree}"),
            "manifest_sha256": digest, "source_sha256": observed,
            "normalized_workflow_sha256": sha(normalized)}


def event_identity() -> dict:
    require(os.environ.get("SPHINX_RECEIVING_CONTEXT") == CONTEXT, "Explicit main receiving context required")
    require(os.environ.get("GITHUB_EVENT_NAME") == "push", "Only the reviewed isolated creation push is allowed")
    require(os.environ.get("GITHUB_REF") == "refs/heads/" + BRANCH, "Wrong control branch")
    require(os.environ.get("GITHUB_RUN_ATTEMPT") == "1", "Consumed control attempt cannot be rerun")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_bytes())
    require(event.get("before") == "0" * 40 and event.get("created") is True,
            "Control activation must be a branch creation")
    require(event.get("after") == os.environ.get("GITHUB_SHA"), "Push event and actual control SHA differ")
    require(event.get("ref") == "refs/heads/" + BRANCH, "Push payload ref differs")
    require(event.get("repository", {}).get("full_name") == "GodaPupa/argentum-batshit-test",
            "Unexpected repository")
    return {"event_name": "push", "ref": event["ref"], "before": event["before"],
            "after": event["after"], "created": event["created"],
            "run_id": os.environ["GITHUB_RUN_ID"], "run_attempt": 1}


def runtime_identity(runtime: Path, manifest: dict) -> dict:
    require(os.environ.get("SPHINX_MAIN_RUNTIME_SHA") == RUNTIME, "Wrong requested runtime")
    require(git(runtime, "rev-parse", "HEAD") == RUNTIME, "Wrong actual runtime checkout")
    require(git(runtime, "rev-parse", "HEAD^{tree}") == RUNTIME_TREE, "Wrong runtime tree")
    require(git(runtime, "rev-list", "--parents", "-n", "1", RUNTIME).split() == [RUNTIME, MAIN, CANONICAL],
            "Actual main/canonical merge parents differ")
    for commit, tree in [(MAIN, MAIN_TREE), (CANONICAL, CANONICAL_TREE)]:
        require(git(runtime, "rev-parse", commit + "^{tree}") == tree, "Anchor tree differs")
    require(not git(runtime, "status", "--porcelain", "--untracked-files=all"), "Runtime checkout is dirty")
    rows = manifest["main_only_files"]
    require(len(rows) == 14 and len({x["path"] for x in rows}) == 14, "Main-only inventory changed")
    actual_diff = git(runtime, "diff", "--name-status", CANONICAL, RUNTIME).splitlines()
    expected_diff = sorted(x["status_code"] + "\t" + x["path"] for x in rows)
    require(sorted(actual_diff) == expected_diff, "Actual complete main delta differs from the fourteen frozen files")
    for row in rows:
        raw = relative(runtime, row["path"]).read_bytes()
        blob = hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
        require(blob == row["git_blob_sha1"], "Main-only file changed: " + row["path"])
        require(git(runtime, "ls-tree", RUNTIME, "--", row["path"]) ==
                "100644 blob " + blob + "\t" + row["path"], "Unexpected main file mode or object")
    # Exact complete tree and fourteen-file delta bind every other runtime/test/
    # build/dependency/deck/policy byte to the accepted canonical source.
    return {"head": RUNTIME, "tree": RUNTIME_TREE, "parents": [MAIN, CANONICAL],
            "canonical": CANONICAL, "canonical_tree": CANONICAL_TREE,
            "complete_main_only_files": rows, "checkout_status": ""}


def original_pins(runtime: Path, group: str, descriptor: dict) -> dict[str, str]:
    pins = {}
    if group == "shared_actor_50":
        extraction = json.loads(relative(runtime, "lab-coordinator/shared-capabilities/actor-input-extraction.json").read_bytes())
        banks = [{row["receiving_path"]: row["receiving_sha256"] for row in extraction["source_records"]},
                 extraction["local_integration_files_sha256"]]
    else:
        budget = json.loads(relative(runtime, "sphinx-approach/STAGE_E_ACTOR_FIXTURE_BUDGET.json").read_bytes())
        scope = json.loads(relative(runtime, "sphinx-approach/STAGE_E_ACTOR_RECEIVING_SCOPE.json").read_bytes())
        banks = [budget["protected_source_sha256"], budget["fixture_source_sha256"], scope["receiving_source_sha256"]]
    for bank in banks:
        for path, expected in bank.items():
            require(path not in pins or pins[path] == expected, "Conflicting original source pin: " + path)
            pins[path] = expected
    require(fingerprint(pins) == descriptor["groups"][group]["original_pins_sha256"], "Original group source scope changed")
    return pins


def _bind(group: str, runtime: Path, control: Path) -> tuple[dict, dict]:
    require(group in REPORTS, "Only the two original actor banks are admitted by these controls")
    require(runtime != control, "Runtime and external control checkouts must be separate")
    event = event_identity()
    raw = relative(control, MANIFEST).read_bytes()
    manifest = json.loads(raw)
    require(manifest["schema"] == "sphinx-main-actor-external-binding/v1", "Wrong main binding schema")
    require(manifest["official_games"] == 0 and manifest["gameplay_authorized"] is False,
            "Software binding cannot grant gameplay authority")
    require(manifest["context"] == CONTEXT and manifest["runtime"] == RUNTIME and
            manifest["runtime_tree"] == RUNTIME_TREE and manifest["canonical"] == CANONICAL and
            manifest["main"] == MAIN, "Frozen source anchors changed")
    controls = control_identity(control, manifest, raw)
    source = runtime_identity(runtime, manifest)
    descriptor_raw = relative(runtime, CANONICAL_DESCRIPTOR).read_bytes()
    require(sha(descriptor_raw) == CANONICAL_DESCRIPTOR_SHA256, "Canonical eleven-path contract changed")
    require(sha(relative(runtime, "sphinx-approach/canonical_receiving_binding.py").read_bytes()) ==
            CANONICAL_HELPER_SHA256, "Canonical receiving helper changed")
    descriptor = json.loads(descriptor_raw)
    require(len(descriptor["allowed_qualification_paths"]) == 11, "Canonical allowance changed")
    review = descriptor["independent_source_review"]
    require(sha(relative(runtime, review["path"]).read_bytes()) == review["sha256"], "Canonical source review changed")
    for path, expected in descriptor["preserved_authority_sha256"].items():
        require(sha(relative(runtime, path).read_bytes()) == expected, "Original authority changed: " + path)
    pins = original_pins(runtime, group, descriptor)
    original_fingerprint = fingerprint(pins)
    for row in descriptor["groups"][group]["explicit_overrides"]:
        require(pins.get(row["path"]) == row["original_sha256"], "Override lost original provenance")
        pins[row["path"]] = row["receiving_sha256"]
    for path, expected in pins.items():
        require(sha(relative(runtime, path).read_bytes()) == expected, "Original qualified runtime source changed: " + path)
    for path in descriptor["qualification_control_paths"] + [review["path"]]:
        observed = sha(relative(runtime, path).read_bytes())
        require(path not in pins or pins[path] == observed, "Canonical control contradicts source pin")
        pins[path] = observed
    proof = {"schema": "sphinx-external-main-binding-proof/v1", "context": CONTEXT, "group": group,
             "runtime": source, "external_controls": controls, "activation_event": event,
             "original_pins_sha256": original_fingerprint, "canonical_descriptor_sha256": CANONICAL_DESCRIPTOR_SHA256,
             "official_games": 0, "gameplay_authorized": False, "independent_receiving_acceptance": False}
    return pins, proof


def observe_source(runtime: Path, control: Path) -> dict:
    """Retain actual inputs before qualification comparisons, including failures."""
    observed = {"identities": {}, "control_files": {}, "runtime_files": {}, "scope_read_errors": []}
    for label, root in [("runtime", runtime), ("control", control)]:
        identity = {}
        for key, args in {"head": ("rev-parse", "HEAD"), "tree": ("rev-parse", "HEAD^{tree}"),
                          "parents": ("show", "-s", "--format=%P", "HEAD"),
                          "status": ("status", "--porcelain", "--untracked-files=all")}.items():
            try:
                identity[key] = git(root, *args)
            except Exception as error:
                identity[key] = {"error_type": type(error).__name__, "message": str(error)}
        observed["identities"][label] = identity
    paths = set(RUNTIME_AUTHORITIES)

    def read_scope(root: Path, path: str, extract) -> None:
        try:
            value = json.loads(relative(root, path).read_bytes())
            names = extract(value)
            require(isinstance(names, list) and all(isinstance(name, str) for name in names),
                    "Source observation scope must be a list of paths")
            paths.update(names)
        except Exception as error:
            observed["scope_read_errors"].append({"path": path, "error_type": type(error).__name__,
                                                   "message": str(error)})

    read_scope(control, MANIFEST, lambda value: [row["path"] for row in value["main_only_files"]])
    read_scope(runtime, CANONICAL_DESCRIPTOR,
               lambda value: list(value["preserved_authority_sha256"]) + value["qualification_control_paths"] +
               [value["independent_source_review"]["path"]] +
               [row["path"] for group in value["groups"].values() for row in group["explicit_overrides"]])
    read_scope(runtime, "lab-coordinator/shared-capabilities/actor-input-extraction.json",
               lambda value: [row["receiving_path"] for row in value["source_records"]] +
               list(value["local_integration_files_sha256"]))
    read_scope(runtime, "sphinx-approach/STAGE_E_ACTOR_FIXTURE_BUDGET.json",
               lambda value: list(value["protected_source_sha256"]) + list(value["fixture_source_sha256"]))
    read_scope(runtime, "sphinx-approach/STAGE_E_ACTOR_RECEIVING_SCOPE.json",
               lambda value: list(value["receiving_source_sha256"]))
    for label, root, names in [("control_files", control, CONTROL_FILES),
                               ("runtime_files", runtime, sorted(paths))]:
        for path in names:
            try:
                raw = relative(root, path).read_bytes()
                observed[label][path] = {"bytes": len(raw), "sha256": sha(raw),
                    "git_blob_sha1": hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()}
            except Exception as error:
                observed[label][path] = {"error_type": type(error).__name__, "message": str(error)}
    return observed


def bind(group: str, runtime: Path, control: Path) -> tuple[dict, dict]:
    runtime, control = runtime.resolve(), control.resolve()
    require(group in REPORTS, "Unknown actor bank")
    base = runtime / "build/reports" / REPORTS[group] / "external-main-binding-attempts"
    base.mkdir(parents=True, exist_ok=True)
    number = 1
    while True:
        out = base / f"attempt-{number:03d}"
        try:
            out.mkdir()
            break
        except FileExistsError:
            number += 1
    record = {"group": group, "status": "ATTEMPTED_NOT_QUALIFIED", "official_games": 0,
              "gameplay_authorized": False, "run_id": os.environ.get("GITHUB_RUN_ID"),
              "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
              "requested_control": os.environ.get("GITHUB_SHA"),
              "requested_runtime": os.environ.get("SPHINX_MAIN_RUNTIME_SHA"), "preserved_files": {}}
    write_json(out / "attempt.json", record)
    try:
        record["observed_source"] = observe_source(runtime, control)
        # Persist observations before comparisons or optional source copying can fail.
        write_json(out / "attempt.json", record)
        for path in CONTROL_FILES:
            source = relative(control, path)
            if source.is_file():
                target = out / "control" / path
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                record["preserved_files"]["control/" + path] = sha(source.read_bytes())
        for path in RUNTIME_AUTHORITIES:
            source = relative(runtime, path)
            if source.is_file():
                target = out / "runtime-authority" / path
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                record["preserved_files"]["runtime-authority/" + path] = sha(source.read_bytes())
        pins, proof = _bind(group, runtime, control)
        record.update(status="BOUND_FOR_ORIGINAL_SOFTWARE_BANK_ONLY", binding=proof)
        return pins, proof
    except Exception as error:
        record.update(status="REJECTED_BINDING_NO_QUALIFICATION", exception={"type": type(error).__name__, "message": str(error)})
        raise
    finally:
        write_json(out / "attempt.json", record)


# Exact previously reviewed owned-process watchdog; qualification authority is separate.
def run(command, cwd, label, timeout):
    row = {'command': command, 'cwd': str(cwd.relative_to(ROOT)), 'started_ns': time.time_ns()}
    path = OUT / (label + '.json')
    path.write_text(json.dumps(row, indent=2) + '\n')
    try:
        with (OUT / (label + '.log')).open('wb') as log:
            process = subprocess.Popen(command, cwd=cwd, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=True)
            row['owned_process_group'] = process.pid
            path.write_text(json.dumps(row, indent=2) + '\n')
            try:
                row['exit_status'] = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                row['timeout_seconds'] = timeout
                row['exit_status'] = 124
                # Retain the original CI watchdog's JVM diagnostics, bounded individually.
                # This fresh runner has one source command; only its process group is killed.
                with (OUT / (label + '-timeout-jvm.log')).open('wb') as diagnostic:
                    java_bin = Path(os.environ.get('JAVA_HOME', '/nonexistent')) / 'bin'
                    try:
                        jps = subprocess.run([str(java_bin / 'jps'), '-q'], stdout=subprocess.PIPE,
                                             stderr=subprocess.STDOUT, timeout=15)
                        diagnostic.write(jps.stdout)
                        for raw_pid in jps.stdout.decode(errors='replace').splitlines():
                            if raw_pid.isdigit():
                                try:
                                    if os.getpgid(int(raw_pid)) == process.pid:
                                        subprocess.run([str(java_bin / 'jstack'), '-l', raw_pid],
                                            stdout=diagnostic, stderr=subprocess.STDOUT, timeout=20)
                                except (ProcessLookupError, subprocess.TimeoutExpired):
                                    diagnostic.write(b'JVM departed or bounded thread dump timed out\n')
                    except (OSError, subprocess.TimeoutExpired):
                        diagnostic.write(b'JVM inventory unavailable within bounded timeout\n')
                for sig in [signal.SIGTERM, signal.SIGKILL]:
                    try:
                        os.killpg(process.pid, sig)
                    except ProcessLookupError:
                        pass
                    if sig == signal.SIGTERM:
                        time.sleep(5)
                process.wait(timeout=10)
                # SIGKILL covers all remaining members before XML copying starts.
                row['owned_process_group_terminated'] = True
    finally:
        row['finished_ns'] = time.time_ns()
        path.write_text(json.dumps(row, indent=2) + '\n')
    return row

def execute_original_bank(group: str, runtime: Path, control: Path) -> int:
    global ROOT, OUT
    bind(group, runtime, control)
    ROOT = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
    OUT = ROOT / "output" / group
    OUT.mkdir(parents=True, exist_ok=True)
    classes = {
        "shared_actor_50": ["ActorObservationBoundaryTest", "ActorStackSourceObservationTest",
                            "ActorTriggerOrderObservationTest", "ActorPriorityObservationTest"],
        "sphinx_actor_136": ["ActorSpellPaymentProjectionTest", "SphinxStageEActorAdapterTest",
                             "ActorObservationBoundaryTest", "ActorStackSourceObservationTest",
                             "ActorTriggerOrderObservationTest", "ActorPriorityObservationTest",
                             "SphinxStageEPilotComponentTest"],
    }[group]
    command = ["just", "test-class", classes[0]]
    for name in classes[1:]:
        command.extend(["--tests", "*" + name])
    command.extend(["--rerun", "--max-workers=1", "-PkotlinCompileParallelism=1",
                    "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx4g",
                    "--info", "--stacktrace"])
    exit_code = 255
    try:
        result = run(command, runtime, "test-command", timeout=1200)
        exit_code = result["exit_status"]
        return exit_code
    finally:
        for target in [OUT / "test-exit-code.txt",
                       runtime / "build/reports" / REPORTS[group] / "test-exit-code.txt"]:
            target.write_text(str(exit_code) + "\n")


if __name__ == "__main__":
    require(len(sys.argv) == 3 and sys.argv[1] == "run-bank", "Only the fixed run-bank entry point is supported")
    control_root = Path(__file__).resolve().parents[1]
    runtime_root = Path(os.environ["SPHINX_MAIN_RUNTIME_ROOT"]).resolve()
    raise SystemExit(execute_original_bank(sys.argv[2], runtime_root, control_root))
