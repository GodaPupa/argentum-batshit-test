#!/usr/bin/env python3
import hashlib, json, pathlib, subprocess, tempfile

ROOT=pathlib.Path(".").resolve()

def sha256(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):
            h.update(chunk)
    return h.hexdigest()

def digest_rows(rows):
    h=hashlib.sha256()
    for row in sorted(rows,key=lambda x:x["path"]):
        h.update(json.dumps(row,sort_keys=True,separators=(",",":")).encode());h.update(b"\n")
    return h.hexdigest()

init = """
gradle.settingsEvaluated {
    println("ARGENTUM_GRADLE_HOME=" + gradle.gradleHomeDir.absolutePath)
    println("ARGENTUM_GRADLE_VERSION=" + gradle.gradleVersion)
}
"""
with tempfile.NamedTemporaryFile("w",suffix=".gradle",delete=False) as f:
    f.write(init)
    init_path=f.name
proc=subprocess.run(["./gradlew","-q","-I",init_path,"help"],cwd=ROOT,text=True,capture_output=True,check=True)
home=None
version=None
for line in (proc.stdout+"\n"+proc.stderr).splitlines():
    if line.startswith("ARGENTUM_GRADLE_HOME="): home=pathlib.Path(line.split("=",1)[1]).resolve()
    if line.startswith("ARGENTUM_GRADLE_VERSION="): version=line.split("=",1)[1].strip()
if home is None or version is None or not home.is_dir():
    raise RuntimeError("could not bind actual Gradle home/version")

jars=[]
for p in sorted(home.rglob("*.jar")):
    if p.is_file():
        jars.append({"path":str(p.relative_to(home)),"bytes":p.stat().st_size,"sha256":sha256(p)})
if not jars:
    raise RuntimeError("no Gradle implementation jars captured")

wrapper=[]
for rel in ["gradle/wrapper/gradle-wrapper.jar","gradle/wrapper/gradle-wrapper.properties","gradlew"]:
    p=ROOT/rel
    if not p.is_file(): raise RuntimeError(f"missing wrapper input {rel}")
    wrapper.append({"path":rel,"bytes":p.stat().st_size,"sha256":sha256(p)})

result={
  "schema":"industrial-r1-seed-free-gradle-implementation-closure-v1",
  "authority":"CANDIDATE_CONSERVATIVE_GRADLE_IMPLEMENTATION_CLOSURE_ONLY",
  "source_commit":subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip(),
  "source_tree":subprocess.check_output(["git","rev-parse","HEAD^{tree}"],cwd=ROOT,text=True).strip(),
  "gradle_version":version,
  "gradle_home":str(home),
  "implementation_jars":len(jars),
  "implementation_jars_sha256":digest_rows(jars),
  "jars":jars,
  "wrapper_files":wrapper,
  "wrapper_files_sha256":digest_rows(wrapper),
  "coverage_note":"All *.jar bytes recursively beneath the actual wrapper-selected Gradle home are retained; this deliberately over-approximates launcher/daemon/test-worker implementation code rather than claiming a precise classloader order.",
  "not_captured":[
    "precise launcher/daemon/test-worker classloader order and parent topology",
    "generated/hidden class policy outside captured Gradle implementation jars",
    "immutable store/lifetime enforcement",
    "authenticated prepared-worker attestation",
    "complete expected-manifest adoption"
  ],
  "official_seed_files_read":False,
  "official_counters":{"allocations":0,"games":0,"outcomes":0}
}
print(json.dumps(result,sort_keys=True,indent=2))
