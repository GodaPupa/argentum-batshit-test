#!/usr/bin/env python3
"""Qualify five existing corrected card fixtures; no baseline or gameplay admission."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import selectors
import subprocess
import time
import xml.etree.ElementTree as ET

SOURCE = "af8685ec97fd0f56195f25e7d3ca83c609fa018a"
TREE = "b46e165748abf32b2c8856052e5df3d8ffa910f9"
ROOT = Path(os.environ["GITHUB_WORKSPACE"])
CHECKOUT = ROOT / "source"
CONTROL = ROOT / "control"
OUT = ROOT / "output"
CONTROL_PATHS = [".github/workflows/izzet-scenario-sequence-qualification.yml",
                 "qualify.py", "gate.json"]


def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_snapshot(gate):
    result = {"head": git(CHECKOUT, "rev-parse", "HEAD"),
              "tree": git(CHECKOUT, "rev-parse", "HEAD^{tree}"),
              "status": git(CHECKOUT, "status", "--porcelain"),
              "authority_sha256": {}, "test_source_git_blobs": {}}
    assert result["head"] == gate["source_head"] == SOURCE
    assert result["tree"] == gate["source_tree"] == TREE
    assert not result["status"], "Tracked/untracked source changed"
    for path, expected in gate["preserved_authority_sha256"].items():
        actual = sha(CHECKOUT / path)
        result["authority_sha256"][path] = actual
        assert actual == expected, "Preserved authority changed: " + path
    for bank in gate["banks"]:
        path = bank["test_source"]
        actual = git(CHECKOUT, "hash-object", path)
        result["test_source_git_blobs"][path] = actual
        assert actual == bank["test_source_git_blob"], "Test source changed: " + path
    return result


def collect_xml(path, bank, started, finished):
    raw = path.read_bytes()
    suite = ET.fromstring(raw)
    cases = suite.findall("testcase")
    names = [case.attrib["name"] for case in cases]
    assert suite.tag == "testsuite" and suite.attrib["name"] == bank["class"]
    assert len(cases) == int(suite.attrib["tests"]) == bank["expected_cases"]
    assert len(names) == len(set(names)), "Duplicate test identity"
    assert sorted(names) == sorted(bank["case_names"]), "Original case identity drift"
    assert all(case.attrib.get("classname") == bank["class"] for case in cases)
    assert not any(int(suite.attrib.get(key, 0)) for key in ("failures", "errors", "skipped"))
    assert not any(suite.findall(".//" + key) for key in ("failure", "error", "skipped"))
    # Gradle output directories are required empty before this command; also reject stale/future XML.
    assert started - 2_000_000_000 <= path.stat().st_mtime_ns <= finished + 2_000_000_000
    return {"class": bank["class"], "actual_cases": len(cases), "case_names": names,
            "xml_sha256": hashlib.sha256(raw).hexdigest()}


def directory_bytes(path):
    return sum(p.stat().st_size for p in path.rglob('*') if p.is_file()) if path.exists() else 0


def terminate_owned_group(process):
    # This Popen owns a new session; never signal another worker or runner process.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    finally:
        # The wrapper may have exited while a child still owns its pipe/session.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=5)


def run_capped(command, root, log_path, reports, out, limits):
    start = time.monotonic()
    reason = None
    written = 0
    discarded_observed_bytes = 0
    process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    try:
        with log_path.open('wb') as log:
            while selector.get_map():
                if time.monotonic() - start > limits['command_timeout_seconds']:
                    reason = 'COMMAND_TIMEOUT'; break
                if directory_bytes(reports) > limits['test_result_bytes']:
                    reason = 'TEST_RESULT_BYTE_LIMIT'; break
                if directory_bytes(out) >= limits['artifact_bytes'] - 4194304:
                    reason = 'ARTIFACT_BYTE_LIMIT'; break
                for key, _ in selector.select(timeout=0.1):
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    remaining = min(limits['stdout_bytes'] - written, limits['artifact_bytes'] - directory_bytes(out) - 4194304)
                    keep = chunk[:max(0, remaining)]
                    log.write(keep); log.flush(); written += len(keep)
                    if len(keep) < len(chunk):
                        discarded_observed_bytes += len(chunk) - len(keep)
                        reason = 'STDOUT_OR_ARTIFACT_BYTE_LIMIT'; break
                if reason:
                    break
            if not reason:
                try:
                    process.wait(timeout=max(0.01, limits['command_timeout_seconds'] - (time.monotonic() - start)))
                except subprocess.TimeoutExpired:
                    reason = 'COMMAND_TIMEOUT'
    finally:
        if reason or process.poll() is None:
            terminate_owned_group(process)
        selector.close()
        process.stdout.close()
    return {'exit_status': process.returncode, 'technical_limit': reason, 'stdout_retained_bytes': written,
            'discarded_observed_pipe_bytes': discarded_observed_bytes, 'output_complete': reason is None,
            'elapsed_seconds': time.monotonic() - start}



def run_bank(bank, index, limits):
    folder = OUT / f"{index:02d}-{bank['stage']}"
    folder.mkdir()
    xml_dir = CHECKOUT / bank["module"] / "build/test-results/test"
    # Only generated reports in this freshly created, owned qualification checkout are removed.
    for path in xml_dir.glob("TEST-*.xml"):
        path.unlink()
    assert not list(xml_dir.glob("TEST-*.xml"))
    command = ["just", "test-class", bank["class"].rsplit(".", 1)[1],
               "--rerun", "--no-build-cache", "--no-daemon", "-DupdateSnapshots=false", "--info", "--stacktrace",
               "--max-workers=1", "-PkotlinCompileParallelism=1",
               "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx4g"]
    row = {"stage": bank["stage"], "scope": bank["scope"], "class": bank["class"],
           "module": bank["module"], "expected_cases": bank["expected_cases"],
           "command": command, "status": "INCOMPLETE", "started_ns": time.time_ns()}
    (folder / "command.json").write_text(json.dumps(row, indent=2) + "\n")
    try:
        row.update(run_capped(command, CHECKOUT, folder / "command.log", xml_dir, OUT, limits))
        row["finished_ns"] = time.time_ns()
        (folder / "exit-status.txt").write_text(str(row["exit_status"]) + "\n")
        # A final XML flush can land after the pipe monitor's last size check.
        row["observed_report_bytes_after_exit"] = directory_bytes(xml_dir)
        if row["observed_report_bytes_after_exit"] > limits["test_result_bytes"]:
            row["technical_limit"] = "TEST_RESULT_BYTE_LIMIT"
            row["output_complete"] = False
        paths = sorted(xml_dir.glob("TEST-*.xml"))
        # Preserve every produced XML (or explicitly marked bounded prefix) before interpretation.
        row["xml_retention"] = []
        for path in paths:
            size = path.stat().st_size
            allowed = max(0, min(limits["test_result_bytes"], limits["artifact_bytes"] - directory_bytes(OUT) - 4194304))
            with path.open("rb") as source:
                raw = source.read(min(size, allowed))
            complete = len(raw) == size
            (folder / (path.name if complete else path.name + ".partial")).write_bytes(raw)
            row["xml_retention"].append({"name": path.name, "observed_bytes": size,
                "retained_bytes": len(raw), "complete": complete, "sha256": hashlib.sha256(raw).hexdigest()})
        if not all(item["complete"] for item in row["xml_retention"]):
            row["technical_limit"] = "INCOMPLETE_XML_BYTE_LIMIT"
            row["output_complete"] = False
        assert row["output_complete"], "Technical resource stop; prefixes are incomplete evidence"
        assert row["exit_status"] == 0, "Qualification command failed"
        assert len(paths) == 1 and paths[0].name == "TEST-" + bank["class"] + ".xml", "Suite mismatch"
        row["actual"] = collect_xml(paths[0], bank, row["started_ns"], row["finished_ns"])
        task = ":" + bank["module"].replace("/", ":") + ":test"
        lines = (folder / "command.log").read_text().splitlines()
        assert any(line.strip() == "> Task " + task for line in lines), "No fresh required test task"
        assert not any(line.strip() == "> Task " + task + " " + suffix
                       for line in lines for suffix in ("FROM-CACHE", "UP-TO-DATE", "NO-SOURCE", "SKIPPED"))
        assert any("Gradle Test Executor" in line and "started executing tests" in line for line in lines)
        assert any("Gradle Test Executor" in line and "finished executing tests" in line for line in lines)
        assert not git(CHECKOUT, "status", "--porcelain"), "Source changed during bank"
        row["status"] = "PASS_PRESERVED_CASE_IDENTITIES"
    except Exception as error:
        row["status"] = "FAILED_PRESERVED_FOR_REVIEW"
        row["error"] = type(error).__name__ + ": " + str(error)
    finally:
        (folder / "command.json").write_text(json.dumps(row, indent=2) + "\n")
    return row


def main():
    OUT.mkdir(exist_ok=False)
    manifest = {"schema": "izzet-five-scenario-sequence-qualification-audit-v1", "status": "INCOMPLETE",
                "source_head": SOURCE, "source_tree": TREE,
                "control_head": None, "control_files_sha256": {},
                "run_id": os.environ.get("GITHUB_RUN_ID"), "attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
                "event": os.environ.get("GITHUB_EVENT_NAME"), "stages": [], "errors": [],
                "new_pilot_cases": 0, "official_games": 0, "official_seeds": 0,
                "gameplay_authorized": False, "full_runtime_accepted": False}
    (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
    try:
        # Retain the exact attempted controls even if JSON parsing or readiness rejects them.
        for path in CONTROL_PATHS:
            target = OUT / "control" / path
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(CONTROL / path, target)
            manifest["control_files_sha256"][path] = sha(target)
            (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
        gate = json.loads((CONTROL / "gate.json").read_text())
        manifest["control_head"] = git(CONTROL, "rev-parse", "HEAD")
        assert os.environ["GITHUB_REPOSITORY"] == "GodaPupa/argentum-batshit-test"
        assert os.environ["GITHUB_RUN_ATTEMPT"] == "1" and os.environ["GITHUB_EVENT_NAME"] == "push"
        assert manifest["control_head"] == os.environ["GITHUB_SHA"]
        assert not git(CONTROL, "status", "--porcelain")
        assert sorted(git(CONTROL, "ls-files").splitlines()) == sorted(CONTROL_PATHS)
        assert gate["ready_for_execution"] is True and gate["source_review"]
        assert os.environ["GITHUB_REF"] == "refs/heads/lab/izzet-scenario-sequence-qualification-20260927"
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        assert event["before"] == "0" * 40, "Only the one reviewed branch-creation event is admitted"
        assert shutil.disk_usage(ROOT).free >= gate["resource_limits"]["minimum_free_bytes"]
        identities = [(bank["class"], name) for bank in gate["banks"] for name in bank["case_names"]]
        assert len(gate["banks"]) == 2 and len(identities) == len(set(identities)) == 5
        manifest["source_before"] = source_snapshot(gate)
        manifest["dependency_files_sha256"] = {p: sha(CHECKOUT / p) for p in gate["dependency_files"]}
        (OUT / "source-before.json").write_text(json.dumps(manifest["source_before"], indent=2) + "\n")
        (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
        for index, bank in enumerate(gate["banks"], 1):
            assert source_snapshot(gate) == manifest["source_before"], "Source drift before stage"
            manifest["current_stage"] = bank["stage"]
            (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
            manifest["stages"].append(run_bank(bank, index, gate["resource_limits"]))
            (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")
            if manifest["stages"][-1].get("technical_limit"):
                manifest["unattempted_classes"] = [rest["class"] for rest in gate["banks"][index:]]
                break
        manifest["source_after"] = source_snapshot(gate)
        (OUT / "source-after.json").write_text(json.dumps(manifest["source_after"], indent=2) + "\n")
        assert manifest["source_before"] == manifest["source_after"]
        assert manifest["control_files_sha256"] == {p: sha(CONTROL / p) for p in CONTROL_PATHS}
        assert manifest["dependency_files_sha256"] == {p: sha(CHECKOUT / p) for p in gate["dependency_files"]}
        assert all(row["status"] == "PASS_PRESERVED_CASE_IDENTITIES" for row in manifest["stages"]), "One or more preserved classes failed; see raw artifacts"
        assert sum(row["actual"]["actual_cases"] for row in manifest["stages"]) == 5
        manifest["status"] = "PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW"
    except Exception as error:
        manifest["errors"].append(type(error).__name__ + ": " + str(error))
        raise
    finally:
        (OUT / "audit.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
