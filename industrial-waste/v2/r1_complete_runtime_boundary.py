#!/usr/bin/env python3
"""Prospective Industrial R1 complete-runtime boundary core.

Expected membership comes only from a predeclared pre-launch spec and verified bytes.
Receiving capture observations are never accepted as manifest input.
"""
from __future__ import annotations
import hashlib,json,re,stat,zipfile
from pathlib import Path,PurePosixPath

RULE={
 "commit":"cf7e623d9ae2d7095820578d919c8cea64cb9596",
 "tree":"17e980edc5c97606a30cfdcda4b68410713081ec",
 "proposal_blob":"5da4912ad51c478b33588eab13af5e7bd95448d5",
 "independent_review_sha256":"b5d87410891e9acc445783a253fc5c85ad52dd2bd9d0e27294d351784d1e4c05",
 "independent_review_comment":6032123880,
}
REQUIRED_CATEGORIES={"BUILD_INPUT","DEPENDENCY_STORE","PROCESS_PATH","JDK","NATIVE","PYTHON","LAUNCHER","RESOURCE"}
FORBIDDEN_ENV={"PATH","PYTHONPATH","PYTHONHOME","PYTHONSTARTUP","PYTHONINSPECT","CLASSPATH","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","JAVA_OPTS","GRADLE_OPTS","GRADLE_USER_HOME","LD_PRELOAD","LD_LIBRARY_PATH","LD_AUDIT","BASH_ENV","ENV"}
HEX64=re.compile(r"[0-9a-f]{64}")

class BoundaryError(RuntimeError): pass

def require(v,m):
    if not v: raise BoundaryError(m)

def canonical(v): return (json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False,allow_nan=False)+"\n").encode()
def sha256_bytes(b): return hashlib.sha256(b).hexdigest()
def sha256_file(p): return sha256_bytes(Path(p).read_bytes())

def strict_json(raw):
    text=raw.decode("utf-8",errors="strict") if isinstance(raw,bytes) else raw
    def pairs(items):
        out={}
        for k,v in items:
            require(k not in out,"duplicate JSON key: "+k); out[k]=v
        return out
    def constant(value): raise BoundaryError("non-finite JSON number: "+value)
    return json.loads(text,object_pairs_hook=pairs,parse_constant=constant)

def exact(obj,keys,label):
    require(set(obj)==set(keys),f"{label} keys mismatch: {sorted(set(obj)^set(keys))}")

def logical(s,label):
    require(isinstance(s,str) and s and "\\" not in s and not any(ord(c)<32 for c in s),label+" missing or ambiguous")
    p=PurePosixPath(s)
    require(not p.is_absolute() and ".." not in p.parts and "." not in p.parts and p.as_posix()==s and "//" not in s,label+" unsafe")
    return s

def physical(base,s):
    # Inspect every lexical component before resolving: resolve() erases symlinks.
    base=Path(base)
    require(base.is_absolute(),"base must be absolute")
    logical(s,"physical root")
    p=base/s
    for node in (p,*p.parents):
        require(not node.is_symlink(),"symlink root or ancestor forbidden")
    require(p.exists(),"missing path: "+s)
    require(p.resolve(strict=True).is_relative_to(base.resolve(strict=True)),"root escapes base")
    return p

