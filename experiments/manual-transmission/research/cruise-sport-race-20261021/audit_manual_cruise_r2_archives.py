#!/usr/bin/env python3
"""CRUISE R2: deterministic, read-only forensic audit of original ten game archives.

Original decisions/actions are read, never re-executed. In particular, this
script cannot independently reconstruct legal mana payments or establish that a
passed commander was a strategic error. The original gameplay harness captured
legal-offer COUNTS at runtime, but not exact indexed legal opportunities. Only
AI scored-offers can be mapped to exact action indices. Preserve the distinction.
"""
import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import statistics
import zipfile

COMMANDER = 'Animar, Soul of Elements'
PROFILE = 'manual-transmission-r5-survival-opt-in'
LAND_TYPES = ('Land',)
ONE_MANA_RAMP = {'Arbor Elf','Llanowar Elves','Elvish Mystic','Fyndhorn Elves','Birds of Paradise','Delighted Halfling'}
MANA_DORKS = ONE_MANA_RAMP | {'Paradise Druid','Sakura-Tribe Elder','Gwenna, Eyes of Gaea'}
SEATS = tuple(f'manual-mirror-{i}' for i in range(4))
DECK_HASH = {'control':'2a9f47d2edcfbdb54a9ea15eadc719f85b36f74da9eb682233b85bd56f034da1',
             'a':'5bfa4467962b105c70408f555c72b2908423342debb8e454acd0419c7b0a5fed'}


def require(cond, why):
    if not cond: raise AssertionError(why)


def sha256(b):
    return hashlib.sha256(b).hexdigest()


def jsonlines(raw):
    return [json.loads(line) for line in raw.decode('utf-8').splitlines() if line.strip()]


def open_exact(z, suffix, prefix):
    # Never use a vague basename: smoke (-prefix) is a distinct 64-action run.
    name = prefix + suffix
    require(z.namelist().count(name) == 1, 'Exact original ZIP member unavailable: '+name)
    return z.read(name)


def initial_hands(state):
    zones = state['zones']; entities = state['entities']
    require(len(zones) % 2 == 0, 'zones must be key/list pairs')
    def card(cid):
        components = entities[cid]
        matches = [v for k,v in components.items() if k.endswith('.CardComponent')]
        require(len(matches)==1, 'card component missing or duplicate: '+cid)
        return matches[0]
    out = {}
    for i in range(0,len(zones),2):
        key = zones[i]; ids = zones[i+1]
        if key.get('zoneType')=='Hand' and key.get('ownerId') in SEATS:
            seat = key['ownerId']
            require(seat not in out, 'duplicate hand zone: '+seat)
            cards = [card(cid) for cid in ids]
            require(len(cards)==7, 'Historical harness must keep exact starting seven: '+seat)
            out[seat] = {
              'cards':[c['name'] for c in cards],
              'land_count':sum('Land' in c['typeLine'] for c in cards),
              'land_cards':[c['name'] for c in cards if 'Land' in c['typeLine']],
              'one_mana_dorks':[c['name'] for c in cards if c['name'] in ONE_MANA_RAMP],
              'ramp_cards':[c['name'] for c in cards if c['name'] in MANA_DORKS],
              'land_capable_mdfc':[c['name'] for c in cards if ' // ' in c['name'] and 'Disciple' in c['name']],
            }
    require(set(out)==set(SEATS), 'Not all four opening hands present')
    return out


