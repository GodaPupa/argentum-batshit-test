"""Isolated successor draft: exact proposed fault forms, never production changes."""
SDK='mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/effects/ManaSpellRider.kt'
CAST='rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/spell/CastSpellHandler.kt'
STACK='rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/StackResolver.kt'
ZONE='rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/ZoneTransitionService.kt'
SERIAL='rules-engine/src/main/kotlin/com/wingedsheep/engine/core/Serialization.kt'
COMP='com.wingedsheep.engine.state.components.stack.CommanderManaEntryCountersComponent'
RIDER='com.wingedsheep.sdk.scripting.effects.ManaSpellRider.CommanderCastEntryCounters'
def replace(s,a,b):
 assert s.count(a)==1,(a,s.count(a))
 return s.replace(a,b)
def forms(normal):
 result={}
 def one(key,path,old,new):result[key]={path:replace(normal[path],old,new)}
 count='val count = commander.castsFromCommandZone'
 one('OP01-drop',CAST,count,'val count = 0')
 one('OP02-current-cast',CAST,count,'val count = commander.castsFromCommandZone - if (state.getEntity(action.cardId)?.get<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>()?.castFromZone == Zone.COMMAND) 1 else 0')
 one('OP03-owner',CAST,'commander.ownerId != action.playerId || ','')
 one('OP04-multiplicity',CAST,'count = previous + count, spellRef = spellRef','count = count, spellRef = spellRef')
 one('OP05-cast-zone',CAST,'state.logicalZone(action.cardId)?.zoneType != Zone.STACK','state.logicalZone(action.cardId)?.zoneType != Zone.STACK || state.getEntity(action.cardId)?.get<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>()?.castFromZone != Zone.COMMAND')
 aggregate='''            val (riderState, riderEvents) = EntersWithReplacements.placeEntryCounters(
                newState, spellId, CounterTypeFilter.PlusOnePlusOne, commanderManaRider.count,
                controllerId, cardComponent?.name ?: "", sourceState = state
            )
            newState = riderState
            counterEvents.addAll(riderEvents)'''
 one('OP06-per-unit',STACK,aggregate,'''            repeat(commanderManaRider.count) {
                val (riderState, riderEvents) = EntersWithReplacements.placeEntryCounters(
                    newState, spellId, CounterTypeFilter.PlusOnePlusOne, 1,
                    controllerId, cardComponent?.name ?: "", sourceState = state
                )
                newState = riderState
                counterEvents.addAll(riderEvents)
            }''')
 # Observe entry against the actual counter state captured before Palace placement, then
 # process that observation once. The returned permanent still has its counters. This
 # makes the original draw-decision assertion, rather than a missing-counter assertion,
 # witness the misplaced observation. Restricted to the temporary mutant source form.
 early=replace(normal[STACK],'        // Aggregate all consumed mana contributions before applying entry counter modifiers once.',
 '        val countersBeforePalace = newState.getEntity(spellId)?.get<CountersComponent>()\n        // Aggregate all consumed mana contributions before applying entry counter modifiers once.')
 early=replace(early,'        return newState to counterEvents\n    }', '''        if (commanderManaRider != null) {
            val observationState = newState.updateEntity(spellId) { c ->
                if (countersBeforePalace == null) c.without<CountersComponent>() else c.with(countersBeforePalace)
            }
            val detector = com.wingedsheep.engine.event.TriggerDetector(cardRegistry)
            val observedEvents = counterEvents.map { event ->
                if (event is ZoneChangeEvent) event.copy(
                    observedEntryTriggers = detector.detectTriggers(observationState, listOf(event))
                ) else event
            }
            return newState to observedEvents
        }
        return newState to counterEvents
    }''')
 result['OP07-order']={STACK:early}
 one('OP08-serializer',SERIAL,'        subclass(CommanderManaEntryCountersComponent::class)\n','')
 one('OP09-direct-exit',STACK,'ZoneMovementUtils.restoreBestowAfterZoneExit(restoredPrototype)\n                .without<'+COMP+'>()', 'ZoneMovementUtils.restoreBestowAfterZoneExit(restoredPrototype)')
 one('OP10-generic-exit',ZONE,'newState = newState.removeFromStack(entityId).updateEntity(entityId) { c ->\n                c.without<'+COMP+'>()\n            }','newState = newState.removeFromStack(entityId)')
 one('OP11-copy',STACK,'        var container = ComponentContainer.of(copiedCardComp, copiedSpellComp)', '        var container = ComponentContainer.of(copiedCardComp, copiedSpellComp)\n        sourceContainer.get<'+COMP+'>()?.let { container = container.with(it) }')
 one('OP12-consume',STACK,'.without<com.wingedsheep.engine.state.components.stack.GraveyardCastRiderComponent>()\n                .without<'+COMP+'>()', '.without<com.wingedsheep.engine.state.components.stack.GraveyardCastRiderComponent>()')
 one('OP13-ordinary',CAST,'for (rider in paymentResult.consumedRiders)', 'for (rider in paymentResult.consumedRiders + listOf('+RIDER+'))')
 one('OP14-unspent',CAST,'for (rider in paymentResult.consumedRiders)', 'for (rider in paymentResult.consumedRiders + (currentCastState.getEntity(action.playerId)?.get<com.wingedsheep.engine.state.components.player.ManaPoolComponent>()?.restrictedMana?.flatMap { it.riders } ?: emptyList()))')
 one('OP15-producer',CAST,count,'''val count = if (state.getBattlefield().any { state.getEntity(it)?.get<CardComponent>()?.name == "Opal Palace" }) commander.castsFromCommandZone else 0''')
 one('OP17-identity',STACK,'?.takeIf { it.spellRef == state.objectRef(spellId) &&','?.takeIf { true &&')
 one('OP18-prohibition',STACK,' &&\n            newState.addToZone(ZoneKey(controllerId, Zone.BATTLEFIELD), spellId).projectedState.canReceiveCounters(spellId)', '')
 return result
