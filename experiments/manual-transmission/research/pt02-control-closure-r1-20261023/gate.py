"""Source-bound PT02 control closure. Never changes a remote branch or runs official games."""
from pathlib import Path
import hashlib,json,os,re,subprocess,sys,xml.etree.ElementTree as ET
ROOT=Path('experiments/manual-transmission/research/pt02-control-closure-r1-20261023')
OUT=Path('manual-pt02-closure-evidence')
R5='0b5018a0df392acc96d8b38e20dd72ccae3ff48a'
BASE='418cdfdacd5191549e9fc8e4a071330120b071b7'
OLD='rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenInstructionScenarioTest.kt'
TEST='rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/PausedTokenPT02ControlClosureTest.kt'
R2=Path('experiments/manual-transmission/research/paused-token-entry-r2-20261023')
HANDLER='rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/decision/SubmitDecisionHandler.kt'
CLASS='com.wingedsheep.engine.scenarios.PausedTokenPT02ControlClosureTest'
XML='rules-engine/build/test-results/test/TEST-'+CLASS+'.xml'
NAMES={'PT02 entry triggers remain unstacked while later batch choices wait; sourceCopy='+a+', serialized='+b for a in ['false','true'] for b in ['false','true']}
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
def obj(ref,p):return subprocess.check_output(['git','show',ref+':'+p])
def sh(b):return hashlib.sha256(b).hexdigest()
def put(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,indent=2)+'\n')
def paths():return json.loads((R2/'PROTOCOL.json').read_text())['baseline_restore_paths']+[HANDLER]
def derive(body):
    s=body.decode();loop='    for (sourceCopy in listOf(false, true)) for (serialized in listOf(false, true)) {\n'
    assert s.count(loop)==1
    start=s.index('        test("PT02 ');end=s.index('        test("PT03 ',start)
    return (s[:s.index(loop)].replace('class PausedTokenInstructionScenarioTest :','class PausedTokenPT02ControlClosureTest :')+loop+'        val suffix = "sourceCopy=$sourceCopy, serialized=$serialized"\n'+s[start:end]+'    }\n})\n').encode()
def bind(mode):
    assert mode in ['negative','candidate']
    source=os.environ['SOURCE'];assert git('rev-parse','HEAD^')==source
    assert git('rev-parse',source+'^{tree}')==os.environ['SOURCE_TREE']
    assert git('diff','--name-only',source,'HEAD')==os.environ['WORKFLOW']
    assert git('merge-base',R5,source)==R5
    assert os.getenv('GITHUB_RUN_ATTEMPT','1')=='1' and os.getenv('GITHUB_RUN_NUMBER','1')=='1'
    allowed={TEST,str(ROOT/'PROTOCOL.json'),str(ROOT/'gate.py'),str(ROOT/'validation.just')}
    assert set(git('diff','--name-only',R5,source).splitlines())==allowed
    assert git('rev-parse',source+':'+OLD)=='2783d8891a70c93b88a1e5be8237cd4ad1afdb83'
    assert git('rev-parse',source+':'+TEST)=='5b5d8e1d13907107670da9bfe320a4e4df6fac48'
    assert Path(TEST).read_bytes()==derive(obj(R5,OLD))==obj(source,TEST)
    assert Path(OLD).read_bytes()==obj(R5,OLD)
    assert Path(TEST).read_text().splitlines()[118].strip()=='stackObjects.count { it.has<TriggeredAbilityOnStackComponent>() } shouldBe 0'
    restore=paths();assert len(restore)==len(set(restore))==10
    production={p for p in git('diff','--name-only',BASE,source).splitlines() if '/src/main/' in p}
    assert production=={p for p in restore if '/src/main/' in p} and len(production)==9
    for p in restore:
        ref=BASE if mode=='negative' else source
        saved=subprocess.run(['git','show',ref+':'+p],capture_output=True)
        if saved.returncode:assert not Path(p).exists(),p
        else:assert Path(p).read_bytes()==saved.stdout,p
    assert set(git('diff','--name-only').splitlines())==(set(restore) if mode=='negative' else set())
    if mode=='candidate':assert not git('status','--porcelain','--untracked-files=no')
    bindings=json.loads((R2/'BINDINGS.json').read_text());assert len(bindings)==144
    ep='rules-engine/src/test/kotlin/com/wingedsheep/engine/event/EntryChoiceEventProvenanceTest.kt'
    annex='experiments/manual-transmission/research/paused-token-entry-r5-20261023/annex/EntryChoiceEventProvenanceTest.R4.original.kt.txt'
    for p,spec in bindings.items():
        body=Path(annex).read_bytes() if p==ep else Path(p).read_bytes()
        assert sh(body)==spec['sha256'],p
    gs=json.loads(Path('experiments/manual-transmission/research/ordinary-build-r1-20261022/GOLDENS.json').read_text());assert len(gs)==23
    for item in gs:assert sh(Path(item['path']).read_bytes())==item['sha256'],item['path']
    assert git('rev-parse',source+':experiments/manual-transmission/control/v0.7.decklist.txt')=='c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89'
    assert git('rev-parse','refs/remotes/origin/main')=='37a603617a03dfd2d81b39dae004f7f336a70dfc'
    put(OUT/('binding-'+mode+'.json'),dict(source=source,source_tree=os.environ['SOURCE_TREE'],validation=git('rev-parse','HEAD'),baseline=BASE,r5=R5,mode=mode,restore_paths=restore,production_baseline_paths=sorted(production),exact_derived_test_sha256=sh(Path(TEST).read_bytes()),goldens=23,protected_bindings=144,official_games=0,official_seeds=0,precision_reserved=2))
