"""One-time receiving of original R1 compiler failure; never engine execution."""
from pathlib import Path
import base64, hashlib, json, re, zipfile
ROOT=Path(__file__).resolve().parent
sha=lambda b:hashlib.sha256(b).hexdigest()
blob=lambda b:hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
m=json.loads((ROOT/'metadata.json').read_text());r=m['run'];a=m['artifact'];j=m['jobs']['jobs'][0]
S='dd50a5ce32331912c7193c3a453d12548424ce2e';T='0fd4dd776d992eab49d85c239471388d0a5aaf7a';B='e067e4b803d3cb2358d0b363929b940ebcfa2cbf';V='c4957fad6391fe19c7fef027108061e11a2fef25';VT='2452318a2888cfcbd178b6ba7b98279b81248cf8';W='.github/workflows/manual-source-history-diagnostic-r1-20261025.yml'
assert r['id']==40178974689 and r['head_sha']==V and r['path']==W and r['workflow_id']==395798725
assert r['event']=='push' and r['run_number']==r['run_attempt']==1 and r['status']=='completed' and r['conclusion']=='failure'
assert m['jobs']['total_count']==len(m['jobs']['jobs'])==1
assert j['id']==121125720316 and j['run_id']==r['id'] and j['run_attempt']==1 and j['head_sha']==V and j['conclusion']=='failure'
steps={s['name']:s['conclusion'] for s in j['steps']}
assert steps['Compile unchanged production and new diagnostic tests']=='failure'
assert steps['Execute exactly five novel source-history diagnostic cases']=='skipped'
for n in ['Bind accepted production and exact four added diagnostic paths','Rebind unchanged accepted source after diagnostic','Preserve original source logs reports and manifest','Run actions/upload-artifact@v4']: assert steps[n]=='success'
for key,s,t,p in [('source_commit',S,T,B),('validation_commit',V,VT,S)]:
 c=m[key];assert c['sha']==s and c['tree']['sha']==t and [x['sha'] for x in c['parents']]==[p]
assert m['main']['object']['sha']=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
delta=m['source_delta']['files'];assert len(delta)==4 and all(x['status']=='added' for x in delta)
vd=m['validation_delta']['files'];assert len(vd)==1 and vd[0]['filename']==W and vd[0]['sha']=='442902b62f20293afefb788d37f2ada587584070'
data=(ROOT/'original-12593915179.zip').read_bytes()
assert a['id']==12593915179 and a['workflow_run']['id']==r['id'] and a['workflow_run']['head_sha']==V
assert len(data)==a['size_in_bytes']==20468 and 'sha256:'+sha(data)==a['digest']=='sha256:156202461889ef74778692f42399ac0c0570a00bc14e2b298c5ee10a1ba82c82'
with zipfile.ZipFile(ROOT/'original-12593915179.zip') as z:
 assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
 raw={n:z.read(n) for n in z.namelist()}
manifest=json.loads(raw['MANIFEST.json']);missing=set(manifest)-set(raw)
assert missing=={'source/'+W} and set(raw)-set(manifest)=={'MANIFEST.json'}
for p,spec in manifest.items():
 if p not in missing: assert spec==dict(bytes=len(raw[p]),sha256=sha(raw[p])),p
for f in delta:assert blob(raw['source/'+f['filename']])==f['sha']
binding=json.loads(raw['BINDING.json'])
assert (binding['source'],binding['tree'],binding['validation'],binding['base'])==(S,T,V,B)
assert int(binding['run'])==r['id'] and binding['attempt']==1 and binding['job']=='diagnostic'
assert binding['protected_bindings']==143 and binding['accepted_resolver_unchanged'] and binding['goldens']==23 and binding['fixtures']==82
assert binding['delta']==[['A',f['filename']] for f in delta] and not raw['tracked-status.txt'].strip()
chunks=raw['source-delta.patch'].decode().split('diff --git ')[1:];assert len(chunks)==4
for ch in chunks:
 lines=ch.splitlines();match=re.fullmatch(r'a/(\S+) b/\1',lines[0]);assert match
 h=next(i for i,l in enumerate(lines) if l.startswith('@@'));assert all(l.startswith('+') for l in lines[h+1:])
 assert ('\n'.join(l[1:] for l in lines[h+1:])+'\n').encode()==raw['source/'+match[1]]
log=raw['compile.log'].decode();errors=[l for l in log.splitlines() if l.startswith('e: ')]
assert len(errors)==2
for line,col,error in zip([34,37],[103,103],errors):assert f'CurrentTargetSourceHistoryDiagnosticTest.kt:{line}:{col} No value passed for parameter \'target\'.' in error
assert re.findall(r'^> Task (\S+) FAILED$',log,re.M)==[':rules-engine:compileTestKotlin'] and 'BUILD FAILED' in log
assert raw['compile-exit.txt'].strip()==b'1' and not any(n.endswith('.xml') for n in raw)
assert 'DIAGNOSTIC_RESULT.json' not in raw and 'test-exit.txt' not in raw
result=dict(state='VERIFIED_COMPILATION_FAILURE_ZERO_BEHAVIORAL_EVIDENCE',source=S,tree=T,base=B,validation=V,validation_tree=VT,workflow=W,workflow_blob=vd[0]['sha'],workflow_id=r['workflow_id'],run=r['id'],job=j['id'],attempt=1,artifact=a,manifest_entries=len(manifest),verified_present_manifest_entries=len(manifest)-1,missing_manifest_entries=sorted(missing),compiler_errors=errors,executed_cases=0,behavioral_evidence=False,accepted=False,protected_bindings=143,accepted_resolver_unchanged=True,goldens=23,fixtures=82,diagnosis='Both novel fixture Effects.Destroy calls omit the mandatory EffectTarget. Source-bound API blob1904846d3b5ad489224784b5d9687c68f8b207ee confirms signature. Also upload-artifact omitted source/.github/workflows despite archive manifest entry. Neither is target-legality behavioral evidence.',justified_successor='Supply explicit single requirement ContextTarget(0) to the two fixture calls without moving or weakening any assertion; archive workflow at a non-hidden path with exact blob verification. Preserve original failure and all protected production. Freeze new source/protocol/review/workflow before one successor.',official_games=0,official_seeds=0,precision_reserved=2)
(ROOT/'VERIFIED_FAILURE.json').write_text(json.dumps(result,indent=2)+'\n')
(ROOT/'original-12593915179.zip.b64').write_text(base64.b64encode(data).decode()+'\n')
print(json.dumps({k:result[k] for k in ['state','executed_cases','verified_present_manifest_entries','missing_manifest_entries','compiler_errors']},indent=2))
