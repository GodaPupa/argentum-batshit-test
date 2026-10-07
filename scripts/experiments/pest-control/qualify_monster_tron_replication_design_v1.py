#!/usr/bin/env python3
"""Static design qualification only: no RNG, network, subprocess, engine or file writes.

Validates a seed-free proposal, not runtime authority. Negatives are in-memory contract
mutations, never official seed vectors or gameplay. JSON stdout is preservation evidence.
"""
import collections
import copy
import hashlib
import json
from pathlib import Path

CONTRACT = 'docs/experiments/pest-control/monster-tron-replication-12-prospective-v1.json'
SPEC = 'docs/experiments/pest-control/monster-tron-replication-12-prospective-v1.md'

class Rejected(ValueError):
    pass

def require(condition, reason):
    if not condition:
        raise Rejected(reason)

def validate(d):
    require(d['schema']=='pest-monster-tron-replication-prospective-v1' and d['status']=='PROPOSED_DESIGN_ONLY','design only')
    require(set(d['current_authority'])=={'entropy','seed_allocation','dispatch','claim','initialization','gameplay','replication'},'authority coverage')
    require(all(v is False for v in d['current_authority'].values()),'authority denied')
    require(d['baseline']==dict(source='ed35c3035d41a740aac1657708b2023b624cfc47',tree='34406e5a26aad6d6ebf64e9b1a1977e1b719b095',engine='433df3310efe31c49f27034b50e6d8d7e60561f7',stopping_rule_blob='6c737d8bb7af58cccc7cafbb60a6abe9876695ae',r1_proposal_blob='f319d6d9e84608f5c251ce805cb669a7799b6c6b'),'baseline identity')
    p=d['prior_smoke']
    require(p==dict(review=6046389902,adoption=6046397725,run=37676510705,attempt=1,claim='f165a7d19447d68a7db5984c36f1654179120eff',artifact=11506879794,artifact_sha256='e4dcf7eb85a08a6bc16a4f4b93c23ffb373b3a1e4a26dde2ced613329fa3fbaf',ordered_results=['W','W','L','W'],pooled=False,authority_consumed=True),'prior smoke preserved separately')
    s=d['sample']; rows=s['cells']
    require(type(s['games']) is int and s['games']==12 and len(rows)==12,'predeclared twelve')
    require(s['rationale']=='PREDECLARED_THREE_PER_CELL_DESCRIPTIVE_NOT_POWERED' and s['outcome_conditioned'] is False,'prospective rationale')
    require(s['official_seeds'] is None,'no seed allocation')
    require([r['slot'] for r in rows]==list(range(1,13)),'immutable slot order')
    for i,r in enumerate(rows):
        require(type(r['pest_seat']) is int and type(r['monster_seat']) is int,'seat types')
        require(r==dict(slot=i+1,pest_seat=(i%4)//2,monster_seat=1-(i%4)//2,starting_deck='PEST_CONTROL' if i%2==0 else 'MONSTER_TRON',pest_play_draw='PLAY' if i%2==0 else 'DRAW'),'exact prospective cell')
    require(collections.Counter((r['pest_seat'],r['pest_play_draw']) for r in rows)=={(0,'PLAY'):3,(0,'DRAW'):3,(1,'PLAY'):3,(1,'DRAW'):3},'balanced cells')
    require(set(d['future_bindings'])=={'source','tree','workflow','workflow_blob','vector','assignments','archive','exclusions','freeze_authority','execution_authority'} and all(x is None for x in d['future_bindings'].values()),'no invented future identities')
    f=d['freeze']
    require(f['entropy_calls']==1 and f['entropy_bytes']==12*8 and f['encoding']=='SIGNED_BIG_ENDIAN_INT64','single complete draw')
    require(f['minimum_exclusion_floor']==574,'exclusion floor includes R1')
    for k in ('quarantine_before_decode','fsync_file_and_directory','remote_preservation_before_validation','complete_draw_rejection','complete_live_inventory_required','reconcile_inventory_before_and_after','zero_duplicate_collision_rejected','unchanged_order','ambiguous_draw_consumed'):
        require(f[k] is True,'freeze '+k)
    e=d['execution']
    require(e['claim_ref']=='refs/heads/pest-control/official-attempts/monster-tron-preboard-replication-12-v1' and e['branch']=='pest-control/tier1-monster-tron-replication-12-v1','separate prospective namespace')
    require(e['dispatch_limit']==e['run_attempt']==e['claim_limit']==1,'one shot')
    for k in ('names_only','create_only','claim_reserves_whole_block','validation_ack_cannot_execute','fresh_exact_authority_required'):
        require(e[k] is True,'execution '+k)
    require((e['process_cap_seconds'],e['job_cap_seconds'],e['claim_reserve_seconds'],e['upload_reserve_seconds'])==(18000,21600,600,900),'unchanged caps')
    require(e['preclaim_minimum_remaining_seconds']==sum(e[k] for k in ('process_cap_seconds','claim_reserve_seconds','upload_reserve_seconds'))<=e['job_cap_seconds'],'budget arithmetic')
    require(d['caps']==dict(starting_life=20,actions_per_game=12000,turns_per_game=60,actions_per_turn=500),'game caps')
    require(set(d['prohibited'])=={'rerun','retry','reroll','replacement','reorder','selective_salvage','refill','replication_of_replication','outcome_shopping','early_performance_stop'} and all(v is True for v in d['prohibited'].values()),'no shopping or repeat')
    require(set(d['evidence'])=={'create_new','attempt_before_initialization','initialization_entry_before_initialization','actual_initialization_separate','intent_before_submission','result_after_submission','exact_one_submission','raw_and_events','whole_inventory','always_upload','preserve_failures'} and all(v is True for v in d['evidence'].values()),'durable evidence')
    require(d['gates']==['DESIGN','SEEDFREE_MACHINERY','FRESH_FREEZE_AUTHORITY_AND_ORIGINAL_AUDIT','EXACT_SOURCE_AND_ACTIVATION','PUBLICATION_AND_REF','FRESH_DISPATCH_AUTHORITY','INDEPENDENT_RESULT_ADOPTION'],'separate gates')
    require(d['failure']==dict(preclaim='PRESERVE_CONSUMED_DISPATCH_STOP',ambiguous_claim='READ_ONLY_RECONCILE_NO_MUTATION_RETRY',postclaim='REJECT_BLOCK_PRESERVE_PREFIX_RETIRE_VECTOR_STOP',legitimate_loss_or_draw='RECORD_CONTINUE_FIXED_ORDER',failed_qualification='PRESERVE_CLASSIFY_JUSTIFIED_SUCCESSOR_ONLY'),'failure semantics')
    require(d['stopping']==dict(complete_accepted_twelve='CLOSE_REGARDLESS_OF_RECORD',defect='STOP_INDEPENDENT_CLASSIFICATION_NO_AUTOMATIC_REPLACEMENT',sample_extension=False),'fixed stopping')
    # Unknown top-level sections cannot hide an allocation/authority extension.
    require(set(d)=={'schema','status','current_authority','baseline','prior_smoke','sample','future_bindings','freeze','execution','caps','prohibited','evidence','gates','failure','stopping'},'closed top-level contract')

def main():
    root=Path(__file__).resolve().parents[3]
    raw=(root/CONTRACT).read_bytes(); d=json.loads(raw); validate(d)
    spec=(root/SPEC).read_bytes()
    require(b'6046397725' in spec and b'DESIGN AND STATIC QUALIFICATION ONLY' in spec,'spec scope binding')
    cases=[]
    def negative(label, mutate):
        changed=copy.deepcopy(d); mutate(changed)
        try:
            validate(changed)
        except (Rejected,KeyError,TypeError) as error:
            cases.append({'case':label,'result':'REJECTED','reason':str(error)})
        else:
            raise AssertionError('unsafe contract accepted: '+label)
    def replace(section,key,value):
        return lambda x: x[section].__setitem__(key,value)
    for flag in d['current_authority']:
        negative('authority '+flag,replace('current_authority',flag,True))
    negative('allocation field populated',replace('sample','official_seeds',[]))
    negative('sixteen including smoke',replace('sample','games',16))
    negative('outcome-conditioned extension',replace('sample','outcome_conditioned',True))
    negative('pooled smoke',replace('prior_smoke','pooled',True))
    negative('suppressed loss',replace('prior_smoke','ordered_results',['W']*4))
    negative('different source',replace('baseline','source','0'*40))
    negative('invented workflow identity',replace('future_bindings','workflow','0'*40))
    negative('reordered slots',lambda x:x['sample']['cells'].reverse())
    negative('imbalanced seat',lambda x:x['sample']['cells'][0].__setitem__('pest_seat',1))
    negative('starting deck inconsistent',lambda x:x['sample']['cells'][0].__setitem__('starting_deck','MONSTER_TRON'))
    negative('second entropy draw',replace('freeze','entropy_calls',2))
    negative('partial entropy draw',replace('freeze','entropy_bytes',32))
    negative('missing retired R1',replace('freeze','minimum_exclusion_floor',570))
    for flag in ('quarantine_before_decode','complete_draw_rejection','complete_live_inventory_required','ambiguous_draw_consumed'):
        negative('freeze '+flag,replace('freeze',flag,False))
    negative('reuse smoke claim',replace('execution','claim_ref','refs/heads/pest-control/official-attempts/monster-tron-replacement-smoke-r1'))
    negative('second attempt',replace('execution','run_attempt',2))
    negative('validation executes',replace('execution','validation_ack_cannot_execute',False))
    negative('short budget reserve',replace('execution','preclaim_minimum_remaining_seconds',18000))
    for flag in d['prohibited']:
        negative('allow '+flag,replace('prohibited',flag,False))
    for flag in d['evidence']:
        negative('omit '+flag,replace('evidence',flag,False))
    negative('skip independent gate',lambda x:x['gates'].pop())
    negative('loss stops experiment',replace('failure','legitimate_loss_or_draw','STOP'))
    negative('win-conditioned closure',replace('stopping','complete_accepted_twelve','CLOSE_IF_WINNING'))
    negative('sample extension',replace('stopping','sample_extension',True))
    print(json.dumps({'scope':'STATIC_DESIGN_QUALIFICATION_ONLY_NOT_RUNTIME_AUTHORITY','contract_sha256':hashlib.sha256(raw).hexdigest(),'spec_sha256':hashlib.sha256(spec).hexdigest(),'positive_contract_passed':True,'negative_cases':cases,'negative_count':len(cases),'official_seed_allocations':0,'entropy_calls':0,'claims':0,'dispatches':0,'initializations':0,'actions':0,'outcomes':0},indent=2))

if __name__=='__main__':
    main()
