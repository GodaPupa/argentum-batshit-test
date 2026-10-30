"""Synthetic transport controls; not card/engine evidence."""
import io,zipfile,json,copy
from RECEIVE import unpack,envelope,sha
P=json.load(open(__file__.replace('check_receiver.py','PINS.json')))
def archive(data):
 b=io.BytesIO()
 with zipfile.ZipFile(b,'w') as z:
  for n,v in data.items():z.writestr(n,v)
 return b.getvalue()
body={'a.txt':b'original'};body['MANIFEST.json']=json.dumps({'a.txt':sha(body['a.txt'])}).encode();raw=archive(body)
assert unpack(raw)[0]==body
r={'id':P['run'],'head_sha':P['validation'],'head_branch':P['branch'],'run_attempt':1,'run_number':1,'event':'push','status':'completed','workflow_id':P['workflow_id'],'path':P['workflow']}
j={'total_count':1,'jobs':[{'id':P['job'],'run_id':P['run'],'head_sha':P['validation'],'status':'completed','run_attempt':1}]}
a={'total_count':1,'artifacts':[{'name':P['artifact_name'],'expired':False,'workflow_run':{'id':P['run'],'head_sha':P['validation']},'size_in_bytes':len(raw),'digest':'sha256:'+sha(raw)}]}
g={'source':{'sha':P['source'],'tree':{'sha':P['tree']},'parents':[{'sha':P['base']}]},'validation':{'sha':P['validation'],'tree':{'sha':P['validation_tree']},'parents':[{'sha':P['source']}]},'main':{'sha':P['main']},'source_delta':{'files':[{'filename':x} for x in P['source_delta']]},'validation_delta':{'files':[{'filename':P['workflow']}]}}
envelope(P,r,j,a,raw,g);bad=[]
def reject(name,fn):
 try:fn()
 except (AssertionError,KeyError,ValueError):bad.append(name);return
 raise AssertionError('accepted '+name)
reject('manifest hash drift',lambda:unpack(archive(dict(body,**{'a.txt':b'changed'}))))
reject('unmanifested member',lambda:unpack(archive(dict(body,**{'extra':b'x'}))))
reject('unsafe path',lambda:unpack(archive(dict(body,**{'../escape':b'x'}))))
for label,part,key,value in [('wrong run',r,'id',0),('wrong event',r,'event','pull_request'),('rerun',r,'run_attempt',2),('nonterminal',r,'status','in_progress')]:
 rr=copy.deepcopy(r);rr[key]=value;reject(label,lambda rr=rr:envelope(P,rr,j,a,raw,g))
aa=copy.deepcopy(a);aa['artifacts'][0]['digest']='sha256:'+('0'*64);reject('archive digest mismatch',lambda:envelope(P,r,j,aa,raw,g))
gg=copy.deepcopy(g);gg['source']['parents'][0]['sha']='0'*40;reject('source parent mismatch',lambda:envelope(P,r,j,a,raw,gg))
gg=copy.deepcopy(g);gg['validation_delta']['files'].append({'filename':'unrelated'});reject('unrelated validation delta',lambda:envelope(P,r,j,a,raw,gg))
jj=copy.deepcopy(j);jj['jobs'][0]['id']=0;reject('wrong original job',lambda:envelope(P,r,jj,a,raw,g))
print(json.dumps({'synthetic_only':True,'valid_shapes':2,'rejected':bad},indent=2))
