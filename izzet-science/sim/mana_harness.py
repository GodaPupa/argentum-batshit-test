#!/usr/bin/env python3
"""Deterministic mana-development harness for Izzet Science?! v0.1.

Initial harness: parses the frozen markdown deck, validates 99 cards, samples
opening hands reproducibly, and exposes card semantics/regression checks.
Turn-policy execution is intentionally staged behind tests.
"""
from __future__ import annotations
import argparse, random, re
from pathlib import Path

SEED = 0x1A22E7001
LANDS = {
"Island","Mountain","Snow-Covered Island","Snow-Covered Mountain",
"Command Tower","Ash Barrens","Evolving Wilds",
"Terramorphic Expanse","Izzet Boilerworks","Volatile Fjord",
"Swiftwater Cliffs","Silverbluff Bridge","Lonely Sandbar","Forgotten Cave"
}
TAPPED_LANDS={"Izzet Boilerworks","Volatile Fjord","Swiftwater Cliffs",
"Silverbluff Bridge","Lonely Sandbar","Forgotten Cave"}
ROCKS={"Everflowing Chalice","Fellwar Stone","Mind Stone","Star Compass",
"Sky Diamond","Fire Diamond","Izzet Signet","Network Terminal",
"Prismatic Lens","Ur-Golem's Eye","Sisay's Ring"}
MANA_CREATURES={"Ornithopter of Paradise","Silver Myr","Iron Myr"}

def parse_deck(path: Path):
    cards=[]
    commander=None
    section=""
    for raw in path.read_text().splitlines():
        line=raw.strip()
        if line=="Commander": section="commander"; continue
        if line.startswith("## "): section=line; continue
        m=re.match(r"^(\d+) (.+)$",line)
        if not m: continue
        n,name=int(m.group(1)),m.group(2)
        if section=="commander": commander=name
        elif section.startswith("## "): cards += [name]*n
    if commander!="Izzet Guildmage": raise ValueError(f"commander={commander!r}")
    if len(cards)!=99: raise ValueError(f"main deck count={len(cards)}")
    return commander,cards

def opening_baseline(cards, samples=100000, seed=SEED):
    rng=random.Random(seed)
    buckets=[0,0,0,0,0]
    rock_counts=[0,0,0,0]
    for _ in range(samples):
        h=rng.sample(cards,7)
        lands=sum(c in LANDS for c in h)
        buckets[min(lands,4)]+=1
        rocks=sum(c in ROCKS for c in h)
        rock_counts[min(rocks,3)]+=1
    return buckets,rock_counts

def regressions():
    assert "Volatile Fjord" in TAPPED_LANDS
    assert "Command Tower" not in TAPPED_LANDS
    assert "Izzet Signet" in ROCKS
    assert "Ornithopter of Paradise" in MANA_CREATURES
    assert "Silver Myr" not in ROCKS
    # High Tide subtype model: basics plus Volatile Fjord only in frozen v0.1.
    island_subtype={"Island","Volatile Fjord"}
    assert "Swiftwater Cliffs" not in island_subtype
    return True



# --- Stateful mana primitives (turn-sequence v1) ---
MANA_COST={
"Everflowing Chalice":0,"Fellwar Stone":2,"Mind Stone":2,"Star Compass":2,
"Sky Diamond":2,"Fire Diamond":2,"Izzet Signet":2,"Network Terminal":3,
"Prismatic Lens":2,
"Ur-Golem's Eye":4,"Sisay's Ring":4,"Ornithopter of Paradise":2,
"Silver Myr":2,"Iron Myr":2,"Izzet Guildmage":2
}
def land_enters_tapped(card): return card in TAPPED_LANDS
def basic_land_kind(card):
    if card in {"Island","Snow-Covered Island"}: return "Island"
    if card in {"Mountain","Snow-Covered Mountain"}: return "Mountain"
    return None

def high_tide_island(card): return card in {"Island","Snow-Covered Island","Volatile Fjord"}
def is_snow_permanent(card):
    return card in {"Snow-Covered Island","Snow-Covered Mountain","Volatile Fjord"}
def rock_enters_tapped(card): return card in {"Star Compass","Sky Diamond","Fire Diamond"}
def creature_mana_ready(card, entered_turn, turn):
    return card in MANA_CREATURES and entered_turn < turn
def chalice_output(kicks): return max(0,kicks)
def signet_net_output(external_input=1):
    return {"U":1,"R":1,"input":external_input,"net":2-external_input}
def boilerworks_legal(lands_before_play): return lands_before_play >= 1

def state_regressions():
    assert land_enters_tapped("Volatile Fjord")
    assert not land_enters_tapped("Island")
    assert high_tide_island("Volatile Fjord")
    assert not high_tide_island("Swiftwater Cliffs")
    assert rock_enters_tapped("Sky Diamond")
    assert not rock_enters_tapped("Mind Stone")
    assert not creature_mana_ready("Silver Myr",2,2)
    assert creature_mana_ready("Silver Myr",2,3)
    assert chalice_output(2)==2
    assert signet_net_output()["net"]==1
    assert not boilerworks_legal(0)
    assert boilerworks_legal(1)
    return True



class DevState:
    def __init__(self, hand, snow_basics=False):
        self.hand=list(hand); self.battlefield=[]; self.turn=0; self.land_played=False
        self.commander_casts=0; self.commander_zone=True
        self.snow_basics=snow_basics
    def begin_turn(self, draw=None):
        self.turn+=1; self.land_played=False
        if draw is not None: self.hand.append(draw)
    def lands(self): return [x for x in self.battlefield if x["card"] in LANDS]
    def permanents(self): return [x["card"] for x in self.battlefield]
    def play_land(self, card):
        if self.land_played or card not in self.hand or card not in LANDS: return False
        if card=="Izzet Boilerworks" and not boilerworks_legal(len(self.lands())): return False
        self.hand.remove(card)
        if card=="Izzet Boilerworks":
            # deterministic development policy: return a tapped land if possible, else last land.
            lands=self.lands(); target=next((x for x in lands if x["tapped"]),lands[-1])
            self.battlefield.remove(target); self.hand.append(target["card"])
        self.battlefield.append({"card":card,"tapped":land_enters_tapped(card),"entered":self.turn})
        self.land_played=True; return True

def development_regressions():
    s=DevState(["Izzet Boilerworks","Island"])
    s.begin_turn()
    assert not s.play_land("Izzet Boilerworks")
    assert s.play_land("Island")
    s.begin_turn()
    assert s.play_land("Izzet Boilerworks")
    assert len(s.lands())==1 and s.lands()[0]["card"]=="Izzet Boilerworks"
    assert "Island" in s.hand
    t=DevState(["Volatile Fjord"]); t.begin_turn(); assert t.play_land("Volatile Fjord")
    assert t.lands()[0]["tapped"]
    return True



def land_colors(card, basics_controlled=None):
    basics_controlled=basics_controlled or set()
    if basic_land_kind(card)=="Island": return {"U"}
    if basic_land_kind(card)=="Mountain": return {"R"}
    if card=="Command Tower": return {"U","R"}
    if card in {"Volatile Fjord","Swiftwater Cliffs","Silverbluff Bridge"}: return {"U","R"}
    if card=="Lonely Sandbar": return {"U"}
    if card=="Forgotten Cave": return {"R"}
    if card=="Ash Barrens": return {"C"}
    if card=="Izzet Boilerworks": return {"UR"}
    return set()

def available_land_mana(state):
    basics={basic_land_kind(x["card"]) for x in state.lands() if basic_land_kind(x["card"])}
    out=[]
    for p in state.lands():
        if not p["tapped"]: out.append((p,land_colors(p["card"],basics)))
    return out

def can_pay_simple(state, generic=0, need_u=0, need_r=0):
    # conservative land-only payer; Boilerworks contributes U+R as two mana.
    pools=[]
    for _,colors in available_land_mana(state):
        pools.append(colors)
    # brute-force choices for <= early-game land counts
    choices=[(0,0,0)]
    for colors in pools:
        opts=[]
        if "UR" in colors: opts=[(1,1,0)]  # U,R,generic-total represented below
        else:
            if "U" in colors: opts.append((1,0,0))
            if "R" in colors: opts.append((0,1,0))
            if "C" in colors: opts.append((0,0,1))
        nxt=[]
        for a in choices:
            for o in opts:
                nxt.append((a[0]+o[0],a[1]+o[1],a[2]+o[2]))
        choices += nxt
    for u,r,c in choices:
        if u>=need_u and r>=need_r and (u+r+c-need_u-need_r)>=generic: return True
    return False

def payment_regressions():
    s=DevState(["Island"]); s.begin_turn(); s.play_land("Island")
    assert can_pay_simple(s,need_u=1)
    assert not can_pay_simple(s,need_r=1)
    b=DevState(["Island","Izzet Boilerworks"]); b.begin_turn(); b.play_land("Island"); b.begin_turn(); b.play_land("Izzet Boilerworks")
    # Boilerworks entered tapped this turn.
    assert not can_pay_simple(b,need_u=1)
    b.lands()[0]["tapped"]=False
    assert can_pay_simple(b,need_u=1) and can_pay_simple(b,need_r=1)
    return True



def untap_step(state):
    for p in state.battlefield: p["tapped"]=False

def fetch_basic(state, fetch):
    if fetch not in {"Evolving Wilds","Terramorphic Expanse"}: return False
    perm=next((p for p in state.battlefield if p["card"]==fetch and not p["tapped"]),None)
    if perm is None: return False
    # deterministic color policy: secure U first, then R.
    controlled={c for p in state.lands() for c in land_colors(p["card"])}
    basic_kind="Island" if "U" not in controlled else "Mountain"
    basic=f"Snow-Covered {basic_kind}" if state.snow_basics else basic_kind
    state.battlefield.remove(perm)
    state.battlefield.append({"card":basic,"tapped":True,"entered":state.turn})
    return True

def ash_barrens_cycle(state):
    if "Ash Barrens" not in state.hand: return False
    # requires one available mana; conservative land-only payment.
    if not can_pay_simple(state,generic=1): return False
    state.hand.remove("Ash Barrens")
    controlled={c for p in state.lands() for c in land_colors(p["card"])}
    basic_kind="Island" if "U" not in controlled else "Mountain"
    basic=f"Snow-Covered {basic_kind}" if state.snow_basics else basic_kind
    state.hand.append(basic)
    return True

def fetch_regressions():
    s=DevState(["Evolving Wilds"]); s.begin_turn(); assert s.play_land("Evolving Wilds")
    assert fetch_basic(s,"Evolving Wilds")
    assert s.lands()[0]["card"]=="Island" and s.lands()[0]["tapped"]
    a=DevState(["Island","Ash Barrens"]); a.begin_turn(); a.play_land("Island")
    assert ash_barrens_cycle(a) and "Mountain" in a.hand and "Ash Barrens" not in a.hand
    z=DevState(["Ash Barrens"]); z.begin_turn()
    assert not ash_barrens_cycle(z)
    return True



def generic_land_capacity(state):
    total=0
    for _,colors in available_land_mana(state):
        total += 2 if "UR" in colors else 1
    return total

def cast_mana_permanent(state, card, chalice_kicks=None):
    if card not in state.hand or card not in ROCKS|MANA_CREATURES: return False
    cost=MANA_COST[card]
    if card=="Everflowing Chalice":
        kicks=chalice_kicks if chalice_kicks is not None else generic_land_capacity(state)//2
        if kicks<1 or not can_pay_simple(state,generic=2*kicks): return False
        state.hand.remove(card); state.battlefield.append({"card":card,"tapped":False,"entered":state.turn,"kicks":kicks})
        return True
    if not can_pay_simple(state,generic=cost): return False
    state.hand.remove(card)
    state.battlefield.append({"card":card,"tapped":rock_enters_tapped(card),"entered":state.turn})
    return True

def nonland_mana_profile(state, conservative_fellwar=True):
    u=r=c=0
    for p in state.battlefield:
        if p["tapped"]: continue
        card=p["card"]
        if card=="Everflowing Chalice": c+=p.get("kicks",0)
        elif card=="Mind Stone": c+=1
        elif card=="Prismatic Lens": c+=1
        elif card=="Sky Diamond": u+=1
        elif card=="Fire Diamond": r+=1
        elif card=="Network Terminal": u+=1  # choose U for Guildmage threshold
        elif card in {"Ur-Golem's Eye","Sisay's Ring"}: c+=2
        elif card=="Star Compass":
            basics={basic_land_kind(x["card"]) for x in state.lands() if basic_land_kind(x["card"])}
            if "Island" in basics: u+=1
            elif "Mountain" in basics: r+=1
        elif card=="Fellwar Stone":
            if not conservative_fellwar: u+=1
            else: c+=1
        elif card=="Ornithopter of Paradise" and creature_mana_ready(card,p["entered"],state.turn): u+=1
        elif card=="Silver Myr" and creature_mana_ready(card,p["entered"],state.turn): u+=1
        elif card=="Iron Myr" and creature_mana_ready(card,p["entered"],state.turn): r+=1
    # Signet handled as converter: with at least one other nonland mana available it can turn 1 into UR.
    signets=sum(p["card"]=="Izzet Signet" and not p["tapped"] for p in state.battlefield)
    return {"U":u,"R":r,"C":c,"signets":signets,"gross":u+r+c}

