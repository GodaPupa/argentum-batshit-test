#!/usr/bin/env python3
"""Package only the explicitly authorized M1 source-audit inputs; never edit those inputs."""
from __future__ import annotations

import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import stat
import zipfile
import zlib

OUT = Path(__file__).resolve().parent
REPO = OUT.parents[3]
SCRATCH = Path('/workspace/scratch/7ef98cd0475e')
COMMON = Path('/tmp/ferocity-qualified-build-a63kaiqe/checkout')
THRESHOLD = int(3.3 * 1024 * 1024)
PART_BYTES = 3 * 1024 * 1024
GROUPS = [
    ('01-m1-controlled-source-import-34', COMMON, 'ferocity-recycling/support-audits/m1-controlled-source-import-34', 'c6c68b67fa9f5ea44fd05edaa79df2966120d890462c45e58da9afb063f651d4'),
    ('02-offline-export-resource-successor-01', COMMON, 'ferocity-recycling/support-audits/offline-export-resource-successor-01', 'f89b9ff359e924f5cd0638b14a4b0a43750fe6b7e0a39b2573fc89845a6a0f49'),
    ('03-red-fixture-and-token-successor-02', SCRATCH/'ferocity-red-fixtures', 'ferocity-recycling/support-audits/red-fixture-and-token-successor-02', '27e50c2213be593baf9ee64b3de4b644dd9dafc495acb761fc08a035d9470f88'),
    ('04-resource-boundary-05', SCRATCH/'ferocity-d2-admission', 'ferocity-recycling/runtime-audits/development-admission/resource-boundary-05', '8a2031667973ed4544a6c1476a5653ab47379bd8fdd451fac32f7a59732b805a'),
    ('05-resource-boundary-06', SCRATCH/'ferocity-d2-admission', 'ferocity-recycling/runtime-audits/development-admission/resource-boundary-06', '95072b39d268ad6110b4e6c7c985cc7d90d5a2c6587defd84753a38ecd7af79a'),
    ('06-m1-export-pin-audit-01', SCRATCH/'ferocity-d2-admission', 'ferocity-recycling/runtime-audits/development-admission/m1-export-pin-audit-01', 'c655a124591d40bc0012afbeeb6a621b2326a3520127993be655f3b126f5561e'),
    ('07-priority-observation-01', SCRATCH/'ferocity-post-block-priority', 'ferocity-recycling/runtime-audits/priority-observation-01', '854b28d4f7d8baf8b1b9f0dbdb80ed94f75342f6626f04c602fd993d822c3783'),
]


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def blob(data: bytes) -> str:
    return hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()


def inventory(paths: list[Path], base: Path) -> list[list]:
    return sorted([[p.relative_to(base).as_posix(), p.stat().st_size, sha(p.read_bytes())] for p in paths])


def files_below(base: Path) -> list[Path]:
    paths = sorted(base.rglob('*'))
    if any(p.is_symlink() for p in paths):
        raise ValueError(f'Symlink in source audit: {base}')
    if any(not p.is_dir() and not p.is_file() for p in paths):
        raise ValueError(f'Nonregular source audit entry: {base}')
    return [p for p in paths if p.is_file()]


def write_exclusive(path: Path, data: bytes) -> None:
    with path.open('xb') as output:
        output.write(data)
        output.flush()
        os.fsync(output.fileno())


def describe(path: Path, data: bytes) -> dict:
    return {'path': path.relative_to(REPO).as_posix(), 'bytes': len(data),
            'sha256': sha(data), 'git_blob_sha1': blob(data)}


