# Industrial Waste — Exploratory payment suffix repair: first-attempt failure audit

Disposition: REPAIR NOT ACCEPTED. This is an isolated local/source qualification, not an R1 game or approval.

First repair commit: df420b157cec9047c7571ee7b44979f321410aa4
Original first attempt: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/38850675717
Retained artifact: https://github.com/GodaPupa/argentum-batshit-test/actions/runs/38850675717/artifacts/12025845188
Original ZIP SHA-256: 78cbe9f723c5d942f09bf4ed505ae40abc19746ae0086179f27a5cf0236a5f91; 3298 bytes; 2 XML members, CRC clean.

- IndustrialWasteExploratoryCropBoulderRepairTest: 1/1 passed. New candidate correctly pays from the reserved Power Plant, next Giant's Boulder selecting green, then submits Crop Rotation using real engine actions.
- IndustrialWasteV2PaymentIntentTest: 13/14 passed; one failed, zero errors/skips. Pre-existing "unassembled Tower funds the bound Prism sacrifice through its canonical conditional output" expected the first mana source with the lowest canonical EntityId.value, but observed another Tower. Original assertion at IndustrialWasteV2PaymentIntentTest.kt:234: expected de79bed8-d85d-4b03-a8b7-e6101723c09b, actual 856b4fda-5299-4b27-bfa5-c227b4a3224d.

Diagnosis: widening admissible multi-step funding options exposed a tie-order mismatch between test-defined canonical source identity and binder's prior action-string tie ordering. The failure is not a rejected game action and not a valid qualification. Correct tie ordering prospectively, retain the prior test unchanged. No original experimental allocation reruns or R1 permits.

Original v1.0, original 2T/3F eight-case archive, and official frozen Industrial Waste R1 are unchanged. The first repair must remain rejected; a changed successor source and a new test attempt are required.
