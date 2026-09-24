from __future__ import annotations
from dataclasses import dataclass
from fractions import Fraction
from math import comb
import hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "opponents/hashaton/source-freeze.json"

CREATURES = {
"Archon of Emeria","Baleful Force","Chancellor of the Annex","Consecrated Sphinx",
"Doctor Octopus, Master Planner","Eidolon of Rhetoric","Elesh Norn, Grand Cenobite",
"Esper Sentinel","Ethersworn Canonist","Glory","Gran-Gran","Hapless Researcher",
"Hydroelectric Specimen // Hydroelectric Laboratory","Jin-Gitaxias, Core Augur",
"M.O.D.O.K.","Nezahal, Primal Tide","Ori, Plate Stacker","Phyrexian Censor",
"Pollywog Prodigy","Putrid Imp","Razaketh, the Foulblooded","Rune-Scarred Demon",
"Smirking Spelljacker","Solitude","Stern Constable","Subtlety","Sun Titan",
"Teferi, Mage of Zhalfir","Thassa's Oracle","Tireless Tribe",
"Valgavoth, Terror Eater","Vilis, Broker of Blood",
}
SINGLE_COST_OUTLETS = [
"Putrid Imp","Stern Constable","Tireless Tribe","The Underworld Cookbook","Bloodthorn Flail"
]
RESOLUTION_OUTLETS = ["Hapless Researcher","Gran-Gran","M.O.D.O.K."]
ANSWER_PROFILES = ["none","removal_only","tidebinder_only","removal_plus_tidebinder"]

@dataclass(frozen=True)
class Cell:
    outlet_class: str
    outlet: str
    payload: str
    answers: str

def choose(n, k):
    return comb(n, k) if 0 <= k <= n else 0

def led_creature_distribution(hand_size: int):
    # LED is already public/on battlefield, so remove the one LED from the exact 99.
    # LED is not a creature: population 98, creatures remain 32.
    N, K, n = 98, 32, hand_size
    den = choose(N, n)
    rows = []
    for k in range(0, n + 1):
        num = choose(K, k) * choose(N-K, n-k)
        if num:
            rows.append({"creatures": k, "numerator": num, "denominator": den,
                         "probability": float(Fraction(num, den))})
    return rows

def foil_creature_alt_probability(hand_size: int):
    # Exact-list development sensitivity. Unconditional h-card hand from the 99.
    # Foil is 1 card. Exact list has four Island-subtype cards usable for Foil's
    # "discard an Island card" alternative-cost component: Hallowed Fountain,
    # Tundra, Underground Sea, Watery Grave. The 32 creatures are disjoint from
    # those cards and Foil.
    N, I, C, h = 99, 4, 32, hand_size
    if h < 3:
        return {"numerator":0,"denominator":choose(N,h),"probability":0.0}
    # Force Foil present, then choose h-1 from the remaining 98 with >=1 Island
    # and >=1 creature via inclusion-exclusion.
    fav = choose(98,h-1)-choose(94,h-1)-choose(66,h-1)+choose(62,h-1)
    den = choose(N,h)
    return {"numerator":fav,"denominator":den,"probability":float(Fraction(fav,den))}

def truth_current_attempt(cell: Cell):
    has_removal = cell.answers in {"removal_only","removal_plus_tidebinder"}
    has_tide = cell.answers in {"tidebinder_only","removal_plus_tidebinder"}

    if cell.outlet_class == "single_cost":
        # Discard cost is paid before priority returns. Ordinary source removal
        # cannot prevent this already-created trigger. A clean Tidebinder ETB can
        # counter the single Hashaton trigger.
        return "STOP" if has_tide else "LIVE_TOKEN_BRANCH"

    if cell.outlet_class == "resolution":
        # Before the ability/trigger that performs the discard resolves, source
        # removal is a genuine pre-discard window. Tidebinder can also counter
        # the creature's activated/triggered outlet ability.
        return "STOP" if (has_removal or has_tide) else "LIVE_TOKEN_BRANCH"

    raise ValueError(cell.outlet_class)

def no_hashaton_overlay_reference(cell: Cell):
    has_removal = cell.answers in {"removal_only","removal_plus_tidebinder"}
    has_tide = cell.answers in {"tidebinder_only","removal_plus_tidebinder"}
    # Deliberately generic fallback used only as a DEVELOPMENT reference:
    # if source removal is available, treat killing Hashaton as the stop;
    # otherwise use Tidebinder if available.
    if has_removal:
        return "STOP"
    if has_tide:
        return "STOP"
    return "LIVE_TOKEN_BRANCH"

