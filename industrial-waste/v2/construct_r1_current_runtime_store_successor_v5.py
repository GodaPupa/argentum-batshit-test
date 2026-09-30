#!/usr/bin/env python3
from __future__ import annotations
import gzip, hashlib, json, os, pathlib, shutil, subprocess, tarfile

ROOT=pathlib.Path(".").resolve()
OUT=ROOT/"build/reports/industrial-r1-current-runtime-store-supplement-v5"
BOOTSTRAP=ROOT/"industrial-waste/v2/r1-dependency-store-bootstrap.init.gradle"
PROBE=ROOT/"industrial-waste/v2/r1-supplemental-hermetic-capture.init.gradle"
SOURCE="de27e189e7597bdf48f213a40a0bbf1b70f63c1a"
TREE="d62e3fbd59dd78cc444c09b26673ae1bf3a848ab"
JAVA=pathlib.Path("/usr/lib/jvm/temurin-21-jdk-amd64/bin/java")
PYTHON=pathlib.Path("/usr/bin/python3.10")
JAVA_SHA="11af352aa2c506c4123a4e4c19c187d59e06cd0dff317d54f5e6806e07c6715d"
PYTHON_SHA="7d51cd6b48b521277f5caa4610a82126e315fa2be4df069823a8b1eeb5bd4a86"
BASE_OUTER_SHA="1cb974fcb7cf4f5dd39889db4645a9943762f7c0dcd78674a57d6648001b476b"
BASE_ARCHIVE_SHA="b686ca26e715ba965a9156c455a6fba8c0f80f8d55883fc9b85285d4bb0b0b23"
BASE_MANIFEST_SHA="8e427eb24c5c3043dab37abb681b0d3caf5243f4cb9357e1d196a92523ae9c0e"
BASE_FILES=1272
KEEP=("wrapper/dists","caches/modules-2")
TARGETS=[
 "community.flock.kotlinx.rgxgen:kotlin-rgxgen:0.0.6",
 "io.github.java-diff-utils:java-diff-utils:4.16",
 "io.github.pdvrieze.xmlutil:serialization:0.91.3",
 "net.bytebuddy:byte-buddy-agent:1.17.6",
 "net.bytebuddy:byte-buddy:1.17.6",
 "org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0",
 "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0",
 "org.jetbrains.kotlinx:kotlinx-io-core:0.8.2",
]

def sha_file(p:pathlib.Path)->str:
    h=hashlib.sha256()
    with p.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""): h.update(b)
    return h.hexdigest()

def canon(v)->bytes:
    return (json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False)+"\n").encode()

def git(*args)->str:
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True,stderr=subprocess.STDOUT).strip()

def inventory(home:pathlib.Path)->list[dict]:
    rows=[]
    for prefix in KEEP:
        root=home/prefix
        if not root.exists(): continue
        for p in sorted(root.rglob("*")):
            if p.is_symlink(): raise RuntimeError(f"symlink in store: {p}")
            if p.is_file():
                rows.append({"path":p.relative_to(home).as_posix(),"bytes":p.stat().st_size,"sha256":sha_file(p)})
            elif not p.is_dir():
                raise RuntimeError(f"unsupported store node: {p}")
    return rows

