"""Dormant exact R1 invocation: prepare -> admit -> complete -> finalize.

Every command requires accepted exact runtime and a live create-only repository claim. Allocation
declarations are durable before initialization. No invalid or incomplete attempt can be retried.
The only numeric comparison is emitted after all 512 artifacts and their replay pass.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import os
import urllib.request
import urllib.error
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import quote, unquote, urlsplit, parse_qs

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "tools"))
from evidence_durability import create_json_once, create_journal, append_journal, verify_journal
from repository_claim import ClaimSpec, verify_claim, reserve_claim, ApiError
from r1_allocation_plan import compile_plan, _filtered_ordering, _parse_main_deck, _copy_labels
from r1_execution_guard import validate_preinitialization_guard, _binding, RUNTIME_STATUS, AUTHORIZATION_STATUS
from r1_artifact_contract import validate_record, validate_complete_corpus, SCHEMA
from r1_metric_projection import project_metrics
from r1_decision_rule import evaluate_complete_metrics
from r1_runtime_binding_audit import EXPECTED_BLOBS, _git_blob_sha

PROTOCOL_ID = "IW_V2_R1_ENGINE_STRUCTURAL_2026_09_25"
CLAIM_REF = "refs/heads/industrial-waste/official-attempts/v2-r1-attempt-1"
CLAIM_PATH = "industrial-waste/v2/official/execution-claim.json"
CONSUMPTION_REF = "refs/heads/industrial-waste/official-attempts/v2-r1-attempt-1-consumed"
CONSUMPTION_PATH = "industrial-waste/v2/official/execution-consumption.json"
OUTPUT_RELATIVE = "build/official/industrial-waste-v2-r1-attempt-1"
GIT_REMOTE = "https://github.com/GodaPupa/argentum-batshit-test.git"
CLARIFICATION_PATH = "industrial-waste/v2/r1-checkpoint-clarification-acceptance.json"
CLARIFICATION_SHA256 = "34511344bbdac128964a612ac36cc839c43a431015e3a6d5375aa62b5f25c6cd"
CLARIFICATION_PROPOSAL_PATH = "industrial-waste/v2/r1-checkpoint-clarification-proposal.json"
CLARIFICATION_PROPOSAL_SHA256 = "d1200c65d5b4573ff66a92e3159f8c6bdcaaf5887c182c83c144db22156b60c5"
ORIGINAL_PROTOCOL_SHA256 = "fd697b6d56b5a5451f4dc6f9ee4bd20584a705570c6f99861dd64f0086ed95bc"
FROZEN_INPUTS = ["industrial-waste/control/industrial-waste-v1.0-submitted.dck",
    "industrial-waste/v2/candidates/compact-loop.dck", "industrial-waste/v2/candidates/recursive-eggs.dck",
    "industrial-waste/v2/candidates/lean-tron-hybrid.dck", "industrial-waste/v2/r0-freeze.json",
    "industrial-waste/v2/protocol-v2-r0.json", "industrial-waste/v2/protocol-v2-r1.json",
    "industrial-waste/v2/r1-ordering-corpus.json"]

def read(path):
    value = json.loads(Path(path).read_text())
    if not isinstance(value, dict):
        raise ValueError(f"object required: {path}")
    return value

def same_json(left, right):
    return json.dumps(left, sort_keys=True, allow_nan=False) == json.dumps(right, sort_keys=True, allow_nan=False)

def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def git(root, *args):
    return subprocess.check_output(["git", *args], cwd=root, text=True).strip()

def repo_path(root, value):
    path = root / value
    if Path(value).is_absolute() or not path.resolve().is_relative_to(root.resolve()):
        raise ValueError("repository-relative bound path required")
    return path

class GitHubExecutionAPI:
    """Authenticated repository-only client; every write is attempted exactly once, never retried."""
    repository = "GodaPupa/argentum-batshit-test"
    def __init__(self):
        self.token = os.environ.get("GITHUB_TOKEN")
        if not self.token:
            raise ValueError("official execution requires its GitHub Actions repository token")
    def request(self, method, path, payload=None):
        request = urllib.request.Request("https://api.github.com/repos/" + self.repository + path,
            data=None if payload is None else json.dumps(payload).encode(), method=method,
            headers={"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as failure:
            raise ApiError(failure.code) from None


class GitImmutableReadAPI:
    """Exact immutable Git reads within one freshly checked live-ref transaction.

    Prepare proves remote publication with one authenticated remote commit read per object.
    Subsequent phases read those content-addressed objects and freshly compare all three live
    refs before and after validation. No live ref, absence or write result survives a phase.
    """
    repository = "GodaPupa/argentum-batshit-test"
    def __init__(self, root, source_branch, *, require_publication):
        self.root = root
        self.refs = ("refs/heads/" + source_branch, CLAIM_REF, CONSUMPTION_REF)
        self.require_publication = require_publication
        self.publication_api = GitHubExecutionAPI() if require_publication else None
        self.verified_objects = set()
        self.snapshot = self.live_refs()

    def live_refs(self):
        # get-url expands insteadOf rules too; an unrelated mirror, local repo or rewritten
        # transport is not the live authoritative repository merely because it has copied SHAs.
        if git(self.root, "remote", "get-url", "origin") != GIT_REMOTE:
            raise ValueError("Git authority reads require the exact canonical repository endpoint")
        rows = git(self.root, "ls-remote", GIT_REMOTE, *self.refs).splitlines()
        found = {}
        for row in rows:
            value, ref = row.split()
            if ref not in self.refs or ref in found or re.fullmatch(r"[0-9a-f]{40}", value) is None:
                raise ValueError("ambiguous live authority reference")
            found[ref] = value
        return found

    def assert_live_unchanged(self):
        if self.live_refs() != self.snapshot:
            raise ValueError("live source, claim or consumption moved during this execution phase")

    def object(self, commit):
        if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
            raise ValueError("full immutable commit required")
        if commit not in self.verified_objects:
            if self.require_publication:
                # Fetch alone may short-circuit for a local-only object. Verify remote existence
                # and exact identity through the repository API once before immutable reuse.
                remote = self.publication_api.request("GET", "/git/commits/" + commit)
                if remote.get("sha") != commit:
                    raise ValueError("immutable authority commit is not published at its exact identity")
                if subprocess.run(["git", "cat-file", "-e", commit + "^{commit}"], cwd=self.root,
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0:
                    subprocess.run(["git", "fetch", "--quiet", "--no-tags", GIT_REMOTE, commit],
                        cwd=self.root, check=True, stdout=subprocess.DEVNULL)
                local_tree = git(self.root, "show", "-s", "--format=%T", commit)
                local_parents = git(self.root, "show", "-s", "--format=%P", commit).split()
                if (remote.get("tree", {}).get("sha") != local_tree
                        or [p.get("sha") for p in remote.get("parents", [])] != local_parents):
                    raise ValueError("local immutable authority differs from its published Git object")
            git(self.root, "cat-file", "-e", commit + "^{commit}")
            self.verified_objects.add(commit)

    def request(self, method, path, payload=None):
        if method != "GET" or payload is not None:
            raise ValueError("immutable read client never creates or updates authority")
        if path.startswith("/git/ref/"):
            ref = "refs/" + unquote(path.removeprefix("/git/ref/"))
            if ref not in self.refs:
                raise ValueError("reference outside this exact execution transaction")
            if ref not in self.snapshot:
                raise ApiError(404)
            return {"ref": ref, "object": {"sha": self.snapshot[ref]}}
        if path.startswith("/git/commits/"):
            commit = path.removeprefix("/git/commits/")
            self.object(commit)
            lines = git(self.root, "show", "-s", "--format=%H%n%T%n%P", commit).splitlines()
            return {"sha": lines[0], "tree": {"sha": lines[1]},
                "parents": [{"sha": p} for p in (lines[2].split() if len(lines) > 2 else [])]}
        if path.startswith("/contents/"):
            parsed = urlsplit(path)
            raw = unquote(parsed.path.removeprefix("/contents/"))
            repo_path(self.root, raw)
            commit = parse_qs(parsed.query)["ref"][0]
            self.object(commit)
            data = subprocess.check_output(["git", "show", f"{commit}:{raw}"], cwd=self.root)
            blob = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
            return {"path": raw, "sha": blob, "encoding": "base64",
                "content": base64.b64encode(data).decode()}
        raise ValueError("unsupported immutable evidence endpoint")


def execution_owner(output):
    names = ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_JOB", "RUNNER_NAME")
    owner = {name: os.environ.get(name) for name in names}
    if any(not isinstance(value, str) or not value.strip() for value in owner.values()):
        raise ValueError("one immutable GitHub run/attempt/job/runner owner is required")
    if not owner["GITHUB_RUN_ID"].isdigit() or not owner["GITHUB_RUN_ATTEMPT"].isdigit():
        raise ValueError("invalid GitHub execution identity")
    return {**owner, "output_directory": str(output.resolve())}


def consumption_spec(authority):
    receipt = authority["remote_receipt"]
    return ClaimSpec(receipt["repository"], receipt["source_branch"], authority["source"],
        CONSUMPTION_REF, CONSUMPTION_PATH)


def validate_consumption(root, output, authority):
    if output.resolve() != (root / OUTPUT_RELATIVE).resolve():
        raise ValueError("execution requires the prospectively fixed project output location")
    payload = read(output / "execution-consumption.json")
    expected = {"protocol_id": PROTOCOL_ID, "original_claim_commit": authority["remote_receipt"]["claim_commit_sha"],
        "source_commit": authority["source"], "execution_owner": execution_owner(output)}
    if not same_json(payload, expected):
        raise ValueError("canonical consumption does not belong to this exact executor and output")
    verify_claim(authority["read_api"], consumption_spec(authority), payload,
        read(output / "execution-consumption-receipt.json"))


def verify_published_binding(root, binding, api=None):
    """Local authority bytes must equal one exact publicly recorded repository object."""
    commit = binding.get("repository_commit")
    if not isinstance(commit, str) or re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("authority binding requires an immutable repository commit")
    path = repo_path(root, binding["path"])
    if sha(path) != binding.get("sha256"):
        raise ValueError("local authority digest differs from its declared binding")
    remote = (api or GitHubExecutionAPI()).request("GET", "/contents/" + quote(binding["path"], safe="/") + "?ref=" + commit)
    if remote.get("encoding") != "base64" or remote.get("path") != binding["path"]:
        raise ValueError("remote immutable authority file is not the declared object")
    data = base64.b64decode("".join(remote["content"].split()), validate=True)
    if data != path.read_bytes() or hashlib.sha256(data).hexdigest() != binding["sha256"]:
        raise ValueError("authority bytes differ from the immutable published object")


def verify_checkpoint_clarification(root, runtime, api):
    """The accepted prospective interpretation is a pinned prerequisite, never execution authority."""
    binding = runtime.get("checkpoint_clarification_acceptance")
    if (not isinstance(binding, dict) or binding.get("path") != CLARIFICATION_PATH
            or binding.get("sha256") != CLARIFICATION_SHA256):
        raise ValueError("runtime must bind the exact independently reviewed checkpoint clarification acceptance")
    verify_published_binding(root, binding, api)
    acceptance = read(root / CLARIFICATION_PATH)
    if (acceptance.get("status") != "ACCEPTED_PROSPECTIVE_CHECKPOINT_CLARIFICATION_ONLY"
            or acceptance.get("protocol_id") != PROTOCOL_ID):
        raise ValueError("checkpoint clarification is not the accepted scoped interpretation")
    proposal = acceptance["proposal"]
    if (proposal["path"] != CLARIFICATION_PROPOSAL_PATH
            or proposal["sha256"] != CLARIFICATION_PROPOSAL_SHA256):
        raise ValueError("accepted checkpoint proposal drift")
    verify_published_binding(root, proposal, api)
    if sha(root / "industrial-waste/v2/protocol-v2-r1.json") != ORIGINAL_PROTOCOL_SHA256:
        raise ValueError("checkpoint clarification cannot modify the frozen original protocol")


def validate_authority(root, session, *, require_publication=True):
    if session.get("protocol_id") != PROTOCOL_ID:
        raise ValueError("session protocol drift")
    claim = read(repo_path(root, session["claim_path"]))
    receipt = read(repo_path(root, session["remote_claim_receipt_path"]))
    if receipt.get("claim_ref") != CLAIM_REF or receipt.get("claim_path") != CLAIM_PATH:
        raise ValueError("only the single frozen R1 claim namespace is admitted")
    if session["claim_path"] != CLAIM_PATH:
        raise ValueError("claim file location differs from the canonical protocol path")
    source = receipt["source_sha"]
    if git(root, "rev-parse", "HEAD") != source:
        raise ValueError("checkout is not the remotely claimed source")
    spec = ClaimSpec(**{key: receipt[key] for key in (
        "repository", "source_branch", "source_sha", "claim_ref", "claim_path")})
    api = GitImmutableReadAPI(root, receipt["source_branch"], require_publication=require_publication)
    verify_claim(api, spec, claim, receipt)
    verify_published_binding(root, claim["runtime_binding"], api)
    verify_published_binding(root, claim["execution_authorization"], api)
    runtime_ref = _binding(root, claim, "runtime_binding", RUNTIME_STATUS)
    authorization_ref = _binding(root, claim, "execution_authorization", AUTHORIZATION_STATUS)
    runtime = read(root / runtime_ref["path"])
    authorization = read(root / authorization_ref["path"])
    verify_checkpoint_clarification(root, runtime, api)
    qualified_source = runtime.get("source_commit")
    if not isinstance(qualified_source, str) or re.fullmatch(r"[0-9a-f]{40}", qualified_source) is None:
        raise ValueError("qualified runtime source is missing")
    subprocess.run(["git", "merge-base", "--is-ancestor", qualified_source, source], cwd=root, check=True)
    if claim.get("expected_branch_head") != source:
        raise ValueError("claim branch-head binding differs")
    if authorization.get("expected_branch_head") != source:
        raise ValueError("authorization does not bind the exact expected branch HEAD")
    if authorization.get("runtime_binding_sha256") != runtime_ref["sha256"]:
        raise ValueError("authorization does not bind this runtime")
    required = {
        "industrial-waste/v2/r1_execute.py", "industrial-waste/v2/r1_metric_projection.py",
        "industrial-waste/v2/r1_artifact_contract.py", "industrial-waste/v2/r1_decision_rule.py",
        "industrial-waste/v2/r1_allocation_plan.py", "industrial-waste/v2/r1_execution_guard.py",
        "industrial-waste/v2/r1-runtime-composition-contract.json",
        CLARIFICATION_PATH, CLARIFICATION_PROPOSAL_PATH,
        "tools/evidence_durability.py", "tools/repository_claim.py",
        "industrial-waste/v2/r1_runtime_binding_audit.py", *FROZEN_INPUTS,
        *["ai/src/test/kotlin/com/wingedsheep/ai/industrialwaste/" + name + ".kt" for name in (
            "IndustrialWasteV2AllocationRunner", "IndustrialWasteV2FullHorizonRunner",
            "IndustrialWasteV2CheckpointMana", "IndustrialWasteV2R1OfficialExecutionTest",
            "IndustrialWasteV2PaymentIntent", "IndustrialWasteV2PublicActionPolicy",
            "IndustrialWasteV2QuietCheckpointReplayTest")],
    }
    pins = runtime.get("source_files_sha256")
    if not isinstance(pins, dict) or not required.issubset(pins):
        raise ValueError("accepted runtime omits execution source")
    for raw, expected in pins.items():
        if sha(repo_path(root, raw)) != expected:
            raise ValueError(f"runtime source drift: {raw}")
        qualified_bytes = subprocess.check_output(["git", "show", f"{qualified_source}:{raw}"], cwd=root)
        if hashlib.sha256(qualified_bytes).hexdigest() != expected:
            raise ValueError(f"runtime pin was not qualified at its declared source: {raw}")
    # Receipt-only successor heads are allowed only when every executable receiving-project
    # source remains byte-identical to the independently qualified commit.
    for raw in FROZEN_INPUTS:
        if _git_blob_sha(root / raw) != EXPECTED_BLOBS[raw]:
            raise ValueError("immutable frozen experiment input drift: " + raw)
    source_directories = [*required, "ai", "rules-engine", "mtg-sdk", "mtg-sets", "gradle", "buildSrc",
        "build-logic", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "justfile"]
    subprocess.run(["git", "diff", "--quiet", qualified_source, "--", *source_directories], cwd=root, check=True)
    for untracked in git(root, "ls-files", "--others", "--exclude-standard", "--", *source_directories).splitlines():
        if untracked.endswith((".kt", ".json", ".kts", ".py", ".properties", ".toml")):
            raise ValueError("unqualified executable or compiled-card source: " + untracked)
    qualification_ref = runtime.get("qualification_receipt")
    if not isinstance(qualification_ref, dict):
        raise ValueError("complete receiving-project qualification receipt is required")
    verify_published_binding(root, qualification_ref, api)
    qualification_path = repo_path(root, qualification_ref["path"])
    if sha(qualification_path) != qualification_ref.get("sha256"):
        raise ValueError("receiving-project qualification receipt digest drift")
    qualification = read(qualification_path)
    if (qualification.get("source_commit") != qualified_source
            or qualification.get("status") != "ACCEPTED_COMPLETE_R1_RUNTIME_COMPOSITION"
            or qualification.get("combined_source_qualification") != "PASS"
            or qualification.get("independent_review") != "ACCEPTED"):
        raise ValueError("receiving-project combined-source qualification and independent review are incomplete")
    if runtime.get("remaining_runtime_bindings") != []:
        raise ValueError("runtime declares missing bindings")
    api.assert_live_unchanged()
    return {"source": source, "runtime": runtime, "runtime_sha256": runtime_ref["sha256"],
        "claim": claim, "remote_receipt": receipt, "read_api": api}

def exact_request(root, allocation, corpus):
    counts = _parse_main_deck(root / allocation["deck_path"])
    row = corpus["rows"][allocation["row"] - 1]
    seat = 0 if allocation["schedule"] == "PLAY_SKIP_FIRST_DRAW" else 1
    return {"allocationId": allocation["allocation_id"],
        "deckCards": [name for name, count in counts.items() for _ in range(count)],
        "namespace": corpus["namespace"], "row": allocation["row"],
        "openingOrders": [_filtered_ordering(order, corpus["labels"], _copy_labels(counts)) for order in row["initial_orderings"]],
        "startingPlayer": seat, "initializerSeed": 9_250_925_200 + allocation["row"] * 2 + seat}

def prepare(root, session_file, output):
    session = read(session_file)
    authority = validate_authority(root, session)
    if output.resolve() != (root / OUTPUT_RELATIVE).resolve():
        raise ValueError("execution requires the prospectively fixed project output location")
    journal_binding = authority["claim"]["preinitialization_journal"]
    if session["preinitialization_journal_path"] != journal_binding["path"]:
        raise ValueError("preinitialization declaration path differs from the immutable claim binding")
    verify_published_binding(root, journal_binding, authority["read_api"])
    validate_preinitialization_guard(root, repo_path(root, session["claim_path"]),
        repo_path(root, session["preinitialization_journal_path"]), authority["source"])
    # Consume one protocol-canonical remote ref before deriving requests or making local state.
    # A crash after reservation consumes the attempt; another directory/host cannot retry it.
    owner = execution_owner(output)
    if output.exists():
        raise ValueError("execution output already exists; no retry")
    if not output.parent.is_dir():
        raise ValueError("canonical output parent must exist before remote consumption")
    authority["read_api"].assert_live_unchanged()
    consumption = {"protocol_id": PROTOCOL_ID,
        "original_claim_commit": authority["remote_receipt"]["claim_commit_sha"],
        "source_commit": authority["source"], "execution_owner": owner}
    consumption_receipt = reserve_claim(GitHubExecutionAPI(), consumption_spec(authority), consumption)
    authority["read_api"].object(consumption_receipt["claim_commit_sha"])
    plan = compile_plan(root, authority["source"])
    def durable_directory(path):
        path.mkdir()  # Existing state is consumed, never reusable.
        parent = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(parent)
        finally:
            os.close(parent)
    durable_directory(output)
    for name in ("requests", "evidence", "tails"):
        durable_directory(output / name)
    create_json_once(output / "execution-consumption.json", consumption)
    create_json_once(output / "execution-consumption-receipt.json", consumption_receipt)
    create_json_once(output / "session.json", session)
    create_json_once(output / "plan.json", plan)
    corpus = read(root / "industrial-waste/v2/r1-ordering-corpus.json")
    for allocation in plan["allocations"]:
        create_json_once(output / "requests" / (allocation["allocation_id"] + ".json"), exact_request(root, allocation, corpus))
    first = {"record_type": "ATTEMPT_DECLARED", "protocol_id": PROTOCOL_ID,
        "claim_id": authority["claim"]["claim_id"], "source_commit": authority["source"],
        "plan_sha256": sha(output / "plan.json"),
        "execution_consumption_receipt_sha256": sha(output / "execution-consumption-receipt.json"),
        "allocations_initialized": 0, "initialized": False}
    tail = create_journal(output / "attempts.jsonl", first, key="attempt:1")
    create_json_once(output / "tails/0000.json", {"tail": tail})
    return {"status": "PREPARED_NOT_INITIALIZED", "initial_tail": tail, "request_count": 512}

def bound_allocation(root, output, index):
    if type(index) is not int or index not in range(1, 513):
        raise ValueError("allocation index outside frozen scope")
    authority = validate_authority(root, read(output / "session.json"), require_publication=False)
    validate_consumption(root, output, authority)
    plan = read(output / "plan.json")
    if not same_json(plan, compile_plan(root, authority["source"])):
        raise ValueError("stored allocation plan differs from frozen source")
    return authority, plan, plan["allocations"][index - 1]


def verify_journal_payload(record, expected):
    payload = {key: value for key, value in record.items() if key not in (
        "journal_sequence", "journal_key", "previous_sha256", "record_sha256")}
    if not same_json(payload, expected):
        raise ValueError("attempt journal type, order, allocation identity or evidence binding drift")


def declaration(output, allocation, index):
    return {"record_type": "ALLOCATION_DECLARED", "allocation_id": allocation["allocation_id"],
        "allocation_index": index, "initialized": False, "stage": "BEFORE_INITIALIZATION",
        "request_sha256": sha(output / "requests" / (allocation["allocation_id"] + ".json"))}


def validate_journal_prefix(authority, plan, output, records, completed, pending=False):
    if len(records) != 1 + 2 * completed + int(pending):
        raise ValueError("prior allocation is missing, duplicated or unfinished; no retry")
    verify_journal_payload(records[0], {"record_type": "ATTEMPT_DECLARED", "protocol_id": PROTOCOL_ID,
        "claim_id": authority["claim"]["claim_id"], "source_commit": authority["source"],
        "plan_sha256": sha(output / "plan.json"),
        "execution_consumption_receipt_sha256": sha(output / "execution-consumption-receipt.json"),
        "allocations_initialized": 0, "initialized": False})
    for offset in range(completed + int(pending)):
        allocation = plan["allocations"][offset]
        verify_journal_payload(records[1 + offset * 2], declaration(output, allocation, offset + 1))
        if offset < completed:
            path = output / "evidence" / allocation["allocation_id"] / "allocation-artifact.json"
            artifact = read(path)
            verify_journal_payload(records[2 + offset * 2], {"record_type": "ALLOCATION_FINALIZED",
                "allocation_id": allocation["allocation_id"], "allocation_index": offset + 1,
                "initialized": True, "validity": artifact["metric_projection"]["validity"],
                "artifact_sha256": sha(path)})
            if artifact["metric_projection"]["validity"] != "VALID":
                raise ValueError("invalid attempt has stopped the corpus")

def admit(root, output, index, tail):
    authority, plan, allocation = bound_allocation(root, output, index)
    records = verify_journal(output / "attempts.jsonl", expected_tail=tail)
    validate_journal_prefix(authority, plan, output, records, index - 1)
    request_path = output / "requests" / (allocation["allocation_id"] + ".json")
    request = read(request_path)
    if not same_json(request, exact_request(root, allocation, read(root / "industrial-waste/v2/r1-ordering-corpus.json"))):
        raise ValueError("request differs from frozen exact allocation")
    next_tail = append_journal(output / "attempts.jsonl", declaration(output, allocation, index),
        key=f"{allocation['allocation_id']}:declared", expected_tail=tail)
    create_json_once(output / "tails" / f"{index:04d}-declared.json", {"tail": next_tail})
    authority["read_api"].assert_live_unchanged()
    return {"status": "ADMITTED_ONCE", "tail": next_tail, "request": request}

def allocation_artifact(root, output, authority, allocation, index):
    """Reconstruct exact evidence bindings for completion and independent finalization."""
    directory = output / "evidence" / allocation["allocation_id"]
    request_path = output / "requests" / (allocation["allocation_id"] + ".json")
    request = read(request_path)
    if not same_json(request, exact_request(root, allocation, read(root / "industrial-waste/v2/r1-ordering-corpus.json"))):
        raise ValueError("request differs from frozen exact allocation")
    if not same_json(read(directory / "input.json"), request):
        raise ValueError("initialized input differs from the admitted request")
    before = read(directory / "attempt-before-initialization.json")
    if not same_json(before, {"allocationId": allocation["allocation_id"], "initialized": False, "official": True}):
        raise ValueError("raw initialization declaration differs from the admitted allocation")
    status = read(directory / "execution-status.json")
    quiet_observations = [json.loads(line) for line in
        (directory / "quiet-checkpoint-observations.jsonl").read_text().splitlines()]
    projection = project_metrics(status, read(directory / "event-metrics.json"),
        json.loads((directory / "checkpoints.json").read_text()), quiet_observations)
    if projection["validity"] == "VALID":
        replay = read(directory / "replay.json")
        if (replay.get("status") != "EXACT_ACTION_EVENT_STATE_REPLAY"
                or type(replay.get("actions")) is not int or replay["actions"] != status["acceptedActions"]):
            raise ValueError("exact transcript replay absent")
        if (replay.get("checkpointReplay") != "EXACT_EVERY_QUIET_CHECKPOINT_REPLAY"
                or type(replay.get("quietCheckpointObservations")) is not int
                or replay["quietCheckpointObservations"] != len(quiet_observations)):
            raise ValueError("complete semantic quiet-checkpoint replay absent")
        actions = json.loads((directory / "actions.json").read_text())
        transitions = [json.loads(line) for line in (directory / "transitions.jsonl").read_text().splitlines()]
        if (not isinstance(actions, list) or len(actions) != replay["actions"]
                or len(transitions) != len(actions) * 2):
            raise ValueError("accepted raw action journal is incomplete")
        for offset, action in enumerate(actions):
            declared, returned = transitions[offset * 2:offset * 2 + 2]
            if (not same_json(declared, {"type": "ACTION_DECLARED", "index": offset + 1, "action": action})
                    or returned.get("type") != "ACTION_RETURNED"
                    or type(declared.get("index")) is not int or type(returned.get("index")) is not int
                    or returned["index"] != offset + 1 or returned.get("error", "MISSING") is not None
                    or not isinstance(returned.get("events"), list)
                    or re.fullmatch(r"[0-9a-f]{64}", str(returned.get("stateSha256"))) is None):
                raise ValueError("accepted raw action journal ordering or replay metadata drift")
        terminal_digest = transitions[-1]["stateSha256"] if transitions else sha(directory / "initial-state.json")
        if terminal_digest != sha(directory / "final-state.json"):
            raise ValueError("raw final state differs from the accepted replay transcript")
        for observation in quiet_observations:
            accepted_index = observation["acceptedActions"]
            expected_state_digest = (sha(directory / "initial-state.json") if accepted_index == 0
                else transitions[accepted_index * 2 - 1]["stateSha256"])
            if observation["stateSha256"] != expected_state_digest:
                raise ValueError("quiet observation is not bound to its exact accepted raw state")
    artifact = {"schema": SCHEMA, "protocol_id": PROTOCOL_ID,
        "allocation_id": allocation["allocation_id"], "allocation_index": index,
        "row": allocation["row"], "schedule": allocation["schedule"],
        "deck": allocation["deck_id"], "deck_sha256": allocation["deck_sha256"],
        "provenance": {"source_commit": authority["source"], "allocation_plan_sha256": sha(output / "plan.json"),
            "runtime_identity_receipt_sha256": authority["runtime_sha256"],
            "runner_binding_sha256": sha(root / "industrial-waste/v2/r1_execute.py"),
            "artifact_contract_sha256": sha(root / "industrial-waste/v2/r1_artifact_contract.py"),
            "metric_projection_sha256": sha(root / "industrial-waste/v2/r1_metric_projection.py"),
            "checkpoint_clarification_acceptance_sha256": sha(root / CLARIFICATION_PATH)},
        "execution": {"terminal_status": status["status"], "submitted_actions": status["submittedActions"],
            "accepted_actions": status["acceptedActions"], "own_turns_started": status["ownTurnsStarted"],
            "own_turns_completed": status["ownTurnsCompleted"]},
        "evidence": {"action_transcript_sha256": sha(directory / "transitions.jsonl"),
            "payment_intents_sha256": sha(directory / "payment-intents.jsonl"),
            "quiet_checkpoint_observations_sha256": sha(directory / "quiet-checkpoint-observations.jsonl"),
            "raw_telemetry_sha256": sha(directory / "event-metrics.json"),
            "checkpoint_telemetry_sha256": sha(directory / "checkpoints.json"),
            "initial_ordering_sha256": sha(directory / "input.json"),
            "admitted_request_sha256": sha(request_path),
            "initialization_declaration_sha256": sha(directory / "attempt-before-initialization.json"),
            "execution_status_sha256": sha(directory / "execution-status.json"),
            "actions_sha256": sha(directory / "actions.json"),
            "initial_state_sha256": sha(directory / "initial-state.json"),
            "initial_events_sha256": sha(directory / "initial-events.json"),
            "final_state_sha256": sha(directory / "final-state.json"),
            "replay_sha256": sha(directory / "replay.json") if (directory / "replay.json").exists() else None,
            "failure_bytes_sha256": sha(directory / "failure.txt") if (directory / "failure.txt").exists() else None},
        "metric_projection": projection}
    validate_record(artifact, allocation)
    return artifact


def complete(root, output, index, tail):
    authority, plan, allocation = bound_allocation(root, output, index)
    records = verify_journal(output / "attempts.jsonl", expected_tail=tail)
    validate_journal_prefix(authority, plan, output, records, index - 1, pending=True)
    directory = output / "evidence" / allocation["allocation_id"]
    artifact = allocation_artifact(root, output, authority, allocation, index)
    digest = create_json_once(directory / "allocation-artifact.json", artifact)
    next_tail = append_journal(output / "attempts.jsonl", {"record_type": "ALLOCATION_FINALIZED",
        "allocation_id": allocation["allocation_id"], "allocation_index": index, "initialized": True,
        "validity": artifact["metric_projection"]["validity"], "artifact_sha256": digest},
        key=f"{allocation['allocation_id']}:finalized", expected_tail=tail)
    create_json_once(output / "tails" / f"{index:04d}-finalized.json", {"tail": next_tail})
    authority["read_api"].assert_live_unchanged()
    return {"status": artifact["metric_projection"]["validity"], "tail": next_tail}

def finalize(root, output, tail):
    authority, plan, _ = bound_allocation(root, output, 512)
    journal = verify_journal(output / "attempts.jsonl", expected_tail=tail)
    validate_journal_prefix(authority, plan, output, journal, 512)
    records = []
    for index, allocation in enumerate(plan["allocations"], 1):
        artifact = read(output / "evidence" / allocation["allocation_id"] / "allocation-artifact.json")
        if not same_json(artifact, allocation_artifact(root, output, authority, allocation, index)):
            raise ValueError("finalized artifact differs from its exact raw evidence or metric projection")
        records.append(artifact)
    audit = validate_complete_corpus(records, plan)
    if not audit["decision_rule_admission_ready"]:
        raise ValueError("invalid evidence cannot reach the decision rule")
    flat = [{"deck": item["deck"], "row": item["row"], "schedule": item["schedule"],
        "terminal_status": item["execution"]["terminal_status"], **item["metric_projection"]} for item in records]
    create_json_once(output / "corpus-audit.json", audit)
    authority["read_api"].assert_live_unchanged()
    create_json_once(output / "r1-decision.json", evaluate_complete_metrics(flat))
    return {"status": "COMPLETE_512_PENDING_EVIDENCE_ACCEPTANCE", "official_allocations": 512}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "admit", "complete", "finalize"])
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--session", type=Path)
    parser.add_argument("--index", type=int)
    parser.add_argument("--tail")
    args = parser.parse_args()
    root, output = args.root.resolve(), args.output.resolve()
    if args.command == "prepare": result = prepare(root, args.session, output)
    elif args.command == "admit": result = admit(root, output, args.index, args.tail)
    elif args.command == "complete": result = complete(root, output, args.index, args.tail)
    else: result = finalize(root, output, args.tail)
    print(json.dumps(result, separators=(",", ":")))

if __name__ == "__main__":
    main()