def package(group: str, source_root: Path, base: Path, paths: list[Path], expected_scan: str | None) -> dict:
    before = inventory(paths, base)
    inventory_sha = sha(json.dumps(before, separators=(',', ':'), ensure_ascii=False).encode())
    if expected_scan is not None and inventory_sha != expected_scan:
        raise ValueError(f'Independent scan inventory mismatch for {group}: {inventory_sha}')
    source_data = {}
    members = []
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9, strict_timestamps=False) as archive:
        for p in paths:
            original_stat = p.stat()
            data = p.read_bytes()
            if p.stat() != original_stat:
                raise ValueError(f'Input changed during read: {p}')
            name = p.relative_to(source_root).as_posix()
            source_data[name] = data
            timestamp = datetime.datetime.fromtimestamp(original_stat.st_mtime, datetime.timezone.utc)
            info = zipfile.ZipInfo(name, timestamp.timetuple()[:6])
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | stat.S_IMODE(original_stat.st_mode)) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data, compresslevel=9)
            members.append({'path': name, 'bytes': len(data), 'sha256': sha(data),
                            'git_blob_sha1': blob(data), 'crc32': f'{zlib.crc32(data)&0xffffffff:08x}',
                            'mode': oct(stat.S_IMODE(original_stat.st_mode)), 'mtime_ns': original_stat.st_mtime_ns})
    raw_zip = output.getvalue()
    artifacts = []
    if len(raw_zip) > THRESHOLD:
        for offset in range(0, len(raw_zip), PART_BYTES):
            path = OUT/f'{group}.zip.part{offset//PART_BYTES+1:03d}'
            part = raw_zip[offset:offset+PART_BYTES]
            write_exclusive(path, part)
            artifacts.append(describe(path, part))
        reconstructed = b''.join((REPO/a['path']).read_bytes() for a in artifacts)
    else:
        path = OUT/f'{group}.zip'
        write_exclusive(path, raw_zip)
        artifacts.append(describe(path, raw_zip))
        reconstructed = path.read_bytes()
    if reconstructed != raw_zip:
        raise ValueError(f'ZIP rejoin changed bytes: {group}')
    with zipfile.ZipFile(io.BytesIO(reconstructed)) as archive:
        if archive.testzip() is not None:
            raise ValueError(f'ZIP CRC error: {group}')
        if set(archive.namelist()) != set(source_data):
            raise ValueError(f'ZIP member set differs: {group}')
        for info in archive.infolist():
            data = archive.read(info)
            if data != source_data[info.filename] or info.CRC != zlib.crc32(data)&0xffffffff:
                raise ValueError(f'ZIP member differs: {group}/{info.filename}')
    if inventory(paths, base) != before:
        raise ValueError(f'Input changed during packaging: {group}')
    return {'id': group, 'source_root': str(source_root), 'source_base': str(base),
            'source_inventory_sha256': inventory_sha, 'independent_scan_inventory_matches': expected_scan is not None,
            'source_file_count': len(members), 'raw_source_bytes': sum(x['bytes'] for x in members),
            'logical_zip_name': group+'.zip', 'zip_bytes': len(raw_zip), 'zip_sha256': sha(raw_zip),
            'split': len(artifacts)>1, 'artifacts': artifacts, 'members': members,
            'verified': {'source_before_after_identical': True, 'zip_rejoin_byte_identical': True,
                         'all_member_bytes_sha256_crc32': True}, '_before': before}


def existing_import_group() -> tuple[dict, tuple[Path, list[Path]]]:
    group, source_root, relative, expected_scan = GROUPS[0]
    base = source_root/relative
    paths = files_below(base)
    before = inventory(paths, base)
    digest = sha(json.dumps(before, separators=(',', ':'), ensure_ascii=False).encode())
    if digest != expected_scan:
        raise ValueError('Import34 changed after independent scan')
    manifest_path = REPO/'ferocity-recycling/evidence/build/m1-local527-publication-02/publication-manifest.json'
    manifest_bytes = manifest_path.read_bytes()
    if sha(manifest_bytes) != '841f27305914245eb548321a4e87787b05943e7118ede80d614cd3589ef50344':
        raise ValueError('Builder publication manifest differs from the authorized reference')
    existing = json.loads(manifest_bytes)
    archives = [x for x in existing['files'] if Path(x['path']).name.startswith('source-history-part-')]
    if len(archives) != 2:
        raise ValueError('Expected exactly two existing source-history ZIPs')
    original = {p.relative_to(source_root).as_posix(): p.read_bytes() for p in paths}
    found = {}
    extras = []
    artifact_records = []
    for record in archives:
        path = REPO/record['path']
        data = path.read_bytes()
        if sha(data) != record['sha256'] or len(data) != record['bytes']:
            raise ValueError(f'Existing builder ZIP differs: {path}')
        artifact_records.append(describe(path, data))
        declared = {x['path']: x for x in record['members']}
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if archive.testzip() is not None or set(archive.namelist()) != set(declared):
                raise ValueError(f'Existing builder ZIP manifest/CRC mismatch: {path}')
            for info in archive.infolist():
                raw = archive.read(info)
                if sha(raw) != declared[info.filename]['sha256'] or len(raw) != declared[info.filename]['bytes']:
                    raise ValueError(f'Existing builder member differs: {info.filename}')
                if info.filename not in original:
                    extras.append(info.filename)
                    continue
                if info.filename in found or raw != original[info.filename]:
                    raise ValueError(f'Duplicate or mismatched Import34 source member: {info.filename}')
                found[info.filename] = {'path': info.filename, 'bytes': len(raw), 'sha256': sha(raw),
                    'git_blob_sha1': blob(raw), 'crc32': f'{info.CRC:08x}', 'transport': record['path']}
    if set(found) != set(original) or inventory(paths, base) != before:
        raise ValueError('Existing source-history archives do not cover exact current Import34')
    return ({'id': group, 'source_root': str(source_root), 'source_base': str(base),
        'source_inventory_sha256': digest, 'independent_scan_inventory_matches': True,
        'source_file_count': len(found), 'raw_source_bytes': sum(len(v) for v in original.values()),
        'transport_selection': 'REFERENCE_EXISTING_BUILDER_GROUP_NO_DUPLICATE_ARCHIVE',
        'builder_manifest': describe(manifest_path, manifest_bytes), 'artifacts': artifact_records,
        'existing_extra_members': extras, 'members': list(found.values()),
        'verified': {'source_before_after_identical': True, 'each_existing_zip_crc_and_manifest': True,
                     'all21_current_member_bytes_sha256_crc32': True}, '_before': before}, (base, paths))