def restore(archive:pathlib.Path, manifest_path:pathlib.Path, target:pathlib.Path)->dict[str,tuple[int,str]]:
    if sha_file(archive)!=BASE_ARCHIVE_SHA or sha_file(manifest_path)!=BASE_MANIFEST_SHA:
        raise RuntimeError("accepted store input digest mismatch")
    m=json.loads(manifest_path.read_text())
    rows=m["files"]
    if len(rows)!=BASE_FILES: raise RuntimeError("accepted store file count mismatch")
    expected={x["path"]:(x["bytes"],x["sha256"]) for x in rows}
    if len(expected)!=BASE_FILES: raise RuntimeError("accepted store duplicate path")
    if target.exists(): raise RuntimeError("target Gradle home already exists")
    target.mkdir(parents=True)
    seen=set()
    with tarfile.open(archive,"r:gz") as tf:
        for member in tf.getmembers():
            if member.isdir(): continue
            pp=pathlib.PurePosixPath(member.name)
            if pp.is_absolute() or ".." in pp.parts or not member.isfile():
                raise RuntimeError("unsafe accepted store member: "+member.name)
            name=pp.as_posix()
            if name not in expected or name in seen: raise RuntimeError("unexpected/duplicate accepted member: "+name)
            data=tf.extractfile(member).read()
            size,digest=expected[name]
            if len(data)!=size or hashlib.sha256(data).hexdigest()!=digest:
                raise RuntimeError("accepted member mismatch: "+name)
            out=target.joinpath(*pp.parts); out.parent.mkdir(parents=True,exist_ok=True); out.write_bytes(data)
            os.chmod(out,member.mode & 0o777); seen.add(name)
    if seen!=set(expected): raise RuntimeError("accepted store archive incomplete")
    actual={x["path"]:(x["bytes"],x["sha256"]) for x in inventory(target)}
    if actual!=expected: raise RuntimeError("restored accepted store differs")
    return expected

