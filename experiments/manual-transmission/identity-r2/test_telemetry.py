#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import unittest

SCRIPT=Path(__file__).with_name("telemetry.py")
spec=importlib.util.spec_from_file_location("identity_r2",SCRIPT)
mod=importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
class DescriptiveTelemetryTests(unittest.TestCase):
 def dummy(self):
  p=mod.PROFILE
  return {"status":"COMPLETE","gameOver":True,"error":None,"prefixOnly":False,
   "seed":202610191621,"arm":"control","accepted":64,"submitted":64,
   "winner":"manual-mirror-0","profile":p,"profilesBySeat":[p]*4,
   "commanderCasts":{},"firstCommanderPersonalTurn":{},"legalCommanderOpportunityDecisions":{},
   "legalCommanderDeclinedDecisions":{}}
 def test_valid_original_summary(self):
  mod.validate_summary(self.dummy(),"202610191621","control")
 def test_censored_is_not_win(self):
  s=self.dummy();s["status"]="CENSORED_ACTION_BUDGET"
  with self.assertRaisesRegex(ValueError,"censored"):mod.validate_summary(s,"202610191621","control")
 def test_action_examples_not_policy_modes(self):
  rows=[{"sourceName":"World Breaker","action":json.dumps({"type":"CastSpell"})},
        {"sourceName":"Walking Ballista","action":json.dumps({"type":"CastSpell"})},
        {"sourceName":"Ancestral Statue","action":json.dumps({"type":"ActivateAbility"})}]
  x=mod.classify_action_names(rows)
  self.assertEqual(x["Sport-example-toolbox-casts"]["total_casts"],1)
  self.assertEqual(x["Race-example-combo-casts"]["total_casts"],1)
  self.assertNotIn("gear",x)
 def test_divergence_does_not_claim_cause(self):
  a=[{"index":1,"actor":"p0","sourceName":None,"action":"a"}]
  b=[{"index":1,"actor":"p0","sourceName":None,"action":"b"}]
  self.assertEqual(mod.first_divergence(a,b),1)
  self.assertIsNone(mod.first_divergence(a,a))
if __name__=="__main__":unittest.main()
