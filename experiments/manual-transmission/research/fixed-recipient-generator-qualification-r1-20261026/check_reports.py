"""Synthetic rejection checks validate the auditor, never count as Kotlin runtime evidence."""
import copy,json,tempfile
from pathlib import Path
import xml.etree.ElementTree as ET
from reports import P,junit,stage
results=[]
def raw(variant='normal',full=False):
    witnesses=P['mutations'].get(variant,{}).get('witnesses',{});groups={}
    cases=P['full_module_cases'] if full else [[P['test_class'],n] for n in P['cases']]
    for cls,name in cases:groups.setdefault(cls,[]).append(name)
    out={}
    for cls,names in groups.items():
        fails=sum(n in witnesses and cls==P['test_class'] for n in names)
        r=ET.Element('testsuite',tests=str(len(names)),failures=str(fails),errors='0',skipped='0')
        for n in names:
            t=ET.SubElement(r,'testcase',classname=cls,name=n)
            if n in witnesses and cls==P['test_class']:
                f=ET.SubElement(t,'failure',type=P['assertion_type']);f.text='at witness(FixedRecipientRetargetEmitterTest.kt:'+str(witnesses[n])+')'
        out['TEST-'+cls+'.xml']=ET.tostring(r)
    return out
def reject(label,fn):
    try:fn()
    except (AssertionError,ValueError,KeyError,FileNotFoundError):results.append(label);return
    raise AssertionError('auditor accepted '+label)
for variant in ['normal','wrong-recipient','unbound-stack']:
    junit(raw(variant),variant)
junit(raw(full=True),full=True)
reject('missing reports',lambda:junit({}))
r=raw();k=next(iter(r));root=ET.fromstring(r[k]);root.remove(root.findall('testcase')[-1]);r[k]=ET.tostring(root)
reject('missing case',lambda:junit(r))
r=raw();k=next(iter(r));root=ET.fromstring(r[k]);root.findall('testcase')[1].set('name',P['cases'][0]);r[k]=ET.tostring(root)
reject('duplicate identity',lambda:junit(r))
r=raw('wrong-recipient');k=next(iter(r));r[k]=r[k].replace(b'.kt:19',b'.kt:18')
reject('wrong assertion frame',lambda:junit(r,'wrong-recipient'))
r=raw('wrong-recipient');k=next(iter(r));r[k]=r[k].replace(P['assertion_type'].encode(),b'java.lang.IllegalStateException')
reject('unrelated exception',lambda:junit(r,'wrong-recipient'))
reject('surviving mutant',lambda:junit(raw(),'unbound-stack'))
r=raw();k=next(iter(r));root=ET.fromstring(r[k]);ET.SubElement(root.find('testcase'),'skipped');root.set('skipped','1');r[k]=ET.tostring(root)
reject('skip',lambda:junit(r))
r=raw(full=True);r.pop(next(k for k in r if 'EmitterGoldenTest' in k))
reject('missing golden suite',lambda:junit(r,full=True))
with tempfile.TemporaryDirectory() as tmp:
    d=Path(tmp)/'compile-normal';d.mkdir();(d/'exit.txt').write_text('0');(d/'output.log').write_text('BUILD SUCCESSFUL\n> Task :mtgish-tooling:compileTestKotlin\n');stage(d)
    (d/'output.log').write_text('BUILD SUCCESSFUL\ne: unresolved\n> Task :mtgish-tooling:compileTestKotlin\n')
    reject('compiler error despite zero exit',lambda:stage(d))
    (d/'exit.txt').write_text('1');reject('failed compilation',lambda:stage(d))
print(json.dumps(dict(state='SYNTHETIC_AUDITOR_CHECKS_ONLY',accepted_shapes=4,rejected=results),indent=2))