def handle_artifact(spec, root):
    p = root/spec['file']
    require(p.is_file(), 'missing original ZIP '+str(p))
    raw = p.read_bytes()
    require(sha256(raw)==spec['sha256'], 'Original ZIP digest mismatch: '+spec['file'])
    cohort=spec['cohort'].lower(); arm=spec['arm']; seed=spec['seed']
    prefix=f'precision-{cohort}-'+('stage1-output/' if cohort=='r1' else 'prospective-output/')+f'{arm}-seed-{seed}/'
    outer=f'precision-{cohort}-'+('stage1-output/' if cohort=='r1' else 'prospective-output/')
    with zipfile.ZipFile(p) as z:
        def item(s):return open_exact(z,s,prefix)
        require(open_exact(z,'source-sha.txt',outer).decode().strip()==spec['source_sha'],
                'Original Actions source mismatch for '+spec['file'])
        summary=json.loads(item('summary.json'))
        init=json.loads(item('initialization.json'))
        binder=json.loads(item('r1-arm-binding.json'))
        state=json.loads(item('initial-state.json'))
        actions=jsonlines(item('actions.jsonl'))
        insights=jsonlines(item('decisions.jsonl'))
        require(str(summary['seed'])==seed and summary['arm']==arm,'arm/seed mismatch')
        require(summary['profile']==PROFILE and binder['profile']==PROFILE,'R5 profile mismatch')
        require(summary['status']=='COMPLETE' and summary['gameOver'] is True and summary['winner'] in SEATS,
                'Do not treat censored/incomplete game as terminal')
        require(summary['error'] is None and summary['accepted']==summary['submitted'] and summary['accepted']>=64,
                'Original accepted-actions gate not met')
        require(summary['prefixOnly'] is False,'Smoke cannot be analyzed as full game')
        require(init['openingPolicy']=='Four seats keep seed-derived initial seven, no smoother or mulligans',
                'Historical no-mulligan seam differs')
        require(binder['matchedInitializerPreflightVerified'] is True and binder['changedSlots']==(0 if arm=='control' else 7),
                'Matched initialization binding mismatch')
        require(binder['deckSha256']==DECK_HASH[arm], 'Frozen hardware hash mismatch')
        require(len(actions)==2*summary['submitted'],'Original actions missing result records')
        submits, results=[],[]
        for i in range(summary['submitted']):
            a,b=actions[2*i],actions[2*i+1]
            require(a['record']=='submit' and b['record']=='result' and a['index']==i+1 and b['index']==i+1,
                    'Gap/duplicate/misalignment in actual action stream '+spec['file'])
            require(b['error'] is None,'Original rejection '+spec['file']+' index '+str(i+1))
            submits.append(a);results.append(b)
        require(results[-1]['gameOver'] is True and results[-1]['winner']==summary['winner'],
                'Final state disagrees with original result stream')
        hands = initial_hands(state)
        own_turns={seat:set() for seat in SEATS}
        turn_owner={}
        for ins in insights:
            require(ins['playerId'] in SEATS,'Unexpected AI player')
            if ins['onOwnTurn']:
                own_turns[ins['playerId']].add(ins['turnNumber'])
                owner=turn_owner.setdefault(ins['turnNumber'],ins['playerId'])
                require(owner==ins['playerId'],'Multiple active actors for same global turn')
        for seat in SEATS:
            require(len(own_turns[seat])==summary['turnsByPlayer'][seat],
                    'Unmapped personal turn: '+spec['file']+'/'+seat)
        pt={seat:{turn:i+1 for i,turn in enumerate(sorted(own_turns[seat]))} for seat in SEATS}
        # Insight stream contains only decisions where AI emitted a scored insight.
        # Trace actions include thousands of simple passes that emitted no insight.
        # Match EVERY original PRIORITY insight to its actual submitted action by
        # order, actor, turn, step, and byte-independent action object equality.
        priority=[x for x in submits if x['pendingDecision'] is None]
        cursor=0; matched=0; opportunities=collections.defaultdict(list)
        for insight in insights:
            if insight['kind']!='PRIORITY':continue
            chosen=[o for o in insight['options'] if o.get('chosen') is True]
            require(len(chosen)==1,'Ambiguous chosen option in source decision log')
            chosen=chosen[0]
            match=None
            for k in range(cursor,len(priority)):
                action=priority[k]
                if action['turn']>insight['turnNumber']+1: break
                if (action['actor']==insight['playerId'] and action['turn']==insight['turnNumber']
                    and action['step']==insight['step'] and json.loads(action['action'])==chosen['action']):
                    match=action; cursor=k+1; break
            require(match is not None,
                    'Could not pair actual action with insight '+spec['file']+'/'+str(matched))
            matched+=1
            offered=[o for o in insight['options'] if o.get('actionType')=='CastSpell'
                     and o.get('cardName')==COMMANDER]
            if offered:
                require(insight['onOwnTurn'] is True and insight['step'] in ('PRECOMBAT_MAIN','POSTCOMBAT_MAIN'),
                        'Out-of-main-phase scored commander offered')
                s=insight['playerId']; gt=insight['turnNumber']
                opportunities[s].append({
                  'action_index':match['index'],'global_turn':gt,'personal_turn':pt[s][gt],
                  'selected_action':insight['chosenLabel'],
                  'selected_type':chosen['actionType'],
                  'selected_commander':chosen.get('cardName')==COMMANDER and chosen['actionType']=='CastSpell',
                  'selected_score_minus_commander':round(chosen['score']-max(o['score'] for o in offered),6),
                  'offered_choices':len(insight['options']),
                  'source':'ORIGINAL_R5_SCORING_NOT_INDEPENDENT_LEGAL_REPLAY'
                })
        expected_priority=sum(ins.get('kind')=='PRIORITY' for ins in insights)
        require(matched==expected_priority,'Missing original matched decisions')
        for seat in SEATS:
            require(len(opportunities[seat])==summary['scoredCommanderOpportunityDecisions'][seat],
                    'Scored opportunity count mismatch with original game harness')
            require(sum(not x['selected_commander'] for x in opportunities[seat])==summary['scoredCommanderDeclinedDecisions'][seat],
                    'Scored decline count mismatch with original game harness')
            require(summary['legalCommanderDeclinedDecisions'][seat]<=summary['legalCommanderOpportunityDecisions'][seat],
                    'Original legal-count inconsistent')
        casts=collections.defaultdict(list)
        for a,b in zip(submits,results):
            action=json.loads(a['action'])
            if action['type']=='CastSpell' and a.get('sourceName')==COMMANDER:
                # The original action was accepted and was recorded as a SpellCastEvent.
                require(any(COMMANDER in e and 'SpellCastEvent(' in e for e in b['events']),
                        'Commander action lacks real SpellCastEvent')
                seat=a['actor']; gt=a['turn']
                casts[seat].append({'action_index':a['index'],'global_turn':gt,'personal_turn':pt[seat][gt]})
        seats=[]
        for seat in SEATS:
            last=casts[seat]; o=opportunities[seat]
            require(len(last)==summary['commanderCasts'][seat], 'Actual command cast count mismatch')
            if last:
                require(last[0]['personal_turn']==summary['firstCommanderPersonalTurn'][seat],
                        'Original first commander personal turn mismatch')
                require(last[0]['global_turn']==summary['firstCommanderGlobalTurn'][seat],
                        'Original first commander global turn mismatch')
            else:
                require(seat not in summary['firstCommanderPersonalTurn'],
                        'Original summary falsely reports cast')
            first=o[0] if o else None
            deferred=last[0]['personal_turn']-first['personal_turn'] if first and last else None
            require(deferred is None or deferred>=0,'Original recorded commander appears before first scored offer')
            seats.append({
              'cohort':spec['cohort'],'seed':seed,'arm':arm,'seat':seat,'opening':hands[seat],
              'total_personal_turns':summary['turnsByPlayer'][seat],
              'first_scored_offer':first,'scored_offer_decisions':len(o),
              'scored_decline_decisions':sum(not x['selected_commander'] for x in o),
              'scored_unique_personal_turns':len(set(x['personal_turn'] for x in o)),
              'original_legal_offer_decisions_count':summary['legalCommanderOpportunityDecisions'][seat],
              'original_legal_declined_decisions_count':summary['legalCommanderDeclinedDecisions'][seat],
              'first_accepted_commander_cast':last[0] if last else None,
              'commander_casts':len(last),
              'personal_turn_deferment_from_first_scored_offer':deferred,
              'scored_vs_legal_count_equal':len(o)==summary['legalCommanderOpportunityDecisions'][seat],
              'opportunities':o,
            })
        return {
          'cohort':spec['cohort'],'seed':seed,'arm':arm,'artifact_id':spec['id'],
          'artifact_sha256':spec['sha256'],'source_sha':spec['source_sha'],
          'status':summary['status'],'winner':summary['winner'],'accepted':summary['accepted'],
          'rejected':0,'historical_scored_choice_records':summary['scoredChoiceRecords'],
          'insight_records':len(insights),'matched_original_priority_insights':matched,
          'historical_legal_offer_count':sum(summary['legalCommanderOpportunityDecisions'].values()),
          'indexed_scored_offer_count':sum(len(opportunities[s]) for s in SEATS),
          'seats':seats
        }


