#!/usr/bin/env python3
"""Read back one existing public artifact; never execute its contents."""
import base64
import binascii
import codecs
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import resource
import signal
import stat
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

REPO = 'GodaPupa/argentum-batshit-test'
BRANCH = 'refs/heads/lab/izzet-factory-artifact-readback-20260927'
ORIGINAL_HEAD = 'a0fe959d2fa345b77513e199e2db046d52cf2d0f'
RUN = 36295267824
ARTIFACT = 10923618567
SIZE = 1945147
DIGEST = 'a8ea41fbe787b944d584fca6a33b78a6d88a01244fb1487c9f7d6f2224d2d1b5'
PATHS = ['readback.py', 'scope.json', '.github/workflows/izzet-factory-artifact-readback.yml']
ORIGINAL_CONTROLS = {
    'control/qualify.py': 'ed3c72a1fa5494598f212d0981e37eb0f6cf1c9116bf61938f2fa8dca9ad88e8',
    'control/gate.json': '4b8a07746888eab107132fb30baed354e2ca7b481760e48556b6dd7f6296a826',
    'control/.github/workflows/izzet-legacy-factory-qualification.yml':
        '53165b8e9920c3c6c46c16a5c642106fbe8a5d010a18ebe6661c6fc9b6d6d74c'}
LIMITS = {'seconds': 300, 'memory': 256 * 1024 * 1024,
          'uncompressed': 128 * 1024 * 1024, 'members': 512,
          'stdout': 4 * 1024 * 1024, 'metadata': 256 * 1024,
          'json_parse_each': 4 * 1024 * 1024, 'json_parse_total': 16 * 1024 * 1024,
          'raw_record_each': 512 * 1024, 'raw_record_total': 1024 * 1024,
          'xml_each': 512 * 1024, 'line_each': 1024 * 1024,
          'log_selected_each': 64 * 1024, 'log_selected_total': 256 * 1024}
DEADLINE = 0.0

class Stop(Exception):
    pass

def require(condition, code):
    if not condition:
        raise Stop(code)

def deadline():
    require(time.monotonic() < DEADLINE, 'DEADLINE')

def timed_out(signum, frame):
    raise Stop('DEADLINE')

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

def git_blob(raw):
    return hashlib.sha1(b'blob ' + str(len(raw)).encode('ascii') + b'\0' + raw).hexdigest()

def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'DUPLICATE_JSON_KEY')
        result[key] = value
    return result

def reject_nonfinite(value):
    raise Stop('NONFINITE_JSON_CONSTANT')

def parse_json(raw):
    deadline()
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique_pairs,
                      parse_constant=reject_nonfinite)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

OPENER = urllib.request.build_opener(NoRedirect)

def request(url, token=None):
    parsed = urllib.parse.urlsplit(url)
    require(parsed.scheme == 'https' and not parsed.username and not parsed.password
            and not parsed.fragment and parsed.port in (None, 443), 'HTTPS_URL_BOUNDARY')
    headers = {'User-Agent': 'argentum-exact-public-artifact-readback',
               'Accept-Encoding': 'identity'}
    if token is not None:
        require(parsed.hostname == 'api.github.com', 'AUTH_HOST_BOUNDARY')
        headers.update({'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json',
                        'X-GitHub-Api-Version': '2022-11-28'})
    deadline()
    # No redirect is followed by this opener. Exceptions are never printed with URLs.
    return OPENER.open(urllib.request.Request(url, headers=headers),
                       timeout=max(0.1, min(20.0, DEADLINE - time.monotonic())))

def bounded_read(response, cap):
    result = bytearray()
    while True:
        deadline()
        part = response.read(min(65536, cap + 1 - len(result)))
        if not part:
            break
        result.extend(part)
        require(len(result) <= cap, 'HTTP_BYTE_LIMIT')
    deadline()
    return bytes(result)

def api(path, token):
    require(path.startswith('/repos/' + REPO + '/'), 'API_PATH_BOUNDARY')
    with request('https://api.github.com' + path, token) as response:
        require(response.status == 200, 'API_STATUS')
        return parse_json(bounded_read(response, LIMITS['metadata']))

