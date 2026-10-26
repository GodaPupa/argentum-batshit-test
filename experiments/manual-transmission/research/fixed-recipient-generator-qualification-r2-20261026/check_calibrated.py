"""Auditor synthetic checks; never gameplay or compile evidence."""
import json,tempfile
from pathlib import Path
from receive_calibrated import receive,F,BASE
checks=[]
def rejected(label,fn):
    try:fn()
    except (AssertionError,ValueError,KeyError):checks.append(label);return
    raise AssertionError('accepted '+label)
with tempfile.TemporaryDirectory() as tmp:
    root=Path(tmp);folder=root/'por';folder.mkdir();(folder/'generated-src').mkdir();names=BASE['por']['emitted'];n=len(names);matched=BASE['por']['matched'];m=len(matched)
    text=''.join('// '+name+'\n'+json.dumps(dict(name=name))+'\n\n' for name in names)
    (folder/'por.generated.json').write_text(text);(folder/'POR.golden.json').write_text(''.join('// '+name+'\n'+json.dumps(dict(name=name))+'\n\n' for name in matched))
    for i in range(n):(folder/'generated-src'/f'{i}.kt').write_text('// synthetic only')
    (folder/'compile-exit.txt').write_text('0');(folder/'fidelity-exit.txt').write_text('0')
    build='\n'.join('> Task :mtg-sets:'+t for t in ['emitGeneratedCards','compileGeneratedCardsKotlin','verifyGeneratedCards'])+f'\nemit-all: wrote {n}/200 whole-card drafts\nverifyGeneratedCards: serialised {n} generated card(s)\nBUILD SUCCESSFUL\n'
    gate=f'AUTO-emitted & COMPILED: {m}/{m}\nGAMEPLAY TREE MATCH: {m}\nGAMEPLAY TREE MISMATCH: 0\nGOLDEN DRIFT SUSPECTED: 0\ncapability MISMATCH: 0\nnot emitted (left to hand): 0\nemitted outside golden: {n-m}\nGATE PASS\n'
    (folder/'compile.log').write_text(build);(folder/'fidelity.log').write_text(gate);receive(folder,'por')
    (folder/'fidelity.log').write_text(gate.replace('GOLDEN DRIFT SUSPECTED: 0','GOLDEN DRIFT SUSPECTED: 1'));rejected('green CLI with suspected drift',lambda:receive(folder,'por'));(folder/'fidelity.log').write_text(gate)
    (folder/'por.generated.json').write_text('');rejected('empty emission',lambda:receive(folder,'por'))
    (folder/'por.generated.json').write_text(text+text);rejected('duplicate serialized name',lambda:receive(folder,'por'))
    (folder/'por.generated.json').write_text(text+ '// outside\n{}\n');rejected('new outside golden identity',lambda:receive(folder,'por'))
    (folder/'por.generated.json').write_text(text.replace('// '+names[0]+'\n','// substitute\n'));rejected('missing floor identity',lambda:receive(folder,'por'));(folder/'por.generated.json').write_text(text)
    (folder/'compile.log').write_text(build+'\ne: bad compiler\n');rejected('compiler error',lambda:receive(folder,'por'));(folder/'compile.log').write_text(build)
    (folder/'generated-src/0.kt').unlink();rejected('missing source file',lambda:receive(folder,'por'))
print(json.dumps(dict(state='SYNTHETIC_AUDITOR_CHECKS_ONLY',accepted_shapes=1,rejected=checks),indent=2))
