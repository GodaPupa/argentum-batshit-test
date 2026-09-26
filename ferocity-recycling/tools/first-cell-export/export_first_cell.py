#!/usr/bin/env python3
"""Read-only exact-source artifact export. No gameplay, allocation, discovery or overwrite mode."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
GETTERS_SHA256 = "bfdd39f056dc4501c7bd1e54b78d02b778f8aecedc1803a534da47bb2085e39d"
WATCHDOG_SHA256 = "803a03aae3f63ffb114d9a8b56d4bf108b2f5f71f051057e204220f5f52adb37"
CODEC_PATH = "gym/src/test/kotlin/com/wingedsheep/gym/ferocity/FerocityJournalCodec.kt"
INLINE_SOURCES = {
    "ferocity/inline-source/GiftDsl.kt": "mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/dsl/mechanics/GiftDsl.kt",
    "ferocity/inline-source/CreateTokenExecutor.kt": "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/token/CreateTokenExecutor.kt",
    "ferocity/inline-source/TokenCreationReplacementHelper.kt": "rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/token/TokenCreationReplacementHelper.kt",
    "ferocity/inline-source/StackResolver.kt": "rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt",
}
PLAN_KEYS = {"schemaVersion", "scope", "repositoryPath", "sourceCommit", "sourceTreeSha256",
    "compiledInputsPath", "compiledInputsSha256", "serializerSha256", "classPath",
    "javaExecutable", "javaExecutableSha256", "policySha256", "protocolSha256", "dependencies"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read_json(path: Path) -> object:
    def unique(pairs: list[tuple[str, object]]) -> dict:
        result = {}
        for key, value in pairs:
            require(key not in result, "Duplicate JSON field")
            result[key] = value
        return result
    return json.loads(path.read_bytes(), object_pairs_hook=unique,
        parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Nonfinite JSON")))


def exact_path(text: str, *, directory: bool = False) -> Path:
    path = Path(text)
    require(path.is_absolute() and path == path.resolve(strict=True), "Path must be absolute, normalized and free of symbolic links")
    require(path.is_dir() if directory else path.is_file(), "Required regular path missing")
    return path


def sha_text(value: object) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def source_path(root: Path, relative: str) -> Path:
    require(isinstance(relative, str) and relative and not Path(relative).is_absolute() and
        ".." not in Path(relative).parts, "Invalid relative source path")
    path = exact_path(str(root / relative))
    require(path.is_relative_to(root), "Source path leaves repository")
    return path


def durable_new(path: Path, data: bytes) -> None:
    with path.open("xb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def git(root: Path, *args: str) -> bytes:
    return subprocess.run(["git", "-C", str(root), *args], check=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30).stdout


def validate(plan: dict) -> tuple[dict, dict]:
    require(set(plan) == PLAN_KEYS and plan["schemaVersion"] == 1 and
        plan["scope"] == "FEROCITY_FIRST_CELL_OFFLINE_EXPORT", "Unsupported export plan schema")
    root = exact_path(plan["repositoryPath"], directory=True)
    require(re.fullmatch(r"[0-9a-f]{40}", plan["sourceCommit"]) is not None, "Invalid logical source commit")
    git(root, "merge-base", "--is-ancestor", plan["sourceCommit"], "HEAD")
    compiled_path = source_path(root, plan["compiledInputsPath"])
    require(file_digest(compiled_path) == plan["compiledInputsSha256"], "Compiled-source map changed")
    source_map = read_json(compiled_path)
    require(isinstance(source_map, dict) and source_map and len(source_map) <= 100000 and
        all(sha_text(value) for value in source_map.values()), "Invalid compiled-source map")
    require(digest(canonical(source_map)) == plan["sourceTreeSha256"], "Logical source-tree digest mismatch")
    changed = set(git(root, "diff", "--no-ext-diff", "--name-only", "-z", plan["sourceCommit"], "--").decode().split("\0"))
    untracked = set(git(root, "ls-files", "--others", "--exclude-standard", "-z").decode().split("\0"))
    require(not (set(source_map) & (changed | untracked)), "Compiled inputs are not the published logical source version")
    for relative, expected in source_map.items():
        require(file_digest(source_path(root, relative)) == expected, "Compiled source bytes differ")
    require(source_map.get(CODEC_PATH) == plan["serializerSha256"], "Serializer is not the compiled journal codec")
    java = exact_path(plan["javaExecutable"])
    require(file_digest(java) == plan["javaExecutableSha256"], "Java executable differs")
    require(sha_text(plan["protocolSha256"]), "Protocol digest missing")
    require(set(plan["policySha256"]) == {"artifact-control", "red-madness"} and
        all(sha_text(value) for value in plan["policySha256"].values()), "Exact two policy digests required")
    require(isinstance(plan["dependencies"], dict) and
        all(isinstance(key, str) and key and sha_text(value) for key, value in plan["dependencies"].items()), "Invalid dependencies")
    cp = plan["classPath"]
    require(isinstance(cp, list) and 1 <= len(cp) <= 1024 and len({row["path"] for row in cp}) == len(cp), "Invalid ordered classpath")
    for row in cp:
        require(set(row) == {"path", "directory", "sha256", "files"} and isinstance(row["directory"], bool) and
            sha_text(row["sha256"]) and isinstance(row["files"], dict), "Invalid runtime path pin")
        path = exact_path(row["path"], directory=row["directory"])
        require(os.pathsep not in str(path), "Classpath path contains separator")
        if row["directory"]:
            observed = {}
            for child in sorted(path.rglob("*")):
                require(not child.is_symlink(), "Symbolic classpath child")
                if child.is_file():
                    observed[child.relative_to(path).as_posix()] = file_digest(child)
                else:
                    require(child.is_dir(), "Special classpath child")
            require(observed == row["files"] and digest(canonical(observed)) == row["sha256"], "Classpath directory differs")
        else:
            require(not row["files"] and file_digest(path) == row["sha256"], "Classpath JAR differs")

    require(file_digest(HERE / "GETTERS.json") == GETTERS_SHA256, "Reviewed finite getter manifest changed")
    getters = read_json(HERE / "GETTERS.json")
    names, decks = set(), {}
    for row in getters["decks"]:
        path = source_path(root, row["path"])
        require(file_digest(path) == row["sha256"], "Frozen first-cell deck bytes changed")
        deck = read_json(path)
        entries = deck["main"]
        require(sum(item["count"] for item in entries) == 60 and len({item["name"] for item in entries}) == len(entries), "Invalid exact main deck")
        names.update(item["name"] for item in entries)
        decks[deck["id"]] = digest("".join(f'{item["count"]} {item["name"]}\n' for item in sorted(entries, key=lambda item: item["name"])).encode())
    names.update({"Blood", "Clue", "Wicked Role"})
    require(len(names) == 36 and len(getters["definitions"]) == 36 and
        {row["name"] for row in getters["definitions"]} == names, "Finite getter closure mismatch")
    for row in getters["definitions"]:
        text = source_path(root, row["sourcePath"]).read_text()
        require(row["sourcePath"] in source_map, "Getter source is not compiled and pinned")
        package = re.search(r"^package\s+([\w.]+)", text, re.M).group(1)
        value = row["getter"][3:]
        require(re.search(r"\bval\s+" + re.escape(value) + r"(?:\s*:\s*CardDefinition)?\s*=\s*(?:card|basicLand)\(\"" +
            re.escape(row["name"]) + r"\"\)", text) is not None, "Getter declaration differs from reviewed card source")
        suffix = "PredefinedTokens" if row["singleton"] else Path(row["sourcePath"]).stem + "Kt"
        require(row["className"] == package + "." + suffix, "Getter package/class mismatch")
    require(set(INLINE_SOURCES.values()) <= set(source_map), "Incomplete compiled Fish recipe sources")
    return dict(plan, sourceMap=source_map, definitions=getters["definitions"], expectedNames=sorted(names),
        externalDeckAliases=getters["externalDeckAliases"], inlineSources=INLINE_SOURCES, deckHashes=decks), getters


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--plan", required=True)
    parser.add_argument("--plan-sha256", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    plan_path = exact_path(args.plan)
    require(sha_text(args.plan_sha256) and file_digest(plan_path) == args.plan_sha256, "Export plan hash differs")
    plan = read_json(plan_path)
    resolved, _ = validate(plan)
    output = Path(args.output)
    require(output.is_absolute() and output.parent == output.parent.resolve(strict=True), "Output parent must be existing and normalized")
    output.mkdir(exist_ok=False)
    original_sources = {name: file_digest(HERE / name) for name in ("export_first_cell.py", "FerocityFinitePoolExport.java", "GETTERS.json")}
    watchdog_path = HERE.parent / "trial_watchdog.py"
    require(file_digest(watchdog_path) == WATCHDOG_SHA256, "Qualified watchdog source differs")
    durable_new(output / "export-plan.json", plan_path.read_bytes())
    resolved["exportPlanSha256"] = args.plan_sha256
    raw = canonical(resolved)
    durable_new(output / "resolved-plan.json", raw)
    command = [plan["javaExecutable"], "-Xmx2048m", "-cp", os.pathsep.join(row["path"] for row in plan["classPath"]),
        str(HERE / "FerocityFinitePoolExport.java"), str(output / "resolved-plan.json"), digest(raw), str(output)]
    durable_new(output / "launch.json", canonical({"scope": "OFFLINE_EXPORT_NO_GAME_NO_ENTROPY", "argv": command,
        "sourceSha256": original_sources, "wallSeconds": 120, "termGraceSeconds": 5, "planSha256": args.plan_sha256}))
    # Reuse the exact resource-boundary supervisor after its fixed gate qualifies, including cancellation during Popen.
    # This wrapper introduces no second process-ownership implementation.
    module_spec = importlib.util.spec_from_file_location("ferocity_export_watchdog", watchdog_path)
    require(module_spec is not None and module_spec.loader is not None, "Watchdog import unavailable")
    watchdog = importlib.util.module_from_spec(module_spec)
    sys.modules[module_spec.name] = watchdog
    module_spec.loader.exec_module(watchdog)
    specification = {"schema_version": 1, "run_id": "EXPORT-" + digest(raw)[:24], "argv": command,
        "cwd": plan["repositoryPath"], "wall_seconds": 120, "term_grace_seconds": 5, "journal_path": None,
        "pinned_files": {plan["javaExecutable"]: plan["javaExecutableSha256"],
            str(plan_path): args.plan_sha256, str(output / "resolved-plan.json"): digest(raw),
            **{str(HERE / name): value for name, value in original_sources.items()}},
        "supervisor_sha256": WATCHDOG_SHA256, "inspection_limit_bytes": 32 * 1024 * 1024,
        "file_size_limit_bytes": 128 * 1024 * 1024, "minimum_free_bytes": 768 * 1024 * 1024}
    durable_new(output / "supervision-spec.json", canonical(specification))
    process_result = watchdog.supervise(watchdog.WatchdogSpec.parse(specification), output / "process")
    durable_new(output / "process-result.json", canonical(process_result))
    require(file_digest(plan_path) == args.plan_sha256 and
        all(file_digest(HERE / name) == value for name, value in original_sources.items()), "Exporter or plan changed; preserve this attempt")
    validate(plan)
    durable_new(output / "artifact-manifest.json", canonical({"scope": "OFFLINE_EXPORT_NO_GAME_NO_ENTROPY",
        "artifacts": {path.relative_to(output).as_posix(): file_digest(path) for path in sorted(output.rglob("*")) if path.is_file()},
        "experimentalAcceptance": False}))
    require(process_result["classification"] == "EXIT_ZERO_REQUIRES_ENGINE_REPLAY" and
        (output / "export-receipt.json").is_file(), "Export failed; all created files retained, no retry performed")
    print(json.dumps({"status": "OFFLINE_EXPORT_RELOADED_REQUIRES_FINAL_ADMISSION", "output": str(output),
        "processResultSha256": file_digest(output / "process-result.json"), "games": 0, "allocatedSeeds": 0}))


if __name__ == "__main__":
    main()
