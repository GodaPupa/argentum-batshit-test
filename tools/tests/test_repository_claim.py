"""Offline create-only repository authority fixtures. Never contacts GitHub."""
import base64
from copy import deepcopy
from dataclasses import replace
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import unittest

SPEC = importlib.util.spec_from_file_location(
    "repository_claim", Path(__file__).resolve().parents[1] / "repository_claim.py")
claim = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = claim
SPEC.loader.exec_module(claim)
SOURCE = "1" * 40
TREE = "2" * 40
CONTEXT = claim.ClaimSpec("fixture/repository", "fixture/source", SOURCE,
                         "refs/heads/fixture/official-attempts/block", "fixture/claim.json")
PAYLOAD = {"schema": "fixture-claim", "initialized": 0, "outcomes": 0}


class FakeAPI:
    repository = "fixture/repository"

    def __init__(self):
        self.calls = []
        self.source = SOURCE
        self.ref = None
        self.blobs = {}
        self.trees = {}
        self.commits = {SOURCE: {"sha": SOURCE, "tree": {"sha": TREE}, "parents": []}}
        self.fail_after_ref = False
        self.race_on_ref = False
        self.move_after_tree = False
        self.corrupt_blob = False

    def request(self, method, path, payload=None):
        self.calls.append((method, path, deepcopy(payload)))
        if method == "GET":
            if path == "/git/ref/heads/fixture/source":
                return {"object": {"sha": self.source}}
            if path == "/git/ref/heads/fixture/official-attempts/block":
                if self.ref is None:
                    raise claim.ApiError(404)
                return deepcopy(self.ref)
            if path.startswith("/git/commits/"):
                return deepcopy(self.commits[path.rsplit("/", 1)[1]])
            if path.startswith("/contents/fixture/claim.json?ref="):
                commit = self.commits[path.split("=", 1)[1]]
                entry = self.trees[commit["tree"]["sha"]]["tree"][0]
                return {"encoding": "base64", "sha": entry["sha"], "path": entry["path"],
                        "content": base64.b64encode(self.blobs[entry["sha"]]).decode()}
            raise AssertionError(path)
        if method != "POST":
            raise AssertionError("only create operations permitted")
        if path == "/git/blobs":
            raw = payload["content"].encode()
            sha = claim._blob_sha(raw)
            self.blobs[sha] = raw
            return {"sha": "f" * 40 if self.corrupt_blob else sha}
        sha = hashlib.sha1(json.dumps(payload, sort_keys=True).encode()).hexdigest()
        if path == "/git/trees":
            self.trees[sha] = deepcopy(payload)
            if self.move_after_tree:
                self.source = "3" * 40
            return {"sha": sha}
        if path == "/git/commits":
            self.commits[sha] = {"sha": sha, "tree": {"sha": payload["tree"]},
                                 "parents": [{"sha": parent} for parent in payload["parents"]]}
            return {"sha": sha}
        if path == "/git/refs":
            if self.ref is not None or self.race_on_ref:
                self.ref = {"ref": CONTEXT.claim_ref, "object": {"sha": "9" * 40}}
                raise claim.ApiError(422)
            self.ref = {"ref": payload["ref"], "object": {"sha": payload["sha"]}}
            if self.fail_after_ref:
                raise TimeoutError("fixture ambiguous post-write timeout")
            return deepcopy(self.ref)
        raise AssertionError(path)


class RepositoryClaimTests(unittest.TestCase):
    def test_confirmed_claim_binds_exact_bytes_and_source_without_authorizing(self):
        api = FakeAPI()
        receipt = claim.reserve_claim(api, CONTEXT, PAYLOAD)
        self.assertFalse(receipt["execution_allowed"])
        self.assertEqual([p for m, p, _ in api.calls if m != "GET"],
                         ["/git/blobs", "/git/trees", "/git/commits", "/git/refs"])
        api.calls.clear()
        claim.verify_claim(api, CONTEXT, PAYLOAD, receipt)
        self.assertTrue(all(m == "GET" for m, _, _ in api.calls))

    def test_second_attempt_fails_before_writes(self):
        api = FakeAPI()
        claim.reserve_claim(api, CONTEXT, PAYLOAD)
        api.calls.clear()
        with self.assertRaises(claim.ClaimError):
            claim.reserve_claim(api, CONTEXT, PAYLOAD)
        self.assertTrue(all(m == "GET" for m, _, _ in api.calls))

    def test_concurrent_reservation_is_not_retried(self):
        api = FakeAPI()
        api.race_on_ref = True
        with self.assertRaises(claim.ApiError):
            claim.reserve_claim(api, CONTEXT, PAYLOAD)
        self.assertEqual(sum(p == "/git/refs" for _, p, _ in api.calls), 1)

    def test_ambiguous_write_preserves_remote_claim_without_retry(self):
        api = FakeAPI()
        api.fail_after_ref = True
        with self.assertRaises(TimeoutError):
            claim.reserve_claim(api, CONTEXT, PAYLOAD)
        self.assertIsNotNone(api.ref)
        self.assertEqual(sum(p == "/git/refs" for _, p, _ in api.calls), 1)

    def test_stale_source_rejected_before_claim_creation(self):
        api = FakeAPI()
        api.move_after_tree = True
        with self.assertRaises(claim.ClaimError):
            claim.reserve_claim(api, CONTEXT, PAYLOAD)
        self.assertFalse(any(p == "/git/refs" for _, p, _ in api.calls))

    def test_changed_payload_and_claim_parent_rejected_read_only(self):
        api = FakeAPI()
        receipt = claim.reserve_claim(api, CONTEXT, PAYLOAD)
        with self.assertRaises(claim.ClaimError):
            claim.verify_claim(api, CONTEXT, {**PAYLOAD, "outcomes": 1}, receipt)
        api.commits[receipt["claim_commit_sha"]]["parents"] = []
        api.calls.clear()
        with self.assertRaises(claim.ClaimError):
            claim.verify_claim(api, CONTEXT, PAYLOAD, receipt)
        self.assertTrue(all(m == "GET" for m, _, _ in api.calls))

    def test_wrong_repository_invalid_namespace_and_nonfinite_fail_before_writes(self):
        for context in [replace(CONTEXT, repository="other/repository"),
                        replace(CONTEXT, claim_ref="refs/heads/main"),
                        replace(CONTEXT, claim_path="../escape")]:
            api = FakeAPI()
            with self.assertRaises(claim.ClaimError):
                claim.reserve_claim(api, context, PAYLOAD)
            self.assertEqual(api.calls, [])
        api = FakeAPI()
        with self.assertRaises(ValueError):
            claim.reserve_claim(api, CONTEXT, {"bad": float("nan")})
        self.assertEqual(api.calls, [])

    def test_wrong_blob_bytes_stop_before_ref(self):
        api = FakeAPI()
        api.corrupt_blob = True
        with self.assertRaises(claim.ClaimError):
            claim.reserve_claim(api, CONTEXT, PAYLOAD)
        self.assertFalse(any(p == "/git/refs" for _, p, _ in api.calls))


if __name__ == "__main__":
    unittest.main()
