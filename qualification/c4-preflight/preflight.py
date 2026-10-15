#!/usr/bin/env python3
"""V3 blocked source candidate. No compile subprocess or supervisor backend exists."""
import base64, hashlib, json, os, pathlib, re, sys, traceback
from memory_collector import sample, assess, POSITIVE_STATUSES, CONTAINMENT_BLOCKER

HERE = pathlib.Path(__file__).resolve().parent
OUT = pathlib.Path(os.environ['RUNNER_TEMP']) / 'c4-evidence'
OUT.mkdir(exist_ok=False)
REV = os.environ['C4_REVISION']
PINS = {'R5': ('b5195ad2d090d26a533aef5bf3fa6a486b5128c6', 'e8e719c42829eeae631b3efeb48e86468f18209f'),
        'R6': ('1e334b9625478689c15a7d153e74d3d805791506', '9e66bc429228cebc7a9dd13c93075d30c99aefba')}

def write(name, value):
    (OUT / name).write_text(json.dumps(value, indent=2) + '\n')

def run(*args, **kwargs):
    """Compatibility denial hook: V3 has no subprocess/compile backend."""
    raise RuntimeError('BLOCKED_D2: no trusted supervisor backend; compile not started')


def scan_failure_logs(records, required_kinds=('compiler', 'gradle', 'kotlin')):
    """Scan attributed log bytes, retaining exact paths/hashes and diagnostic gaps."""
    findings, gaps, receipts, seen = [], [], [], set()
    signatures = [
        r'^(?:Exception in thread "[^"]+"\s+|Caused by:\s*)?java\.lang\.OutOfMemoryError(?:\s*:.*)?$',
        r'^#\s*A fatal error has been detected by the Java Runtime Environment:?$',
        r'^#\s*There is insufficient memory for the Java Runtime Environment to continue\.?$',
        r'^Expiring Daemon because JVM heap space is exhausted\.?$',
        r'^Could not connect to kotlin daemon\.\s*Using fallback strategy\.?$',
        r'^Gradle build daemon disappeared unexpectedly(?:\s*\(it may have been killed or may have crashed\))?\.?$',
        r'^Daemon compilation failed(?::.*)?$',
        r'^Unable to connect to Kotlin compile daemon\.?$',
        r'^Using fallback strategy:\s*Compile without Kotlin daemon\.?$',
    ]
    hazard = re.compile(r'java\.lang\.OutOfMemoryError|fatal error has been detected|insufficient memory for the Java Runtime|heap space is exhausted|daemon.*(?:disappeared|fallback|compilation failed)|could not connect to kotlin daemon', re.I)
    for record in records:
        entry = dict(record)
        try:
            data = pathlib.Path(record['path']).read_bytes()
            entry.update(sha256=hashlib.sha256(data).hexdigest(), bytes=len(data), raw_base64=base64.b64encode(data).decode('ascii'))
            text = data.decode('utf-8', errors='strict')
            if not text.strip():
                raise ValueError('empty log')
            if not record.get('source_bound'):
                gaps.append({'path': record['path'], 'reason': 'log process/origin attribution unverified'})
            else:
                seen.add(record['kind'])
                for number, line in enumerate(text.splitlines(), 1):
                    normalized = re.sub(r'^(?:e:|>)\s*', '', line.strip())
                    literal = bool(re.search(r'\b(?:simulated|example|documentation|echo)\b', normalized, re.I))
                    if not literal and any(re.fullmatch(pattern, normalized, re.I) for pattern in signatures):
                        findings.append({'path': record['path'], 'sha256': entry['sha256'], 'line': number, 'text': line})
                    elif hazard.search(line) and not re.match(r'^\s*INFO documentation:', line):
                        gaps.append({'path': record['path'], 'line': number, 'text': line,
                                     'reason': 'ambiguous fatal-like text; no definitive attribution from wording'})
        except (OSError, ValueError) as exc:
            entry['error'] = str(exc); gaps.append({'path': record['path'], 'reason': str(exc)})
        receipts.append(entry)
    gaps.extend({'reason': 'required log kind missing', 'kind': k} for k in required_kinds if k not in seen)
    return {'status': 'FAIL' if findings else ('INCOMPLETE' if gaps else 'NO_FATAL_SIGNATURE_OBSERVED'),
            'findings': findings, 'gaps': gaps, 'logs': receipts, 'complete': not gaps,
            'provenance': 'caller-asserted source association; not authenticated supervisor evidence'}


def qualification_status(memory, logs, process_scope, compile_ok):
    if memory['status'] in POSITIVE_STATUSES or logs['status'] == 'FAIL':
        return 'FAIL'
    # No JSON, dict, Python object or caller assertion authenticates containment.
    # A reviewed OS-backed supervisor and evidence verifier do not yet exist.
    return 'BLOCKED'

def main():
    if REV not in PINS:
        raise ValueError('unknown revision')
    discovery = sample()
    write('memory-discovery.json', discovery)
    if not discovery['available'] or not discovery.get('qualifying_scope'):
        raise ValueError('authoritative subtree OOM counters unavailable; compile not started')
    # Capability design model is not trusted kernel evidence. No executable
    # compiler backend exists in V3; runtime flags cannot introduce one.
    write('process-scope.json', {'status': 'BLOCKED', 'qualifying': False, 'reason': CONTAINMENT_BLOCKER})
    write('result.json', {'status': 'BLOCKED', 'revision': REV, 'reason': CONTAINMENT_BLOCKER,
                          'compile_started': False})
    return 2

if __name__ == '__main__':
    try: code = main()
    except Exception as exc:
        write('result.json', {'status': 'INCOMPLETE', 'revision': REV, 'reason': str(exc)})
        (OUT / 'exception.txt').write_text(traceback.format_exc()); code = 2
    finally:
        entries = [f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(OUT)}' for p in sorted(OUT.rglob('*')) if p.is_file() and p.name != 'SHA256SUMS']
        (OUT / 'SHA256SUMS').write_text('\n'.join(entries)+'\n')
    sys.exit(code)