def aggregate(manifest, runs):
    seats=[s for run in runs for s in run['seats']]
    require(len(runs)==10 and len(seats)==40,'Ten games/four players each required')
    by={(r['seed'],r['arm']):r for r in runs}
    require(set(by)=={(s,a) for s in manifest['seeds'] for a in manifest['arms']},
            'Matched seed pair missing, duplicated or unexpected')
    pairs=[]
    for seed in manifest['seeds']:
        control=by[(seed,'control')]; candidate=by[(seed,'a')]
        byseat={s['seat']:s for s in control['seats']}; byseat_a={s['seat']:s for s in candidate['seats']}
        deltas=[]
        for seat in SEATS:
            c=byseat[seat];a=byseat_a[seat]
            cc=c['first_accepted_commander_cast']; ac=a['first_accepted_commander_cast']
            deltas.append({'seat':seat,
                'control_opening_lands':c['opening']['land_count'],'a_opening_lands':a['opening']['land_count'],
                'control_first_scored_pt':c['first_scored_offer']['personal_turn'] if c['first_scored_offer'] else None,
                'a_first_scored_pt':a['first_scored_offer']['personal_turn'] if a['first_scored_offer'] else None,
                'control_first_cast_pt':cc['personal_turn'] if cc else None,
                'a_first_cast_pt':ac['personal_turn'] if ac else None,
                'cast_turn_delta_a_minus_control':ac['personal_turn']-cc['personal_turn'] if cc and ac else None})
        pairs.append({'seed':seed,'control_winner':control['winner'],'a_winner':candidate['winner'],
                      'same_winner':control['winner']==candidate['winner'], 'seat_matches':deltas})
    withcast=[s['first_accepted_commander_cast']['personal_turn'] for s in seats if s['first_accepted_commander_cast']]
    scored=[s['first_scored_offer']['personal_turn'] for s in seats if s['first_scored_offer']]
    deferred=[s['personal_turn_deferment_from_first_scored_offer'] for s in seats if s['personal_turn_deferment_from_first_scored_offer'] is not None]
    byarm={}
    for arm in ['control','a']:
        sc=[s for s in seats if s['arm']==arm]
        first=[s['first_accepted_commander_cast']['personal_turn'] for s in sc if s['first_accepted_commander_cast']]
        byarm[arm]={'seats':len(sc),'first_casts_observed':len(first),
                    'median_first_cast_personal_turn_casters_only':statistics.median(first) if first else None,
                    'no_commander_cast_seats':len(sc)-len(first),
                    'opening_land_count_distribution':dict(sorted(collections.Counter(s['opening']['land_count'] for s in sc).items())),
                    'scored_offers':sum(s['scored_offer_decisions'] for s in sc),
                    'original_legal_offer_decisions':sum(s['original_legal_offer_decisions_count'] for s in sc)}
    totals={
        'source_archives_validated':len(runs),'original_completed_full_games':len(runs),
        'original_accepted_actions':sum(r['accepted'] for r in runs),
        'new_full_games':0,'original_rejected_actions':sum(r['rejected'] for r in runs),
        'actual_seat_observations':len(seats),
        'observed_commander_first_casts':len(withcast),
        'never_cast_before_game_end':len(seats)-len(withcast),
        'observed_first_scored_offers':len(scored),
        'first_scored_offer_immediately_chosen':sum(s['first_scored_offer']['selected_commander'] for s in seats if s['first_scored_offer']),
        'median_first_scored_offer_personal_turn':statistics.median(scored) if scored else None,
        'median_first_cast_personal_turn_among_casts':statistics.median(withcast) if withcast else None,
        'median_deferment_personal_turns_among_casters':statistics.median(deferred) if deferred else None,
        'caster_seats_deferred_at_least_one_personal_turn':sum(d>0 for d in deferred),
        'original_legal_offer_decisions':sum(r['historical_legal_offer_count'] for r in runs),
        'indexed_scored_offer_decisions':sum(r['indexed_scored_offer_count'] for r in runs),
        'original_scored_priority_decisions_matched':sum(r['matched_original_priority_insights'] for r in runs),
        'scored_vs_legal_mismatching_game_arms':[
          {'seed':r['seed'],'arm':r['arm'],'scored':r['indexed_scored_offer_count'],'legal':r['historical_legal_offer_count']}
          for r in runs if r['indexed_scored_offer_count']!=r['historical_legal_offer_count']],
        'arm':byarm,
    }
    return {'schema':'MANUAL_CRUISE_R2_ARCHIVED_STATE_FORENSICS_REPORT_V1',
            'disposition':'DESCRIPTIVE_ARCHIVE_FORENSICS_ONLY_NO_POLICY_CHANGE',
            'source':'Original immutable gameplay Action/AI insight/initializer traces, not new games',
            'method':'Complete pinned 10 ZIP archives. Exact 2x submit/result stream and all PRIORITY chosen actions matched in order.',
            'limitation':'AI scored offers are indexed; original legal enumerator offers are aggregate counts only. No independent mana/action replay. Not causal/win-rate proof.',
            'official_precision_budget':manifest['official_precision_budget'],
            'totals':totals,'pairs':pairs,'games':runs}


