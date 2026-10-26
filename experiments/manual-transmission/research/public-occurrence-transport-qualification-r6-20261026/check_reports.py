import copy,json
import xml.etree.ElementTree as ET
from reports import P,server,client

def xml(mutant):
    root=ET.Element('testsuite',tests='4',failures='3' if mutant else '0',errors='0',skipped='0')
    for name in P['server_cases']:
        row=ET.SubElement(root,'testcase',name=name,classname='com.wingedsheep.gameserver.session.FixedRecipientChoiceTransportTest')
        if mutant and name in P['server_witnesses']:
            ET.SubElement(row,'failure',type=P['assertion_type']).text=f'FixedRecipientChoiceTransportTest.kt:{P["server_witnesses"][name]}'
    return {'TEST-test.xml':ET.tostring(root)}

def js(variant):
    failures=P.get('client_'+variant+'_witnesses',{})
    rows=[dict(title=n,fullName=P['client_full_names'][n],status='failed' if n in failures else 'passed',failureMessages=[f'AssertionError: expected value\n liveInteraction.test.ts:{failures[n]}:5'] if n in failures else []) for n in P['client_cases']]
    return dict(numTotalTests=P['client_count'],numPendingTests=0,numTodoTests=0,numFailedTests=len(failures),numPassedTests=P['client_count']-len(failures),success=not failures,testResults=[dict(assertionResults=rows)])

checks=[]
for m in [False,True]:server(xml(m),m);checks.append('server normal/mutant exact synthetic contract')
for v in ['normal','index','stale','legacy','group']:client(json.dumps(js(v)),v);checks.append('client '+v+' exact synthetic contract')
def reject(label,fn):
    try:fn()
    except (AssertionError,KeyError,ValueError):checks.append(label);return
    raise AssertionError('accepted invalid '+label)
reject('missing XML rejected',lambda:server({}))
reject('duplicate XML rejected',lambda:server({**xml(False),'TEST-other.xml':next(iter(xml(False).values()))}))
bad=xml(True);bad={k:v.replace(b'org.opentest4j.AssertionFailedError',b'CompilerError') for k,v in bad.items()}
reject('nonassertion rejected',lambda:server(bad,True))
badframe={k:v.replace(b'.kt:45',b'.kt:999') for k,v in xml(True).items()}
reject('wrong Kotlin frame rejected',lambda:server(badframe,True))
for key,value in [('numPendingTests',1),('numTotalTests',0),('numFailedTests',0)]:
    data=js('index');data[key]=value
    reject('client '+key+' mismatch rejected',lambda data=data:client(json.dumps(data),'index'))
data=js('stale');data['testResults'][0]['assertionResults'][0]['title']='different'
reject('wrong identity rejected',lambda:client(json.dumps(data),'stale'))
data=js('stale')
for row in data['testResults'][0]['assertionResults']:
    if row['status']=='failed':row['failureMessages']=['AssertionError liveInteraction.test.ts:999:1']
reject('wrong TS frame rejected',lambda:client(json.dumps(data),'stale'))
data=js('normal');data['testResults'][0]['assertionResults'][0]['fullName']='wrong suite identity'
reject('wrong full name rejected',lambda:client(json.dumps(data)))
print(json.dumps({'state':'SYNTHETIC_CHECKS_ONLY_NOT_BEHAVIORAL_EVIDENCE','count':len(checks),'checks':checks}))
