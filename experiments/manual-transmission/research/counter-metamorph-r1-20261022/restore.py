from pathlib import Path
import json,os,subprocess
p=json.loads(Path(os.environ['RECORD'],'PROTOCOL.json').read_text())
mode=os.environ.get('RESTORE_MODE','candidate')
for path,v in p['source_manifest'].items():
    if '/src/main/' not in path and path not in p['negative_excluded_tests'] and path not in p['baseline_restore_paths']: continue
    if mode=='baseline' and (v.get('baseline_absent') or path in p['negative_excluded_tests']):
        Path(path).unlink()
    else:
        source=p['baseline_source'] if mode=='baseline' else 'HEAD'
        subprocess.run(['git','restore','--source='+source,'--',path],check=True)
