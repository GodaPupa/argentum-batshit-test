from __future__ import annotations
import json,pathlib,tempfile,unittest,zipfile,sys
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from r1_complete_runtime_capture import build
D="1"*64
def spec():
 return {"source_commit":"1"*40,"source_tree":"2"*40,"build_inputs":["gradlew"],"dependency_nodes":[{"id":"worker","kind":"archive","path":"worker.jar","loader":"app"}],"edges":[],"process_paths":[{"process":"worker","loader":"app","ordinal":0,"node_id":"worker"}],"inventory_artifact_ids":["worker"],"platform":{"jdk_digest":D,"os_image_digest":D,"architecture":"x86_64","python_digest":D,"shell_digest":D,"just_digest":D},"launch":{"working_directory":"repo","commands":[["just","test-class","Worker"]],"allowed_environment":{"LANG":"C.UTF-8"}},"loader_policy":{"allowed_loaders":["app"],"generated_class_policy":"ELIMINATED","agents_allowed":False,"dynamic_attach_allowed":False,"runtime_compilation_allowed":False}}
class T(unittest.TestCase):
 def fixture(self):
  td=tempfile.TemporaryDirectory(); p=pathlib.Path(td.name); (p/"gradlew").write_text("x")
  with zipfile.ZipFile(p/"worker.jar","w") as z:z.writestr("a/A.class",b"abc");z.writestr("META-INF/services/x",b"impl")
  return td,p
 def test_builds_and_validates_archive_manifest(self):
  td,p=self.fixture()
  try:
   m=build(spec(),p);self.assertEqual(len(m["inventories"][0]["members"]),2);self.assertEqual(m["process_paths"][0]["ordinal"],0)
  finally:td.cleanup()
 def test_duplicate_zip_member_rejected(self):
  td,p=self.fixture()
  try:
   with zipfile.ZipFile(p/"worker.jar","w") as z:z.writestr("a/A.class",b"a");z.writestr("a/A.class",b"b")
   with self.assertRaises(ValueError):build(spec(),p)
  finally:td.cleanup()
 def test_path_traversal_rejected(self):
  td,p=self.fixture()
  try:
   with zipfile.ZipFile(p/"worker.jar","w") as z:z.writestr("../A.class",b"a")
   with self.assertRaises(ValueError):build(spec(),p)
  finally:td.cleanup()
 def test_multirelease_rejected_until_policy_review(self):
  td,p=self.fixture()
  try:
   with zipfile.ZipFile(p/"worker.jar","w") as z:z.writestr("META-INF/versions/21/a/A.class",b"a")
   with self.assertRaises(ValueError):build(spec(),p)
  finally:td.cleanup()
 def test_loader_mismatch_rejected(self):
  td,p=self.fixture();s=spec();s["process_paths"][0]["loader"]="other"
  try:
   with self.assertRaises(ValueError):build(s,p)
  finally:td.cleanup()
if __name__=="__main__":unittest.main(verbosity=2)
