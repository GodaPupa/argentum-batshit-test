"""Strict calibrated artifact gate; CLI zero exit alone is insufficient."""
import json,re
from pathlib import Path
D=Path(__file__).resolve().parent
F=json.loads((D/'IDENTITY_FLOORS.json').read_text())
BASE=json.loads((D/'CALIBRATED_BASELINE.json').read_text())
def blocks(text):
    parts=re.split(r'^// (.+)\n',text,flags=re.M);assert not parts[0].strip()
    assert (len(parts)-1)%2==0
    pairs=[(parts[i],json.loads(parts[i+1])) for i in range(1,len(parts),2)]
    assert len(pairs)==len(dict(pairs)), 'duplicate serialized identity'
    return dict(pairs)
def receive(folder,code):
    folder=Path(folder)
    for name in ['compile','fidelity']:assert int((folder/(name+'-exit.txt')).read_text())==0
    log=(folder/'compile.log').read_text();gate=(folder/'fidelity.log').read_text()
    assert not re.search(r'^e: |Compilation error|Unresolved reference',log,re.M)
    assert 'BUILD SUCCESSFUL' in log
    for task in ['emitGeneratedCards','compileGeneratedCardsKotlin','verifyGeneratedCards']:
        assert re.search(r'^> Task :mtg-sets:'+task+r'$',log,re.M),task
    generated=blocks((folder/(code+'.generated.json')).read_text())
    golden=blocks((folder/(code.upper()+'.golden.json')).read_text())
    assert generated and set(generated)==set(BASE[code]['emitted'])
    assert set(generated)-set(golden)==set(BASE[code]['outside'])
    assert set(generated)&set(golden)==set(BASE[code]['matched'])
    assert len(golden)==BASE[code]['golden_count']
    emitted=re.findall(r'emit-all: wrote (\d+)/(\d+) whole-card drafts',log);assert len(emitted)==1
    serialized=re.findall(r'verifyGeneratedCards: serialised (\d+) generated card',log);assert len(serialized)==1
    assert int(emitted[0][0])==int(serialized[0])==len(generated)==len(list((folder/'generated-src').glob('*.kt')))
    for label in ['GAMEPLAY TREE MISMATCH','GOLDEN DRIFT SUSPECTED','capability MISMATCH']:
        m=re.findall(re.escape(label)+r':\s+(\d+)',gate);assert m==['0'],(label,m)
    m=re.findall(r'GAMEPLAY TREE MATCH:\s+(\d+)',gate);assert m==[str(len(BASE[code]['matched']))]
    m=re.findall(r'AUTO-emitted & COMPILED:\s+(\d+)/(\d+)',gate);assert m==[(str(len(BASE[code]['matched'])),str(len(golden)))]
    m=re.findall(r'not emitted \(left to hand\):\s+(\d+)',gate);assert m==[str(len(set(golden)-set(generated)))]
    m=re.findall(r'emitted outside golden:\s+(\d+)',gate);assert m==[str(len(BASE[code]['outside']))]
    assert 'GATE PASS' in gate and 'GATE FAIL' not in gate
    return dict(code=code,emitted=sorted(generated),matched_in_golden=sorted(BASE[code]['matched']),compiled_outside_golden_no_gameplay_claim=sorted(BASE[code]['outside']),not_emitted=sorted(set(golden)-set(generated)),golden_count=len(golden),floor=len(F[code]['whole']),mismatches=0,suspected_drift=0,capability_mismatches=0)
