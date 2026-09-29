#!/usr/bin/env python3
import hashlib, json, os, pathlib, shutil, subprocess, sys, sysconfig

def sha256(path):
    h=hashlib.sha256()
    with open(path,"rb") as f:
        for chunk in iter(lambda:f.read(1024*1024),b""):
            h.update(chunk)
    return h.hexdigest()

def canonical_digest(rows):
    h=hashlib.sha256()
    for row in sorted(rows, key=lambda x: json.dumps(x,sort_keys=True,separators=(",",":"))):
        h.update(json.dumps(row,sort_keys=True,separators=(",",":")).encode())
        h.update(b"\n")
    return h.hexdigest()

def file_row(path, base=None):
    p=pathlib.Path(path)
    rp=p.resolve()
    st=rp.stat()
    name=str(rp.relative_to(base)) if base is not None and rp.is_relative_to(base) else str(rp)
    return {"path":name,"resolved_path":str(rp),"bytes":st.st_size,"sha256":sha256(rp)}

def ldd_paths(path):
    proc=subprocess.run(["ldd",str(path)],check=True,text=True,capture_output=True)
    out=set()
    for raw in proc.stdout.splitlines():
        line=raw.strip()
        if not line:
            continue
        candidate=None
        if "=>" in line:
            rhs=line.split("=>",1)[1].strip()
            if rhs.startswith("/"):
                candidate=rhs.split(" ",1)[0]
        elif line.startswith("/"):
            candidate=line.split(" ",1)[0]
        if candidate and pathlib.Path(candidate).is_file():
            out.add(str(pathlib.Path(candidate).resolve()))
    return out

roots=[]
for name in ["java","python3","bash","just"]:
    p=shutil.which(name)
    if not p:
        raise RuntimeError(f"missing executable: {name}")
    roots.append(str(pathlib.Path(p).resolve()))
java_home=pathlib.Path(os.environ["JAVA_HOME"]).resolve()
libjvm=java_home/"lib/server/libjvm.so"
if not libjvm.is_file():
    raise RuntimeError("missing libjvm")
roots.append(str(libjvm.resolve()))

seen=set()
queue=list(roots)
while queue:
    current=queue.pop(0)
    if current in seen:
        continue
    seen.add(current)
    try:
        deps=ldd_paths(current)
    except subprocess.CalledProcessError as e:
        raise RuntimeError(f"ldd failed for {current}: {e.stderr}") from e
    for dep in sorted(deps):
        if dep not in seen:
            queue.append(dep)

dynamic_rows=[file_row(p) for p in sorted(seen)]
external_dynamic=[r for r in dynamic_rows if not pathlib.Path(r["resolved_path"]).is_relative_to(java_home)]

stdlib=pathlib.Path(sysconfig.get_path("stdlib")).resolve()
if not stdlib.is_dir():
    raise RuntimeError("python stdlib root missing")
stdlib_rows=[]
for p in sorted(stdlib.rglob("*")):
    rel=p.relative_to(stdlib)
    if any(part in {"site-packages","dist-packages","__pycache__"} for part in rel.parts):
        continue
    if p.is_symlink():
        stdlib_rows.append({"path":str(rel),"kind":"symlink","target":os.readlink(p)})
    elif p.is_file():
        row=file_row(p,stdlib)
        row["kind"]="file"
        stdlib_rows.append(row)

result={
  "schema":"industrial-r1-seed-free-os-python-closure-v1",
  "authority":"CANDIDATE_OS_PYTHON_CLOSURE_SLICE_ONLY",
  "source_commit":subprocess.check_output(["git","rev-parse","HEAD"],text=True).strip(),
  "source_tree":subprocess.check_output(["git","rev-parse","HEAD^{tree}"],text=True).strip(),
  "dynamic_roots":roots,
  "dynamic_nodes":len(dynamic_rows),
  "external_dynamic_nodes":len(external_dynamic),
  "dynamic_canonical_sha256":canonical_digest(dynamic_rows),
  "external_dynamic_canonical_sha256":canonical_digest(external_dynamic),
  "dynamic_files":dynamic_rows,
  "python":{
    "version":sys.version,
    "executable":str(pathlib.Path(sys.executable).resolve()),
    "stdlib_root":str(stdlib),
    "stdlib_nodes":len(stdlib_rows),
    "stdlib_canonical_sha256":canonical_digest(stdlib_rows),
    "stdlib_files":stdlib_rows,
    "sysconfig_platform":sysconfig.get_platform(),
  },
  "not_captured":[
    "Gradle launcher/daemon/test-worker implementation classpaths",
    "generated/hidden class policy and loader topology",
    "immutable store/lifetime enforcement",
    "authenticated prepared-worker attestation",
    "complete expected-manifest adoption"
  ],
  "official_seed_files_read":False,
  "official_counters":{"allocations":0,"games":0,"outcomes":0}
}
print(json.dumps(result,sort_keys=True,indent=2))
