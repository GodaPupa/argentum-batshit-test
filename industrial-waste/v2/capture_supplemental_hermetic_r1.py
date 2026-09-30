#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,pathlib,re,shutil,subprocess,sys,sysconfig,tarfile,threading,time,zipfile,xml.etree.ElementTree as ET

ROOT=pathlib.Path(".").resolve()
OUT=ROOT/"build/reports/industrial-r1-supplemental-hermetic"
INIT=ROOT/"industrial-waste/v2/r1-supplemental-hermetic-capture.init.gradle"
BOOTSTRAP=ROOT/"industrial-waste/v2/r1-dependency-store-bootstrap.init.gradle"
STORE_ARCHIVE_SHA="b686ca26e715ba965a9156c455a6fba8c0f80f8d55883fc9b85285d4bb0b0b23"
STORE_MANIFEST_SHA="8e427eb24c5c3043dab37abb681b0d3caf5243f4cb9357e1d196a92523ae9c0e"
STORE_FILES=1272
SOURCE="de27e189e7597bdf48f213a40a0bbf1b70f63c1a"
SOURCE_TREE="d62e3fbd59dd78cc444c09b26673ae1bf3a848ab"
JAVA=pathlib.Path("/usr/lib/jvm/temurin-21-jdk-amd64/bin/java")
PYTHON=pathlib.Path("/usr/bin/python3.10")
EXPECTED={
 "java":"11af352aa2c506c4123a4e4c19c187d59e06cd0dff317d54f5e6806e07c6715d",
 "python3":"7d51cd6b48b521277f5caa4610a82126e315fa2be4df069823a8b1eeb5bd4a86",
 "bash":"59474588a312b6b6e73e5a42a59bf71e62b55416b6c9d5e4a6e1c630c2a9ecd4",
 "git":"011fb1c28096c72e25688e56793f20b33e68f7f1065489f745763f19b6e9f516",
 "sha256sum":"b88ea413571562a591268213d736121fada5ba14330bfcc74b8d9f14e4018ddf",
}
SEMANTIC_ENV=("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH","PYTHONPATH","JAVA_OPTS","GRADLE_OPTS")
ALLOW_ENV=("CI","GITHUB_ACTIONS","RUNNER_ARCH","RUNNER_OS","LANG","LANGUAGE","LC_ALL","LC_CTYPE","TZ","HOME","PATH","JAVA_HOME","GRADLE_USER_HOME")
TEST="com.wingedsheep.ai.engine.IndustrialWasteV2FrozenResponderEquivalenceTest"

def sha_file(p:pathlib.Path)->str:
    h=hashlib.sha256()
    with p.open("rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""): h.update(b)
    return h.hexdigest()
def canonical(v)->bytes: return (json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False)+"\n").encode()
def cmd(*a): return subprocess.check_output(a,cwd=ROOT,text=True,stderr=subprocess.STDOUT).strip()
def display(p:pathlib.Path)->str:
    p=p.resolve()
    try:return p.relative_to(ROOT).as_posix()
    except ValueError:
        try:return "$HOME/"+p.relative_to(pathlib.Path.home().resolve()).as_posix()
        except ValueError:return str(p)

def file_inventory(root:pathlib.Path, exclude_names=frozenset()):
    rows=[]
    if not root.exists(): return rows
    for p in sorted(root.rglob("*")):
        rel=p.relative_to(root).as_posix()
        if any(x in p.parts for x in exclude_names): continue
        if p.is_symlink(): rows.append({"path":rel,"kind":"symlink","target":os.readlink(p)})
        elif p.is_file(): rows.append({"path":rel,"kind":"file","bytes":p.stat().st_size,"sha256":sha_file(p)})
        elif p.is_dir(): continue
        else: raise RuntimeError(f"unsupported filesystem node: {p}")
    return rows

def repo_inputs():
    names=subprocess.check_output(["git","ls-files","-z"],cwd=ROOT).split(b"\0")
    rows=[]
    for raw in names:
        if not raw: continue
        rel=raw.decode(); p=ROOT/rel
        if p.is_symlink(): rows.append({"path":rel,"kind":"symlink","target":os.readlink(p)})
        elif p.is_file(): rows.append({"path":rel,"kind":"file","bytes":p.stat().st_size,"sha256":sha_file(p),"git_blob":cmd("git","hash-object",rel)})
        else: raise RuntimeError("tracked non-file input: "+rel)
    return rows