def report_md(result):
    t=result['totals'];seats=[s for r in result['games'] for s in r['seats']]
    out=['# Manual Transmission — CRUISE R2 archival opening/Animar forensics',
         '','**Disposition:** `DESCRIPTIVE_ARCHIVE_FORENSICS_ONLY_NO_POLICY_CHANGE`. Original complete artifacts, not new games.',
         '**Method:** SHA-256-verified 10 original R1/R6 ZIP archives, same five paired seeds; all original action submissions, results and priority insights examined.',
         '**Integrity:** Original accepted actions and source commits enforced; no unapproved main/CURRENT/deck/R5 writes.',
         '', '## Quantitative audit',
         f'- Original full games: **{t["original_completed_full_games"]}** across **5 matched seed pairs**; **{t["original_accepted_actions"]:,}** accepted actions, **{t["original_rejected_actions"]}** rejections.',
         f'- **{t["actual_seat_observations"]}** seat-game observations; **{t["observed_first_scored_offers"]}** have an archived AI-scored Animar cast option.',
         f'- Commander cast at least once in **{t["observed_commander_first_casts"]}** / 40 seats; **{t["never_cast_before_game_end"]}** never cast before their game ended.',
         f'- Median **first scored Animar option**: personal turn **{t["median_first_scored_offer_personal_turn"]}**; median **first actual cast among 35 casters**: personal turn **{t["median_first_cast_personal_turn_among_casts"]}**.',
         f'- At the **first scored offer**, Animar was selected in **{t["first_scored_offer_immediately_chosen"]} / 40** seat-game observations. This is not 38 proven mistakes: an alternative cast, land play, or response may be superior.',
         f'- For seats that eventually cast, median deferment from first scored offer: **{t["median_deferment_personal_turns_among_casters"]} personal turns**; **{t["caster_seats_deferred_at_least_one_personal_turn"]}** cast later than the first scored personal turn.',
         f'- The original harness counted **{t["original_legal_offer_decisions"]}** legal own-main commander options; the AI insight stream contains **{t["indexed_scored_offer_decisions"]}** scored options. Counts are NOT automatically interchangeable.',
         f'- Original AI priority insights mapped to exact submitted action indices: **{t["original_scored_priority_decisions_matched"]} / {t["original_scored_priority_decisions_matched"]}** (no gaps in PRIORITY matches).',
         '', '### Matched hardware — descriptive',
         '| Arm | Seat observations | First casts | Median cast turn (casters only) | Scored offers | Original legal options |',
         '|---|---:|---:|---:|---:|---:|']
    for arm,label in [('control','Frozen v0.7'),('a','Challenger A')]:
        a=t['arm'][arm]; out.append(f'| {label} | {a["seats"]} | {a["first_casts_observed"]} | {a["median_first_cast_personal_turn_casters_only"]} | {a["scored_offers"]} | {a["original_legal_offer_decisions"]} |')
    out += ['', '### Five matched seed pairs',
      '| Seed | CONTROL winner | A winner | Same winner? | Cast-turn differences across seats (A−CONTROL) |',
      '|---|---|---|---|---|']
    for p in result['pairs']:
        d=', '.join('S'+x['seat'].split('-')[-1]+':'+(f'{x["cast_turn_delta_a_minus_control"]:+d}' if x['cast_turn_delta_a_minus_control'] is not None else 'NA') for x in p['seat_matches'])
        out.append(f'| {p["seed"]} | {p["control_winner"]} | {p["a_winner"]} | {p["same_winner"]} | {d} |')
    if t['scored_vs_legal_mismatching_game_arms']:
        out+=['','### Instrumentation discrepancy — HOLD',
         'One original game arm has different counts for AI-scored Animar options and independent on-turn legal-enumerator offers. It is incorrect to call every scored option a separately verified legal command-zone opportunity.']
        for mismatch in t['scored_vs_legal_mismatching_game_arms']:
            out.append(f'- Seed {mismatch["seed"]}, {mismatch["arm"]}: scored={mismatch["scored"]}, source-counted legal={mismatch["legal"]}. An exact archived-state legality replay would be needed to explain the difference.')
    out+=['','## All 40 original opening hands and first scored/cast observations',
          'Opening land counts use the serialized printed card type line; **MDFC land capability is not counted as a land**. These are actual keep-seven hands, not controller mulligan outcomes.',
          '', '| Seed | Arm | Seat | Initial lands | Ramp in hand | First scored (turn/action) | Chosen instead | First actual cast PT |',
          '|---|---|---:|---:|---|---|---|---:|']
    for s in seats:
        o=s['first_scored_offer']; c=s['first_accepted_commander_cast'];se=s['seat'].split('-')[-1]
        first=f'PT{o["personal_turn"]} / #{o["action_index"]}' if o else '—'
        chosen=o['selected_action'].replace('|','/') if o else '—'
        if len(chosen)>47:chosen=chosen[:44]+'…'
        out.append(f'| {s["seed"]} | {s["arm"]} | {se} | {s["opening"]["land_count"]} | {", ".join(s["opening"]["ramp_cards"]) or "—"} | {first} | {chosen} | {c["personal_turn"] if c else "—"} |')
    out+=['','## Decision boundary and next gate',
          '- **No proven R5 strategic mistake yet.** Passes and alternative plays often outscore Animar. An early legal/scored option alone cannot establish that casting it would be better after interactions.',
          '- **No causality assigned to colors, lands, mulligans or commander tax:** the historical harness skipped mulligans; full per-action mana state was not recorded. Early-card classifications are descriptive only.',
          '- The source-bound next test should independently reconstruct a SMALL, preselected set of high-delay first-opportunity checkpoints, validate legal mana and real opponent priority without scripted passes, and compare unchanged R5 to an isolated one-variable proposal. No full games.',
          '- A positive turn-count gap is **not** sufficient for a deck change, a promotion, or a claim that Challenger A wins more often. Sample is five paired seeds with four dependent mirror seats.',
          '- Original 10/12 game budget remains consumed; **2 full games reserved**, **0 new games used** in CRUISE R2.',
          '', 'The complete `observations.json`, `seat-observations.csv`, and `paired-seeds.json` accompany this report.']
    return '\n'.join(out)+'\n'


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--manifest',type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    manifest=json.loads(args.manifest.read_text())
    require(manifest['schema']=='MANUAL_CRUISE_R2_ARCHIVE_FORENSICS_V1', 'Invalid preregistered source manifest')
    require(len(manifest['archives'])==10,'Fixed 10 original arm archives needed')
    require(manifest['official_precision_budget']=={'consumed':10,'cap':12,'reserved':2,'new_games_allowed':0},
            'Unauthorized new full games')
    require({(x['seed'],x['arm']) for x in manifest['archives']} ==
            {(s,a) for s in manifest['seeds'] for a in manifest['arms']}, 'Unregistered arm/seed found')
    runs=[handle_artifact(x,args.archive_dir) for x in manifest['archives']]
    result=aggregate(manifest,runs)
    args.out.mkdir(parents=True,exist_ok=True)
    (args.out/'observations.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
    (args.out/'paired-seeds.json').write_text(json.dumps(result['pairs'],indent=2)+'\n')
    (args.out/'REPORT.md').write_text(report_md(result))
    seats=[seat for r in runs for seat in r['seats']]
    with (args.out/'seat-observations.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=['seed','arm','seat','opening_lands','opening_cards','opening_ramp',
                                           'first_scored_pt','first_scored_index','chosen_at_first_offer',
                                           'first_cast_pt','first_cast_index','deferred_personal_turns',
                                           'scored_options','legal_options_source_count','source_count_matches'])
        writer.writeheader()
        for s in seats:
            o=s['first_scored_offer'];c=s['first_accepted_commander_cast']
            writer.writerow({'seed':s['seed'],'arm':s['arm'],'seat':s['seat'],
                'opening_lands':s['opening']['land_count'],'opening_cards':'; '.join(s['opening']['cards']),
                'opening_ramp':'; '.join(s['opening']['ramp_cards']),
                'first_scored_pt':o['personal_turn'] if o else '',
                'first_scored_index':o['action_index'] if o else '',
                'chosen_at_first_offer':o['selected_action'] if o else '',
                'first_cast_pt':c['personal_turn'] if c else '',
                'first_cast_index':c['action_index'] if c else '',
                'deferred_personal_turns':s['personal_turn_deferment_from_first_scored_offer'],
                'scored_options':s['scored_offer_decisions'],
                'legal_options_source_count':s['original_legal_offer_decisions_count'],
                'source_count_matches':s['scored_vs_legal_count_equal']})
    t=result['totals']
    print('CRUISE_R2_AUDIT_PASS original_archives='+str(t['source_archives_validated'])+
          ' original_accepted='+str(t['original_accepted_actions'])+
          ' scored_priority_aligned='+str(t['original_scored_priority_decisions_matched'])+
          ' commander_seats_cast='+str(t['observed_commander_first_casts'])+
          ' zero_new_games=TRUE')
    print('CRUISE_R2_KEY_FIGURES='+json.dumps({k:t[k] for k in (
        'median_first_scored_offer_personal_turn','median_first_cast_personal_turn_among_casts',
        'first_scored_offer_immediately_chosen','caster_seats_deferred_at_least_one_personal_turn',
        'scored_vs_legal_mismatching_game_arms')},sort_keys=True))

if __name__=='__main__':main()