def class_key(member,jdk):
    if not member.endswith(".class") or member=="module-info.class": return None
    version=0; name=member
    if member.startswith("META-INF/versions/"):
        rest=member[len("META-INF/versions/"):]; ver,sep,name=rest.partition("/")
        require(sep and ver.isdigit(),"bad multi-release member")
        version=int(ver)
        require(version>=9 and str(version)==ver,"bad multi-release version")
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
        require(p.is_file() and not p.is_symlink() and p.stat().st_nlink==1,"file root invalid or alias"); add("@file",p.read_bytes(),"FILE",0)
    elif spec["kind"]=="directory":
        require(p.is_dir() and not p.is_symlink(),"directory root invalid"); n=0
        for c in sorted(p.rglob("*")):
            require(not c.is_symlink(),"symlink forbidden")
            if c.is_dir(): continue
            require(c.is_file() and c.stat().st_nlink==1,"unsupported filesystem node or hardlink alias")
            rel=c.relative_to(p).as_posix(); add(rel,c.read_bytes(),"DIRECTORY_MEMBER",n,class_key(rel,jdk)); n+=1
    else:
        require(p.is_file() and not p.is_symlink() and p.stat().st_nlink==1 and zipfile.is_zipfile(p),"archive root invalid")
        with zipfile.ZipFile(p) as z:
            all_infos=z.infolist(); names=[i.filename for i in all_infos]
            require(len(names)==len(set(names)),"duplicate ZIP member")
            for i in all_infos:
                logical(i.filename[:-1] if i.is_dir() else i.filename,"archive member")
                mode=(i.external_attr>>16)&0xffff
                require(stat.S_IFMT(mode) in {0,stat.S_IFREG,stat.S_IFDIR},"archive special node forbidden")
            infos=[i for i in all_infos if not i.is_dir()]
            mr=False
            if "META-INF/MANIFEST.MF" in names:
                header=z.read("META-INF/MANIFEST.MF").decode("utf-8",errors="strict").replace("\r\n","\n")
                require("\r" not in header,"ambiguous JAR manifest")
                main=header.split("\n\n",1)[0].replace("\n ","")
                attrs={}
                for line in main.splitlines():
                    key,sep,value=line.partition(": ")
                    require(sep and key.lower() not in attrs,"ambiguous JAR manifest attribute")
                    attrs[key.lower()]=value
                mr=attrs.get("multi-release","").lower()=="true"
            add("@archive",p.read_bytes(),"ARCHIVE_BYTES",-1)
            for n,i in enumerate(infos):
                member=logical(i.filename,"archive member")
                mode=(i.external_attr>>16)&0xffff; require(stat.S_IFMT(mode)!=stat.S_IFLNK,"archive symlink forbidden")
                binary=None if member.startswith("META-INF/versions/") and not mr else class_key(member,jdk)
                add(member,z.read(i),"ARCHIVE_MEMBER",n,binary)
    return rows

