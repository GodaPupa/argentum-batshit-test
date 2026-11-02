"""Synthetic integrity controls only; none are gameplay/qualification evidence."""
import copy,io,json,tempfile,zipfile,hashlib,stat
from pathlib import Path
from receive import archive_files,check_rows
rejected=[]
def reject(name,fn):
 try:fn()
 except (AssertionError,KeyError,ValueError,zipfile.BadZipFile):rejected.append(name)
 else:raise AssertionError('accepted corrupt fixture: '+name)
cases={'test.Probe':['witness']}
contract={'assertion_file':'Probe.kt','assertion_line':71,'expected':'25','actual':'22'}
normal=[{'class':'test.Probe','name':'witness','failure':[],'errors':[],'skips':[]}]
negative=copy.deepcopy(normal);negative[0]['failure']=[{'type':'AssertionError','message':'expected:<25> but was:<22>','text':'AssertionError at test.Probe(Probe.kt:71)'}]
check_rows(normal,cases,[]);check_rows(negative,cases,[('test.Probe','witness')],contract)
def mutate(rows,fn):
 result=copy.deepcopy(rows);fn(result);return result
for name,rows,failed in [
 ('duplicate identity',normal*2,[]),('missing row',[],[]),
 ('wrong class',mutate(normal,lambda r:r[0].update({'class':'test.Other'})),[]),
 ('renamed row',mutate(normal,lambda r:r[0].update({'name':'other'})),[]),
 ('skip',mutate(normal,lambda r:r[0].update({'skips':[{}]})),[]),
 ('runtime error',mutate(normal,lambda r:r[0].update({'errors':[{}]})),[]),
 ('unexpected assertion',negative,[]),('missing negative witness',normal,[('test.Probe','witness')])]:
 reject(name,lambda rows=rows,failed=failed:check_rows(rows,cases,failed))
for name,fields in [('compiler error',{'type':'CompilerException','text':'unresolved Probe.kt:71'}),
 ('wrong line',{'text':'AssertionError at Probe.kt:72'}),('line prefix',{'text':'AssertionError at Probe.kt:710'}),
 ('wrong file',{'text':'AssertionError at Other.kt:71'}),('wrong expected',{'message':'expected:<24> but was:<22>'}),
 ('wrong actual',{'message':'expected:<25> but was:<21>'}),
 ('expected numeric prefix',{'message':'expected:<250> but was:<22>'}),
 ('actual numeric prefix',{'message':'expected:<25> but was:<220>'})]:
 rows=copy.deepcopy(negative);rows[0]['failure'][0].update(fields)
 reject(name,lambda rows=rows:check_rows(rows,cases,[('test.Probe','witness')],contract))
with tempfile.TemporaryDirectory() as directory:
 p=Path(directory)/'fixture.zip'
 def make(members,manifest=None,symlink=False):
  with zipfile.ZipFile(p,'w') as z:
   for name,data in members:
    if symlink:
     info=zipfile.ZipInfo(name);info.external_attr=(stat.S_IFLNK|0o777)<<16;z.writestr(info,data)
    else:z.writestr(name,data)
   z.writestr('MANIFEST.json',json.dumps(manifest if manifest is not None else {n:hashlib.sha256(b).hexdigest() for n,b in members}))
 make([('source.txt',b'original')]);assert archive_files(p)['source.txt']==b'original'
 make([('source.txt',b'altered')],{'source.txt':hashlib.sha256(b'original').hexdigest()});reject('changed bytes',lambda:archive_files(p))
 make([('source.txt',b'original'),('extra.txt',b'extra')],{'source.txt':hashlib.sha256(b'original').hexdigest()});reject('extra unmanifested file',lambda:archive_files(p))
 for name in ['../escape','/absolute','a\\escape']:
  make([(name,b'x')]);reject('unsafe '+name,lambda:archive_files(p))
 make([('source.txt',b'x'),('source.txt',b'x')]);reject('duplicate ZIP entry',lambda:archive_files(p))
 make([('link',b'other')],symlink=True);reject('symlink',lambda:archive_files(p))
print(json.dumps({'positive_synthetic_controls':3,'rejections':rejected,'gameplay_evidence':False},indent=2))
