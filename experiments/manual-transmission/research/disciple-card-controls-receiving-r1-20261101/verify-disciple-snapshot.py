"""Verify original byte restoration before admitting the single additive MH3 candidate."""
import sys,hashlib,json,re
from pathlib import Path
original=Path(sys.argv[1]).read_bytes();candidate=Path(sys.argv[2]).read_bytes();new=Path(sys.argv[3]).read_bytes()
assert len(original)==67843
assert hashlib.sha256(original).hexdigest()=='b021d21c371237f2f080f751b9ccc256e5de91f4bb559a0c5fb4c50ae537b4c3'
def blocks(b):
 assert b.endswith(b'\n')
 out={}
 for block in b[:-1].split(b'\n\n'):
  header,body=block.split(b'\n',1);assert header.startswith(b'// ')
  name=header[3:].decode();assert name not in out
  json.loads(body);out[name]=block
 return out
old=blocks(original);now=blocks(candidate)
assert len(old)==24 and len(now)==25
assert set(now)-set(old)=={'Disciple of Freyalise'}
assert all(now[n]==b for n,b in old.items())
assert now['Disciple of Freyalise']==new
recovered=b'\n\n'.join(b for n,b in now.items() if n!='Disciple of Freyalise')+b'\n'
assert recovered==original
assert list(now)==sorted(now)
print(json.dumps(dict(original_bytes=len(original),original_sha256=hashlib.sha256(original).hexdigest(),candidate_sha256=hashlib.sha256(candidate).hexdigest(),original_entries=24,additive_entries=1,original_bytes_restored=True)))