def fetch_original(token):
    path = '/repos/' + REPO + '/actions/artifacts/' + str(ARTIFACT) + '/zip'
    try:
        response = request('https://api.github.com' + path, token)
    except urllib.error.HTTPError as error:
        try:
            require(error.code == 302, 'ARTIFACT_REDIRECT_STATUS')
            location = error.headers.get('Location')
            require(isinstance(location, str) and len(location) <= 16384, 'ARTIFACT_LOCATION')
        finally:
            error.close()
    else:
        response.close()
        raise Stop('ARTIFACT_EXPECTED_SIGNED_REDIRECT')
    parsed = urllib.parse.urlsplit(location)
    require(parsed.scheme == 'https' and parsed.hostname is not None
            and parsed.hostname.endswith('.blob.core.windows.net')
            and not parsed.username and not parsed.password and not parsed.fragment
            and parsed.port in (None, 443), 'SIGNED_STORAGE_HOST_BOUNDARY')
    # This distinct request carries no API token and follows no further redirect.
    with request(location) as response:
        require(response.status == 200, 'SIGNED_STORAGE_STATUS')
        length = response.headers.get('Content-Length')
        require(length is None or int(length) == SIZE, 'SIGNED_STORAGE_LENGTH')
        raw = bounded_read(response, SIZE)
    require(len(raw) == SIZE and digest(raw) == DIGEST, 'ORIGINAL_SIZE_OR_SHA256')
    return raw

def verify_control(scope, token):
    require(scope['ready_for_readback'] is True and isinstance(scope['source_review'], dict)
            and re.fullmatch(r'[0-9a-f]{64}', scope['source_review'].get('sha256', '')),
            'UNREVIEWED_CONTROL')
    require(scope['artifact'] == {'id': ARTIFACT, 'run_id': RUN, 'run_attempt': 1,
            'head': ORIGINAL_HEAD, 'bytes': SIZE, 'sha256': DIGEST}, 'SCOPE_IDENTITY')
    require(scope['limits'] == LIMITS, 'SCOPE_LIMITS')
    require(os.environ.get('GITHUB_REPOSITORY') == REPO
            and os.environ.get('GITHUB_REF') == BRANCH
            and os.environ.get('GITHUB_EVENT_NAME') == 'push'
            and os.environ.get('GITHUB_RUN_ATTEMPT') == '1', 'ONE_CREATION_EVENT')
    head = os.environ.get('GITHUB_SHA', '')
    require(re.fullmatch(r'[0-9a-f]{40}', head) is not None, 'CONTROL_HEAD')
    event_path = Path(os.environ['GITHUB_EVENT_PATH'])
    with event_path.open('rb') as stream:
        event_raw = stream.read(1024 * 1024 + 1)
    require(len(event_raw) <= 1024 * 1024, 'EVENT_BYTE_LIMIT')
    event = parse_json(event_raw)
    require(event.get('created') is True and event.get('before') == '0' * 40
            and event.get('after') == head and event.get('ref') == BRANCH, 'CREATION_PAYLOAD')
    commit = api('/repos/' + REPO + '/git/commits/' + head, token)
    require(commit['sha'] == head and [p['sha'] for p in commit['parents']] == [ORIGINAL_HEAD],
            'ISOLATED_CONTROL_PARENT')
    tree = api('/repos/' + REPO + '/git/trees/' + commit['tree']['sha'] + '?recursive=1', token)
    require(tree.get('truncated') is False, 'CONTROL_TREE_TRUNCATED')
    blobs = {r['path']: r for r in tree['tree'] if r['type'] == 'blob'}
    require(sorted(blobs) == sorted(PATHS)
            and all(r['type'] in ('blob', 'tree') for r in tree['tree']), 'THREE_CONTROL_FILES_ONLY')
    actual = {}
    for path in PATHS:
        raw = Path(path).read_bytes()
        require(len(raw) <= 128 * 1024 and blobs[path]['mode'] == '100644'
                and git_blob(raw) == blobs[path]['sha'], 'CONTROL_BYTE_DRIFT')
        actual[path] = {'bytes': len(raw), 'sha256': digest(raw), 'git_blob': blobs[path]['sha']}
    for path, expected in scope['reviewed_file_sha256'].items():
        require(path in ('readback.py', PATHS[2]) and actual[path]['sha256'] == expected,
                'REVIEWED_FILE_DRIFT')
    require(set(scope['reviewed_file_sha256']) == {'readback.py', PATHS[2]}, 'REVIEW_FILE_SET')
    return {'head': head, 'tree': commit['tree']['sha'], 'files': actual,
            'readback_run_id': os.environ.get('GITHUB_RUN_ID'), 'attempt': 1,
            'event': 'push', 'created': True, 'original_parent': ORIGINAL_HEAD,
            'source_review': scope['source_review']}

