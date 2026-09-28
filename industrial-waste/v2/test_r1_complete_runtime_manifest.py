from __future__ import annotations
import copy, json, pathlib, sys, unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from r1_complete_runtime_manifest import *

Z="0"*64
C="1"*40

def valid():
    v={
      "schema":SCHEMA,"source_commit":C,"source_tree":"2"*40,
      "build_inputs":[{"path":"gradlew","length":1,"sha256":"3"*64}],
      "dependency_graph":{"nodes":[{"id":"worker","kind":"archive","path":"worker.jar","length":10,"sha256":"4"*64}],"edges":[]},
      "process_paths":[{"process":"worker","loader":"app","ordinal":0,"kind":"archive","path":"worker.jar","length":10,"sha256":"4"*64}],
      "inventories":[{"artifact_id":"worker","kind":"archive","path":"worker.jar","length":10,"sha256":"4"*64,
        "members":[{"path":"a/A.class","kind":"class","length":3,"sha256":"5"*64,"loader":"app"}]}],
      "platform":{"jdk_digest":"6"*64,"os_image_digest":"7"*64,"architecture":"x86_64","python_digest":"8"*64,"shell_digest":"9"*64,"just_digest":"a"*64},
      "launch":{"working_directory":"repo","commands":[["just","test-class","Worker"]],"allowed_environment":{"LANG":"C.UTF-8"}},
      "loader_policy":{"allowed_loaders":["app"],"generated_class_policy":"ELIMINATED","agents_allowed":False,"dynamic_attach_allowed":False,"runtime_compilation_allowed":False},
      "manifest_digest":Z
    }
    v["manifest_digest"]=sha256_bytes(canonical_bytes(v))
    return v

class CompleteRuntimeManifestTest(unittest.TestCase):
    def bad(self,mut):
        v=valid(); mut(v)
        with self.assertRaises(ValueError): validate_manifest(v)

    def test_valid_canonical_tiny_manifest(self):
        self.assertEqual(validate_manifest(valid())["build_inputs"],1)

    def test_unknown_top_level_rejected(self):
        self.bad(lambda v:v.__setitem__("extra",1))

    def test_duplicate_json_key_rejected(self):
        with self.assertRaisesRegex(ValueError,"duplicate JSON key"):
            loads_strict('{"schema":"x","schema":"y"}')

    def test_bad_digest_rejected(self):
        self.bad(lambda v:v["build_inputs"][0].__setitem__("sha256","BAD"))

    def test_duplicate_input_rejected(self):
        self.bad(lambda v:v["build_inputs"].append(copy.deepcopy(v["build_inputs"][0])))

    def test_unknown_edge_rejected(self):
        self.bad(lambda v:v["dependency_graph"]["edges"].append({"from":"worker","to":"missing"}))

    def test_noncontiguous_order_rejected(self):
        self.bad(lambda v:v["process_paths"][0].__setitem__("ordinal",1))

    def test_duplicate_member_rejected(self):
        self.bad(lambda v:v["inventories"][0]["members"].append(copy.deepcopy(v["inventories"][0]["members"][0])))

    def test_duplicate_same_loader_binary_rejected(self):
        def m(v):
            second=copy.deepcopy(v["inventories"][0]); second["artifact_id"]="worker2"; second["path"]="worker2.jar"; second["sha256"]="b"*64
            v["inventories"].append(second)
        self.bad(m)

    def test_undeclared_loader_rejected(self):
        self.bad(lambda v:v["inventories"][0]["members"][0].__setitem__("loader","hidden"))

    def test_forbidden_environment_rejected(self):
        self.bad(lambda v:v["launch"]["allowed_environment"].__setitem__("JAVA_TOOL_OPTIONS","-javaagent:x"))

    def test_dynamic_loading_rejected(self):
        self.bad(lambda v:v["loader_policy"].__setitem__("agents_allowed",True))

    def context(self):
        return AttestationContext(valid()["manifest_digest"],C,"owner","worker-1",7,frozenset({"used"}))

    def att(self):
        return {"schema":ATTESTATION_SCHEMA,"manifest_digest":valid()["manifest_digest"],"source_commit":C,
          "worker_id":"worker-1","execution_owner":"owner","nonce":"fresh","issued_sequence":7,
          "process_paths_digest":"c"*64,"inventory_digest":"d"*64}

    def test_wrong_worker_attestation_rejected(self):
        a=self.att();a["worker_id"]="worker-2"
        with self.assertRaises(ValueError): validate_attestation(a,self.context())

    def test_replayed_nonce_rejected(self):
        a=self.att();a["nonce"]="used"
        with self.assertRaises(ValueError): validate_attestation(a,self.context())

    def test_stale_sequence_rejected(self):
        a=self.att();a["issued_sequence"]=6
        with self.assertRaises(ValueError): validate_attestation(a,self.context())

if __name__=="__main__":
    unittest.main(verbosity=2)
