#!/usr/bin/env python3
from pathlib import Path
from public_action_compiler import PublicPermanent, PublicState, ObservedActionSpec
from observed_action_behavior import PublicBehaviorContext
from public_integration import load_phase25_surface_map, integrate_public_fixture

ROOT=Path(__file__).resolve().parents[1]
SURFACE=ROOT/"opponents"/"veteran-beastrider-commander-clash-2025.action-surfaces.json"

def S(perms, observed=(), phase="main", step="precombat", active="opponent", priority="opponent"):
    return PublicState(phase,step,active,priority,tuple(perms),tuple(sorted(observed)),0)

def main():
    m=load_phase25_surface_map(SURFACE)
    guild=PublicPermanent("guild","Izzet Guildmage","self")
    forest=PublicPermanent("forest","Forest","opponent",mana_colors=("G",),mana_amount=1)
    forest2=PublicPermanent("forest2","Forest","opponent",mana_colors=("G",),mana_amount=1)
    plains=PublicPermanent("plains","Plains","opponent",mana_colors=("W",),mana_amount=1)
    sick=PublicPermanent("elf","Llanowar Elves","opponent",summoning_sick=True,mana_colors=("G",),mana_amount=1,has_tap_cost=True)
    tapped=PublicPermanent("orn","Ornithopter of Paradise","opponent",tapped=True,mana_colors=("G","W"),mana_amount=1,has_tap_cost=True)

    bite=ObservedActionSpec("bite","Bite Down","targeted_creature_control","sorcery",1,("G",),"creature","self","G")
    gift=ObservedActionSpec("gift","Generous Gift","targeted_noncreature_control","instant",2,("W",),"permanent","self","W")
    ramp=ObservedActionSpec("ramp","Bonder's Ornament","battlefield_mana_source","activated",0,(),None,None,None)
    trample=ObservedActionSpec("trample","Wose Pathfinder","commander_trample","activated",0,(),None,None,None)

    ctx=PublicBehaviorContext("guild",(),(),0,16)

    # Observed Guildmage removal compiles and wins behavior precedence.
    st=S([forest,forest2,plains,guild],("Bite Down","Generous Gift"))
    r=integrate_public_fixture(m,st,(gift,bite),ctx)
    assert r.decision.selected_action_id=="bite"
    assert any(a.action_id=="gift" for a in r.compiled)

    # Order-invariance and deterministic replay.
    r2=integrate_public_fixture(m,st,(bite,gift),ctx)
    assert r==r2
    assert r==integrate_public_fixture(m,st,(gift,bite),ctx)

    # Public commander-pressure development is accepted when observed.
    st2=S([forest,guild],("Wose Pathfinder",))
    rc=integrate_public_fixture(m,st2,(trample,),PublicBehaviorContext("guild",(),(),15,16))
    assert rc.decision.selected_action_id=="trample"

    # Public mana development surface accepted when observed.
    st3=S([forest,guild],("Bonder's Ornament",))
    rm=integrate_public_fixture(m,st3,(ramp,),ctx)
    assert rm.decision.selected_action_id=="ramp"

    # Timing invalid sorcery rejected.
    st4=S([forest,forest2,guild],("Bite Down",),phase="combat",step="declare_attackers")
    assert integrate_public_fixture(m,st4,(bite,),ctx).decision.action=="pass"

    # Tapped/summoning-sick public mana cannot pay.
    st5=S([sick,tapped,guild],("Bite Down",))
    assert integrate_public_fixture(m,st5,(bite,),ctx).decision.action=="pass"

    # Unobserved source fails closed at compiler boundary.
    try:
        integrate_public_fixture(m,S([forest,forest2,guild]),(bite,),ctx)
        raise AssertionError("unobserved source accepted")
    except ValueError:
        pass

    # Invalid Phase-25 source/surface pairing fails closed.
    bad=ObservedActionSpec("bad","Bite Down","battlefield_mana_source","sorcery",1,("G",))
    try:
        integrate_public_fixture(m,S([forest,forest2,guild],("Bite Down",)),(bad,),ctx)
        raise AssertionError("invalid surface pairing accepted")
    except ValueError:
        pass

    # Empty observed action set passes.
    assert integrate_public_fixture(m,S([guild]),(),ctx).decision.action=="pass"

    print("V09_PHASE28_PUBLIC_INTEGRATION_VALIDATION_PASS")

if __name__=="__main__":
    main()
