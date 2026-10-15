#!/usr/bin/env python3
"""Anonymous read-only fetch; no credential/config inheritance or fallback."""
import json,os,pathlib,subprocess,sys
PINS={'R5':'b5195ad2d090d26a533aef5bf3fa6a486b5128c6','R6':'1e334b9625478689c15a7d153e74d3d805791506'}
def fetch(revision,root,log):
 sha=PINS[revision];root=pathlib.Path(root);root.mkdir(exist_ok=False)
 env={'PATH':os.environ['PATH'],'GIT_CONFIG_NOSYSTEM':'1','GIT_CONFIG_GLOBAL':'/dev/null','GIT_TERMINAL_PROMPT':'0','GIT_ASKPASS':'/bin/false','LC_ALL':'C'}
 def git(*args):
  p=subprocess.run(['git','-c','credential.helper=','-c','core.askPass=/bin/false',*args],cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=120)
  log.write(p.stdout);log.flush()
  if p.returncode:raise RuntimeError('anonymous git failed: '+str(p.returncode))
  return p.stdout.strip()
 git('init');git('fetch','--depth=1','https://github.com/GodaPupa/argentum-batshit-test.git',sha)
 git('checkout','--detach','FETCH_HEAD')
 if git('rev-parse','HEAD')!=sha:raise RuntimeError('source identity mismatch')
 return {'revision':revision,'commit':sha,'tree':git('rev-parse','HEAD^{tree}'),'anonymous':True}
if __name__=='__main__':
 try:
  with open(sys.argv[3],'x') as log:result=fetch(sys.argv[1],sys.argv[2],log)
  print(json.dumps(result))
 except Exception as e:print(json.dumps({'status':'INCOMPLETE','reason':str(e)}));sys.exit(2)
