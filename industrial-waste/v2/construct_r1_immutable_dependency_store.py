#!/usr/bin/env python3
from __future__ import annotations
import gzip, hashlib, json, os, pathlib, shutil, subprocess, tarfile

ROOT=pathlib.Path(".").resolve()
OUT=ROOT/"build/reports/industrial-r1-dependency-store"
INIT=ROOT/"industrial-waste/v2/r1-dependency-store-bootstrap.init.gradle"
SOURCE="de27e189e7597bdf48f213a40a0bbf1b70f63c1a"
TREE="d62e3fbd59dd78cc444c09b26673ae1bf3a848ab"
JAVA=pathlib.Path("/usr/lib/jvm/temurin-21-jdk-amd64/bin/java")
PYTHON=pathlib.Path("/usr/bin/python3.10")
JAVA_SHA="11af352aa2c506c4123a4e4c19c187d59e06cd0dff317d54f5e6806e07c6715d"
PYTHON_SHA="7d51cd6b48b521277f5caa4610a82126e315fa2be4df069823a8b1eeb5bd4a86"
KEEP=("wrapper/dists","caches/modules-2")

def sha_file(p:pathlib.Path)->str:
    h=hashlib.sha256()
    with p.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""): h.update(b)
    return h.hexdigest()

def canon(v)->bytes:
    return (json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False)+"\n").encode()

def git(*args)->str:
    return subprocess.check_output(["git",*args],cwd=ROOT,text=True,stderr=subprocess.STDOUT).strip()

