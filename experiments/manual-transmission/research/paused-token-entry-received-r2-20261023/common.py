"""Local original-byte receipt helpers. No network, builds, games or subprocess execution."""
from pathlib import Path, PurePosixPath
import hashlib
import io
import json
import re
import sys
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parent


def digest(body):
    return hashlib.sha256(body).hexdigest()


def load_json(name):
    return json.loads((ROOT / name).read_bytes())


def frozen_inputs():
    config = load_json('RECEIVER.json')
    for name, spec in config['inputs'].items():
        body = (ROOT / name).read_bytes()
        if len(body) != spec['bytes'] or digest(body) != spec['sha256']:
            raise ValueError('Frozen receiver input differs: ' + name)
    return config


def local_path(name):
    path = PurePosixPath(name)
    if path.is_absolute() or '..' in path.parts or '\\' in name:
        raise ValueError('Unsafe local payload path: ' + name)
    return ROOT / name


def artifact(config):
    """Require raw GitHub metadata plus either exact ZIP or individually pinned chunks."""
    meta = load_json('ARTIFACT.json')
    if not isinstance(meta.get('id'), int) or meta['id'] <= 0:
        raise ValueError('Actual artifact ID required')
    if meta.get('name') != config['artifact_prefix'] + str(config['run_id']):
        raise ValueError('Wrong artifact name')
    run = meta['workflow_run']
    if run['id'] != config['run_id'] or run['head_sha'] != config['validation']:
        raise ValueError('Artifact run/source mismatch')
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', meta.get('digest', '')):
        raise ValueError('Actual GitHub SHA-256 required')
    if bool(meta.get('parts')) == bool(meta.get('zip_file')):
        raise ValueError('Supply exactly one of parts or zip_file')
    if meta.get('parts'):
        parts, seen = [], set()
        for part in meta['parts']:
            if part['name'] in seen:
                raise ValueError('Duplicate artifact part')
            seen.add(part['name'])
            body = local_path(part['name']).read_bytes()
            if len(body) != part['bytes'] or digest(body) != part['sha256']:
                raise ValueError('Artifact chunk integrity: ' + part['name'])
            parts.append(body)
        body = b''.join(parts)
    else:
        body = local_path(meta['zip_file']).read_bytes()
    if len(body) != meta['size_in_bytes'] or digest(body) != meta['digest'][7:]:
        raise ValueError('Original artifact ZIP size/SHA-256 mismatch')
    archive = zipfile.ZipFile(io.BytesIO(body))
    if archive.testzip() is not None:
        raise ValueError('ZIP CRC failure')
    names = archive.namelist()
    if len(names) != len(set(names)):
        raise ValueError('Duplicate ZIP entries')
    for name in names:
        local_path(name)
    # Directory records are allowed, but every payload byte must have a root manifest entry.
    files = {item.filename for item in archive.infolist() if not item.is_dir()}
    inventory = {}
    for line in archive.read('SHA256SUMS').decode().splitlines():
        expected, name = line.split('  ', 1)
        if name in inventory or not re.fullmatch(r'[0-9a-f]{64}', expected):
            raise ValueError('Malformed/duplicate payload manifest entry')
        if digest(archive.read(name)) != expected:
            raise ValueError('Payload hash mismatch: ' + name)
        inventory[name] = expected
    if files != set(inventory) | {'SHA256SUMS'}:
        raise ValueError('Incomplete payload inventory')
    return meta, archive, files, inventory


def differential_blocks(text):
    """Preserved prior parser: trim only block-edge whitespace; retain every interior byte."""
    headers = list(re.finditer(r'^  (.+)  \[([A-Z0-9]+)\]$', text, re.M))
    result = {}
    for index, header in enumerate(headers):
        name = header.group(1) + ' [' + header.group(2) + ']'
        if name in result:
            raise ValueError('Duplicate differential block: ' + name)
        end = headers[index + 1].start() if index + 1 < len(headers) else len(text)
        result[name] = text[header.end():end].strip()
    return result


def compare_differential(meta, archive, names):
    baseline = (ROOT / 'BASELINE_DIFFERENTIAL.log').read_bytes()
    current = archive.read('assay/differential.log') if 'assay/differential.log' in names else None
    old = differential_blocks(baseline.decode())
    new = differential_blocks(current.decode()) if current is not None else {}
    added, removed = sorted(new.keys() - old.keys()), sorted(old.keys() - new.keys())
    changed = sorted(key for key in old.keys() & new.keys() if old[key] != new[key])
    passed = current is not None and len(old) == len(new) == 51 and not (added or removed or changed)
    return dict(baseline_artifact_id=12469559629, current_artifact_id=meta['id'],
        baseline_report_sha256=digest(baseline), current_report_sha256=digest(current) if current else None,
        baseline_divergences=len(old), current_divergences=len(new), added=added, removed=removed,
        changed=changed, retained_divergent_cards=sorted(new), all_original_block_interiors_identical=passed,
        qualification='UNCHANGED_DIVERGENCES_RETAINED' if passed else 'REVIEW_REQUIRED',
        comparison_scope='Same preserved parser: block-edge whitespace stripped; interior text exact. No semantic normalization or reclassification. Raw report hashes retained.',
        limitations='Original 51 divergences remain unresolved; this comparison gives no card or competitive admission.')


def save_or_compare(name, report, write):
    target = ROOT / name
    if write:
        target.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
    elif json.loads(target.read_bytes()) != report:
        raise ValueError('Read-only reproduction differs: ' + name)