def read_zip(raw):
    inventory, retained, json_values, excerpts = [], {}, {}, {}
    total = retained_total = parsed_total = excerpt_total = 0
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        require(len(entries) <= LIMITS['members'], 'MEMBER_COUNT')
        seen = set()
        for entry in entries:
            deadline()
            name = entry.filename
            path = PurePosixPath(name)
            require(name and len(name) <= 512 and name not in seen and '\0' not in name
                    and '\\' not in name and not path.is_absolute()
                    and all(p not in ('', '.', '..') for p in name.rstrip('/').split('/'))
                    and not re.match(r'^[A-Za-z]:', name), 'ZIP_MEMBER_PATH_OR_DUPLICATE')
            seen.add(name)
            require(not entry.flag_bits & 1 and entry.compress_type in
                    (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), 'ZIP_ENCRYPTION_OR_CODEC')
            mode = entry.external_attr >> 16
            require(not stat.S_ISLNK(mode) and (not stat.S_IFMT(mode) or
                    stat.S_ISREG(mode) or stat.S_ISDIR(mode)), 'ZIP_SPECIAL_MEMBER')
            require(entry.file_size >= 0 and entry.compress_size >= 0, 'ZIP_SIZE')
            total += entry.file_size
            require(total <= LIMITS['uncompressed'], 'ZIP_TOTAL_UNCOMPRESSED')
            is_json = name.endswith('.json')
            is_xml = name.endswith('.xml')
            is_control = name in ORIGINAL_CONTROLS
            is_text_record = (is_control or is_json or is_xml
                or name.endswith('/exit-status.txt') or name == 'build-semaphore/os-release.txt'
                or name.endswith('-apt-metadata.txt'))
            is_source_map = bool(re.search(r'(^|/)source-(before|after)(-[^/]*)?\.json$', name))
            if is_json:
                parsed_total += entry.file_size
                require(entry.file_size <= LIMITS['json_parse_each']
                        and parsed_total <= LIMITS['json_parse_total'], 'JSON_PARSE_LIMIT')
            if is_xml:
                require(entry.file_size <= LIMITS['xml_each'], 'XML_BYTE_LIMIT')
            keep_raw = is_text_record and entry.file_size <= LIMITS['raw_record_each']
            require(not is_text_record or keep_raw or is_source_map, 'REQUIRED_RAW_RECORD_TOO_LARGE')
            if keep_raw:
                retained_total += entry.file_size
                require(retained_total <= LIMITS['raw_record_total'], 'RAW_RECORD_TOTAL')
            buffer = bytearray() if keep_raw or is_json else None
            h = hashlib.sha256()
            crc = count = 0
            log_lines, log_line_no, line_buffer, selected_bytes = [], 0, '', 0
            decoder = codecs.getincrementaldecoder('utf-8')('strict') if name.endswith('.log') else None
            def take_lines(text, final=False):
                nonlocal line_buffer, log_line_no, selected_bytes, excerpt_total
                line_buffer += text
                require(len(line_buffer.encode('utf-8')) <= LIMITS['line_each'], 'LOG_LINE_LIMIT')
                lines = line_buffer.split('\n')
                line_buffer = '' if final else lines.pop()
                if final and lines == ['']:
                    lines = []
                for line in lines:
                    log_line_no += 1
                    if re.search(r'^> Task |^Gradle Test Executor \d+ (started|finished) executing tests\.$'
                                 r'|running ./gradlew unlocked|shlock not found', line):
                        size = len(line.encode('utf-8')) + 64
                        selected_bytes += size
                        excerpt_total += size
                        require(selected_bytes <= LIMITS['log_selected_each']
                                and excerpt_total <= LIMITS['log_selected_total'], 'LOG_EXCERPT_LIMIT')
                        log_lines.append({'line': log_line_no, 'text': line})
            with archive.open(entry, 'r') as stream:
                while True:
                    deadline()
                    chunk = stream.read(65536)
                    if not chunk:
                        break
                    count += len(chunk)
                    require(count <= entry.file_size and count <= LIMITS['uncompressed'], 'MEMBER_READ_LIMIT')
                    h.update(chunk)
                    crc = binascii.crc32(chunk, crc)
                    if buffer is not None:
                        buffer.extend(chunk)
                    if decoder is not None:
                        take_lines(decoder.decode(chunk))
            if decoder is not None:
                take_lines(decoder.decode(b'', final=True), final=True)
                excerpts[name] = {'kind': 'explicit_line_subset_not_full_log',
                                  'line_count': log_line_no, 'selected': log_lines}
            require(count == entry.file_size and (crc & 0xffffffff) == entry.CRC, 'MEMBER_CRC_OR_LENGTH')
            row = {'path': name, 'bytes': count, 'compressed_bytes': entry.compress_size,
                   'crc32': format(crc & 0xffffffff, '08x'), 'sha256': h.hexdigest(),
                   'is_directory': entry.is_dir(), 'raw_text_relayed': keep_raw}
            if is_text_record and not keep_raw:
                row['raw_omission'] = 'large source map: complete original ZIP relayed; parsed strict comparisons only'
            elif not keep_raw:
                row['raw_omission'] = 'not selected text; complete original ZIP relayed'
            inventory.append(row)
            if keep_raw:
                exact = bytes(buffer)
                exact.decode('utf-8', errors='strict')
                retained[name] = exact
            if is_json:
                json_values[name] = parse_json(bytes(buffer))
    return inventory, retained, json_values, excerpts

