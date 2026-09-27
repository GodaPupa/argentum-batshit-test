"""Finite, one-use software qualification; never an experimental executor."""
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import resource
import shutil
import signal
import stat
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "observer-qualification-output"
BRANCH = "refs/heads/lab/parent-jvm-observer-qualification-20260927"
MAX_LOG = 4 * 1024 * 1024
MAX_ARCHIVE_INPUT = 32 * 1024 * 1024
MAX_MEMBERS = 1500
SOURCE_CAPTURE_LIMIT = 256 * 1024
CONTROL_FILES = (
    ".github/workflows/parent-jvm-observer-qualification.yml",
    "CONTROL_MANIFEST.json", "QUALIFICATION_PROPOSAL.md", "SOURCE_MANIFEST.json",
    "tools/observed_test_runtime.py", "tools/parent_jvm_observer.py",
    "tools/qualify_parent_jvm_observer.py", "tools/test_parent_jvm_observer.py",
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def save(path, data):
    with path.open("xb") as stream:
        stream.write((json.dumps(data, indent=2, sort_keys=True) + "\n").encode())
        stream.flush()
        os.fsync(stream.fileno())


def require(value, message):
    if not value:
        raise ValueError(message)


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT, timeout=5)


def child_limit():
    resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_LOG, MAX_LOG))


def reap_owned_children():
    """This process is a subreaper; terminate/reap only its actual children."""
    deadline = time.monotonic() + 5
    children_file = Path(f"/proc/{os.getpid()}/task/{os.getpid()}/children")
    while True:
        for text in children_file.read_text().split():
            try:
                os.kill(int(text), signal.SIGKILL)
            except ProcessLookupError:
                pass
        try:
            while os.waitpid(-1, os.WNOHANG)[0]:
                pass
        except ChildProcessError:
            return
        require(time.monotonic() < deadline, "Owned-child cleanup did not finish within five seconds")
        time.sleep(0.01)


def run(name, argv, seconds, env):
    save(OUTPUT / (name + "-command.json"), {"argv": argv, "outer_timeout_seconds": seconds, "per_file_limit": MAX_LOG})
    timed_out = False
    with (OUTPUT / (name + ".stdout")).open("xb") as out, (OUTPUT / (name + ".stderr")).open("xb") as err:
        process = subprocess.Popen(argv, cwd=ROOT, env=env, stdout=out, stderr=err,
                                   start_new_session=True, preexec_fn=child_limit)
        try:
            returncode = process.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            os.killpg(process.pid, signal.SIGKILL)
            returncode = process.wait(timeout=5)
        finally:
            # Includes an adopted probe JVM/Python child from any separate session.
            reap_owned_children()
    record = {"returncode": returncode, "timeout": timed_out, "owned_descendants_terminated_and_reaped": True}
    save(OUTPUT / (name + "-exit.json"), record)
    require(returncode == 0 and not timed_out, name + " failed; complete bounded logs retained")


def capture_inputs(label):
    """Retain bounded actual attempted bytes and errors before comparisons."""
    source = OUTPUT / label
    source.mkdir(exist_ok=False)
    observed = {"files": {}, "git": {}}
    for name in CONTROL_FILES:
        path = ROOT / name
        row = {}
        try:
            mode = path.lstat().st_mode
            row["mode"] = mode
            row["canonical"] = path.resolve(strict=True) == path
            if stat.S_ISLNK(mode):
                row.update(kind="symlink", link_target=os.readlink(path), capture_complete=False)
            elif not stat.S_ISREG(mode) or not row["canonical"]:
                row.update(kind="nonregular_or_noncanonical", capture_complete=False)
            else:
                row.update(kind="regular", bytes_on_disk=path.stat().st_size)
                descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
                with os.fdopen(descriptor, "rb") as stream:
                    require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode), "Source became nonregular before capture")
                    data = stream.read(SOURCE_CAPTURE_LIMIT + 1)
                complete = len(data) <= SOURCE_CAPTURE_LIMIT and len(data) == row["bytes_on_disk"]
                retained = data[:SOURCE_CAPTURE_LIMIT]
                destination = source / name
                destination.parent.mkdir(parents=True, exist_ok=True)
                with destination.open("xb") as output:
                    output.write(retained)
                row.update(bytes_captured=len(retained), sha256=sha(retained), capture_complete=complete,
                           git_blob_sha1=hashlib.sha1(b"blob " + str(len(retained)).encode() + b"\0" + retained).hexdigest() if complete else None)
        except Exception as error:
            row["error"] = {"type": type(error).__name__, "message": str(error)}
        observed["files"][name] = row
        save(source / ("capture-record-" + str(len(observed["files"])) + ".json"), {"path": name, **row})
    for name, args in {
        "head": ("rev-parse", "HEAD"), "tree": ("rev-parse", "HEAD^{tree}"),
        "ls_tree": ("ls-tree", "-r", "HEAD"),
        "tracked_status": ("status", "--porcelain=v1", "--untracked-files=no"),
    }.items():
        try:
            raw = git(*args)
            require(len(raw) <= MAX_LOG, "Git identity record exceeded output bound")
            observed["git"][name] = raw.decode().strip() if name in ("head", "tree") else raw.decode()
        except Exception as error:
            observed["git"][name] = {"error": {"type": type(error).__name__, "message": str(error)}}
        save(source / ("git-" + name + ".json"), {"observed": observed["git"][name]})
    save(OUTPUT / (label + ".json"), observed)
    return observed