def reversal_threshold(state):
    p=nonland_mana_profile(state)
    gross=p["gross"]
    # A ready Signet can consume one existing mana and return UR: net +1 and guarantees U.
    if p["signets"] and gross>=1:
        gross += 1
        has_u=True
    else:
        has_u=p["U"]>=1
    neutral=has_u and gross>=3
    positive=has_u and gross>3
    return neutral,positive,gross

def rock_regressions():
    s=DevState(["Island","Island","Mind Stone"]); s.begin_turn(); s.play_land("Island"); s.begin_turn(); untap_step(s); s.play_land("Island")
    assert cast_mana_permanent(s,"Mind Stone")
    assert nonland_mana_profile(s)["C"]==1
    d=DevState(["Island","Island","Sky Diamond"]); d.begin_turn(); d.play_land("Island"); d.begin_turn(); untap_step(d); d.play_land("Island")
    assert cast_mana_permanent(d,"Sky Diamond")
    assert nonland_mana_profile(d)["U"]==0
    d.begin_turn(); untap_step(d); assert nonland_mana_profile(d)["U"]==1
    return True



LAND_PRIORITY=["Island","Snow-Covered Island","Command Tower","Mountain","Snow-Covered Mountain","Ash Barrens","Evolving Wilds",
"Terramorphic Expanse","Volatile Fjord","Swiftwater Cliffs","Silverbluff Bridge",
"Lonely Sandbar","Forgotten Cave","Izzet Boilerworks"]
ROCK_PRIORITY=["Mind Stone","Izzet Signet","Prismatic Lens","Sky Diamond","Fire Diamond","Star Compass",
"Fellwar Stone","Ornithopter of Paradise","Silver Myr","Iron Myr","Network Terminal",
"Everflowing Chalice","Ur-Golem's Eye","Sisay's Ring"]

def choose_land(state):
    candidates=[c for c in LAND_PRIORITY if c in state.hand]
    if not candidates: return None
    # secure U, then R; prefer untapped sources, delay Boilerworks.
    controlled=set()
    for p in state.lands(): controlled |= land_colors(p["card"])
    if "U" not in controlled:
        for c in candidates:
            if "U" in land_colors(c) and not land_enters_tapped(c): return c
    if "R" not in controlled:
        for c in candidates:
            if "R" in land_colors(c) and not land_enters_tapped(c): return c
    return candidates[0]

def choose_mana_permanent(state):
    for c in ROCK_PRIORITY:
        if c in state.hand:
            if c=="Everflowing Chalice":
                if generic_land_capacity(state)>=2: return c
            elif can_pay_simple(state,generic=MANA_COST[c]): return c
    return None

def development_turn(state):
    untap_step(state)
    # Information-limited: uses only current hand/battlefield.
    land=choose_land(state)
    if land: state.play_land(land)
    # crack fetches immediately for deterministic color development.
    for fetch in ("Evolving Wilds","Terramorphic Expanse"):
        if any(p["card"]==fetch for p in state.battlefield): fetch_basic(state,fetch)
    rock=choose_mana_permanent(state)
    if rock: cast_mana_permanent_unified(state,rock)
    return {"land":land,"mana_permanent":rock,"reversal":reversal_threshold(state)}

def policy_regressions():
    s=DevState(["Mountain","Island"]); s.begin_turn(); assert choose_land(s)=="Island"
    s.play_land("Island"); s.begin_turn(); untap_step(s); assert choose_land(s)=="Mountain"
    r=DevState(["Island","Island","Mind Stone"]); r.begin_turn(); development_turn(r)
    r.begin_turn(); r.hand.append("Island"); out=development_turn(r)
    assert out["mana_permanent"]=="Mind Stone"
    return True



def pay_generic_from_lands(state, amount):
    """Conservative mutating generic payment. Returns False without mutation if impossible."""
    avail=[p for p,_ in available_land_mana(state)]
    capacity=sum(2 if p["card"]=="Izzet Boilerworks" else 1 for p in avail)
    if capacity<amount: return False
    need=amount
    # one land cannot be partially tapped: Boilerworks spends its full two-mana production.
    for p in avail:
        if need<=0: break
        p["tapped"]=True
        need-=2 if p["card"]=="Izzet Boilerworks" else 1
    return True

def cast_mana_permanent_mutating(state, card, chalice_kicks=None):
    if card not in state.hand or card not in ROCKS|MANA_CREATURES: return False
    if card=="Everflowing Chalice":
        kicks=chalice_kicks if chalice_kicks is not None else generic_land_capacity(state)//2
        if kicks<1 or not pay_generic_from_lands(state,2*kicks): return False
        state.hand.remove(card); state.battlefield.append({"card":card,"tapped":False,"entered":state.turn,"kicks":kicks})
        return True
    if not pay_generic_from_lands(state,MANA_COST[card]): return False
    state.hand.remove(card); state.battlefield.append({"card":card,"tapped":rock_enters_tapped(card),"entered":state.turn})
    return True

def mutating_payment_regressions():
    s=DevState(["Island","Island","Mind Stone","Sky Diamond"])
    s.begin_turn(); s.play_land("Island")
    s.begin_turn(); untap_step(s); s.play_land("Island")
    assert cast_mana_permanent_mutating(s,"Mind Stone")
    assert all(p["tapped"] for p in s.lands())
    assert not cast_mana_permanent_mutating(s,"Sky Diamond")
    b=DevState(["Island","Izzet Boilerworks","Mind Stone"])
    b.begin_turn(); b.play_land("Island"); b.begin_turn(); untap_step(b); b.play_land("Izzet Boilerworks")
    b.begin_turn(); untap_step(b)
    assert cast_mana_permanent_mutating(b,"Mind Stone")
    assert b.lands()[0]["tapped"]
    return True



def color_flags(state):
    colors=set()
    untapped=available_land_mana(state)
    for _,cs in untapped: colors |= cs
    u=any("U" in cs or "UR" in cs for _,cs in untapped)
    r=any("R" in cs or "UR" in cs for _,cs in untapped)
    # conservative UU: enumerate basic capacities; Boilerworks alone can provide only one U.
    u_sources=sum(1 for _,cs in untapped if "U" in cs or "UR" in cs)
    return u,r,u_sources>=2





UU_HAND={"Counterspell","Deprive","Ideas Unbound"}
U_HAND={"Brainstorm","Consider","Opt","Ponder","Preordain","Impulse","Dispel","Negate","Memory Lapse","Prohibit","Spell Pierce","Turn Aside","Dive Down","Mizzium Skin","Into the Roil","Blink of an Eye","Echoing Truth","Merchant Scroll","Dizzy Spell","Muddle the Mixture","High Tide","Snap"}
R_HAND={"Lightning Bolt","Galvanic Blast","Skred","Flame Slash","Abrade","Shattering Pulse","Lava Spike","Desperate Ritual","Faithless Looting","Pyroblast"}



def total_actionable_land_capacity(state):
    return sum(2 if p["card"]=="Izzet Boilerworks" else 1 for p,_ in available_land_mana(state))

def hold_up_metrics(state):
    # Conservative capacity checks after land play, before optional development.
    # Guildmage is modeled as UR (2 total); retaining U/R requires a third mana
    # with the retained color available from remaining source structure.
    cap=total_actionable_land_capacity(state)
    _,r,uu=color_flags(state)
    u,_,_=color_flags(state)
    guild=can_pay_simple(state,need_u=1,need_r=1)
    return {
        "guild_plus_u": guild and cap>=3 and uu,
        "guild_plus_r": guild and cap>=3 and r,
        "uu_plus_r": can_pay_simple(state,need_u=2,need_r=1),
    }

def actual_hand_action_metrics(state):
    h=set(state.hand)
    # evaluated at actionable state after land play/fetch, before optional rock spending
    uu_present=bool(h & UU_HAND)
    u_present=bool(h & U_HAND)
    r_present=bool(h & R_HAND)
    return {
        "uu_spell_present":uu_present,
        "uu_spell_exec":uu_present and can_pay_simple(state,need_u=2),
        "u_spell_present":u_present,
        "u_spell_exec":u_present and can_pay_simple(state,need_u=1),
        "r_spell_present":r_present,
        "r_spell_exec":r_present and can_pay_simple(state,need_r=1),
        "ritual_present":"Desperate Ritual" in h,
        "spike_present":"Lava Spike" in h,
    }

def snow_readiness_metrics(state):
    snow=sum(is_snow_permanent(p["card"]) for p in state.battlefield)
    skred_castable="Skred" in state.hand and ready_payment_feasible_exact(state,need_r=1)
    return {
        "snow_permanents":snow,
        "skred_damage":snow if skred_castable else 0,
        "skred_live":skred_castable and snow>=1,
        "skred_3plus":skred_castable and snow>=3,
    }

def snow_regressions():
    regular=DevState(["Island","Mountain"])
    regular.begin_turn(); assert regular.play_land("Island")
    assert land_colors("Snow-Covered Island")==land_colors("Island")=={"U"}
    assert land_colors("Snow-Covered Mountain")==land_colors("Mountain")=={"R"}
    assert high_tide_island("Snow-Covered Island")
    assert not is_snow_permanent("Island")
    assert is_snow_permanent("Snow-Covered Island") and is_snow_permanent("Volatile Fjord")

    snow=DevState(["Snow-Covered Island","Snow-Covered Mountain","Skred"],snow_basics=True)
    snow.begin_turn(); assert snow.play_land("Snow-Covered Island")
    snow.begin_turn(); untap_step(snow); assert snow.play_land("Snow-Covered Mountain")
    metrics=snow_readiness_metrics(snow)
    assert metrics=={"snow_permanents":2,"skred_damage":2,"skred_live":True,"skred_3plus":False}

    fetch=DevState(["Evolving Wilds"],snow_basics=True)
    fetch.begin_turn(); assert fetch.play_land("Evolving Wilds") and fetch_basic(fetch,"Evolving Wilds")
    assert fetch.lands()[0]["card"]=="Snow-Covered Island"
    return True

def high_tide_metrics(state):
    islands=sum(high_tide_island(p["card"]) and not p["tapped"] for p in state.lands())
    # If High Tide is in hand, casting it costs U from an Island; remaining untapped Islands
    # then each produce an extra U this turn. Gross post-cast mana from those Islands is 2 each.
    in_hand="High Tide" in state.hand
    castable=in_hand and islands>=1
    post_cast_island_mana=(islands-1)*2 if castable else 0
    net_gain_over_no_tide=(islands-1) if castable else 0
    productive=castable and net_gain_over_no_tide>0
    return castable,productive,post_cast_island_mana,net_gain_over_no_tide



SELECTION_SPECS={
"Ponder":(1,3),"Preordain":(1,2),"Brainstorm":(1,3),"Consider":(1,1),
"Opt":(1,1),"Impulse":(2,4),"Curate":(2,2),"Faithless Looting":(1,2),
"Thrill of Possibility":(2,2),"Frantic Search":(3,2),"Think Twice":(2,1),"Strategic Planning":(2,3),
"Pieces of the Puzzle":(3,5)
}
INTERACTION_CARDS={"Counterspell","Arcane Denial","Negate","Dispel","Memory Lapse","Deprive","Prohibit","Spell Pierce","Turn Aside","Lose Focus","Pyroblast","Dive Down","Mizzium Skin","Lightning Bolt","Galvanic Blast","Skred","Flame Slash","Fire // Ice","Into the Roil","Blink of an Eye","Echoing Truth","Abrade","Shattering Pulse"}
COMBO_CARDS={"Lava Spike","Desperate Ritual"}

def selection_priority(card, state):
    # Uses current state only; caller supplies only legally viewed cards.
    lands_in_hand=sum(c in LANDS for c in state.hand)
    if card in COMBO_CARDS and bool((COMBO_CARDS-{card}) & set(state.hand)): return 90
    if card in INTERACTION_CARDS and not (INTERACTION_CARDS & set(state.hand)): return 80
    if card in LANDS and lands_in_hand==0: return 70
    if basic_land_kind(card) or card=="Command Tower": return 60
    if card in INTERACTION_CARDS: return 50
    if card in COMBO_CARDS: return 40
    return 20

