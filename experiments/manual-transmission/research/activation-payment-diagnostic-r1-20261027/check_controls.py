"""Synthetic auditor checks, never substituted for executable engine evidence."""
from reports import reports,P
from copy import deepcopy
import xml.etree.ElementTree as ET
def fixture(variant):
    expected=P['witnesses'][variant]
    root=ET.Element('testsuite',tests='4',errors='0',skipped='0',failures=str(len(expected)))
    for name in P['cases']:
        row=ET.SubElement(root,'testcase',name=name,classname=P['class'])
        if name in expected:
            w=expected[name];f=ET.SubElement(row,'failure',type='org.opentest4j.AssertionFailedError',message=' '.join(w['contains']))
            f.text='ActivatedPhyrexianPaymentDiagnosticTest.kt:'+str(w['line'])
    return root
def check(root,variant='baseline'):
    return reports({'TEST-diagnostic.xml':ET.tostring(root)},variant)
for variant in ['baseline','mutant']:check(fixture(variant),variant)
checks=[]
def reject(name,edit):
    root=fixture('baseline');edit(root)
    try:check(root)
    except (AssertionError,ValueError):checks.append(name)
    else:raise AssertionError('accepted invalid '+name)
reject('missing case',lambda r:r.remove(r.findall('testcase')[0]))
reject('duplicate identity',lambda r:r.findall('testcase')[0].set('name',r.findall('testcase')[1].get('name')))
reject('wrong class',lambda r:r.findall('testcase')[0].set('classname','other.Class'))
reject('unexpected skip',lambda r:ET.SubElement(r.findall('testcase')[0],'skipped'))
reject('error not assertion',lambda r:ET.SubElement(r.findall('testcase')[0],'error'))
reject('wrong assertion type',lambda r:r.find('.//failure').set('type','java.lang.IllegalStateException'))
reject('wrong frame',lambda r:setattr(r.find('.//failure'),'text','ActivatedPhyrexianPaymentDiagnosticTest.kt:1'))
reject('missing expected failure',lambda r:r.findall('testcase')[1].remove(r.find('.//failure')))
reject('unrelated assertion',lambda r:r.find('.//failure').set('message','unrelated assertion'))
reject('wrong summary',lambda r:r.set('failures','0'))
try:reports({},'baseline')
except AssertionError:checks.append('missing report')
else:raise AssertionError('empty reports accepted')
print('Synthetic receiver rejections: '+str(len(checks)))
