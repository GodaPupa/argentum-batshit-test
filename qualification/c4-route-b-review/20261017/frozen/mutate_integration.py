"""Additional changed-source weakening tests; no live adapters."""
import json,sys
from concurrent.futures import ThreadPoolExecutor
from mutate import run
MUTANTS=[
('external_service','escrow replay',"and r['seq']==self.rx",""),
('external_service','escrow binding',"and r['binding']==self.binding",""),
('integration','image hash',"and h.hexdigest()==self.expected_sha",""),
('integration','scope one attempt',"require(not self.used,'scope attempt consumed');self.used=True","self.used=True"),
('observer_lifecycle','hotplug',"require(frozenset(self.provider.online_cpus())==self.cpus,'CPU hotplug: coverage invalid')","pass"),
('observer_lifecycle','producer loss',"all(type(v) is int and v==0 for v in loss.values())","all(type(v) is int and v>=0 for v in loss.values())"),
('observer_lifecycle','drain gate',"require(drain_check() is True,'drain not verified')","pass"),
('watchdog','post-kill journal failure',"except Exception as exc:journal_error=exc\n            self.evidence_error=journal_error","except Exception:pass\n            self.evidence_error=journal_error"),
]
if __name__=='__main__':
 with ThreadPoolExecutor(max_workers=4) as pool:r=list(pool.map(run,MUTANTS))
 print(json.dumps(r,indent=2));sys.exit(0 if all(x['detected'] for x in r) else 1)
