"""Reconstruct original CI ZIPs and audit their contents. Starts no games."""
from pathlib import Path
import hashlib, io, json, re, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
def sha(data): return hashlib.sha256(data).hexdigest()

def audit(data, role):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names=z.namelist()
        assert len(names)==len(set(names)) and z.testzip() is None
        hashed=set()
        for name in names:
            if name.endswith('SHA256SUMS'):
                for line in z.read(name).decode().splitlines():
                    digest,member=line.split(maxsplit=1)
                    member=name[:-len('SHA256SUMS')]+member
                    assert sha(z.read(member))==digest and member not in hashed
                    hashed.add(member)
        if role not in ('source','verdict'):
            assert hashed=={n for n in names if not n.endswith('SHA256SUMS')}
        cases=[]; classes=0
        for name in names:
            if '/TEST-' in name and name.endswith('.xml'):
                s=ET.fromstring(z.read(name)); rows=s.findall('.//testcase'); classes+=1
                assert int(s.get('tests'))==len(rows)
                for key,tag in [('failures','failure'),('errors','error'),('skipped','skipped')]:
                    assert int(s.get(key))==len(s.findall('.//'+tag))==0,(name,key)
                cases += [(c.get('classname'),c.get('name')) for c in rows]
        if role=='cases': assert len(cases)==len(set(cases))
        if role not in ('source','verdict'): assert cases
        if role=='source':
            assert z.read('source-sha.txt').decode().strip()=='421be62c1f5f540d937ab5e90ac875b8b51737d7'
            assert z.read('validation-sha.txt').decode().strip()=='21c77e717980345fd7d007183ddaf8c8c67d2643'
        if role=='verdict':
            v=json.loads(z.read('TWO_COUNTERS_R1_RESULT.json'))
            assert v['mechanics_catalog_cases']==214 and v['full_game_admission'] is False
            assert v['registry_totals']['registered_slots']==484
            assert v['official_capability']==v['official_primary']==0 and v['precision_reserved']==2
        if role=='registry':
            v=json.loads(z.read('COMPOSED_R1_LIVE_REGISTRY_GATE.json'))
            assert v['status']=='HOLD_REGISTRY_COVERAGE'
            assert v['totals']['manual_registered']==92 and v['totals']['opponent_registered']==392
            assert v['actual_games_initialized']==v['official_results']==0
        if role=='broad':
            exits=[n for n in names if n.endswith('exit.txt')]
            assert len(exits)==1 and z.read(exits[0]).strip()==b'0'
        if role=='assay':
            delta=json.loads(z.read('snapshot-delta.json'))
            assert delta['added']==delta['expected_additions'] and len(delta['added'])==30
            assert not delta['removed'] and not delta['modified']
            def bank(prefix):
                result={}
                for n in names:
                    if n.startswith(prefix) and n.endswith('.json'):
                        for s in re.split(r'^// ',z.read(n).decode(),flags=re.M)[1:]:
                            name,body=s.split('\n',1); key=(Path(n).name,name)
                            assert key not in result
                            result[key]=json.loads(body)
                return result
            before,after=bank('before/'),bank('generated/')
            assert sorted(set(after)-set(before))==[tuple(x) for x in delta['added']]
            assert all(before[k]==after[k] for k in set(before)&set(after))
            assert not {k for k in before if k[0] in {j[0] for j in after}}-set(after)
            for code,expected in [('MH3',0),('MID',1)]:
                log=z.read('differential-'+code+'.log').decode()
                assert re.search(r'DIVERGENT — read every one\s+'+str(expected)+r'\b',log)
                assert re.search(r'golden would not decode\s+0\b',log)
            assert 'Ghoulish Procession  [MID]' in z.read('differential-MID.log').decode()
        return {'passed_rows':len(cases),'xml_classes':classes,'members':len(names),'internal_hashes':len(hashed)}

def main():
    index=json.loads((ROOT/'ORIGINALS.json').read_text());results=[]
    for a in index['artifacts']:
        chunks=[]
        for p in a['ordered_parts']:
            data=(ROOT/p['path']).read_bytes()
            assert len(data)==p['bytes'] and sha(data)==p['sha256']
            chunks.append(data)
        data=b''.join(chunks)
        assert len(data)==a['bytes'] and sha(data)==a['sha256']
        actual=audit(data,a['role']); assert actual==a['audit'],a['name']
        results.append({'name':a['name'],**actual})
    assert sum(r['passed_rows'] for r in results if '-cases-' in r['name'])==214
    print(json.dumps({'status':'ORIGINALS_VERIFIED','artifacts':results},indent=2))
if __name__=='__main__': main()
