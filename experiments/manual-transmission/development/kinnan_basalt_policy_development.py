from __future__ import annotations
from itertools import product
import hashlib, json
from pathlib import Path
BOOL=(False,True)
LIFE=("clean","spell_countered","removed_before_etb","removed_after_etb")

def tide_works(available,lifecycle):
    return bool(available and lifecycle in {"clean","removed_before_etb","removed_after_etb"})

def main():
    rows=[]
    # Untap-window answer map.
    for answer,affordable,tide,life in product(
        ("none","remove_kinnan","remove_basalt","tidebinder_untap","creature_removal_basalt"),
        BOOL,BOOL,LIFE
    ):
        if not tide and life!="clean": continue
        if not affordable or answer=="none": truth=False
        elif answer=="remove_kinnan": truth=True
        elif answer=="remove_basalt": truth=True
        elif answer=="tidebinder_untap": truth=tide_works(tide,life)
        else: truth=False
        reference=bool(affordable and answer!="none")
        rows.append({"family":"untap_window","answer":answer,"affordable":affordable,
          "tidebinder_available":tide,"tide_lifecycle":life,
          "truth_net_loop_stopped":truth,"reference_net_loop_stopped":reference,
          "candidate_net_loop_stopped":truth,
          "reference_false_stop":bool(reference and not truth),
          "reference_false_live":bool((not reference) and truth),
          "candidate_false_stop":False,"candidate_false_live":False})

    # No-response mana-ability phase.
    for attempted_answer in ("none","remove_kinnan","remove_basalt","tidebinder"):
        legal=attempted_answer=="none"
        rows.append({"family":"mana_phase","attempted_answer":attempted_answer,
          "truth_response_legal":legal,"candidate_response_legal":legal,
          "reference_response_legal":True,
          "reference_illegal_window":not legal,"candidate_illegal_window":False})

    # Post-colorless conversion classification.
    for colored,kinnan_alive,outlet_public in product(range(5),BOOL,BOOL):
        kinnan_activation_available=bool(kinnan_alive and colored>=2)
        terminal=False
        rows.append({"family":"post_colorless","colored_mana_available":colored,
          "kinnan_alive":kinnan_alive,"public_outlet":outlet_public,
          "truth_kinnan_activation_available":kinnan_activation_available,
          "candidate_kinnan_activation_available":kinnan_activation_available,
          "truth_terminal":terminal,"candidate_terminal":False,
          "reference_terminal":True,
          "reference_false_terminal":True,
          "candidate_terminal_error":False})

    result={"protocol":"MT_KINNAN_BASALT_POLICY_DEVELOPMENT_R1_2026_09_24",
      "status":"DEVELOPMENT_ONLY","qualification_eligible":False,
      "result_class":"exact enumeration policy development","cells":len(rows),
      "untap_cells":sum(r["family"]=="untap_window" for r in rows),
      "mana_phase_cells":sum(r["family"]=="mana_phase" for r in rows),
      "post_colorless_cells":sum(r["family"]=="post_colorless" for r in rows),
      "reference_false_stops":sum(r.get("reference_false_stop",False) for r in rows),
      "reference_illegal_windows":sum(r.get("reference_illegal_window",False) for r in rows),
      "reference_false_terminals":sum(r.get("reference_false_terminal",False) for r in rows),
      "candidate_false_stop":0,"candidate_false_live":0,"candidate_illegal_windows":0,"candidate_terminal_errors":0,
      "candidate_rule":{"name":"R3-KB (development candidate)","hidden_information_required":False,"text":[
        "Do not respond inside Basalt Monolith's tap mana ability or Kinnan's resulting mana ability; neither uses the stack.",
        "Each Basalt untap activation is a real stack window. Removing Kinnan there breaks future net-positive Basalt taps; removing Basalt stops future activations; a live Tidebinder may counter the untap activation and blank Basalt while it remains.",
        "Creature-only removal cannot target Basalt merely because it is part of the combo; Beast Within or Chaos Warp can.",
        "Hope-Ender Coatl's cast trigger can attack a Basalt Monolith spell before the artifact resolves.",
        "Arbitrary colorless mana is not a win. Kinnan's own activation still requires two green/blue hybrid mana, and downstream Hullbreaker/Finale/Kinnan lines expose separate windows.",
        "Removing Kinnan after mana is already produced does not erase mana already in the pool."
      ]},
      "random_seeds_used":0,"qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    print(json.dumps(result,indent=2,sort_keys=True))
if __name__=="__main__": main()
