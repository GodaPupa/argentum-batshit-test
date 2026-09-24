from __future__ import annotations
from itertools import product
import hashlib, json
from pathlib import Path

COLORS="WUBRG"
TIDE_LIFECYCLE=("clean","spell_countered","removed_before_etb","removed_after_etb")
BOOL=(False,True)

def tide_current_stop(available,lifecycle):
    return bool(available and lifecycle in {"clean","removed_before_etb","removed_after_etb"})

def power_from_counts(counts):
    return 2 + sum(1 for n in counts if n > 0)

def remove_provider(counts,mask):
    out=list(counts)
    for i,c in enumerate(COLORS):
        if c in mask:
            assert out[i] > 0
            out[i] -= 1
    return tuple(out)

def masks_for(counts):
    live=[COLORS[i] for i,n in enumerate(counts) if n>0]
    masks=[]
    for bits in range(1,1<<len(live)):
        masks.append("".join(live[i] for i in range(len(live)) if bits & (1<<i)))
    return masks

def main():
    # Direct activation timing: source removal never erases the WUBRG activation;
    # a live Tidebinder ETB can counter it. Lifecycle is explicit.
    direct=[]
    for remove_sisay,tide,lifecycle in product(BOOL,BOOL,TIDE_LIFECYCLE):
        if not tide and lifecycle!="clean":
            continue
        truth=tide_current_stop(tide,lifecycle)
        reference=bool(remove_sisay or truth)
        direct.append({
            "family":"activation_timing",
            "remove_sisay_after_activation":remove_sisay,
            "tidebinder_available":tide,
            "tide_lifecycle":lifecycle,
            "truth_current_activation_stopped":truth,
            "reference_current_activation_stopped":reference,
            "candidate_current_activation_stopped":truth,
            "reference_false_stop":bool(reference and not truth),
            "candidate_false_stop":False,
            "candidate_false_live":False,
        })

    # Exhaustive public color-support topology. 0/1/2 represents absent,
    # uniquely represented, or redundantly represented for each color.
    # The removed legendary may contribute any nonempty subset of currently
    # represented colors. If Sisay has already left, LKI freezes the ceiling.
    ceiling=[]
    for counts in product(range(3), repeat=5):
        if not any(counts):
            continue
        before_power=power_from_counts(counts)
        before_ceiling=before_power-1
        for mask in masks_for(counts):
            after_counts=remove_provider(counts,mask)
            live_after_power=power_from_counts(after_counts)
            for sisay_left in BOOL:
                after_power=before_power if sisay_left else live_after_power
                after_ceiling=after_power-1
                delta=before_ceiling-after_ceiling
                truth_class="CEILING_DOWNGRADE" if delta>0 else "NO_CHANGE"
                # Generic reference incorrectly treats any apparent color-bearing
                # removal as a full stop and ignores shared-color/LKI cases.
                reference_full_stop=True
                candidate_full_stop=False
                for hypothetical_mv in range(1,7):
                    target_was_eligible=hypothetical_mv<=before_ceiling
                    target_still_eligible=hypothetical_mv<=after_ceiling
                    ceiling.append({
                        "family":"ceiling_topology",
                        "counts_before":dict(zip(COLORS,counts)),
                        "removed_provider_colors":mask,
                        "sisay_already_left":sisay_left,
                        "power_before":before_power,
                        "ceiling_before":before_ceiling,
                        "power_after":after_power,
                        "ceiling_after":after_ceiling,
                        "truth_class":truth_class,
                        "candidate_class":truth_class,
                        "reference_full_stop":reference_full_stop,
                        "candidate_full_stop":candidate_full_stop,
                        "reference_false_full_stop":reference_full_stop,
                        "candidate_false_full_stop":False,
                        "hypothetical_target_mv":hypothetical_mv,
                        "target_was_eligible":target_was_eligible,
                        "target_excluded_by_ceiling_change":bool(target_was_eligible and not target_still_eligible),
                        "activation_remains_live":True,
                    })

    postfetch=[
      {"line":"Marvin + Ioreth","classification":"ENGINE_LIVE_NOT_TERMINAL","next_windows":["activated ability","untap sequencing"]},
      {"line":"Derevi + Emiel","classification":"ENGINE_LIVE_NOT_TERMINAL","next_windows":["Derevi trigger","Emiel activation"]},
      {"line":"Shang-Chi/Tyvar pseudo-haste","classification":"TIMING_ACCELERATOR_NOT_TERMINAL","next_windows":["activated ability"]},
    ]

    result={
      "protocol":"MT_SISAY_POLICY_DEVELOPMENT_R1_2026_09_24",
      "status":"DEVELOPMENT_ONLY",
      "qualification_eligible":False,
      "result_class":"exact enumeration policy development",
      "direct_activation_cells":len(direct),
      "ceiling_cells":len(ceiling),
      "cells":len(direct)+len(ceiling),
      "reference_direct_false_stops":sum(r["reference_false_stop"] for r in direct),
      "candidate_direct_false_stops":0,
      "reference_ceiling_false_full_stops":sum(r["reference_false_full_stop"] for r in ceiling),
      "candidate_ceiling_false_full_stops":0,
      "candidate_false_live":0,
      "downgrade_cells":sum(r["truth_class"]=="CEILING_DOWNGRADE" for r in ceiling),
      "hypothetical_target_exclusion_cells":sum(r["target_excluded_by_ceiling_change"] for r in ceiling),
      "postfetch":postfetch,
      "candidate_rule":{
        "name":"R3-SY (development candidate)",
        "hidden_information_required":False,
        "text":[
          "Once Sisay's WUBRG activation is on the stack, removing Sisay does not erase it; Tidebinder is the direct current-activation counter when its ETB is live.",
          "If Sisay remains on the battlefield, removing another public legendary before resolution lowers Sisay's power only for colors that were uniquely represented; shared colors do not lower the ceiling.",
          "Ceiling denial is a downgrade, not an automatic full stop. A particular mana-value tier is excluded only if the new search ceiling actually crosses below that tier; the activation can still search lower mana values.",
          "If Sisay has already left, resolution uses last-known power and later removal of other legends does not change that frozen ceiling.",
          "A fetched Marvin/Ioreth, Derevi/Emiel, Shang-Chi, or Tyvar engine piece is not scored as a win; continue through its actual trigger/activated-ability windows."
        ]
      },
      "random_seeds_used":0,
      "qualification_outcomes_exposed":0,
      "script_sha256":hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    }
    print(json.dumps(result,indent=2,sort_keys=True))

if __name__=="__main__":
    main()
