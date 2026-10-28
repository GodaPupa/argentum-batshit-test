from pathlib import Path
import copy,json,xml.etree.ElementTree as ET
from reports import P,reports

def fixture(variant):
    classes={P['control_class']:P['classes'][P['control_class']]} if variant in P['mutants'] else P['classes']
    witnesses=P['mutants'][variant]['witnesses'] if variant in P['mutants'] else {}
    raw={}
    for cls,names in classes.items():
        root=ET.Element('testsuite',tests=str(len(names)),failures=str(len(witnesses)),errors='0',skipped='0')
        for name in names:
            case=ET.SubElement(root,'testcase',name=name,classname=cls)
            if name in witnesses:
                w=witnesses[name];f=ET.SubElement(case,'failure',type='org.opentest4j.AssertionFailedError',message=' '.join(w['contains']))
                f.text='at witness(ActivatedPhyrexianPaymentControlTest.kt:'+str(w['line'])+')'
        raw[cls]=ET.tostring(root)
    return raw
for variant in ['normal',*P['mutants'],'restored']:reports(fixture(variant),variant)
variant='ignore-choice';valid=fixture(variant);key=next(iter(valid))
checks={}
def reject(name,change):
    raw=copy.deepcopy(valid);root=ET.fromstring(raw[key]);change(root);raw[key]=ET.tostring(root)
    try:reports(raw,variant)
    except Exception:checks[name]=True
    else:raise AssertionError(name)
reject('wrong-type',lambda r:r.find('testcase/failure').set('type','java.lang.IllegalStateException'))
reject('wrong-frame',lambda r:setattr(r.find('testcase/failure'),'text','at witness(ActivatedPhyrexianPaymentControlTest.kt:1)'))
reject('wrong-message',lambda r:r.find('testcase/failure').set('message','unrelated'))
reject('missing-case',lambda r:r.remove(r.find('testcase')))
reject('duplicate-case',lambda r:r.append(copy.deepcopy(r.find('testcase'))))
reject('wrong-identity',lambda r:r.find('testcase').set('name','other'))
reject('wrong-class',lambda r:r.find('testcase').set('classname','other'))
reject('skip',lambda r:ET.SubElement(r.find('testcase'),'skipped'))
reject('error',lambda r:ET.SubElement(r.find('testcase'),'error'))
reject('missing-witness',lambda r:r.find('testcase').remove(r.find('testcase/failure')))
reject('extra-failure',lambda r:ET.SubElement(next(c for c in r.findall('testcase') if c.find('failure') is None),'failure',type='org.opentest4j.AssertionFailedError'))
reject('wrong-summary',lambda r:r.set('tests','0'))
for label,raw in [('missing-report',{}),('extra-report',{**valid,'extra':valid[key]})]:
    try:reports(raw,variant)
    except Exception:checks[label]=True
    else:raise AssertionError(label)
result=dict(accepted_variants=11,rejected_invalid_reports=len(checks),checks=checks)
expected=Path(__file__).with_name('SYNTHETIC_CHECKS.json')
if expected.exists():assert json.loads(expected.read_text())==result
else:expected.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
