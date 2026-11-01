from pathlib import Path
import json,hashlib,re,difflib,copy
sha=lambda b:hashlib.sha256(b).hexdigest()
card=Path('disciple-card-frozen.kt').read_text();test=Path('disciple-card-controls-isolated.kt').read_text();P=json.loads(Path('card-controls-protocol.json').read_text())
old='''            Effects.SelectTarget(
                TargetObject(filter = TargetFilter.CreatureYouControl.other()),
                storeAs = "discipleSacrifice"
            ),
'''
new='''            GatherCardsEffect(
                source = CardSource.BattlefieldMatching(
                    filter = GameObjectFilter.Creature,
                    player = Player.You,
                    excludeSelf = true
                ),
                storeAs = "discipleEligibleSacrifices"
            ),
            SelectFromCollectionEffect(
                from = "discipleEligibleSacrifices",
                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),
                storeSelected = "discipleSacrifice",
                prompt = "Choose another creature you control to sacrifice",
                useTargetingUI = true
            ),
'''
assert card.count(old)==1
candidate=card.replace(old,new).replace('import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter\n','').replace('import com.wingedsheep.sdk.scripting.targets.TargetObject\n','')
imports='''import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player
'''
candidate=candidate.replace('import com.wingedsheep.sdk.scripting.EntersTapped\n','import com.wingedsheep.sdk.scripting.EntersTapped\n'+imports)
# This is a proposal-only draft; no repository implementation path is updated.
transport=test.replace('ChooseTargetsDecision','com.wingedsheep.engine.core.SelectCardsDecision').replace('submitTargetSelection','submitCardSelection')
# Preserve the genuine triggered-target mutant response before the zero-event assertion.
transport=transport.replace('import com.wingedsheep.engine.core.com.wingedsheep.engine.core.SelectCardsDecision', 'import com.wingedsheep.engine.core.ChooseTargetsDecision')
transport=transport.replace('        if (d.pendingDecision is com.wingedsheep.engine.core.SelectCardsDecision) {\n            d.submitCardSelection(me, listOf(fodder)).error shouldBe null\n        }\n        d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0','        if (d.pendingDecision is ChooseTargetsDecision) {\n            d.submitTargetSelection(me, listOf(fodder)).error shouldBe null\n        }\n        d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0')
oldlines=test.splitlines();newlines=transport.splitlines();assert len(oldlines)==len(newlines)
edits=[dict(line=i+1,before=a,after=b) for i,(a,b) in enumerate(zip(oldlines,newlines)) if a!=b]
assert all(a.replace('ChooseTargetsDecision','com.wingedsheep.engine.core.SelectCardsDecision').replace('submitTargetSelection','submitCardSelection')==b for a,b in zip(oldlines,newlines) if a!=b)
assertions=lambda s:[l.strip() for l in s.splitlines() if re.search(r'\bshould[A-Z]|\.should[A-Z]',l)]
original_assertions=assertions(test);transport_assertions=assertions(transport)
changed_assertions=[dict(before=a,after=b) for a,b in zip(original_assertions,transport_assertions) if a!=b]
assert len(original_assertions)==len(transport_assertions)==401
extra='''
    test("DF12 non-targeted shroud selection survives serialization and preserves unchosen fodder") {
        val d = driver()
        val me = d.activePlayer!!
        d.registerCard(card("Disciple non-targeted shroud fixture") {
            typeLine = "Creature — Bear"; power = 5; toughness = 5
            keywords(Keyword.SHROUD, Keyword.HEXPROOF)
            keywordAbility(com.wingedsheep.sdk.scripting.KeywordAbility.Ward(com.wingedsheep.sdk.scripting.effects.WardCost.Mana("{2}")))
        })
        val chosen = d.putCreatureOnBattlefield(me, "Disciple non-targeted shroud fixture")
        val unchosen = d.putCreatureOnBattlefield(me, "Disciple non-targeted shroud fixture")
        cast(d)
        val hand = d.getHandSize(me)
        val start = d.events.size
        d.submitYesNo(me, true).error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.SelectCardsDecision>()
        roundtrip(d)
        d.submitCardSelection(me, listOf(chosen)).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(chosen) shouldBe true
        d.getPermanents(me).contains(unchosen) shouldBe true
        d.events.drop(start).filterIsInstance<BecomesTargetEvent>().size shouldBe 0
        d.events.drop(start).filterIsInstance<com.wingedsheep.engine.core.AbilityTriggeredEvent>().size shouldBe 0
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }

    test("DF12 non-targeted choice rejects source and opposing shroud while accepting own shroud") {
        val d = driver()
        val me = d.activePlayer!!
        val opp = d.getOpponent(me)
        d.registerCard(card("Disciple non-targeted foreign fixture") {
            typeLine = "Creature — Bear"; power = 5; toughness = 5
            keywords(Keyword.SHROUD, Keyword.HEXPROOF)
        })
        val own = d.putCreatureOnBattlefield(me, "Disciple non-targeted foreign fixture")
        val other = d.putCreatureOnBattlefield(me, "Disciple test fodder")
        val foreign = d.putCreatureOnBattlefield(opp, "Disciple non-targeted foreign fixture")
        val source = cast(d)
        val hand = d.getHandSize(me)
        d.submitYesNo(me, true).error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<com.wingedsheep.engine.core.SelectCardsDecision>()
        d.submitCardSelection(me, listOf(source)).isSuccess shouldBe false
        d.submitCardSelection(me, listOf(foreign)).isSuccess shouldBe false
        d.getLifeTotal(me) shouldBe 20
        d.submitCardSelection(me, listOf(own)).error shouldBe null
        d.getLifeTotal(me) shouldBe 25
        d.getHandSize(me) shouldBe hand + 5
        d.getGraveyard(me).contains(own) shouldBe true
        d.getPermanents(me).contains(source) shouldBe true
        d.getPermanents(me).contains(other) shouldBe true
        d.getPermanents(opp).contains(foreign) shouldBe true
        d.pendingDecision shouldBe null
        d.state.stack.size shouldBe 0
    }
'''
assert transport.endswith('})\n');candidate_test=transport[:-3]+extra+'})\n'
assertions_extra=len(assertions(candidate_test))-401
newcases=re.findall(r'test\("([^"\n]+)"\)',extra)
mutations=copy.deepcopy(P['mutations'])
for m in mutations:
 if m['id']=='allow_self':m['edits']=[dict(before='excludeSelf = true',after='excludeSelf = false')]
 if m['id']=='allow_opponent':m['edits'][0]=dict(before='player = Player.You',after='player = Player.Each')
 if m['id']=='wrong_back_collector':
  prefix=candidate.split('val DiscipleOfFreyalise:')[0]
  m['edits']=[dict(before=prefix,after=prefix.replace('collectorNumber = \"250\"','collectorNumber = \"251\"'))]
 if m['id']=='triggered_target':
  m['edits'][0]['after']='        trigger = Triggers.EntersBattlefield\n        target = com.wingedsheep.sdk.scripting.targets.TargetObject(filter = com.wingedsheep.sdk.scripting.filters.unified.TargetFilter.CreatureYouControl.other())'
  m['edits'][1]=dict(before=new,after='')
 # Original test lines are unchanged in position; freeze the exact successor source.
 s=candidate
 for e in m['edits']:
  assert s.count(e['before'])==1,(m['id'],e['before']);s=s.replace(e['before'],e['after'])
 m['source_sha256']=sha(s.encode())
 for w in m['witnesses']:assert candidate_test.splitlines()[w['line']-1].strip()==w['assertion']
