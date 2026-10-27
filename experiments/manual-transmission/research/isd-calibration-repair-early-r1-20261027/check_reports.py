import json,xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path
from reports import P,junit,stage
import tempfile

def make(variant='normal',full=False):
    cases=P['full_module_cases'] if full else P['targeted_cases']
    groups=defaultdict(list)
    for cls,n in cases:groups[cls].append(n)
    want={(x['test_class'],x['name']):x['frame'] for x in P['mutations'].get(variant,{}).get('witnesses',[])}
    skip=set(map(tuple,P['full_module_baseline_skips'])) if full else set()
    raw={}
    for cls,names in groups.items():
        root=ET.Element('testsuite',tests=str(len(names)),errors='0',failures=str(sum((cls,n) in want for n in names)),skipped=str(sum((cls,n) in skip for n in names)))
        for n in names:
            row=ET.SubElement(root,'testcase',classname=cls,name=n)
            if (cls,n) in want:
                failure=ET.SubElement(row,'failure',type=P['assertion_type']);failure.text=f'at {cls}({cls.rsplit(".",1)[1]}.kt:{want[(cls,n)]})'
            if (cls,n) in skip:ET.SubElement(row,'skipped')
        raw[cls+'.xml']=ET.tostring(root)
    return raw
for name in ['normal',*P['mutations'],'restored']:junit(make(name),name)
r=junit(make(full=True),full=True);assert (r['tests'],r['passed'],r['skips'])==(196,193,3)
rejections=[]
for mode in ['missing-report','duplicate-identity','new-skip','unrelated-failure','wrong-assertion-type','wrong-frame','survivor','compiler-log']:
    variant='morbid-once';raw=make(variant);key=P['test_class']+'.xml';root=ET.fromstring(raw[key])
    if mode=='missing-report':del raw[key]
    elif mode=='duplicate-identity':root[1].set('name',root[0].get('name'))
    elif mode=='new-skip':ET.SubElement(root[-1],'skipped');root.set('skipped','1')
    elif mode=='unrelated-failure':ET.SubElement(root[-1],'failure',type=P['assertion_type']);root.set('failures',str(int(root.get('failures'))+1))
    elif mode=='wrong-assertion-type':root[0].find('failure').set('type','java.lang.IllegalStateException')
    elif mode=='wrong-frame':root[0].find('failure').text='at IsdGeneratorCalibrationRepairTest.kt:9999'
    elif mode=='survivor':root[0].remove(root[0].find('failure'));root.set('failures',str(int(root.get('failures'))-1))
    if mode!='missing-report':raw[key]=ET.tostring(root)
    try:
        if mode=='compiler-log':
            with tempfile.TemporaryDirectory() as t:
                p=Path(t)/'compile-normal';p.mkdir();(p/'exit.txt').write_text('0');(p/'output.log').write_text('e: Unresolved reference\nBUILD SUCCESSFUL\n> Task :mtgish-tooling:compileTestKotlin\n');stage(p)
        else:junit(raw,variant)
    except AssertionError:rejections.append(mode)
    else:raise AssertionError(mode)
result=dict(state='SYNTHETIC_RECEIVER_CHECKS_ONLY_NOT_EXECUTABLE_KOTLIN_EVIDENCE',accepted_synthetic_variants=7,full_module=196,full_module_passes=193,historical_skips=3,rejected=rejections)
Path(__file__).with_name('SYNTHETIC_REPORT_CHECKS.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
