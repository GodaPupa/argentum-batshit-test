"""Validate real frozen source with a simulated launch ledger, never start behavior."""
from pathlib import Path
import argparse, copy, importlib.util, json, subprocess, sys
sys.dont_write_bytecode=True
a=argparse.ArgumentParser();a.add_argument('--source-root',required=True);a.add_argument('--carrier',required=True);a.add_argument('--output',required=True);args=a.parse_args()
root=Path(args.source_root).resolve()
record='experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
namespace='experiments/manual-transmission/autonomy/clean-room-recovery-20261124'
spec=importlib.util.spec_from_file_location('frozen_verifier',root/record/'verify_dispatch.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
actual_raw=v.raw;actual_git=v.git
current=json.loads(actual_raw(args.carrier,namespace+'/CURRENT.json'))
assert current['attempts']['local_reserved']==current['attempts']['local_started']==0
ledger=copy.deepcopy(current)
ledger.update(status='QUALIFICATION_RESERVED',lease={'owner':v.CLEAN_OWNER,'sole_source_writer':True,'scheduled_executor_enabled':False,'writable_ref':'refs/heads/'+v.CLEAN_BRANCH})
ledger['attempts'].update(local_reserved=1,local_started=1)
historical=json.loads(actual_raw(v.HISTORICAL_CHECKPOINT,'experiments/manual-transmission/autonomy/CURRENT.json'))
envelope={'event':None,'local_execution':True,'first_parent':args.carrier,'validation':None,
          'source':current['source_freeze']['source'],'qualification':current['source_freeze'],
          'grant':historical['surge_bushwhacker_production_authorization'],'run':None,'attempt':1,
          'clean_room':{'recovery_id':v.CLEAN_EPOCH,'branch':v.CLEAN_BRANCH,'charter_commit':v.CLEAN_CHARTER_COMMIT,
                        'charter_blob':v.CLEAN_CHARTER_BLOB,'lane':'local','attempt':1,'attempt_id':v.CLEAN_EPOCH+'/local-01'}}
active=ledger
def mocked_raw(ref,path):
    if ref==args.carrier and path==namespace+'/CURRENT.json':return (json.dumps(active)+'\n').encode()
    return actual_raw(ref,path)
v.raw=mocked_raw
print('START real immutable-source verification with simulated reservation metadata',flush=True)
pins=v.verify(copy.deepcopy(envelope));assert len(pins['bindings'])==376
results=[{'name':'real frozen source, historical grants and all protected bindings','result':'PASS'}]
print('PASS full source verifier; no qualification launched',flush=True)
def reject(name,edit_envelope=None,edit_ledger=None,full=False):
    global active
    active=copy.deepcopy(ledger);e=copy.deepcopy(envelope)
    if edit_envelope:edit_envelope(e)
    if edit_ledger:edit_ledger(active)
    try:(v.verify if full else v.verify_clean_room_authority)(e)
    except (ValueError,KeyError) as error:results.append({'name':name,'result':'PASS','expected':'reject','reason':str(error)})
    else:raise AssertionError('Accepted invalid envelope: '+name)
for key,value in [('branch','manual-transmission/quarantine-recovery-20261106'),('recovery_id','wrong-epoch'),('charter_blob','0'*40),('charter_commit','0'*40),('attempt',2),('attempt_id','wrong-attempt'),('lane','legacy')]:
    reject('wrong-envelope-'+key,edit_envelope=lambda e,k=key,val=value:e['clean_room'].__setitem__(k,val))
reject('wrong-owner',edit_ledger=lambda c:c.__setitem__('owner','old-owner'))
reject('scheduled-executor-enabled',edit_ledger=lambda c:c['lease'].__setitem__('scheduled_executor_enabled',True))
reject('wrong-writable-ref',edit_ledger=lambda c:c['lease'].__setitem__('writable_ref','refs/heads/main'))
reject('no-attempt-reservation',edit_ledger=lambda c:c['attempts'].__setitem__('local_reserved',0))
reject('second-attempt',edit_ledger=lambda c:c['attempts'].__setitem__('local_started',2))
reject('already-completed-local',edit_ledger=lambda c:c['attempts'].__setitem__('local_completed',1))
reject('premature-ci-activation',edit_ledger=lambda c:c['attempts'].__setitem__('ci_activated',1))
reject('wrong-source-tree-freeze',edit_envelope=lambda e:e['qualification'].__setitem__('source_tree','0'*40))
reject('wrong-pins-freeze',edit_envelope=lambda e:e['qualification'].__setitem__('candidate_pins_blob','0'*40))
reject('immutable-source-tree-mismatch',edit_envelope=lambda e:e['qualification'].__setitem__('source_tree','0'*40),edit_ledger=lambda c:c['source_freeze'].__setitem__('source_tree','0'*40),full=True)
reject('immutable-source-pins-blob-mismatch',edit_envelope=lambda e:e['qualification'].__setitem__('candidate_pins_blob','0'*40),edit_ledger=lambda c:c['source_freeze'].__setitem__('candidate_pins_blob','0'*40),full=True)
reject('phase2-game',edit_ledger=lambda c:c['safeguards'].__setitem__('phase2_games',1))
reject('removed-clean-room-cannot-fall-back',edit_envelope=lambda e:e.pop('clean_room'),full=True)
active=ledger
original_git=v.git
def changed_delta(*argv):
    if argv[:3]==('diff','--name-only',v.NORMALIZED_SOURCE):return original_git(*argv)+'\nunlisted/production-change.kt'
    return original_git(*argv)
v.git=changed_delta
try:v.verify_m14_successor(envelope['source'])
except ValueError as error:results.append({'name':'fourth-source-path-rejected','result':'PASS','reason':str(error)})
else:raise AssertionError('Unlisted source path accepted')
v.git=original_git
for kind in ['binding-path-removed','protected-binding-changed','changed-size-forged','historical-metadata-changed']:
    def corrupt_pins(ref,path,k=kind):
        b=mocked_raw(ref,path)
        if ref==envelope['source'] and path==record+'/CANDIDATE_PINS.json':
            j=json.loads(b)
            if k=='binding-path-removed':j['bindings'].pop(next(iter(j['bindings'])))
            elif k=='protected-binding-changed':j['bindings'][next(iter(j['bindings']))]['sha256']='0'*64
            elif k=='changed-size-forged':j['bindings'][record+'/audit_reports.py']['bytes']+=1
            else:j['targeted_report_normalization']['M01_helper_unchanged']=False
            b=json.dumps(j).encode()
        return b
    v.raw=corrupt_pins
    try:v.verify_m14_successor(envelope['source'])
    except ValueError as error:results.append({'name':kind,'result':'PASS','reason':str(error)})
    else:raise AssertionError('Invalid source pins accepted: '+kind)
v.raw=actual_raw
v.git=actual_git
out={'state':'PASS','kind':'IMMUTABLE_SOURCE_AND_SIMULATED_LAUNCH_METADATA_CONTROLS','source':envelope['source'],'carrier':args.carrier,
     'qualification_starts':0,'behavioral_runs':0,'real_source_bindings_verified':376,
     'simulation':'Only a copy of CURRENT launch metadata is supplied in memory. The durable CURRENT remains zero reserved/started. No source/history bytes or refs are changed.',
     'control_count':len(results),'controls':results}
Path(args.output).write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({'state':'PASS','controls':len(results),'qualification_starts':0}),flush=True)
