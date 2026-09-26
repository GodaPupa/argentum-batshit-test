#!/usr/bin/env python3
"""Assemble an offline export plan from exact supplied evidence; never launch or allocate."""
from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPORTER = HERE.parent / "export_first_cell.py"
EXPORTER_SHA = "29d753603a7b25a6410d834060872146c799ec0c4008b6ab978007aa55b57b69"
MATERIAL_KEYS = {"schemaVersion", "scope", "repositoryPath", "sourceCommit", "compiledInputs",
    "classPathFile", "javaExecutable", "policies", "protocolFiles", "dependencies", "exportOutput"}


def load_exporter():
    import hashlib
    if hashlib.sha256(EXPORTER.read_bytes()).hexdigest() != EXPORTER_SHA:
        raise ValueError("Reviewed exporter source changed")
    spec = importlib.util.spec_from_file_location("ferocity_offline_export_plan_validation", EXPORTER)
    if spec is None or spec.loader is None:
        raise ValueError("Reviewed export validator unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--material", required=True)
    parser.add_argument("--material-sha256", required=True)
    parser.add_argument("--output", required=True, help="New plan directory outside repository and all classpath directories")
    args = parser.parse_args()
    e = load_exporter()
    material_path = e.exact_path(args.material)
    e.require(e.sha_text(args.material_sha256) and e.file_digest(material_path) == args.material_sha256,
        "Material digest changed or was not supplied")
    material = e.read_json(material_path)
    e.require(isinstance(material, dict) and set(material) == MATERIAL_KEYS and
        material["schemaVersion"] == 1 and material["scope"] == "FEROCITY_FIRST_CELL_EXPORT_MATERIAL",
        "Unsupported material schema")
    e.require(all(value is not None for value in material.values()), "Unfilled material is not an executable plan")
    root = e.exact_path(material["repositoryPath"], directory=True)

    def file_ref(value, *, relative=False):
        e.require(isinstance(value, dict) and set(value) == {"path", "sha256"} and
            e.sha_text(value["sha256"]), "Exact file reference missing")
        path = e.source_path(root, value["path"]) if relative else e.exact_path(value["path"])
        e.require(e.file_digest(path) == value["sha256"], "Referenced material bytes differ")
        return path

    source_path = file_ref(material["compiledInputs"], relative=True)
    source_map = e.read_json(source_path)
    e.require(isinstance(source_map, dict) and source_map and
        all(e.sha_text(value) for value in source_map.values()), "Complete compiled-input map missing")
    class_path_file = file_ref(material["classPathFile"])
    class_path = e.read_json(class_path_file)
    java = file_ref(material["javaExecutable"])
    policies = material["policies"]
    e.require(isinstance(policies, dict) and set(policies) == {"artifact-control", "red-madness"},
        "Both exact policy source maps required")
    for policy in policies.values():
        e.require(isinstance(policy, dict) and set(policy) == {"version", "files"} and
            isinstance(policy["version"], str) and bool(policy["version"].strip()) and
            isinstance(policy["files"], dict) and policy["files"], "Incomplete policy identity")
        for name, value in policy["files"].items():
            e.require(e.sha_text(value) and source_map.get(name) == value,
                "Policy input is not the actual compiled input")
            file_ref({"path": name, "sha256": value}, relative=True)
    protocol = material["protocolFiles"]
    e.require(isinstance(protocol, dict) and protocol, "Effective protocol file map missing")
    for name, value in protocol.items():
        file_ref({"path": name, "sha256": value}, relative=True)
    plan = {"schemaVersion": 1, "scope": "FEROCITY_FIRST_CELL_OFFLINE_EXPORT",
        "repositoryPath": str(root), "sourceCommit": material["sourceCommit"],
        "sourceTreeSha256": e.digest(e.canonical(source_map)),
        "compiledInputsPath": material["compiledInputs"]["path"],
        "compiledInputsSha256": material["compiledInputs"]["sha256"],
        "serializerSha256": source_map.get(e.CODEC_PATH), "classPath": class_path,
        "javaExecutable": str(java), "javaExecutableSha256": material["javaExecutable"]["sha256"],
        "policySha256": {name: e.digest(e.canonical(value)) for name, value in policies.items()},
        "protocolSha256": e.digest(e.canonical(protocol)), "dependencies": material["dependencies"]}
    e.validate(plan)  # Read-only exact sources, declared source version, JVM, classpath and fixed closure.

    def new_external_directory(value):
        e.require(isinstance(value, str), "Output directory must be explicitly supplied")
        path = Path(value)
        e.require(path.is_absolute() and path == path.parent.resolve(strict=True) / path.name and
            not path.exists() and not path.is_symlink(), "Output must be new, absolute and normalized")
        e.require(not path.is_relative_to(root), "Plan and export outputs must be outside the source repository")
        for entry in class_path:
            pinned = Path(entry["path"])
            e.require(not (entry["directory"] and path.is_relative_to(pinned)) and path != pinned,
                "Output would modify a pinned classpath entry")
        return path

    output = new_external_directory(args.output)
    export_output = new_external_directory(material["exportOutput"])
    e.require(not output.is_relative_to(export_output) and not export_output.is_relative_to(output),
        "Assembly and export directories must be disjoint")
    e.require(e.file_digest(material_path) == args.material_sha256 and
        e.file_digest(class_path_file) == material["classPathFile"]["sha256"], "Material changed during validation")
    output.mkdir(exist_ok=False)
    plan_bytes = e.canonical(plan)
    e.durable_new(output / "export-plan.json", plan_bytes)
    e.durable_new(output / "material.json", material_path.read_bytes())
    e.durable_new(output / "PLAN_ASSEMBLY_RECEIPT.json", e.canonical({
        "scope": "OFFLINE_PLAN_ASSEMBLY_NO_EXPORT_NO_GAME_NO_ENTROPY",
        "status": "EXACT_MATERIAL_VALIDATED_REQUIRES_PLAN_AND_GATE_REVIEW",
        "materialSha256": args.material_sha256, "classPathFileSha256": material["classPathFile"]["sha256"],
        "exportPlanSha256": e.digest(plan_bytes), "exportOutput": str(export_output),
        "assemblySourceSha256": e.file_digest(Path(__file__).resolve()), "exporterSourceSha256": EXPORTER_SHA,
        "derivedFields": ["sourceTreeSha256", "serializerSha256", "policySha256", "protocolSha256"],
        "gateReceiptsSelected": False, "experimentalAcceptance": False, "games": 0, "allocatedSeeds": 0}))
    print(e.canonical({"status": "PLAN_READY_FOR_REVIEW", "plan": str(output / "export-plan.json"),
        "sha256": e.digest(plan_bytes), "exportOutput": str(export_output), "exportExecuted": False}).decode())


if __name__ == "__main__":
    main()
