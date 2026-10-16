"""Explicitly invoked V6.1 offline mutation-killing harness. Never executes helper ELFs.

Copies only importable Python and JSON fixtures into temporary directories; it
never launches V6 runtime functions or GitHub/systemd/cgroup integrations.
"""
from pathlib import Path
import hashlib, json, os, shutil, subprocess, sys, tempfile

HERE=Path(__file__).resolve().parent
FILES=("v6_adapter.py","v6_contract.py","kernel_supervisor.py", "preflight.py",
       "memory_collector.py", "jvm_evidence.py",
       "v6-unit-contract.json", "v61_scope.py","v61_observer.py",
       "v61_watchdog.py","v61_gate.py","test_v61_source_contracts.py")
# (label, source file, source snippet, malicious replacement, detecting focused test)
MUTATIONS=(
 ("pid1-spoof","v61_scope.py",'m["owner_pid"]==1', 'm["owner_pid"]>=1',
  'test_v61_source_contracts.ManagerTests.test_pid1_or_bus_spoof'),
 ("acl-bypass","v61_scope.py",'x.get("acl")==[]','x.get("acl") is not None',
  'test_v61_source_contracts.ManagerTests.test_inode_acl_mode_and_mount'),
 ("lost-events","v61_observer.py",'c.get("lost")!=0','c.get("lost")<0',
  'test_v61_source_contracts.ObserverTests.test_ring_drop_and_cpu_gap'),
 ("wrong-target-D","v61_observer.py",'a.get("target") != bind["s"]','a.get("target") != bind["d"]',
  'test_v61_source_contracts.ObserverTests.test_wrong_migration_target_s_vs_d'),
 ("allow-ENOENT","v61_observer.py",'raw not in (-1,-13)','raw not in (-1,-2,-13)',
  'test_v61_source_contracts.ObserverTests.test_sock_family_and_raw_errno'),
 ("StopUnit-name","v61_watchdog.py",'r.get("StopUnit_by_name") is not False','r.get("StopUnit_by_name") is True and False',
  'test_v61_source_contracts.WatchdogTests.test_never_stopunit_or_numeric_pid_fallback'),
 ("unit-reuse","v61_watchdog.py",'r.get("no_unit_name_reuse_window") is not True','r.get("no_unit_name_reuse_window") is False and False',
  'test_v61_source_contracts.WatchdogTests.test_reused_unit_and_invocation_stale'),
 ("compiler-open","v61_gate.py",'"compiler_enabled":False','"compiler_enabled":True',
  'test_v61_source_contracts.IntegrationTests.test_consistent_three_models_never_unlock')
)

def main():
    if len(sys.argv)!=2:raise SystemExit("usage: v61_mutation_runner.py OUTPUT_DIR")
    dest=Path(sys.argv[1]);dest.mkdir(parents=True,exist_ok=True)
    baseline={name:hashlib.sha256((HERE/name).read_bytes()).hexdigest() for name in FILES}
    results=[]
    for label,source,original,replacement,test in MUTATIONS:
        with tempfile.TemporaryDirectory(prefix='argentum-v61-mutant-') as td:
            root=Path(td)
            for file in FILES:shutil.copyfile(HERE/file,root/file)
            s=(root/source).read_text()
            if s.count(original)!=1:raise RuntimeError(f'not exactly one mutant site: {label} occurrences={s.count(original)}')
            (root/source).write_text(s.replace(original,replacement,1))
            env={**os.environ,'PYTHONDONTWRITEBYTECODE':'1'}
            p=subprocess.run([sys.executable,'-B','-m','unittest','-v',test],cwd=root,
                             capture_output=True,text=True,timeout=12,env=env)
            log=(p.stdout+'\n'+p.stderr)
            (dest/(label+'.log')).write_text(log)
            # A test failure attributed to this mutant is expected; an import,
            # timeout or other internal harness error cannot count as killed.
            killed=p.returncode!=0 and ('FAIL: '+test.split('.')[-1] in log or
                                        'AssertionError:' in log) and 'Ran 1 test' in log
            results.append({'mutant':label,'source_file':source,'expected_detection':test,
                            'exit_code':p.returncode,'killed':killed,
                            'log_sha256':hashlib.sha256(log.encode()).hexdigest()})
    unchanged=all(hashlib.sha256((HERE/n).read_bytes()).hexdigest()==h for n,h in baseline.items())
    receipt={'schema':'ARGENTUM_V61_SYNTHETIC_MUTATION_RECEIPT_1',
             'mutants':results,'detected':sum(r['killed'] for r in results),
             'total':len(results),'source_unchanged':unchanged,
             'helper_elf_executions':0,'privileged_calls':0,'hosted_calls':0,
             'runtime_qualification':False,'historical_C4':'INCOMPLETE'}
    (dest/'mutation-results.json').write_text(json.dumps(receipt,sort_keys=True,indent=2)+'\n')
    print(json.dumps({'detected':receipt['detected'],'total':len(results),'source_unchanged':unchanged}))
    if not unchanged or receipt['detected']!=len(results):raise SystemExit(1)

if __name__=='__main__':main()
