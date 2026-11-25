"""One durable clean-room attempt, using frozen qualification code unchanged."""
from pathlib import Path
import argparse, datetime, fcntl, hashlib, json, os, subprocess, sys

FOLLOWUP_SOURCE='82ec335573572edfd2a2e4d734d2f0d81801139f'
FOLLOWUP_TREE='923ec79e1c867eedf35b408153bf60988fbd26b6'
SOURCE_BASIS='2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
CHARTER_COMMIT='86450c1ecb227804dbd45acf5ba28689ce0c61bc'
CHARTER_BLOB='418fb7f5a0ebeed92582f8285e76305c39250efa'
CHARTER_SHA256='a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e'
PRIOR_RECOVERY_CHECKPOINT='618f562154ff10a9da082a144d43c2011703d2e0'
PRIOR_NAMESPACE='experiments/manual-transmission/autonomy/clean-room-recovery-20261124'
if any(str(value).startswith('PENDING_') for value in (FOLLOWUP_SOURCE, FOLLOWUP_TREE, CHARTER_COMMIT, CHARTER_BLOB, CHARTER_SHA256,)):
    raise RuntimeError('Unfinalized lint-followup draft: fill all pinned identities before use')

parser=argparse.ArgumentParser()
parser.add_argument('--source-root',required=True)
parser.add_argument('--host-root',required=True)
parser.add_argument('--runtime',required=True)
parser.add_argument('--launch-commit',required=True)
args=parser.parse_args()
repo=Path(args.source_root).resolve();host=Path(args.host_root).resolve();runtime=Path(args.runtime).resolve()
record='experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
namespace='experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124'
branch='manual-transmission/clean-room-recovery-20261124';epoch='manual-lint-followup-20261124-01'
def git(*a):return subprocess.check_output(['git',*a],cwd=repo)
def sha(b):return hashlib.sha256(b).hexdigest()
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n')

lockpath=Path.home()/'.cache/argentum/manual-transmission.lock'
lockpath.parent.mkdir(parents=True,exist_ok=True)
lock=lockpath.open('a+');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
current=json.loads(git('show',args.launch_commit+':'+namespace+'/CURRENT.json'))
assert Path(__file__).read_bytes()==git('show',args.launch_commit+':'+namespace+'/run_local_qualification.py')
assert sha((host/'environment.py').read_bytes())==current['host_ready']['environment_helper_sha256']
assert current['branch']==branch and current['recovery_id']==epoch
assert current['owner']==current['lease']['owner']=='manual-lint-foreground-20261124'
assert current['lease']['sole_source_writer'] is True and current['lease']['scheduled_executor_enabled'] is False
assert current['lease']['writable_ref']=='refs/heads/'+branch
assert git('show',args.launch_commit+':'+PRIOR_NAMESPACE+'/CURRENT.json')==git('show',PRIOR_RECOVERY_CHECKPOINT+':'+PRIOR_NAMESPACE+'/CURRENT.json')
charter_bytes=git('show',CHARTER_COMMIT+':'+namespace+'/CHARTER.json')
assert git('rev-parse',CHARTER_COMMIT+':'+namespace+'/CHARTER.json').decode().strip()==CHARTER_BLOB
assert sha(charter_bytes)==CHARTER_SHA256 and git('show',args.launch_commit+':'+namespace+'/CHARTER.json')==charter_bytes
assert current['status']=='QUALIFICATION_RESERVED'
assert current['attempts']['local_reserved']==current['attempts']['local_started']==1
assert current['attempts']['local_completed']==current['attempts']['ci_activated']==current['attempts']['original_artifact_downloads']==0
assert git('ls-remote','origin','refs/heads/'+branch).decode().split()[0]==args.launch_commit
q=current['source_freeze'];assert q['source']==FOLLOWUP_SOURCE and q['source_tree']==FOLLOWUP_TREE
assert q['source_basis']==SOURCE_BASIS and q['binding_count']==378
assert git('show','-s','--format=%P',q['source']).decode().strip()==SOURCE_BASIS
assert git('rev-parse','HEAD').decode().strip()==q['source']
assert git('rev-parse','HEAD^{tree}').decode().strip()==q['source_tree']
assert not git('status','--porcelain=v1','--untracked-files=no').strip()
pins=json.loads((repo/record/'CANDIDATE_PINS.json').read_text())
assert len(pins['bindings'])==378
for path,pin in pins['bindings'].items():
    b=(repo/path).read_bytes();assert sha(b)==pin['sha256'] and len(b)==pin['bytes'],path
