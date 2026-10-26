import json,xml.etree.ElementTree as E
from collections import defaultdict
from reports import P,junit
from pathlib import Path
rows=defaultdict(list)
for cls,n in P['full_module_cases']:rows[cls].append(n)
assert len(rows)==22
skips=set(map(tuple,P['full_module_baseline_skips']))
def make():
 raw={}
 for cls,names in rows.items():
  root=E.Element('testsuite',tests=str(len(names)),failures='0',errors='0',skipped=str(sum((cls,n) in skips for n in names)))
  for n in names:
   r=E.SubElement(root,'testcase',classname=cls,name=n)
   if (cls,n) in skips:E.SubElement(r,'skipped')
  raw[cls+'.xml']=E.tostring(root)
 return raw
r=junit(make(),full=True);assert (r['tests'],r['passed'],r['skips'])==(171,168,3)
rejected=[]
for mode in ['missing-report','duplicate-identity','new-skip','unrelated-failure']:
 raw=make();key=P['test_class']+'.xml';root=E.fromstring(raw[key])
 if mode=='missing-report':del raw[key]
 elif mode=='duplicate-identity':root[1].set('name',root[0].get('name'));raw[key]=E.tostring(root)
 elif mode=='new-skip':E.SubElement(root[0],'skipped');root.set('skipped','1');raw[key]=E.tostring(root)
 else:E.SubElement(root[0],'failure',type='org.opentest4j.AssertionFailedError');root.set('failures','1');raw[key]=E.tostring(root)
 try:junit(raw,full=True)
 except AssertionError:rejected.append(mode)
 else:raise AssertionError(mode)
result=dict(state='SYNTHETIC_FULL_REPORT_CHECKS_ONLY',cases=171,passes=168,exact_historical_skips=3,rejected=rejected)
Path(__file__).with_name('SYNTHETIC_FULL_REPORT_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
