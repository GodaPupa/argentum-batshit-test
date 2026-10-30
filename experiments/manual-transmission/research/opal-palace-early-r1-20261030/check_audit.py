"""Synthetic rejection tests of receiving logic, not Kotlin behavioral evidence."""
from pathlib import Path
import json,copy,xml.etree.ElementTree as ET
from audit import verify
C=json.loads((Path(__file__).parent/'EARLY_CONTRACT.json').read_text())
def fixture(form):
    w=C['witnesses'].get(form,{})
    selected=list(w)+C['controls'] if w else list(C['cases'])
    raw={'exit.txt':b'1' if w else b'0','output.log':b'> Task :mtg-sets:2008-2016:tests:test FAILED\nBUILD FAILED\n' if w else b'BUILD SUCCESSFUL\n'}
    classes={C['cases'][key]['classname'] for key in selected}
    for cls in classes:
        keys=[k for k in selected if C['cases'][k]['classname']==cls]
        root=ET.Element('testsuite',name=cls,tests=str(len(keys)),failures=str(sum(k in w for k in keys)),errors='0',skipped='0')
        for key in keys:
            c=C['cases'][key];row=ET.SubElement(root,'testcase',classname=cls,name=c['name'])
            if key in w:
                f=ET.SubElement(row,'failure',type='org.opentest4j.AssertionFailedError',message=w[key]['message'])
                f.text=w[key]['message']+'\n at witness('+c['file']+':'+str(w[key]['line'])+')'
        module=C['cases'][keys[0]]['module'].lstrip(':').replace(':','/')
        raw['junit/'+module+'/build/test-results/test/TEST-'+cls+'.xml']=ET.tostring(root)
    return raw
for form in ['normal','restored']+list(C['witnesses']):verify(fixture(form),C,form)
rejections=[]
def reject(name,change,form='OP01-drop'):
    raw=fixture(form);change(raw)
    try:verify(raw,C,form)
    except (AssertionError,KeyError,ValueError):rejections.append(name)
    else:raise AssertionError(name)
def edit(raw,change):
    key=next(k for k,v in raw.items() if k.endswith('.xml') and b'<failure' in v)
    root=ET.fromstring(raw[key]);change(root);raw[key]=ET.tostring(root)
reject('compiler error',lambda r:r.update({'output.log':r['output.log']+b'e: unresolved\n'}))
reject('zero exit',lambda r:r.update({'exit.txt':b'0'}))
reject('missing report',lambda r:r.pop(next(k for k in r if k.endswith('.xml'))))
reject('duplicate report',lambda r:r.update({'junit/duplicate.xml':next(v for k,v in r.items() if k.endswith('.xml'))}))
reject('wrong assertion type',lambda r:edit(r,lambda x:x.find('testcase/failure').set('type','java.lang.IllegalStateException')))
reject('wrong expected actual',lambda r:edit(r,lambda x:x.find('testcase/failure').set('message','unrelated')))
reject('wrong assertion line',lambda r:edit(r,lambda x:setattr(x.find('testcase/failure'),'text','expected:<1> but was:<0>\n at witness(CommanderManaRiderWitnessTest.kt:1)')))
reject('missing failure',lambda r:edit(r,lambda x:x.find('testcase').remove(x.find('testcase/failure'))))
reject('duplicate identity',lambda r:edit(r,lambda x:x.append(copy.deepcopy(x.find('testcase')))))
reject('skip',lambda r:edit(r,lambda x:ET.SubElement(x.find('testcase'),'skipped')))
reject('extra error',lambda r:edit(r,lambda x:ET.SubElement(x.find('testcase'),'error')))
reject('wrong case identity',lambda r:edit(r,lambda x:x.find('testcase').set('name','wrong')))
reject('unrelated failed task',lambda r:r.update({'output.log':r['output.log']+b'> Task :rules-engine:test FAILED\n'}))
print(json.dumps(dict(state='SYNTHETIC_AUDITOR_CHECK_ONLY',valid_forms=19,rejected=rejections,kotlin_evidence=False)))
