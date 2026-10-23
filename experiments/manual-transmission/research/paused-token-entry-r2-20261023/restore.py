"""Swap only preregistered production/fixture paths for the original negative control."""
from pathlib import Path
import json
import os
import subprocess

protocol = json.loads(Path(os.environ['RECORD'], 'PROTOCOL.json').read_text())
assert protocol['status'] == 'SOURCE_FROZEN_EXECUTION_REQUIRES_ACTIVITY_AND_WORKFLOW_PINS'
mode = os.environ.get('RESTORE_MODE', 'candidate')
assert mode in ('candidate', 'baseline')
for path in protocol['baseline_restore_paths']:
    spec = protocol['source_manifest'][path]
    if mode == 'baseline' and spec.get('baseline_absent'):
        Path(path).unlink(missing_ok=True)
    else:
        source = protocol['baseline_source'] if mode == 'baseline' else os.environ['SOURCE']
        subprocess.run(['git', 'restore', '--source=' + source, '--', path], check=True)
