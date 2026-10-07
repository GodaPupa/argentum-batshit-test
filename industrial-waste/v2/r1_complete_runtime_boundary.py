#!/usr/bin/env python3
"""Prospective Industrial R1 complete-runtime boundary core.

Expected membership comes only from a predeclared pre-launch spec and verified bytes.
Receiving capture observations are never accepted as manifest input.
"""
from __future__ import annotations
import hashlib,hmac,json,os,re,stat,zipfile
from pathlib import Path,PurePosixPath

RULE={
 "commit":"cf7e623d9ae2d7095820578d919c8cea64cb9596",
 "tree":"17e980edc5c97606a30cfdcda4b68410713081ec",
 "proposal_blob":"5da4912ad51c478b33588eab13af5e7bd95448d5",
 "independent_review_sha256":"b5d87410891e9acc445783a253fc5c85ad52dd2bd9d0e27294d351784d1e4c05",
 "independent_review_comment":6032123880,
}
REQUIRED_CATEGORIES={"BUILD_INPUT","DEPENDENCY_STORE","PROCESS_PATH","JDK","NATIVE","PYTHON","LAUNCHER","RESOURCE"}
FORBIDDEN_ENV={"PATH","PYTHONPATH","CLASSPATH","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","JAVA_OPTS","GRADLE_OPTS"}
HEX64=re.compile(r"[0-9a-f]{64}")

class BoundaryError(RuntimeError): pass

def require(v,m):
    if not v: raise BoundaryError(m)

def canonical(v): return (json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False)+"\n").encode()
def sha256_bytes(b): return hashlib.sha256(b).hexdigest()
def sha256_file(p): return sha256_bytes(Path(p).read_bytes())

def strict_json(raw):
    text=raw.decode("utf-8",errors="strict") if isinstance(raw,bytes) else raw
    def pairs(items):
        out={}
        for k,v in items:
            require(k not in out,"duplicate JSON key: "+k); out[k]=v
        return out
    return json.loads(text,object_pairs_hook=pairs)

def exact(obj,keys,label):
    require(set(obj)==set(keys),f"{label} keys mismatch: {sorted(set(obj)^set(keys))}")

def logical(s,label):
    require(isinstance(s,str) and s,label+" missing")
    p=PurePosixPath(s)
    require(not p.is_absolute() and ".." not in p.parts and "." not in p.parts and p.as_posix()==s and "//" not in s,label+" unsafe")
    return s

def physical(base,s):
    p=Path(s); p=p.resolve(strict=True) if p.is_absolute() else (base/p).resolve(strict=True)
    require(p.exists(),"missing path: "+s); return p

def class_key(member,jdk):
    if not member.endswith(".class") or member=="module-info.class": return None
    version=0; name=member
    if member.startswith("META-INF/versions/"):
        rest=member[len("META-INF/versions/"):]; ver,sep,name=rest.partition("/")
        require(sep and ver.isdigit(),"bad multi-release member")
        version=int(ver)
        if version>jdk: return None
    return name[:-6].replace("/","."),version

def inventory(base,spec,jdk):
    exact(spec,{"logical_name","path","category","kind","loader_id","ordinal"},"root")
    name=logical(spec["logical_name"],"logical_name")
    require(spec["category"] in REQUIRED_CATEGORIES|{"GENERATED_PRELAUNCH"},"bad category")
    require(spec["kind"] in {"file","directory","archive"},"bad kind")
    require(type(spec["ordinal"]) is int and spec["ordinal"]>=0,"bad ordinal")
    p=physical(base,spec["path"]); rows=[]
    def add(member,data,kind,ordinal,binary=None):
        row={"root":name,"root_ordinal":spec["ordinal"],"loader_id":spec["loader_id"],"origin_category":spec["category"],"entry_type":kind,"member":member,"member_ordinal":ordinal,"bytes":len(data),"sha256":sha256_bytes(data)}
        if binary: row.update(binary_name=binary[0],multi_release_version=binary[1])
        rows.append(row)
    if spec["kind"]=="file":
        require(p.is_file() and not p.is_symlink(),"file root invalid"); add("@file",p.read_bytes(),"FILE",0)
    elif spec["kind"]=="directory":
        require(p.is_dir() and not p.is_symlink(),"directory root invalid"); n=0
        for c in sorted(p.rglob("*")):
            require(not c.is_symlink(),"symlink forbidden")
            if c.is_dir(): continue
            require(c.is_file(),"unsupported filesystem node")
            rel=c.relative_to(p).as_posix(); add(rel,c.read_bytes(),"DIRECTORY_MEMBER",n,class_key(rel,jdk)); n+=1
    else:
        require(p.is_file() and not p.is_symlink() and zipfile.is_zipfile(p),"archive root invalid")
        with zipfile.ZipFile(p) as z:
            infos=[i for i in z.infolist() if not i.is_dir()]; names=[i.filename for i in infos]
            require(len(names)==len(set(names)),"duplicate ZIP member")
            add("@archive",p.read_bytes(),"ARCHIVE_BYTES",-1)
            for n,i in enumerate(infos):
                member=logical(i.filename,"archive member")
                mode=(i.external_attr>>16)&0xffff; require(stat.S_IFMT(mode)!=stat.S_IFLNK,"archive symlink forbidden")
                add(member,z.read(i),"ARCHIVE_MEMBER",n,class_key(member,jdk))
    return rows

