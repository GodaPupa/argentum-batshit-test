from pathlib import Path
import hashlib,json,os,subprocess
r=Path(os.environ['RECORD'])
p=json.loads((r/'PROTOCOL.json').read_text())
mode=os.environ.get('BIND_MODE','fixed')
source=os.environ['SOURCE']
git=lambda *args: subprocess.check_output(['git',*args],text=True).strip()
assert git('rev-parse','HEAD^') == source
assert git('diff','--name-only','HEAD^','HEAD') == os.environ['WORKFLOW']
for path,v in json.loads((r/'BINDINGS.json').read_text()).items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==v['sha256'],path
for path,v in p['source_manifest'].items():
    if mode=='negative' and (v.get('baseline_absent') or path in p['negative_excluded_tests']):
        assert not Path(path).exists(),path
        continue
    key='baseline_sha256' if mode=='negative' and 'baseline_sha256' in v else 'sha256'
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest()==v[key],path
goldens=json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text())
for g in goldens:
    assert hashlib.sha256(Path(g['path']).read_bytes()).hexdigest()==g['sha256']
if mode=='fixed':
    assert not git('status','--porcelain','--untracked-files=no')
out=Path('copiable-entry-evidence');out.mkdir(exist_ok=True)
(out/('binding-'+mode+'.json')).write_text(json.dumps({'source':source,'validation':git('rev-parse','HEAD'),
    'baseline':p['baseline_source'],'source_tree':git('rev-parse',source+'^{tree}'),'mode':mode,
    'source_manifest':p['source_manifest'],'bindings':json.loads((r/'BINDINGS.json').read_text()),
    'goldens_verified':len(goldens),'official_games':0,'official_seeds':0,'precision_reserved':2},indent=2)+'\n')
print('Verified',mode,'source, 23 protected files and',len(goldens),'goldens')
