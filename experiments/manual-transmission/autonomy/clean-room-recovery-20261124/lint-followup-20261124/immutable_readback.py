#!/usr/bin/env python3
"""Independently read back a published local evidence archive.

Read-only Git operations plus temporary data extraction and reexecution of the
exact hash-pinned immutable S2 auditor. No checkout, build/test/qualification,
active-source access, archived-code execution, CI receiving, or publication.
The original ZIP is assembled byte-for-byte in temporary scratch, never repacked.
Run only after root review and publication of the terminal collection.

Example:
  python immutable_readback.py --repo CARRIER --receipt-commit FULL_SHA \
    --directory experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124/LOCAL_QUALIFICATION_01 \
    --expected-live-head FULL_SHA --output-json /scratch/readback.json

Exit 0 means complete archive/publication readback, including a declared failed
qualification with intact manifests. It never admits the capability or Phase 2.
Any missing/invalid manifest produces a nonzero exit while retaining successful
part/archive/outer checks in the JSON report.
"""
import argparse
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import sys
import types

sys.dont_write_bytecode = True
import zipfile

SOURCE = "82ec335573572edfd2a2e4d734d2f0d81801139f"
TREE = "923ec79e1c867eedf35b408153bf60988fbd26b6"
LAUNCH = "9953c3e78c6f386f4284352f39ccf5d81554e10b"
EPOCH = "manual-lint-followup-20261124-01"
BRANCH = "refs/heads/manual-transmission/clean-room-recovery-20261124"
PASS = "ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED"
FAIL = "FAILED_OR_INCOMPLETE_LOCAL_ORIGINAL_PRESERVED"
CHARTER_SHA = "a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e"
PINS_SHA = "b0e8da912f98b75a35c44db4a3e9e3334d941af3891e0bb28281490ab01e7bbd"
AUDIT_SHA = "aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81"
PRESERVER_SHA = "8775d77e96cd382afc348457fb77cb1744b578628bede0a265fec96eecb510ba"
CHARTER_COMMIT = "86450c1ecb227804dbd45acf5ba28689ce0c61bc"
CHARTER_BLOB = "418fb7f5a0ebeed92582f8285e76305c39250efa"
CHUNK = 450000
SOURCE_BASIS = '2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
RECORD = 'experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
AUDIT_SOURCE_ROOT = "review/immutable-audit-source"
AUDIT_INPUTS = {
    "experiments/manual-transmission/research/final-compatibility-inventory-r1-20261027/CLIENT64_CONTRACT.json": {
        "blob": "cc710313ca994088495d713567df0300479c5316",
        "bytes": 7346,
        "sha256": "1a89fdb2d7e0580153bfb93e7e1babb99d64e8a1f87e53431724e2ee36dfff7f"
    },
    "experiments/manual-transmission/research/reckless-own-prior-spell-diagnostic-proposal-20261106/PROTOCOL.json": {
        "blob": "e5cb500bb419587f219c999ff133a40af8dd68fe",
        "bytes": 15193,
        "sha256": "77d7d24c092961ddc6803e41c90c13e84dfbcb3fe57827c04b1bbb930d8264fc"
    },
    "experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114/ACCEPTED_INVENTORY.json.gz.b64": {
        "blob": "0ffde46d47183dcfe0904e3dc6344b7525827d07",
        "bytes": 648641,
        "sha256": "e5d75cfdede337d11316546d3b733f42d427f6ff36c6c739e42be11a98d13499"
    },
    "experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114/COMPILE_WITNESSES.json": {
        "blob": "af53be2ee07bae95b76b4b63d424ee41a764e578",
        "bytes": 14045,
        "sha256": "1a1833d56f44ff5fb6d0be6a97bf1ae42a9b68dfa5138bee5f927a24cd0cbd90"
    },
    "experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114/TEST_MATRIX.json": {
        "blob": "fdf6986b134af8a849b5d3f9a4ddf6c91d7ed691",
        "bytes": 66624,
        "sha256": "79389b761cd97a238d20c5d524a2083d16b9a325067772a85224c057b97e0494"
    },
    "experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114/audit_reports.py": {
        "blob": "e04e119a4a24ba200f3d165ac94ac8688b6350ec",
        "bytes": 17257,
        "sha256": "aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81"
    }
}
if any(str(value).startswith('PENDING_') for value in (SOURCE, TREE, LAUNCH, CHARTER_SHA, PINS_SHA, AUDIT_SHA, PRESERVER_SHA,)):
    raise RuntimeError('Unfinalized lint-followup draft: fill all pinned identities before use')