def build_manifest(spec,base,environment):
    exact(spec,{"schema","authority","rule","source","platform","allowed_environment","loader_topology","inventory_roots","generated_prelaunch","ordered_processes","duplicate_binary_resolution"},"spec")
    require(spec["schema"]=="industrial-r1-complete-runtime-input-spec-v1","spec schema")
    require(spec["authority"]=="PROSPECTIVE_EXPECTED_BUILD_INPUT_ONLY","spec authority")
    require(spec["rule"]==RULE,"adopted rule binding mismatch")
    exact(spec["source"],{"commit","tree"},"source"); require(re.fullmatch(r"[0-9a-f]{40}",spec["source"]["commit"]),"source commit"); require(re.fullmatch(r"[0-9a-f]{40}",spec["source"]["tree"]),"source tree")
    exact(spec["platform"],{"container_image_digest","architecture","jdk_feature"},"platform")
    require(re.fullmatch(r"sha256:[0-9a-f]{64}",spec["platform"]["container_image_digest"]),"container digest")
    require(spec["platform"]["architecture"]=="linux/amd64" and type(spec["platform"]["jdk_feature"]) is int,"platform")
    require(environment==spec["allowed_environment"] and set(environment)==set(spec["allowed_environment"]),"environment mismatch")
    require(not(set(environment)&FORBIDDEN_ENV),"forbidden semantic environment")

    loaders=spec["loader_topology"]; require(isinstance(loaders,list) and loaders,"loader topology")
    ids=set()
    for x in loaders:
        exact(x,{"id","parent","delegation"},"loader"); require(x["id"] not in ids,"duplicate loader"); ids.add(x["id"])
        require(x["delegation"] in {"BOOTSTRAP","PARENT_FIRST","CHILD_FIRST"},"loader delegation")
    require(any(x["delegation"]=="BOOTSTRAP" and x["parent"] is None for x in loaders),"bootstrap loader absent")
    require(all(x["parent"] is None or x["parent"] in ids for x in loaders),"loader parent absent")

    roots=spec["inventory_roots"]; require(isinstance(roots,list) and roots,"roots")
    require([x["ordinal"] for x in roots]==list(range(len(roots))),"root ordinals")
    names=[x["logical_name"] for x in roots]; require(len(names)==len(set(names)),"duplicate root")
    cats={x["category"] for x in roots}; require(REQUIRED_CATEGORIES<=cats,"incomplete closure categories")
    require(all(x["loader_id"] in ids for x in roots),"root loader absent")
    entries=[]
    for x in roots: entries+=inventory(Path(base),x,spec["platform"]["jdk_feature"])

    gen_roots={r["root"] for r in entries if r["origin_category"]=="GENERATED_PRELAUNCH"}; declared=set()
    for g in spec["generated_prelaunch"]:
        exact(g,{"root","recipe_sha256","generator_sha256","input_sha256","first_output_sha256","second_output_sha256"},"generated")
        declared.add(g["root"]); require(all(HEX64.fullmatch(g[k]) for k in ("recipe_sha256","generator_sha256","first_output_sha256","second_output_sha256")),"generated digest")
        require(g["first_output_sha256"]==g["second_output_sha256"],"non-reproducible generated root")
        require(g["input_sha256"] and all(HEX64.fullmatch(x) for x in g["input_sha256"]),"generated inputs")
        rows=[r for r in entries if r["root"]==g["root"]]; require(rows and sha256_bytes(canonical(rows))==g["first_output_sha256"],"generated output mismatch")
    require(declared==gen_roots,"generated provenance mismatch")

    processes=spec["ordered_processes"]; require([p["ordinal"] for p in processes]==list(range(len(processes))),"process ordinals")
    for p in processes:
        exact(p,{"role","ordinal","executable_root","argv","cwd","classpath","module_path","loader_id"},"process")
        require(p["executable_root"] in names and p["loader_id"] in ids,"process binding")
        require(all(x in names for x in p["classpath"]+p["module_path"]),"process path binding"); logical(p["cwd"],"cwd")

    resolutions={(r["loader_id"],r["binary_name"]):r["winner_root"] for r in spec["duplicate_binary_resolution"]}
    require(len(resolutions)==len(spec["duplicate_binary_resolution"]),"duplicate resolution")
    groups={}
    for r in entries:
        if "binary_name" in r: groups.setdefault((r["loader_id"],r["binary_name"]),[]).append(r)
    effective=[]
    for key,rows in groups.items():
        byroot={}
        for r in rows:
            if r["root"] not in byroot or r["multi_release_version"]>byroot[r["root"]]["multi_release_version"]: byroot[r["root"]]=r
        selected=list(byroot.values())
        if len(selected)>1:
            winner=resolutions.get(key); require(winner is not None,"ambiguous duplicate binary")
            selected=[r for r in selected if r["root"]==winner]; require(len(selected)==1,"duplicate winner absent")
        effective+=selected
    require(not(set(resolutions)-set(groups)),"unused duplicate resolution")

    manifest={"schema":"industrial-r1-complete-runtime-manifest-v1","authority":"EXPECTED_PRELAUNCH_MANIFEST_FOR_INDEPENDENT_REVIEW_ONLY","rule":RULE,"source":spec["source"],"platform":spec["platform"],"environment":spec["allowed_environment"],"loader_topology":loaders,"ordered_processes":processes,"roots":[{k:x[k] for k in ("logical_name","category","kind","loader_id","ordinal")} for x in roots],"entries":sorted(entries,key=lambda r:(r["root_ordinal"],r["member_ordinal"],r["member"])),"effective_binaries":sorted(effective,key=lambda r:(r["loader_id"],r["binary_name"])),"generated_prelaunch":spec["generated_prelaunch"],"receiving_capture_membership_used":False,"complete_closure":True,"official_counters_delta":0}
    return manifest,sha256_bytes(canonical(manifest))

