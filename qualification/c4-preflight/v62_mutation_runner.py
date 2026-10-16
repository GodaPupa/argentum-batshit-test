"""V6.2 local mutation detection only. Copies new Python sources to temp dirs.

Never imports/calls privileged/hosted paths or executes any packaged ELF.
"""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE=Path(__file__).resolve().parent
TARGET=HERE/"v62_host_capability.py"
TEST=HERE/"test_v62_host_capability.py"
CASES={
    "runtime-open": ('"runtime_enforcement_qualified": False', '"runtime_enforcement_qualified": True', 'test_default_no_authority_even_all_green'),
    "compiler-open": ('"compile_enabled": False', '"compile_enabled": True', 'test_default_no_authority_even_all_green'),
    "d2-open": ('"D2_qualified": False', '"D2_qualified": True', 'test_default_no_authority_even_all_green'),
    "target-spoof": ('"target_github_runner_identified": False', '"target_github_runner_identified": True', 'test_default_no_authority_even_all_green'),
    "dbus-source-spoof": ('"pid1_dbus_unique_owner": "NOT_QUERIED_NO_CONNECTION"', '"pid1_dbus_unique_owner": "VERIFIED"', 'test_manager_never_gets_dbus_authority_from_socket'),
    "inode-source-spoof": ('"kernel_fd_inode_resolution": "NOT_ESTABLISHED"', '"kernel_fd_inode_resolution": "VERIFIED"', 'test_observer_btf_and_perm_flags_not_backend'),
    "watchdog-source-spoof": ('"externally_armed_controller": "NOT_IMPLEMENTED"', '"externally_armed_controller": "ARMED"', 'test_watchdog_cgroup_kill_metadata_does_not_grant_termination'),
    "historical-c4-lie": ('"historical_C4": "INCOMPLETE"', '"historical_C4": "PASS"', 'test_default_no_authority_even_all_green'),
}

def main():
    original=TARGET.read_bytes()
    digest=hashlib.sha256(original).hexdigest()
    result=[]
    for name,(before,after,test) in CASES.items():
        source=original.decode()
        if source.count(before)!=1:
            raise RuntimeError(f"mutant {name}: replacement not uniquely present")
        with tempfile.TemporaryDirectory(prefix="argentum-v62-mutation-") as td:
            dest=Path(td)
            for filename in ("test_v62_host_capability.py", "v61_gate.py", "v61_scope.py", "v61_observer.py", "v61_watchdog.py", "v6_adapter.py", "preflight.py"):
                shutil.copy2(HERE/filename, dest/filename)
            (dest/"v62_host_capability.py").write_text(source.replace(before,after))
            cp=subprocess.run([sys.executable,"-B",str(dest/"test_v62_host_capability.py"),
                  "CapabilityTests."+test],cwd=dest,text=True,capture_output=True,timeout=15)
            # Required targeted test should fail its assertion: test errors are not a valid kill.
            detected=cp.returncode!=0 and "FAIL:" in cp.stderr and "ERROR:" not in cp.stderr
            result.append({"case":name,"detected":detected,"return_code":cp.returncode,
                           "test":test,"assertion_failure": "FAIL:" in cp.stderr})
    if hashlib.sha256(TARGET.read_bytes()).hexdigest()!=digest:
        raise AssertionError("Frozen source mutated by harness")
    print(json.dumps({"contract":"ARGENTUM_C4_V62_MUTATION_EVIDENCE_V1",
          "source_sha256":digest,"mutants":result,"killed":sum(x["detected"] for x in result),
          "total":len(result),"original_source_unchanged":True},indent=2,sort_keys=True))
    if not all(x["detected"] for x in result):
        raise SystemExit(1)

if __name__=="__main__":
    main()
