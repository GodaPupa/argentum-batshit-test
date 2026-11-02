"""Fail closed on original source, compilation, exact assertions and snapshot insertion."""
from pathlib import Path
from collections import Counter
import hashlib,json,re,xml.etree.ElementTree as ET
HERE=Path(__file__).resolve().parent
P=json.loads((HERE/'PROTOCOL.json').read_text())
sha=lambda b:hashlib.sha256(b).hexdigest()
def compile_gate(folder):
 log=(folder/'output.log').read_text()
 assert (folder/'exit.txt').read_text().strip()=='0' and 'BUILD SUCCESSFUL' in log
 assert not re.search(r'^e: |Compilation error|Unresolved reference|^> Task .* FAILED$',log,re.M)
 assert not list((folder/'junit').rglob('TEST-*.xml'))
 return {'compiled':True}
def reports(folder,expected,failures=None):
 failures=failures or {};log=(folder/'output.log').read_text();rc=int((folder/'exit.txt').read_text())
 assert (rc!=0)==bool(failures)
 assert ('BUILD FAILED' if failures else 'BUILD SUCCESSFUL') in log
 assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
 wanted=sorted(set(':'+k.split('\t')[0].replace('/',':')+':test' for k in failures))
 assert sorted(re.findall(r'^> Task (\S+) FAILED$',log,re.M))==wanted
 got=Counter();bad={};files=list((folder/'junit').rglob('TEST-*.xml'));assert files
 for f in files:
  rt=ET.fromstring(f.read_bytes());rows=rt.findall('.//testcase');assert int(rt.get('tests'))==len(rows)
  for attr,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
   assert int(rt.get(attr,'0'))==sum(t.find(tag)is not None for t in rows)
  module=str(f.relative_to(folder/'junit')).split('/build/test-results/test/')[0]
  for t in rows:
   key='\t'.join([module,t.get('classname'),t.get('name')]);got[key]+=1
   assert t.find('error') is None and t.find('skipped') is None
   failure=t.find('failure')
   if failure is not None:
    assert key in failures,key
    assert 'Assertion' in failure.get('type',''),failure.attrib
    w=failures[key];frame=w['file']+':'+str(w['line']);assert frame in (failure.text or ''),frame
    bad[key]={'type':failure.get('type'),'frame':frame,'message':failure.get('message')}
 assert got==Counter(expected),{'missing':dict(Counter(expected)-got),'extra':dict(got-Counter(expected))}
 assert set(bad)==set(failures)
 return {'rows':sum(got.values()),'passes':sum(got.values())-len(bad),'failures':bad,'skips':0,'identities':dict(got)}
def snapshot_insertion(original,candidate):
 assert original.count(b'// Fblthp, the Lost\n')==0 and candidate.count(b'// Fblthp, the Lost\n')==1
 headers=list(re.finditer(rb'(?m)^// ([^\n]+)\n',candidate));names=[m[1].decode() for m in headers]
 assert names==sorted(names) and len(names)==len(set(names))
 at=names.index('Fblthp, the Lost');assert at+1<len(headers)
 start=headers[at].start();end=headers[at+1].start()
 assert candidate[:start]+candidate[end:]==original,'Original WAR bytes changed'
 return {'original_sha256':sha(original),'candidate_sha256':sha(candidate),'entry_sha256':sha(candidate[start:end]),'original_entries':len(headers)-1,'added_entries':1}
