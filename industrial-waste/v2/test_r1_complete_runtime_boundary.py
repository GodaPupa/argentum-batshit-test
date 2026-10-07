#!/usr/bin/env python3
import os,tempfile,unittest,zipfile
from pathlib import Path
import r1_complete_runtime_boundary as B

class CompleteRuntimeBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.t=tempfile.TemporaryDirectory()
        self.r=Path(self.t.name)
        self.env={"LANG":"C.UTF-8","TZ":"UTC"}
        cats=[("build","BUILD_INPUT"),("store","DEPENDENCY_STORE"),("process","PROCESS_PATH"),("jdk","JDK"),
              ("native","NATIVE"),("python","PYTHON"),("launcher","LAUNCHER"),("resource","RESOURCE")]
        roots=[]
        for i,(name,category) in enumerate(cats):
            d=self.r/name
            d.mkdir()
            (d/"x.txt").write_text(name,encoding="utf-8")
            roots.append({"logical_name":name,"path":name,"category":category,"kind":"directory","loader_id":"app","ordinal":i})
        self.spec={
            "schema":"industrial-r1-complete-runtime-input-spec-v1",
            "authority":"PROSPECTIVE_EXPECTED_BUILD_INPUT_ONLY",
            "rule":B.RULE,
            "source":{"commit":"1"*40,"tree":"2"*40},
            "platform":{"container_image_digest":"sha256:"+"3"*64,"architecture":"linux/amd64","jdk_feature":21},
            "allowed_environment":self.env,
            "loader_topology":[
                {"id":"boot","parent":None,"delegation":"BOOTSTRAP"},
                {"id":"app","parent":"boot","delegation":"PARENT_FIRST"},
            ],
            "inventory_roots":roots,
            "generated_prelaunch":[],
            "ordered_processes":[
                {"role":"worker","ordinal":0,"executable_root":"process","argv":["java"],"cwd":"work",
                 "classpath":["resource"],"module_path":[],"loader_id":"app"}
            ],
            "duplicate_binary_resolution":[],
        }

    def tearDown(self):
        self.t.cleanup()

    def test_inventory_has_no_capture_membership_or_closure_authority(self):
        manifest,digest=B.build_inventory_candidate(self.spec,self.r,self.env)
        self.assertFalse(manifest["complete_closure"])
        self.assertFalse(manifest["receiving_capture_membership_used"])
        self.assertEqual(len(digest),64)

    def test_missing_category_fails(self):
        self.spec["inventory_roots"]=self.spec["inventory_roots"][:-1]
        with self.assertRaisesRegex(B.BoundaryError,"incomplete closure"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_environment_fails_closed(self):
        with self.assertRaisesRegex(B.BoundaryError,"environment mismatch"):
            B.build_inventory_candidate(self.spec,self.r,{**self.env,"HOME":"/tmp"})
        self.spec["allowed_environment"]={"PATH":"/bin"}
        with self.assertRaisesRegex(B.BoundaryError,"forbidden semantic"):
            B.build_inventory_candidate(self.spec,self.r,{"PATH":"/bin"})

    def test_duplicate_zip_member_fails(self):
        archive=self.r/"duplicate.jar"
        with zipfile.ZipFile(archive,"w") as out:
            out.writestr("A.class",b"a")
            out.writestr("A.class",b"b")
        self.spec["inventory_roots"][7]={
            "logical_name":"resource","path":"duplicate.jar","category":"RESOURCE",
            "kind":"archive","loader_id":"app","ordinal":7
        }
        with self.assertRaisesRegex(B.BoundaryError,"duplicate ZIP"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_multi_release_and_duplicate_resolution(self):
        archive=self.r/"multi.jar"
        with zipfile.ZipFile(archive,"w") as out:
            out.writestr("META-INF/MANIFEST.MF",b"Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n")
            out.writestr("pkg/A.class",b"base")
            out.writestr("META-INF/versions/17/pkg/A.class",b"v17")
            out.writestr("META-INF/versions/22/pkg/A.class",b"v22")
        self.spec["inventory_roots"][7]={
            "logical_name":"resource","path":"multi.jar","category":"RESOURCE",
            "kind":"archive","loader_id":"app","ordinal":7
        }
        manifest,_=B.build_inventory_candidate(self.spec,self.r,self.env)
        row=[x for x in manifest["effective_binaries"] if x["binary_name"]=="pkg.A"][0]
        self.assertEqual(row["multi_release_version"],17)
        (self.r/"build"/"pkg").mkdir()
        (self.r/"build"/"pkg"/"A.class").write_bytes(b"other")
        with self.assertRaisesRegex(B.BoundaryError,"ambiguous duplicate"):
            B.build_inventory_candidate(self.spec,self.r,self.env)
        self.spec["duplicate_binary_resolution"]=[
            {"loader_id":"app","binary_name":"pkg.A","winner_root":"resource"}
        ]
        B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_generated_reproducibility_fails_closed(self):
        generated=self.r/"generated"
        generated.mkdir()
        (generated/"G.class").write_bytes(b"g")
        ordinal=len(self.spec["inventory_roots"])
        root={
            "logical_name":"generated","path":"generated","category":"GENERATED_PRELAUNCH",
            "kind":"directory","loader_id":"app","ordinal":ordinal
        }
        self.spec["inventory_roots"].append(root)
        digest=B.sha256_bytes(B.canonical(B.inventory(self.r,root,21)))
        self.spec["generated_prelaunch"]=[{
            "root":"generated","recipe_sha256":"4"*64,"generator_sha256":"5"*64,
            "input_sha256":["6"*64],"first_output_sha256":digest,"second_output_sha256":digest
        }]
        B.build_inventory_candidate(self.spec,self.r,self.env)
        self.spec["generated_prelaunch"][0]["second_output_sha256"]="7"*64
        with self.assertRaisesRegex(B.BoundaryError,"non-reproducible"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_name_only_hidden_provenance_is_rejected(self):
        good={
            "name":"java.lang.invoke.LambdaForm$MH/0x1",
            "source":"__JVM_LookupDefineClass__",
            "hidden":True,
            "generator_module":"java.base",
            "generator_binary":"java.lang.invoke.InvokerBytecodeGenerator",
            "definition_mechanism":"MethodHandles.Lookup.hiddenClass",
        }
        with self.assertRaisesRegex(B.BoundaryError,"INCOMPLETE_RUNTIME_PROVENANCE"):
            B.verify_runtime_class_rows([good],set())
        bad=dict(good)
        bad["name"]="jdk.proxy.$Proxy0/0x1"
        with self.assertRaisesRegex(B.BoundaryError,"INCOMPLETE_RUNTIME_PROVENANCE"):
            B.verify_runtime_class_rows([bad],set())

    def test_unknown_ordinary_class_fails(self):
        row={"name":"x.Unknown","source":"file:/x.jar","hidden":False}
        with self.assertRaisesRegex(B.BoundaryError,"INCOMPLETE_RUNTIME_PROVENANCE"):
            B.verify_runtime_class_rows([row],{"x.Allowed"})

    def test_unqualified_attestation_and_consumption_are_disabled(self):
        key=self.r/"key"
        key.write_bytes(b"k"*32)
        os.chmod(key,0o600)
        consumed=self.r/"consumed"
        consumed.mkdir()
        payload={
            "schema":"industrial-r1-prepared-worker-attestation-v1",
            "manifest_sha256":"8"*64,
            "source_commit":"1"*40,
            "source_tree":"2"*40,
            "rule_review_sha256":B.RULE["independent_review_sha256"],
            "runtime_receipt_sha256":"9"*64,
            "worker_exe_sha256":"a"*64,
            "worker_pid":1,
            "worker_start_ticks":2,
            "launch_id":"launch-20261007-0001",
            "nonce":"b"*64,
            "execution_owner":"owner",
        }
        with self.assertRaisesRegex(B.BoundaryError,"INCOMPLETE_AUTHENTICATED_CHANNEL"):
            B.sign_attestation(payload,key)
        with self.assertRaisesRegex(B.BoundaryError,"INCOMPLETE_AUTHENTICATED_CHANNEL"):
            B.verify_and_consume_attestation({"payload":payload},key,payload,consumed)
        self.assertEqual(list(consumed.iterdir()),[])

    def test_category_labels_cannot_emit_canonical_manifest(self):
        with self.assertRaisesRegex(B.BoundaryError,"INCOMPLETE_CLOSURE"):
            B.build_manifest(self.spec,self.r,self.env)

    def test_root_symlink(self):
        (self.r/"alias").symlink_to(self.r/"build",target_is_directory=True)
        self.spec["inventory_roots"][0]["path"]="alias"
        with self.assertRaisesRegex(B.BoundaryError,"symlink"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_ancestor_symlink(self):
        (self.r/"alias").symlink_to(self.r/"build",target_is_directory=True)
        self.spec["inventory_roots"][0].update(path="alias/x.txt",kind="file")
        with self.assertRaisesRegex(B.BoundaryError,"symlink"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_hardlink_alias(self):
        os.link(self.r/"build/x.txt",self.r/"resource/alias.txt")
        with self.assertRaisesRegex(B.BoundaryError,"alias"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_overlapping_roots(self):
        (self.r/"build/nested").mkdir()
        self.spec["inventory_roots"][1]["path"]="build/nested"
        with self.assertRaisesRegex(B.BoundaryError,"overlapping"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_missing_root(self):
        self.spec["inventory_roots"][1]["path"]="absent"
        with self.assertRaisesRegex(B.BoundaryError,"missing path"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_absolute_root(self):
        self.spec["inventory_roots"][0]["path"]=str(self.r/"build")
        with self.assertRaisesRegex(B.BoundaryError,"unsafe"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_loader_cycle(self):
        self.spec["loader_topology"][1]["parent"]="app"
        with self.assertRaisesRegex(B.BoundaryError,"cycle"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_empty_processes(self):
        self.spec["ordered_processes"]=[]
        with self.assertRaisesRegex(B.BoundaryError,"process ordinals"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_agent_option(self):
        self.spec["ordered_processes"][0]["argv"].append("-javaagent:evil.jar")
        with self.assertRaisesRegex(B.BoundaryError,"extension"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_native_injection_environment(self):
        self.env["LD_PRELOAD"]="evil.so"
        with self.assertRaisesRegex(B.BoundaryError,"forbidden semantic"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_duplicate_process_path(self):
        self.spec["ordered_processes"][0]["classpath"]=["resource","resource"]
        with self.assertRaisesRegex(B.BoundaryError,"duplicate process path"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_non_finite_json(self):
        with self.assertRaisesRegex(B.BoundaryError,"non-finite"):
            B.strict_json('{"value":NaN}')
        with self.assertRaises(ValueError):
            B.canonical({"value":float("inf")})

    def test_duplicate_json(self):
        with self.assertRaisesRegex(B.BoundaryError,"duplicate JSON"):
            B.strict_json('{"value":1,"value":2}')

    def test_archive_traversal_directory(self):
        archive=self.r/"bad.jar"
        with zipfile.ZipFile(archive,"w") as out:
            out.writestr("../",b"")
            out.writestr("A.class",b"a")
        self.spec["inventory_roots"][7].update(path="bad.jar",kind="archive")
        with self.assertRaisesRegex(B.BoundaryError,"unsafe"):
            B.build_inventory_candidate(self.spec,self.r,self.env)

    def test_multi_release_requires_manifest_opt_in(self):
        archive=self.r/"plain.jar"
        with zipfile.ZipFile(archive,"w") as out:
            out.writestr("pkg/A.class",b"base")
            out.writestr("META-INF/versions/17/pkg/A.class",b"v17")
        self.spec["inventory_roots"][7].update(path="plain.jar",kind="archive")
        inventory,_=B.build_inventory_candidate(self.spec,self.r,self.env)
        self.assertEqual(inventory["effective_binaries"][0]["multi_release_version"],0)
        self.assertEqual(len(inventory["entries"]),10)

    def test_changed_jdk_changes_inventory_only(self):
        before,digest=B.build_inventory_candidate(self.spec,self.r,self.env)
        (self.r/"jdk/x.txt").write_bytes(b"changed JDK")
        after,new_digest=B.build_inventory_candidate(self.spec,self.r,self.env)
        self.assertNotEqual(digest,new_digest)
        self.assertFalse(after["complete_closure"])
        with self.assertRaises(B.BoundaryError):
            B.build_manifest(self.spec,self.r,self.env)

    def test_extra_resource_does_not_establish_closure(self):
        (self.r/"resource/extra").write_bytes(b"extra")
        inventory,_=B.build_inventory_candidate(self.spec,self.r,self.env)
        self.assertFalse(inventory["complete_closure"])
        self.assertTrue(any(r["member"]=="extra" for r in inventory["entries"]))
        with self.assertRaises(B.BoundaryError):
            B.build_manifest(self.spec,self.r,self.env)

    def test_failure_record_has_no_authority(self):
        record=B.closure_failure_record()
        self.assertEqual(record["status"],"INCOMPLETE_CLOSURE")
        for field in ("canonical_manifest_emitted","prepared_worker_attestation_emitted","admission_authorized"):
            self.assertIs(record[field],False)
        self.assertEqual(record["official_counters_delta"],0)

if __name__=="__main__":
    unittest.main(verbosity=2)