def descendants(rootpid:int):
    parents={}
    for d in pathlib.Path("/proc").iterdir():
        if not d.name.isdigit(): continue
        try:
            stat=(d/"stat").read_text()
            close=stat.rfind(")")
            fields=stat[close+2:].split()
            parents[int(d.name)]=int(fields[1])
        except Exception: pass
    keep={rootpid}; changed=True
    while changed:
        changed=False
        for pid,ppid in list(parents.items()):
            if ppid in keep and pid not in keep: keep.add(pid); changed=True
    return keep

def process_row(pid:int):
    base=pathlib.Path("/proc")/str(pid)
    try:
        exe=str((base/"exe").resolve(strict=True)); exesha=sha_file(pathlib.Path(exe))
        cmdline=[x.decode(errors="replace") for x in (base/"cmdline").read_bytes().split(b"\0") if x]
        cwd=str((base/"cwd").resolve(strict=True))
        env={}
        for item in (base/"environ").read_bytes().split(b"\0"):
            if b"=" not in item: continue
            k,v=item.split(b"=",1); key=k.decode(errors="replace")
            if key in ALLOW_ENV: env[key]=v.decode(errors="replace")
        stat=(base/"stat").read_text(); close=stat.rfind(")"); fields=stat[close+2:].split()
        return {"pid":pid,"ppid":int(fields[1]),"start_ticks":int(fields[19]),"exe":exe,"exe_sha256":exesha,"argv":cmdline,"cwd":cwd,"environment":env}
    except Exception:return None

