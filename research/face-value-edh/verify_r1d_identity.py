#!/usr/bin/env python3
import hashlib, pathlib, re, sys
TARGET="1548264c2199d2f5c376022d63200ed80a325fa0058dafaa4bfb4b4eb3d9b026"
p=pathlib.Path(__file__).with_name("recovered-v0.11-user-list.txt")
raw=p.read_text(encoding="utf-8")
lines=raw.splitlines()
cards=[]
section=None
for line in lines:
    s=line.strip()
    if not s: continue
    if s in {"Commander","Creatures","Noncreature spells","Lands"}:
        section=s; continue
    m=re.fullmatch(r"(\d+) (.+)",s)
    if not m: raise SystemExit(f"unparsed line: {s}")
    n,name=int(m.group(1)),m.group(2)
    cards.extend([(section,name)]*n)
if len(cards)!=100: raise SystemExit(f"expected 100 cards, got {len(cards)}")
counts={k:sum(1 for sec,_ in cards if sec==k) for k in ["Commander","Creatures","Noncreature spells","Lands"]}
if counts!={"Commander":1,"Creatures":51,"Noncreature spells":15,"Lands":33}: raise SystemExit(f"composition mismatch {counts}")
names=[n for _,n in cards]
for required in ["Animar, Soul of Elements","Hope-Ender Coatl","Mirrorshell Crab"]:
    if required not in names: raise SystemExit(f"missing {required}")
if "Duplicant" in names: raise SystemExit("Duplicant must be absent in v0.11")

# Historical serialization candidates. A hash match is sufficient; no match means
# card identity is structurally verified but byte serialization remains unverified.
cands={}
cands["user_raw_lf"]=raw.replace("\r\n","\n")
cands["user_raw_lf_no_final_newline"]=cands["user_raw_lf"].rstrip("\n")
expanded="\n".join(f"1 {n}" for _,n in cards)
cands["expanded_plain_lf"]=expanded+"\n"
cands["expanded_plain_no_final_newline"]=expanded
sectioned=[]
for sec in ["Commander","Creatures","Noncreature spells","Lands"]:
    sectioned.append(sec)
    sectioned += [f"1 {n}" for s,n in cards if s==sec]
    sectioned.append("")
cands["sectioned_expanded_lf"]="\n".join(sectioned).rstrip()+"\n"
for key,val in list(cands.items()):
    cands[key+"_crlf"]=val.replace("\n","\r\n")
matches=[]
for key,val in cands.items():
    h=hashlib.sha256(val.encode()).hexdigest()
    print(key,h)
    if h==TARGET: matches.append(key)
print("STRUCTURAL_IDENTITY_PASS",counts)
if matches:
    print("BYTE_HASH_MATCH",matches)
    sys.exit(0)
print("BYTE_HASH_NO_MATCH")
sys.exit(3)
