"""Canonical fail-closed structures for the prospective complete Industrial R1 runtime boundary.

This module is deterministic receiving infrastructure only. It does not generate expected bytes,
inspect official corpus rows, create claims, initialize workers, or authorize execution.
"""
from __future__ import annotations
import hashlib, json, re
from dataclasses import dataclass
from typing import Any

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
SCHEMA = "industrial-r1-complete-runtime-manifest-v1"
ATTESTATION_SCHEMA = "industrial-r1-prepared-worker-attestation-v1"

TOP_KEYS = {
    "schema","source_commit","source_tree","build_inputs","dependency_graph","process_paths",
    "inventories","platform","launch","loader_policy","manifest_digest"
}
INPUT_KEYS = {"path","length","sha256"}
NODE_KEYS = {"id","kind","path","length","sha256"}
EDGE_KEYS = {"from","to"}
PATH_KEYS = {"process","loader","ordinal","kind","path","length","sha256"}
INVENTORY_KEYS = {"artifact_id","kind","path","length","sha256","members"}
MEMBER_KEYS = {"path","kind","length","sha256","loader"}
PLATFORM_KEYS = {"jdk_digest","os_image_digest","architecture","python_digest","shell_digest","just_digest"}
LAUNCH_KEYS = {"working_directory","commands","allowed_environment"}
LOADER_KEYS = {"allowed_loaders","generated_class_policy","agents_allowed","dynamic_attach_allowed","runtime_compilation_allowed"}
ATTEST_KEYS = {
    "schema","manifest_digest","source_commit","worker_id","execution_owner","nonce",
    "issued_sequence","process_paths_digest","inventory_digest"
}

def _pairs(pairs):
    out={}
    for key,value in pairs:
        if key in out: raise ValueError(f"duplicate JSON key: {key}")
        out[key]=value
    return out

def loads_strict(text: str) -> dict[str,Any]:
    value=json.loads(text,object_pairs_hook=_pairs)
    if not isinstance(value,dict): raise ValueError("root JSON object required")
    return value

def canonical_bytes(value: Any) -> bytes:
    return (json.dumps(value,sort_keys=True,separators=(",",":"),ensure_ascii=False,allow_nan=False)+"\n").encode()

def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def _exact_keys(obj: dict[str,Any], allowed:set[str], label:str):
    extra=set(obj)-allowed
    if extra: raise ValueError(f"{label} unknown fields: {sorted(extra)}")

def _digest(value: Any,label:str):
    if not isinstance(value,str) or not SHA256_RE.fullmatch(value):
        raise ValueError(f"{label} must be lowercase sha256")

def _commit(value: Any,label:str):
    if not isinstance(value,str) or not COMMIT_RE.fullmatch(value):
        raise ValueError(f"{label} must be git commit/tree id")

def _path(value: Any,label:str):
    if not isinstance(value,str) or not value or value.startswith("/") or ".." in value.split("/"):
        raise ValueError(f"{label} must be canonical relative path")

def _length(value: Any,label:str):
    if not isinstance(value,int) or isinstance(value,bool) or value < 0:
        raise ValueError(f"{label} must be nonnegative integer")

def _records(rows,label,keys):
    if not isinstance(rows,list): raise ValueError(f"{label} list required")
    for i,row in enumerate(rows):
        if not isinstance(row,dict): raise ValueError(f"{label}[{i}] object required")
        _exact_keys(row,keys,f"{label}[{i}]")
    return rows

