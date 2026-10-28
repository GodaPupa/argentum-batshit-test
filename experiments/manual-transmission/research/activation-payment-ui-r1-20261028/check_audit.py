"""Synthetic receiver checks only, never behavioral qualification."""
import copy,json,tempfile,zipfile
from pathlib import Path
from audit import P,browser_gate,discovery
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
            attachments.append({'name':name,'path':'/runner/manual-payment-ui-r1-evidence/normal/'+name})
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
    with tempfile.TemporaryDirectory() as tmp:
        root=Path(tmp)
        for m in P['mutations']:
            folder=root/('discover-'+m['name']);folder.mkdir()
            (folder/'exit.txt').write_text('0')
            report={'errors':[],'suites':[{'specs':[{'title':t,'tests':[{}]} for t in [m['witness'],P['control']]]}]}
            (folder/'output.log').write_text(json.dumps(report))
        discovery(root);checks.append('accept exact discovery identities')
        folder=root/('discover-'+P['mutations'][0]['name']);good=json.loads((folder/'output.log').read_text())
        for label,mutate in [('empty discovery',lambda x:x['suites'][0]['specs'].clear()),('wrong discovery',lambda x:x['suites'][0]['specs'][0].update(title='unrelated')),('duplicate discovery',lambda x:x['suites'][0]['specs'].append(x['suites'][0]['specs'][0])),('discovery runner error',lambda x:x['errors'].append({'message':'No tests found'}))]:
            bad=copy.deepcopy(good);mutate(bad);(folder/'output.log').write_text(json.dumps(bad))
            try:discovery(root)
            except Exception:checks.append('reject '+label)
            else:raise AssertionError('accepted '+label)
    output={'scope':'SYNTHETIC_RECEIVER_CHECKS_NOT_BEHAVIORAL_EVIDENCE','passed':checks}
    (Path(__file__).parent/'SYNTHETIC_CHECKS.json').write_text(json.dumps(output,indent=2)+'\n')
    print(json.dumps({'synthetic_checks':len(checks)}))
if __name__=='__main__':main()
