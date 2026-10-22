"""Read-only comparison of every original testcase from both retained executions."""
from pathlib import Path
from collections import Counter
import hashlib, io, json, sys, zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent
first = root.parent / 'copied-choices-received'
if '--first' in sys.argv:
    first = Path(sys.argv[sys.argv.index('--first') + 1]).resolve()

def receive(directory):
    meta = json.loads((directory / 'ARTIFACT.json').read_text())
    raw = b''.join((directory / p['name']).read_bytes() for p in meta['parts'])
    assert len(raw) == meta['size_in_bytes']
    assert hashlib.sha256(raw).hexdigest() == meta['digest'].removeprefix('sha256:')
    archive = zipfile.ZipFile(io.BytesIO(raw))
    assert archive.testzip() is None
    inventory = {}
    for name in sorted(archive.namelist()):
        if '/junit/' not in name or not name.endswith('.xml'):
            continue
        suite = ET.fromstring(archive.read(name))
        cases = []
        for case in suite.findall('.//testcase'):
            failures = tuple((child.tag, child.get('type'), child.get('message'))
                             for child in case if child.tag in {'failure', 'error', 'skipped'})
            cases.append((case.get('classname'), case.get('name'), failures))
        # Preserve duplicate case multiplicities; times and output streams are not verdicts.
        inventory[name] = Counter(cases)
    audit = json.loads((directory / 'INDEPENDENT_AUDIT.json').read_text())
    return meta, archive, inventory, audit

old_meta, old_zip, old, old_audit = receive(first)
new_meta, new_zip, new, new_audit = receive(root)
missing = sorted(old.keys() - new.keys())
added = sorted(new.keys() - old.keys())
changed = sorted(name for name in old.keys() & new.keys() if old[name] != new[name])
binding_equal = all(old_zip.read(name) == new_zip.read(name)
                    for name in ['binding-fixed.json', 'binding-negative.json'])
source_equal = (old_audit['source'], old_audit['validation']) == (new_audit['source'], new_audit['validation'])
corpus_equal = old_audit['assay']['oracle-bulk-sha256.txt'].split()[0] == new_audit['assay']['oracle-bulk-sha256.txt'].split()[0]
report = {
    'first_run': old_audit['run'], 'second_run': new_audit['run'],
    'first_artifact': old_meta['id'], 'second_artifact': new_meta['id'],
    'source_equal': source_equal, 'binding_files_byte_identical': binding_equal,
    'original_xml_files': {'first': len(old), 'second': len(new)},
    'missing_xml': missing, 'added_xml': added, 'changed_case_verdicts': changed,
    'same_oracle_corpus': corpus_equal,
    'assay_summary_identical': old_audit['assay'] == new_audit['assay'],
    'both_component_gates_satisfied': all(a['status'] == 'COPIED_CHOICES_QUALIFIED_COMPONENT_ONLY' for a in [old_audit, new_audit]),
    'first_acceptance_preceded_second_completion': True,
    'duplicate_cause': 'Not established; no second dispatch or rerun requested by this assistant.',
    'interpretation': 'Two executions of the same source and workflow. Retain both; do not sum report inventories or treat this as independent competitive evidence.',
    'official_games': 0, 'official_seeds': 0, 'precision_reserved': 2,
}
report['discrepancy_requires_investigation'] = not (source_equal and binding_equal and corpus_equal and not missing and not added and not changed and report['both_component_gates_satisfied'] and report['assay_summary_identical'])
if '--write' in sys.argv:
    (root / 'RUN_COMPARISON.json').write_text(json.dumps(report, indent=2) + '\n')
else:
    assert json.loads((root / 'RUN_COMPARISON.json').read_text()) == report
print(json.dumps(report, indent=2))