def choose_from_seen(seen, state, take=1):
    ranked=sorted(enumerate(seen),key=lambda x:(-selection_priority(x[1],state),x[0]))
    idx={i for i,_ in ranked[:take]}
    return [c for i,c in enumerate(seen) if i in idx],[c for i,c in enumerate(seen) if i not in idx]

def selection_regressions():
    s=DevState(["Lava Spike"])
    keep,rest=choose_from_seen(["Desperate Ritual","Mountain"],s,1)
    assert keep==["Desperate Ritual"]
    t=DevState(["Counterspell"])
    keep,_=choose_from_seen(["Island","Lightning Bolt"],t,1)
    assert keep==["Island"]
    z=DevState(["Island"])
    keep,_=choose_from_seen(["Negate","Mountain"],z,1)
    assert keep==["Negate"]
    return True



class SpellState:
    def __init__(self, hand, library):
        self.hand=list(hand); self.library=list(library); self.graveyard=[]
        self.cards_seen=0; self.cards_drawn=0
    def draw(self,n=1):
        got=self.library[:n]; self.library=self.library[n:]; self.hand+=got
        self.cards_drawn+=len(got); return got
    def look(self,n):
        seen=self.library[:n]; self.cards_seen+=len(seen); return seen
    def remove_top(self,n): self.library=self.library[n:]



def resolve_strategic_planning(ss,dev):
    seen=ss.look(3); ss.remove_top(len(seen))
    keep,rest=choose_from_seen(seen,dev,1)
    ss.hand+=keep; ss.cards_drawn+=len(keep); ss.graveyard+=rest
    return keep,rest

NON_INSTANT_SORCERY_CARDS=LANDS|ROCKS|MANA_CREATURES|{
    "Goblin Electromancer","Archaeomancer","Mnemonic Wall","Izzet Chronarch","Murmuring Mystic"
}

def resolve_pieces_of_the_puzzle(ss,dev):
    seen=ss.look(5); ss.remove_top(len(seen))
    eligible=[c for c in seen if c not in NON_INSTANT_SORCERY_CARDS]
    keep=[]
    hand=set(dev.hand)
    # When both primary pieces are visible, preserve the pair before taking generic value.
    if not (COMBO_CARDS & hand) and COMBO_CARDS <= set(eligible):
        keep=["Lava Spike","Desperate Ritual"]
    elif len(COMBO_CARDS & hand)==1:
        missing=next(iter(COMBO_CARDS-hand))
        if missing in eligible:
            keep=[missing]
    remaining=list(eligible)
    for card in keep:
        remaining.remove(card)
    if len(keep)<2 and remaining:
        extra,_=choose_from_seen(remaining,dev,2-len(keep))
        keep+=extra
    rest=list(seen)
    for card in keep:
        rest.remove(card)
    ss.hand+=keep; ss.cards_drawn+=len(keep); ss.graveyard+=rest
    return keep,rest

def resolve_curate(ss, dev):
    seen=ss.look(2); ss.remove_top(len(seen))
    keep,rest=choose_from_seen(seen,dev,1)
    ss.hand+=keep; ss.cards_drawn+=len(keep); ss.graveyard+=rest
    return keep,rest

def resolve_consider(ss,dev):
    seen=ss.look(1); ss.remove_top(len(seen))
    if not seen: return [],[]
    keep,rest=choose_from_seen(seen,dev,1)
    # deterministic policy: keep any card scoring >=40, otherwise mill then draw.
    if selection_priority(seen[0],dev)>=40:
        ss.library=seen+ss.library
        return ss.draw(1),[]
    ss.graveyard+=seen
    return ss.draw(1),seen

def resolve_opt(ss,dev):
    seen=ss.look(1); ss.remove_top(len(seen))
    if seen and selection_priority(seen[0],dev)>=40:
        ss.library=seen+ss.library
    # otherwise bottom the looked card
    elif seen: ss.library+=seen
    return ss.draw(1)

def resolve_impulse(ss,dev):
    seen=ss.look(4); ss.remove_top(len(seen))
    keep,rest=choose_from_seen(seen,dev,1)
    ss.hand+=keep; ss.cards_drawn+=len(keep); ss.library+=rest
    return keep

def selection_resolution_regressions():
    d=DevState(["Lava Spike"])
    s=SpellState([],["Desperate Ritual","Mountain","Island"])
    keep,mill=resolve_curate(s,d)
    assert keep==["Desperate Ritual"] and mill==["Mountain"] and s.library[0]=="Island"
    d2=DevState(["Island"])
    o=SpellState([],["Negate","Mountain"])
    assert resolve_opt(o,d2)==["Negate"]
    i=SpellState([],["Mountain","Negate","Island","Lava Spike","Desperate Ritual"])
    k=resolve_impulse(i,d2)
    assert k==["Negate"] and len(i.library)==4
    sp=SpellState([],["Mountain","Desperate Ritual","Island","Opt"])
    keep,mill=resolve_strategic_planning(sp,DevState(["Lava Spike"]))
    assert keep==["Desperate Ritual"] and len(mill)==2 and len(sp.graveyard)==2
    pp=SpellState([],["Negate","Lava Spike","Desperate Ritual","Island","Mind Stone","Opt"])
    keep,mill=resolve_pieces_of_the_puzzle(pp,DevState([]))
    assert keep==["Lava Spike","Desperate Ritual"] and len(mill)==3
    assert pp.library==["Opt"]
    return True



def resolve_preordain(ss,dev):
    seen=ss.look(2); ss.remove_top(len(seen))
    ranked=sorted(seen,key=lambda c:-selection_priority(c,dev))
    top=[c for c in ranked if selection_priority(c,dev)>=40]
    bottom=[c for c in ranked if selection_priority(c,dev)<40]
    ss.library=top+ss.library+bottom
    return ss.draw(1)

def resolve_ponder(ss,dev):
    seen=ss.look(3); ss.remove_top(len(seen))
    ranked=sorted(seen,key=lambda c:-selection_priority(c,dev))
    # deterministic no-shuffle policy when any viewed card is useful; otherwise shuffle viewed
    # cards into library with caller RNG handled later by turn engine.
    if ranked and selection_priority(ranked[0],dev)>=40:
        ss.library=ranked+ss.library
        return ss.draw(1),False
    ss.library=seen+ss.library
    return ss.draw(1),False

def resolve_brainstorm(ss,dev):
    drawn=ss.draw(3)
    # Put back two lowest-priority cards from the full hand.
    ranked=sorted(enumerate(ss.hand),key=lambda x:(selection_priority(x[1],dev),x[0]))
    chosen=sorted([i for i,_ in ranked[:2]],reverse=True)
    put=[]
    for i in chosen: put.append(ss.hand.pop(i))
    # first popped should become deeper; reverse to preserve deterministic top order
    ss.library=list(reversed(put))+ss.library
    return drawn,list(reversed(put))

def complex_selection_regressions():
    d=DevState(["Island"])
    p=SpellState([],["Negate","Mountain","Lava Spike","Island"])
    draw,_=resolve_ponder(p,d); assert draw==["Negate"]
    q=SpellState([],["Mountain","Negate","Island"])
    assert resolve_preordain(q,d)==["Negate"]
    b=SpellState(["Negate","Mountain"],["Island","Lava Spike","Desperate Ritual","Opt"])
    drawn,put=resolve_brainstorm(b,d)
    assert len(drawn)==3 and len(put)==2 and len(b.hand)==3
    return True



def discard_lowest(hand,dev,n):
    ranked=sorted(enumerate(hand),key=lambda x:(selection_priority(x[1],dev),x[0]))
    idx=sorted([i for i,_ in ranked[:n]],reverse=True)
    out=[]
    for i in idx: out.append(hand.pop(i))
    return list(reversed(out))

def resolve_faithless_looting(ss,dev):
    drawn=ss.draw(2); discarded=discard_lowest(ss.hand,dev,min(2,len(ss.hand)))
    ss.graveyard+=discarded; return drawn,discarded

def resolve_thrill(ss,dev):
    if not ss.hand: return [],[]
    discarded=discard_lowest(ss.hand,dev,1); ss.graveyard+=discarded
    return ss.draw(2),discarded

def resolve_frantic_search(ss,dev):
    drawn=ss.draw(2); discarded=discard_lowest(ss.hand,dev,min(2,len(ss.hand)))
    ss.graveyard+=discarded
    return drawn,discarded

def resolve_think_twice(ss,dev,flashback=False):
    drawn=ss.draw(1)
    return drawn

def draw_discard_regressions():
    d=DevState(["Lava Spike"])
    f=SpellState(["Mountain","Negate"],["Desperate Ritual","Island","Opt"])
    drawn,disc=resolve_faithless_looting(f,d)
    assert drawn==["Desperate Ritual","Island"] and len(disc)==2
    t=SpellState(["Mountain","Negate"],["Island","Opt"])
    drawn,disc=resolve_thrill(t,d)
    assert len(disc)==1 and drawn==["Island","Opt"]
    q=SpellState([],["Island","Opt"])
    assert resolve_think_twice(q,d)==["Island"]
    return True



SELECTION_CAST_ORDER=["Ponder","Preordain","Consider","Opt","Curate","Impulse","Brainstorm",
"Faithless Looting","Thrill of Possibility","Think Twice","Strategic Planning","Pieces of the Puzzle","Frantic Search"]

def selection_cost(card):
    return {"Ponder":(0,1,0),"Preordain":(0,1,0),"Brainstorm":(0,1,0),
            "Consider":(0,1,0),"Opt":(0,1,0),"Curate":(1,1,0),"Impulse":(1,1,0),
            "Faithless Looting":(0,0,1),"Thrill of Possibility":(1,0,1),
            "Think Twice":(1,1,0),"Strategic Planning":(1,1,0),"Pieces of the Puzzle":(2,1,0),
            "Frantic Search":(2,1,0)}[card]

def can_cast_selection(state,card):
    g,u,r=selection_cost(card)
    return card in state.hand and can_pay_simple(state,generic=g,need_u=u,need_r=r)

def choose_selection_spell(state):
    # Cast selection when it addresses a concrete current need; otherwise prefer cheap velocity.
    land_count=sum(c in LANDS for c in state.hand)
    combo=set(state.hand)&COMBO_CARDS
    no_interaction=not bool(set(state.hand)&INTERACTION_CARDS)
    candidates=[c for c in SELECTION_CAST_ORDER if can_cast_selection(state,c)]
    if not candidates: return None
    # Combo completion is highest priority.
    if len(combo)==1:
        return candidates[0]
    # Preserve interaction once development is stable; only dig when the next land is genuinely at risk.
    if not no_interaction:
        if land_count==0 and len(state.lands())<2:
            return candidates[0]
        return None
    if land_count==0 or no_interaction:
        return candidates[0]
    return next((c for c in candidates if sum(selection_cost(c))==1),None)

def scheduler_regressions():
    s=DevState(["Island","Ponder"]); s.begin_turn(); s.play_land("Island")
    assert choose_selection_spell(s)=="Ponder"
    t=DevState(["Island","Island","Impulse"]); t.begin_turn(); t.play_land("Island"); t.begin_turn(); untap_step(t); t.play_land("Island")
    assert choose_selection_spell(t)=="Impulse"
    z=DevState(["Island","Island","Counterspell","Impulse"]); z.begin_turn(); z.play_land("Island"); z.begin_turn(); untap_step(z); z.play_land("Island")
    assert choose_selection_spell(z) is None
    return True





def source_options(state):
    opts=[]
    for p,colors in available_land_mana(state):
        if "UR" in colors: opts.append((p,2,{"UR"}))
        else: opts.append((p,1,set(colors)))
    for p,v in ready_nonland_sources(state):
        c=p["card"]
        cols={"U","R"} if c in {"Network Terminal","Ornithopter of Paradise"} else ({"U"} if c in {"Sky Diamond","Silver Myr"} else ({"R"} if c in {"Fire Diamond","Iron Myr"} else {"C"}))
        opts.append((p,v,cols))
    return opts