def validate_inputs(observed, manifest):
    """Validate only after the actual attempted bytes/identities are durable."""
    require(all(isinstance(value, str) for value in observed["git"].values()), "Missing actual Git identity")
    require(observed["git"]["head"] == os.environ.get("GITHUB_SHA"), "Checkout commit mismatch")
    require(observed["git"]["tracked_status"] == "", "Tracked working-tree or index drift")
    committed = {}
    for line in observed["git"]["ls_tree"].splitlines():
        header, path = line.split("\t", 1)
        mode, kind, blob = header.split()
        require(path not in committed, "Duplicate committed path")
        committed[path] = {"mode": mode, "kind": kind, "blob": blob}
    require(set(committed) == set(CONTROL_FILES), "Unexpected source tree membership")
    for path in CONTROL_FILES:
        row = observed["files"][path]
        require(row.get("capture_complete") and row.get("kind") == "regular" and row.get("canonical"), "Incomplete/nonregular source capture: " + path)
        require(committed[path]["kind"] == "blob" and committed[path]["mode"] == "100644" and not row["mode"] & 0o111,
                "Unexpected source mode: " + path)
        require(row["git_blob_sha1"] == committed[path]["blob"], "Actual bytes differ from committed blob: " + path)
    rows = manifest["files"]
    require(len(rows) == len(CONTROL_FILES) - 1 and {row["path"] for row in rows} == set(CONTROL_FILES) - {"CONTROL_MANIFEST.json"},
            "Control manifest source inventory differs")
    for row in rows:
        actual = observed["files"][row["path"]]
        require(actual["bytes_captured"] == row["bytes"] and actual["sha256"] == row["sha256"], "Declared source hash mismatch: " + row["path"])


def archive():
    """Keep symlink bytes/types; never follow fake-procfs links during archival."""
    total = 0
    members = []
    with zipfile.ZipFile(ROOT / "observer-qualification-original.zip", "x", compression=zipfile.ZIP_DEFLATED) as target:
        for directory, dirs, files in os.walk(OUTPUT, followlinks=False):
            dirs.sort()
            for name in sorted(files):
                path = Path(directory) / name
                mode = path.lstat().st_mode
                label = path.relative_to(OUTPUT).as_posix()
                require(len(members) < MAX_MEMBERS, "Evidence member limit exceeded; incomplete archive retained")
                if stat.S_ISLNK(mode):
                    data = os.readlink(path).encode()
                    kind = "symlink_target_bytes"
                elif stat.S_ISREG(mode):
                    require(path.stat().st_size <= MAX_LOG, "Evidence file limit exceeded; incomplete archive retained")
                    data = path.read_bytes()
                    kind = "regular"
                else:
                    # A killed FIFO test may not run its finally block. Preserve
                    # the type, never open a pipe/device for archival.
                    data = (json.dumps({"original_path": label, "mode": mode, "scope": "Special-file metadata only; no readable byte stream"}) + "\n").encode()
                    label += ".special-file-metadata.json"
                    kind = "special_file_metadata"
                    mode = stat.S_IFREG | 0o644
                total += len(data)
                require(total <= MAX_ARCHIVE_INPUT, "Evidence total limit exceeded; incomplete archive retained")
                info = zipfile.ZipInfo(label)
                info.create_system = 3
                info.external_attr = mode << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                target.writestr(info, data)
                members.append({"path": label, "kind": kind, "bytes": len(data), "sha256": sha(data)})
    save(ROOT / "observer-qualification-archive.json", {"members": members, "uncompressed_bytes": total,
         "zip_bytes": (ROOT / "observer-qualification-original.zip").stat().st_size,
         "zip_sha256": sha((ROOT / "observer-qualification-original.zip").read_bytes())})