def validate_manifest(manifest: dict[str,Any], *, require_self_digest=True) -> dict[str,Any]:
    _exact_keys(manifest,TOP_KEYS,"manifest")
    if manifest.get("schema") != SCHEMA: raise ValueError("manifest schema")
    _commit(manifest.get("source_commit"),"source_commit")
    _commit(manifest.get("source_tree"),"source_tree")

    inputs=_records(manifest.get("build_inputs"),"build_inputs",INPUT_KEYS)
    seen=set()
    for i,row in enumerate(inputs):
        _path(row.get("path"),f"build_inputs[{i}].path"); _length(row.get("length"),"length"); _digest(row.get("sha256"),"sha256")
        if row["path"] in seen: raise ValueError("duplicate build input path")
        seen.add(row["path"])

    graph=manifest.get("dependency_graph")
    if not isinstance(graph,dict) or set(graph)!={"nodes","edges"}: raise ValueError("dependency_graph fields")
    nodes=_records(graph["nodes"],"nodes",NODE_KEYS); node_ids=set()
    for i,row in enumerate(nodes):
        if not isinstance(row.get("id"),str) or not row["id"]: raise ValueError("dependency node id")
        if row["id"] in node_ids: raise ValueError("duplicate dependency node id")
        node_ids.add(row["id"]); _path(row["path"],"node path"); _length(row["length"],"node length"); _digest(row["sha256"],"node sha256")
        if row.get("kind") not in {"archive","directory","tool","native","jdk","python"}: raise ValueError("dependency node kind")
    for row in _records(graph["edges"],"edges",EDGE_KEYS):
        if row.get("from") not in node_ids or row.get("to") not in node_ids: raise ValueError("dependency edge references unknown node")

    paths=_records(manifest.get("process_paths"),"process_paths",PATH_KEYS)
    order_by_process={}
    keyset=set()
    for row in paths:
        for k in ("process","loader","kind"):
            if not isinstance(row.get(k),str) or not row[k]: raise ValueError(f"process path {k}")
        _path(row["path"],"process path"); _length(row["length"],"process length"); _digest(row["sha256"],"process sha256")
        if not isinstance(row.get("ordinal"),int) or row["ordinal"] < 0: raise ValueError("process ordinal")
        key=(row["process"],row["loader"],row["ordinal"])
        if key in keyset: raise ValueError("duplicate process path ordinal")
        keyset.add(key); order_by_process.setdefault((row["process"],row["loader"]),[]).append(row["ordinal"])
    for label,ordinals in order_by_process.items():
        if sorted(ordinals) != list(range(len(ordinals))): raise ValueError(f"noncontiguous process path order: {label}")

    inventories=_records(manifest.get("inventories"),"inventories",INVENTORY_KEYS)
    artifact_ids=set(); binary_names={}
    for inv in inventories:
        if not isinstance(inv.get("artifact_id"),str) or not inv["artifact_id"]: raise ValueError("inventory artifact_id")
        if inv["artifact_id"] in artifact_ids: raise ValueError("duplicate inventory artifact_id")
        artifact_ids.add(inv["artifact_id"]); _path(inv["path"],"inventory path"); _length(inv["length"],"inventory length"); _digest(inv["sha256"],"inventory sha256")
        if inv.get("kind") not in {"archive","directory"}: raise ValueError("inventory kind")
        members=_records(inv.get("members"),"members",MEMBER_KEYS); member_paths=set()
        for m in members:
            _path(m["path"],"member path"); _length(m["length"],"member length"); _digest(m["sha256"],"member sha256")
            if m["path"] in member_paths: raise ValueError("duplicate inventory member")
            member_paths.add(m["path"])
            if not isinstance(m.get("loader"),str) or not m["loader"]: raise ValueError("member loader")
            if m.get("kind") not in {"class","resource","native","generated"}: raise ValueError("member kind")
            if m["kind"]=="class":
                prior=binary_names.setdefault((m["loader"],m["path"]),inv["artifact_id"])
                if prior != inv["artifact_id"]: raise ValueError("duplicate class binary across same loader")

    platform=manifest.get("platform")
    if not isinstance(platform,dict): raise ValueError("platform object")
    _exact_keys(platform,PLATFORM_KEYS,"platform")
    for k in PLATFORM_KEYS: _digest(platform.get(k),f"platform.{k}")
    if not isinstance(platform.get("architecture"),str): raise ValueError("architecture")

    launch=manifest.get("launch")
    if not isinstance(launch,dict): raise ValueError("launch object")
    _exact_keys(launch,LAUNCH_KEYS,"launch")
    _path(launch.get("working_directory"),"working_directory")
    commands=launch.get("commands")
    if not isinstance(commands,list) or not commands or any(not isinstance(x,list) or not x or any(not isinstance(y,str) or not y for y in x) for x in commands):
        raise ValueError("launch commands")
    env=launch.get("allowed_environment")
    if not isinstance(env,dict) or any(not isinstance(k,str) or not isinstance(v,str) for k,v in env.items()): raise ValueError("allowed_environment")
    forbidden={"JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","CLASSPATH","PYTHONPATH","JAVA_OPTS","GRADLE_OPTS"}
    if forbidden & set(env): raise ValueError("forbidden environment influence")

    policy=manifest.get("loader_policy")
    if not isinstance(policy,dict): raise ValueError("loader_policy object")
    _exact_keys(policy,LOADER_KEYS,"loader_policy")
    loaders=policy.get("allowed_loaders")
    if not isinstance(loaders,list) or not loaders or len(loaders)!=len(set(loaders)) or any(not isinstance(x,str) or not x for x in loaders):
        raise ValueError("allowed_loaders")
    if policy.get("generated_class_policy") not in {"ELIMINATED","PINNED_GENERATOR_INPUTS"}: raise ValueError("generated class policy")
    if any(policy.get(k) is not False for k in ("agents_allowed","dynamic_attach_allowed","runtime_compilation_allowed")):
        raise ValueError("dynamic loading policy must fail closed")
    declared_loaders=set(loaders)
    if any(m["loader"] not in declared_loaders for inv in inventories for m in inv["members"]):
        raise ValueError("inventory member uses undeclared loader")

    supplied=manifest.get("manifest_digest")
    _digest(supplied,"manifest_digest")
    without=dict(manifest); without["manifest_digest"]="0"*64
    computed=sha256_bytes(canonical_bytes(without))
    if require_self_digest and supplied != computed: raise ValueError("manifest self digest mismatch")
    return {"manifest_digest":supplied,"build_inputs":len(inputs),"dependency_nodes":len(nodes),"process_paths":len(paths),"inventories":len(inventories)}

@dataclass(frozen=True)
class AttestationContext:
    manifest_digest:str
    source_commit:str
    execution_owner:str
    worker_id:str
    minimum_sequence:int
    used_nonces:frozenset[str]

def validate_attestation(value:dict[str,Any], ctx:AttestationContext)->None:
    _exact_keys(value,ATTEST_KEYS,"attestation")
    if value.get("schema") != ATTESTATION_SCHEMA: raise ValueError("attestation schema")
    if value.get("manifest_digest") != ctx.manifest_digest: raise ValueError("wrong manifest")
    if value.get("source_commit") != ctx.source_commit: raise ValueError("wrong source")
    if value.get("execution_owner") != ctx.execution_owner: raise ValueError("wrong owner")
    if value.get("worker_id") != ctx.worker_id: raise ValueError("wrong worker")
    nonce=value.get("nonce")
    if not isinstance(nonce,str) or not nonce or nonce in ctx.used_nonces: raise ValueError("stale/replayed nonce")
    seq=value.get("issued_sequence")
    if not isinstance(seq,int) or seq < ctx.minimum_sequence: raise ValueError("stale attestation sequence")
    _digest(value.get("process_paths_digest"),"process_paths_digest")
    _digest(value.get("inventory_digest"),"inventory_digest")
