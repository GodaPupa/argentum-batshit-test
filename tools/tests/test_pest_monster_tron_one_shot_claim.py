"""Deterministic claim-boundary tests. No network, real claims, seeds, or gameplay."""
from __future__ import annotations

import base64
from copy import deepcopy
from dataclasses import replace
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "_monster_tron_claim_under_test", ROOT / "scripts/pest-monster-tron-one-shot-claim.py"
)
assert SPEC is not None and SPEC.loader is not None
claim = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = claim
SPEC.loader.exec_module(claim)

SOURCE = "1" * 40
WORKFLOW = "2" * 40
SOURCE_TREE = "3" * 40
RIVAL_COMMIT = "4" * 40
MOVED = "5" * 40
RUN_ID = 12345
BRANCH = "pest-control/excluded-claim-fixture"
REF_ENDPOINT = "/git/ref/" + claim.CLAIM_REF.removeprefix("refs/")
BRANCH_ENDPOINT = "/git/ref/heads/" + BRANCH
CONTEXT = claim.Context(SOURCE, WORKFLOW, RUN_ID, 1)


def object_id(kind: str, value: dict) -> str:
    """Stable fake tree/commit identities; only blob objects require Git's real byte hash."""
    return hashlib.sha1((kind + json.dumps(value, sort_keys=True)).encode()).hexdigest()


class FakeGitHub:
    """An in-memory create-only Git object store and one live workflow.

    Failure injection happens after a write, so timeout tests exercise an ambiguous
    server-side success rather than an obviously unattempted request.
    """

    def __init__(self):
        self.calls: list[tuple[str, str, dict | None]] = []
        self.reference: dict | None = None
        self.branch_sha = WORKFLOW
        self.branch_reads = 0
        self.move_branch_on_read: int | None = None
        self.run = {
            "id": RUN_ID,
            "run_attempt": 1,
            "head_sha": WORKFLOW,
            "status": "in_progress",
            "conclusion": None,
            "path": claim.WORKFLOW_PATH,
            "head_branch": BRANCH,
        }
        self.blobs: dict[str, bytes] = {}
        self.trees: dict[str, dict] = {}
        self.commits = {SOURCE: {"sha": SOURCE, "tree": {"sha": SOURCE_TREE}, "parents": []}}
        self.ref_conflict_status: int | None = None
        self.after_write_errors: dict[str, Exception] = {}
        self.content_overrides: dict = {}
        self.content_bytes_override: bytes | None = None
        self.response_overrides: dict[tuple[str, str], dict] = {}

    @property
    def writes(self):
        return [call for call in self.calls if call[0] != "GET"]

    def request(self, method: str, path: str, payload: dict | None = None) -> dict:
        self.calls.append((method, path, deepcopy(payload)))
        response = self._request(method, path, payload)
        if method != "GET" and path in self.after_write_errors:
            raise self.after_write_errors[path]
        return deepcopy(self.response_overrides.get((method, path), response))

    def _request(self, method: str, path: str, payload: dict | None) -> dict:
        if method == "GET":
            if path == REF_ENDPOINT:
                if self.reference is None:
                    raise claim.ApiError(404)
                return self.reference
            if path == BRANCH_ENDPOINT:
                self.branch_reads += 1
                if self.move_branch_on_read == self.branch_reads:
                    self.branch_sha = MOVED
                return {"ref": "refs/heads/" + BRANCH, "object": {"sha": self.branch_sha}}
            if path == f"/actions/runs/{RUN_ID}":
                return self.run
            if path.startswith("/git/commits/"):
                return self.commits[path.removeprefix("/git/commits/")]
            if path.startswith(f"/contents/{claim.CLAIM_PATH}?ref="):
                commit_sha = path.split("?ref=", 1)[1]
                tree_sha = self.commits[commit_sha]["tree"]["sha"]
                entry = next(entry for entry in self.trees[tree_sha]["tree"]
                             if entry["path"] == claim.CLAIM_PATH)
                raw = self.blobs[entry["sha"]]
                if self.content_bytes_override is not None:
                    raw = self.content_bytes_override
                return {
                    "encoding": "base64", "sha": entry["sha"], "path": claim.CLAIM_PATH,
                    "content": base64.b64encode(raw).decode() + "\n",
                    **self.content_overrides,
                }
            raise AssertionError(f"Unexpected fake GET: {path}")

        if method != "POST" or payload is None:
            raise AssertionError(f"Non-create mutation attempted: {method} {path}")
        if path == "/git/blobs":
            if payload["encoding"] != "utf-8":
                raise AssertionError("Claim must have exact UTF-8 bytes")
            raw = payload["content"].encode("utf-8")
            sha = claim.git_blob_sha(raw)
            self.blobs[sha] = raw
            return {"sha": sha}
        if path == "/git/trees":
            sha = object_id("tree", payload)
            self.trees[sha] = deepcopy(payload)
            return {"sha": sha}
        if path == "/git/commits":
            sha = object_id("commit", payload)
            self.commits[sha] = {
                "sha": sha, "tree": {"sha": payload["tree"]},
                "parents": [{"sha": parent} for parent in payload["parents"]],
            }
            return {"sha": sha}
        if path == "/git/refs":
            if self.ref_conflict_status is not None:
                self.reference = {"ref": claim.CLAIM_REF, "object": {"sha": RIVAL_COMMIT}}
                raise claim.ApiError(self.ref_conflict_status)
            if self.reference is not None:
                raise claim.ApiError(422)
            self.reference = {"ref": payload["ref"], "object": {"sha": payload["sha"]}}
            return self.reference
        raise AssertionError(f"Unexpected fake POST: {path}")


