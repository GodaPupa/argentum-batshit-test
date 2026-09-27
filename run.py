#!/usr/bin/env python3
"""One reviewed passive diagnostic of the existing six-case regression bank."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import signal
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def git(root, *args):
    return subprocess.check_output(['git', *args], cwd=root, text=True).strip()


def source_snapshot(root, gate, instrumented=False):
    expected_changes = sorted(row['path'] for row in gate['instrumentation']) if instrumented else []
    status = git(root, 'status', '--porcelain')
    observed_changes = sorted(git(root, 'diff', '--name-only', 'HEAD').splitlines())
    assert not git(root, 'diff', '--cached', '--name-only'), 'Staged source/index drift'
    assert not git(root, 'diff', '--name-only', '--diff-filter=U'), 'Unmerged source state'
    if not instrumented:
        assert not status, 'Source must be completely clean'
    assert observed_changes == expected_changes
    assert not git(root, 'ls-files', '--others', '--exclude-standard')
    record = {'head': git(root, 'rev-parse', 'HEAD'), 'tree': git(root, 'rev-parse', 'HEAD^{tree}'), 'status': status, 'file_sha256': {}}
    assert record['head'] == gate['source_head'] and record['tree'] == gate['source_tree']
    for row in gate['instrumentation']:
        raw = (root / row['path']).read_bytes()
        expected = row['instrumented_sha256'] if instrumented else row['original_sha256']
        assert sha(raw) == expected, row['path']
        record['file_sha256'][row['path']] = sha(raw)
    for row in gate['unchanged_tests']:
        raw = (root / row['path']).read_bytes()
        assert sha(raw) == row['sha256'] and git(root, 'hash-object', row['path']) == row['git_blob']
        record['file_sha256'][row['path']] = sha(raw)
    for path, expected in gate['preserved_authority_sha256'].items():
        raw = (root / path).read_bytes()
        assert sha(raw) == expected
        record['file_sha256'][path] = sha(raw)
    return record


class DiagnosticResourceLimit(RuntimeError):
    pass


def inspect_xml(raw, bank, output, limits):
    suite = ET.fromstring(raw)
    cases = suite.findall('testcase')
    names = [c.attrib['name'] for c in cases]
    assert suite.tag == 'testsuite' and suite.attrib['name'] == bank['class']
    assert len(cases) == int(suite.attrib['tests']) == bank['expected_cases']
    assert len(names) == len(set(names)) and sorted(names) == sorted(bank['case_names'])
    assert all(c.attrib['classname'] == bank['class'] for c in cases)
    counts = {kind: sum(c.find(kind) is not None for c in cases) for kind in ['failure', 'error', 'skipped']}
    assert counts == {'failure': int(suite.attrib['failures']), 'error': int(suite.attrib['errors']), 'skipped': int(suite.attrib['skipped'])}
    assert counts['error'] == counts['skipped'] == 0
    records = []
    traces = []
    for node in suite.iter('system-out'):
        for line in (node.text or '').splitlines():
            for marker, destination in [('IZZET_DIAGNOSTIC_TRACE ', traces), ('IZZET_DECISION_DIAGNOSTIC ', records)]:
                if line.startswith(marker):
                    destination.append(base64.b64decode(line[len(marker):], validate=True))
    result = {'class': bank['class'], 'case_names': names, 'cases': len(cases), 'failures': counts['failure'], 'errors': counts['error'], 'skipped': counts['skipped'], 'xml_sha256': sha(raw)}
    if bank['class'].endswith('.FrozenBaselineTest'):
        raw_trace = b''.join(traces)
        if directory_bytes(output.parent) + len(raw_trace) > limits['artifact_bytes'] - 4194304:
            raise DiagnosticResourceLimit('Derived trace artifact budget; raw XML retained')
        (output / 'baseline-hash-input.bin').write_bytes(raw_trace)
        assert len(traces) >= 2 and not records
        assert all(t.endswith(b'\n') and t.count(b'\n') == 1 for t in traces)
        assert [int(re.match(rb'[AD]([0-9]+)\|', t).group(1)) for t in traces[:-1]] == list(range(1, len(traces)))
        assert traces[-1].startswith(b'END|')
        result['baseline'] = {'entries': len(traces), 'bytes': len(raw_trace), 'sha256': sha(raw_trace), 'end_record': traces[-1].decode()}
    else:
        assert records and not traces
        if len(records) > 4096 or sum(map(len, records)) > 33554432:
            raise DiagnosticResourceLimit('Decoded diagnostic size/count limit; complete raw XML retained')
        if directory_bytes(output.parent) + sum(map(len, records)) > limits['artifact_bytes'] - 4194304:
            raise DiagnosticResourceLimit('Derived state artifact budget; raw XML retained')
        for i, raw_record in enumerate(records, 1):
            (output / f'action-{i:03d}.txt').write_bytes(raw_record)
            text = raw_record.decode()
            assert all(label in text for label in ['action=', 'success=', 'paused=', 'error=', 'beforePendingType=', 'afterPendingType=', 'before=', 'resultState=', 'storedAfter='])
        result['decision_records'] = [{'index': i, 'bytes': len(raw_record), 'sha256': sha(raw_record)} for i, raw_record in enumerate(records, 1)]
    return result


def directory_bytes(path):
    return sum(p.stat().st_size for p in path.rglob('*') if p.is_file()) if path.exists() else 0


def terminate_owned_group(process):
    # This Popen owns a new session; never signal another worker or runner process.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    finally:
        # The wrapper may have exited while a child still owns its pipe/session.
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=5)


def run_capped(command, root, log_path, reports, out, limits):
    start = time.monotonic()
    reason = None
    written = 0
    discarded_observed_bytes = 0
    process = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    try:
        with log_path.open('wb') as log:
            while selector.get_map():
                if time.monotonic() - start > limits['command_timeout_seconds']:
                    reason = 'COMMAND_TIMEOUT'; break
                if directory_bytes(reports) > limits['test_result_bytes']:
                    reason = 'TEST_RESULT_BYTE_LIMIT'; break
                if directory_bytes(out) >= limits['artifact_bytes'] - 4194304:
                    reason = 'ARTIFACT_BYTE_LIMIT'; break
                for key, _ in selector.select(timeout=0.1):
                    chunk = os.read(key.fileobj.fileno(), 65536)
                    if not chunk:
                        selector.unregister(key.fileobj)
                        continue
                    remaining = min(limits['stdout_bytes'] - written, limits['artifact_bytes'] - directory_bytes(out) - 4194304)
                    keep = chunk[:max(0, remaining)]
                    log.write(keep); log.flush(); written += len(keep)
                    if len(keep) < len(chunk):
                        discarded_observed_bytes += len(chunk) - len(keep)
                        reason = 'STDOUT_OR_ARTIFACT_BYTE_LIMIT'; break
                if reason:
                    break
            if not reason:
                try:
                    process.wait(timeout=max(0.01, limits['command_timeout_seconds'] - (time.monotonic() - start)))
                except subprocess.TimeoutExpired:
                    reason = 'COMMAND_TIMEOUT'
    finally:
        if reason or process.poll() is None:
            terminate_owned_group(process)
        selector.close()
        process.stdout.close()
    return {'exit_status': process.returncode, 'technical_limit': reason, 'stdout_retained_bytes': written,
            'discarded_observed_pipe_bytes': discarded_observed_bytes, 'output_complete': reason is None,
            'elapsed_seconds': time.monotonic() - start}


def run_bank(root, out, bank, index, limits):
    folder = out / f"{index:02d}-{bank['stage']}"
    folder.mkdir()
    reports = root / bank['module'] / 'build/test-results/test'
    for p in reports.glob('TEST-*.xml'):
        p.unlink()  # Generated XML only, in this new owned disposable checkout.
    command = ['just', 'test-class', bank['class'].rsplit('.', 1)[1], '--rerun', '--no-build-cache', '--no-daemon', '-DupdateSnapshots=false', '--info', '--stacktrace', '--max-workers=1', '-PkotlinCompileParallelism=1', '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx4g']
    row = {'class': bank['class'], 'command': command, 'started_ns': time.time_ns(), 'status': 'INCOMPLETE'}
    write(folder / 'command.json', row)
    try:
        row.update(run_capped(command, root, folder / 'command.log', reports, out, limits))
        row['finished_ns'] = time.time_ns()
        (folder / 'exit-status.txt').write_text(str(row['exit_status']) + '\n')
        paths = sorted(reports.glob('TEST-*.xml'))
        row['xml_retention'] = []
        for p in paths:
            size = p.stat().st_size
            allowed = max(0, min(limits['test_result_bytes'], limits['artifact_bytes'] - directory_bytes(out) - 4194304))
            with p.open('rb') as source:
                kept = source.read(min(size, allowed))
            complete = len(kept) == size
            target = folder / (p.name if complete else p.name + '.partial')
            target.write_bytes(kept)
            row['xml_retention'].append({'file': p.name, 'observed_source_bytes': size, 'retained_bytes': len(kept), 'complete': complete, 'sha256': sha(kept)})
        if not all(x['complete'] for x in row['xml_retention']):
            row['technical_limit'] = 'INCOMPLETE_XML_BYTE_LIMIT'
            row['output_complete'] = False
        assert row['output_complete'], 'Technical output/time limit; retained prefixes are not complete evidence'
        assert len(paths) == 1 and paths[0].name == 'TEST-' + bank['class'] + '.xml'
        assert row['started_ns'] - 2_000_000_000 <= paths[0].stat().st_mtime_ns <= row['finished_ns'] + 2_000_000_000
        row['actual'] = inspect_xml(paths[0].read_bytes(), bank, folder, limits)
        assert row['exit_status'] in (0, 1)
        assert bool(row['exit_status']) == bool(row['actual']['failures'])
        lines = (folder / 'command.log').read_text().splitlines()
        task = ':' + bank['module'].replace('/', ':') + ':test'
        assert '> Task ' + task + (' FAILED' if row['exit_status'] else '') in lines
        assert not any('> Task ' + task + ' ' + suffix in lines for suffix in ['NO-SOURCE', 'UP-TO-DATE', 'FROM-CACHE', 'SKIPPED'])
        assert sum('Gradle Test Executor' in x and 'started executing tests' in x for x in lines) == 1
        assert sum('Gradle Test Executor' in x and 'finished executing tests' in x for x in lines) == 1
        assert directory_bytes(out) <= limits['artifact_bytes'], 'Artifact size limit; evidence incomplete'
        row['status'] = 'DIAGNOSTIC_MATERIAL_COMPLETE_PENDING_REVIEW'
    except Exception as error:
        row['error'] = type(error).__name__ + ': ' + str(error)
        if isinstance(error, DiagnosticResourceLimit):
            row['technical_limit'] = 'DERIVED_BYTE_OR_RECORD_LIMIT'
            row['output_complete'] = False
    finally:
        write(folder / 'command.json', row)
    return row


def main():
    workspace = Path(os.environ['GITHUB_WORKSPACE'])
    control, root, out = workspace / 'control', workspace / 'source', workspace / 'output'
    out.mkdir(exist_ok=False)
    paths = ['.github/workflows/izzet-receiving-failure-diagnostic.yml', 'run.py', 'scope.json']
    gate = {}
    audit = {'schema': 'izzet-receiving-passive-diagnostic-audit-v1', 'run': os.environ['GITHUB_RUN_ID'], 'attempt': os.environ['GITHUB_RUN_ATTEMPT'], 'control_head': git(control, 'rev-parse', 'HEAD'), 'control_tree': git(control, 'rev-parse', 'HEAD^{tree}'), 'stages': [], 'errors': [], 'official_games': 0, 'official_seeds': 0, 'new_cases': 0, 'runtime_accepted': False, 'gameplay_authorized': False}
    originals = {}
    exit_code = 2
    try:
        # Preserve exact controls before any readiness/review/source assertion can reject them.
        audit['control_sha256'] = {p: sha((control / p).read_bytes()) for p in paths}
        for p in paths:
            dest = out / 'control' / p; dest.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(control / p, dest)
        write(out / 'audit.json', audit)
        gate = json.loads((control / 'scope.json').read_text())
        assert os.environ['GITHUB_REPOSITORY'] == 'GodaPupa/argentum-batshit-test'
        assert os.environ['GITHUB_EVENT_NAME'] == 'push' and os.environ['GITHUB_RUN_ATTEMPT'] == '1'
        assert audit['control_head'] == os.environ['GITHUB_SHA'] and not git(control, 'status', '--porcelain')
        assert sorted(git(control, 'ls-files').splitlines()) == sorted(paths)
        assert gate['ready'] is True and gate['source_review']
        limits = gate['resource_limits']
        assert limits == {'command_timeout_seconds': 1200, 'stdout_bytes': 33554432, 'test_result_bytes': 33554432, 'artifact_bytes': 201326592, 'minimum_free_bytes': 536870912}
        assert shutil.disk_usage(workspace).free >= limits['minimum_free_bytes']
        audit['resource_limits'] = limits
        review = gate['source_review']; assert sha(review['record'].encode()) == review['sha256']
        assert len(gate['existing_class_bank']) == 3 and sum(b['expected_cases'] for b in gate['existing_class_bank']) == 6
        audit['source_before'] = source_snapshot(root, gate)
        write(out / 'source-before.json', audit['source_before'])
        for row in gate['instrumentation']:
            path = root / row['path']; raw = path.read_bytes(); originals[row['path']] = raw
            before = row['replacement_old'].encode(); after = row['replacement_new'].encode()
            assert raw.count(before) == 1
            patched = raw.replace(before, after)
            assert patched.replace(after, before) == raw and sha(patched) == row['instrumented_sha256']
            dest = out / 'instrumentation' / row['path']; dest.parent.mkdir(parents=True, exist_ok=True)
            dest.with_suffix(dest.suffix + '.original').write_bytes(raw); dest.write_bytes(patched); path.write_bytes(patched)
        audit['instrumented_source'] = source_snapshot(root, gate, True)
        write(out / 'instrumented-source.json', audit['instrumented_source']); write(out / 'audit.json', audit)
        for i, bank in enumerate(gate['existing_class_bank'], 1):
            assert source_snapshot(root, gate, True) == audit['instrumented_source']
            audit['current_stage'] = bank['stage']; write(out / 'audit.json', audit)
            audit['stages'].append(run_bank(root, out, bank, i, limits)); write(out / 'audit.json', audit)
            if audit['stages'][-1].get('technical_limit'):
                audit['unattempted_classes'] = [b['class'] for b in gate['existing_class_bank'][i:]]
                break
        assert source_snapshot(root, gate, True) == audit['instrumented_source']
        assert all(r['status'] == 'DIAGNOSTIC_MATERIAL_COMPLETE_PENDING_REVIEW' for r in audit['stages'])
        assert sum(r['actual']['cases'] for r in audit['stages']) == 6
        audit['diagnostic_material_complete'] = True
        exit_code = 1 if any(r['actual']['failures'] for r in audit['stages']) else 0
    except Exception as error:
        audit['errors'].append(type(error).__name__ + ': ' + str(error))
    finally:
        for path, raw in originals.items():
            (root / path).write_bytes(raw)
        try:
            audit['source_restored'] = source_snapshot(root, gate)
            assert audit['source_restored'] == audit['source_before']
            assert audit['control_sha256'] == {p: sha((control / p).read_bytes()) for p in paths}
            write(out / 'source-restored.json', audit['source_restored'])
        except Exception as error:
            audit['errors'].append('Restoration/integrity: ' + str(error)); exit_code = 2
        audit['exit_status'] = exit_code; write(out / 'audit.json', audit)
    return exit_code


if __name__ == '__main__':
    raise SystemExit(main())