def run(label:str, home:pathlib.Path, offline:bool)->dict:
    argv=["./gradlew","--no-daemon","--no-build-cache","--no-configuration-cache","--rerun-tasks","--console=plain"]
    if offline: argv.append("--offline")
    argv += ["-I",str(INIT),":ai:testClasses"]
    env=os.environ.copy()
    env["JAVA_HOME"]=str(JAVA.parent.parent)
    env["GRADLE_USER_HOME"]=str(home)
    log=OUT/f"{label}.log"
    with log.open("wb") as out:
        p=subprocess.run(argv,cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
    return {"argv":argv,"returncode":p.returncode,"log_sha256":sha_file(log)}

def selected_files(home:pathlib.Path):
    rows=[]
    for prefix in KEEP:
        root=home/prefix
        if not root.exists(): continue
        for p in sorted(root.rglob("*")):
            if p.is_symlink(): raise RuntimeError(f"symlink in immutable store: {p}")
            if p.is_file():
                rows.append({
                    "path":p.relative_to(home).as_posix(),
                    "bytes":p.stat().st_size,
                    "sha256":sha_file(p),
                })
            elif not p.is_dir():
                raise RuntimeError(f"unsupported store node: {p}")
    return rows

def deterministic_archive(home:pathlib.Path, rows:list[dict], target:pathlib.Path):
    raw=target.with_suffix("")
    with tarfile.open(raw,"w",format=tarfile.PAX_FORMAT) as tf:
        for row in rows:
            p=home/row["path"]
            info=tf.gettarinfo(str(p),arcname=row["path"])
            info.uid=0; info.gid=0; info.uname=""; info.gname=""; info.mtime=0
            with p.open("rb") as f: tf.addfile(info,f)
    with raw.open("rb") as src, target.open("wb") as dst:
        with gzip.GzipFile(filename="",mode="wb",fileobj=dst,mtime=0) as gz:
            shutil.copyfileobj(src,gz)
    raw.unlink()

def main():
    if OUT.exists(): raise RuntimeError("output already exists")
    OUT.mkdir(parents=True)
    subprocess.run(["git","config","--global","--add","safe.directory",str(ROOT)],check=True)
    assert git("rev-parse",f"{SOURCE}^{{tree}}")==TREE
    subprocess.run(["git","merge-base","--is-ancestor",SOURCE,"HEAD"],cwd=ROOT,check=True)
    assert JAVA.resolve()==JAVA and sha_file(JAVA)==JAVA_SHA
    assert PYTHON.resolve()==PYTHON and sha_file(PYTHON)==PYTHON_SHA
    assert git("hash-object","gradle/wrapper/gradle-wrapper.properties")=="a9db11550c6202b666ed9c44cafdc6726fd5b264"
    assert git("hash-object","buildSrc/build.gradle.kts")=="6f476b8598831073d79a9cee7cc3c43a721650b1"
    assert git("hash-object","buildSrc/settings.gradle.kts")=="705bfb5e577139e4a0f0c38abfc3749dcf26e12a"
    assert git("hash-object","gradle/libs.versions.toml")=="1c9d36482bf902b332df50bd2d2332f4d6ad623a"

    temp=pathlib.Path(os.environ["RUNNER_TEMP"]).resolve()
    home1=temp/"industrial-r1-store-network-home"
    home2=temp/"industrial-r1-store-offline-home"
    for p in (home1,home2):
        if p.exists(): raise RuntimeError(f"Gradle home already exists: {p}")
    first=run("network-construction",home1,False)
    if first["returncode"]!=0: raise RuntimeError("network store construction failed")

    rows=selected_files(home1)
    if not rows: raise RuntimeError("empty dependency store")
    manifest={"schema":"industrial-r1-immutable-gradle-store-manifest-v1","gradle_version":"9.6.1","paths":list(KEEP),"files":rows}
    manifest_sha=hashlib.sha256(canon(manifest)).hexdigest()
    (OUT/"store-manifest.json").write_bytes(canon(manifest))
    archive=OUT/"immutable-gradle-input-store.tar.gz"
    deterministic_archive(home1,rows,archive)
    archive_sha=sha_file(archive)

    home2.mkdir()
    with tarfile.open(archive,"r:gz") as tf:
        for m in tf.getmembers():
            pp=pathlib.PurePosixPath(m.name)
            if pp.is_absolute() or ".." in pp.parts or not m.isfile():
                raise RuntimeError(f"unsafe archive member: {m.name}")
        tf.extractall(home2)
    rows2=selected_files(home2)
    if rows2!=rows: raise RuntimeError("reconstructed store differs")

    project_gradle=ROOT/".gradle"
    if project_gradle.exists(): shutil.rmtree(project_gradle)
    second=run("offline-reconstruction",home2,True)
    if second["returncode"]!=0: raise RuntimeError("offline store reconstruction failed")

    log_text=(OUT/"network-construction.log").read_text(errors="replace")+"\n"+(OUT/"offline-reconstruction.log").read_text(errors="replace")
    forbidden=("> Task :ai:test ","IndustrialWasteV2FrozenResponderEquivalenceTest","official seed","game initialized")
    if any(x in log_text for x in forbidden): raise RuntimeError("forbidden behavioral execution marker observed")

    summary={
        "schema":"industrial-r1-immutable-dependency-store-construction-v1",
        "logical_source_commit":SOURCE,
        "logical_source_tree":TREE,
        "observed_head":git("rev-parse","HEAD"),
        "init_blob":git("hash-object",str(INIT.relative_to(ROOT))),
        "network":first,
        "offline":second,
        "store_files":len(rows),
        "store_manifest_sha256":manifest_sha,
        "store_archive_sha256":archive_sha,
        "store_archive_bytes":archive.stat().st_size,
        "tests_executed":0,
        "official_seed_files_read":False,
        "official_counters":{"claims":0,"allocations":0,"games":0,"outcomes":0},
        "authority":"IMMUTABLE_GRADLE_9_6_1_DEPENDENCY_STORE_CANDIDATE_FOR_INDEPENDENT_REVIEW_ONLY",
    }
    (OUT/"summary.json").write_bytes(canon(summary))
    print(json.dumps(summary,sort_keys=True))

if __name__=="__main__": main()
