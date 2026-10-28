"""Strict raw-report compatibility gates; exact identities and multiplicities."""
from pathlib import Path
from collections import Counter
import json,gzip,base64,hashlib,re,xml.etree.ElementTree as ET
HERE=Path(__file__).resolve().parent
P=json.loads((HERE/'PROTOCOL.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
def inventory(root):
 from derive_successor_inventory import derive
 return derive(root)
def junit(folder,expected):
 folder=Path(folder);assert int((folder/'exit.txt').read_text())==0
 log=(folder/'output.log').read_text();assert 'BUILD SUCCESSFUL' in log
 assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
 names=Counter();skips=Counter();files=list((folder/'junit').rglob('TEST-*.xml'));assert files
 for f in files:
  rel=str(f.relative_to(folder/'junit'));assert '/build/test-results/test/' in rel
  module=rel.split('/build/test-results/test/')[0];rt=ET.fromstring(f.read_bytes());rows=rt.findall('.//testcase');assert int(rt.get('tests'))==len(rows)
  for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:assert int(rt.get(attr))==sum(t.find(tag) is not None for t in rows)
  for t in rows:
   assert t.find('failure') is None and t.find('error') is None
   key='\t'.join([module,t.get('classname'),t.get('name')]);names[key]+=1
   if t.find('skipped') is not None:skips[key]+=1
 assert names==Counter(expected['identities']),dict(missing=Counter(expected['identities'])-names,extra=names-Counter(expected['identities']))
 assert skips==Counter(expected['skips'])
 return dict(rows=sum(names.values()),passed=sum(names.values())-sum(skips.values()),skips=sum(skips.values()),identities=dict(names),skipped_identities=dict(skips))
def client(folder,contract):
 folder=Path(folder)
 for n in ['npm-ci','typecheck','test']:assert int((folder/(n+'-exit.txt')).read_text())==0
 r=json.loads((folder/'vitest.json').read_text());assert r['success'] and r['numTotalTests']==r['numPassedTests']==64 and r['numFailedTests']==r['numPendingTests']==0
 got=Counter()
 for suite in r['testResults']:
  path='web-client/'+suite['name'].split('/web-client/',1)[1];assert suite['status']=='passed'
  for a in suite['assertionResults']:
   assert a['status']=='passed' and not a.get('failureMessages');got[(path,a['title'])]+=1
 expected=Counter((path,n) for path,names in contract['client_cases'].items() for n in names)
 assert got==expected
 return dict(passed=64,skips=0)