def observations(inventory, retained, values, excerpts):
    findings = []
    by_name = {row['path']: row for row in inventory}
    def observe(condition, label):
        findings.append({'check': label, 'observed': bool(condition)})
    for name, expected in ORIGINAL_CONTROLS.items():
        observe(name in by_name and by_name[name]['sha256'] == expected, 'original-control:' + name)
    gate = values.get('control/gate.json', {})
    audit = values.get('audit.json', {})
    observe(audit.get('control_head') == ORIGINAL_HEAD and str(audit.get('run_id')) == str(RUN)
            and str(audit.get('attempt')) == '1', 'original-audit-identity')
    observe(audit.get('official_games') == 0 and audit.get('official_seeds') == 0
            and audit.get('gameplay_authorized') is False, 'recorded-no-official-execution')
    source_comparisons = []
    for name, value in values.items():
        if re.search(r'(^|/)source-(before|after)(-[^/]*)?\.json$', name):
            role = value.get('role')
            expected = gate.get('sources', {}).get(role, {})
            comparison = {'path': name, 'role': role, 'head': value.get('head'),
                          'tree': value.get('tree'), 'errors': value.get('errors'),
                          'clean': value.get('status') == '',
                          'identity_equal': value.get('head') == expected.get('head')
                              and value.get('tree') == expected.get('tree'),
                          'authority_equal': value.get('authority_sha256') == gate.get('preserved_authority_sha256'),
                          'dependencies_equal': value.get('dependency_sha256') == gate.get('dependency_sha256'),
                          'production_equal': value.get('production_sha256') == expected.get('production_sha256')}
            source_comparisons.append(comparison)
    required = set(ORIGINAL_CONTROLS) | {'audit.json', 'build-semaphore/prerequisite.json',
        'build-semaphore/os-release.txt', 'source-before-before.json', 'source-before-fixed.json',
        'source-after-before.json', 'source-after-fixed.json'}
    xml_summaries = []
    banks = gate.get('banks', [])
    observe(len(banks) == 3, 'original-three-bank-gate')
    for i, bank in enumerate(banks, 1):
        folder = str(i).zfill(2) + '-' + bank['stage']
        required.update(folder + '/' + x for x in ['source-before.json', 'source-after.json',
            'command.json', 'command.log', 'command.log.process-cleanup.json', 'exit-status.txt'])
        xml_path = folder + '/TEST-' + bank['class'] + '.xml'
        required.add(xml_path)
        if xml_path in retained:
            content = retained[xml_path]
            require(b'<!DOCTYPE' not in content.upper() and b'<!ENTITY' not in content.upper(),
                    'XML_ENTITY_DECLARATION')
            root = ET.fromstring(content)
            cases = root.findall('testcase')
            rows = [{'name': c.get('name'), 'class': c.get('classname'),
                     'failures': len(c.findall('failure')), 'errors': len(c.findall('error')),
                     'skipped': len(c.findall('skipped'))} for c in cases]
            xml_summaries.append({'path': xml_path, 'root': root.tag, 'attributes': root.attrib,
                'case_records': rows, 'raw_failure_preserved': True,
                'case_names_equal_gate': sorted(c.get('name', '') for c in cases) == sorted(bank['case_names'])})
    missing = sorted(required - set(by_name))
    observe(not missing, 'all-declared-original-records-present')
    return {'kind': 'derived_observations_not_independent_admission', 'findings': findings,
            'missing_required_paths': missing, 'source_map_comparisons': source_comparisons,
            'xml_summaries': xml_summaries, 'log_line_subsets': excerpts,
            'no_tests_or_packages_executed_by_readback': True,
            'no_qualification_or_gameplay_admission': True}