def run_gradle(label:str,offline:bool,classlog:pathlib.Path,graph:pathlib.Path,gradle_home:pathlib.Path):
    argv=["./gradlew","--no-daemon","--no-build-cache","--no-configuration-cache","--rerun-tasks","--console=plain"]
    if offline: argv.append("--offline")
    argv += ["-I",str(BOOTSTRAP),"-I",str(INIT),f"-Dindustrial.classlog={classlog}",f"-Dindustrial.runtime.out={graph}",":ai:test",":ai:industrialR1CaptureRuntime"]
    log=OUT/f"{label}.log"
    env=os.environ.copy(); env["JAVA_HOME"]=str(JAVA.parent.parent); env["GRADLE_USER_HOME"]=str(gradle_home)
    with log.open("wb") as out:
        p=subprocess.Popen(argv,cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
        seen={}
        stop=False
        def watch():
            while not stop:
                for pid in descendants(p.pid):
                    row=process_row(pid)
                    if row: seen[(row["pid"],row["start_ticks"])]=row
                time.sleep(.03)
        t=threading.Thread(target=watch,daemon=True); t.start()
        rc=p.wait(); stop=True; t.join(timeout=2)
    (OUT/f"{label}-processes.json").write_bytes(canonical(sorted(seen.values(),key=lambda x:(x["start_ticks"],x["pid"]))))
    if rc: raise RuntimeError(f"{label} Gradle failed rc={rc}; see {log}")
    return {"argv":argv,"returncode":rc,"processes":len(seen),"log_sha256":sha_file(log)}

def restore_accepted_store(archive:pathlib.Path, manifest_path:pathlib.Path, target:pathlib.Path):
    assert archive.is_file() and sha_file(archive)==STORE_ARCHIVE_SHA
    assert manifest_path.is_file() and sha_file(manifest_path)==STORE_MANIFEST_SHA
    manifest=json.loads(manifest_path.read_text())
    rows=manifest["files"]
    assert len(rows)==STORE_FILES
    expected={r["path"]:(r["bytes"],r["sha256"]) for r in rows}
    assert len(expected)==STORE_FILES
    assert all(p.startswith("wrapper/dists/") or p.startswith("caches/modules-2/") for p in expected)
    if target.exists(): raise RuntimeError("writable Gradle home already exists")
    target.mkdir(parents=True)
    seen=set()
    with tarfile.open(archive,"r:gz") as tf:
        for member in tf.getmembers():
            if member.isdir(): continue
            if not member.isfile(): raise RuntimeError("non-file dependency-store member: "+member.name)
            rel=pathlib.PurePosixPath(member.name)
            if rel.is_absolute() or ".." in rel.parts or "." in rel.parts:
                raise RuntimeError("unsafe dependency-store path: "+member.name)
            name=rel.as_posix()
            if name not in expected or name in seen: raise RuntimeError("unexpected/duplicate dependency-store member: "+name)
            data=tf.extractfile(member).read()
            size,digest=expected[name]
            if len(data)!=size or hashlib.sha256(data).hexdigest()!=digest:
                raise RuntimeError("dependency-store member mismatch: "+name)
            out=target.joinpath(*rel.parts)
            out.parent.mkdir(parents=True,exist_ok=True)
            out.write_bytes(data)
            os.chmod(out,member.mode & 0o777)
            seen.add(name)
    if seen!=set(expected): raise RuntimeError("dependency-store archive missing manifest members")
    restored=file_inventory(target)
    actual={r["path"]:(r["bytes"],r["sha256"]) for r in restored if r["kind"]=="file"}
    if actual!=expected: raise RuntimeError("restored dependency-store differs from accepted manifest")
    receipt={"schema":"industrial-r1-accepted-dependency-store-restore-v1",
             "source_artifact_id":11080705894,
             "outer_artifact_sha256":"1cb974fcb7cf4f5dd39889db4645a9943762f7c0dcd78674a57d6648001b476b",
             "archive_sha256":STORE_ARCHIVE_SHA,"manifest_sha256":STORE_MANIFEST_SHA,
             "files":len(seen),"writable_gradle_home":str(target),
             "input_archive_read_only":not os.access(archive,os.W_OK),
             "input_manifest_read_only":not os.access(manifest_path,os.W_OK)}
    (OUT/"accepted-dependency-store-restore.json").write_bytes(canonical(receipt))
    return expected,receipt

def parse_classlog(path:pathlib.Path):
    rows=[]
    rx=re.compile(r"\[class,load\]\s+(.+?)\s+source:\s+(.*)$")
    for line in path.read_text(errors="replace").splitlines():
        m=rx.search(line)
        if not m: continue
        name=m.group(1); source=m.group(2)
        normalized=re.sub(r"/0x[0-9a-fA-F]+","/0x<HIDDEN>",name)
        rows.append({"name":name,"normalized_name":normalized,"source":source,"hidden":"/0x" in name})
    normalized=sorted((r["normalized_name"],r["source"]) for r in rows)
    return rows,hashlib.sha256(canonical(normalized)).hexdigest()

def archive_inventory(path:pathlib.Path):
    if path.is_dir():
        members=file_inventory(path)
        return {"kind":"directory","path":display(path),"members":members,"member_count":len(members),"sha256":hashlib.sha256(canonical(members)).hexdigest()}
    if not path.is_file(): raise RuntimeError("missing classpath entry "+str(path))
    whole=sha_file(path)
    if path.suffix.lower() not in (".jar",".zip"):
        return {"kind":"file","path":display(path),"bytes":path.stat().st_size,"sha256":whole,"members":[]}
    members=[]
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None: raise RuntimeError("CRC failure "+str(path))
        names=z.namelist()
        if len(names)!=len(set(names)): raise RuntimeError("duplicate archive member "+str(path))
        for info in z.infolist():
            if info.is_dir(): continue
            data=z.read(info.filename)
            members.append({"path":info.filename,"compression":info.compress_type,"bytes":info.file_size,"compressed_bytes":info.compress_size,"crc":info.CRC,"sha256":hashlib.sha256(data).hexdigest()})
    return {"kind":"archive","path":display(path),"bytes":path.stat().st_size,"sha256":whole,"members":members,"member_count":len(members)}

def ldd_closure(roots):
    seen=set(); queue=[str(pathlib.Path(x).resolve()) for x in roots]
    while queue:
        p=queue.pop(0)
        if p in seen: continue
        seen.add(p)
        proc=subprocess.run(["ldd",p],text=True,capture_output=True)
        if proc.returncode: continue
        for line in proc.stdout.splitlines():
            line=line.strip(); candidate=None
            if "=>" in line:
                rhs=line.split("=>",1)[1].strip()
                if rhs.startswith("/"): candidate=rhs.split(" ",1)[0]
            elif line.startswith("/"): candidate=line.split(" ",1)[0]
            if candidate and pathlib.Path(candidate).is_file():
                rp=str(pathlib.Path(candidate).resolve())
                if rp not in seen: queue.append(rp)
    return [{"path":p,"bytes":pathlib.Path(p).stat().st_size,"sha256":sha_file(pathlib.Path(p))} for p in sorted(seen)]

def main():
    if OUT.exists(): raise RuntimeError("output already exists")
    OUT.mkdir(parents=True)
    subprocess.run(["git","config","--global","--add","safe.directory",str(ROOT)],check=True)
    assert cmd("git","rev-parse",f"{SOURCE}^{{tree}}")==SOURCE_TREE
    subprocess.run(["git","merge-base","--is-ancestor",SOURCE,"HEAD"],cwd=ROOT,check=True)
    assert os.uname().machine=="x86_64"
    tools={"java":JAVA,"python3":PYTHON,"bash":pathlib.Path("/usr/bin/bash"),"git":pathlib.Path("/usr/bin/git"),"sha256sum":pathlib.Path("/usr/bin/sha256sum")}
    for k,p in tools.items(): assert p.resolve()==p and sha_file(p)==EXPECTED[k],(k,sha_file(p),EXPECTED[k])
    assert all(not os.environ.get(k) for k in SEMANTIC_ENV)
    platform={"declared_image":os.environ["ARGENTUM_DECLARED_IMAGE"],"declared_arch":"linux/amd64","tools":{k:{"path":str(p),"sha256":sha_file(p)} for k,p in tools.items()},"os_release_sha256":sha_file(pathlib.Path("/etc/os-release")),"mountinfo_sha256":hashlib.sha256(pathlib.Path("/proc/self/mountinfo").read_bytes()).hexdigest(),"environment":{k:os.environ[k] for k in ALLOW_ENV if k in os.environ}}
    (OUT/"platform.json").write_bytes(canonical(platform))
    inputs=repo_inputs(); (OUT/"tracked-build-inputs.json").write_bytes(canonical(inputs))
    store_archive=pathlib.Path(os.environ["ARGENTUM_DEPENDENCY_STORE_ARCHIVE"]).resolve(strict=True)
    store_manifest=pathlib.Path(os.environ["ARGENTUM_DEPENDENCY_STORE_MANIFEST"]).resolve(strict=True)
    assert not os.access(store_archive,os.W_OK) and not os.access(store_manifest,os.W_OK)
    assert sha_file(BOOTSTRAP)=="e2181fa92f04c14f1bbb724d9d0f4240ccc1b856"
    gradle_home=pathlib.Path(os.environ["RUNNER_TEMP"]).resolve()/"industrial-r1-hermetic-gradle-home"
    accepted_store,store_receipt=restore_accepted_store(store_archive,store_manifest,gradle_home)
    first=run_gradle("first-offline-from-accepted-store",True,OUT/"classload-1.log",OUT/"runtime-1.json",gradle_home)
    graph1=json.loads((OUT/"runtime-1.json").read_text())
    assert not [d for d in graph1["dependencies"] if d.get("selected") is None]
    store_root=gradle_home
    store_before=file_inventory(store_root,exclude_names=frozenset({"daemon","workers","notifications","fileHashes","file-changes","buildOutputCleanup"}))
    (OUT/"store-before-offline.json").write_bytes(canonical(store_before))
    second=run_gradle("second-offline-repeat",True,OUT/"classload-2.log",OUT/"runtime-2.json",gradle_home)
    graph2=json.loads((OUT/"runtime-2.json").read_text())
    assert graph1["ordered_files"]==graph2["ordered_files"] and graph1["components"]==graph2["components"] and graph1["dependencies"]==graph2["dependencies"]
    for raw in graph2["ordered_files"]:
        rp=pathlib.Path(raw).resolve()
        assert rp.is_relative_to(ROOT) or rp.is_relative_to(gradle_home), ("runtime entry outside source/store",rp)
    for label in ("first-offline-from-accepted-store","second-offline-repeat"):
        proc_rows=json.loads((OUT/f"{label}-processes.json").read_text())
        workers=[r for r in proc_rows if any("Gradle Test Executor" in a for a in r["argv"])]
        assert workers, ("missing captured Gradle test worker",label)
        assert all(r["exe"]==str(JAVA) and r["exe_sha256"]==EXPECTED["java"] for r in workers), ("unexpected test-worker Java",label,workers)
    store_after=file_inventory(store_root,exclude_names=frozenset({"daemon","workers","notifications","fileHashes","file-changes","buildOutputCleanup"}))
    (OUT/"store-after-offline.json").write_bytes(canonical(store_after))
    class1,d1=parse_classlog(OUT/"classload-1.log"); class2,d2=parse_classlog(OUT/"classload-2.log")
    assert d1==d2,("generated/hidden normalized census differs",d1,d2)
    (OUT/"class-census-1.json").write_bytes(canonical(class1)); (OUT/"class-census-2.json").write_bytes(canonical(class2))
    entries=[archive_inventory(pathlib.Path(x).resolve()) for x in graph2["ordered_files"]]
    first_visible={}
    for ordinal,entry in enumerate(entries):
        for m in entry.get("members",[]):
            key=m["path"]
            m["visibility"]="FIRST_VISIBLE" if key not in first_visible else f"SHADOWED_BY_{first_visible[key]}"
            first_visible.setdefault(key,ordinal)
    runtime={"schema":"industrial-r1-complete-runtime-member-inventory-v1","ordered_entries":entries,"entry_count":len(entries),"visible_member_keys":len(first_visible)}
    (OUT/"runtime-member-inventory.json").write_bytes(canonical(runtime))
    jdk=file_inventory(JAVA.parent.parent); (OUT/"jdk-inventory.json").write_bytes(canonical(jdk))
    stdlib=pathlib.Path(sysconfig.get_path("stdlib")).resolve()
    py=file_inventory(stdlib,exclude_names=frozenset({"__pycache__","site-packages","dist-packages"})); (OUT/"python-stdlib-inventory.json").write_bytes(canonical(py))
    dyn=ldd_closure([str(p) for p in tools.values()]+[str(JAVA.parent.parent/"lib/server/libjvm.so")]); (OUT/"dynamic-library-closure.json").write_bytes(canonical(dyn))
    gradle_home_path=pathlib.Path(graph2["gradle_home"]).resolve()
    gradle_files=file_inventory(gradle_home_path); (OUT/"gradle-implementation-inventory.json").write_bytes(canonical(gradle_files))
    xml=ROOT/"ai/build/test-results/test/TEST-com.wingedsheep.ai.engine.IndustrialWasteV2FrozenResponderEquivalenceTest.xml"
    suite=ET.parse(xml).getroot(); assert int(suite.attrib["tests"])==10 and all(int(suite.attrib[k])==0 for k in ("failures","errors","skipped"))
    shutil.copy2(xml,OUT/xml.name)
    for raw in graph2["ordered_files"]:
        rp=pathlib.Path(raw).resolve()
        if rp.is_relative_to(gradle_home/"caches/modules-2"):
            rel=rp.relative_to(gradle_home).as_posix()
            if rel not in accepted_store or sha_file(rp)!=accepted_store[rel][1]:
                raise RuntimeError("resolved dependency is not exact accepted store byte: "+rel)
    summary={"schema":"industrial-r1-supplemental-hermetic-capture-v2","logical_source_commit":SOURCE,"logical_source_tree":SOURCE_TREE,"observed_head":cmd("git","rev-parse","HEAD"),"platform":platform,"tracked_input_count":len(inputs),"runtime_classpath_entries":len(entries),"runtime_visible_member_keys":len(first_visible),"first_launch":first,"offline_repeat":second,"class_census":{"first_sha256":d1,"second_sha256":d2,"equal":True,"hidden_first":sum(r["hidden"] for r in class1),"hidden_second":sum(r["hidden"] for r in class2),"policy":"PINNED_GENERATOR_INPUTS_REPEAT_NORMALIZED_EQUIVALENCE"},"immutable_store":{"source_artifact_id":11080705894,"outer_artifact_sha256":"1cb974fcb7cf4f5dd39889db4645a9943762f7c0dcd78674a57d6648001b476b","archive_sha256":STORE_ARCHIVE_SHA,"manifest_sha256":STORE_MANIFEST_SHA,"files":STORE_FILES,"mode":"VERIFIED_READ_ONLY_INPUT_RECONSTRUCTED_TO_WRITABLE_GRADLE_HOME","bootstrap_blob":"e2181fa92f04c14f1bbb724d9d0f4240ccc1b856"},"fixed_worker":{"class":TEST,"tests":10,"failures":0,"errors":0,"skipped":0},"official_seed_files_read":False,"official_counters":{"claims":0,"allocations":0,"games":0,"outcomes":0},"authority":"SUPPLEMENTAL_HERMETIC_CAPTURE_ORIGINAL_FOR_INDEPENDENT_REVIEW_ONLY"}
    (OUT/"summary.json").write_bytes(canonical(summary))
    manifest=[]
    for p in sorted(x for x in OUT.rglob("*") if x.is_file() and x.name!="artifact-manifest.json"):
        manifest.append({"path":p.relative_to(OUT).as_posix(),"bytes":p.stat().st_size,"sha256":sha_file(p)})
    (OUT/"artifact-manifest.json").write_bytes(canonical(manifest))
    print(json.dumps({"authority":summary["authority"],"runtime_entries":len(entries),"tracked_inputs":len(inputs),"class_census_equal":True,"store_files":len(preserve),"official_games":0},sort_keys=True))
if __name__=="__main__": main()
