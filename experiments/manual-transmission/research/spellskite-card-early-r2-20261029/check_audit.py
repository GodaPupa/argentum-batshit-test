"""Executable receiver controls; never substitute for production negative witnesses."""
from pathlib import Path
import tempfile,json,xml.etree.ElementTree as ET
from audit import P,reports,compile_gate,snapshot_insertion
results=[]
def check(name,fn,reject=False):
 try:fn()
 except AssertionError:
  assert reject,name
 else:assert not reject,name
 results.append({'name':name,'passed':True,'expected_rejection':reject})
with tempfile.TemporaryDirectory() as td:
 f=Path(td);j=f/'junit/mtg-sets/build/test-results/test';j.mkdir(parents=True)
 def write(negative=False,change=None):
  m=P['mutants']['blue-only'];fail=m['witnesses'] if negative else {}
  rt=ET.Element('testsuite',tests='8',failures=str(len(fail)),errors='0',skipped='0')
  for key in P['registration_cases']:
   module,cls,name=key.split('\t');t=ET.SubElement(rt,'testcase',classname=cls,name=name)
   if key in fail:ET.SubElement(t,'failure',type='org.opentest4j.AssertionFailedError').text='at SpellskiteRegistrationTest.kt:'+str(fail[key])
  if change:change(rt)
  (j/'TEST-reg.xml').write_bytes(ET.tostring(rt));(f/'exit.txt').write_text('1' if negative else '0')
  (f/'output.log').write_text('> Task :mtg-sets:test FAILED\nBUILD FAILED' if negative else 'BUILD SUCCESSFUL')
 def gate(negative=False):return reports(f,P['registration_cases'],P['mutants']['blue-only']['witnesses'] if negative else None)
 write();check('exact normal',gate)
 write(True);check('exact executable assertion pair',lambda:gate(True))
 controls=[('wrong identity',lambda rt:rt[0].set('name','other')),('duplicate identity',lambda rt:rt[1].set('name',rt[0].get('name'))),('unexpected skip',lambda rt:ET.SubElement(rt[7],'skipped')),('error instead of assertion',lambda rt:ET.SubElement(rt[7],'error')),('missing case',lambda rt:rt.remove(rt[7])),('wrong failure type',lambda rt:rt.find('.//failure').set('type','CompilerError')),('wrong assertion frame',lambda rt:setattr(rt.find('.//failure'),'text','at Wrong.kt:1')),('extra failure',lambda rt:ET.SubElement(rt[7],'failure',type='AssertionError'))]
 for name,fn in controls:
  write(True,fn);check(name,lambda:gate(True),True)
 write(True);(f/'output.log').write_text('e: Unresolved reference\n> Task :mtg-sets:test FAILED\nBUILD FAILED');check('compiler contamination',lambda:gate(True),True)
 write(True);(f/'exit.txt').write_text('0');check('negative success exit',lambda:gate(True),True)
 write(True);(f/'output.log').write_text('> Task :other:compileKotlin FAILED\nBUILD FAILED');check('unrelated failed task',lambda:gate(True),True)
 for x in j.glob('*.xml'):x.unlink()
 (f/'exit.txt').write_text('0');(f/'output.log').write_text('BUILD SUCCESSFUL');check('compile success',lambda:compile_gate(f))
 (f/'exit.txt').write_text('1');check('compile failure',lambda:compile_gate(f),True)
 (f/'exit.txt').write_text('0');(f/'output.log').write_text('e: broken\nBUILD SUCCESSFUL');check('compile diagnostic',lambda:compile_gate(f),True)
 original=b'// A\n{}\n\n// Z\n{}\n';candidate=b'// A\n{}\n\n// Spellskite\n{"name":"Spellskite"}\n\n// Z\n{}\n'
 check('one insertion exact old bytes',lambda:snapshot_insertion(original,candidate))
 for name,b in [('unrelated content edit',candidate.replace(b'// A\n{}',b'// A\n{"changed":true}')),('old whitespace edit',candidate.replace(b'// A\n{}',b'// A\n{ }')),('missing original entry',candidate.replace(b'// A\n{}\n\n',b'')),('second addition',candidate.replace(b'// Z',b'// T\n{}\n\n// Z')),('duplicate Spellskite',candidate.replace(b'// Z',b'// Spellskite\n{}\n\n// Z')),('no insertion',original),('wrong name',candidate.replace(b'// Spellskite',b'// Other'))]:
  check(name,lambda b=b:snapshot_insertion(original,b),True)
 check('last insertion',lambda:snapshot_insertion(b'// A\n{}\n',b'// A\n{}\n\n// Spellskite\n{}\n'))
Path(__file__).with_name('SYNTHETIC_CHECKS.json').write_text(json.dumps({'kind':'receiver robustness not engine evidence','checks':results,'count':len(results)},indent=2)+'\n')
print(json.dumps({'checks':len(results),'passed':True}))