host_receipt=json.loads((host/'BUILD_TOOLCHAIN_READY_PREFLIGHT.json').read_text())
assert host_receipt['state']=='PASS' and host_receipt['host_properties_restored']
assert not (repo/'manual-surge-bushwhacker-evidence').exists()
assert not runtime.exists(),'This attempt already has local state; never silently relaunch.'
runtime.mkdir(parents=True)
sys.path.insert(0,str(host));import environment
env=environment.prepare_environment()
props=Path(env['GRADLE_USER_HOME'])/'gradle.properties';original_properties=props.read_bytes()
env.update(PYTHONDONTWRITEBYTECODE='1',RUNNER_TEMP=str(runtime),GRADLE_LOCK_SLOTS='1')
# The Assay Java subprocess needs the same current-session proxy and trust as Gradle.
env['JAVA_TOOL_OPTIONS']=' '.join(x for x in [env.get('JAVA_TOOL_OPTIONS',''),env['GRADLE_OPTS']] if x)
historical=json.loads(git('show','62b95ee8f854cb9a29830832c287106ad8baaac4:experiments/manual-transmission/autonomy/CURRENT.json'))
envelope={'event':None,'local_execution':True,'first_parent':args.launch_commit,'validation':None,
          'source':q['source'],'qualification':q,'grant':historical['surge_bushwhacker_production_authorization'],
          'run':None,'attempt':1,'lint_followup':{'recovery_id':epoch,'branch':branch,
          'charter_commit':CHARTER_COMMIT,
          'charter_blob':CHARTER_BLOB,'lane':'local','attempt':1,'attempt_id':epoch+'/local-01'}}
save(runtime/'RECOVERY_EVENT.json',envelope)
launch={'state':'LOCAL_LAUNCH_RECORDED','recovery_id':epoch,'attempt_id':epoch+'/local-01',
        'launch_commit':args.launch_commit,'source':q['source'],'started_utc':now(),
        'qualification_invocations':0,'process_id':os.getpid(),
        'lock':'standard exclusive Manual flock','host_configuration':'current invocation proxy and platform CA; exact Temurin21.0.13+9, Node22, historical4g/inprocess settings'}
save(runtime/'LOCAL_LAUNCH.json',launch)
code=None
try:
    props.write_bytes(original_properties+b'\norg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=768m -XX:+UseSerialGC\nkotlin.compiler.execution.strategy=in-process\n')
    subprocess.run([sys.executable,str(repo/record/'verify_dispatch.py'),str(runtime/'RECOVERY_EVENT.json')],cwd=repo,env=env,check=True)
    launch['qualification_invocations']=1;launch['qualifier_started_utc']=now();save(runtime/'LOCAL_LAUNCH.json',launch)
    with (runtime/'qualification.log').open('x') as log:
        child=subprocess.Popen([sys.executable,str(repo/record/'qualify.py')],cwd=repo,env=env,
                               stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        for line in child.stdout:
            log.write(line);log.flush();print(line,end='',flush=True)
        code=child.wait()
finally:
    props.write_bytes(original_properties)
    bad=[path for path,pin in pins['bindings'].items() if sha((repo/path).read_bytes())!=pin['sha256'] or (repo/path).stat().st_size!=pin['bytes']]
    terminal={'state':'QUALIFICATION_FINISHED' if code is not None else 'LAUNCH_OR_VERIFICATION_FAILED',
              'returncode':code,'ended_utc':now(),'qualification_invocations':launch['qualification_invocations'],
              'source':q['source'],'launch_commit':args.launch_commit,'source_bindings_verified':378,
              'all_bindings_match':not bad,'failed_bindings':bad,
              'tracked_index_clean':not git('status','--porcelain=v1','--untracked-files=no').strip(),
              'host_properties_restored':props.read_bytes()==original_properties,'admitted':False,
              'phase2_games':0,'phase2_seeds':0,'ci_activations':0,'original_artifact_downloads':0}
    save(runtime/'LOCAL_TERMINAL.json',terminal);print(json.dumps(terminal),flush=True)
sys.exit(code if code is not None else 2)

