import subprocess,json,tempfile
from pathlib import Path
original=Path('MH3.original.json').read_bytes()
blocks=original[:-1].split(b'\n\n'); assert len(blocks)==24
new=b'// Disciple of Freyalise\n{"SYNTHETIC_ONLY":true}'
full=b'\n\n'.join(sorted(blocks+[new]))+b'\n'
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp);(p/'original').write_bytes(original);(p/'new').write_bytes(new)
 def check(data):
  (p/'candidate').write_bytes(data)
  return subprocess.run(['python','verify-disciple-snapshot.py',str(p/'original'),str(p/'candidate'),str(p/'new')],capture_output=True).returncode
 assert check(full)==0
 bad={'old_byte_changed':full.replace(blocks[0],blocks[0]+b' ',1),'old_block_removed':full.replace(blocks[0]+b'\n\n',b'',1),'extra_entry':full+b'\n// Unauthorized\n{}\n','missing_addition':original,'reordered':b'\n\n'.join(reversed(sorted(blocks+[new])))+b'\n'}
 for label,data in bad.items():assert check(data)!=0,label
 print(json.dumps(dict(synthetic_only=True,original_entries=24,corruptions_rejected=list(bad),real_candidate_generated=False)))
