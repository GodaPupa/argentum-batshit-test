"""Bounded draft forms. Compilation and behavioral witnesses remain mandatory."""
from pathlib import Path
import hashlib, json, sys

def sha(data): return hashlib.sha256(data.encode()).hexdigest()
def replace_once(text, old, new):
    assert text.count(old) == 1, (old, text.count(old))
    return text.replace(old, new, 1)

def forms(normal):
    power = '(projectedState.getPower(entityId) ?: card.baseStats?.basePower ?: 0) >= predicate.min'
    selector = 'return projectedState.getBattlefieldControlledBy(playerId).any { entityId ->'
    start = normal.index('    private fun controlsMatchingPermanent(')
    end = normal.index('\n    /**', start)
    body = normal[start:end]
    def local(old, new): return normal[:start] + replace_once(body, old, new) + normal[end:]
    result = {
      'drop-power': local(power, 'false'),
      'exclusive-threshold': local(power, power.replace('>=', '>')),
      'base-power': local(power, '(card.baseStats?.basePower ?: 0) >= predicate.min'),
      'owner-battlefield': local(selector, 'return state.getBattlefield(playerId).any { entityId ->'),
      'ignore-controller': local(selector, 'return state.getBattlefield().any { entityId ->'),
      'wrong-amount': replace_once(normal, 'if (controls) source.amount else 0', 'if (controls) source.amount - 1 else 0'),
      'drop-red': replace_once(normal, 'effectiveCost = reduceGenericCost(effectiveCost, totalReduction)',
         'effectiveCost = reduceGenericCost(effectiveCost, totalReduction)\n        if (totalReduction > 0) effectiveCost = ManaCost(effectiveCost.symbols.filterNot { Color.RED in it.colors })'),
      'base-type': local('matchesBattlefieldPredicate(entityId, cardDef, predicate, projectedState)',
         'if (predicate == CardPredicate.IsCreature) cardDef.typeLine.isCreature else matchesBattlefieldPredicate(entityId, cardDef, predicate, projectedState)'),
    }
    assert len(set(result.values())) == 8 and normal not in result.values()
    return result

if __name__ == '__main__':
    normal = Path(sys.argv[1]).read_text()
    output = Path(sys.argv[2]); output.mkdir(parents=True, exist_ok=True)
    manifest = {'state':'DRAFT_FORMS_NOT_COMPILED', 'normal_sha256':sha(normal), 'forms':{}}
    for name, content in forms(normal).items():
        (output / (name + '.kt')).write_text(content)
        manifest['forms'][name] = {'sha256':sha(content), 'bytes':len(content.encode())}
    (output / 'FORMS.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest))