def main() -> None:
    imported, source_selection = existing_import_group()
    results = [imported]
    selections = [source_selection]
    for name, root, rel, expected in GROUPS[1:]:
        base = root/rel
        paths = files_below(base)
        selections.append((base, paths))
        results.append(package(name, root, base, paths, expected))
    root = SCRATCH/'ferocity-integrated'
    base = root/'ferocity-recycling/policy-development'
    historical = [base/'FIRST_CELL_POLICY_PLAN.md']
    for name in ['red-madness-v0.1', 'artifact-pilot-v0.1', 'shared-actor-utilities-v0.1']:
        historical += sorted(p for p in (base/name).iterdir() if p.is_file())
    historical = sorted(historical)
    results.append(package('08-historical-first-cell-policy', root, base, historical,
                           '64c1814182557b386a4ccaced1399a2dce7585f4fce82a0c6b87429c91c21eb1'))
    # Recheck all seven complete directory sets after the last archive, not just known file bytes.
    for result, (base, paths) in zip(results, selections):
        if files_below(base) != paths or inventory(paths, base) != result['_before']:
            raise ValueError(f'Final source directory guard failed: {base}')
    if inventory(historical, base=root/'ferocity-recycling/policy-development') != results[-1]['_before']:
        raise ValueError('Historical selected bytes changed')
    for result in results:
        result.pop('_before')
    manifest = {
        'schema': 'ferocity-M1-source-audit-publication-v1',
        'created_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'status': 'LOSSLESS_ARCHIVES_VERIFIED_AWAITING_PARENT_PUBLICATION',
        'packaging_tool': describe(Path(__file__).resolve(), Path(__file__).read_bytes()),
        'runtime': {'python': platform.python_version(), 'zlib': zlib.ZLIB_RUNTIME_VERSION},
        'scope': 'Seven complete authorized source-audit directories plus explicitly curated top-level historical policy provenance. No gameplay campaign or new validation execution.',
        'split_threshold_bytes': THRESHOLD, 'maximum_part_bytes': PART_BYTES,
        'groups': results,
        'totals': {'source_files': sum(x['source_file_count'] for x in results),
                   'raw_source_bytes': sum(x['raw_source_bytes'] for x in results),
                   'new_logical_archives': len(results)-1,
                   'new_archive_artifacts': sum(len(x['artifacts']) for x in results[1:]),
                   'new_archive_bytes': sum(x['zip_bytes'] for x in results[1:]),
                   'referenced_existing_source_history_archives': len(results[0]['artifacts']),
                   'referenced_existing_source_history_bytes': sum(x['bytes'] for x in results[0]['artifacts'])},
        'historical_curated_exclusion': {
            'path': 'ferocity-recycling/policy-development/red-madness-v0.1/authoring-source-boundaries',
            'reason': 'Parent requested curated historical top-level plans/notes/freezes; retired code-boundary subtree is explicitly outside that addition. No omission from the seven whole audit directories.'},
        'new_executions': {'builds': 0, 'kotlin_cases': 0, 'browser_cases': 0,
                           'development_games': 0, 'evaluation_games': 0, 'confirmation_games': 0},
    }
    write_exclusive(OUT/'MANIFEST.json', (json.dumps(manifest, indent=2)+'\n').encode())
    print(json.dumps(manifest['totals']))
    print(json.dumps([{'id':g['id'],'zip_bytes':g.get('zip_bytes'),'split':g.get('split'),
                      'transport_selection':g.get('transport_selection')} for g in results]))


if __name__ == '__main__':
    main()
