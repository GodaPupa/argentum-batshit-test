"""Synthetic receiver rejection controls; never substitute for executable engine evidence."""
from pathlib import Path
import json,tempfile,xml.etree.ElementTree as ET
from audit import P,reports,compile_gate

def main():
 checked=[]
 with tempfile.TemporaryDirectory() as td:
  root=Path(td)
  def setup(failure=False):
   folder=root/str(len(list(root.iterdir())));folder.mkdir()
   witnesses=P['mutants']['missing-cast']['witnesses'] if failure else {}
   tasks=sorted(set(':'+k.split('\t')[0].replace('/',':')+':test' for k in witnesses))
   (folder/'output.log').write_text('\n'.join('> Task '+t+' FAILED' for t in tasks)+'\nBUILD '+('FAILED' if failure else 'SUCCESSFUL'))
   (folder/'exit.txt').write_text('1' if failure else '0')
   for key in P['normal_cases']:
    module,cls,name=key.split('\t');p=folder/'junit'/module/'build/test-results/test'/('TEST-'+cls+'.xml');p.parent.mkdir(parents=True,exist_ok=True)
    suite=ET.parse(p).getroot() if p.exists() else ET.Element('testsuite')
    case=ET.SubElement(suite,'testcase',{'classname':cls,'name':name})
    if key in witnesses:
     w=witnesses[key];ET.SubElement(case,'failure',{'type':'java.lang.AssertionError','message':'expected mismatch'}).text=f"at {cls}.test({w['file']}:{w['line']})"
    suite.set('tests',str(len(suite.findall('testcase'))));suite.set('failures',str(len(suite.findall('testcase/failure'))));suite.set('errors','0');suite.set('skipped','0');ET.ElementTree(suite).write(p)
   return folder,witnesses
  for failure in [False,True]:
   f,w=setup(failure);reports(f,P['normal_cases'],w)
  def reject(name,change,failure=False):
   f,w=setup(failure);change(f)
   try:reports(f,P['normal_cases'],w)
   except (AssertionError,ValueError):checked.append(name)
   else:raise AssertionError('Accepted tampered evidence '+name)
  def xmlchange(f,fn):
   p=next((f/'junit').rglob('TEST-*.xml'));r=ET.parse(p);fn(r.getroot());r.write(p)
  reject('missing report',lambda f:next((f/'junit').rglob('TEST-*.xml')).unlink())
  reject('duplicate report multiplicity',lambda f:(f/'junit/copy').mkdir() or (f/'junit/copy/TEST-duplicate.xml').write_bytes(next((f/'junit').rglob('TEST-*.xml')).read_bytes()))
  reject('wrong count',lambda f:xmlchange(f,lambda r:r.set('tests','0')))
  reject('skip',lambda f:xmlchange(f,lambda r:(ET.SubElement(r[0],'skipped'),r.set('skipped','1'))))
  reject('runtime error',lambda f:xmlchange(f,lambda r:(ET.SubElement(r[0],'error'),r.set('errors','1'))))
  reject('compiler contamination',lambda f:(f/'output.log').write_text((f/'output.log').read_text()+'\ne: Unresolved reference'),True)
  def failurechange(f,fn):
   for p in (f/'junit').rglob('TEST-*.xml'):
    t=ET.parse(p);x=t.find('.//failure')
    if x is not None:fn(x);t.write(p);return
   raise AssertionError('missing synthetic failure')
  reject('non assertion failure',lambda f:failurechange(f,lambda x:x.set('type','java.lang.IllegalStateException')),True)
  reject('wrong assertion location',lambda f:failurechange(f,lambda x:setattr(x,'text','Wrong.kt:1')),True)
  reject('wrong test identity',lambda f:xmlchange(f,lambda r:r[0].set('name','unrelated')))
  reject('mutant zero exit',lambda f:(f/'exit.txt').write_text('0'),True)
  f=root/'compile';f.mkdir();(f/'exit.txt').write_text('0');(f/'output.log').write_text('BUILD SUCCESSFUL');compile_gate(f)
  (f/'output.log').write_text('BUILD SUCCESSFUL\ne: compiler failure')
  try:compile_gate(f)
  except AssertionError:checked.append('compiler log despite zero exit')
  else:raise AssertionError('Accepted compiler contamination')
 return {'synthetic_only':True,'valid_report_controls':2,'valid_compile_control':1,'rejected':checked}
if __name__=='__main__':print(json.dumps(main(),indent=2))
