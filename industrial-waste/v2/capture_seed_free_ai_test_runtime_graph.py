#!/usr/bin/env python3
from __future__ import annotations
import hashlib, json, pathlib, subprocess, textwrap

ROOT = pathlib.Path(".").resolve()
OUT = ROOT / "build/reports/industrial-ai-test-runtime-graph"

INIT = r"""
import groovy.json.JsonOutput
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

gradle.projectsEvaluated {
    def p = rootProject.project(":ai")
    p.tasks.register("industrialR1CaptureTestRuntime") {
        doLast {
            def cfg = p.configurations.getByName("testRuntimeClasspath")
            def orderedFiles = cfg.files.toList().collect { it.absolutePath }
            def rr = cfg.incoming.resolutionResult
            def components = rr.allComponents.collect { c ->
                [id: c.id.displayName, reason: c.selectionReason.description]
            }.sort { a,b -> a.id <=> b.id }
            def dependencies = rr.allDependencies.collect { d ->
                def row = [from: d.from.id.displayName, requested: d.requested.displayName]
                if (d instanceof ResolvedDependencyResult) {
                    row.selected = d.selected.id.displayName
                    row.failure = null
                } else if (d instanceof UnresolvedDependencyResult) {
                    row.selected = null
                    row.failure = d.failure?.message
                }
                row
            }.sort { a,b ->
                (a.from <=> b.from) ?: (a.requested <=> b.requested) ?: ((a.selected ?: "") <=> (b.selected ?: ""))
            }
            def artifacts = cfg.resolvedConfiguration.resolvedArtifacts.collect { a ->
                [
                    component: a.moduleVersion.id.toString(),
                    name: a.name,
                    type: a.type,
                    classifier: a.classifier,
                    path: a.file.absolutePath
                ]
            }.sort { a,b ->
                (a.component <=> b.component) ?: (a.name <=> b.name) ?: ((a.classifier ?: "") <=> (b.classifier ?: ""))
            }
            def out = [
                schema: "industrial-r1-gradle-ai-test-runtime-raw-v1",
                project: ":ai",
                configuration: "testRuntimeClasspath",
                ordered_files: orderedFiles,
                components: components,
                dependencies: dependencies,
                artifacts: artifacts
            ]
            def target = new File(System.getProperty("industrial.capture.out"))
            target.parentFile.mkdirs()
            target.text = JsonOutput.prettyPrint(JsonOutput.toJson(out)) + "\n"
        }
    }
}
"""

def sha_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def sha_file(path: pathlib.Path) -> str:
    return sha_bytes(path.read_bytes())

def canonical(value) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n").encode()

def inventory_path(path: pathlib.Path) -> dict:
    if path.is_symlink():
        raise SystemExit(f"symlink forbidden in captured classpath: {path}")
    if path.is_file():
        return {
            "kind": "file",
            "length": path.stat().st_size,
            "sha256": sha_file(path),
        }
    if not path.is_dir():
        raise SystemExit(f"missing classpath entry: {path}")
    members = []
    for child in sorted(path.rglob("*")):
        if child.is_symlink():
            raise SystemExit(f"symlink forbidden inside captured classpath: {child}")
        if child.is_file():
            rel = child.relative_to(path).as_posix()
            data = child.read_bytes()
            members.append({"path": rel, "length": len(data), "sha256": sha_bytes(data)})
    return {
        "kind": "directory",
        "members": members,
        "canonical_inventory_length": len(canonical(members)),
        "canonical_inventory_sha256": sha_bytes(canonical(members)),
    }

def display_path(path: pathlib.Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        home = pathlib.Path.home().resolve()
        try:
            return "$HOME/" + path.relative_to(home).as_posix()
        except ValueError:
            return str(path)

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    init = OUT / "capture.init.gradle"
    raw = OUT / "raw-gradle-graph.json"
    init.write_text(textwrap.dedent(INIT))
    subprocess.run([
        "./gradlew", "-I", str(init), "--no-daemon", "--console=plain",
        f"-Dindustrial.capture.out={raw}",
        ":ai:industrialR1CaptureTestRuntime"
    ], cwd=ROOT, check=True)
    data = json.loads(raw.read_text())
    if data.get("project") != ":ai" or data.get("configuration") != "testRuntimeClasspath":
        raise SystemExit("unexpected Gradle capture target")
    unresolved = [d for d in data["dependencies"] if d.get("selected") is None]
    if unresolved:
        raise SystemExit("unresolved dependency graph: " + json.dumps(unresolved, sort_keys=True))
    ordered = []
    seen = set()
    for ordinal, raw_path in enumerate(data["ordered_files"]):
        p = pathlib.Path(raw_path).resolve()
        key = str(p)
        if key in seen:
            raise SystemExit("duplicate ordered classpath entry: " + key)
        seen.add(key)
        inv = inventory_path(p)
        ordered.append({
            "ordinal": ordinal,
            "path": display_path(p),
            **inv,
        })
    artifact_rows = []
    for row in data["artifacts"]:
        p = pathlib.Path(row["path"]).resolve()
        inv = inventory_path(p)
        artifact_rows.append({
            "component": row["component"],
            "name": row["name"],
            "type": row["type"],
            "classifier": row.get("classifier"),
            "path": display_path(p),
            **inv,
        })
    result = {
        "schema": "industrial-r1-seed-free-ai-test-runtime-graph-capture-v1",
        "source_commit": subprocess.check_output(["git","rev-parse","HEAD"], cwd=ROOT, text=True).strip(),
        "source_tree": subprocess.check_output(["git","rev-parse","HEAD^{tree}"], cwd=ROOT, text=True).strip(),
        "gradle_project": ":ai",
        "configuration": "testRuntimeClasspath",
        "ordered_classpath": ordered,
        "components": data["components"],
        "dependencies": data["dependencies"],
        "resolved_artifacts": artifact_rows,
        "official_seed_files_read": False,
        "official_counters": {"allocations": 0, "games": 0, "outcomes": 0},
        "authority": "CANDIDATE_AI_TEST_RUNTIME_GRAPH_SLICE_ONLY",
        "not_captured": [
            "Gradle launcher and daemon implementation classpaths",
            "Gradle test-worker implementation classpath outside :ai:testRuntimeClasspath",
            "JDK image/modules/executables/native closure",
            "OS/container/native dynamic-loader closure",
            "Python stdlib/import closure",
            "shell/just/launcher executable closure",
            "generated/hidden class policy and loader topology",
            "immutable store/lifetime enforcement",
            "authenticated prepared-worker attestation",
            "complete expected-manifest adoption"
        ]
    }
    (OUT / "capture.json").write_text(json.dumps(result, sort_keys=True, indent=2) + "\n")
    print(json.dumps({
        "classpath_entries": len(ordered),
        "components": len(result["components"]),
        "dependencies": len(result["dependencies"]),
        "artifacts": len(artifact_rows),
        "authority": result["authority"]
    }, indent=2))

if __name__ == "__main__":
    main()