class MonsterTronClaimTests(unittest.TestCase):
    def setUp(self):
        self.no_network = patch.object(
            claim, "urlopen", side_effect=AssertionError("Network is forbidden in claim fixtures")
        )
        self.no_network.start()
        self.addCleanup(self.no_network.stop)

    def reserved(self):
        api = FakeGitHub()
        receipt = claim.create_claim(api, CONTEXT)
        api.calls.clear()
        return api, receipt

    def assert_read_only_rejection(self, api, context, receipt):
        with self.assertRaises(claim.ClaimError):
            claim.verify_receipt(api, context, receipt)
        self.assertTrue(all(method == "GET" for method, _, _ in api.calls))

    def test_success_reserves_exactly_four_once_with_bound_durable_objects(self):
        api = FakeGitHub()
        receipt = claim.create_claim(api, CONTEXT)
        self.assertEqual([path for _, path, _ in api.writes],
                         ["/git/blobs", "/git/trees", "/git/commits", "/git/refs"])
        self.assertEqual(api.reference["object"]["sha"], receipt["claim_commit_sha"])
        committed = json.loads(api.blobs[receipt["claim_blob_sha"]])
        for field, expected in {
            "reserved_games": 4, "attempt_limit": 1,
            "state": "CLAIMED_ALL_FOUR_RESERVED_NO_RETRY",
            "official_games_initialized": 0, "actions_submitted": 0, "outcome_exposure": 0,
            "execution_allowed": False, "execution_source_sha": SOURCE,
            "execution_source_tree_sha": SOURCE_TREE, "workflow_source_sha": WORKFLOW,
            "workflow_run_id": RUN_ID, "workflow_run_attempt": 1,
            "vector_sha256": "1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31",
            "assignments_sha256": claim.ASSIGNMENTS, "archive_sha256": claim.ARCHIVE,
            "freeze_source_sha": claim.FREEZE_SOURCE,
        }.items():
            with self.subTest(field=field):
                self.assertIs(type(committed[field]), type(expected))
                self.assertEqual(committed[field], expected)
        tree = api.trees[receipt["claim_tree_sha"]]
        self.assertEqual(tree["base_tree"], SOURCE_TREE)
        self.assertEqual(tree["tree"], [{"path": claim.CLAIM_PATH, "mode": "100644",
                                        "type": "blob", "sha": receipt["claim_blob_sha"]}])
        self.assertEqual(api.commits[receipt["claim_commit_sha"]]["parents"], [{"sha": SOURCE}])
        self.assertEqual(receipt["claim_payload_sha256"],
                         hashlib.sha256(api.blobs[receipt["claim_blob_sha"]]).hexdigest())
        self.assertIs(receipt["claim_confirmed"], True)
        self.assertIs(receipt["execution_allowed"], False)

    def test_existing_claim_permanently_refuses_even_if_attempt_did_not_finish(self):
        api, _ = self.reserved()
        with self.assertRaisesRegex(claim.ClaimError, "already has a claim"):
            claim.create_claim(api, CONTEXT)
        self.assertEqual(api.calls, [("GET", REF_ENDPOINT, None)])
        self.assertEqual(api.writes, [])

    def test_competing_worker_wins_ref_creation_without_retry_or_overwrite(self):
        for status in (409, 422):
            with self.subTest(status=status):
                api = FakeGitHub()
                api.ref_conflict_status = status
                with self.assertRaises(claim.ApiError) as failure:
                    claim.create_claim(api, CONTEXT)
                self.assertEqual(failure.exception.status, status)
                self.assertEqual(sum(path == "/git/refs" for _, path, _ in api.writes), 1)
                self.assertEqual(api.reference["object"]["sha"], RIVAL_COMMIT)
                api.calls.clear()
                with self.assertRaisesRegex(claim.ClaimError, "already has a claim"):
                    claim.create_claim(api, CONTEXT)
                self.assertEqual(api.writes, [])

    def test_ambiguous_mutation_timeouts_never_retry_or_return_a_receipt(self):
        for endpoint in ("/git/blobs", "/git/trees", "/git/commits", "/git/refs"):
            with self.subTest(endpoint=endpoint):
                api = FakeGitHub()
                api.after_write_errors[endpoint] = TimeoutError("response lost after server write")
                missing = object()
                receipt = missing
                with self.assertRaises(TimeoutError):
                    receipt = claim.create_claim(api, CONTEXT)
                self.assertIs(receipt, missing)
                self.assertEqual(sum(path == endpoint for _, path, _ in api.writes), 1)
                self.assertEqual(api.writes[-1][1], endpoint)
                if endpoint == "/git/refs":
                    self.assertIsNotNone(api.reference)
                    api.calls.clear()
                    with self.assertRaisesRegex(claim.ClaimError, "already has a claim"):
                        claim.create_claim(api, CONTEXT)
                    self.assertEqual(api.writes, [])

    def test_rerun_wrong_repository_or_bad_identity_abort_before_any_request(self):
        for context in (replace(CONTEXT, run_attempt=2), replace(CONTEXT, run_id=0),
                        replace(CONTEXT, repository="someone/else"),
                        replace(CONTEXT, source_sha="short"),
                        replace(CONTEXT, workflow_sha="A" * 40)):
            with self.subTest(context=context):
                api = FakeGitHub()
                with self.assertRaises(claim.ClaimError):
                    claim.create_claim(api, context)
                self.assertEqual(api.calls, [])

    def test_wrong_or_nonrunning_workflow_aborts_before_mutation(self):
        for field, value in (("id", RUN_ID + 1), ("run_attempt", 2), ("head_sha", MOVED),
                             ("status", "completed"), ("conclusion", "failure"),
                             ("path", ".github/workflows/unreviewed.yml"),
                             ("head_branch", "")):
            with self.subTest(field=field):
                api = FakeGitHub()
                api.run[field] = value
                with self.assertRaises(claim.ClaimError):
                    claim.create_claim(api, CONTEXT)
                self.assertEqual(api.writes, [])

    def test_moved_branch_aborts_before_claim_and_never_retries_a_claim(self):
        for read_number in (1, 2, 3):
            with self.subTest(branch_read=read_number):
                api = FakeGitHub()
                api.move_branch_on_read = read_number
                with self.assertRaisesRegex(claim.ClaimError, "branch moved"):
                    claim.create_claim(api, CONTEXT)
                ref_writes = [call for call in api.writes if call[1] == "/git/refs"]
                self.assertEqual(len(ref_writes), 1 if read_number == 3 else 0)
                if read_number == 1:
                    self.assertEqual(api.writes, [])
                if read_number == 3:
                    self.assertIsNotNone(api.reference)

    def test_wrong_blob_response_prevents_claim_commit_or_ref(self):
        api = FakeGitHub()
        api.response_overrides[("POST", "/git/blobs")] = {"sha": MOVED}
        with self.assertRaisesRegex(claim.ClaimError, "claim blob"):
            claim.create_claim(api, CONTEXT)
        self.assertEqual([path for _, path, _ in api.writes], ["/git/blobs"])
        self.assertIsNone(api.reference)

    def test_receipt_verification_authenticates_remote_objects_using_only_get(self):
        api, receipt = self.reserved()
        verified = claim.verify_receipt(api, CONTEXT, receipt)
        self.assertEqual(verified["status"], "DURABLE_CLAIM_VERIFIED_NOT_GAMEPLAY_AUTHORIZATION")
        self.assertEqual(verified["claim_commit_sha"], receipt["claim_commit_sha"])
        self.assertIs(verified["execution_allowed"], False)
        self.assertTrue(all(method == "GET" for method, _, _ in api.calls))
        paths = {path for _, path, _ in api.calls}
        self.assertTrue({f"/actions/runs/{RUN_ID}", BRANCH_ENDPOINT, REF_ENDPOINT,
                         f"/git/commits/{receipt['claim_commit_sha']}",
                         f"/git/commits/{SOURCE}",
                         f"/contents/{claim.CLAIM_PATH}?ref={receipt['claim_commit_sha']}"} <= paths)

    def test_forged_receipt_fields_or_boolean_numeric_substitution_are_rejected(self):
        for field, value in (("vector_sha256", "0" * 64), ("archive_sha256", "0" * 64),
                             ("reserved_games", 5), ("attempt_limit", True),
                             ("claim_confirmed", 1), ("execution_allowed", 0),
                             ("execution_source_sha", MOVED), ("workflow_run_id", RUN_ID + 1)):
            with self.subTest(field=field):
                api, receipt = self.reserved()
                receipt[field] = value
                self.assert_read_only_rejection(api, CONTEXT, receipt)

    def test_receipt_cannot_be_reused_by_a_different_source_workflow_or_run(self):
        for context in (replace(CONTEXT, source_sha=MOVED), replace(CONTEXT, workflow_sha=MOVED),
                        replace(CONTEXT, run_id=RUN_ID + 1), replace(CONTEXT, run_attempt=2),
                        replace(CONTEXT, repository="someone/else")):
            with self.subTest(context=context):
                api, receipt = self.reserved()
                self.assert_read_only_rejection(api, context, receipt)

    def test_receipt_checks_live_run_and_workflow_branch(self):
        for field, value in (("path", ".github/workflows/unreviewed.yml"),
                             ("status", "completed"), ("conclusion", "success"),
                             ("run_attempt", 2), ("head_sha", MOVED),
                             ("head_branch", "another/branch")):
            with self.subTest(field=field):
                api, receipt = self.reserved()
                api.run[field] = value
                self.assert_read_only_rejection(api, CONTEXT, receipt)
        api, receipt = self.reserved()
        api.branch_sha = MOVED
        self.assert_read_only_rejection(api, CONTEXT, receipt)

    def test_receipt_rejects_moved_claim_ref_or_wrong_ref_name(self):
        for changed in ({"ref": claim.CLAIM_REF, "object": {"sha": MOVED}},
                        {"ref": "refs/heads/another-claim", "object": {"sha": None}}):
            with self.subTest(reference=changed):
                api, receipt = self.reserved()
                if changed["object"]["sha"] is None:
                    changed["object"]["sha"] = receipt["claim_commit_sha"]
                api.reference = changed
                self.assert_read_only_rejection(api, CONTEXT, receipt)

    def test_receipt_checks_exact_commit_parent_tree_and_execution_source_tree(self):
        for change in ("commit_sha", "tree", "wrong_parent", "extra_parent", "source_sha", "source_tree"):
            with self.subTest(change=change):
                api, receipt = self.reserved()
                commit = api.commits[receipt["claim_commit_sha"]]
                if change == "commit_sha":
                    commit["sha"] = MOVED
                elif change == "tree":
                    commit["tree"]["sha"] = MOVED
                elif change == "wrong_parent":
                    commit["parents"] = [{"sha": MOVED}]
                elif change == "extra_parent":
                    commit["parents"].append({"sha": MOVED})
                elif change == "source_sha":
                    api.commits[SOURCE]["sha"] = MOVED
                else:
                    api.commits[SOURCE]["tree"]["sha"] = MOVED
                self.assert_read_only_rejection(api, CONTEXT, receipt)

    def test_receipt_rejects_wrong_blob_metadata_or_changed_remote_bytes(self):
        for field, value in (("encoding", "utf-8"), ("sha", MOVED), ("path", "another/path")):
            with self.subTest(field=field):
                api, receipt = self.reserved()
                api.content_overrides[field] = value
                self.assert_read_only_rejection(api, CONTEXT, receipt)
        api, receipt = self.reserved()
        api.content_bytes_override = api.blobs[receipt["claim_blob_sha"]] + b" "
        self.assert_read_only_rejection(api, CONTEXT, receipt)

    def test_receipt_must_match_the_exact_committed_payload_without_extra_fields(self):
        for change in ("extra_field", "payload_digest", "source_tree"):
            with self.subTest(change=change):
                api, receipt = self.reserved()
                if change == "extra_field":
                    receipt["permit_retry"] = True
                elif change == "payload_digest":
                    receipt["claim_payload_sha256"] = "0" * 64
                else:
                    receipt["execution_source_tree_sha"] = MOVED
                self.assert_read_only_rejection(api, CONTEXT, receipt)


if __name__ == "__main__":
    unittest.main()