def pay_colored_mutating(state,generic=0,need_u=0,need_r=0):
    opts=source_options(state)
    # Small early-game source sets: brute force subsets and color assignments conservatively.
    import itertools
    for k in range(1,len(opts)+1):
        for subset in itertools.combinations(opts,k):
            total=sum(v for _,v,_ in subset)
            if total < generic+need_u+need_r: continue
            color_sources=[cs for _,_,cs in subset]
            import itertools as _it
            ok=False
            modes=[]
            for cs in color_sources:
                if "UR" in cs: modes.append(((1,1),))
                else:
                    source_modes=[]
                    if "U" in cs: source_modes.append((1,0))
                    if "R" in cs: source_modes.append((0,1))
                    if not source_modes: source_modes.append((0,0))
                    modes.append(tuple(source_modes))
            for assignment in _it.product(*modes):
                if sum(u for u,_ in assignment)>=need_u and sum(r for _,r in assignment)>=need_r:
                    ok=True; break
            if not ok: continue
            for p,_,_ in subset: p["tapped"]=True
            return True
    return generic+need_u+need_r==0

def colored_payment_regressions():
    s=DevState([]); s.turn=3
    s.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Mountain","tapped":False,"entered":2}]
    assert pay_colored_mutating(s,need_u=1)
    assert s.battlefield[0]["tapped"] and not s.battlefield[1]["tapped"]
    t=DevState([]); t.turn=3
    t.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Mountain","tapped":False,"entered":2}]
    assert pay_colored_mutating(t,need_u=1,need_r=1)
    assert all(p["tapped"] for p in t.battlefield)
    b=DevState([]); b.turn=3
    b.battlefield=[{"card":"Izzet Boilerworks","tapped":False,"entered":2}]
    assert pay_colored_mutating(b,need_u=1,need_r=1)
    assert b.battlefield[0]["tapped"]
    return True

def pay_selection_cost(state,card):
    g,nu,nr=selection_cost(card)
    return pay_colored_mutating(state,generic=g,need_u=nu,need_r=nr)

def resolve_selected_card(card,ss,dev):
    if card=="Strategic Planning": return resolve_strategic_planning(ss,dev)
    if card=="Pieces of the Puzzle": return resolve_pieces_of_the_puzzle(ss,dev)
    if card=="Curate": return resolve_curate(ss,dev)
    if card=="Consider": return resolve_consider(ss,dev)
    if card=="Opt": return resolve_opt(ss,dev)
    if card=="Impulse": return resolve_impulse(ss,dev)
    if card=="Ponder": return resolve_ponder(ss,dev)
    if card=="Preordain": return resolve_preordain(ss,dev)
    if card=="Brainstorm": return resolve_brainstorm(ss,dev)
    if card=="Faithless Looting": return resolve_faithless_looting(ss,dev)
    if card=="Thrill of Possibility": return resolve_thrill(ss,dev)
    if card=="Frantic Search": return resolve_frantic_search(ss,dev)
    if card=="Think Twice": return resolve_think_twice(ss,dev)
    raise ValueError(card)

def cast_one_selection(state, library):
    card=choose_selection_spell(state)
    if card is None or not pay_selection_cost(state,card): return None,library,0,0
    state.hand.remove(card)
    ss=SpellState(state.hand,library)
    before_seen,before_drawn=ss.cards_seen,ss.cards_drawn
    resolve_selected_card(card,ss,state)
    ss.graveyard.append(card)
    state.hand=ss.hand
    return card,ss.library,ss.cards_seen-before_seen,ss.cards_drawn-before_drawn

def integration_regressions():
    s=DevState(["Island","Ponder"]); s.begin_turn(); s.play_land("Island")
    card,lib,seen,drawn=cast_one_selection(s,["Negate","Mountain","Opt"])
    assert card=="Ponder" and drawn==1 and "Negate" in s.hand
    assert s.lands()[0]["tapped"]
    return True



def mutable_library_regressions():
    # Brainstorm put-backs remain on the same future library.
    d=DevState(["Island"])
    ss=SpellState(["Mountain","Negate"],["Island","Lava Spike","Desperate Ritual","Opt"])
    _,put=resolve_brainstorm(ss,d)
    assert ss.library[:2]==put
    first=ss.draw(1)[0]
    assert first==put[0]
    # Ponder ordering mutates the future draw library.
    pp=SpellState([],["Negate","Mountain","Lava Spike","Island"])
    drawn,_=resolve_ponder(pp,d)
    assert drawn==["Negate"]
    assert pp.library[0] in {"Lava Spike","Mountain"}
    return True





def guildmage_on_battlefield(state):
    return any(p["card"]=="Izzet Guildmage" for p in state.battlefield)

def guildmage_tax(state):
    return 2*state.commander_casts

def should_deploy_guildmage(state):
    if guildmage_on_battlefield(state) or not state.commander_zone: return False
    # Deploy when affordable and either combo pair is present or hand has interaction to support future turns.
    if not can_pay_simple(state,generic=guildmage_tax(state),need_u=1,need_r=1): return False
    h=set(state.hand)
    pair={"Lava Spike","Desperate Ritual"} <= h
    protected=bool(h & INTERACTION_CARDS)
    return pair or protected

def deploy_guildmage(state):
    if not should_deploy_guildmage(state): return False
    if not pay_colored_mutating(state,generic=guildmage_tax(state),need_u=1,need_r=1): return False
    state.battlefield.append({"card":"Izzet Guildmage","tapped":False,"entered":state.turn})
    state.commander_casts+=1; state.commander_zone=False
    return True

def remove_guildmage_to_command_zone(state):
    permanent=next((p for p in state.battlefield if p["card"]=="Izzet Guildmage"),None)
    if permanent is None: return False
    state.battlefield.remove(permanent); state.commander_zone=True
    return True

def commander_regressions():
    s=DevState(["Lava Spike","Desperate Ritual"]); s.turn=3
    s.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Mountain","tapped":False,"entered":2}]
    assert deploy_guildmage(s) and guildmage_on_battlefield(s)
    assert s.commander_casts==1 and not s.commander_zone
    assert all(p["tapped"] for p in s.lands())
    assert not deploy_guildmage(s)
    assert remove_guildmage_to_command_zone(s)
    assert s.commander_zone and not guildmage_on_battlefield(s)
    untap_step(s)
    s.battlefield.append({"card":"Island","tapped":False,"entered":3})
    assert not should_deploy_guildmage(s)  # three mana cannot pay the first 2-mana tax
    s.battlefield.append({"card":"Mountain","tapped":False,"entered":3})
    assert deploy_guildmage(s) and s.commander_casts==2
    z=DevState(["Ponder"]); z.turn=3
    z.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Mountain","tapped":False,"entered":2}]
    assert not should_deploy_guildmage(z)
    return True



def ready_red_payment_feasible(state,generic,need_r):
    # Aggregate ready mana, including Boilerworks' fixed U+R production and Signet's
    # one-mana activation. For a red-heavy payment, every flexible source may
    # produce red; a Signet can consume non-red mana first (or one red if necessary).
    if any(p["card"]=="Prismatic Lens" and not p.get("tapped",False)
           for p in state.battlefield):
        return ready_payment_feasible_exact(state,generic=generic,need_r=need_r)
    total=0; max_red=0
    for _,value,colors in source_options(state):
        total+=value
        if "UR" in colors: max_red+=1
        elif "R" in colors: max_red+=1
    ready_signets=sum(p["card"]=="Izzet Signet" and not p["tapped"] for p in state.battlefield)
    for signets in range(ready_signets+1):
        if signets and total<1:
            continue
        post_total=total+signets
        activation_red_cost=1 if signets and total==max_red else 0
        post_red=max_red+signets-activation_red_cost
        if post_total>=generic+need_r and post_red>=need_r:
            return True
    return False

def primary_combo_launch_feasible(state):
    if not guildmage_on_battlefield(state): return False
    if not {"Lava Spike","Desperate Ritual"} <= set(state.hand): return False
    electromancer=any(p["card"]=="Goblin Electromancer" for p in state.battlefield)

    # Seething Song can resolve first and turn an initial 2R payment into the five
    # red mana required to cast Spike with Ritual spliced and then cast Ritual itself.
    if "Seething Song" in state.hand:
        song_generic=1 if electromancer else 2
        if ready_red_payment_feasible(state,generic=song_generic,need_r=1):
            return True

    # Baseline route: cast Spike with Ritual spliced, then cast the still-in-hand
    # Ritual while the combined Spike remains on stack. The two casts cost 2RRR
    # total, or RRR with Electromancer reducing both generic portions. The resolved
    # Ritual then creates the RRR needed for Guildmage's first 2R activation.
    generic=0 if electromancer else 2
    return ready_red_payment_feasible(state,generic=generic,need_r=3)

