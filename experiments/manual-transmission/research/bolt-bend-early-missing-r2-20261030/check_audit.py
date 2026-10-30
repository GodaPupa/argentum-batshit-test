from pathlib import Path
import tempfile,copy,json,xml.etree.ElementTree as ET
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from audit import P,verify
count=0
with tempfile.TemporaryDirectory() as tmp:
    d=Path(tmp)
    def fixture(form):
        for p in d.rglob('TEST-*.xml'):p.unlink()
        w=P['witnesses'].get(form,{})
        (d/'exit.txt').write_text('1' if w else '0')
        (d/'output.log').write_text('> Task :rules-engine:test FAILED\nBUILD FAILED\n' if w else 'BUILD SUCCESSFUL\n')
        paths=[]
        for cls in sorted({c['class'] for c in P['cases'].values()}):
            cs=[(id,c) for id,c in P['cases'].items() if c['class']==cls]
            r=ET.Element('testsuite',name=cls,tests=str(len(cs)),failures=str(sum(id in w for id,c in cs)),errors='0',skipped='0')
            for id,c in cs:
                t=ET.SubElement(r,'testcase',name=c['name'],classname=cls)
                if id in w:
                    msg='org.opentest4j.AssertionFailedError: '+w[id]['message']
                    ET.SubElement(t,'failure',type='org.opentest4j.AssertionFailedError',message=msg).text=msg+'\n at fixture('+c['file']+':'+str(w[id]['line'])+')'
            p=d/'junit/rules-engine/build/test-results/test'/('TEST-'+cls+'.xml');p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(ET.tostring(r));paths.append(p)
        return paths
    for form in ['normal','restored',*P['witnesses']]:
        fixture(form);verify(d,form)
    for label in ['missing-report','missing-case','duplicate','wrong-class','skip','wrong-count','missing-witness','wrong-type','wrong-message','wrong-frame','unrelated-failure','compile-error']:
        paths=fixture('drop-power');p=paths[0];r=ET.fromstring(p.read_bytes());f=r.find('.//failure')
        if label=='missing-report':p.unlink()
        elif label=='compile-error':(d/'output.log').write_text('e: Unresolved reference\n> Task :rules-engine:compileTestKotlin FAILED\nBUILD FAILED')
        else:
            if label=='missing-case':r.remove(r[0])
            elif label=='duplicate':r.append(copy.deepcopy(r[0]))
            elif label=='wrong-class':r[0].set('classname','wrong')
            elif label=='skip':ET.SubElement(r[0],'skipped')
            elif label=='wrong-count':r.set('tests','0')
            elif label=='missing-witness':next(t for t in r if t.find('failure') is not None).remove(f)
            elif label=='wrong-type':f.set('type','CompilerException')
            elif label=='wrong-message':f.set('message','wrong')
            elif label=='wrong-frame':f.text=f.get('message')+'\n at fixture(BoltBendCostPredicateTest.kt:1)'
            elif label=='unrelated-failure':ET.SubElement(r[0],'failure')
            p.write_bytes(ET.tostring(r))
        try:verify(d,'drop-power')
        except (AssertionError,ValueError):count+=1
        else:raise AssertionError('accepted '+label)
print(json.dumps({'accepted_synthetic_forms':10,'rejected_corruptions':count,'engine_evidence':False}))