def run_resolution(label:str, home:pathlib.Path, offline:bool)->dict:
    graph=OUT/f"{label}-runtime.json"
    classlog=OUT/f"{label}-unused-classlog.log"
    argv=["./gradlew","--no-daemon","--no-build-cache","--no-configuration-cache","--rerun-tasks","--console=plain"]
    if offline: argv.append("--offline")
    argv += ["-I",str(BOOTSTRAP),"-I",str(PROBE),f"-Dindustrial.classlog={classlog}",f"-Dindustrial.runtime.out={graph}",":ai:industrialR1CaptureRuntime"]
    env=os.environ.copy(); env["JAVA_HOME"]=str(JAVA.parent.parent); env["GRADLE_USER_HOME"]=str(home)
    log=OUT/f"{label}.log"
    with log.open("wb") as out:
        p=subprocess.run(argv,cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
    return {"argv":argv,"returncode":p.returncode,"log_sha256":sha_file(log),"graph":str(graph)}

def normalized_graph(graph:dict, home:pathlib.Path)->dict:
    ordered=[]
    for raw in graph["ordered_files"]:
        p=pathlib.Path(raw).resolve()
        if p.is_relative_to(ROOT): ordered.append("$SOURCE/"+p.relative_to(ROOT).as_posix())
        elif p.is_relative_to(home): ordered.append("$STORE/"+p.relative_to(home).as_posix())
        else: raise RuntimeError("runtime entry outside source/store: "+str(p))
    return {"ordered_files":ordered,"components":graph["components"],"dependencies":graph["dependencies"]}

def archive(home:pathlib.Path, rows:list[dict], target:pathlib.Path):
    raw=target.with_suffix("")
    with tarfile.open(raw,"w",format=tarfile.PAX_FORMAT) as tf:
        for row in rows:
            p=home/row["path"]; info=tf.gettarinfo(str(p),arcname=row["path"])
            info.uid=0; info.gid=0; info.uname=""; info.gname=""; info.mtime=0
            with p.open("rb") as f: tf.addfile(info,f)
    with raw.open("rb") as src,target.open("wb") as dst:
        with gzip.GzipFile(filename="",mode="wb",fileobj=dst,mtime=0) as gz: shutil.copyfileobj(src,gz)
    raw.unlink()

def assert_no_tests(log:pathlib.Path):
    text=log.read_text(errors="replace")
    forbidden=("> Task :ai:test ","IndustrialWasteV2FrozenResponderEquivalenceTest","game initialized","official seed")
    if any(x in text for x in forbidden): raise RuntimeError("forbidden behavioral marker observed")

def main():
    if OUT.exists(): raise RuntimeError("output already exists")
    OUT.mkdir(parents=True)
    subprocess.run(["git","config","--global","--add","safe.directory",str(ROOT)],check=True)
    if git("rev-parse",f"{SOURCE}^{{tree}}")!=TREE: raise RuntimeError("source tree mismatch")
    subprocess.run(["git","merge-base","--is-ancestor",SOURCE,"HEAD"],cwd=ROOT,check=True)
    if JAVA.resolve()!=JAVA or sha_file(JAVA)!=JAVA_SHA: raise RuntimeError("Java mismatch")
    if PYTHON.resolve()!=PYTHON or sha_file(PYTHON)!=PYTHON_SHA: raise RuntimeError("Python mismatch")
    pins={
      "ai/build.gradle.kts":"17a38cd1b2cf29542eec327e71d324df00fbeff4",
      "settings.gradle.kts":"b3cf7d0b70c58c6c4d5b214588f48ca5660d902e",
      "build.gradle.kts":"8c5298acbab5eae78f5ee612046317889666c83e",
      "gradle/libs.versions.toml":"1c9d36482bf902b332df50bd2d2332f4d6ad623a",
      "gradle/wrapper/gradle-wrapper.properties":"a9db11550c6202b666ed9c44cafdc6726fd5b264",
      "buildSrc/build.gradle.kts":"6f476b8598831073d79a9cee7cc3c43a721650b1",
      "buildSrc/settings.gradle.kts":"705bfb5e577139e4a0f0c38abfc3749dcf26e12a",
    }
    for path,digest in pins.items():
        if git("hash-object",path)!=digest: raise RuntimeError("build input changed: "+path)
    if git("hash-object",str(BOOTSTRAP.relative_to(ROOT)))!="e2181fa92f04c14f1bbb724d9d0f4240ccc1b856":
        raise RuntimeError("bootstrap init changed")
    if git("hash-object",str(PROBE.relative_to(ROOT)))!="ef3a56e80afc5002e66a05b4f5cf3635eb604c21":
        raise RuntimeError("runtime probe init changed")

    archive_in=pathlib.Path(os.environ["ARGENTUM_BASE_STORE_ARCHIVE"]).resolve(strict=True)
    manifest_in=pathlib.Path(os.environ["ARGENTUM_BASE_STORE_MANIFEST"]).resolve(strict=True)
    if (archive_in.stat().st_mode & 0o222) or (manifest_in.stat().st_mode & 0o222):
        raise RuntimeError("accepted input store must be read-only")
    temp=pathlib.Path(os.environ["RUNNER_TEMP"]).resolve()
    home1=temp/"industrial-r1-current-runtime-store-v5-network"
    home2=temp/"industrial-r1-current-runtime-store-v5-offline"
    base=restore(archive_in,manifest_in,home1)

    project_gradle=ROOT/".gradle"
    if project_gradle.exists(): shutil.rmtree(project_gradle)
    online=run_resolution("online-current-runtime-resolution",home1,False)
    if online["returncode"]!=0: raise RuntimeError("online current runtime resolution failed")
    assert_no_tests(OUT/"online-current-runtime-resolution.log")
    graph1=json.loads(pathlib.Path(online["graph"]).read_text())
    unresolved=[d for d in graph1["dependencies"] if d.get("selected") is None]
    if unresolved: raise RuntimeError("online graph still unresolved")
    graph_text=json.dumps(graph1,sort_keys=True)
    missing=[x for x in TARGETS if x not in graph_text]
    if missing: raise RuntimeError("observed target coordinates absent after resolution: "+repr(missing))

    rows=inventory(home1); current={x["path"]:(x["bytes"],x["sha256"]) for x in rows}
    removed=sorted(set(base)-set(current))
    if removed: raise RuntimeError("accepted store files removed: "+repr(removed[:20]))
    changed=sorted(k for k in base if current[k]!=base[k])
    immutable_changed=[k for k in changed if k.startswith("wrapper/dists/") or k.startswith("caches/modules-2/files-2.1/")]
    if immutable_changed: raise RuntimeError("accepted wrapper/artifact payload changed: "+repr(immutable_changed[:20]))
    added=sorted(set(current)-set(base))
    delta={"schema":"industrial-r1-current-runtime-store-v5-delta-v1","base_files":len(base),"successor_files":len(current),
           "added":added,"changed_metadata":changed,"removed":removed,"targets":TARGETS}
    (OUT/"base-to-successor-delta.json").write_bytes(canon(delta))

    merged={"schema":"industrial-r1-immutable-gradle-store-manifest-v2","gradle_version":"9.6.1",
            "parent_artifact_id":11080705894,"parent_archive_sha256":BASE_ARCHIVE_SHA,
            "parent_manifest_sha256":BASE_MANIFEST_SHA,"paths":list(KEEP),"targets":TARGETS,"files":rows}
    (OUT/"store-manifest.json").write_bytes(canon(merged))
    manifest_sha=sha_file(OUT/"store-manifest.json")
    arc=OUT/"immutable-current-runtime-gradle-store.tar.gz"; archive(home1,rows,arc); archive_sha=sha_file(arc)

    home2.mkdir()
    with tarfile.open(arc,"r:gz") as tf:
        expected={x["path"]:(x["bytes"],x["sha256"]) for x in rows}; seen=set()
        for m in tf.getmembers():
            if m.isdir(): continue
            pp=pathlib.PurePosixPath(m.name)
            if pp.is_absolute() or ".." in pp.parts or not m.isfile() or pp.as_posix() not in expected or pp.as_posix() in seen:
                raise RuntimeError("unsafe/unexpected successor archive member: "+m.name)
            data=tf.extractfile(m).read(); size,digest=expected[pp.as_posix()]
            if len(data)!=size or hashlib.sha256(data).hexdigest()!=digest: raise RuntimeError("successor archive member mismatch")
            out=home2.joinpath(*pp.parts); out.parent.mkdir(parents=True,exist_ok=True); out.write_bytes(data); os.chmod(out,m.mode & 0o777); seen.add(pp.as_posix())
        if seen!=set(expected): raise RuntimeError("successor archive incomplete")
    if project_gradle.exists(): shutil.rmtree(project_gradle)
    offline=run_resolution("offline-current-runtime-reconstruction",home2,True)
    if offline["returncode"]!=0: raise RuntimeError("offline successor reconstruction failed")
    assert_no_tests(OUT/"offline-current-runtime-reconstruction.log")
    graph2=json.loads(pathlib.Path(offline["graph"]).read_text())
    if [d for d in graph2["dependencies"] if d.get("selected") is None]: raise RuntimeError("offline graph unresolved")
    n1=normalized_graph(graph1,home1); n2=normalized_graph(graph2,home2)
    if n1!=n2: raise RuntimeError("online/offline normalized runtime graph differs")
    (OUT/"normalized-runtime-graph.json").write_bytes(canon(n2))

    summary={
      "schema":"industrial-r1-current-runtime-store-supplement-v5-result-v1",
      "logical_source_commit":SOURCE,"logical_source_tree":TREE,
      "observed_head":git("rev-parse","HEAD"),
      "accepted_base_store":{"artifact_id":11080705894,"files":BASE_FILES,"archive_sha256":BASE_ARCHIVE_SHA,"manifest_sha256":BASE_MANIFEST_SHA},
      "target_coordinates":TARGETS,
      "online":online,"offline":offline,
      "unresolved_dependencies":0,
      "successor_store_files":len(rows),
      "added_files":len(added),"changed_metadata_files":len(changed),"removed_files":0,
      "store_manifest_sha256":manifest_sha,"store_archive_sha256":archive_sha,"store_archive_bytes":arc.stat().st_size,
      "tests_executed":0,"official_seed_files_read":False,
      "official_counters":{"claims":0,"allocations":0,"games":0,"outcomes":0},
      "authority":"IMMUTABLE_CURRENT_RUNTIME_DEPENDENCY_STORE_SUCCESSOR_CANDIDATE_FOR_INDEPENDENT_REVIEW_ONLY"
    }
    (OUT/"summary.json").write_bytes(canon(summary))
    print(json.dumps(summary,sort_keys=True))

if __name__=="__main__": main()