def main():
    os.chdir(ROOT)
    OUTPUT.mkdir(exist_ok=False)
    result = {"schema": "parent-jvm-observer-software-qualification-v1", "status": "INCOMPLETE",
              "admission": False, "official_allocations": 0, "engine_initializations": 0}
    save(OUTPUT / "intent.json", {**result, "control_sha256": sha(Path(__file__).read_bytes())})
    save(OUTPUT / "attempt-context.json", {key: os.environ.get(key) for key in
         ("GITHUB_SHA", "GITHUB_REF", "GITHUB_EVENT_NAME", "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT")})
    try:
        before = capture_inputs("source-before")
        result.update(attempted_control_commit=before["git"].get("head"), attempted_control_tree=before["git"].get("tree"))
        require(os.environ.get("GITHUB_EVENT_NAME") == "push" and os.environ.get("GITHUB_REF") == BRANCH,
                "Wrong qualification event/ref")
        require(os.environ.get("GITHUB_RUN_ATTEMPT") == "1", "Only original creation attempt is permitted")
        event_path = Path(os.environ["GITHUB_EVENT_PATH"])
        require(event_path.stat().st_size <= 1024 * 1024, "Event metadata is oversized")
        event = json.loads(event_path.read_bytes())
        require(event.get("before") == "0" * 40, "Only original branch creation is permitted")
        require(ctypes.CDLL(None, use_errno=True).prctl(36, 1, 0, 0, 0) == 0, "Could not establish owned-child subreaper")
        manifest = json.loads((OUTPUT / "source-before/CONTROL_MANIFEST.json").read_text())
        require(manifest["schema"] == "parent-jvm-observer-isolated-control-v1", "Wrong control manifest")
        validate_inputs(before, manifest)
        result.update(control_commit=git("rev-parse", "HEAD").decode().strip(), control_tree=git("rev-parse", "HEAD^{tree}").decode().strip(),
                      run_id=os.environ.get("GITHUB_RUN_ID"), run_attempt=1, event="creation_push")
        require(result["control_commit"] == os.environ.get("GITHUB_SHA"), "Checkout commit mismatch")
        java = Path(shutil.which("java") or "missing").resolve(strict=True)
        javac = Path(shutil.which("javac") or "missing").resolve(strict=True)
        java_home = Path(os.environ["JAVA_HOME"]).resolve(strict=True)
        require(java == java_home / "bin/java" and javac == java_home / "bin/javac", "Unexpected Java launcher/compiler root")
        release = (java_home / "release").read_bytes()
        require(re.search(rb'^JAVA_VERSION="21(?:[.\-+][^"\n]*)?"$', release, re.M), "JDK21 is required")
        (OUTPUT / "jdk-release.txt").write_bytes(release)
        save(OUTPUT / "environment.json", {"python_version": sys.version, "python_executable": str(Path(sys.executable).resolve()),
             "python_sha256": sha(Path(sys.executable).resolve().read_bytes()), "java_path": str(java), "java_sha256": sha(java.read_bytes()),
             "javac_path": str(javac), "javac_sha256": sha(javac.read_bytes()), "jdk_release_sha256": sha(release),
             "scope": "Reported/hash-bound qualification tools, not full JDK/native/loaded-class closure"})
        fixtures = OUTPUT / "fixtures"
        fixtures.mkdir()
        env = dict(os.environ, PARENT_JVM_OBSERVER_FIXTURE_ROOT=str(fixtures), PYTHONDONTWRITEBYTECODE="1", PYTHONNOUSERSITE="1")
        run("unit21", ["timeout", "--signal=TERM", "--kill-after=5s", "60s", sys.executable, "tools/test_parent_jvm_observer.py", "-v"], 70, env)
        log = (OUTPUT / "unit21.stderr").read_text()
        source_manifest = json.loads((ROOT / "SOURCE_MANIFEST.json").read_text())
        require(source_manifest["unit_fixture_count"] == 21 and len(source_manifest["unit_fixture_identities"]) == 21, "Wrong finite fixture inventory")
        actual = re.findall(r'^(test_\w+) \([^\n]+\) \.\.\. ok$', log, re.M)
        require(sorted(actual) == sorted(source_manifest["unit_fixture_identities"]), "Actual unittest identity bank mismatch")
        require(re.search(r'\nRan 21 tests in [^\n]+\n\nOK\n\Z', log), "Unittest summary is not a clean21-case execution")
        run("actual-parent-probe", ["timeout", "--signal=TERM", "--kill-after=5s", "60s", sys.executable,
                                    "tools/test_parent_jvm_observer.py", "--actual-jvm-probe"], 70, env)
        probe = json.loads((fixtures / "actual-parent-probe/result.json").read_text())
        require(probe["status"] == "PASS_READ_ONLY_ACTUAL_PARENT_PROBE" and probe["admission"] is False, "Actual probe did not pass")
        require(probe["claims_created"] == probe["engine_initializations"] == 0, "Unexpected scope counters")
        after = capture_inputs("source-after")
        validate_inputs(after, manifest)
        require(after == before, "Qualification source or control manifest changed")
        result.update(status="PASS_BOUNDED_OBSERVATION_COMPONENT_ONLY", unit_cases=21, unit_failures=0, unit_errors=0,
                      unit_skips=0, actual_parent_probes=1, probe_result_sha256=sha((fixtures / "actual-parent-probe/result.json").read_bytes()))
    except Exception as error:
        result.update(status="FAILED_SOFTWARE_QUALIFICATION", error={"type": type(error).__name__, "message": str(error)})
    finally:
        save(OUTPUT / "result.json", result)
        try:
            archive()
        except Exception as error:
            save(ROOT / "observer-qualification-archive-error.json", {"type": type(error).__name__, "message": str(error), "complete": False})
            result["status"] = "FAILED_EVIDENCE_ARCHIVE"
    print(json.dumps(result, sort_keys=True))
    return 0 if result["status"] == "PASS_BOUNDED_OBSERVATION_COMPONENT_ONLY" else 1


if __name__ == "__main__":
    raise SystemExit(main())
