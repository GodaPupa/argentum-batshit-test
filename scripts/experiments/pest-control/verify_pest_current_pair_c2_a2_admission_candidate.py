#!/usr/bin/env python3
import json,pathlib,subprocess,sys

PHASE_B_I="5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd"
PHASE_B_TREE="e7c37f620c8cefa0352742e38635ca196e887637"
EXPECTED_DELTA=sorted([
 ".github/workflows/pest-c2-a2-admission-candidate-validate-20261005.yml",
 "docs/experiments/pest-control/PEST_CURRENT_PAIR_C2_A2_ADMISSION_CANDIDATE_20261005.json",
 "scripts/experiments/pest-control/verify_pest_current_pair_c2_a2_admission_candidate.py",
])

def git(*a): return subprocess.check_output(["git",*a],text=True).strip()

def main():
 if len(sys.argv)!=2: raise SystemExit("manifest required")
 m=json.loads(pathlib.Path(sys.argv[1]).read_text())
 assert m["schema"]=="pest-current-pair-c2-a2-admission-candidate-v1"
 assert m["authority"]=="SOURCE_GATE_CANDIDATE_ONLY__NO_GAMEPLAY_AUTHORITY"
 impl=m["implementation"]
 assert git("rev-parse","HEAD^")==impl["commit"]
 assert git("rev-parse","HEAD^^")==PHASE_B_I
 assert git("rev-parse",impl["commit"]+"^{tree}")==impl["tree"]
 assert sorted(git("diff","--name-only",impl["commit"]+"..HEAD").splitlines())==EXPECTED_DELTA
 pb=m["phase_b_v5_2_terminal"]
 assert pb["implementation_commit"]==PHASE_B_I and pb["implementation_tree"]==PHASE_B_TREE
 assert pb["run"]==37258189059 and pb["aggregate_artifact"]==11328302918
 assert pb["aggregate_result_json_sha256"]=="116bd630d563e9095ecda45ca6aa2e70191f20528fc581d89e6aaee635fab8a5"
 assert pb["global_plan_sha256"]=="bcc577b1596c7b37b7f14f0c70d68fefafcf569de3b74f561a0b8317e6e1cc71"
 assert pb["completed_rows"]==34912840 and pb["shard_count"]==32
 assert pb["mismatches"]==pb["unknowns"]==pb["exceptions"]==0
 expected={
  "C2P01":"SATISFIED_BY_PHASE_B_V5_2_TERMINAL_PARITY",
  "C2P02":"SATISFIED_BY_PHASE_B_V5_2_TERMINAL_PARITY",
  "C2P03":"SATISFIED_CURRENT_FINITE_PAIR_ACTION_PAYMENT_SURFACE",
  "C2P04":"SATISFIED_CURRENT_FINITE_PAIR_PENDING_CHOICE_SURFACE",
  "C2P05":"SATISFIED_CURRENT_PAIR_COMBAT_POSTBLOCK_SURFACE",
  "C2P06":"SATISFIED_CURRENT_WHOLE_PAIR_HIDDEN_STATE_INVARIANCE",
  "C2P07":"SATISFIED_CURRENT_PAIR_SOURCE_BOUND_FRESH_REPLAY",
 }
 assert m["gate_dispositions"]==expected
 required_evidence={"phase_b","c2p03_p04","c2p05","c2p06","c2p07"}
 assert required_evidence==set(m["evidence"])
 for name,e in m["evidence"].items():
  assert type(e["run"]) is int and e["run"]>0
  assert type(e["artifact"]) is int and e["artifact"]>0
  assert e["artifact_digest"].startswith("sha256:") and len(e["artifact_digest"])==71
  assert len(e["head"])==40 and all(c in "0123456789abcdef" for c in e["head"])
 for p,b in m["protected_current_source_blobs"].items():
  actual=git("rev-parse","HEAD:"+p)
  assert actual==b,(p,actual,b)
 assert m["official_counters"]=={"allocations":0,"claims":0,"games":0,"outcomes":0}
 assert m["monster_tron_activation"]=="ABSENT_NOT_AUTHORIZED"
 assert m["phase_b_authority"]=="PERMANENTLY_CONSUMED_NO_REUSE"
 print(json.dumps({
  "schema":"pest-current-pair-c2-a2-admission-candidate-verifier-v1",
  "status":"PASS",
  "head":git("rev-parse","HEAD"),
  "tree":git("rev-parse","HEAD^{tree}"),
  "implementation_commit":impl["commit"],
  "implementation_tree":impl["tree"],
  "closed_gates":7,
  "protected_current_source_blobs":len(m["protected_current_source_blobs"]),
  "official_counters_delta":0,
 },sort_keys=True))

if __name__=="__main__": main()
