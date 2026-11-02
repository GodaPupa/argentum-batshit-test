"""Receiver parser controls using archived raw reports; no qualification re-execution."""
from pathlib import Path
import copy,json,zipfile
from receive import rows_from_files,check_rows
P=json.loads(Path(__file__).with_name('PROTOCOL.json').read_text())
positives=[];rejections=[]
def reject(label,fn):
    try:fn()
    except (AssertionError,KeyError,ValueError):rejections.append(label)
    else:raise AssertionError('accepted corrupt witness '+label)
with zipfile.ZipFile('departure-time-r2-original.zip') as z:
    F={n:z.read(n) for n in z.namelist() if n.endswith('.xml')}
for name,m in P['mutants'].items():
    if name=='restore-absent-to-empty-fallback':continue
    rows=rows_from_files(F,'negative-'+name)
    cases={c:P['cases'][c] for c in m['test_classes']}
    check_rows(rows,cases,m['failed_identities'],m);positives.append(name)
m=P['mutants']['restore-absent-to-empty-fallback']
with zipfile.ZipFile('departure-time-full-r1-original.zip') as z:
    F={n:z.read(n) for n in z.namelist() if n.endswith('.xml')}
rows=rows_from_files(F,'ordinary')
trace=[r for r in rows if r['class'].endswith(('LegacySuspensionMigrationTest','SuspensionTraceTest'))]
cases={c:P['cases'][c] for c in m['test_classes'] if '.state.' in c}
failed=[x for x in m['failed_identities'] if '.state.' in x[0]]
check_rows(trace,cases,failed,m);positives.append('both original trace payloads and all24identities')
for label,edit in [
 ('trace wrong field',lambda f:f.update(message=f['message'].replace('castChoices','otherChoices'))),
 ('trace wrong actual',lambda f:f.update(message=f['message'].replace('("castChoices", [])','("castChoices", [true])'))),
 ('trace wrong line',lambda f:f.update(text=f['text'].replace('MigrationTest.kt:65','MigrationTest.kt:650').replace('TraceTest.kt:53','TraceTest.kt:530'))),
 ('trace runtime error',lambda f:f.update(type='RuntimeException'))]:
    corrupted=copy.deepcopy(trace)
    for r in corrupted:
        if r['failure']:edit(r['failure'][0])
    reject(label,lambda:check_rows(corrupted,cases,failed,m))
cls=m['test_classes'][0];cases={cls:P['cases'][cls]};failed=[x for x in m['failed_identities'] if x[0]==cls]
rows=[dict({'class':cls,'name':n},failure=[],errors=[],skips=[]) for n in cases[cls]]
for r in rows:
    key='\t'.join([cls,r['name']]);w=m['witnesses'].get(key)
    if w:r['failure']=[{'type':'org.opentest4j.AssertionFailedError','message':'expected:<true> but was:<false>','text':f"Assertion at {w['file']}:{w['line']}"}]
check_rows(rows,cases,failed,m);positives.append('synthetic exact new boolean witnesses')
for label,edit in [('wrong new boolean',lambda f:f.update(message='expected:<true> but was:<true>')),('missing new line',lambda f:f.update(text='Assertion at LegacyAbsenceChoiceCaptureTest.kt:999'))]:
    bad=copy.deepcopy(rows)
    for r in bad:
        if r['failure']:edit(r['failure'][0])
    reject(label,lambda:check_rows(bad,cases,failed,m))
print(json.dumps({'positive_parser_controls':positives,'rejections':rejections,'new_gameplay_evidence':False},indent=2))