def primary_combo_damage_available(state, opponent_life=30):
    if not primary_combo_launch_feasible(state): return 0
    # First copied combined spell refunds exactly RRR, funding each subsequent 2R copy.
    # Thus arbitrarily many copies are available in this goldfish model.
    copies_needed=max(0,(opponent_life+2)//3 - 1)  # original contributes final 3
    return 3*(copies_needed+1)

def primary_combo_table_damage_plan(state, opponent_lives=(30,30,30)):
    """Return the finite resolving-spell plan for a multiplayer goldfish table kill.

    The original combined Lava Spike has one fixed target. Each Guildmage copy may
    choose a new target, so every opponent can receive an independently sufficient
    number of three-damage resolutions. One of those resolutions is the original;
    all others are copies. ``None`` means the launch itself is unavailable.
    """
    lives=tuple(opponent_lives)
    if not lives or any(isinstance(life,bool) or not isinstance(life,int) or life<=0
                        for life in lives):
        raise ValueError("opponent_lives must contain positive integers")
    if not primary_combo_launch_feasible(state): return None
    resolving_spells=tuple((life+2)//3 for life in lives)
    return {
        "resolving_spells_by_opponent":resolving_spells,
        "damage_by_opponent":tuple(3*spells for spells in resolving_spells),
        "copies":sum(resolving_spells)-1,
        "originals":1,
    }

def max_x_damage_castable(state, card):
    """Maximum X for an in-hand XR backup finisher using ready sources exactly."""
    if card not in {"Rolling Thunder","Kaervek's Torch"} or card not in state.hand:
        return 0
    pools=ready_mana_pool_states(state)
    max_total=max((sum(pool) for pool in pools),default=0)
    electromancer=any(p["card"]=="Goblin Electromancer" for p in state.battlefield)
    discount=1 if electromancer else 0
    best=0
    for x in range(1,max_total+discount+1):
        if ready_payment_feasible_exact(state,generic=max(0,x-discount),need_r=1):
            best=x
    return best

def commander_independent_readiness(state):
    """Observable backup-plan readiness; deliberately not a win-rate model."""
    electromancer=any(p["card"]=="Goblin Electromancer" for p in state.battlefield)
    capsize_generic=3 if electromancer else 4
    return {
        "mystic_present":"Murmuring Mystic" in state.hand,
        "mystic_castable":"Murmuring Mystic" in state.hand and
            ready_payment_feasible_exact(state,generic=3,need_u=1),
        "rolling_present":"Rolling Thunder" in state.hand,
        "rolling_x":max_x_damage_castable(state,"Rolling Thunder"),
        "torch_present":"Kaervek's Torch" in state.hand,
        "torch_x":max_x_damage_castable(state,"Kaervek's Torch"),
        "capsize_present":"Capsize" in state.hand,
        "capsize_buyback":"Capsize" in state.hand and
            ready_payment_feasible_exact(state,generic=capsize_generic,need_u=2),
    }


# v0.7 phase-0 interaction semantics. Soft permission remains deliberately excluded
# from guaranteed protection because the opponent's available payment is unspecified.
PROTECTION_COSTS={
    "Counterspell":(0,2,0), "Arcane Denial":(1,1,0), "Negate":(1,1,0),
    "Dispel":(0,1,0), "Memory Lapse":(1,1,0), "Deprive":(0,2,0),
    "Turn Aside":(0,1,0), "Pyroblast":(0,0,1), "Dive Down":(0,1,0),
    "Mizzium Skin":(0,1,0),
}
PROTECTION_COVERAGE={
    "Counterspell":{"spell"}, "Arcane Denial":{"spell"},
    "Memory Lapse":{"spell"}, "Deprive":{"spell"},
    "Negate":{"noncreature"}, "Dispel":{"instant"},
    "Turn Aside":{"targeted_spell"}, "Pyroblast":{"blue_spell"},
    "Dive Down":{"targeted_creature"}, "Mizzium Skin":{"targeted_creature"},
}
CONDITIONAL_PROTECTION={"Prohibit","Spell Pierce","Lose Focus"}
CONDITIONAL_PROTECTION_COSTS={
    "Prohibit":(1,1,0), "Spell Pierce":(0,1,0), "Lose Focus":(1,1,0),
}
CONDITIONAL_PROTECTION_COVERAGE={
    "Prohibit":{"spell"}, "Spell Pierce":{"noncreature"}, "Lose Focus":{"spell"},
}

def protection_covers(card, threat_tags):
    if card in CONDITIONAL_PROTECTION: return False
    coverage=PROTECTION_COVERAGE.get(card,set())
    threats=set(threat_tags)
    return ("spell" in coverage and "spell" in threats) or bool((coverage-{"spell"}) & threats)

def primary_combo_launch_with_protection_feasible(state, protection_card, threat_tags):
    """Phase-0 land-payment check; sampled instrumentation is not authorized here."""
    if protection_card not in state.hand or not protection_covers(protection_card,threat_tags):
        return False
    if not primary_combo_launch_feasible(state): return False
    protect_generic,protect_u,protect_r=PROTECTION_COSTS[protection_card]
    electromancer=any(p["card"]=="Goblin Electromancer" for p in state.battlefield)
    if "Seething Song" in state.hand:
        launch_generic,launch_red=(1 if electromancer else 2),1
    else:
        launch_generic,launch_red=(0 if electromancer else 2),3
    return can_pay_simple(state,generic=launch_generic+protect_generic,
                          need_u=protect_u,need_r=launch_red+protect_r)

def ready_mana_pool_states(state):
    """Enumerate exact ready U/R/C pools, including fixed Boilerworks and Signet conversion."""
    source_modes=[]
    basics={basic_land_kind(p["card"]) for p in state.lands() if basic_land_kind(p["card"])}
    for p in state.battlefield:
        if p.get("tapped",False): continue
        card=p["card"]
        if card=="Izzet Signet": continue
        if card=="Izzet Boilerworks": source_modes.append(((1,1,0),)); continue
        colors=land_colors(card,basics)
        if colors:
            modes=[]
            if "U" in colors: modes.append((1,0,0))
            if "R" in colors: modes.append((0,1,0))
            if "C" in colors: modes.append((0,0,1))
            if modes: source_modes.append(tuple(modes))
            continue
        if card=="Everflowing Chalice" and p.get("kicks",0)>0:
            source_modes.append(((0,0,p["kicks"]),))
        elif card in {"Mind Stone","Fellwar Stone"}:
            source_modes.append(((0,0,1),))
        elif card=="Prismatic Lens":
            # Lens is handled below: tapping for C adds one mana, while filtering
            # recolors one mana from another source without increasing the total.
            continue
        elif card=="Star Compass":
            modes=[]
            if "Island" in basics: modes.append((1,0,0))
            if "Mountain" in basics: modes.append((0,1,0))
            if modes: source_modes.append(tuple(modes))
        elif card in {"Sky Diamond","Silver Myr"} and (card not in MANA_CREATURES or creature_mana_ready(card,p["entered"],state.turn)):
            source_modes.append(((1,0,0),))
        elif card in {"Fire Diamond","Iron Myr"} and (card not in MANA_CREATURES or creature_mana_ready(card,p["entered"],state.turn)):
            source_modes.append(((0,1,0),))
        elif card in {"Network Terminal","Ornithopter of Paradise"} and (card not in MANA_CREATURES or creature_mana_ready(card,p["entered"],state.turn)):
            source_modes.append(((1,0,0),(0,1,0)))
        elif card in {"Ur-Golem's Eye","Sisay's Ring"}:
            source_modes.append(((0,0,2),))

    pools={(0,0,0)}
    for modes in source_modes:
        pools={(u+du,r+dr,c+dc) for u,r,c in pools for du,dr,dc in modes}

    lenses=sum(p["card"]=="Prismatic Lens" and not p.get("tapped",False)
               for p in state.battlefield)
    for _ in range(lenses):
        expanded=set()
        for u,r,c in pools:
            expanded.add((u,r,c+1))
            if u:
                expanded.add((u-1,r+1,c))
                expanded.add((u,r,c))
            if r:
                expanded.add((u+1,r-1,c))
                expanded.add((u,r,c))
            if c:
                expanded.add((u+1,r,c-1))
                expanded.add((u,r+1,c-1))
        pools=expanded

    signets=sum(p["card"]=="Izzet Signet" and not p.get("tapped",False) for p in state.battlefield)
    for _ in range(signets):
        expanded=set(pools)
        for u,r,c in pools:
            if u: expanded.add((u,r+1,c))       # spend U; add UR
            if r: expanded.add((u+1,r,c))       # spend R; add UR
            if c: expanded.add((u+1,r+1,c-1))   # spend C; add UR
        pools=expanded
    return pools

def ready_payment_feasible_exact(state,generic=0,need_u=0,need_r=0):
    for u,r,c in ready_mana_pool_states(state):
        if u>=need_u and r>=need_r and u+r+c-need_u-need_r>=generic:
            return True
    return generic+need_u+need_r==0

def primary_launch_source_cost(state):
    electromancer=any(p["card"]=="Goblin Electromancer" for p in state.battlefield)
    if "Seething Song" in state.hand:
        return (1 if electromancer else 2),0,1
    return (0 if electromancer else 2),0,3

def primary_combo_launch_with_protection_exact(state, protection_card, threat_tags):
    if protection_card not in state.hand or not protection_covers(protection_card,threat_tags):
        return False
    if not primary_combo_launch_feasible(state): return False
    lg,lu,lr=primary_launch_source_cost(state)
    pg,pu,pr=PROTECTION_COSTS[protection_card]
    return ready_payment_feasible_exact(state,lg+pg,lu+pu,lr+pr)

def conditional_combo_protection_exact(state, protection_card, threat_tags, threat_mv):
    if protection_card not in state.hand or protection_card not in CONDITIONAL_PROTECTION:
        return False
    coverage=CONDITIONAL_PROTECTION_COVERAGE[protection_card]
    if "spell" not in coverage and not (coverage & set(threat_tags)): return False
    if protection_card=="Prohibit" and threat_mv>2: return False
    if not primary_combo_launch_feasible(state): return False
    lg,lu,lr=primary_launch_source_cost(state)
    pg,pu,pr=CONDITIONAL_PROTECTION_COSTS[protection_card]
    return ready_payment_feasible_exact(state,lg+pg,lu+pu,lr+pr)

def commander_recovery_launch_feasible(state):
    if not guildmage_on_battlefield(state) or state.commander_casts<1: return False
    if not {"Lava Spike","Desperate Ritual"} <= set(state.hand): return False
    if not primary_combo_launch_feasible(state): return False
    lg,lu,lr=primary_launch_source_cost(state)
    # Model one resolved removal, command-zone replacement, and immediate taxed recast.
    return ready_payment_feasible_exact(state,lg+guildmage_tax(state),lu+1,lr+1)

def interaction_readiness_metrics(state):
    stack_tags={"spell","instant","noncreature"}
    blue_stack_tags=stack_tags|{"blue_spell"}
    removal_tags={"spell","instant","noncreature","targeted_spell","targeted_permanent","targeted_creature"}
    ability_removal_tags={"ability","targeted_permanent","targeted_creature"}
    guaranteed=tuple(PROTECTION_COSTS)
    conditional=tuple(CONDITIONAL_PROTECTION)
    return {
        "stack_guaranteed":any(primary_combo_launch_with_protection_exact(state,c,stack_tags) for c in guaranteed),
        "blue_stack_guaranteed":any(primary_combo_launch_with_protection_exact(state,c,blue_stack_tags) for c in guaranteed),
        "stack_conditional":any(conditional_combo_protection_exact(state,c,stack_tags,2) for c in conditional),
        "removal_guaranteed":any(primary_combo_launch_with_protection_exact(state,c,removal_tags) for c in guaranteed),
        "ability_removal_guaranteed":any(primary_combo_launch_with_protection_exact(state,c,ability_removal_tags) for c in guaranteed),
        "removal_conditional":any(conditional_combo_protection_exact(state,c,removal_tags,2) for c in conditional),
        "commander_recovery":commander_recovery_launch_feasible(state),
    }

def interaction_regressions():
    exact=DevState(["Lava Spike","Desperate Ritual","Dispel"]); exact.turn=5
    exact.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Island","Island"]):
        exact.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(exact)
    assert not primary_combo_launch_with_protection_feasible(exact,"Dispel",{"instant","spell"})
    exact.battlefield.append({"card":"Island","tapped":False,"entered":5})
    assert primary_combo_launch_with_protection_feasible(exact,"Dispel",{"instant","spell"})
    assert not primary_combo_launch_with_protection_feasible(exact,"Turn Aside",{"instant","spell"})
    assert not primary_combo_launch_with_protection_feasible(exact,"Spell Pierce",{"instant","spell"})

    song=DevState(["Lava Spike","Desperate Ritual","Seething Song","Dispel"]); song.turn=4
    song.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Island","Island"]):
        song.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(song)
    assert not primary_combo_launch_with_protection_feasible(song,"Dispel",{"instant"})
    song.battlefield.append({"card":"Island","tapped":False,"entered":4})
    assert primary_combo_launch_with_protection_feasible(song,"Dispel",{"instant"})

    electromancer=DevState(["Lava Spike","Desperate Ritual","Dispel"]); electromancer.turn=4
    electromancer.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2},
                              {"card":"Goblin Electromancer","tapped":False,"entered":3}]
    for i in range(3):
        electromancer.battlefield.append({"card":"Mountain","tapped":False,"entered":i})
    assert primary_combo_launch_feasible(electromancer)
    assert not primary_combo_launch_with_protection_feasible(electromancer,"Dispel",{"instant"})
    electromancer.battlefield.append({"card":"Island","tapped":False,"entered":4})
    assert primary_combo_launch_with_protection_feasible(electromancer,"Dispel",{"instant"})

    pyro=DevState(["Lava Spike","Desperate Ritual","Pyroblast"]); pyro.turn=6
    pyro.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Mountain","Island","Island"]):
        pyro.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_with_protection_exact(pyro,"Pyroblast",{"blue_spell"})
    assert not primary_combo_launch_with_protection_exact(pyro,"Pyroblast",{"instant","spell"})

    dive=DevState(["Lava Spike","Desperate Ritual","Dive Down"]); dive.turn=6
    dive.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Island","Island","Island"]):
        dive.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_with_protection_exact(
        dive,"Dive Down",{"targeted_creature","targeted_permanent"})
    assert not primary_combo_launch_with_protection_exact(
        dive,"Dive Down",{"instant","spell","noncreature"})

    # Turn Aside can counter a spell targeting Guildmage, but cannot answer a
    # targeted activated or triggered ability. Hexproof from Mizzium Skin covers
    # either origin while the Guildmage remains the target.
    turn_aside=DevState(["Lava Spike","Desperate Ritual","Turn Aside"]); turn_aside.turn=6
    turn_aside.battlefield=list(dive.battlefield)
    assert primary_combo_launch_with_protection_exact(
        turn_aside,"Turn Aside",{"spell","targeted_spell","targeted_creature","targeted_permanent"})
    assert not primary_combo_launch_with_protection_exact(
        turn_aside,"Turn Aside",{"ability","targeted_creature","targeted_permanent"})
    broad_counter=DevState(["Lava Spike","Desperate Ritual","Counterspell"]); broad_counter.turn=7
    broad_counter.battlefield=list(dive.battlefield)+[{"card":"Island","tapped":False,"entered":6}]
    assert not primary_combo_launch_with_protection_exact(
        broad_counter,"Counterspell",{"ability","targeted_creature","targeted_permanent"})
    skin=DevState(["Lava Spike","Desperate Ritual","Mizzium Skin"]); skin.turn=6
    skin.battlefield=list(dive.battlefield)
    assert primary_combo_launch_with_protection_exact(
        skin,"Mizzium Skin",{"spell","targeted_spell","targeted_creature","targeted_permanent"})
    assert primary_combo_launch_with_protection_exact(
        skin,"Mizzium Skin",{"ability","targeted_creature","targeted_permanent"})
    assert not primary_combo_launch_with_protection_exact(
        skin,"Mizzium Skin",{"spell","instant","noncreature"})

    lens=DevState([]); lens.turn=6
    lens.battlefield=[{"card":"Prismatic Lens","tapped":False,"entered":2},
                      {"card":"Mountain","tapped":False,"entered":1}]
    assert ready_payment_feasible_exact(lens,need_u=1)
    assert not ready_payment_feasible_exact(lens,generic=1,need_u=1)
    lens.battlefield.append({"card":"Mountain","tapped":False,"entered":2})
    assert ready_payment_feasible_exact(lens,generic=1,need_u=1)

    lens_recovery=DevState(["Lava Spike","Desperate Ritual"]); lens_recovery.turn=9
    lens_recovery.commander_casts=1; lens_recovery.commander_zone=False
    lens_recovery.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2},
                               {"card":"Prismatic Lens","tapped":False,"entered":3}]
    for i,c in enumerate(["Mountain"]*4+["Island"]*4):
        lens_recovery.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert commander_recovery_launch_feasible(lens_recovery)
    lens_recovery.battlefield.remove(next(
        p for p in lens_recovery.battlefield if p["card"]=="Prismatic Lens"))
    assert not commander_recovery_launch_feasible(lens_recovery)

    # Phase-1 exact payer counts nonland mana and Signet conversion without changing
    # the accepted solitaire launch predicate.
    rock=DevState(["Lava Spike","Desperate Ritual","Dispel"]); rock.turn=6
    rock.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2},
                      {"card":"Sky Diamond","tapped":False,"entered":3}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Island","Island"]):
        rock.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_with_protection_exact(rock,"Dispel",{"instant"})
    assert ready_payment_feasible_exact(rock,generic=2,need_u=1,need_r=3)

    signet=DevState([]); signet.turn=6
    signet.battlefield=[{"card":"Izzet Signet","tapped":False,"entered":2}]
    for i,c in enumerate(["Island","Mountain","Mountain","Ash Barrens","Ash Barrens"]):
        signet.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert ready_payment_feasible_exact(signet,generic=2,need_u=1,need_r=3)
    signet.battlefield.pop(0)
    assert not ready_payment_feasible_exact(signet,generic=2,need_u=1,need_r=3)

    soft=DevState(["Lava Spike","Desperate Ritual","Spell Pierce"]); soft.turn=6
    soft.battlefield=list(exact.battlefield)
    assert conditional_combo_protection_exact(soft,"Spell Pierce",{"noncreature"},2)
    assert not protection_covers("Spell Pierce",{"noncreature"})

    recover=DevState(["Lava Spike","Desperate Ritual"]); recover.turn=9
    recover.commander_casts=1; recover.commander_zone=False
    recover.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain"]*4+["Island"]*5):
        recover.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert commander_recovery_launch_feasible(recover)
    recover.battlefield.pop()
    assert not commander_recovery_launch_feasible(recover)
    return True