def verify_runtime_class_rows(rows,ordinary):
    require(isinstance(rows,list) and rows,"empty class census")
    for r in rows:
        if r.get("hidden") or r.get("source") in {"__JVM_LookupDefineClass__","__dynamic_proxy__","__ClassDefiner__"}:
            ok=(r.get("hidden") is True and r.get("source")=="__JVM_LookupDefineClass__" and str(r.get("name","")).startswith("java.lang.invoke.LambdaForm$") and r.get("generator_module")=="java.base" and r.get("generator_binary")=="java.lang.invoke.InvokerBytecodeGenerator" and r.get("definition_mechanism")=="MethodHandles.Lookup.hiddenClass")
            require(ok,"unpermitted generated/hidden class")
        else: require(r.get("name") in ordinary,"ordinary runtime class absent from manifest")
    return True

def _key(path):
    p=Path(path); require(p.is_file() and not p.is_symlink(),"attestation key invalid"); require(stat.S_IMODE(p.stat().st_mode)&0o077==0,"attestation key permissions"); b=p.read_bytes(); require(len(b)>=32,"attestation key too short"); return b

def sign_attestation(payload,key_path):
    exact(payload,{"schema","manifest_sha256","source_commit","source_tree","rule_review_sha256","runtime_receipt_sha256","worker_exe_sha256","worker_pid","worker_start_ticks","launch_id","nonce","execution_owner"},"attestation")
    require(payload["schema"]=="industrial-r1-prepared-worker-attestation-v1" and payload["rule_review_sha256"]==RULE["independent_review_sha256"],"attestation binding")
    require(all(HEX64.fullmatch(payload[k]) for k in ("manifest_sha256","rule_review_sha256","runtime_receipt_sha256","worker_exe_sha256","nonce")),"attestation digest")
    require(re.fullmatch(r"[0-9a-f]{40}",payload["source_commit"]) and re.fullmatch(r"[0-9a-f]{40}",payload["source_tree"]),"attestation source")
    require(type(payload["worker_pid"]) is int and payload["worker_pid"]>0 and type(payload["worker_start_ticks"]) is int and payload["worker_start_ticks"]>0,"attestation process")
    require(re.fullmatch(r"[A-Za-z0-9._-]{16,128}",payload["launch_id"]),"launch id")
    sig=hmac.new(_key(key_path),canonical(payload),hashlib.sha256).hexdigest(); return {"payload":payload,"hmac_sha256":sig}

def verify_and_consume_attestation(envelope,key_path,expected,consumed_dir):
    exact(envelope,{"payload","hmac_sha256"},"envelope"); signed=sign_attestation(envelope["payload"],key_path)
    require(hmac.compare_digest(envelope["hmac_sha256"],signed["hmac_sha256"]),"attestation signature")
    p=envelope["payload"]
    for k in ("manifest_sha256","source_commit","source_tree","runtime_receipt_sha256","execution_owner"): require(p[k]==expected[k],"attestation "+k)
    d=Path(consumed_dir); require(d.is_absolute() and d.is_dir() and not d.is_symlink(),"consumed directory")
    marker=d/(p["launch_id"]+".consumed")
    try: fd=os.open(marker,os.O_WRONLY|os.O_CREAT|os.O_EXCL|getattr(os,"O_NOFOLLOW",0),0o600)
    except FileExistsError as e: raise BoundaryError("replayed launch identity") from e
    try: os.write(fd,canonical({"launch_id":p["launch_id"],"nonce":p["nonce"],"manifest_sha256":p["manifest_sha256"]})); os.fsync(fd)
    finally: os.close(fd)
    dirfd=os.open(d,os.O_RDONLY)
    try: os.fsync(dirfd)
    finally: os.close(dirfd)
    return p
