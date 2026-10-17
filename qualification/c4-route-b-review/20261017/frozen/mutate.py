"""Offline changed-source mutation checks; isolated copies; no host invocation."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json
import shutil
import subprocess
import sys
import tempfile
ROOT=Path(__file__).resolve().parent
MUTANTS=[
 ('manager','owner drift',"require((await self.daemon('GetNameOwner',[MANAGER]))[0]==self.owner,'PID1 owner drift')","pass"),
 ('manager','bus peer',"require(self.wire.peer[1:]==(0,0),'bus credentials')","pass"),
 ('manager','invocation',"u['InvocationID']==list(bytes.fromhex(binding.invocation))","True"),
 ('manager','delegation',"s.get('Delegate') is True","True"),
 ('observer','event identity',"require(r['binding']==self.binding,'event binding drift')","pass"),
 ('observer','loss counter',"v==0 for v in losses.values()","v>=0 for v in losses.values()"),
 ('watchdog','cleanup timeout',"require(self.clock()<=self.deadline,'cleanup absolute deadline')","pass"),
 ('watchdog','inactive gate',"require(manager_inactive is True,'exact invocation not inactive')","pass"),
]
def run(item):
 module,name,old,new=item
 with tempfile.TemporaryDirectory() as tmp:
  p=Path(tmp)
  for directory in ['src','tests','reference']:shutil.copytree(ROOT/directory,p/directory)
  f=p/'src'/(module+'.py');text=f.read_text();assert text.count(old)==1,(name,text.count(old));f.write_text(text.replace(old,new))
  r=subprocess.run([sys.executable,'-B','-m','unittest','discover','-s',str(p/'tests'),'-v'],capture_output=True,text=True,timeout=30)
  # Require behavioral assertion failure, not syntax/import failure.
  detected=r.returncode!=0 and ('FAIL:' in r.stderr or 'ERROR:' in r.stderr) and 'SyntaxError' not in r.stderr and 'ImportError' not in r.stderr
  return {'mutation':name,'detected':detected,'exit':r.returncode,'log':r.stdout+r.stderr}
if __name__=='__main__':
 with ThreadPoolExecutor(max_workers=4) as pool:results=list(pool.map(run,MUTANTS))
 print(json.dumps(results,indent=2));sys.exit(0 if all(x['detected'] for x in results) else 1)