def restore(mode):
    assert mode in ['negative','candidate'];ref=BASE if mode=='negative' else os.environ['SOURCE']
    for p in paths():
        body=subprocess.run(['git','show',ref+':'+p],capture_output=True)
        if body.returncode:Path(p).unlink(missing_ok=True)
        else:Path(p).parent.mkdir(parents=True,exist_ok=True);Path(p).write_bytes(body.stdout)
    bind(mode)
def prepare(stage):
    assert stage in ['negative','positive'];out=OUT/stage;out.mkdir(parents=True,exist_ok=True)
    p=Path(XML)
    if p.exists():(out/'preexisting-junit.xml').write_bytes(p.read_bytes());p.unlink()
def receive(stage):
    assert stage in ['negative','positive'];out=OUT/stage;out.mkdir(parents=True,exist_ok=True)
    raw=Path(XML).read_bytes();(out/'original-junit.xml').write_bytes(raw)
    suite=ET.fromstring(raw);cases=suite.findall('.//testcase')
    report={'stage':stage,'source':os.environ['SOURCE'],'validation':os.environ.get('GITHUB_SHA'),'run':os.environ.get('GITHUB_RUN_ID'),'xml_sha256':sh(raw),'suite':suite.attrib,'cases':[]}
    for c in cases:
        f=c.find('failure');e=c.find('error');k=c.find('skipped')
        report['cases'].append(dict(name=c.get('name'),classname=c.get('classname'),failure=None if f is None else dict(type=f.get('type'),message=f.get('message'),detail=f.text),error=e is not None,skipped=k is not None))
    put(out/'RECEIVING.json',report)
    assert len(cases)==int(suite.get('tests'))==4
    assert {c.get('name') for c in cases}==NAMES and all(c.get('classname')==CLASS for c in cases)
    assert int(suite.get('errors'))==int(suite.get('skipped'))==0
    assert all(c.find('error') is None and c.find('skipped') is None for c in cases)
    code=int((out/'exit.txt').read_text())
    if stage=='negative':
        assert code!=0 and int(suite.get('failures'))==4
        for c in cases:
            f=c.find('failure');assert f is not None and f.get('type')=='org.opentest4j.AssertionFailedError'
            frame=re.search(r'PausedTokenPT02ControlClosureTest.kt:(\d+)',f.text or '')
            assert frame and int(frame.group(1))==119
            assert 'expected:<0> but was:<1>' in f.get('message','')
    else:
        assert code==0 and int(suite.get('failures'))==0 and all(c.find('failure') is None for c in cases)
    log=(out/'gradle.log').read_text();assert ('> Task :rules-engine:test FAILED' if stage=='negative' else '> Task :rules-engine:test\n') in log
    report['status']='EXPECTED_FOUR_ASSERTION_FAILURES_VERIFIED' if stage=='negative' else 'FOUR_CANDIDATE_PASSES_VERIFIED';put(out/'RECEIVING.json',report)
    print(json.dumps({'stage':stage,'status':report['status'],'cases':4},indent=2))
if __name__=='__main__':
    command,mode=sys.argv[1:];{'bind':bind,'restore':restore,'prepare':prepare,'receive':receive}[command](mode)