def need(condition, message):
    if not condition:
        raise ValueError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def exact_int(value, expected, name):
    need(type(value) is int and value == expected, name)


def digest_string(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def json_value(data):
    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, "duplicate JSON key: " + key)
            result[key] = value
        return result
    return json.loads(data.decode("utf-8"), object_pairs_hook=pairs,
                      parse_constant=lambda _: (_ for _ in ()).throw(ValueError("non-finite JSON number")))


def safe_name(name):
    need(isinstance(name, str) and name, "empty/non-string member name")
    need(not any(ord(c) < 32 or ord(c) == 127 for c in name), "control character in member name")
    need("\\" not in name and ":" not in name, "unsafe member separator/drive")
    need(all(p not in ("", ".", "..") for p in name.split("/")), "unsafe member path")
    return name


def manifest_value(data):
    value = json_value(data)
    need(isinstance(value, dict), "manifest must be a mapping")
    for name, value_sha in value.items():
        safe_name(name)
        need(digest_string(value_sha), "invalid manifest SHA256: " + name)
    return value


def audit_counts(value):
    mutants = {
        "M01-surge-price": 5, "M02-red-payment": 20,
        "M03-paid-guard": 32, "M04-other-filter": 32,
        "M05-controller-filter": 32, "M06-toughness": 32,
        "M07-granted-haste": 32, "M08-duration": 32,
        "M09-intrinsic-haste": 32, "M10-entity-only-exclusion": 10,
        "M11-exclude-none": 10, "M12-followed-origin": 10,
        "M13-missing-choice": 32, "M14-copy-choice": 20,
    }
    need(isinstance(value, dict) and set(value) == set(mutants) | {"normal", "restored", "cards", "ordinary", "client"},
         "raw audit exact gate set")
    def row(name, expected):
        observed = value[name]
        need(isinstance(observed, dict) and set(observed) == set(expected), "raw audit fields: " + name)
        for key, number in expected.items():
            exact_int(observed[key], number, "raw audit count: " + name + "/" + key)
    for name in ("normal", "restored"):
        row(name, {"rows": 67, "skips": 0, "failures": 0})
    row("cards", {"rows": 401, "skips": 0, "failures": 0})
    row("ordinary", {"rows": 19429, "skips": 43, "failures": 0})
    for name, count in mutants.items():
        row(name, {"rows": count, "executed": 2, "skips": count - 2, "failures": 1})
    client = value["client"]
    need(isinstance(client, dict) and set(client) == {"focused", "full", "typecheck"}, "client audit fields")
    exact_int(client["focused"], 70, "focused client count")
    need(type(client["full"]) is int and client["full"] >= 70 and client["typecheck"] is True,
         "full client/typecheck audit")


