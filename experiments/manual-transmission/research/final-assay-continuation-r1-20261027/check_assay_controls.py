"""Synthetic parser controls only; never represents engine or Assay execution."""
from pathlib import Path
import tempfile,subprocess,json,os,hashlib,gzip
H=Path(__file__).resolve().parent
def check(mode):
 with tempfile.TemporaryDirectory() as td:
  p=Path(td)/'assay';p.mkdir()
  for n in ['build','gate','differential','corpus-capture']:(p/(n+'-exit.txt')).write_text('0')
  metrics=['Cards assayed','Ambiguous — distinct readings','Print mismatch','Normalization not invertible','Full inverse not reproduced','Redundant readings (same model)']
  s='\n'.join(n+': '+str(2000 if i==0 else 0) for i,n in enumerate(metrics))+'\n'
  (p/'gate.log').write_text(s);(p/'differential.log').write_bytes((H/'BASELINE_DIFFERENTIAL.log').read_bytes())
  b=gzip.compress(b'{"synthetic_auditor_fixture":true}\n',mtime=0)
  (p/'oracle-bulk-corpus.jsonl.gz').write_bytes(b);(p/'oracle-bulk-sha256.txt').write_text(hashlib.sha256(b).hexdigest());(p/'oracle-bulk-bytes.txt').write_text(str(len(b)))
  if mode=='floor':(p/'gate.log').write_text(s.replace('2000','1999'))
  elif mode=='duplicate_metric':(p/'gate.log').write_text(s+'Cards assayed: 2000\n')
  elif mode=='bad_metric':(p/'gate.log').write_text(s.replace('Print mismatch: 0','Print mismatch: 1'))
  elif mode=='nonzero':(p/'build-exit.txt').write_text('1')
  elif mode=='missing_corpus':(p/'oracle-bulk-corpus.jsonl.gz').unlink()
  elif mode=='wrong_hash':(p/'oracle-bulk-sha256.txt').write_text('wrong')
  elif mode=='wrong_bytes':(p/'oracle-bulk-bytes.txt').write_text('1')
  elif mode=='missing_differential':(p/'differential.log').unlink()
  elif mode=='changed_differential':
   with (p/'differential.log').open('a') as f:f.write('\nchanged original block\n')
  r=subprocess.run(['python3',str(H/'check_assay.py')],env=dict(os.environ,RECORD=str(H),EVIDENCE=td),capture_output=True)
  assert (r.returncode==0)==(mode=='valid'),mode
cases=['valid','floor','duplicate_metric','bad_metric','nonzero','missing_corpus','wrong_hash','wrong_bytes','missing_differential','changed_differential']
for case in cases:check(case)
print(json.dumps(dict(synthetic_only=True,valid=1,rejected=cases[1:])))