def lethal_regressions():
    # The classic Ritual route launches from five mana with three red sources.
    a=DevState(["Lava Spike","Desperate Ritual"]); a.turn=5
    a.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Island","Island"]):
        a.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(a)
    assert primary_combo_damage_available(a,30)>=30
    # Five mana with only two red sources cannot pay the three red pips.
    c=DevState(["Lava Spike","Desperate Ritual"]); c.turn=5
    c.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,card in enumerate(["Mountain","Mountain","Island","Island","Island"]):
        c.battlefield.append({"card":card,"tapped":False,"entered":i})
    assert not primary_combo_launch_feasible(c)
    # six mana with enough red can launch and kill a 30-life opponent.
    b=DevState(["Lava Spike","Desperate Ritual"]); b.turn=6
    b.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Mountain","Island","Island"]):
        b.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(b)
    assert primary_combo_damage_available(b,30)>=30
    return True



TUTOR_SPECS={
    "Muddle the Mixture":{"mode":"transmute","cost":(1,2,0),"mv":2},
    "Dizzy Spell":{"mode":"transmute","cost":(1,2,0),"mv":1},
    "Drift of Phantasms":{"mode":"transmute","cost":(1,2,0),"mv":3},
    "Merchant Scroll":{"mode":"search","cost":(1,1,0),"blue_instant":True},
}
CARD_MV={"Lava Spike":1,"Desperate Ritual":2,"High Tide":1,"Dramatic Reversal":2,
         "Counterspell":2,"Lose Focus":2,"Snap":2,"Ideas Unbound":2,
         # X is zero while these cards are in the library.
         "Rolling Thunder":1,"Kaervek's Torch":1,
         "Capsize":3,"Murmuring Mystic":4}
BLUE_INSTANTS={"High Tide","Dramatic Reversal","Counterspell","Lose Focus","Snap","Capsize"}
BACKUP_CARDS=("Murmuring Mystic","Rolling Thunder","Kaervek's Torch","Capsize")

def legal_tutor_targets(card,library):
    spec=TUTOR_SPECS[card]
    if spec["mode"]=="transmute":
        return [candidate for candidate in library if CARD_MV.get(candidate)==spec["mv"]]
    return [candidate for candidate in library if candidate in BLUE_INSTANTS]

def backup_tutor_targets(card,library):
    """Return legal declared backup targets without spending or changing policy."""
    legal=set(legal_tutor_targets(card,library))
    return tuple(candidate for candidate in BACKUP_CARDS if candidate in legal)

def tutor_target(card,state,library):
    h=set(state.hand)
    missing=[]
    if "Lava Spike" not in h: missing.append("Lava Spike")
    if "Desperate Ritual" not in h: missing.append("Desperate Ritual")
    legal=legal_tutor_targets(card,library)
    for want in missing:
        if want in legal: return want
    return legal[0] if legal else None

def tutor_regressions():
    s=DevState(["Lava Spike","Muddle the Mixture"])
    lib=["Island","Desperate Ritual","Counterspell"]
    assert tutor_target("Muddle the Mixture",s,lib)=="Desperate Ritual"
    d=DevState(["Desperate Ritual","Dizzy Spell"])
    lib=["Island","Lava Spike","High Tide"]
    assert tutor_target("Dizzy Spell",d,lib)=="Lava Spike"
    m=DevState(["Merchant Scroll"])
    assert tutor_target("Merchant Scroll",m,["Mountain","High Tide"])=="High Tide"
    # Non-combo tutors must not be spent by the primary-pair completion policy.
    m.turn=3
    m.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Island","tapped":False,"entered":2}]
    assert choose_tutor(m,["Mountain","High Tide"]) is None
    d2=DevState(["Desperate Ritual","Dizzy Spell"]); d2.turn=4
    d2.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Island","tapped":False,"entered":2},{"card":"Island","tapped":False,"entered":3}]
    assert choose_tutor(d2,["Mountain","Lava Spike","High Tide"])=="Dizzy Spell"
    return True

def backup_tutor_connectivity_regressions():
    library=["Murmuring Mystic","Rolling Thunder","Kaervek's Torch","Capsize"]
    assert backup_tutor_targets("Muddle the Mixture",library)==()
    assert backup_tutor_targets("Dizzy Spell",library)==(
        "Rolling Thunder","Kaervek's Torch")
    assert backup_tutor_targets("Drift of Phantasms",library)==("Capsize",)
    assert backup_tutor_targets("Merchant Scroll",library)==("Capsize",)

    # Primary-pair completion still outranks passive backup connectivity.
    missing_spike=DevState(["Desperate Ritual","Dizzy Spell"])
    assert tutor_target("Dizzy Spell",missing_spike,
                        ["Rolling Thunder","Lava Spike","Kaervek's Torch"])=="Lava Spike"
    complete=DevState(["Lava Spike","Desperate Ritual","Dizzy Spell"]); complete.turn=4
    complete.battlefield=[{"card":"Island","tapped":False,"entered":1},
                          {"card":"Island","tapped":False,"entered":2},
                          {"card":"Island","tapped":False,"entered":3}]
    assert tutor_target("Dizzy Spell",complete,library)=="Rolling Thunder"
    assert choose_tutor(complete,library) is None
    return True



def pay_tutor_cost(state,card):
    g,nu,nr=TUTOR_SPECS[card]["cost"]
    return pay_colored_mutating(state,generic=g,need_u=nu,need_r=nr)

def execute_tutor(state,library,card,rng):
    if card not in state.hand: return None,library
    target=tutor_target(card,state,library)
    if target is None or not pay_tutor_cost(state,card): return None,library
    state.hand.remove(card)
    # Transmute discards the card; Merchant Scroll resolves to graveyard. Graveyard zone
    # is not yet persistent on DevState, so telemetry only records consumption here.
    idx=library.index(target)
    state.hand.append(library.pop(idx))
    rng.shuffle(library)
    return target,library

def choose_tutor(state,library):
    # Spend tutor mana only when the tutor can directly fetch the missing primary combo half.
    h=set(state.hand)
    for card in ("Dizzy Spell","Muddle the Mixture","Merchant Scroll","Drift of Phantasms"):
        if card not in h:
            continue
        target=tutor_target(card,state,library)
        if target not in {"Lava Spike","Desperate Ritual"}:
            continue
        g,nu,nr=TUTOR_SPECS[card]["cost"]
        if can_pay_simple(state,generic=g,need_u=nu,need_r=nr):
            return card
    return None

def tutor_execution_regressions():
    import random as _r
    s=DevState(["Lava Spike","Muddle the Mixture"]); s.turn=4
    s.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Island","tapped":False,"entered":2},{"card":"Island","tapped":False,"entered":3}]
    target,lib=execute_tutor(s,["Mountain","Desperate Ritual","Counterspell"],"Muddle the Mixture",_r.Random(1))
    assert target=="Desperate Ritual" and "Desperate Ritual" in s.hand and "Muddle the Mixture" not in s.hand
    return True

def combo_assembly_metrics(state):
    h=set(state.hand)
    spike="Lava Spike" in h
    ritual="Desperate Ritual" in h
    pair=spike and ritual
    guild_action=can_pay_simple(state,need_u=1,need_r=1)
    return {
        "spike":spike,
        "ritual":ritual,
        "pair":pair,
        "pair_guild_action":pair and guild_action,
    }

def combo_assembly_regressions():
    s=DevState(["Lava Spike","Desperate Ritual","Island","Mountain"])
    s.begin_turn(); s.play_land("Island"); s.begin_turn(); untap_step(s); s.play_land("Mountain")
    m=combo_assembly_metrics(s)
    assert m["spike"] and m["ritual"] and m["pair"] and m["pair_guild_action"]
    z=DevState(["Lava Spike","Island"])
    z.begin_turn(); z.play_land("Island")
    assert not combo_assembly_metrics(z)["pair"]
    return True

BACKUP_ROW_KEYS=("mystic_present","mystic_castable","rolling_present","rolling_x",
                 "torch_present","torch_x","capsize_present","capsize_buyback")

def simulate_one(cards, rng, through=6, include_interaction=False, include_backup=False):
    library=list(cards); rng.shuffle(library)
    hand=library[:7]; library=library[7:]
    snow_basics="Snow-Covered Island" in cards or "Snow-Covered Mountain" in cards
    s=DevState(hand,snow_basics=snow_basics)
    rows=[]
    first_lethal_turn=None
    for turn in range(1,through+1):
        draw=library.pop(0) if library else None
        s.begin_turn(draw)
        # Start-of-main telemetry: after untap/draw, before land play or spending.
        untap_step(s)
        start_u,start_r,start_uu=color_flags(s)
        start_guildmage=can_pay_simple(s,need_u=1,need_r=1)
        # Actionable state: make the policy's legal land play/fetch, but spend no optional mana.
        land=choose_land(s)
        if land: s.play_land(land)
        for fetch in ("Evolving Wilds","Terramorphic Expanse"):
            if any(p["card"]==fetch for p in s.battlefield): fetch_basic(s,fetch)
        action_u,action_r,action_uu=color_flags(s)
        action_guildmage=can_pay_simple(s,need_u=1,need_r=1)
        hand_actions=actual_hand_action_metrics(s)
        snow_metrics=snow_readiness_metrics(s)
        combo=combo_assembly_metrics(s)
        combo_pair_commander_ready=combo["pair"] and guildmage_on_battlefield(s)
        combo_lethal=primary_combo_launch_feasible(s)
        interaction=interaction_readiness_metrics(s) if include_interaction else None
        backup=commander_independent_readiness(s) if include_backup else None
        if combo_lethal and first_lethal_turn is None:
            first_lethal_turn=turn
        first_lethal_now=(first_lethal_turn==turn)
        lethal_by_now=(first_lethal_turn is not None and first_lethal_turn<=turn)
        holds=hold_up_metrics(s)
        ht_castable,ht_productive,ht_post,ht_gain=high_tide_metrics(s)
        # Cast at most one information-limited selection spell before optional infrastructure.
        selected,library,sel_seen,sel_drawn=cast_one_selection(s,library)
        tutor_used=choose_tutor(s,library)
        tutor_found=None
        if tutor_used:
            tutor_found,library=execute_tutor(s,library,tutor_used,rng)
        commander_deployed=deploy_guildmage(s)
        # Finish optional mana development without replaying a land.
        rock=choose_mana_permanent(s)
        if rock: cast_mana_permanent_unified(s,rock)
        out={"land":land,"mana_permanent":rock,"reversal":reversal_threshold(s)}
        residual_u,residual_r,residual_uu=color_flags(s)
        neutral,positive,gross=reversal_threshold(s)
        islands=sum(high_tide_island(p["card"]) for p in s.lands())
        row={"turn":turn,"lands":len(s.lands()),"selection_cast":selected is not None,
                     "commander_deployed":commander_deployed,"commander_battlefield":guildmage_on_battlefield(s),
                     "tutor_used":tutor_used is not None,"tutor_found_combo":tutor_found in {"Lava Spike","Desperate Ritual"},
                     "selection_seen":sel_seen,"selection_drawn":sel_drawn,
                     "start_U":start_u,"start_R":start_r,"start_UU":start_uu,
                     "action_U":action_u,"action_R":action_r,"action_UU":action_uu,
                     "residual_U":residual_u,"residual_R":residual_r,"residual_UU":residual_uu,
                     "guildmage_start":start_guildmage,"guildmage_action":action_guildmage,
                     "uu_spell_present":hand_actions["uu_spell_present"],"uu_spell_exec":hand_actions["uu_spell_exec"],
                     "u_spell_present":hand_actions["u_spell_present"],"u_spell_exec":hand_actions["u_spell_exec"],
                     "r_spell_present":hand_actions["r_spell_present"],"r_spell_exec":hand_actions["r_spell_exec"],
                     "ritual_present":hand_actions["ritual_present"],"spike_present":hand_actions["spike_present"],
                     "snow_permanents":snow_metrics["snow_permanents"],"skred_damage":snow_metrics["skred_damage"],
                     "skred_live":snow_metrics["skred_live"],"skred_3plus":snow_metrics["skred_3plus"],
                     "combo_pair":combo["pair"],"combo_pair_guild_action":combo["pair_guild_action"],
                     "combo_pair_commander_ready":combo_pair_commander_ready,"combo_lethal":combo_lethal,
                     "first_lethal_now":first_lethal_now,"lethal_by_now":lethal_by_now,
                     "guild_plus_u":holds["guild_plus_u"],"guild_plus_r":holds["guild_plus_r"],"uu_plus_r":holds["uu_plus_r"],
                     "high_tide_castable":ht_castable,"high_tide_productive":ht_productive,
                     "high_tide_post_mana":ht_post,"high_tide_gain":ht_gain,
                     "guildmage_end":can_pay_simple(s,need_u=1,need_r=1),
                     "reversal_neutral":neutral,"reversal_positive":positive,
                     "nonland_gross":gross,"islands":islands}
        if interaction is not None: row.update(interaction)
        if backup is not None: row.update(backup)
        rows.append(row)
    return rows

