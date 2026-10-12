"""Excluded static Kotlin integration checks. Never execute the adapter/engine."""
from pathlib import Path
import unittest
import gameplay_authority as AUTH

ROOT=Path(__file__).resolve().parents[2]
ADAPTER=ROOT/'gym/src/test/kotlin/com/wingedsheep/gym/PlayFirstIsolatedPestMonsterTronTest.kt'
RECEIVER=ROOT/'scripts/play-first/strict_receiving.py'

class PositiveAuthorityParityTests(unittest.TestCase):
    def setUp(self):
        self.kotlin=ADAPTER.read_text();self.python=RECEIVER.read_text()
    def test_neither_path_uses_authorization_comment_denylist(self):
        self.assertNotRegex(self.kotlin,r'authorizationComment\s*!in\s*setOf')
        self.assertNotRegex(self.python,r"case\['authorizationComment'\]\s+not in")
    def test_paths_separate_live_release_from_historical_at_execution_gate(self):
        self.assertIn('freeze_intent, verify_historical',self.python)
        self.assertIn('authority = verify_historical(',self.python)
        self.assertIn('ProcessBuilder("python3", "-I", "scripts/play-first/gameplay_authority.py", "verify-release",',self.kotlin)
        self.assertNotIn('verify_record(',self.python)
    def test_adapter_gate_precedes_any_original_slot_or_engine_initialization(self):
        gate=self.kotlin.index('check(gate.exitValue() == 0)')
        for marker in ('PlayFirstTimingJournal(', 'CardRegistry()', 'GameEnvironment.create(', 'env.reset('):
            self.assertLess(gate,self.kotlin.index(marker))
    def test_receiver_adapter_trace_contract_matches(self):
        self.assertIn('put("authorityContract", "'+AUTH.CONTRACT+'")',self.kotlin)
        self.assertIn('put("authorityDecisionBodySha256", authority.getValue("decisionBodySha256").jsonPrimitive.content)',self.kotlin)
        self.assertIn("start.get('authorityContract') == AUTHORITY_CONTRACT",self.python)
        self.assertIn("start.get('authorityDecisionBodySha256') == authority['decisionBodySha256']",self.python)
        self.assertIn('put("authorityReleaseBodySha256", authority.getValue("releaseBodySha256").jsonPrimitive.content)',self.kotlin)
        self.assertIn("start.get('authorityReleaseBodySha256') == authority['releaseBodySha256']",self.python)
    def test_missing_external_record_and_context_have_no_environment_fallback(self):
        self.assertIn('?: error("Missing external positive authority record")',self.kotlin)
        self.assertIn('?: error("Missing exact externally approved run context")',self.kotlin)
        self.assertIn('check(gate.exitValue() == 0)',self.kotlin)
        self.assertIn('"TRUSTED_EXTERNAL_LAUNCH_RELEASE"',self.kotlin)
        self.assertIn('"RUN_SPECIFIC_RELEASE_VERIFIED"',self.kotlin)
        self.assertIn('check(it.getValue("engineAllowed").jsonPrimitive.boolean)',self.kotlin)
    def test_trace_source_parent_matches_accepted_production_parent(self):
        self.assertIn('put("sourceParent", "'+AUTH.SOURCE_PARENT+'")',self.kotlin)
    def test_both_paths_explicitly_refuse_g08_and_b01(self):
        self.assertIn('gameId != "G08"',self.kotlin)
        self.assertIn("case['gameId'] != 'G08'",self.python)
        self.assertIn('!blockId.contains("B01")',self.kotlin)
        self.assertIn("'B01' not in case['blockId']",self.python)
    def test_adapter_binds_platform_context_and_prevents_case_race(self):
        for name in ('GITHUB_RUN_ID','GITHUB_SHA','GITHUB_REF','GITHUB_RUN_NUMBER'):
            self.assertIn('System.getenv("'+name+'")',self.kotlin)
        self.assertIn('Files.readAllBytes(casePath).contentEquals(bytes)',self.kotlin)
    def test_no_test_transport_cli_switch_or_script_override_exists(self):
        helper=(ROOT/'scripts/play-first/gameplay_authority.py').read_text()
        self.assertNotIn("parser.add_argument('--fixture",helper)
        self.assertNotIn("parser.add_argument('--client",helper)
        self.assertNotIn('PLAY_FIRST_AUTHORITY_VERIFIER',self.kotlin)
    def test_case_intent_schema_excludes_future_comment_and_run_ids(self):
        declaration=self.kotlin[self.kotlin.index('require(input.keys'):self.kotlin.index('check(input.getValue("scope")')]
        self.assertNotIn('authorizationComment',declaration)
        self.assertNotIn('runId',declaration)
        self.assertIn('val authorizationComment = authority.getValue("claims")',self.kotlin)
        self.assertIn('"ARGENTUM_GAMEPLAY_INTENT_V2"',self.kotlin)
    def test_production_has_no_trusted_backend_and_cli_fails_closed(self):
        helper=(ROOT/'scripts/play-first/gameplay_authority.py').read_text()
        self.assertIn('_trusted=None',helper)
        self.assertIn('_anchor=None',helper)
        self.assertIn('result=verify_release(data,run,record)',helper)
        self.assertIn('result=verify_historical(data,run,record,args.manifest_sha256)',helper)
    def test_authority_gate_precedes_create_new_original(self):
        self.assertLess(self.kotlin.index('check(gate.exitValue() == 0)'),self.kotlin.index('}.toString() + "\\n", CREATE_NEW)'))

if __name__=='__main__':unittest.main()
