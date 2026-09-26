"""Audit the unchanged 27-stage priority bank from actual JUnit and stage exits."""
import hashlib
import json
import pathlib
import subprocess
import xml.etree.ElementTree as ET


def main():
    root = pathlib.Path('.')
    out = root / 'build/reports/engine-priority-after-resolution'
    out.mkdir(parents=True, exist_ok=True)
    manifest_path = root / 'lab-coordinator/shared-capabilities/priority-receiving-sources.json'
    manifest = json.loads(manifest_path.read_text())
    errors = []
    rows = []
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    tree = subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], text=True).strip()
    for path, digest in manifest['source_files_sha256'].items():
        file = root / path
        if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            errors.append(f'Source mismatch: {path}')
    provenance = out / 'source-provenance.json'
    if not provenance.is_file():
        errors.append('No successful exact-source/rules binding')
    else:
        binding = json.loads(provenance.read_text())
        if binding['candidate_head'] != head or binding['manifest_sha256'] != hashlib.sha256(manifest_path.read_bytes()).hexdigest():
            errors.append('Source binding differs from final source')
    expected = manifest['stages']
    actual_dirs = {p.name for p in (out / 'tests').glob('*') if p.is_dir()}
    if actual_dirs != {s['stage'] for s in expected}:
        errors.append('Missing or unexpected stage directories')
    for stage in expected:
        folder = out / 'tests' / stage['stage']
        row = dict(stage)
        rows.append(row)
        try:
            row['exit_status'] = int((folder / 'exit-status.txt').read_text())
            if row['exit_status'] != 0:
                errors.append(f"Failed command: {stage['stage']}")
            matches = list(folder.glob(f"TEST-{stage['class']}.xml"))
            if len(matches) != 1:
                raise ValueError('Expected exactly one class XML')
            raw = matches[0].read_bytes()
            suite = ET.fromstring(raw)
            cases = suite.findall('testcase')
            row.update(xml_sha256=hashlib.sha256(raw).hexdigest(), actual_cases=len(cases),
                       case_names=[c.attrib['name'] for c in cases])
            if suite.tag != 'testsuite' or suite.attrib['name'] != stage['class']:
                raise ValueError('Wrong suite identity')
            if len(cases) != stage['expected_cases'] or len(cases) != int(suite.attrib['tests']):
                raise ValueError('Actual case count differs from original bank')
            if len(set(row['case_names'])) != len(cases) or any(c.attrib.get('classname') != stage['class'] for c in cases):
                raise ValueError('Duplicate case identity or wrong class')
            if any(int(suite.attrib.get(k, 0)) for k in ('failures', 'errors', 'skipped')) or any(c.find(k) is not None for c in cases for k in ('failure', 'error', 'skipped')):
                raise ValueError('Failed, errored, or skipped actual case')
        except (OSError, ValueError, KeyError, ET.ParseError) as exc:
            errors.append(f"{stage['stage']}: {exc}")
    actual_cases = sum(r.get('actual_cases', 0) for r in rows)
    if len(expected) != 27 or actual_cases != 220:
        errors.append('Original 27 stages / 220 cases not complete')
    report = {'schema': 'shared-priority-actual-receiving-audit-v1', 'head': head, 'tree': tree,
              'status': 'PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW' if not errors else 'INCOMPLETE_OR_FAILED',
              'manifest_sha256': hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
              'stages': rows, 'actual_cases': actual_cases, 'errors': errors,
              'official_execution_authorized': False, 'official_games': 0, 'outcomes_exposed': 0}
    (out / 'actual-case-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'actual_cases': actual_cases, 'stage_count': len(rows), 'errors': errors}))
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