def build_inventory_candidate(spec,base,environment):
    """Inventory only. Category coverage does NOT prove complete runtime closure.

    This source stage cannot emit a canonical manifest or attest a real worker.
    See qualification scope for the independently reviewed platform/VM gate.
    """
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
    require(all(isinstance(k,str) and isinstance(v,str) and "\x00" not in k+v and "=" not in k for k,v in environment.items()),"invalid environment")

    loaders=spec["loader_topology"]; require(isinstance(loaders,list) and loaders,"loader topology")
    ids=set()
    for x in loaders:
        exact(x,{"id","parent","delegation"},"loader"); require(x["id"] not in ids,"duplicate loader"); ids.add(x["id"])
        require(x["delegation"] in {"BOOTSTRAP","PARENT_FIRST","CHILD_FIRST"},"loader delegation")
    require(any(x["delegation"]=="BOOTSTRAP" and x["parent"] is None for x in loaders),"bootstrap loader absent")
    require(all(x["parent"] is None or x["parent"] in ids for x in loaders),"loader parent absent")
    require(sum(x["delegation"]=="BOOTSTRAP" for x in loaders)==1,"exactly one bootstrap required")
    parents={x["id"]:x["parent"] for x in loaders}
    for x in loaders:
        require((x["parent"] is None)==(x["delegation"]=="BOOTSTRAP"),"loader root policy")
        seen=set(); current=x["id"]
        while current is not None:
            require(current not in seen,"loader cycle"); seen.add(current); current=parents[current]

    roots=spec["inventory_roots"]; require(isinstance(roots,list) and roots,"roots")
    require([x["ordinal"] for x in roots]==list(range(len(roots))),"root ordinals")
    names=[x["logical_name"] for x in roots]; require(len(names)==len(set(names)),"duplicate root")
    cats={x["category"] for x in roots}; require(REQUIRED_CATEGORIES<=cats,"incomplete closure categories")
    require(all(x["loader_id"] in ids for x in roots),"root loader absent")
    paths=[physical(Path(base),x["path"]) for x in roots]
    for i,p in enumerate(paths):
        for q in paths[:i]:
            require(p!=q and not p.is_relative_to(q) and not q.is_relative_to(p),"overlapping or aliased roots")
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

    processes=spec["ordered_processes"]; require(processes and [p["ordinal"] for p in processes]==list(range(len(processes))),"process ordinals")
    require(len({p["role"] for p in processes})==len(processes),"duplicate process role")
    for p in processes:
        exact(p,{"role","ordinal","executable_root","argv","cwd","classpath","module_path","loader_id"},"process")
        require(p["executable_root"] in names and p["loader_id"] in ids,"process binding")
        require(all(x in names for x in p["classpath"]+p["module_path"]),"process path binding"); logical(p["cwd"],"cwd")
        require(isinstance(p["argv"],list) and p["argv"] and all(isinstance(a,str) and "\x00" not in a for a in p["argv"]),"process argv")
        require(not any(a.startswith(("-javaagent","-agentlib","-agentpath","-Xbootclasspath","--patch-module","-XX:OnError","-XX:OnOutOfMemoryError","-XX:+EnableDynamicAgentLoading")) for a in p["argv"]),"forbidden executable extension")
        require(len(set(p["classpath"]+p["module_path"]))==len(p["classpath"]+p["module_path"]),"duplicate process path")

    for r in spec["duplicate_binary_resolution"]:
        exact(r,{"loader_id","binary_name","winner_root"},"binary resolution")
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

    manifest={"schema":"industrial-r1-runtime-inventory-candidate-v2","authority":"INVENTORY_ONLY_NO_MANIFEST_ATTESTATION_OR_ADMISSION_AUTHORITY","rule":RULE,"source":spec["source"],"platform":spec["platform"],"environment":spec["allowed_environment"],"loader_topology":loaders,"ordered_processes":processes,"roots":[{k:x[k] for k in ("logical_name","path","category","kind","loader_id","ordinal")} for x in roots],"entries":sorted(entries,key=lambda r:(r["root_ordinal"],r["member_ordinal"],r["member"])),"effective_binaries":sorted(effective,key=lambda r:(r["loader_id"],r["binary_name"])),"generated_prelaunch":spec["generated_prelaunch"],"receiving_capture_membership_used":False,"complete_closure":False,"official_counters_delta":0}
    return manifest,sha256_bytes(canonical(manifest))

def build_manifest(spec,base,environment):
    raise BoundaryError("INCOMPLETE_CLOSURE: independently qualified build/import/native/loader graphs and lifetime enforcement are unavailable; no canonical manifest")

def verify_runtime_class_rows(rows,ordinary):
    """Legacy name-only input is deliberately rejected, including LambdaForm names."""
    raise BoundaryError("INCOMPLETE_RUNTIME_PROVENANCE: name-only census cannot authenticate class definitions")

def sign_attestation(payload,key_path):
    raise BoundaryError("INCOMPLETE_AUTHENTICATED_CHANNEL: no prepared-worker attestation")

def verify_and_consume_attestation(envelope,key_path,expected,consumed_dir):
    raise BoundaryError("INCOMPLETE_AUTHENTICATED_CHANNEL: no admission or launch consumption")

def closure_failure_record():
    """The only publishable complete-runtime result until the missing gates close."""
    return {
        "schema": "industrial-r1-complete-runtime-failure-v2",
        "status": "INCOMPLETE_CLOSURE",
        "rule": dict(RULE),
        "missing_evidence": [
            "independently qualified complete resolved build and runtime graphs",
            "exact accepted store and JDK/native/Python/launcher byte bindings",
            "deterministic generated/transient reproduction with executed recipe evidence",
            "authenticated definition-time ordinary and hidden provenance",
            "immutable mounts and executable sources for the entire worker lifetime",
            "authenticated process channel with receiver-bound fresh challenge",
        ],
        "canonical_manifest_emitted": False,
        "prepared_worker_attestation_emitted": False,
        "admission_authorized": False,
        "official_counters_delta": 0,
    }