def emit(raw, control, metadata, inventory, retained, derived):
    lines = []
    def add(kind, payload):
        deadline()
        lines.append('ARGENTUM_READBACK ' + json.dumps({'kind': kind, **payload},
                     sort_keys=True, separators=(',', ':'), ensure_ascii=True) + '\n')
    encoded = base64.b64encode(raw).decode('ascii')
    chunks = [encoded[i:i + 16384] for i in range(0, len(encoded), 16384)]
    identity = {'artifact_id': ARTIFACT, 'original_run_id': RUN, 'original_attempt': 1,
                'original_head': ORIGINAL_HEAD, 'zip_bytes': SIZE, 'zip_sha256': DIGEST,
                'base64_chars': len(encoded), 'zip_chunks': len(chunks),
                'chunk_char_limit': 16384}
    add('BEGIN', {**identity, 'control': control, 'metadata': metadata,
        'scope': 'additional separately reviewed read-only transport of exact original',
        'prior_transport': 'First transfer retained by owner; private locator deliberately omitted.',
        'prior_local_materialization': 'failed environment_offline; original never locally audited',
        'limitations': ['Decoded-job-log tool maximum is unspecified; consumer must verify full END and hashes.',
            'Member hashes/CRC are complete; selected log lines and derived checks are not full independent admission.',
            'No fixture, JVM, Gradle, package, source runtime, policy, claim or seed command runs.']})
    for i, chunk in enumerate(chunks):
        add('ZIP_CHUNK', {'index': i, 'count': len(chunks), 'base64': chunk})
    for row in inventory:
        add('MEMBER', row)
    for path, exact in sorted(retained.items()):
        encoded_text = base64.b64encode(exact).decode('ascii')
        parts = [encoded_text[i:i + 16384] for i in range(0, len(encoded_text), 16384)] or ['']
        for i, part in enumerate(parts):
            add('RAW_RECORD', {'path': path, 'bytes': len(exact), 'sha256': digest(exact),
                              'index': i, 'count': len(parts), 'base64': part})
    derived_raw = json.dumps(derived, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode('ascii')
    derived_encoded = base64.b64encode(derived_raw).decode('ascii')
    derived_parts = [derived_encoded[i:i + 16384] for i in range(0, len(derived_encoded), 16384)]
    for i, part in enumerate(derived_parts):
        add('DERIVED_CHUNK', {'index': i, 'count': len(derived_parts), 'bytes': len(derived_raw),
                             'sha256': digest(derived_raw), 'base64': part})
    body = ''.join(lines).encode('ascii')
    end = {'kind': 'END', **identity, 'complete_original_relay': True,
           'body_bytes': len(body), 'body_sha256': digest(body), 'body_lines': len(lines),
           'members': len(inventory), 'raw_records': len(retained),
           'all_required_records_present': not derived['missing_required_paths'],
           'qualification_accepted': False, 'gameplay_accepted': False}
    final = ('ARGENTUM_READBACK ' + json.dumps(end, sort_keys=True,
             separators=(',', ':'), ensure_ascii=True) + '\n').encode('ascii')
    require(len(body) + len(final) <= LIMITS['stdout'], 'STDOUT_RELAY_BUDGET')
    deadline()
    # Each framed line is ASCII; external truncation remains detectable by END, indices and both hashes.
    sys.stdout.buffer.write(body)
    sys.stdout.buffer.write(final)
    sys.stdout.buffer.flush()
    return 0 if not derived['missing_required_paths'] else 2

def main():
    global DEADLINE
    DEADLINE = time.monotonic() + LIMITS['seconds']
    signal.signal(signal.SIGALRM, timed_out)
    signal.alarm(LIMITS['seconds'])
    resource.setrlimit(resource.RLIMIT_AS, (LIMITS['memory'], LIMITS['memory']))
    resource.setrlimit(resource.RLIMIT_CPU, (LIMITS['seconds'], LIMITS['seconds']))
    scope = parse_json(Path('scope.json').read_bytes())
    token = os.environ.get('READBACK_TOKEN')
    require(bool(token), 'MISSING_READ_ONLY_TOKEN')
    control = verify_control(scope, token)
    run = api('/repos/' + REPO + '/actions/runs/' + str(RUN), token)
    artifact = api('/repos/' + REPO + '/actions/artifacts/' + str(ARTIFACT), token)
    require(run['id'] == RUN and run['head_sha'] == ORIGINAL_HEAD and run['run_attempt'] == 1
            and run['event'] == 'push' and run['status'] == 'completed', 'ORIGINAL_RUN_IDENTITY')
    require(artifact['id'] == ARTIFACT and artifact['name'] == 'izzet-legacy-factory-36295267824-1'
            and artifact['size_in_bytes'] == SIZE and artifact['digest'] == 'sha256:' + DIGEST
            and artifact['expired'] is False and artifact['workflow_run']['id'] == RUN,
            'ORIGINAL_ARTIFACT_IDENTITY')
    raw = fetch_original(token)
    # API token has no purpose during parsing/relay and is not serialized anywhere.
    del token
    os.environ.pop('READBACK_TOKEN', None)
    inventory, retained, values, excerpts = read_zip(raw)
    derived = observations(inventory, retained, values, excerpts)
    metadata = {'original_run_conclusion': run.get('conclusion'),
                'artifact_name': artifact['name'], 'artifact_created_at': artifact.get('created_at'),
                'artifact_expires_at': artifact.get('expires_at')}
    return emit(raw, control, metadata, inventory, retained, derived)

if __name__ == '__main__':
    try:
        status = main()
    except BaseException as error:
        # Deliberately exclude exception strings/tracebacks: urllib can embed signed URLs.
        code = str(error) if isinstance(error, Stop) else type(error).__name__
        if not re.fullmatch(r'[A-Za-z0-9_]{1,80}', code):
            code = 'UNCLASSIFIED_READBACK_ERROR'
        print('ARGENTUM_READBACK_ERROR ' + json.dumps({'complete_original_relay': False,
              'code': code, 'qualification_accepted': False}), flush=True)
        status = 1
    sys.exit(status)