oldqualified=old.replace('TargetObject(', 'com.wingedsheep.sdk.scripting.targets.TargetObject(').replace('TargetFilter.', 'com.wingedsheep.sdk.scripting.filters.unified.TargetFilter.')
old_selector=candidate.replace(new,oldqualified)
mutations.append(dict(id='targeting_choice_regression',requirements=['DF12'],path=P['paths'][-1],edits=[dict(before=new,after=oldqualified)],source_sha256=sha(old_selector.encode()),witnesses=[dict(case='DF12 shroud hexproof ward fodder sacrifices without tax or intermediate priority',line=1055,assertion='d.getLifeTotal(me) shouldBe 25')],unaffected_controls=P['mutations'][0]['unaffected_controls']))
for m in mutations:
 assert m['source_sha256']!=sha(candidate.encode())
report=dict(state='PROPOSAL_ONLY_NOT_APPLIED_NOT_COMPILED',base_source='a15c7859ea1e0a3155168338c8e465b975bc6a19',test=P['test'],original_test_sha256=sha(test.encode()),transport_only_sha256=sha(transport.encode()),candidate_test_sha256=sha(candidate_test.encode()),original_card_sha256=sha(card.encode()),candidate_card_sha256=sha(candidate.encode()),exact_line_replacements=edits,original_assertion_lines=401,changed_transport_assertion_lines=len(changed_assertions),unchanged_assertion_lines=401-len(changed_assertions),changed_transport_assertions=changed_assertions,preserved_case_identities=P['case_names'],new_case_identities=newcases,original_case_count=65,candidate_case_count=67,new_assertion_lines=assertions_extra,total_assertion_lines=401+assertions_extra,mutations=mutations,forms=['normal']+[m['id'] for m in mutations],required_witnesses=sum(len(m['witnesses']) for m in mutations),required_unaffected_controls=sum(len(m['unaffected_controls']) for m in mutations),registration_cases=P['registration_cases'],runtime_sdk_server_client_changes=False,other_assertion_relaxation=False)
Path('DiscipleOfFreyalise.non-targeted.proposal.kt').write_text(candidate)
Path('DiscipleOfFreyaliseScenarioTest.non-targeted.proposal.kt').write_text(candidate_test)
Path('disciple-selection-exception-review.json').write_text(json.dumps(report,indent=2)+'\n')
Path('disciple-selection-exception.patch').write_text(''.join(difflib.unified_diff(card.splitlines(True),candidate.splitlines(True),fromfile=P['paths'][-1],tofile=P['paths'][-1]))+'\n'+''.join(difflib.unified_diff(test.splitlines(True),candidate_test.splitlines(True),fromfile=P['test'],tofile=P['test'])))
print(json.dumps({k:report[k] for k in ['state','original_assertion_lines','changed_transport_assertion_lines','unchanged_assertion_lines','original_case_count','candidate_case_count','new_assertion_lines','total_assertion_lines','required_witnesses','required_unaffected_controls']}));print('exact line replacements',len(edits))
