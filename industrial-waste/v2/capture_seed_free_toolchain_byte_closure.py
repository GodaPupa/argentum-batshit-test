#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, os, pathlib, shutil, subprocess, sys

ROOT = pathlib.Path(".").resolve()
OUT = ROOT / "build/reports/industrial-toolchain-closure"

def sha256_file(path: pathlib.Path) -> str:
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024), b""):
            h.update(chunk)
    return h.hexdigest()

def record_path(path: pathlib.Path, base: pathlib.Path) -> dict:
    rel=path.relative_to(base).as_posix()
    if path.is_symlink():
        return {"path":rel,"kind":"symlink","target":os.readlink(path)}
    if path.is_file():
        st=path.stat()
        return {"path":rel,"kind":"file","bytes":st.st_size,"sha256":sha256_file(path),"mode":oct(st.st_mode & 0o7777)}
    if path.is_dir():
        return {"path":rel,"kind":"directory"}
    raise SystemExit(f"unsupported JDK node: {path}")

def executable(name: str) -> dict:
    raw=shutil.which(name)
    if raw is None:
        raise SystemExit(f"missing executable: {name}")
    p=pathlib.Path(raw)
    resolved=p.resolve(strict=True)
    return {
        "name":name,
        "path":str(p),
        "resolved_path":str(resolved),
        "bytes":resolved.stat().st_size,
        "sha256":sha256_file(resolved),
        "symlink_chain_start":p.is_symlink(),
    }

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    java_home=os.environ.get("JAVA_HOME")
    if not java_home:
        raise SystemExit("JAVA_HOME missing")
    jdk=pathlib.Path(java_home).resolve(strict=True)
    nodes=[]
    for p in sorted(jdk.rglob("*"), key=lambda x:x.relative_to(jdk).as_posix()):
        nodes.append(record_path(p,jdk))
    canonical=(json.dumps(nodes,sort_keys=True,separators=(",",":"))+"\n").encode()
    result={
        "schema":"industrial-r1-seed-free-toolchain-byte-closure-v1",
        "source_commit":subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip(),
        "source_tree":subprocess.check_output(["git","rev-parse","HEAD^{tree}"],text=True).strip(),
        "jdk_root":str(jdk),
        "jdk_nodes":nodes,
        "jdk_node_count":len(nodes),
        "jdk_canonical_inventory_sha256":hashlib.sha256(canonical).hexdigest(),
        "executables":[executable(x) for x in ("java","python3","bash","just")],
        "gradle_wrapper":{
            "script":{"path":"gradlew","sha256":sha256_file(ROOT/"gradlew")},
            "jar":{"path":"gradle/wrapper/gradle-wrapper.jar","sha256":sha256_file(ROOT/"gradle/wrapper/gradle-wrapper.jar")}
        },
        "official_seed_files_read":False,
        "official_counters":{"allocations":0,"games":0,"outcomes":0},
        "authority":"CANDIDATE_TOOLCHAIN_BYTE_CLOSURE_SLICE_ONLY",
        "not_captured":[
            "OS dynamic loader and system shared-library closure outside JAVA_HOME",
            "Python standard-library/import closure beyond executable bytes",
            "Gradle launcher/daemon/test-worker implementation classpaths",
            "generated/hidden class policy and loader topology",
            "immutable store/lifetime enforcement",
            "authenticated prepared-worker attestation",
            "complete expected-manifest adoption"
        ]
    }
    (OUT/"capture.json").write_text(json.dumps(result,sort_keys=True,indent=2)+"\n")
    print(json.dumps({
        "jdk_nodes":len(nodes),
        "jdk_inventory_sha256":result["jdk_canonical_inventory_sha256"],
        "executables":{x["name"]:x["sha256"] for x in result["executables"]},
        "official_seed_files_read":False
    },indent=2))

if __name__=="__main__":
    main()
