"""Reproduce additive WAR construction from original R3 evidence, never normalize original bytes."""
from pathlib import Path
import zipfile,hashlib,sys,re,json,tempfile
HERE=Path(__file__).resolve().parent
zpath=HERE/'original.zip'
assert hashlib.sha256(zpath.read_bytes()).hexdigest()=='60270ac90a542c20f2d142a6326576327d52b32fee5ac604b0c41484ea23e37b'
with zipfile.ZipFile(zpath) as z:
 a=z.read('export/WAR.original.json');b=z.read('export/WAR.json');aud=z.read('protocol/audit.py')
with tempfile.TemporaryDirectory() as d:
 p=Path(d);(p/'audit.py').write_bytes(aud);sys.path.insert(0,d);import audit
 headers=list(re.finditer(rb'(?m)^// ([^\n]+)\n',b));i=[h[1] for h in headers].index(b'Fblthp, the Lost')
 start=headers[i].start();end=headers[i+1].start();block=b[start:end]
 assert b[:start]+b[end:]+b'\n'==a
 candidate=a[:start]+block+a[start:]
 assert candidate==(HERE/'WAR.additive.json').read_bytes()
 result=audit.snapshot_insertion(a,candidate)
 for bad in [b,candidate.replace(b'Finale of Revelation',b'Finale of RevelatioN',1),candidate[:start]+block+candidate[start:]]:
  try:audit.snapshot_insertion(a,bad)
  except AssertionError:pass
  else:raise AssertionError('Invalid snapshot accepted')
 print(json.dumps(result))
