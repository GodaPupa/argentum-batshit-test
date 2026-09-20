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
"Island","Mountain","Command Tower","Ash Barrens","Evolving Wilds",
"Terramorphic Expanse","Izzet Boilerworks","Volatile Fjord",
"Swiftwater Cliffs","Silverbluff Bridge","Lonely Sandbar","Forgotten Cave"
}
TAPPED_LANDS={"Izzet Boilerworks","Volatile Fjord","Swiftwater Cliffs",
"Silverbluff Bridge","Lonely Sandbar","Forgotten Cave"}
ROCKS={"Everflowing Chalice","Fellwar Stone","Mind Stone","Star Compass",
"Sky Diamond","Fire Diamond","Izzet Signet","Network Terminal",
"Ur-Golem's Eye","Sisay's Ring"}
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
"Ur-Golem's Eye":4,"Sisay's Ring":4,"Ornithopter of Paradise":2,
"Silver Myr":2,"Iron Myr":2,"Izzet Guildmage":2
}
def land_enters_tapped(card): return card in TAPPED_LANDS
def high_tide_island(card): return card in {"Island","Volatile Fjord"}
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
    def __init__(self, hand):
        self.hand=list(hand); self.battlefield=[]; self.turn=0; self.land_played=False
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
    if card=="Island": return {"U"}
    if card=="Mountain": return {"R"}
    if card=="Command Tower": return {"U","R"}
    if card in {"Volatile Fjord","Swiftwater Cliffs","Silverbluff Bridge"}: return {"U","R"}
    if card=="Lonely Sandbar": return {"U"}
    if card=="Forgotten Cave": return {"R"}
    if card=="Ash Barrens": return {"C"}
    if card=="Izzet Boilerworks": return {"UR"}
    return set()

def available_land_mana(state):
    basics={x["card"] for x in state.lands() if x["card"] in {"Island","Mountain"}}
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
    basic="Island" if "U" not in controlled else "Mountain"
    state.battlefield.remove(perm)
    state.battlefield.append({"card":basic,"tapped":True,"entered":state.turn})
    return True

def ash_barrens_cycle(state):
    if "Ash Barrens" not in state.hand: return False
    # requires one available mana; conservative land-only payment.
    if not can_pay_simple(state,generic=1): return False
    state.hand.remove("Ash Barrens")
    controlled={c for p in state.lands() for c in land_colors(p["card"])}
    basic="Island" if "U" not in controlled else "Mountain"
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
        elif card=="Sky Diamond": u+=1
        elif card=="Fire Diamond": r+=1
        elif card=="Network Terminal": u+=1  # choose U for Guildmage threshold
        elif card in {"Ur-Golem's Eye","Sisay's Ring"}: c+=2
        elif card=="Star Compass":
            basics={x["card"] for x in state.lands()}
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



LAND_PRIORITY=["Island","Command Tower","Mountain","Ash Barrens","Evolving Wilds",
"Terramorphic Expanse","Volatile Fjord","Swiftwater Cliffs","Silverbluff Bridge",
"Lonely Sandbar","Forgotten Cave","Izzet Boilerworks"]
ROCK_PRIORITY=["Mind Stone","Izzet Signet","Sky Diamond","Fire Diamond","Star Compass",
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
U_HAND={"Brainstorm","Consider","Opt","Ponder","Preordain","Impulse","Dispel","Negate","Memory Lapse","Prohibit","Spell Pierce","Turn Aside","Into the Roil","Blink of an Eye","Echoing Truth","Merchant Scroll","Dizzy Spell","Muddle the Mixture","High Tide","Snap"}
R_HAND={"Lightning Bolt","Galvanic Blast","Skred","Flame Slash","Abrade","Shattering Pulse","Lava Spike","Desperate Ritual","Faithless Looting"}



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
"Thrill of Possibility":(2,2),"Frantic Search":(3,2),"Think Twice":(2,1)
}
INTERACTION_CARDS={"Counterspell","Arcane Denial","Negate","Dispel","Memory Lapse","Deprive","Prohibit","Spell Pierce","Turn Aside","Lose Focus","Lightning Bolt","Galvanic Blast","Skred","Flame Slash","Fire // Ice","Into the Roil","Blink of an Eye","Echoing Truth","Abrade","Shattering Pulse"}
COMBO_CARDS={"Lava Spike","Desperate Ritual"}

def selection_priority(card, state):
    # Uses current state only; caller supplies only legally viewed cards.
    lands_in_hand=sum(c in LANDS for c in state.hand)
    if card in COMBO_CARDS and bool((COMBO_CARDS-{card}) & set(state.hand)): return 90
    if card in INTERACTION_CARDS and not (INTERACTION_CARDS & set(state.hand)): return 80
    if card in LANDS and lands_in_hand==0: return 70
    if card in {"Island","Mountain","Command Tower"}: return 60
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
"Faithless Looting","Thrill of Possibility","Think Twice","Frantic Search"]