def empty_backup_aggregate():
    return {"mystic_present":0,"mystic_castable":0,
            "rolling_present":0,"rolling_live":0,"rolling_5plus":0,"rolling_x_sum":0,
            "torch_present":0,"torch_live":0,"torch_5plus":0,"torch_x_sum":0,
            "capsize_present":0,"capsize_buyback":0}

def accumulate_backup_metrics(aggregate,row):
    for key in ("mystic_present","mystic_castable","rolling_present",
                "torch_present","capsize_present","capsize_buyback"):
        aggregate[key]+=int(row[key])
    aggregate["rolling_live"]+=int(row["rolling_x"]>0)
    aggregate["rolling_5plus"]+=int(row["rolling_x"]>=5)
    aggregate["rolling_x_sum"]+=row["rolling_x"]
    aggregate["torch_live"]+=int(row["torch_x"]>0)
    aggregate["torch_5plus"]+=int(row["torch_x"]>=5)
    aggregate["torch_x_sum"]+=row["torch_x"]

def simulate_sample(cards, samples=10000, seed=SEED, through=10,
                    include_interaction=False, include_backup=False):
    rng=random.Random(seed)
    agg={t:{"n":0,"selection_cast":0,"commander_deployed":0,"commander_battlefield":0,"tutor_used":0,"tutor_found_combo":0,"selection_seen_sum":0,"selection_drawn_sum":0,"start_U":0,"start_R":0,"start_UU":0,"action_U":0,"action_R":0,"action_UU":0,
            "residual_U":0,"residual_R":0,"residual_UU":0,
            "guildmage_start":0,"guildmage_action":0,"guildmage_end":0,
            "uu_spell_present":0,"uu_spell_exec":0,"u_spell_present":0,"u_spell_exec":0,
            "r_spell_present":0,"r_spell_exec":0,"ritual_present":0,"spike_present":0,
            "snow_permanents_sum":0,"skred_damage_sum":0,"skred_live":0,"skred_3plus":0,
            "combo_pair":0,"combo_pair_guild_action":0,"combo_pair_commander_ready":0,"combo_lethal":0,
            "first_lethal_now":0,"lethal_by_now":0,
            "guild_plus_u":0,"guild_plus_r":0,"uu_plus_r":0,
            "high_tide_castable":0,"high_tide_productive":0,"high_tide_post_sum":0,"high_tide_gain_sum":0,
            "reversal_neutral":0,"reversal_positive":0,"lands_sum":0,"islands_sum":0}
         for t in range(1,through+1)}
    interaction_keys=("stack_guaranteed","blue_stack_guaranteed","stack_conditional","removal_guaranteed","ability_removal_guaranteed",
                      "removal_conditional","commander_recovery")
    if include_interaction:
        for a in agg.values(): a.update({k:0 for k in interaction_keys})
    if include_backup:
        for a in agg.values(): a.update(empty_backup_aggregate())
    for _ in range(samples):
        for row in simulate_one(cards,rng,through,include_interaction,include_backup):
            a=agg[row["turn"]]; a["n"]+=1
            a["selection_cast"]+=int(row["selection_cast"])
            a["commander_deployed"]+=int(row["commander_deployed"])
            a["commander_battlefield"]+=int(row["commander_battlefield"])
            a["tutor_used"]+=int(row["tutor_used"])
            a["tutor_found_combo"]+=int(row["tutor_found_combo"])
            a["selection_seen_sum"]+=row["selection_seen"]
            a["selection_drawn_sum"]+=row["selection_drawn"]
            for k in ("start_U","start_R","start_UU","action_U","action_R","action_UU","residual_U","residual_R","residual_UU","guildmage_start","guildmage_action","guildmage_end","uu_spell_present","uu_spell_exec","u_spell_present","u_spell_exec","r_spell_present","r_spell_exec","ritual_present","spike_present","combo_pair","combo_pair_guild_action","combo_pair_commander_ready","combo_lethal","first_lethal_now","lethal_by_now","guild_plus_u","guild_plus_r","uu_plus_r","high_tide_castable","high_tide_productive","reversal_neutral","reversal_positive"):
                a[k]+=int(row[k])
            a["snow_permanents_sum"]+=row["snow_permanents"]
            a["skred_damage_sum"]+=row["skred_damage"]
            a["skred_live"]+=int(row["skred_live"])
            a["skred_3plus"]+=int(row["skred_3plus"])
            if include_interaction:
                for k in interaction_keys: a[k]+=int(row[k])
            if include_backup:
                accumulate_backup_metrics(a,row)
            a["high_tide_post_sum"]+=row["high_tide_post_mana"]
            a["high_tide_gain_sum"]+=row["high_tide_gain"]
            a["lands_sum"]+=row["lands"]; a["islands_sum"]+=row["islands"]
    if include_interaction:
        for turn,a in agg.items():
            lethal=a["combo_lethal"]
            for key in interaction_keys:
                if not 0<=a[key]<=lethal:
                    raise ValueError(f"interaction subset violation turn={turn} key={key}")
            if a["removal_guaranteed"]<a["stack_guaranteed"]:
                raise ValueError(f"removal protection ordering violation turn={turn}")
            if a["blue_stack_guaranteed"]<a["stack_guaranteed"]:
                raise ValueError(f"blue protection ordering violation turn={turn}")
            if a["removal_conditional"]<a["stack_conditional"]:
                raise ValueError(f"conditional protection ordering violation turn={turn}")
    if include_backup:
        for turn,a in agg.items():
            for ready,present in (("mystic_castable","mystic_present"),
                                  ("rolling_live","rolling_present"),
                                  ("rolling_5plus","rolling_live"),
                                  ("torch_live","torch_present"),
                                  ("torch_5plus","torch_live"),
                                  ("capsize_buyback","capsize_present")):
                if not 0<=a[ready]<=a[present]<=a["n"]:
                    raise ValueError(f"backup subset violation turn={turn} {ready}<={present}")
    return agg

def simulation_regressions(cards):
    rng=random.Random(12345)
    rows=simulate_one(cards,rng,6)
    assert [r["turn"] for r in rows]==[1,2,3,4,5,6]
    assert len(rows)==6
    return True



def ready_nonland_sources(state):
    out=[]
    for p in state.battlefield:
        if p["tapped"]: continue
        c=p["card"]
        if c=="Everflowing Chalice" and p.get("kicks",0)>0: out.append((p,p.get("kicks",0)))
        elif c in {"Mind Stone","Prismatic Lens","Sky Diamond","Fire Diamond","Star Compass","Fellwar Stone","Network Terminal"}: out.append((p,1))
        elif c in {"Ur-Golem's Eye","Sisay's Ring"}: out.append((p,2))
        elif c in MANA_CREATURES and creature_mana_ready(c,p["entered"],state.turn): out.append((p,1))
    return out

def pay_generic_unified(state, amount):
    land=[(p,2 if p["card"]=="Izzet Boilerworks" else 1) for p,_ in available_land_mana(state)]
    nonland=ready_nonland_sources(state)
    sources=nonland+land  # spend nonland first to preserve colored lands when possible
    if sum(v for _,v in sources)<amount: return False
    need=amount
    for p,v in sources:
        if need<=0: break
        p["tapped"]=True; need-=v
    return True

def cast_mana_permanent_unified(state, card, chalice_kicks=None):
    if card not in state.hand or card not in ROCKS|MANA_CREATURES: return False
    if card=="Everflowing Chalice":
        total=generic_land_capacity(state)+sum(v for _,v in ready_nonland_sources(state))
        kicks=chalice_kicks if chalice_kicks is not None else total//2
        if kicks<1 or not pay_generic_unified(state,2*kicks): return False
        state.hand.remove(card); state.battlefield.append({"card":card,"tapped":False,"entered":state.turn,"kicks":kicks}); return True
    if not pay_generic_unified(state,MANA_COST[card]): return False
    state.hand.remove(card); state.battlefield.append({"card":card,"tapped":rock_enters_tapped(card),"entered":state.turn}); return True

def unified_payment_regressions():
    s=DevState([])
    s.turn=3
    s.battlefield=[
        {"card":"Island","tapped":False,"entered":1},
        {"card":"Mind Stone","tapped":False,"entered":2}]
    s.hand=["Sky Diamond"]
    assert cast_mana_permanent_unified(s,"Sky Diamond")
    ms=next(p for p in s.battlefield if p["card"]=="Mind Stone")
    assert ms["tapped"]
    return True


def readiness_regressions(cards):
    rng=random.Random(24680)
    rows=simulate_one(cards,rng,6)
    assert all("guildmage_start" in x and "guildmage_end" in x for x in rows)
    assert all("start_U" in x and "action_U" in x and "residual_U" in x for x in rows)
    assert all("guildmage_action" in x for x in rows)
    return True


def loop_selection_regression(cards):
    rng=random.Random(777)
    rows=simulate_one(cards,rng,3)
    assert all("selection_cast" in r and "selection_seen" in r for r in rows)
    return True




def five_mana_ritual_route_regressions():
    s=DevState(["Lava Spike","Desperate Ritual"]); s.turn=5
    s.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Island","Island"]):
        s.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(s)
    # Four mana without Electromancer cannot pay Spike+splice and then Ritual.
    z=DevState(["Lava Spike","Desperate Ritual"]); z.turn=4
    z.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Island","Island"]):
        z.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert not primary_combo_launch_feasible(z)
    # A ready Signet turns four lands into the required five mana and third red.
    q=DevState(["Lava Spike","Desperate Ritual"]); q.turn=5
    q.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2},
                   {"card":"Izzet Signet","tapped":False,"entered":3}]
    for i,c in enumerate(["Mountain","Mountain","Island","Island"]):
        q.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(q)
    return True

def seething_song_launch_regressions():
    s=DevState(["Lava Spike","Desperate Ritual","Seething Song"]); s.turn=4
    s.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Island","Island"]):
        s.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(s)
    s.hand.remove("Seething Song")
    assert not primary_combo_launch_feasible(s)
    return True

