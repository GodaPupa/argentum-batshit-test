"""Source-local fatal log diagnostics; containment authority lives elsewhere."""
import base64, hashlib, pathlib, re

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

