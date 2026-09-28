from __future__ import annotations
import hashlib,json,os,pathlib,stat,zipfile
from r1_complete_runtime_manifest import SCHEMA,canonical_bytes,sha256_bytes,validate_manifest

def _sha(data:bytes)->str:return hashlib.sha256(data).hexdigest()
def _safe_rel(name:str)->str:
    p=pathlib.PurePosixPath(name)
    if not name or p.is_absolute() or ".." in p.parts or "\\" in name: raise ValueError(f"unsafe path {name}")
    return str(p)
def _file(path:pathlib.Path):
    if path.is_symlink(): raise ValueError(f"symlink forbidden: {path}")
    data=path.read_bytes(); return len(data),_sha(data)
def _dir_inventory(root:pathlib.Path,loader:str):
    if root.is_symlink(): raise ValueError("inventory root symlink")
    rows=[]
    for p in sorted(root.rglob("*")):
        if p.is_symlink(): raise ValueError(f"symlink forbidden: {p}")
        if p.is_file():
            rel=_safe_rel(p.relative_to(root).as_posix()); data=p.read_bytes()
            rows.append({"path":rel,"kind":"class" if rel.endswith(".class") else "resource","length":len(data),"sha256":_sha(data),"loader":loader})
    canon=canonical_bytes(rows); return rows,len(canon),_sha(canon)
def _zip_inventory(path:pathlib.Path,loader:str):
    data=path.read_bytes(); rows=[]; seen=set()
    with zipfile.ZipFile(path) as z:
        bad=z.testzip()
        if bad: raise ValueError(f"zip CRC failure: {bad}")
        for info in z.infolist():
            name=_safe_rel(info.filename)
            if name in seen: raise ValueError(f"duplicate zip member: {name}")
            seen.add(name)
            if stat.S_ISLNK((info.external_attr>>16)&0o170000): raise ValueError(f"zip symlink forbidden: {name}")
            if name.startswith("META-INF/versions/"): raise ValueError("multi-release archive requires separately reviewed selection policy")
            if info.is_dir(): continue
            body=z.read(info)
            rows.append({"path":name,"kind":"class" if name.endswith(".class") else "resource","length":len(body),"sha256":_sha(body),"loader":loader})
    return rows,len(data),_sha(data)
def build(spec:dict,base:pathlib.Path)->dict:
    build_inputs=[]
    for rel in spec["build_inputs"]:
        rel=_safe_rel(rel); n,h=_file(base/rel); build_inputs.append({"path":rel,"length":n,"sha256":h})
    nodes=[]; node_map={}
    for row in spec["dependency_nodes"]:
        rel=_safe_rel(row["path"]); p=base/rel; kind=row["kind"]
        if kind=="directory": members,n,h=_dir_inventory(p,row["loader"])
        elif kind=="archive": members,n,h=_zip_inventory(p,row["loader"])
        else:
            n,h=_file(p); members=[]
        out={"id":row["id"],"kind":kind,"path":rel,"length":n,"sha256":h}; nodes.append(out);node_map[row["id"]]=(out,members,row["loader"])
    paths=[]
    for row in spec["process_paths"]:
        node,members,loader=node_map[row["node_id"]]
        if row["loader"]!=loader: raise ValueError("process loader/node loader mismatch")
        paths.append({"process":row["process"],"loader":row["loader"],"ordinal":row["ordinal"],"kind":node["kind"],"path":node["path"],"length":node["length"],"sha256":node["sha256"]})
    inventories=[]
    for aid in spec["inventory_artifact_ids"]:
        node,members,loader=node_map[aid]
        if node["kind"] not in {"archive","directory"}: raise ValueError("inventory artifact must archive/directory")
        inventories.append({"artifact_id":aid,"kind":node["kind"],"path":node["path"],"length":node["length"],"sha256":node["sha256"],"members":members})
    manifest={"schema":SCHEMA,"source_commit":spec["source_commit"],"source_tree":spec["source_tree"],"build_inputs":build_inputs,
      "dependency_graph":{"nodes":nodes,"edges":spec["edges"]},"process_paths":paths,"inventories":inventories,
      "platform":spec["platform"],"launch":spec["launch"],"loader_policy":spec["loader_policy"],"manifest_digest":"0"*64}
    manifest["manifest_digest"]=sha256_bytes(canonical_bytes(manifest))
    validate_manifest(manifest)
    return manifest
