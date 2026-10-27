"""Synthetic receiver checks only, never behavioral qualification."""
import copy,json,tempfile,zipfile
from pathlib import Path
from audit import P,browser_gate
def main():
    checks=[]
    with tempfile.TemporaryDirectory() as tmp:
        root=Path(tmp);stage='normal';folder=root/stage;folder.mkdir()
        attachments=[]
        for name in ['decision-wire.json','owner-observed.json','owner-dom.html','owner-screen.png','seat-0-trace','seat-1-trace']:
            file=folder/name
            if name.endswith('-trace'):
                with zipfile.ZipFile(file,'w') as z:z.writestr('test.trace','{}')
            else:file.write_bytes(b'\x89PNG\r\n\x1a\n' if name.endswith('.png') else b'{}')
            attachments.append({'name':name,'path':'/runner/manual-public-occurrence-ui-r2-evidence/normal/'+name})
        def report(titles,failed=None,marker=None):
            return {'errors':[],'suites':[{'specs':[{'title':t,'tests':[{'results':[{'status':'failed' if t==failed else 'passed','errors':[{'message':'expect: '+marker}] if t==failed else [],'attachments':attachments}]}]} for t in titles]}]}
        def run(obj,rc=0,name='normal'):
            target=root/name;target.mkdir(exist_ok=True);(target/'report.json').write_text(json.dumps(obj));(target/'exit.txt').write_text(str(rc));return browser_gate(root,name)
        good=report(P['browser_cases']);run(good);checks.append('accept exact normal five')
        mutations={
            'missing identity':lambda r:r['suites'][0]['specs'].pop(),
            'duplicate identity':lambda r:r['suites'][0]['specs'].append(copy.deepcopy(r['suites'][0]['specs'][0])),
            'retry':lambda r:r['suites'][0]['specs'][0]['tests'][0]['results'].append(copy.deepcopy(r['suites'][0]['specs'][0]['tests'][0]['results'][0])),
            'skip':lambda r:r['suites'][0]['specs'][0]['tests'][0]['results'][0].update(status='skipped'),
            'unexpected failure':lambda r:r['suites'][0]['specs'][0]['tests'][0]['results'][0].update(status='failed'),
            'missing evidence':lambda r:r['suites'][0]['specs'][0]['tests'][0]['results'][0]['attachments'].pop(),
            'runner error':lambda r:r['errors'].append({'message':'compile error'}),
        }
        for name,mutate in mutations.items():
            bad=copy.deepcopy(good);mutate(bad)
            try:run(bad)
            except Exception:checks.append('reject '+name)
            else:raise AssertionError('accepted '+name)
        for m in P['mutations']:
            goodm=report([m['witness'],P['control']],m['witness'],m['marker'])
            run(goodm,1,m['name']);checks.append('accept exact witness '+m['name'])
            for label,case,rc in [('survivor',report([m['witness'],P['control']]),0),('wrong assertion',report([m['witness'],P['control']],m['witness'],'unrelated'),1)]:
                try:run(case,rc,m['name'])
                except Exception:checks.append('reject '+m['name']+' '+label)
                else:raise AssertionError('accepted '+label)
    output={'scope':'SYNTHETIC_RECEIVER_CHECKS_NOT_BEHAVIORAL_EVIDENCE','passed':checks}
    (Path(__file__).parent/'SYNTHETIC_CHECKS.json').write_text(json.dumps(output,indent=2)+'\n')
    print(json.dumps({'synthetic_checks':len(checks)}))
if __name__=='__main__':main()
