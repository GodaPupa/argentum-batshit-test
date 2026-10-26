"""Synthetic harness checks only; never behavioral evidence."""
import copy,json,importlib.util,xml.etree.ElementTree as E
from pathlib import Path
sp=importlib.util.spec_from_file_location('receiver',Path(__file__).with_name('RECEIVE.py'))
r=importlib.util.module_from_spec(sp);sp.loader.exec_module(r)

def make(stage):
 cases=r.P['negative_cases'] if stage=='mutation' else r.P['positive_cases']
 failures=r.P['negative_first_frames'] if stage=='mutation' else {}
 s=E.Element('testsuite',tests=str(len(cases)),failures=str(len(failures)),errors='0',skipped='0')
 fs=[]
 for ident in cases:
  cls,name=ident.split('::',1);row=E.SubElement(s,'testcase',classname=cls,name=name)
  if ident in failures:
   typ='org.opentest4j.AssertionFailedError';stack='at '+cls+'(FixedRecipientChoiceTest.kt:'+str(failures[ident])+')'
   E.SubElement(row,'failure',type=typ).text=stack;fs.append(dict(identity=ident,type=typ,stack=stack))
 rc=1 if failures else 0
 log='> Task :rules-engine:test FAILED\n' if failures else '> Task :rules-engine:test\nBUILD SUCCESSFUL\n'
 return {stage+'/junit/rules-engine/build/test-results/test/TEST-fixture.xml':E.tostring(s),stage+'/exit.txt':str(rc).encode(),stage+'/gradle.log':log.encode(),stage+'/SUMMARY.json':json.dumps(dict(cases=cases,failures=fs,errors=[],skips=[],exit=rc)).encode()}

checks=[]
for stage in ['targeted','mutation','restored']:
 r.verify_reports(make(stage),stage);checks.append(stage+' accepts exact synthetic identity/count/witness contract')

def reject(label,fn):
 raw=make('mutation');fn(raw)
 try:r.verify_reports(raw,'mutation')
 except (AssertionError,KeyError,ValueError):checks.append(label+' rejected');return
 raise AssertionError(label+' incorrectly accepted')

key='mutation/junit/rules-engine/build/test-results/test/TEST-fixture.xml'
reject('missing report',lambda x:x.pop(key))
reject('duplicate report',lambda x:x.update({'mutation/junit/rules-engine/build/test-results/test/TEST-duplicate.xml':x[key]}))
reject('compiler failure',lambda x:x.update({'mutation/gradle.log':b'e: Unresolved reference\n> Task :rules-engine:test FAILED\n'}))
reject('wrong assertion frame',lambda x:x.update({key:x[key].replace(b'.kt:63)',b'.kt:999)')}))
reject('wrong assertion type',lambda x:x.update({key:x[key].replace(b'org.opentest4j.AssertionFailedError',b'java.lang.IllegalStateException')}))
reject('zero failure exit',lambda x:x.update({'mutation/exit.txt':b'0'}))
def skip(x):
 s=E.fromstring(x[key]);row=s.findall('testcase')[0]
 for child in list(row):row.remove(child)
 E.SubElement(row,'skipped');s.set('skipped','1');s.set('failures',str(sum(y.find('failure') is not None for y in s)))
 x[key]=E.tostring(s)
reject('skipped case',skip)
Path(__file__).with_name('PREPARED.json').write_text(json.dumps({'state':'SYNTHETIC_RECEIVER_CHECKS_ONLY_NOT_BEHAVIORAL_EVIDENCE','checks':checks,'count':len(checks)},indent=2)+'\n')
print(json.dumps({'synthetic_checks':len(checks)}))