def candidate_r3_ht_classification(cell: Cell):
    # Candidate is a timing/classification overlay, not a forced action sequence.
    return truth_current_attempt(cell)

def main():
    source = json.loads(SOURCE.read_text())
    assert len(source["mainboard"]) == 99
    assert CREATURES.issubset(set(source["mainboard"]))
    assert len(CREATURES) == 32

    cells = []
    for outlet in SINGLE_COST_OUTLETS:
        for payload in sorted(CREATURES):
            for answers in ANSWER_PROFILES:
                cells.append(Cell("single_cost", outlet, payload, answers))
    for outlet in RESOLUTION_OUTLETS:
        for payload in sorted(CREATURES):
            for answers in ANSWER_PROFILES:
                cells.append(Cell("resolution", outlet, payload, answers))

    ref_false_stop = 0
    candidate_false_stop = 0
    disagreements = []
    for cell in cells:
        truth = truth_current_attempt(cell)
        ref = no_hashaton_overlay_reference(cell)
        cand = candidate_r3_ht_classification(cell)
        if ref == "STOP" and truth != "STOP":
            ref_false_stop += 1
            disagreements.append({
                "outlet_class":cell.outlet_class,"outlet":cell.outlet,
                "payload":cell.payload,"answers":cell.answers,
                "reference":ref,"truth":truth
            })
        if cand == "STOP" and truth != "STOP":
            candidate_false_stop += 1

    led = {}
    for h in [4,5,6,7]:
        dist = led_creature_distribution(h)
        den = dist[0]["denominator"]
        p_ge1 = sum(Fraction(r["numerator"],den) for r in dist if r["creatures"] >= 1)
        p_ge2 = sum(Fraction(r["numerator"],den) for r in dist if r["creatures"] >= 2)
        led[str(h)] = {
            "distribution":dist,
            "p_at_least_1_creature":float(p_ge1),
            "p_at_least_2_creatures":float(p_ge2),
            "interpretation":"With >=2 creatures discarded to LED, one Tidebinder can counter one Hashaton trigger but cannot erase sibling triggers already on the stack."
        }

    foil = {str(h): foil_creature_alt_probability(h) for h in [5,6,7,8,9]}

    result = {
        "protocol":"MT_HASHATON_POLICY_DEVELOPMENT_R1_2026_09_23",
        "status":"DEVELOPMENT_ONLY",
        "result_class":"exact enumeration + development sensitivity",
        "qualification_eligible":False,
        "source_digest":source["digests"]["commander_plus_99_sha256"],
        "cells_enumerated":len(cells),
        "no_hashaton_overlay_reference_false_stop_cells":ref_false_stop,
        "candidate_r3_ht_false_stop_cells":candidate_false_stop,
        "candidate_rule":{
            "name":"R3-HT (development candidate)",
            "text":[
                "A public discard-as-activation-cost outlet can pay its discard cost before priority returns; ordinary removal of Hashaton does not erase the resulting Hashaton trigger.",
                "If a discard occurs only when an already-stacked ability/trigger resolves, ordinary removal before that object resolves is a genuine pre-discard prevention window.",
                "Once a Hashaton trigger exists, source removal alone is not a current-trigger stop.",
                "Lion's Eye Diamond may create multiple Hashaton triggers at once; one Tidebinder can counter one and blank Hashaton while it remains, but sibling triggers already on the stack survive.",
                "If Foil's alternate cost discards a creature while Hashaton is live, that discard creates a Hashaton trigger during the counterwar.",
                "Tidebinder's blanking rider lasts only while Tidebinder remains; if Tidebinder leaves before its ETB resolves, the targeted trigger is still countered but Hashaton is not blanked."
            ]
        },
        "led_exact_hidden_hand_sensitivity":led,
        "foil_alt_cost_exact_hidden_hand_sensitivity":foil,
        "disagreement_examples":disagreements[:16],
        "notes":[
            "The reference is not claimed to be accepted Race R3 behavior; it is a deliberately generic source-removal-first fallback used only to locate timing defects.",
            "No opponent protection/counterwar probability is used in the candidate promotion decision at this stage.",
            "No win rate, matchup rate, or full-game result is reported.",
            "No random seeds are used."
        ],
        "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    print(json.dumps(result,indent=2,sort_keys=True))

if __name__ == "__main__":
    main()
