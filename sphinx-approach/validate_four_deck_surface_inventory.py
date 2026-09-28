#!/usr/bin/env python3
import hashlib,json,pathlib
DECKS={"reconstructed-v01":"274521097cc731ac5f5aa9b7645a20b13d4546fd73b17dbcd14af0b0a25c4486","reconstructed-hybrid":"f4cf2c64bb07847bd013e9984480f5b0fa76ac3a40dc5a47894a3609e9cb3b2e","closest-no-approach-v01":"548998c77f5f688d793d36cdb9f21ae8b3b63963856875c4b84d82c271196a83","serpico-terror-benchmark":"6c678f94112c56b0856c1fe4c008f77e7d0897bdeb290f3e2a0ec9d034147c62"}
def main():
 m=json.loads(pathlib.Path("sphinx-approach/STAGE_E_FOUR_DECK_OWN_CARD_SURFACE_MAP_20260928.json").read_text());rows={};union=set()
 for name,h in DECKS.items():
  p=pathlib.Path("sphinx-approach/decks")/(name+".csv");data=p.read_bytes();assert hashlib.sha256(data).hexdigest()==h
  entries=[]
  for line in data.decode().splitlines():
   if not line.strip():continue
   card,count=line.rsplit(",",1);entries.append((card,int(count)));union.add(card)
  assert sum(c for _,c in entries)==60;unknown=sorted({n for n,_ in entries}-set(m["classification"]));assert not unknown,(name,unknown)
  rows[name]={"cards":dict(entries),"surfaces":sorted({m["classification"][n] for n,_ in entries})}
 unsupported=sorted(n for n in union if "UNCOVERED" in m["classification"][n] or "REMAIN" in m["classification"][n])
 out=pathlib.Path("build/reports/sphinx-four-deck-surface");out.mkdir(parents=True,exist_ok=True)
 (out/"result.json").write_text(json.dumps({"decks":rows,"unique_cards":sorted(union),"unsupported_card_identities":unsupported,"global_uncovered":m["global_uncovered"],"behavioral_cases":0,"official_counters":0,"result":"FINITE_OWN_CARD_SURFACE_INVENTORY_RECONCILED"},indent=2)+"\n")
 print("SPHINX_FOUR_DECK_SURFACE_INVENTORY_PASS")
if __name__=="__main__":main()
