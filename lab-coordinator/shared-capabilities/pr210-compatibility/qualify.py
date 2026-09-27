"""Prospective exact-source compatibility; never changes historical receiving manifests."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
PREFIX = 'lab-coordinator/shared-capabilities/pr210-compatibility'
OUT = ROOT / 'build/reports/pr210-derived-compatibility'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()

def blob(ref, path):
    return subprocess.check_output(['git', 'show', ref + ':' + path], cwd=ROOT)

def proposal():
    return json.loads((ROOT / PREFIX / 'proposal.json').read_text())

def source():
    p = proposal()
    assert p['status'] == 'PROSPECTIVE_NONAUTHOR_REVIEW_REQUIRED'
    head = git('rev-parse', 'HEAD')
    assert head == os.environ['EXPECTED_HEAD']
    assert not git('status', '--porcelain', '--untracked-files=all')
    assert git('rev-parse', p['source'] + '^{tree}') == p['source_tree']
    changed = git('diff', '--name-only', p['source'], head).splitlines()
    assert changed and all(f.startswith(PREFIX + '/') for f in changed), changed
    observations = []
    for i, entry in enumerate(p['frozen_manifests']):
        raw = (ROOT / entry['path']).read_bytes()
        assert sha(raw) == entry['sha256']
        assert raw == blob(p['source'], entry['path'])
        m = json.loads(raw)
        assert len(m['source_files_sha256']) == entry['pin_count']
        deltas = {d['path']: d for d in p['deltas'][i]['mismatches']}
        found = set()
        for path, expected in m['source_files_sha256'].items():
            actual = sha((ROOT / path).read_bytes())
            assert (ROOT / path).read_bytes() == blob(p['source'], path)
            if actual != expected:
                d = deltas[path]
                assert d['expected'] == expected and d['actual'] == actual
                assert sha(blob(p['prior_accepted_source'], path)) == expected
                found.add(path)
            observations.append({'path': path, 'sha256': actual})
        assert found == set(deltas)
    assert len(p['stages']) == 22 and sum(s['expected_cases'] for s in p['stages']) == 466
    return {'head': head, 'tree': git('rev-parse', 'HEAD^{tree}'),
            'proposal_sha256': sha((ROOT / PREFIX / 'proposal.json').read_bytes()),
            'observed': observations, 'official_games': 0, 'gameplay_authorized': False}

def write(name, data):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / name).write_text(json.dumps(data, indent=2, sort_keys=True) + '\n')

def bind():
    state = source()
    for stage in proposal()['stages']:
        assert not list((ROOT / stage['module'] / 'build/test-results/test').glob('TEST-*.xml'))
    write('source-before.json', state)

def collect():
    errors = []
    rows = []
    try:
        after = source()
        write('source-after.json', after)
        assert after == json.loads((OUT / 'source-before.json').read_text())
        assert (OUT / 'exit.txt').read_text().strip() == '0'
        log = (OUT / 'tests.log').read_text()
        assert 'BUILD SUCCESSFUL' in log
        for module in sorted({s['module'] for s in proposal()['stages']}):
            task = ':' + module.replace('/', ':') + ':test'
            lines = [line for line in log.splitlines() if re.match(r'^> Task ' + re.escape(task) + r'(?:\s|$)', line)]
            assert lines and not any(re.search(r'FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE|FAILED', line) for line in lines), task
            wanted = {'TEST-' + s['class'] + '.xml' for s in proposal()['stages'] if s['module'] == module}
            assert {f.name for f in (ROOT / module / 'build/test-results/test').glob('TEST-*.xml')} == wanted
        for s in proposal()['stages']:
            path = ROOT / s['module'] / 'build/test-results/test' / ('TEST-' + s['class'] + '.xml')
            raw = path.read_bytes()
            x = ET.fromstring(raw)
            cases = x.findall('testcase')
            assert x.get('name') == s['class'] and int(x.get('tests')) == s['expected_cases'] == len(cases)
            assert all(int(x.get(k, 0)) == 0 for k in ('failures', 'errors', 'skipped'))
            names = [c.get('name') for c in cases]
            assert len(set(names)) == len(names) and set(names) == set(s['case_names'])
            assert all(c.get('classname') == s['class'] and not any(n.tag in ('failure','error','skipped') for n in c) for c in cases)
            dest = OUT / 'xml' / path.name
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(raw)
            rows.append({'class': s['class'], 'cases': len(cases), 'xml_sha256': sha(raw), 'groups': s['groups']})
    except (AssertionError, OSError, KeyError, ValueError, subprocess.SubprocessError) as e:
        errors.append(type(e).__name__ + ': ' + str(e))
    write('audit.json', {'status': 'INCOMPLETE' if errors else 'AWAIT_INDEPENDENT_ARTIFACT_REVIEW',
                        'errors': errors, 'rows': rows, 'official_games': 0, 'gameplay_authorized': False,
                        'original_attempts_reclassified': False})
    return bool(errors)

if __name__ == '__main__':
    if sys.argv[1:] == ['bind']:
        bind()
    elif sys.argv[1:] == ['collect']:
        sys.exit(collect())
    else:
        raise SystemExit('Use bind or collect')
