#!/usr/bin/env python3
"""Hash-pinned disposable overlays; never changes refs or executes Kotlin."""
import hashlib,json,pathlib,subprocess,sys
if sys.flags.optimize: raise SystemExit('REFUSED: optimized Python disables assertion gates')
root=pathlib.Path(sys.argv[1]).resolve(); here=pathlib.Path(__file__).resolve().parent
sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
pins={'b5195ad2d090d26a533aef5bf3fa6a486b5128c6':'360c40b490458cfba82d43b29b2fcb0ea0913b68','1e334b9625478689c15a7d153e74d3d805791506':'9ad64c2cf141beca0f1091a801c0f7af3bb1e0c7'}
assert sha in pins
base='ai/src/main/kotlin/com/wingedsheep/ai/engine/'
changes=[]
def rewrite(rel,fn,expected=None):
 p=root/rel; raw=p.read_bytes(); original=subprocess.check_output(['git','show',f'HEAD:{rel}'],cwd=root)
 assert raw==original, f'already modified: {rel}'
 blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
 if expected:assert blob==expected
 new=fn(raw.decode()).encode();assert new!=raw
 p.write_bytes(new)
 changes.append({'path':rel,'base_blob':blob,'original_sha256':hashlib.sha256(raw).hexdigest(),'overlay_sha256':hashlib.sha256(new).hexdigest()})
def once(s,a,b):
 assert s.count(a)==1,(a,s.count(a));return s.replace(a,b)
def strategy(s):
 marker='    private fun bestLandUnlockedPayoffSequence(\n'
 wrapper='''    private fun bestLandUnlockedPayoffSequence(state: GameState, playerId: EntityId): LandUnlockedPayoffSequence? {
        R6DiffObserver.count("searchEvaluations")
        try {
            val answer = r6DiffOriginalSearch(state, playerId)
            R6DiffObserver.result(answer?.landId?.value, answer?.setupCardId?.value, answer?.focalCardId?.value, answer?.projectedScore, answer?.comparisonScore)
            return answer
        } catch (failure: Throwable) {
            R6DiffObserver.count("searchExceptions")
            throw failure
        }
    }
    private fun r6DiffOriginalSearch(
'''
 s=once(s,marker,wrapper)
 s=once(s,'        val passScore = if (pass != null) {','        R6DiffObserver.raw(leaves.map { it.action }, leafScores)\n        val passScore = if (pass != null) {')
 if sha.startswith('1e'):
  s=once(s,'landPayoffForDecision = { landPayoffForDecision.value },','landPayoffForDecision = { R6DiffObserver.count("searchRequests"); landPayoffForDecision.value },')
 else:
  # R5 each actual helper call is also a request; wrapper used only at original call sites.
  s=once(s,'        R6DiffObserver.count("searchEvaluations")','        R6DiffObserver.count("searchRequests")\n        R6DiffObserver.count("searchEvaluations")')
 return s
rewrite(base+'Strategist.kt',strategy,pins[sha])
def simulator(s):
 for declaration,counter in [('fun simulate(state: GameState, action: GameAction): SimulationResult {','simulations'),('fun simulateDecision(state: GameState, response: DecisionResponse): SimulationResult {','decisionSimulations'),('fun getLegalActions(state: GameState, playerId: EntityId): List<LegalAction> {','enumerations')]:
  s=once(s,declaration,declaration+'\n        R6DiffObserver.count("'+counter+'")')
 # Count actual exhausted-return sites without evaluating any additional condition.
 needle='return stoppedAtLimit(current, allEvents, iterations)';assert s.count(needle)==4
 return s.replace(needle,'R6DiffObserver.count("stoppedAtLimit"); '+needle)
rewrite(base+'GameSimulator.kt',simulator,'fc61c63000993d58b2f433722d1bdf4fd7baac27')
def budget(s):
 a='    fun expired(): Boolean = deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos'
 b='''    fun expired(): Boolean {
        val result = deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos
        if (result) com.wingedsheep.ai.engine.R6DiffObserver.count("expiredTrue")
        return result
    }'''
 return once(s,a,b)
rewrite(base+'budget/DecisionBudget.kt',budget,'4f3b8a25c752ab6e338d7004bbaf104f1903df1d')
for rel,source in [(base+'R6DiffObserver.kt','observer.kt'),('ai/src/test/kotlin/com/wingedsheep/ai/engine/R5R6DecisionDifferentialTest.kt','probe.kt')]:
 p=root/rel;assert not p.exists();p.write_bytes((here/source).read_bytes());changes.append({'path':rel,'added_sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(root/'r6-diff-overlay.json').write_text(json.dumps({'source':sha,'changes':changes},indent=2)+'\n')
print(json.dumps({'source':sha,'changes':changes},indent=2))
