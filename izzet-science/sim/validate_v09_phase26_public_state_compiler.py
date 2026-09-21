#!/usr/bin/env python3
from public_action_compiler import *

def state(perms, **kw):
    return PublicState(
        phase=kw.get("phase","main"), step=kw.get("step","precombat"),
        active_player=kw.get("active_player","opponent"),
        priority_player=kw.get("priority_player","opponent"),
        permanents=tuple(perms), observed_cards=tuple(sorted(kw.get("observed_cards",()))),
        commander_damage_to_self=kw.get("commander_damage_to_self",0),
    )

def main():
    forest=PublicPermanent("forest","Forest","opponent",mana_colors=("G",),mana_amount=1)
    plains=PublicPermanent("plains","Plains","opponent",mana_colors=("W",),mana_amount=1)
    sick=PublicPermanent("elf","Llanowar Elves","opponent",summoning_sick=True,mana_colors=("G",),mana_amount=1,has_tap_cost=True)
    tapped=PublicPermanent("rock","Bonder's Ornament","opponent",tapped=True,mana_colors=("C",),mana_amount=1,has_tap_cost=True)
    guild=PublicPermanent("guild","Izzet Guildmage","self")
    protected=PublicPermanent("prot","Izzet Guildmage","self",hexproof=True)

    bite=ObservedActionSpec("bite","Bite Down","targeted_creature_control","sorcery",generic_cost=1,colored_cost=("G",),target_type="creature",target_controller="self")
    gift=ObservedActionSpec("gift","Generous Gift","targeted_noncreature_control","instant",generic_cost=2,colored_cost=("W",),target_controller="self")

    forest2=PublicPermanent("forest2","Forest","opponent",mana_colors=("G",),mana_amount=1)
    plains2=PublicPermanent("plains2","Plains","opponent",mana_colors=("W",),mana_amount=1)
    plains3=PublicPermanent("plains3","Plains","opponent",mana_colors=("W",),mana_amount=1)
    s=state([forest,forest2,plains,plains2,plains3,guild],observed_cards=("Bite Down","Generous Gift"))
    a=compile_public_actions(s,(bite,gift))
    assert any(x.action_id=="bite" and x.target_id=="guild" for x in a)
    assert any(x.action_id=="gift" and x.target_id=="guild" for x in a)

    # tapped and summoning-sick mana sources cannot be used.
    s2=state([sick,tapped,guild],observed_cards=("Bite Down",))
    assert compile_public_actions(s2,(bite,))==()

    # sorcery-speed action rejected outside own main phase.
    s3=state([forest,forest2,plains,guild],phase="combat",step="declare_attackers",observed_cards=("Bite Down",))
    assert compile_public_actions(s3,(bite,))==()

    # hexproof blocks opposing targeted action.
    s4=state([forest,forest2,plains,protected],observed_cards=("Bite Down",))
    assert compile_public_actions(s4,(bite,))==()

    # hidden/unobserved source contamination fails closed.
    try:
        compile_public_actions(state([forest,plains,guild]),(bite,))
        raise AssertionError("hidden source accepted")
    except ValueError:
        pass

    # deterministic replay equality.
    assert compile_public_actions(s,(gift,bite)) == compile_public_actions(s,(bite,gift))

    # commander-damage terminal state yields no further actions.
    s5=state([forest,forest2,plains,guild],observed_cards=("Bite Down",),commander_damage_to_self=16)
    assert compile_public_actions(s5,(bite,))==()

    print("V09_PHASE26_PUBLIC_STATE_COMPILER_VALIDATION_PASS")

if __name__=="__main__":
    main()
