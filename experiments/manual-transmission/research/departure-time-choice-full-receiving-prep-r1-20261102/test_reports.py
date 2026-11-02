import tempfile,copy,json
from pathlib import Path
from audit import junit
from report_audit import reports
expected={'module\tProbe\tcase':1}
xml='<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase classname="Probe" name="case"/></testsuite>'
with tempfile.TemporaryDirectory() as d:
 p=Path(d);f=p/'junit/module/build/test-results/test/TEST-Probe.xml';f.parent.mkdir(parents=True);(p/'exit.txt').write_text('0');(p/'output.log').write_text('BUILD SUCCESSFUL')
 f.write_text(xml);assert reports(p,expected)['passes']==1;assert junit(p,{'identities':expected,'skips':{}})['passed']==1
 rejected=[]
 for name,bad in [('missing',xml.replace('<testcase classname="Probe" name="case"/>','')),('duplicate',xml.replace('</testsuite>','<testcase classname="Probe" name="case"/></testsuite>').replace('tests="1"','tests="2"')),('renamed',xml.replace('name="case"','name="other"')),('skip',xml.replace('skipped="0"','skipped="1"').replace('/>','><skipped/></testcase>')),('failure',xml.replace('failures="0"','failures="1"').replace('/>','><failure/></testcase>'))]:
  f.write_text(bad)
  for checker in [lambda:reports(p,expected),lambda:junit(p,{'identities':expected,'skips':{}})]:
   try:checker()
   except AssertionError:rejected.append(name)
   else:raise AssertionError(name)
 print(json.dumps({'synthetic_positive_checks':2,'synthetic_rejections':rejected,'gameplay_evidence':False}))