class Reader:
    def __init__(self, args):
        self.args = args
        self.report = {
            "schema": "manual-transmission/immutable-local-readback/v1",
            "readback_program_sha256": sha(Path(__file__).read_bytes()),
            "started_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "receipt_commit": args.receipt_commit, "directory": args.directory,
            "expected_source": SOURCE, "expected_live_head": args.expected_live_head,
            "remote": args.remote, "branch": BRANCH, "checks": {},
            "qualification_invocations": 0, "raw_auditor_reexecuted": False,
            "source_bindings_reexecuted": False, "ci_activations": 0,
            "original_ci_artifact_receives": 0, "admitted": False,
            "phase2_games": 0, "phase2_seeds": 0,
        }
        self.env = dict(os.environ, GIT_OPTIONAL_LOCKS="0", GIT_NO_LAZY_FETCH="1")

    def git(self, *arguments):
        result = subprocess.run(["git", "--no-replace-objects", "-C", str(self.args.repo), *arguments],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=self.env, timeout=120)
        need(result.returncode == 0, "read-only git operation failed (exit %d)" % result.returncode)
        return result.stdout

    def blob(self, name):
        safe_name(name)
        return self.git("show", "--no-ext-diff", "--no-textconv",
                        self.args.receipt_commit + ":" + self.args.directory + "/" + name)

    def stage(self, name, action):
        try:
            result = action()
            self.report["checks"][name] = {"state": "PASS"}
            return result
        except Exception as exc:
            self.report["checks"][name] = {"state": "FAIL", "error": str(exc)}
            return None

    def index(self):
        data = self.blob("ARCHIVE_PARTS.json")
        value = json_value(data)
        need(isinstance(value, dict) and set(value) == {"bytes", "sha256", "parts"}, "part index fields")
        need(type(value["bytes"]) is int and value["bytes"] > 0 and digest_string(value["sha256"]), "part index size/hash")
        need(isinstance(value["parts"], list) and len(value["parts"]) == (value["bytes"] + CHUNK - 1) // CHUNK,
             "part count")
        for number, item in enumerate(value["parts"]):
            need(isinstance(item, dict) and set(item) == {"file", "bytes", "sha256"}, "part fields")
            need(item["file"] == f"original.part{number:04d}.b64", "canonical ordered part name")
            exact_int(item["bytes"], min(CHUNK, value["bytes"] - number * CHUNK), "part size")
            need(digest_string(item["sha256"]), "part SHA256")
        self.report["parts_index_sha256"] = sha(data)
        return value

    def assemble(self, index, archive):
        combined = hashlib.sha256()
        total = 0
        part_results = []
        with archive.open("xb") as output:
            for item in index["parts"]:
                encoded = self.blob(item["file"])
                need(encoded.endswith(b"\n"), "canonical base64 requires one terminal LF")
                raw = base64.b64decode(encoded[:-1], validate=True)
                need(base64.b64encode(raw) + b"\n" == encoded, "noncanonical base64: " + item["file"])
                exact_int(len(raw), item["bytes"], "decoded part length")
                need(sha(raw) == item["sha256"], "decoded part SHA256: " + item["file"])
                output.write(raw)
                combined.update(raw)
                total += len(raw)
                part_results.append(dict(item, encoded_sha256=sha(encoded), encoded_bytes=len(encoded)))
        need(total == index["bytes"] and combined.hexdigest() == index["sha256"], "assembled original ZIP size/hash")
        self.report["archive"] = {"bytes": total, "sha256": combined.hexdigest(), "repacked": False}
        self.report["parts"] = part_results
        return True

    def zip_structure(self, archive):
        hashes = {}
        total = 0
        with zipfile.ZipFile(archive) as z:
            names = [safe_name(i.filename) for i in z.infolist()]
            need(len(names) == len(set(names)), "duplicate ZIP member")
            name_set = set(names)
            for name in names:
                components = name.split("/")
                need(not any("/".join(components[:i]) in name_set for i in range(1, len(components))),
                     "file/directory prefix collision")
            for info in z.infolist():
                mode = info.external_attr >> 16
                need(not info.is_dir() and stat.S_IFMT(mode) in (0, stat.S_IFREG), "nonregular ZIP member")
                need(not info.flag_bits & 1, "encrypted ZIP member")
                digest = hashlib.sha256()
                count = 0
                with z.open(info) as member:
                    for block in iter(lambda: member.read(1024 * 1024), b""):
                        digest.update(block)
                        count += len(block)
                need(count == info.file_size, "ZIP member size mismatch")
                hashes[info.filename] = digest.hexdigest()
                total += count
        self.report["zip"] = {"members": len(hashes), "uncompressed_bytes": total, "crc": "PASS",
                              "safe_unique_regular_members": True}
        return hashes

    @staticmethod
    def member(archive, name):
        with zipfile.ZipFile(archive) as z:
            return z.read(name)

    def manifest(self, archive, hashes, prefix):
        name = prefix + "MANIFEST.json"
        manifest = manifest_value(self.member(archive, name))
        actual = {key[len(prefix):]: value for key, value in hashes.items()
                  if key.startswith(prefix) and key != name}
        need(manifest == actual, name + " exact member set/hashes")
        self.report["outer_manifest" if not prefix else "nested_manifest"] = {
            "sha256": hashes[name], "members": len(manifest), "exact": True}
        return True

    def receipt(self):
        data = self.blob("LOCAL_RECEIPT.json")
        value = json_value(data)
        need(isinstance(value, dict), "receipt must be an object")
        self.report["local_receipt_sha256"] = sha(data)
        self.report["qualification_receipt_state"] = value.get("state")
        return value

    def receipt_identity(self, value, index):
        expected = {"schema": "manual-transmission/clean-room-local-receipt/v1",
                    "source": SOURCE, "source_tree": TREE, "source_basis": SOURCE_BASIS, "launch_commit": LAUNCH,
                    "recovery_id": EPOCH, "attempt_id": EPOCH + "/local-01",
                    "archive_origin": "FIRST_TERMINAL_LOCAL_EVIDENCE_COLLECTION_NOT_CI_ARTIFACT",
                    "archive_name": "original.zip", "archive_sha256": index["sha256"]}
        for key, wanted in expected.items():
            need(value.get(key) == wanted, "receipt identity: " + key)
        exact_int(value.get("archive_bytes"), index["bytes"], "receipt archive bytes")
        need(value.get("state") in (PASS, FAIL), "unrecognized qualification receipt state")
        need(value.get("admitted") is False and value.get("requires_immutable_and_live_publication_readback") is True,
             "receipt admission/readback boundary")
        for key in ("phase2_games", "phase2_seeds", "ci_activations", "original_artifact_downloads", "new_qualification_invocations"):
            exact_int(value.get(key), 0, "receipt prohibited action count: " + key)
        need(isinstance(value.get("errors"), list), "receipt errors field")
        return True

    def receipt_claim(self, value, archive, index, hashes):
        if value["state"] == FAIL:
            need(value.get("all_frozen_local_gates_pass") is False, "failed receipt must not claim local pass")
            return True
        need(value.get("all_frozen_local_gates_pass") is True and value["errors"] == [], "local pass/errors")
        exact_int(value.get("source_bindings_verified"), 378, "receipt binding count")
        exact_int(value.get("qualifier_returncode"), 0, "qualifier return code")
        exact_int(value.get("original_qualification_invocations"), 1, "original qualification invocation count")
        need(value.get("host_properties_restored") is True, "host restoration receipt")
        need(value.get("preserver_sha256") == PRESERVER_SHA, "reviewed preserver identity")
        checks = value.get("checks")
        needed = {"immutable authority " + name for name in (
            "CHARTER.json", "LAUNCH_CURRENT.json", "SOURCE_FREEZE.json", "run_local_qualification.py",
            "CANDIDATE_PINS.json", "audit_reports.py", "qualify.py")}
        needed |= {"immutable source and launch authority", "original terminal and invocation", "original inner manifest",
                   "terminal source and copied original bindings", "host restoration evidence",
                   "immutable audit dependency staging",
                   "independent frozen raw-report audit", "first local archive CRC and member readback",
                   "original archive part readback"}
        need(isinstance(checks, dict) and needed <= set(checks), "required receipt checks")
        need(all(isinstance(v, dict) and v.get("state") == "PASS" for v in checks.values()), "receipt check failure")
        audit_counts(value.get("raw_audit"))
        for key, wanted in (("archive_validation", {"crc": "PASS", "member_manifest": "PASS", "members": len(hashes),
                                                   "bytes": index["bytes"], "sha256": index["sha256"]}),
                            ("parts_readback", {"state": "PASS", "parts": len(index["parts"]),
                                                "bytes": index["bytes"], "sha256": index["sha256"]})):
            observed = value.get(key)
            need(isinstance(observed, dict) and set(observed) == set(wanted), "receipt archive proof fields: " + key)
            for field, expected in wanted.items():
                if type(expected) is int:
                    exact_int(observed[field], expected, "receipt archive proof count: " + field)
                else:
                    need(observed[field] == expected, "receipt archive proof: " + field)
        def obj(name):
            result = json_value(self.member(archive, name))
            need(isinstance(result, dict), "archived JSON object: " + name)
            return result
        need(hashes.get("authority/CHARTER.json") == CHARTER_SHA and
             hashes.get("authority/CANDIDATE_PINS.json") == PINS_SHA and
             hashes.get("qualification/CANDIDATE_PINS.json") == PINS_SHA and
             hashes.get("authority/audit_reports.py") == AUDIT_SHA and
             hashes.get("authority/preserve_local.py") == PRESERVER_SHA, "archived authority hashes")
        result = obj("qualification/RESULT.json")
        need(result.get("state") == PASS and result.get("admitted") is False, "archived qualifier result")
        first = obj("qualification/COMPILE_FIRST.json")
        exact_int(first.get("forms"), 15, "archived compile-first forms")
        need(first.get("all_compiled") is True, "archived compile-first result")
        independent = obj("review/INDEPENDENT_AUDIT.json")
        need(independent.get("state") == "PASS" and independent.get("auditor_sha256") == AUDIT_SHA and
             independent.get("result") == value["raw_audit"], "archived independent audit agrees")
        inner_proof = independent.get("original_inner_manifest")
        need(isinstance(inner_proof, dict) and inner_proof.get("manifest_sha256") == hashes["qualification/MANIFEST.json"],
             "independent audit inner-manifest identity")
        exact_int(inner_proof.get("files"), sum(name.startswith("qualification/") for name in hashes) - 1,
                  "independent audit inner-manifest member count")
        preservation = obj("review/PRESERVATION_CHECKS.json")
        need(preservation.get("errors") == [] and preservation.get("copied_originals_unchanged") is True and
             preservation.get("admitted") is False, "archived preservation errors/boundary")
        exact_int(preservation.get("qualification_invocations_started_by_this_script"), 0, "archived preservation invocation count")
        exact_int(preservation.get("ci_downloads"), 0, "archived preservation CI downloads")
        prior_checks = preservation.get("checks")
        need(isinstance(prior_checks, dict) and prior_checks and
             all(isinstance(check, dict) and check.get("state") == "PASS" for check in prior_checks.values()),
             "archived pre-archive preservation checks")
        terminal = obj("runtime/LOCAL_TERMINAL.json")
        need(terminal.get("source") == SOURCE and terminal.get("launch_commit") == LAUNCH and
             terminal.get("state") == "QUALIFICATION_FINISHED", "archived terminal identity")
        for key, number in (("returncode", 0), ("qualification_invocations", 1), ("source_bindings_verified", 378),
                            ("phase2_games", 0), ("phase2_seeds", 0), ("ci_activations", 0), ("original_artifact_downloads", 0)):
            exact_int(terminal.get(key), number, "archived terminal count: " + key)
        need(terminal.get("all_bindings_match") is True and terminal.get("failed_bindings") == [] and
             terminal.get("tracked_index_clean") is True and terminal.get("host_properties_restored") is True and
             terminal.get("admitted") is False, "archived terminal restoration/boundary")
        rebound = obj("review/SOURCE_REBINDING.json")
        need(rebound.get("source") == rebound.get("head") == SOURCE and rebound.get("failed_bindings") == [] and
             rebound.get("tracked_index_clean") is True, "archived rebinding report")
        exact_int(rebound.get("source_bindings_verified"), 378, "archived rebinding count")
        exact_int(rebound.get("expected_bindings"), 378, "archived expected bindings")
        need(obj("review/HOST_RESTORATION.json") == value.get("host_restoration_evidence_basis"), "host evidence agrees")
        freeze = obj("authority/SOURCE_FREEZE.json")["qualification"]
        need(freeze["source"] == SOURCE and freeze["source_tree"] == TREE and freeze["source_basis"] == SOURCE_BASIS, "archived source freeze")
        exact_int(freeze["binding_count"], 378, "archived frozen binding count")
        event = obj("runtime/RECOVERY_EVENT.json")
        need(event == obj("qualification/RECOVERY_EVENT.json"), "original runtime/qualifier event agreement")
        need("clean_room" not in event and event.get("source") == SOURCE and event.get("first_parent") == LAUNCH
             and event.get("qualification") == freeze and event.get("local_execution") is True,
             "lint followup local envelope source and scope")
        need(all(key in event and event[key] is None for key in ("event", "validation", "run")),
             "local envelope must not claim CI identity")
        exact_int(event.get("attempt"), 1, "local envelope attempt")
        followup = event.get("lint_followup")
        need(isinstance(followup, dict), "explicit lint followup authority required")
        exact_int(followup.get("attempt"), 1, "lint followup attempt")
        need(followup == dict(recovery_id=EPOCH, branch=BRANCH.removeprefix("refs/heads/"),
                             charter_commit=CHARTER_COMMIT, charter_blob=CHARTER_BLOB,
                             lane="local", attempt=1, attempt_id=EPOCH + "/local-01"),
             "exact lint followup envelope authority")
        return True

    def audit_original(self, archive, hashes, receipt):
        """Audit only copied raw data, using the complete immutable S2 input tree."""
        need(self.git("rev-parse", SOURCE + "^{tree}").decode().strip() == TREE,
             "immutable audit source tree")
        expected = {"schema":"manual-transmission/immutable-audit-source/v1",
                    "source":SOURCE,"source_tree":TREE,"root":AUDIT_SOURCE_ROOT,
                    "inputs":AUDIT_INPUTS,"bound_input_count":5,"original_binding_count":378,
                    "additional_source_bindings":0,"original_qualification_modified":False}
        staged = json_value(self.member(archive, "review/AUDIT_SOURCE.json"))
        need(staged == expected, "archived immutable audit dependency proof")
        independent = json_value(self.member(archive, "review/INDEPENDENT_AUDIT.json"))
        need(independent.get("audit_source") == staged and
             independent.get("audit_source_manifest_sha256") == hashes["review/AUDIT_SOURCE.json"],
             "independent audit dependency manifest identity")
        archived_inputs = {name.removeprefix(AUDIT_SOURCE_ROOT + "/") for name in hashes
                           if name.startswith(AUDIT_SOURCE_ROOT + "/")}
        need(archived_inputs == set(AUDIT_INPUTS), "exact archived audit input set")
        pins = json_value(self.member(archive, "qualification/CANDIDATE_PINS.json"))
        need(len(pins["bindings"]) == 378 and
             sum(path in pins["bindings"] for path in AUDIT_INPUTS) == 5,
             "original 378 binding inventory retained")
        frozen_prefix = "qualification/frozen-source/"
        frozen_members = {name.removeprefix(frozen_prefix) for name in hashes
                          if name.startswith(frozen_prefix)}
        need(frozen_members == set(pins["bindings"]), "exact 378 archived frozen-source member set")
        with zipfile.ZipFile(archive) as z:
            for path, pin in pins["bindings"].items():
                safe_name(path)
                member = frozen_prefix + path
                need(hashes[member] == pin["sha256"], "archived frozen-source SHA256 " + path)
                exact_int(z.getinfo(member).file_size, pin["bytes"], "archived frozen-source byte length " + path)
        self.report["source_bindings_reexecuted"] = True
        self.report["archived_source_bindings_verified"] = 378
        self.report["archived_source_member_set_exact"] = True
        audit_root = archive.parent / "immutable-audit-source"
        evidence = archive.parent / "qualification-for-audit"
        need(not audit_root.exists() and not evidence.exists(), "fresh readback audit staging")
        for path, pin in AUDIT_INPUTS.items():
            need(self.git("rev-parse", SOURCE + ":" + path).decode().strip() == pin["blob"],
                 "immutable audit dependency blob " + path)
            data = self.git("show", "--no-ext-diff", "--no-textconv", SOURCE + ":" + path)
            need(len(data) == pin["bytes"] and sha(data) == pin["sha256"],
                 "immutable audit dependency bytes " + path)
            if path in pins["bindings"]:
                need(pins["bindings"][path] == {key:pin[key] for key in ("bytes","sha256")},
                     "bound audit input agrees with source pins " + path)
            archived_name = AUDIT_SOURCE_ROOT + "/" + path
            need(hashes[archived_name] == pin["sha256"] and self.member(archive, archived_name) == data,
                 "archived audit input differs from immutable source " + path)
            destination = audit_root / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(data)
        # All names, regular-file modes, CRCs and both manifests were checked first.
        with zipfile.ZipFile(archive) as z:
            for name in sorted(key for key in hashes if key.startswith("qualification/")):
                relative = safe_name(name.removeprefix("qualification/"))
                data = z.read(name)
                need(sha(data) == hashes[name], "original qualification member changed " + name)
                destination = evidence / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(data)
        module_path = audit_root / RECORD / "audit_reports.py"
        code = module_path.read_bytes()
        need(sha(code) == AUDIT_SHA, "readback auditor identity")
        module = types.ModuleType("_immutable_local_readback_auditor")
        module.__file__ = str(module_path)
        self.report["raw_auditor_reexecuted"] = True
        exec(compile(code, str(module_path), "exec"), module.__dict__)
        result = module.audit_all(evidence)
        audit_counts(result)
        archived_result = json_value(self.member(archive, "qualification/AUDIT.json"))
        need(result == receipt["raw_audit"] == independent["result"] == archived_result,
             "reexecuted immutable raw audit agrees with every original result")
        inner = manifest_value(self.member(archive, "qualification/MANIFEST.json"))
        actual = {p.relative_to(evidence).as_posix():sha(p.read_bytes())
                  for p in evidence.rglob("*") if p.is_file() and p != evidence / "MANIFEST.json"}
        need(actual == inner, "raw qualification unchanged after readback audit")
        for path, pin in AUDIT_INPUTS.items():
            data = (audit_root / path).read_bytes()
            need(len(data) == pin["bytes"] and sha(data) == pin["sha256"],
                 "immutable audit input unchanged after execution " + path)
        self.report["independent_raw_audit"] = {
            "state":"PASS", "source":SOURCE, "auditor_sha256":AUDIT_SHA, "inputs":AUDIT_INPUTS,
            "result":result, "original_qualification_manifest_unchanged":True,
            "qualification_invocations":0, "build_or_test_invocations":0}
        return True

    def live_head(self):
        data = self.git("ls-remote", "--exit-code", "--refs", self.args.remote, BRANCH).decode("ascii")
        lines = [line.split("\t") for line in data.splitlines() if line]
        need(len(lines) == 1 and len(lines[0]) == 2 and lines[0][1] == BRANCH, "exact live branch response")
        observed = lines[0][0]
        self.report["observed_live_head"] = observed
        self.report["live_readback_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        need(observed == self.args.expected_live_head, "live branch head differs from caller's expected head")
        return True

    def run(self, archive):
        committed = self.stage("immutable_commit", lambda: need(self.git("cat-file", "-t", self.args.receipt_commit).strip() == b"commit",
                                                                "receipt object must be a commit") or True)
        index = self.stage("parts_index", self.index) if committed else None
        receipt = self.stage("receipt_json", self.receipt) if committed else None
        assembled = self.stage("original_bytes", lambda: self.assemble(index, archive)) if index else None
        hashes = self.stage("zip_structure_and_crc", lambda: self.zip_structure(archive)) if assembled else None
        outer = self.stage("outer_manifest", lambda: self.manifest(archive, hashes, "")) if hashes else None
        inner = self.stage("inner_manifest", lambda: self.manifest(archive, hashes, "qualification/")) if hashes else None
        identity = self.stage("receipt_identity_and_archive", lambda: self.receipt_identity(receipt, index)) if receipt and index else None
        claim = self.stage("qualification_receipt_claim", lambda: self.receipt_claim(receipt, archive, index, hashes)) if identity and hashes else None
        # Reexecution is required for a successful local claim; failed archives remain preservable.
        raw_audit = True
        if receipt and receipt.get("state") == PASS:
            raw_audit = self.stage("independent_frozen_raw_audit", lambda: self.audit_original(archive, hashes, receipt)) if all((outer, inner, identity, claim)) else None
        # Deliberately last: separate live publication proof from immutable content.
        live = self.stage("live_branch_readback", self.live_head)
        complete = all((committed, index is not None, receipt is not None, assembled, hashes is not None,
                        outer, inner, identity, claim, raw_audit, live))
        local_pass = bool(complete and receipt["state"] == PASS)
        self.report.update({
            "exact_original_archive_bytes_verified": bool(assembled),
            "zip_members_and_crc_verified": hashes is not None,
            "outer_manifest_verified": bool(outer), "nested_manifest_verified": bool(inner),
            "receipt_identity_verified": bool(identity), "receipt_claim_verified": bool(claim),
            "live_branch_verified": bool(live), "complete_readback": bool(complete),
            "published_local_qualification_pass_validated": local_pass,
            "scientific_admission": False,
            "state": ("ARCHIVE_READBACK_PASS_LOCAL_QUALIFICATION_PASS_CI_REQUIRED" if local_pass else
                      "ARCHIVE_READBACK_PASS_QUALIFICATION_FAILED" if complete else "READBACK_INCOMPLETE_OR_INVALID"),
            "finished_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        })
        return 0 if complete else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--receipt-commit", required=True)
    parser.add_argument("--directory", required=True)
    parser.add_argument("--expected-live-head", required=True)
    parser.add_argument("--remote", default="origin")
    parser.add_argument("--output-json", type=Path, required=True)
    args = parser.parse_args()
    for value in (args.receipt_commit, args.expected_live_head):
        need(re.fullmatch(r"[0-9a-f]{40}", value) is not None, "full lowercase Git object ID required")
    safe_name(args.directory)
    need(args.remote and not args.remote.startswith("-"), "invalid remote")
    args.repo = args.repo.resolve()
    output = args.output_json.resolve()
    need(args.repo.is_dir(), "repository directory absent")
    need(not output.is_relative_to(args.repo), "readback output must be outside the repository")
    need(not output.exists(), "refusing to overwrite an existing readback report")
    output.parent.mkdir(parents=True, exist_ok=True)
    reader = Reader(args)
    with tempfile.TemporaryDirectory(prefix="manual-immutable-readback-", dir=output.parent) as temporary:
        exit_code = reader.run(Path(temporary) / "original.zip")
    with output.open("x", encoding="utf-8") as report_file:
        json.dump(reader.report, report_file, indent=2, sort_keys=True)
        report_file.write("\n")
    print(json.dumps({key: reader.report[key] for key in (
        "state", "complete_readback", "published_local_qualification_pass_validated",
        "exact_original_archive_bytes_verified", "nested_manifest_verified", "live_branch_verified")}))
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())