def selection_cost(card):
    return {"Ponder":(0,1,0),"Preordain":(0,1,0),"Brainstorm":(0,1,0),
            "Consider":(0,1,0),"Opt":(0,1,0),"Curate":(1,1,0),"Impulse":(1,1,0),
            "Faithless Looting":(0,0,1),"Thrill of Possibility":(1,0,1),
            "Think Twice":(1,1,0),"Frantic Search":(2,1,0)}[card]

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
        if "UR" in colors: opts.append((p,2,{"U","R"}))
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
            has_u=sum(1 for _,_,cs in subset if "U" in cs)
            has_r=sum(1 for _,_,cs in subset if "R" in cs)
            if has_u<need_u or has_r<need_r: continue
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
    return True

def pay_selection_cost(state,card):
    g,nu,nr=selection_cost(card)
    return pay_colored_mutating(state,generic=g,need_u=nu,need_r=nr)

def resolve_selected_card(card,ss,dev):
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

def should_deploy_guildmage(state):
    if guildmage_on_battlefield(state): return False
    # Deploy when affordable and either combo pair is present or hand has interaction to support future turns.
    if not can_pay_simple(state,need_u=1,need_r=1): return False
    h=set(state.hand)
    pair={"Lava Spike","Desperate Ritual"} <= h
    protected=bool(h & INTERACTION_CARDS)
    return pair or protected

def deploy_guildmage(state):
    if not should_deploy_guildmage(state): return False
    if not pay_colored_mutating(state,need_u=1,need_r=1): return False
    state.battlefield.append({"card":"Izzet Guildmage","tapped":False,"entered":state.turn})
    return True

def commander_regressions():
    s=DevState(["Lava Spike","Desperate Ritual"]); s.turn=3
    s.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Mountain","tapped":False,"entered":2}]
    assert deploy_guildmage(s) and guildmage_on_battlefield(s)
    assert all(p["tapped"] for p in s.lands())
    assert not deploy_guildmage(s)
    z=DevState(["Ponder"]); z.turn=3
    z.battlefield=[{"card":"Island","tapped":False,"entered":1},{"card":"Mountain","tapped":False,"entered":2}]
    assert not should_deploy_guildmage(z)
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

def simulate_one(cards, rng, through=6):
    library=list(cards); rng.shuffle(library)
    hand=library[:7]; library=library[7:]
    s=DevState(hand)
    rows=[]
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
        combo=combo_assembly_metrics(s)
        holds=hold_up_metrics(s)
        ht_castable,ht_productive,ht_post,ht_gain=high_tide_metrics(s)
        # Cast at most one information-limited selection spell before optional infrastructure.
        selected,library,sel_seen,sel_drawn=cast_one_selection(s,library)
        commander_deployed=deploy_guildmage(s)
        # Finish optional mana development without replaying a land.
        rock=choose_mana_permanent(s)
        if rock: cast_mana_permanent_unified(s,rock)
        out={"land":land,"mana_permanent":rock,"reversal":reversal_threshold(s)}
        residual_u,residual_r,residual_uu=color_flags(s)
        neutral,positive,gross=reversal_threshold(s)
        islands=sum(high_tide_island(p["card"]) for p in s.lands())
        rows.append({"turn":turn,"lands":len(s.lands()),"selection_cast":selected is not None,
                     "commander_deployed":commander_deployed,"commander_battlefield":guildmage_on_battlefield(s),
                     "selection_seen":sel_seen,"selection_drawn":sel_drawn,
                     "start_U":start_u,"start_R":start_r,"start_UU":start_uu,
                     "action_U":action_u,"action_R":action_r,"action_UU":action_uu,
                     "residual_U":residual_u,"residual_R":residual_r,"residual_UU":residual_uu,
                     "guildmage_start":start_guildmage,"guildmage_action":action_guildmage,
                     "uu_spell_present":hand_actions["uu_spell_present"],"uu_spell_exec":hand_actions["uu_spell_exec"],
                     "u_spell_present":hand_actions["u_spell_present"],"u_spell_exec":hand_actions["u_spell_exec"],
                     "r_spell_present":hand_actions["r_spell_present"],"r_spell_exec":hand_actions["r_spell_exec"],
                     "ritual_present":hand_actions["ritual_present"],"spike_present":hand_actions["spike_present"],
                     "combo_pair":combo["pair"],"combo_pair_guild_action":combo["pair_guild_action"],
                     "guild_plus_u":holds["guild_plus_u"],"guild_plus_r":holds["guild_plus_r"],"uu_plus_r":holds["uu_plus_r"],
                     "high_tide_castable":ht_castable,"high_tide_productive":ht_productive,
                     "high_tide_post_mana":ht_post,"high_tide_gain":ht_gain,
                     "guildmage_end":can_pay_simple(s,need_u=1,need_r=1),
                     "reversal_neutral":neutral,"reversal_positive":positive,
                     "nonland_gross":gross,"islands":islands})
    return rows

