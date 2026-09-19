# Executable Action Gate v1 — Audit

Source: 82aab7f6836ada3372d3b4286dd8a337b99acaf1
Run: 35453331187
Samples: 100000
Seed: 0x1A22E7001

Actual-hand execution confirms the basic-ratio tradeoff.

T3 executable:
UU spell: control 18.500%, R0.5 17.647%, R1 16.765%
U spell: 86.404%, 85.581%, 84.633%
R spell: 37.629%, 40.669%, 43.245%
Guildmage actionable: 56.822%, 60.726%, 63.883%

T4:
UU spell: 23.233%, 22.433%, 21.572%
U spell: 90.220%, 89.734%, 89.138%
R spell: 46.923%, 49.683%, 51.987%
Guildmage: 68.718%, 72.320%, 75.205%

T6:
UU spell: 30.625%, 30.051%, 29.414%
U spell: 94.644%, 94.395%, 94.116%
R spell: 61.021%, 62.949%, 64.550%
Guildmage: 82.908%, 85.276%, 87.176%

Interpretation:
R0.5 gives materially more executable red actions and Guildmage access while the loss in actual UU-spell execution is under 1 percentage point at T3/T4 and 0.574 pp at T6. Single-U execution remains extremely high and loses <1 pp by T3 and only 0.249 pp by T6.

R1 continues the same direction but incurs roughly twice the blue/UU cost. R0.5 captures a substantial share of the red/Guildmage gain with smaller execution loss.

Scope limitation:
Hold-up metrics requiring simultaneous payment (Guildmage plus U/R, UU spell plus R) are not yet implemented despite being named in the protocol. Do not claim them from this sample.

Disposition:
R0_5_EXECUTABLE_ACTION_HYPOTHESIS_PASS
R0_5_PROMOTION_JUSTIFIED_PENDING_SIMULTANEOUS_HOLDUP_CHECK
R1_NOT_REJECTED_BUT_NOT_PREFERRED_YET
