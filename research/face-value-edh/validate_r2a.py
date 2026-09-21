#!/usr/bin/env python3
import json, pathlib
p=pathlib.Path(__file__).with_name("r2-a-contract.json")
x=json.loads(p.read_text())
assert x["protocol"]=="FVEDH-R2-A-v1"
assert x["deck_identity_sha256"]=="9a29093306b92cab815112c61b2401a1b5e48ac418b87d50ee263b4fd2b402ca"
assert x["phase"]=="synthetic_readiness"
assert x["official_seed_vector"] is None
assert x["official_outcomes_exposed"]==0
assert x["historical_vector_access"]=="forbidden"
assert x["fail_closed"] is True
assert set(x["required_surfaces"])=={"ordinary_cast","targeted_interaction","stack_priority","commander_unavailable","food_chain_pressure"}
r=x["runner_rules"]
assert r=={"deterministic_same_input":True,"unsupported_action":"abort","illegal_action":"reject","future_information":"forbidden","outcome_adaptive_policy":"forbidden","deck_mutation":"forbidden"}
print("R2_A_CONTRACT_PASS")
