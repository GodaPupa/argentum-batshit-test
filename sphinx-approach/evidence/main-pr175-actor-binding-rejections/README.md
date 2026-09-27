# PR175 original actor receiving guard failures

These records preserve two actual attempt-1 workflow failures after the separately accepted canonical PR205 integration. Both workflows checked out PR175's actual main test merge `22cf67791fda69a65d7e1eeb76c2ae369407cb01`, tree `3b4e50dd7a0fe484b796bb191bd848b048bea12b`.

| Workflow | Run | Original artifact | Original bytes | Result |
| --- | --- | --- | ---: | --- |
| Shared Actor Input Qualification | 36284779284 | 10920092743 | 13,683 | Binding rejected; 50-case bank skipped |
| Sphinx Stage E Actor Receiving | 36284779421 | 10919607933 | 19,637 | Binding rejected; 136-case bank skipped |

The complete original ZIPs contain nine members in total, including the actual attempt receipts and archived helper/authority files. Both receipts record `REJECTED_BINDING_NO_QUALIFICATION`, exact checkout identity and an empty checkout status. The error's 25 named paths are exactly the canonical contract's eleven qualification paths plus fourteen main-only control/evidence paths. The independently fetched exact-source bytes match every archived source member.

Neither test bank ran. No test XML, passed cases, Stage-E games or gameplay outcomes are supplied by these failures. The Sphinx workflow's always-run collector additionally failed because `source-before.json` had not been created; that secondary failure is preserved. Shared actor collection was skipped. Complete connector-decoded job logs are explicitly distinguished from original artifact ZIP bytes.

`original-two-actor-rejections.zip` preserves the two original ZIPs, two complete decoded logs as gzip files, actual GitHub metadata, exact independently fetched source, the actual fourteen-path comparison, and the read-only audit script. The archive index binds every retained byte stream. `actual-failure-audit.json` records the factual author audit; independent preservation review must be separately identified.

`prospective-main-binding-proposal.json` is an unadopted proposal. It proposes a separate exact main-receiving context while preserving the canonical descriptor/helper and eleven-path allowlist. It supplies no concrete implementation, fresh fixture allowance, dispatch permission, main integration acceptance, resource command or gameplay authority. A future source change needs ownership assignment and independent review of the actual source, main composition and activation scope. No unchanged workflow rerun was requested or performed.

The accepted canonical source remains `8ed9787ad75b8bc6c2438a8c0b526ff822aa776e`, tree `4693352c4a22d9f3b35eb47048c8edaabe8157f1`. These main test-merge guard rejections do not amend its acceptance or create a negative deck verdict. No deck-performance evidence changed.
