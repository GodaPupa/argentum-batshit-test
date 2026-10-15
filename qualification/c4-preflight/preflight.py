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


from jvm_evidence import scan_failure_logs


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
