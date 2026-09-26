"""Deterministic orchestration/claim fixtures only; no engine or official initialization."""
import importlib.util
import base64
import copy
import os
import subprocess
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch, Mock

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location("iw_r1_execute", HERE / "r1_execute.py")
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ExecutionAdapterTests(unittest.TestCase):
    def test_missing_authority_never_creates_output_or_initializes(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            session = root / "session.json"
            session.write_text(json.dumps({"protocol_id": "NOT_THE_FROZEN_PROTOCOL"}))
            output = root / "execution"
            with self.assertRaisesRegex(ValueError, "protocol drift"):
                runner.prepare(root, session, output)
            self.assertFalse(output.exists())

    def test_all_512_requests_reproduce_every_frozen_ordering_digest(self):
        root = HERE.parents[1]
        plan = runner.compile_plan(root, "0" * 40)
        corpus = runner.read(root / "industrial-waste/v2/r1-ordering-corpus.json")
        for allocation in plan["allocations"]:
            request = runner.exact_request(root, allocation, corpus)
            self.assertEqual(request["allocationId"], allocation["allocation_id"])
            self.assertEqual(len(request["deckCards"]), 60)
            self.assertEqual(request["namespace"], corpus["namespace"])
            actual = [runner.hashlib.sha256(("\n".join(order) + "\n").encode()).hexdigest()
                for order in request["openingOrders"]]
            self.assertEqual(actual, allocation["initial_ordering_sha256"])

    def fixture(self, output):
        source_root = HERE.parents[1]
        plan = runner.compile_plan(source_root, "1" * 40)
        authority = {"source": "1" * 40, "claim": {"claim_id": "SYNTHETIC_OFFLINE_FIXTURE"},
            "runtime_sha256": "2" * 64, "read_api": Mock(spec=runner.GitImmutableReadAPI)}
        for name in ("requests", "tails", "evidence"):
            (output / name).mkdir()
        runner.create_json_once(output / "plan.json", plan)
        runner.create_json_once(output / "execution-consumption-receipt.json", {"fixture": "offline only"})
        allocation = plan["allocations"][0]
        request = runner.exact_request(source_root, allocation,
            runner.read(source_root / "industrial-waste/v2/r1-ordering-corpus.json"))
        runner.create_json_once(output / "requests" / (allocation["allocation_id"] + ".json"), request)
        first = {"record_type": "ATTEMPT_DECLARED", "protocol_id": runner.PROTOCOL_ID,
            "claim_id": authority["claim"]["claim_id"], "source_commit": authority["source"],
            "plan_sha256": runner.sha(output / "plan.json"),
            "execution_consumption_receipt_sha256": runner.sha(output / "execution-consumption-receipt.json"),
            "allocations_initialized": 0, "initialized": False}
        tail = runner.create_journal(output / "attempts.jsonl", first, key="fixture:1")
        return authority, plan, allocation, request, tail

    def test_admission_is_durable_before_it_returns_and_never_retries(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw)
            authority, plan, allocation, request, tail = self.fixture(output)
            with patch.object(runner, "bound_allocation", return_value=(authority, plan, allocation)):
                answer = runner.admit(HERE.parents[1], output, 1, tail)
                self.assertEqual(answer["status"], "ADMITTED_ONCE")
                records = runner.verify_journal(output / "attempts.jsonl", expected_tail=answer["tail"])
                self.assertEqual(records[-1]["stage"], "BEFORE_INITIALIZATION")
                self.assertFalse(records[-1]["initialized"])
                for attempted_tail in (tail, answer["tail"]):
                    with self.assertRaises((ValueError, runner.verify_journal.__globals__["EvidenceError"])):
                        runner.admit(HERE.parents[1], output, 1, attempted_tail)

    def test_changed_request_is_rejected_without_consuming_the_allocation(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw)
            authority, plan, allocation, _, tail = self.fixture(output)
            (output / "requests" / (allocation["allocation_id"] + ".json")).write_text('{"different":true}')
            with patch.object(runner, "bound_allocation", return_value=(authority, plan, allocation)):
                with self.assertRaisesRegex(ValueError, "request differs"):
                    runner.admit(HERE.parents[1], output, 1, tail)
            self.assertEqual(len(runner.verify_journal(output / "attempts.jsonl", expected_tail=tail)), 1)

    def raw_fixture(self, output, allocation, request):
        # Fabricated unit-test telemetry, deliberately never submitted to a game initializer.
        directory = output / "evidence" / allocation["allocation_id"]
        directory.mkdir()
        values = {"input.json": request,
            "attempt-before-initialization.json": {"allocationId": allocation["allocation_id"],
                "initialized": False, "official": True},
            "execution-status.json": {"status": "REAL_TERMINAL", "submittedActions": 0,
                "acceptedActions": 0, "ownTurnsStarted": 0, "ownTurnsCompleted": 0,
                "engineGameOver": True, "diagnostic": None},
            "event-metrics.json": {"acceptedTransitions": 0, "demonstratedLoopReadyTurn": None,
                "demonstratedLoopCycles": [], "actualLethalTurn": None,
                "certifiedFutureConversionTurn": None, "deterministicConversionTurn": None},
            "checkpoints.json": [], "actions.json": [], "initial-state.json": {},
            "initial-events.json": [], "final-state.json": {},
            "replay.json": {"status": "EXACT_ACTION_EVENT_STATE_REPLAY", "actions": 0}}
        for name, value in values.items():
            (directory / name).write_text(json.dumps(value))
        (directory / "transitions.jsonl").write_text("")
        (directory / "payment-intents.jsonl").write_text("")
        (directory / "quiet-checkpoint-observations.jsonl").write_text("")
        return directory

    def test_completion_binds_initialized_input_to_exact_admission(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw)
            authority, plan, allocation, request, tail = self.fixture(output)
            with patch.object(runner, "bound_allocation", return_value=(authority, plan, allocation)):
                admitted = runner.admit(HERE.parents[1], output, 1, tail)
                directory = self.raw_fixture(output, allocation, request)
                (directory / "input.json").write_text(json.dumps({**request, "initializerSeed": 999}))
                with self.assertRaisesRegex(ValueError, "input differs"):
                    runner.complete(HERE.parents[1], output, 1, admitted["tail"])
                self.assertFalse((directory / "allocation-artifact.json").exists())

    def test_finalized_artifact_tampering_or_journal_mislabeling_is_rejected(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw)
            authority, plan, allocation, request, tail = self.fixture(output)
            with patch.object(runner, "bound_allocation", return_value=(authority, plan, allocation)):
                admitted = runner.admit(HERE.parents[1], output, 1, tail)
                directory = self.raw_fixture(output, allocation, request)
                answer = runner.complete(HERE.parents[1], output, 1, admitted["tail"])
            records = runner.verify_journal(output / "attempts.jsonl", expected_tail=answer["tail"])
            runner.validate_journal_prefix(authority, plan, output, records, 1)
            for index, key, value in ((1, "record_type", "ALLOCATION_FINALIZED"),
                    (1, "allocation_index", True), (2, "allocation_id", "IW_V2_R1_0002")):
                changed = copy.deepcopy(records)
                changed[index][key] = value
                with self.subTest(key=key), self.assertRaisesRegex(ValueError, "journal type"):
                    runner.validate_journal_prefix(authority, plan, output, changed, 1)
            artifact = runner.read(directory / "allocation-artifact.json")
            artifact["metric_projection"]["loop_ready_by_t8"] = True
            (directory / "allocation-artifact.json").write_text(json.dumps(artifact))
            with self.assertRaisesRegex(ValueError, "journal type"):
                runner.validate_journal_prefix(authority, plan, output, records, 1)

    def test_finalizer_rechecks_raw_evidence_after_artifact_was_finalized(self):
        with tempfile.TemporaryDirectory() as raw:
            output = Path(raw)
            authority, plan, allocation, request, tail = self.fixture(output)
            with patch.object(runner, "bound_allocation", return_value=(authority, plan, allocation)):
                admitted = runner.admit(HERE.parents[1], output, 1, tail)
                directory = self.raw_fixture(output, allocation, request)
                answer = runner.complete(HERE.parents[1], output, 1, admitted["tail"])
            # Isolate the one-allocation artifact validator; complete 512-member admission stays mandatory.
            small_plan = {**plan, "allocations": [allocation]}
            for filename, altered in (("initial-events.json", '[{"changed":true}]'),
                    ("replay.json", '{"status":"EXACT_ACTION_EVENT_STATE_REPLAY","actions":true}')):
                previous = (directory / filename).read_text()
                (directory / filename).write_text(altered)
                with patch.object(runner, "bound_allocation", return_value=(authority, small_plan, allocation)), \
                        patch.object(runner, "validate_journal_prefix"), \
                        patch.object(runner, "evaluate_complete_metrics") as decision:
                    with self.subTest(filename=filename), self.assertRaises(ValueError):
                        runner.finalize(HERE.parents[1], output, answer["tail"])
                    decision.assert_not_called()
                (directory / filename).write_text(previous)

    def test_published_authority_requires_exact_immutable_bytes(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            file = root / "authority.json"
            file.write_text('{"synthetic":"authority fixture"}')
            binding = {"path": file.name, "sha256": runner.sha(file), "repository_commit": "3" * 40}
            api = Mock()
            api.request.return_value = {"path": file.name, "encoding": "base64",
                "content": base64.b64encode(file.read_bytes()).decode()}
            with patch.object(runner, "GitHubExecutionAPI", return_value=api):
                runner.verify_published_binding(root, binding)
                api.request.return_value["content"] = base64.b64encode(b'{"different":true}').decode()
                with self.assertRaisesRegex(ValueError, "immutable published"):
                    runner.verify_published_binding(root, binding)
                with self.assertRaisesRegex(ValueError, "immutable repository commit"):
                    runner.verify_published_binding(root, {**binding, "repository_commit": "main"})

    def test_canonical_consumption_precedes_requests_and_cannot_move_to_a_second_checkout(self):
        # Shared reserve_claim is independently tested against a full fake Git database. This receiver
        # fixture exercises actual prepare ordering and canonical namespace across distinct executors.
        consumed = set()
        calls = []
        def reserve(api, spec, payload):
            calls.append((spec, copy.deepcopy(payload)))
            if spec.claim_ref in consumed:
                raise ValueError("canonical execution already consumed")
            consumed.add(spec.claim_ref)
            return {"fixture": "consumed once", "claim_commit_sha": "4" * 40}
        authority = {"source": "1" * 40, "read_api": Mock(spec=runner.GitImmutableReadAPI),
            "remote_receipt": {"repository": "GodaPupa/argentum-batshit-test", "source_branch": "fixture/source",
                "claim_commit_sha": "2" * 40},
            "claim": {"preinitialization_journal": {"path": "journal.json"}}}
        env = {"GITHUB_RUN_ID": "101", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_JOB": "fixture", "RUNNER_NAME": "fixture-host"}
        with tempfile.TemporaryDirectory() as raw, patch.dict(os.environ, env), \
                patch.object(runner, "validate_authority", return_value=authority), \
                patch.object(runner, "verify_published_binding"), \
                patch.object(runner, "validate_preinitialization_guard"), \
                patch.object(runner, "GitHubExecutionAPI", return_value=object()), \
                patch.object(runner, "reserve_claim", side_effect=reserve), \
                patch.object(runner, "compile_plan", side_effect=RuntimeError("synthetic failure after consumption")) as compile:
            for attempt in (1, 2):
                root = Path(raw) / f"checkout-{attempt}"
                output = root / runner.OUTPUT_RELATIVE
                output.parent.mkdir(parents=True)
                session = root / "session.json"
                session.write_text(json.dumps({"claim_path": runner.CLAIM_PATH, "preinitialization_journal_path": "journal.json"}))
                os.environ["GITHUB_RUN_ID"] = str(100 + attempt)
                with self.assertRaises((RuntimeError, ValueError)):
                    runner.prepare(root, session, output)
                self.assertFalse(output.exists())
            self.assertEqual(compile.call_count, 1)
            self.assertEqual([item[0].claim_ref for item in calls], [runner.CONSUMPTION_REF] * 2)
            self.assertNotEqual(calls[0][1]["execution_owner"], calls[1][1]["execution_owner"])

    def test_consumption_cannot_be_reused_by_a_different_workflow_owner(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            output = root / runner.OUTPUT_RELATIVE
            output.mkdir(parents=True)
            (output / "execution-consumption.json").write_text(json.dumps({"wrong": "owner"}))
            authority = {"source": "1" * 40, "remote_receipt": {"claim_commit_sha": "2" * 40}}
            env = {"GITHUB_RUN_ID": "101", "GITHUB_RUN_ATTEMPT": "1", "GITHUB_JOB": "fixture", "RUNNER_NAME": "fixture-host"}
            with patch.dict(os.environ, env), patch.object(runner, "GitHubExecutionAPI") as api:
                with self.assertRaisesRegex(ValueError, "exact executor"):
                    runner.validate_consumption(root, output, authority)
                api.assert_not_called()

    def test_alternate_claim_namespace_cannot_create_another_r1_attempt(self):
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            claim = root / runner.CLAIM_PATH
            claim.parent.mkdir(parents=True)
            claim.write_text('{}')
            receipt = root / "receipt.json"
            session = {"protocol_id": runner.PROTOCOL_ID, "claim_path": runner.CLAIM_PATH,
                "remote_claim_receipt_path": receipt.name}
            for wrong in (runner.CLAIM_REF + "-second", "refs/heads/other/official-attempts/r1"):
                receipt.write_text(json.dumps({"claim_ref": wrong, "claim_path": runner.CLAIM_PATH}))
                with patch.object(runner, "GitHubExecutionAPI") as api:
                    with self.assertRaisesRegex(ValueError, "single frozen R1 claim namespace"):
                        runner.validate_authority(root, session)
                    api.assert_not_called()

    def test_boolean_alias_is_not_an_exact_integer_in_a_frozen_request(self):
        self.assertFalse(runner.same_json({"startingPlayer": True}, {"startingPlayer": 1}))
        self.assertFalse(runner.same_json({"row": 1.0}, {"row": 1}))

    def test_immutable_git_reads_require_publication_then_recheck_live_refs_each_phase(self):
        with tempfile.TemporaryDirectory() as raw:
            base = Path(raw)
            remote, root = base / "remote.git", base / "checkout"
            def command(*args, cwd=None):
                return subprocess.check_output(["git", *args], cwd=cwd, text=True, stderr=subprocess.DEVNULL).strip()
            command("init", "--bare", "--quiet", str(remote))
            command("init", "--quiet", str(root))
            command("remote", "add", "origin", str(remote), cwd=root)
            (root / "evidence.json").write_text('{"fixture":"published"}')
            command("add", "evidence.json", cwd=root)
            command("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "fixture", cwd=root)
            published = command("rev-parse", "HEAD", cwd=root)
            command("push", "-q", "origin", "HEAD:refs/heads/fixture/source", cwd=root)
            publication = Mock()
            def published_object(method, path, payload=None):
                commit = path.rsplit("/", 1)[-1]
                lines = command("--git-dir", str(remote), "show", "-s", "--format=%H%n%T%n%P", commit).splitlines()
                return {"sha": lines[0], "tree": {"sha": lines[1]},
                    "parents": [{"sha": value} for value in (lines[2].split() if len(lines) > 2 else [])]}
            publication.request.side_effect = published_object
            with self.assertRaisesRegex(ValueError, "canonical repository endpoint"), \
                    patch.object(runner, "GitHubExecutionAPI", return_value=publication):
                runner.GitImmutableReadAPI(root, "fixture/source", require_publication=True)
            endpoint_patch = patch.object(runner, "GIT_REMOTE", str(remote))
            endpoint_patch.start()
            self.addCleanup(endpoint_patch.stop)
            with patch.object(runner, "GitHubExecutionAPI", return_value=publication):
                api = runner.GitImmutableReadAPI(root, "fixture/source", require_publication=True)
            with patch.object(runner.subprocess, "run", wraps=subprocess.run) as processes:
                for _ in range(2):
                    contents = api.request("GET", "/contents/evidence.json?ref=" + published)
                    self.assertEqual(base64.b64decode(contents["content"]), (root / "evidence.json").read_bytes())
                fetches = [call for call in processes.call_args_list if call.args[0][:2] == ["git", "fetch"]]
                self.assertEqual(len(fetches), 0)
                self.assertEqual(publication.request.call_count, 1, "one remote verification is required even for a local object")
            api.assert_live_unchanged()
            with self.assertRaisesRegex(ValueError, "never creates"):
                api.request("POST", "/git/refs", {})
            (root / "evidence.json").write_text('{"fixture":"local only"}')
            command("add", "evidence.json", cwd=root)
            command("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "local only", cwd=root)
            local_only = command("rev-parse", "HEAD", cwd=root)
            with self.assertRaises(subprocess.CalledProcessError):
                api.request("GET", "/contents/evidence.json?ref=" + local_only)
            command("push", "-q", "origin", "HEAD:refs/heads/fixture/source", cwd=root)
            with self.assertRaisesRegex(ValueError, "moved during"):
                api.assert_live_unchanged()
            successor = runner.GitImmutableReadAPI(root, "fixture/source", require_publication=False)
            self.assertEqual(successor.request("GET", "/git/ref/heads/fixture/source")["object"]["sha"], local_only)
            with patch.object(runner.subprocess, "run", wraps=subprocess.run) as processes:
                successor.request("GET", "/contents/evidence.json?ref=" + published)
                self.assertFalse(any(call.args[0][:2] == ["git", "fetch"] for call in processes.call_args_list))
            successor.assert_live_unchanged()


if __name__ == "__main__":
    unittest.main()
