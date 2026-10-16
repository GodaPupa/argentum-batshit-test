"""Excluded LOCAL-ONLY V6.3 mutant testing. Runs no host capture or workflow."""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

BASE = Path(__file__).resolve().parent
CASE = [
    ("repository-spoof", 'EXPECTED_REPOSITORY = "GodaPupa/argentum-batshit-test"',
      'EXPECTED_REPOSITORY = "evil/repo"', "test_untrusted_repository"),
    ("event-change", '"GITHUB_EVENT_NAME": "push"',
      '"GITHUB_EVENT_NAME": "workflow_dispatch"', "test_workflow_dispatch_event"),
    ("branch-bypass", 'EXPECTED_BRANCH = "infra/argentum-c4-v63-readonly-once-20261015"',
      'EXPECTED_BRANCH = "main"', "test_wrong_ref"),
    ("second-original", '"GITHUB_RUN_NUMBER": "1"',
      '"GITHUB_RUN_NUMBER": "2"', "test_second_run_forbidden"),
    ("rerun-enabled", '"GITHUB_RUN_ATTEMPT": "1"',
      '"GITHUB_RUN_ATTEMPT": "2"', "test_rerun_forbidden"),
    ("self-hosted-allowed", '"RUNNER_ENVIRONMENT": "github-hosted"',
      '"RUNNER_ENVIRONMENT": "self-hosted"', "test_self_hosted_not_allowed"),
    ("dependency-hash-skipped", 'if hashlib.sha256(data).hexdigest() != V62_MODULE_SHA256:',
      'if False:', "test_dependency_exact_bytes"),
    ("compiler-open", '"compile_enabled": False,',
      '"compile_enabled": True,', "test_all_green_remains_blocked"),
    ("source-falsely-authenticated", '"source_authenticated": False,',
      '"source_authenticated": True,', "test_all_green_hints_cannot_authenticate"),
    ("workflow-identity-relaxed", '"GITHUB_WORKFLOW_REF": EXPECTED_WORKFLOW_REF,',
      '"GITHUB_WORKFLOW_REF": "attacker@refs/heads/main",', "test_wrong_workflow_ref"),
]


def main():
    original=(BASE/"v63_target_runner.py").read_bytes()
    before=hashlib.sha256(original).hexdigest()
    results=[]
    for label,old,new,test in CASE:
        assert original.decode().count(old)==1, label
        with tempfile.TemporaryDirectory(prefix="argentum-v63-mutant-") as td:
            t=Path(td)
            for name in ("test_v63_target_runner.py","v62_host_capability.py",
                         "V63_INACTIVE_WORKFLOW.yml.template","V63_TARGET_HOST_SCOPE.md"):
                shutil.copyfile(BASE/name,t/name)
            (t/"v63_target_runner.py").write_text(original.decode().replace(old,new,1))
            command=[sys.executable,"-I","-B","-m","unittest",
                     "discover","-s",str(t),"-p","test_v63_target_runner.py","-v"]
            env=os.environ.copy()
            env.pop("PYTHONPATH",None)
            proc=subprocess.run(command,cwd=t,env=env,stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE,timeout=30,check=False)
            # Source mutation must fail focused tests, never be counted as a PASS.
            killed=proc.returncode!=0 and (b"FAIL" in proc.stderr or b"ERROR" in proc.stderr)
            results.append({"mutant":label,"killed":killed,"exit":proc.returncode,
                            "test":test,"stderr_tail":proc.stderr.decode(errors="replace")[-550:]})
    after=hashlib.sha256((BASE/"v63_target_runner.py").read_bytes()).hexdigest()
    report={"contract":"ARGENTUM_V63_LOCAL_MUTATION_EVIDENCE_V1",
            "source_sha256_before":before,"source_sha256_after":after,
            "source_untouched":before==after,"mutations":results,
            "killed":sum(1 for x in results if x["killed"]),"total":len(CASE),
            "no_host_capture_or_privileged_execution":True}
    print(json.dumps(report,indent=2,sort_keys=True))
    return 0 if report["killed"]==len(CASE) and before==after else 1


if __name__=="__main__":
    raise SystemExit(main())
