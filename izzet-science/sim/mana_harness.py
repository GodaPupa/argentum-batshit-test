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
    if rock: cast_mana_permanent_mutating(state,rock)
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

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--deck",default="izzet-science/v0.1-control.md")
    ap.add_argument("--samples",type=int,default=100000)
    ap.add_argument("--seed",type=lambda x:int(x,0),default=SEED)
    args=ap.parse_args()
    regressions()
    state_regressions()
    development_regressions()
    payment_regressions()
    fetch_regressions()
    rock_regressions()
    policy_regressions()
    mutating_payment_regressions()
    _,cards=parse_deck(Path(args.deck))
    b,r=opening_baseline(cards,args.samples,args.seed)
    print("seed",hex(args.seed),"samples",args.samples)
    print("land_buckets_0_1_2_3_4plus",b)
    print("rock_buckets_0_1_2_3plus",r)

if __name__=="__main__":
    main()
