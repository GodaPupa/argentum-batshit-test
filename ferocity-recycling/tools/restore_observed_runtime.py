#!/usr/bin/env python3
"""Relocate independently reviewed observed runtime bytes without launching any runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import tarfile
import zipfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()


def read_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=unique,
        parse_constant=lambda value: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))


def relative(text):
    require(isinstance(text, str) and text and not PurePosixPath(text).is_absolute() and
            not any(p in ('', '.', '..') for p in text.split('/')) and '\\' not in text,
            'Unsafe relative member')
    return PurePosixPath(text)


def real(path):
    require(path.is_absolute() and path == path.resolve(strict=True), 'Path must be absolute and have no aliases')
    return path


def write_new(path, data):
    with path.open('xb') as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--qualification-zip', type=Path, required=True)
    parser.add_argument('--zip-sha256', required=True)
    parser.add_argument('--capture-receipt-sha256', required=True)
    parser.add_argument('--source-head', required=True)
    parser.add_argument('--repository', type=Path, required=True)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(re.fullmatch('[0-9a-f]{40}', args.source_head), 'Exact source commit required')
    for value in (args.zip_sha256, args.capture_receipt_sha256):
        require(re.fullmatch('[0-9a-f]{64}', value), 'Exact artifact digest required')
    archive_path, repository, java = real(args.qualification_zip), real(args.repository), real(args.java)
    require(archive_path.is_file() and repository.is_dir() and java.is_file(), 'Input kind mismatch')
    output = args.output
    require(output.is_absolute() and output == output.parent.resolve(strict=True) / output.name and
            not output.exists() and not output.is_symlink() and os.pathsep not in str(output),
            'Output must be one new normalized directory with a real existing parent')
    require(not output.is_relative_to(repository) and not repository.is_relative_to(output), 'Output overlaps source repository')
    require(not any(p.is_relative_to(output) for p in (archive_path, java)), 'Output overlaps input')
    require(file_digest(archive_path) == args.zip_sha256, 'Original qualification ZIP differs')
    output.mkdir()
    report = {'schema': 'ferocity-observed-runtime-relocation-v1', 'status': 'INCOMPLETE',
              'scope': 'Exact ordered runtime byte relocation only; no launch, qualification or admission.',
              'source_head': args.source_head, 'original_zip_sha256': args.zip_sha256,
              'capture_receipt_sha256': args.capture_receipt_sha256,
              'engine_initializations': 0, 'games': 0, 'entropy_draws': 0}
    try:
        with zipfile.ZipFile(archive_path) as archive:
            require(len(archive.namelist()) == len(set(archive.namelist())), 'Duplicate ZIP members')
            receipt_raw = archive.read('qualification/receipt.json')
            receipt = read_json(receipt_raw)
            observation_raw = archive.read('qualification/runtime-classpath-observation/manifest.json')
            observation = read_json(observation_raw)
            capture_raw = archive.read('qualification/observed-gym-runtime/runtime-archive-receipt.json')
            require(digest(capture_raw) == args.capture_receipt_sha256, 'Capture receipt differs')
            capture = read_json(capture_raw)
            require(capture['schema'] == 'ferocity-observed-gym-runtime-archive-v1' and
                    capture['status'] == 'ARCHIVED_OBSERVED_BYTES_REQUIRES_INDEPENDENT_REVIEW', 'Unsupported capture')
            require(capture['source_head'] == receipt['source_head'] == args.source_head and
                    receipt['status'] == 'PASS' and receipt['compiled_inputs_unchanged'] is True, 'Qualification binding differs')
            require(capture['qualification_receipt_sha256'] == observation['receipt_sha256'] == digest(receipt_raw) and
                    capture['observation_sha256'] == digest(observation_raw), 'Observation binding differs')
            require(file_digest(java) == capture['java_executable']['sha256'], 'Local Java executable differs from captured identity')
            require(observation['status'] == 'OBSERVED' and len(observation['stages']) == 1, 'Unsupported observation')
            stage = observation['stages'][0]
            require(stage['module'] == 'gym' and stage['status'] == 'OBSERVED' and len(stage['matching_workers']) == 1, 'Ambiguous worker')
            worker = stage['matching_workers'][0]
            entries = capture['ordered_classpath']
            require(entries == worker['classpath'] and entries and len(entries) <= 1024 and
                    capture['actual_worker_argument_file_sha256'] == worker['argument_file_sha256'], 'Classpath order or worker differs')
            pins, expected = [], []
            for index, entry in enumerate(entries):
                require(entry['kind'] in ('repository', 'gradle_home'), 'Unsupported original path origin')
                original = relative(entry['path'])
                folder = output / 'classpath' / f'{index:03d}'
                folder.mkdir(parents=True)
                if entry['type'] == 'file':
                    destination = folder / original.name
                    pins.append({'path': str(destination), 'directory': False, 'sha256': entry['sha256'], 'files': {}})
                    selected = [('runtime', destination, entry['sha256'], entry['bytes'])]
                else:
                    require(entry['type'] == 'directory', 'Unsupported classpath kind')
                    files = {m['path']: m['sha256'] for m in entry['members']}
                    require(len(files) == len(entry['members']), 'Duplicate directory members')
                    pins.append({'path': str(folder), 'directory': True, 'sha256': digest(canonical(files)), 'files': files})
                    selected = [(str(relative(m['path'])), folder.joinpath(*relative(m['path']).parts), m['sha256'], m['bytes'])
                                for m in entry['members']]
                for name, destination, sha, size in selected:
                    require(re.fullmatch('[0-9a-f]{64}', sha) and isinstance(size, int) and size >= 0, 'Invalid member identity')
                    expected.append(({'path': f'classpath/{index:03d}/{name}', 'classpath_index': index,
                                      'sha256': sha, 'bytes': size}, destination))
            require([m for m, _ in expected] == capture['archive']['members'], 'Archive member map differs from ordered entries')
            require(len({m['path'] for m, _ in expected}) == len(expected), 'Duplicate archive destinations')
            tar_name = 'qualification/observed-gym-runtime/' + str(relative(capture['archive']['path']))
            # Check the exact compressed tar before writing any archived member.
            with archive.open(tar_name) as stream:
                require(hashlib.file_digest(stream, 'sha256').hexdigest() == capture['archive']['sha256'], 'Runtime tar digest differs')
            require(archive.getinfo(tar_name).file_size == capture['archive']['bytes'], 'Runtime tar size differs')
            with archive.open(tar_name) as stream, tarfile.open(fileobj=stream, mode='r|gz') as tar:
                count = 0
                for member in tar:
                    require(count < len(expected), 'Extra tar member')
                    expected_member, destination = expected[count]
                    require(member.isfile() and member.name == expected_member['path'] and member.size == expected_member['bytes'],
                            'Tar member order, kind, name or size differs')
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    require(destination.parent == destination.parent.resolve(strict=True), 'Destination parent alias')
                    with tar.extractfile(member) as source, destination.open('xb') as target:
                        sha = hashlib.sha256()
                        for block in iter(lambda: source.read(65536), b''):
                            sha.update(block); target.write(block)
                        target.flush(); os.fsync(target.fileno())
                    require(sha.hexdigest() == expected_member['sha256'], 'Member bytes differ')
                    count += 1
                require(count == len(expected), 'Missing tar member')
            for pin in pins:
                path = real(Path(pin['path']))
                if pin['directory']:
                    files = {}
                    for child in path.rglob('*'):
                        real(child)
                        require(child.is_file() or child.is_dir(), 'Unsupported restored member')
                        if child.is_file(): files[child.relative_to(path).as_posix()] = file_digest(child)
                    require(files == pin['files'] and digest(canonical(files)) == pin['sha256'], 'Restored directory differs')
                else:
                    require(path.is_file() and file_digest(path) == pin['sha256'], 'Restored file differs')
            require(file_digest(archive_path) == args.zip_sha256 and file_digest(java) == capture['java_executable']['sha256'],
                    'Original ZIP or Java identity changed during relocation')
            write_new(output / 'ordered-classpath.json', canonical(pins) + b'\n')
            write_new(output / 'original-capture-receipt.json', capture_raw)
            report.update(status='RESTORED_EXACT_BYTES_REQUIRES_INDEPENDENT_REVIEW', ordered_classpath=pins,
                          ordered_classpath_sha256=file_digest(output / 'ordered-classpath.json'),
                          archive_members=count, java_executable={'path': str(java), 'sha256': file_digest(java)})
    except Exception as error:
        report['error'] = {'type': type(error).__name__, 'message': str(error)}
        raise
    finally:
        write_new(output / 'relocation-receipt.json', json.dumps(report, indent=2).encode() + b'\n')
        print(json.dumps({'status': report['status'], 'receipt': str(output / 'relocation-receipt.json')}))


if __name__ == '__main__':
    main()