def multiplayer_table_kill_regressions():
    s=DevState(["Lava Spike","Desperate Ritual"]); s.turn=5
    s.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain","Island","Island"]):
        s.battlefield.append({"card":c,"tapped":False,"entered":i})
    plan=primary_combo_table_damage_plan(s,(30,30,30))
    assert plan=={
        "resolving_spells_by_opponent":(10,10,10),
        "damage_by_opponent":(30,30,30),
        "copies":29,
        "originals":1,
    }
    uneven=primary_combo_table_damage_plan(s,(1,4,31))
    assert uneven["resolving_spells_by_opponent"]==(1,2,11)
    assert uneven["damage_by_opponent"]==(3,6,33)
    assert uneven["copies"]==13
    z=DevState(["Lava Spike","Desperate Ritual"]); z.turn=4
    z.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Island","Island"]):
        z.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_table_damage_plan(z,(30,30,30)) is None
    for invalid in ((),(30,0,30),(30,-1,30),(30,True,30),(30,3.5,30)):
        try:
            primary_combo_table_damage_plan(s,invalid)
        except ValueError:
            pass
        else:
            raise AssertionError(f"accepted invalid opponent lives: {invalid}")
    return True

def commander_independent_readiness_regressions():
    direct=DevState(["Rolling Thunder","Kaervek's Torch"]); direct.turn=6
    for i,card in enumerate(["Mountain","Mountain","Island","Island","Island","Island"]):
        direct.battlefield.append({"card":card,"tapped":False,"entered":i})
    ready=commander_independent_readiness(direct)
    assert ready=={"mystic_present":False,"mystic_castable":False,
                  "rolling_present":True,"rolling_x":5,
                  "torch_present":True,"torch_x":5,
                  "capsize_present":False,"capsize_buyback":False}
    direct.battlefield.append({"card":"Goblin Electromancer","tapped":False,"entered":2})
    reduced=commander_independent_readiness(direct)
    assert reduced["rolling_x"]==6 and reduced["torch_x"]==6

    utility=DevState(["Murmuring Mystic","Capsize"]); utility.turn=6
    for i,card in enumerate(["Island","Island","Island","Island","Mountain","Mountain"]):
        utility.battlefield.append({"card":card,"tapped":False,"entered":i})
    utility_ready=commander_independent_readiness(utility)
    assert utility_ready["mystic_castable"] and utility_ready["capsize_buyback"]
    assert utility_ready["rolling_x"]==0 and utility_ready["torch_x"]==0

    short=DevState(["Murmuring Mystic","Capsize","Rolling Thunder"]); short.turn=3
    for i,card in enumerate(["Island","Island","Mountain"]):
        short.battlefield.append({"card":card,"tapped":False,"entered":i})
    assert commander_independent_readiness(short)=={
        "mystic_present":True,"mystic_castable":False,
        "rolling_present":True,"rolling_x":2,
        "torch_present":False,"torch_x":0,
        "capsize_present":True,"capsize_buyback":False,
    }
    return True

def commander_independent_instrumentation_regressions(cards):
    class FixtureRng:
        def shuffle(self,items):
            return None
        def choice(self,items):
            return items[0]
    front=["Murmuring Mystic","Rolling Thunder","Kaervek's Torch","Capsize",
           "Snow-Covered Island","Snow-Covered Mountain","Snow-Covered Island"]
    ordered=list(cards)
    for card in front:
        ordered.remove(card)
    ordered=front+ordered
    plain=simulate_one(ordered,FixtureRng(),10,False,False)
    observed=simulate_one(ordered,FixtureRng(),10,False,True)
    for baseline,instrumented in zip(plain,observed):
        legacy={k:v for k,v in instrumented.items() if k not in BACKUP_ROW_KEYS}
        assert baseline==legacy
        assert set(BACKUP_ROW_KEYS)<=set(instrumented)
        assert not instrumented["mystic_castable"] or instrumented["mystic_present"]
        assert not instrumented["rolling_x"] or instrumented["rolling_present"]
        assert not instrumented["torch_x"] or instrumented["torch_present"]
        assert not instrumented["capsize_buyback"] or instrumented["capsize_present"]
    aggregate=empty_backup_aggregate()
    aggregate["n"]=len(observed)
    for row in observed:
        accumulate_backup_metrics(aggregate,row)
    assert aggregate["mystic_present"]==len(observed)
    assert aggregate["rolling_present"]==len(observed)
    assert aggregate["torch_present"]==len(observed)
    assert aggregate["capsize_present"]==len(observed)
    return True

def electromancer_lethal_regressions():
    # Electromancer removes both generic costs, enabling a three-red-mana launch.
    s=DevState(["Lava Spike","Desperate Ritual"]); s.turn=4
    s.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2},
                   {"card":"Goblin Electromancer","tapped":False,"entered":3}]
    for i,c in enumerate(["Mountain","Mountain","Mountain"]):
        s.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert primary_combo_launch_feasible(s)
    z=DevState(["Lava Spike","Desperate Ritual"]); z.turn=4
    z.battlefield=[{"card":"Izzet Guildmage","tapped":False,"entered":2}]
    for i,c in enumerate(["Mountain","Mountain","Mountain"]):
        z.battlefield.append({"card":c,"tapped":False,"entered":i})
    assert not primary_combo_launch_feasible(z)
    return True

def first_lethal_regression():
    # Absorption semantics: first lethal is recorded once and cumulative state remains true.
    first=None; seen=[]
    for turn,lethal in [(1,False),(2,False),(3,True),(4,True)]:
        if lethal and first is None: first=turn
        seen.append((turn,first==turn,first is not None and first<=turn))
    assert seen==[(1,False,False),(2,False,False),(3,True,True),(4,False,True)]
    return True

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--deck",default="izzet-science/v0.1-control.md")
    ap.add_argument("--samples",type=int,default=100000)
    ap.add_argument("--seed",type=lambda x:int(x,0),default=SEED)
    ap.add_argument("--diagnostic-size",type=int,default=None,
                    help="Explicit reduced main-deck size for diagnostic exclusion runs only")
    ap.add_argument("--interaction-pilot",action="store_true",
                    help="Emit v0.7 fixed-event interaction-readiness telemetry")
    ap.add_argument("--commander-independent-pilot",action="store_true",
                    help="Emit v0.9 commander-independent readiness telemetry")
    args=ap.parse_args()
    if args.diagnostic_size is None:
        _,cards=parse_deck(Path(args.deck))
    else:
        # Diagnostic parser preserves commander identity but permits only the exact declared reduced size.
        raw=Path(args.deck).read_text()
        tmp=[]
        commander=None; section=""
        for line in raw.splitlines():
            line=line.strip()
            if line=="Commander": section="commander"; continue
            if line.startswith("## "): section=line; continue
            mm=re.match(r"^(\d+) (.+)$",line)
            if not mm: continue
            n,name=int(mm.group(1)),mm.group(2)
            if section=="commander": commander=name
            elif section.startswith("## "): tmp += [name]*n
        if commander!="Izzet Guildmage" or len(tmp)!=args.diagnostic_size:
            raise ValueError(f"diagnostic identity mismatch commander={commander} size={len(tmp)} expected={args.diagnostic_size}")
        cards=tmp
    regressions()
    state_regressions()
    development_regressions()
    payment_regressions()
    fetch_regressions()
    rock_regressions()
    policy_regressions()
    mutating_payment_regressions()
    simulation_regressions(cards)
    unified_payment_regressions()
    readiness_regressions(cards)
    snow_regressions()
    selection_regressions()
    selection_resolution_regressions()
    complex_selection_regressions()
    draw_discard_regressions()
    scheduler_regressions()
    integration_regressions()
    colored_payment_regressions()
    mutable_library_regressions()
    loop_selection_regression(cards)
    combo_assembly_regressions()
    commander_regressions()
    lethal_regressions()
    interaction_regressions()
    tutor_regressions()
    backup_tutor_connectivity_regressions()
    tutor_execution_regressions()
    first_lethal_regression()
    electromancer_lethal_regressions()
    five_mana_ritual_route_regressions()
    seething_song_launch_regressions()
    multiplayer_table_kill_regressions()
    commander_independent_readiness_regressions()
    commander_independent_instrumentation_regressions(cards)
    b,r=opening_baseline(cards,args.samples,args.seed)
    print("seed",hex(args.seed),"samples",args.samples)
    print("land_buckets_0_1_2_3_4plus",b)
    print("rock_buckets_0_1_2_3plus",r)
    agg=simulate_sample(cards,args.samples,args.seed,10,args.interaction_pilot,
                        args.commander_independent_pilot)
    for turn in range(1,11):
        a=agg[turn]; n=a["n"]
        print("turn",turn,"selection_cast",a["selection_cast"]/n,
              "commander_deployed",a["commander_deployed"]/n,"commander_battlefield",a["commander_battlefield"]/n,
              "tutor_used",a["tutor_used"]/n,"tutor_found_combo",a["tutor_found_combo"]/n,
              "avg_selection_seen",a["selection_seen_sum"]/n,"avg_selection_drawn",a["selection_drawn_sum"]/n,
              "start_U",a["start_U"]/n,"start_R",a["start_R"]/n,"start_UU",a["start_UU"]/n,
              "action_U",a["action_U"]/n,"action_R",a["action_R"]/n,"action_UU",a["action_UU"]/n,
              "residual_U",a["residual_U"]/n,"residual_R",a["residual_R"]/n,"residual_UU",a["residual_UU"]/n,
              "guildmage_start",a["guildmage_start"]/n,"guildmage_action",a["guildmage_action"]/n,"guildmage_end",a["guildmage_end"]/n,
              "combo_pair",a["combo_pair"]/n,"combo_pair_guild_action",a["combo_pair_guild_action"]/n,
              "combo_pair_commander_ready",a["combo_pair_commander_ready"]/n,"combo_lethal",a["combo_lethal"]/n,
              "first_lethal_now",a["first_lethal_now"]/n,"lethal_by_now",a["lethal_by_now"]/n,
              "guild_plus_u",a["guild_plus_u"]/n,"guild_plus_r",a["guild_plus_r"]/n,"uu_plus_r",a["uu_plus_r"]/n,
              "uu_exec",a["uu_spell_exec"]/n,"u_exec",a["u_spell_exec"]/n,"r_exec",a["r_spell_exec"]/n,
              "high_tide_castable",a["high_tide_castable"]/n,"high_tide_productive",a["high_tide_productive"]/n,
              "avg_high_tide_gain",a["high_tide_gain_sum"]/n,
              "reversal_neutral",a["reversal_neutral"]/n,"reversal_positive",a["reversal_positive"]/n,
              "avg_snow_permanents",a["snow_permanents_sum"]/n,
              "avg_skred_damage",a["skred_damage_sum"]/n,
              "skred_live",a["skred_live"]/n,"skred_3plus",a["skred_3plus"]/n,
              "avg_lands",a["lands_sum"]/n,"avg_islands",a["islands_sum"]/n)
        if args.interaction_pilot:
            print("interaction","turn",turn,
                  "stack_guaranteed",a["stack_guaranteed"]/n,
                  "blue_stack_guaranteed",a["blue_stack_guaranteed"]/n,
                  "stack_conditional",a["stack_conditional"]/n,
                  "removal_guaranteed",a["removal_guaranteed"]/n,
                  "ability_removal_guaranteed",a["ability_removal_guaranteed"]/n,
                  "removal_conditional",a["removal_conditional"]/n,
                  "commander_recovery",a["commander_recovery"]/n,
                  "stack_loss",(a["combo_lethal"]-a["stack_guaranteed"])/n,
                  "blue_stack_loss",(a["combo_lethal"]-a["blue_stack_guaranteed"])/n,
                  "removal_loss",(a["combo_lethal"]-a["removal_guaranteed"])/n,
                  "ability_removal_loss",(a["combo_lethal"]-a["ability_removal_guaranteed"])/n,
                  "recovery_loss",(a["combo_lethal"]-a["commander_recovery"])/n)
        if args.commander_independent_pilot:
            print("backup","turn",turn,
                  "mystic_present",a["mystic_present"]/n,
                  "mystic_castable",a["mystic_castable"]/n,
                  "rolling_present",a["rolling_present"]/n,
                  "rolling_live",a["rolling_live"]/n,
                  "rolling_5plus",a["rolling_5plus"]/n,
                  "avg_rolling_x",a["rolling_x_sum"]/n,
                  "torch_present",a["torch_present"]/n,
                  "torch_live",a["torch_live"]/n,
                  "torch_5plus",a["torch_5plus"]/n,
                  "avg_torch_x",a["torch_x_sum"]/n,
                  "capsize_present",a["capsize_present"]/n,
                  "capsize_buyback",a["capsize_buyback"]/n)

if __name__=="__main__":
    main()