def simulate_sample(cards, samples=10000, seed=SEED, through=6):
    rng=random.Random(seed)
    agg={t:{"n":0,"selection_cast":0,"commander_deployed":0,"commander_battlefield":0,"selection_seen_sum":0,"selection_drawn_sum":0,"start_U":0,"start_R":0,"start_UU":0,"action_U":0,"action_R":0,"action_UU":0,
            "residual_U":0,"residual_R":0,"residual_UU":0,
            "guildmage_start":0,"guildmage_action":0,"guildmage_end":0,
            "uu_spell_present":0,"uu_spell_exec":0,"u_spell_present":0,"u_spell_exec":0,
            "r_spell_present":0,"r_spell_exec":0,"ritual_present":0,"spike_present":0,
            "combo_pair":0,"combo_pair_guild_action":0,
            "guild_plus_u":0,"guild_plus_r":0,"uu_plus_r":0,
            "high_tide_castable":0,"high_tide_productive":0,"high_tide_post_sum":0,"high_tide_gain_sum":0,
            "reversal_neutral":0,"reversal_positive":0,"lands_sum":0,"islands_sum":0}
         for t in range(1,through+1)}
    for _ in range(samples):
        for row in simulate_one(cards,rng,through):
            a=agg[row["turn"]]; a["n"]+=1
            a["selection_cast"]+=int(row["selection_cast"])
            a["commander_deployed"]+=int(row["commander_deployed"])
            a["commander_battlefield"]+=int(row["commander_battlefield"])
            a["selection_seen_sum"]+=row["selection_seen"]
            a["selection_drawn_sum"]+=row["selection_drawn"]
            for k in ("start_U","start_R","start_UU","action_U","action_R","action_UU","residual_U","residual_R","residual_UU","guildmage_start","guildmage_action","guildmage_end","uu_spell_present","uu_spell_exec","u_spell_present","u_spell_exec","r_spell_present","r_spell_exec","ritual_present","spike_present","combo_pair","combo_pair_guild_action","guild_plus_u","guild_plus_r","uu_plus_r","high_tide_castable","high_tide_productive","reversal_neutral","reversal_positive"):
                a[k]+=int(row[k])
            a["high_tide_post_sum"]+=row["high_tide_post_mana"]
            a["high_tide_gain_sum"]+=row["high_tide_gain"]
            a["lands_sum"]+=row["lands"]; a["islands_sum"]+=row["islands"]
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
        elif c in {"Mind Stone","Sky Diamond","Fire Diamond","Star Compass","Fellwar Stone","Network Terminal"}: out.append((p,1))
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

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--deck",default="izzet-science/v0.1-control.md")
    ap.add_argument("--samples",type=int,default=100000)
    ap.add_argument("--seed",type=lambda x:int(x,0),default=SEED)
    ap.add_argument("--diagnostic-size",type=int,default=None,
                    help="Explicit reduced main-deck size for diagnostic exclusion runs only")
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
    b,r=opening_baseline(cards,args.samples,args.seed)
    print("seed",hex(args.seed),"samples",args.samples)
    print("land_buckets_0_1_2_3_4plus",b)
    print("rock_buckets_0_1_2_3plus",r)
    agg=simulate_sample(cards,args.samples,args.seed,6)
    for turn in range(1,7):
        a=agg[turn]; n=a["n"]
        print("turn",turn,"selection_cast",a["selection_cast"]/n,
              "commander_deployed",a["commander_deployed"]/n,"commander_battlefield",a["commander_battlefield"]/n,
              "avg_selection_seen",a["selection_seen_sum"]/n,"avg_selection_drawn",a["selection_drawn_sum"]/n,
              "start_U",a["start_U"]/n,"start_R",a["start_R"]/n,"start_UU",a["start_UU"]/n,
              "action_U",a["action_U"]/n,"action_R",a["action_R"]/n,"action_UU",a["action_UU"]/n,
              "residual_U",a["residual_U"]/n,"residual_R",a["residual_R"]/n,"residual_UU",a["residual_UU"]/n,
              "guildmage_start",a["guildmage_start"]/n,"guildmage_action",a["guildmage_action"]/n,"guildmage_end",a["guildmage_end"]/n,
              "combo_pair",a["combo_pair"]/n,"combo_pair_guild_action",a["combo_pair_guild_action"]/n,
              "guild_plus_u",a["guild_plus_u"]/n,"guild_plus_r",a["guild_plus_r"]/n,"uu_plus_r",a["uu_plus_r"]/n,
              "uu_exec",a["uu_spell_exec"]/n,"u_exec",a["u_spell_exec"]/n,"r_exec",a["r_spell_exec"]/n,
              "high_tide_castable",a["high_tide_castable"]/n,"high_tide_productive",a["high_tide_productive"]/n,
              "avg_high_tide_gain",a["high_tide_gain_sum"]/n,
              "reversal_neutral",a["reversal_neutral"]/n,"reversal_positive",a["reversal_positive"]/n,
              "avg_lands",a["lands_sum"]/n,"avg_islands",a["islands_sum"]/n)

if __name__=="__main__":
    main()
