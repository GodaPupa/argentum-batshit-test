#!/usr/bin/env python3
"""Run project-selected deterministic real-engine tests through the repository's just gate.

No gameplay is initialized and no experimental seeds are generated here. Existing
fixed scenario seeds remain fixture identities, never randomized development data.
Each test invocation preserves its XML before another invocation can replace it.
An absent prerequisite, empty XML, skipped test, compile error, or assertion failure
fails visibly. The first failure stops dependent validation; it is not rerolled.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = "d0c78bb4cca79b7402ba65bd62b5cb621230a054"
TESTS = {'FerocityOfTheHuntScenarioTest': 'rules-engine',
 'RatsSweeperInteractionTest': 'rules-engine',
 'ToxinAnalysisScenarioTest': 'mtg-sets/2024/tests',
 'KrarkClanShamanScenarioTest': 'mtg-sets/2003-2007/tests',
 'NotDeadAfterAllScenarioTest': 'mtg-sets/2023/tests',
 'RefurbishedFamiliarScenarioTest': 'mtg-sets/2024/tests',
 'MalevolentRumbleScenarioTest': 'mtg-sets/2024/tests',
 'FanaticalOfferingScenarioTest': 'mtg-sets/2023/tests',
 'IchorWellspringScenarioTest': 'mtg-sets/2008-2016/tests',
 'ShamblingGhastScenarioTest': 'mtg-sets/2017-2022/tests',
 'BloodFountainScenarioTest': 'mtg-sets/2017-2022/tests',
 'MakeshiftMunitionsScenarioTest': 'mtg-sets/2017-2022/tests',
 'GrixisAffinityAgentDecisionTest': 'ai',
 'CryptRatsScenarioTest': 'mtg-sets/1993-1999/tests',
 'GlintHawkScenarioTest': 'mtg-sets/2008-2016/tests',
 'KorSkyfisherScenarioTest': 'mtg-sets/2008-2016/tests',
 'JourneyToNowhereScenarioTest': 'mtg-sets/2008-2016/tests',
 'WitchsCottageScenarioTest': 'mtg-sets/2017-2022/tests',
 'BonePickerScenarioTest': 'mtg-sets/2017-2022/tests',
 'CutDownScenarioTest': 'mtg-sets/2017-2022/tests',
 'DefileScenarioTest': 'mtg-sets/2017-2022/tests',
 'EvisceratorsInsightScenarioTest': 'mtg-sets/2024/tests',
 'DispatchScenarioTest': 'mtg-sets/2008-2016/tests',
 'FerocityPrereleaseDefinitionIdentityTest': 'rules-engine',
 'FerocityTrialJournalTest': 'gym',
 'FerocityObservationBoundaryTest': 'gym',
 'FerocityStackSourceObservationTest': 'gym',
 'ArtifactControlPolicyTest': 'gym',
 'FerocityCardDefinitionBundleTest': 'gym',
 'FerocityDiagnosticsTest': 'gym',
 'CardDefinitionSnapshotTest': 'mtg-sets',
 'FerocityBloodActivationTest': 'gym',
 'FerocityDevelopmentAdmissionTest': 'gym',
 'FerocityInlineTokenProvenanceTest': 'gym',
 'FerocityTriggerOrderObservationTest': 'gym',
 'FerocityWickedRoleSourceTest': 'gym',
 'RedMadnessPilotScenarioTest': 'gym',
 'FieryTemperScenarioTest': 'mtg-sets/2000-2002/tests',
 'LavaDartScenarioTest': 'mtg-sets/2000-2002/tests',
 'ThoughtcastScenarioTest': 'mtg-sets/2003-2007/tests',
 'GalvanicBlastScenarioTest': 'mtg-sets/2008-2016/tests',
 'NihilSpellbombScenarioTest': 'mtg-sets/2008-2016/tests',
 'CastDownScenarioTest': 'mtg-sets/2017-2022/tests',
 'KessigFlamebreatherScenarioTest': 'mtg-sets/2017-2022/tests',
 'ReckonersBargainScenarioTest': 'mtg-sets/2017-2022/tests',
 'GiftChosenOpponentTest': 'mtg-sets/2024/tests',
 'GrabThePrizeScenarioTest': 'mtg-sets/2024/tests',
 'HighwayRobberyScenarioTest': 'mtg-sets/2024/tests',
 'SazacapsBrewScenarioTest': 'mtg-sets/2024/tests',
 'SneakySnackerScenarioTest': 'mtg-sets/2024/tests',
 'MaraudingMakoScenarioTest': 'mtg-sets/2025/tests',
 'ActivationPriorityScenarioTest': 'rules-engine',
 'BandingTrampleDrainScenarioTest': 'rules-engine',
 'BatchMayQuestionTest': 'rules-engine',
 'CombatAssignmentCurrentRulesTest': 'rules-engine',
 'CombatDamageAssignmentTest': 'rules-engine',
 'CombatResolutionBoardTest': 'rules-engine',
 'DamageSourceIdentityScenarioTest': 'rules-engine',
 'DamageSourceTriggerScenarioTest': 'rules-engine',
 'ManaAbilityColorChoicePipelineTest': 'rules-engine',
 'MoveToZoneEffectExecutorTest': 'rules-engine',
 'NestedManaPaymentBoundaryScenarioTest': 'rules-engine',
 'PaidFlashbackStackExitScenarioTest': 'rules-engine',
 'PlayerTargetRevalidationScenarioTest': 'rules-engine',
 'PredicateEvaluatorRecordTest': 'rules-engine',
 'SelfEntersTappedOffStackEntryScenarioTest': 'rules-engine',
 'TargetedDepartedSourceLkiTest': 'rules-engine',
 'TriggerOrderingConcessionScenarioTest': 'rules-engine',
 'TriggerOrderingScenarioTest': 'rules-engine',
 'GiftSpellResolutionTest': 'rules-engine',
 'GiftPromisedAtCastTimeTest': 'rules-engine',
 'CardLintTest': 'mtg-sets',
 'FacadeBoundaryTest': 'mtg-sets',
 'PostBlockDeclarationPriorityScenarioTest': 'rules-engine',
 'DeclareBlockersPriorityWindowTest': 'rules-engine',
 'MultiDefenderCombatTest': 'rules-engine',
 'TwoHeadedGiantCombatTest': 'rules-engine',
 'PostBlockDeclarationSessionTest': 'game-server',
 'TwoHeadedGiantSessionTest': 'game-server'}
DEFAULT_TESTS = ('FerocityOfTheHuntScenarioTest',
 'RatsSweeperInteractionTest',
 'ToxinAnalysisScenarioTest',
 'KrarkClanShamanScenarioTest',
 'NotDeadAfterAllScenarioTest',
 'RefurbishedFamiliarScenarioTest',
 'MalevolentRumbleScenarioTest',
 'FanaticalOfferingScenarioTest',
 'IchorWellspringScenarioTest',
 'ShamblingGhastScenarioTest',
 'BloodFountainScenarioTest',
 'MakeshiftMunitionsScenarioTest',
 'GrixisAffinityAgentDecisionTest')
BUILD_FILES = (
    "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar", "build.gradle.kts", "settings.gradle.kts",
    "gradle.properties", "buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts",
    "gradlew", "gradlew.bat", "justfile", "scripts/test-class", "scripts/gradle-locked",
)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stamp() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def xml_timestamp_freshness(root: ET.Element, started: str, finished: str) -> dict:
    """Reject restored reports even when Gradle failed before executing a test task."""
    raw = root.attrib.get("timestamp")
    detail = {"timestamp": raw, "within_invocation_window": False}
    try:
        if raw is None:
            raise ValueError("XML has no timestamp")
        value = dt.datetime.fromisoformat(raw.replace("Z", "+00:00"))
        if value.tzinfo is None:
            raise ValueError("XML timestamp has no timezone")
        detail["within_invocation_window"] = dt.datetime.fromisoformat(started) <= value <= dt.datetime.fromisoformat(finished)
        if not detail["within_invocation_window"]:
            detail["reason"] = "XML timestamp is outside this invocation; retained but not fresh evidence"
    except ValueError as exc:
        detail["reason"] = str(exc)
    return detail


def output(command: list[str]) -> str:
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(f"{command[0]} failed ({result.returncode}): {result.stderr.strip()}")
    return (result.stdout + result.stderr).strip()


def source_roots(classes: list[str] | tuple[str, ...]) -> list[Path]:
    roots = [ROOT / name / "src/main" for name in ("mtg-sdk", "rules-engine", "ai", "gym", "buildSrc")]
    # Server tests consume these additional project modules at runtime. Keep this
    # closure conditional so an engine-only receipt does not claim a server gate.
    if any(TESTS[name] == "game-server" for name in classes):
        roots.extend(ROOT / name / "src/main" for name in ("game-server", "mtg-search", "oracle-assay"))
    roots.append(ROOT / "rules-engine/src/testFixtures")
    roots.extend((ROOT / "mtg-sets").glob("**/src/main"))
    roots.extend(ROOT / TESTS[name] / "src/test" for name in classes)
    return roots


def is_compiled_input(name: str, classes: list[str] | tuple[str, ...]) -> bool:
    """Use path membership, not existence: a deleted source must fail the dirty guard."""
    if name in BUILD_FILES or Path(name).name in {"build.gradle.kts", "settings.gradle.kts"}:
        return True
    if name.startswith("mtg-sets/") and "/src/main/" in name:
        return True
    return any(name.startswith(str(root.relative_to(ROOT)) + "/") for root in source_roots(classes))


def compiled_inputs(classes: list[str] | tuple[str, ...]) -> dict[str, str]:
    """Pin Kotlin/Java/KTS and classpath resources, including untracked fixtures."""
    paths = {ROOT / name for name in BUILD_FILES}
    paths.update(ROOT.glob("**/build.gradle.kts"))
    paths.update(ROOT.glob("**/settings.gradle.kts"))
    for base in source_roots(classes):
        if base.exists():
            paths.update(p for p in base.rglob("*") if p.is_file())
    return {
        str(path.relative_to(ROOT)): sha(path) for path in sorted(paths)
        if "/build/" not in str(path.relative_to(ROOT)) and "/.gradle/" not in str(path.relative_to(ROOT))
    }


def literal_test_sources(classes: list[str] | tuple[str, ...]) -> dict[str, list[Path]]:
    """Mirror test-class's literal filename discovery, including historical directories.

    Each configured class has a known literal source file, so the repository's
    declaration-search fallback cannot apply. Fail rather than run an unrecorded
    second module when another matching filename exists outside the mapped module.
    """
    found = {name: [] for name in classes}
    excluded = {"build", "node_modules", ".git", ".gradle", ".claude", "temp"}
    for directory, subdirectories, filenames in os.walk(ROOT):
        subdirectories[:] = [name for name in subdirectories if name not in excluded]
        for filename in filenames:
            if filename.endswith(".kt") and filename[:-3] in found:
                found[filename[:-3]].append(Path(directory) / filename)
    return {name: sorted(paths) for name, paths in found.items()}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--classes", nargs="+", choices=TESTS, default=DEFAULT_TESTS)
    parser.add_argument("--expected-head")
    parser.add_argument("--batch", action="store_true", help="Run named classes from each module together; every class must emit fresh passing XML")
    args = parser.parse_args()
    report_dir = args.out.resolve()
    report_dir.mkdir(parents=True, exist_ok=False)
    receipt = {
        "schema": "ferocity-recycling-real-engine-qualification-v1",
        "started_at_utc": stamp(),
        "scope": "DETERMINISTIC_BUILD_CARD_AND_FIXED_POLICY_FIXTURES_ONLY",
        "interactive_development_games": 0, "evaluation_games": 0,
        "confirmation_games": 0, "randomized_experimental_seeds": 0,
        "status": "RUNNING", "stages": [],
    }
    receipt_path = report_dir / "receipt.json"

    def write() -> None:
        receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")

    write()
    try:
        receipt["source_head"] = output(["git", "rev-parse", "HEAD"])
        receipt["source_tree"] = output(["git", "rev-parse", "HEAD^{tree}"])
        input_hashes = compiled_inputs(args.classes)
        tracked_changes = output(["git", "diff", "--name-only", "HEAD"]).splitlines()
        untracked = output(["git", "ls-files", "--others", "--exclude-standard"]).splitlines()
        receipt["tracked_compiled_input_changes"] = [p for p in tracked_changes if is_compiled_input(p, args.classes)]
        receipt["untracked_compiled_inputs_sha256"] = {p: input_hashes[p] for p in untracked if p in input_hashes}
        receipt["source_is_dirty"] = bool(receipt["tracked_compiled_input_changes"] or receipt["untracked_compiled_inputs_sha256"])
        # The pinned commit supplies unchanged inputs. Archive actual bytes for the
        # local changes and additions so a later correction cannot erase a failed
        # fixture/source version while leaving only its unresolvable hash behind.
        changed_names = sorted(set(receipt["tracked_compiled_input_changes"]) | set(receipt["untracked_compiled_inputs_sha256"]))
        archived_sources = []
        deleted_sources = []
        with zipfile.ZipFile(report_dir / "source-changes.zip", "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in changed_names:
                path = ROOT / name
                if not path.is_file():
                    deleted_sources.append(name)
                    continue
                data = path.read_bytes()
                digest = hashlib.sha256(data).hexdigest()
                if input_hashes.get(name) != digest:
                    raise RuntimeError(f"Source changed between initial capture and archive: {name}")
                item = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                item.compress_type = zipfile.ZIP_DEFLATED
                item.external_attr = 0o644 << 16
                archive.writestr(item, data)
                archived_sources.append({"path": name, "sha256": digest, "bytes": len(data)})
        receipt["source_archive"] = {
            "path": "source-changes.zip", "sha256": sha(report_dir / "source-changes.zip"),
            "files": archived_sources, "deleted_paths": deleted_sources,
            "replay": "Check out source_head, extract source-changes.zip at repository root, remove deleted_paths, and verify every compiled-inputs-before.json digest before replay.",
        }
        shutil.copyfile(Path(__file__), report_dir / "validator-attempt.py")
        receipt["archived_runner_sha256"] = sha(report_dir / "validator-attempt.py")
        inputs_path = report_dir / "compiled-inputs-before.json"
        inputs_path.write_text(json.dumps(input_hashes, indent=2) + "\n")
        receipt["compiled_inputs_before_sha256"] = sha(inputs_path)
        receipt["base_commit"] = BASE
        output(["git", "merge-base", "--is-ancestor", BASE, receipt["source_head"]])
        if args.expected_head and args.expected_head != receipt["source_head"]:
            raise RuntimeError("Checked-out source does not match expected head")
        if args.expected_head and receipt["source_is_dirty"]:
            raise RuntimeError("CI runtime/test compile inputs differ from committed expected head")
        if not shutil.which("just"):
            raise RuntimeError("Required just executable absent; raw Gradle bypass is forbidden")
        receipt["java_version"] = output(["java", "-version"])
        receipt["just_version"] = output(["just", "--version"])
        receipt["build_file_sha256"] = {name: sha(ROOT / name) for name in BUILD_FILES}
        receipt["runner_sha256"] = sha(Path(__file__))
        write()
        discovered = literal_test_sources(args.classes)
        receipt["literal_class_discovery"] = {
            name: [str(path.relative_to(ROOT)) for path in paths]
            for name, paths in discovered.items()
        }
        write()
        groups = [[name] for name in args.classes]
        if args.batch:
            if len(set(args.classes)) != len(args.classes):
                raise RuntimeError("A batch cannot contain duplicate class names")
            modules = dict.fromkeys(TESTS[name] for name in args.classes)
            groups = [[name for name in args.classes if TESTS[name] == module] for module in modules]
        for group_index, names in enumerate(groups, 1):
            name = names[0]
            module = TESTS[name]
            class_sources = {}
            for selected in names:
                sources = list((ROOT / module / "src/test/kotlin").rglob(f"{selected}.kt"))
                if len(sources) != 1:
                    raise RuntimeError(f"Expected exactly one source file for {selected}; found {len(sources)}")
                if discovered[selected] != sources:
                    raise RuntimeError(
                        f"test-class would discover additional or different sources for {selected}: "
                        f"{receipt['literal_class_discovery'][selected]}"
                    )
                class_sources[selected] = sources[0]
            stage_dir = report_dir / (name if len(names) == 1 else f"batch-{group_index:02d}-{module.replace('/', '-')}")
            stage_dir.mkdir()
            # Archive then remove only this class's old XML. A cached prior success
            # must not masquerade as a test executed by this invocation.
            old_xml = sorted(path for selected in names for path in (ROOT / module / "build/test-results/test").glob(f"TEST-*{selected}.xml"))
            if old_xml:
                archive = stage_dir / "preexisting-xml"
                archive.mkdir()
                for path in old_xml:
                    shutil.copyfile(path, archive / path.name)
                    path.unlink()
            command = [
                "just", "test-class", name, "--rerun", "--no-build-cache", "--console=plain", "--stacktrace",
                "--max-workers=2", "-PkotlinCompileParallelism=1",
            ]
            for selected in names[1:]:
                command.extend(["--tests", f"*.{selected}"])
            stage = {
                "test_class": name, "module": module, "started_at_utc": stamp(),
                "test_classes": names,
                "command": command, "source_path": str(class_sources[name].relative_to(ROOT)),
                "source_sha256": sha(class_sources[name]), "status": "RUNNING",
                "class_sources": {selected: {"path": str(path.relative_to(ROOT)), "sha256": sha(path)} for selected, path in class_sources.items()},
            }
            receipt["stages"].append(stage)
            write()
            print(f"Running {', '.join(names)}", flush=True)
            with (stage_dir / "build.log").open("w") as stream:
                result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
            stage["exit_status"] = result.returncode
            stage["finished_at_utc"] = stamp()
            xml_files = sorted(path for selected in names for path in (ROOT / module / "build/test-results/test").glob(f"TEST-*{selected}.xml"))
            copied = []
            totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
            class_totals = {selected: dict(totals) for selected in names}
            timestamp_totals = dict(totals)
            class_timestamp_totals = {selected: dict(totals) for selected in names}
            freshness_records = []
            xml_errors = []
            for source in xml_files:
                dest = stage_dir / source.name
                try:
                    shutil.copyfile(source, dest)
                    copied.append({"name": dest.name, "sha256": sha(dest)})
                    root = ET.parse(dest).getroot()
                    selected = next(selected for selected in names if source.name.endswith(f"{selected}.xml"))
                    counts = {key: int(root.attrib.get(key, "0")) for key in totals}
                    freshness = xml_timestamp_freshness(root, stage["started_at_utc"], stage["finished_at_utc"])
                    freshness_records.append({"name": dest.name, **freshness, "totals": counts})
                    for key in totals:
                        totals[key] += counts[key]
                        class_totals[selected][key] += counts[key]
                        if freshness["within_invocation_window"]:
                            timestamp_totals[key] += counts[key]
                            class_timestamp_totals[selected][key] += counts[key]
                except Exception as exc:
                    xml_errors.append({"source": str(source.relative_to(ROOT)), "type": type(exc).__name__, "message": str(exc)})
            stage["test_xml"] = copied
            stage["totals"] = totals
            stage["raw_xml_totals"] = dict(totals)
            stage["class_totals"] = class_totals
            stage["xml_freshness"] = freshness_records
            stage["xml_capture_or_parse_errors"] = xml_errors
            stage["log_sha256"] = sha(stage_dir / "build.log")
            task = ":" + module.replace("/", ":") + ":test"
            task_statuses = re.findall(
                r"^> Task " + re.escape(task) + r"(?: ([^\r\n]*))?$",
                (stage_dir / "build.log").read_text(), re.MULTILINE,
            )
            stage["observed_test_task_statuses"] = task_statuses
            stage["test_task_has_execution_marker"] = bool(task_statuses) and all(
                status in {"", "FAILED"} for status in task_statuses
            )
            stage["test_task_executed"] = stage["test_task_has_execution_marker"] and timestamp_totals["tests"] > 0
            stage["fresh_test_task_totals"] = timestamp_totals if stage["test_task_has_execution_marker"] else {key: 0 for key in totals}
            stage["unaccepted_xml_totals"] = {key: totals[key] - stage["fresh_test_task_totals"][key] for key in totals}
            stage["class_fresh_test_task_totals"] = class_timestamp_totals if stage["test_task_has_execution_marker"] else {selected: {key: 0 for key in totals} for selected in names}
            passed = result.returncode == 0 and not xml_errors and stage["test_task_executed"] and not stage["unaccepted_xml_totals"]["tests"] and all(counts["tests"] > 0 for counts in stage["class_fresh_test_task_totals"].values()) and not any(
                totals[key] for key in ("failures", "errors", "skipped")
            )
            stage["status"] = "PASS" if passed else "FAIL"
            write()
            if not passed:
                label = name if len(names) == 1 else f"Named batch ({', '.join(names)})"
                raise RuntimeError(f"{label} did not qualify; preserve logs and diagnose before any successor")
        receipt["status"] = "PASS"
        receipt["qualification_limit"] = (
            "Only the recorded deterministic assertions passed. This is not production card "
            "admission, complete selected-pool coverage, pilot totality, or matchup evidence."
        )
    except Exception as exc:
        receipt["status"] = "FAIL"
        receipt["failure"] = {"type": type(exc).__name__, "message": str(exc)}
        print(str(exc), file=sys.stderr)
    finally:
        if "compiled_inputs_before_sha256" in receipt:
            try:
                after = compiled_inputs(args.classes)
                after_path = report_dir / "compiled-inputs-after.json"
                after_path.write_text(json.dumps(after, indent=2) + "\n")
                receipt["compiled_inputs_after_sha256"] = sha(after_path)
                receipt["compiled_inputs_unchanged"] = receipt["compiled_inputs_before_sha256"] == receipt["compiled_inputs_after_sha256"]
                if not receipt["compiled_inputs_unchanged"]:
                    receipt["status"] = "FAIL"
                    receipt["source_change_failure"] = "Compiled inputs changed during validation; no assertions admitted"
            except Exception as exc:
                receipt["status"] = "FAIL"
                receipt["compiled_inputs_unchanged"] = False
                receipt["source_change_failure"] = f"Final source capture failed: {type(exc).__name__}: {exc}"
        receipt["finished_at_utc"] = stamp()
        write()
    print(json.dumps({"status": receipt["status"], "receipt": str(receipt_path)}), flush=True)
    return 0 if receipt["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
