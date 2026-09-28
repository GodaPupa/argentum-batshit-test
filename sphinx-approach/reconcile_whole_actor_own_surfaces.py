#!/usr/bin/env python3
import csv,json,pathlib

DECKS=[
 "reconstructed-v01",
 "reconstructed-hybrid",
 "closest-no-approach-v01",
 "serpico-terror-benchmark",
]
UNSUPPORTED=[
 "Artful Dodge","Deem Inferior","Dispel","Lórien Revealed",
 "Sleep of the Dead","Snap","Spell Pierce","Sphinx's Approach",
]

def load(name):
 p=pathlib.Path("sphinx-approach/decks")/(name+".csv")
 rows={}
 with p.open() as f:
  for card,count in csv.reader(f):
   rows[card]=int(count)
 assert sum(rows.values())==60,(name,sum(rows.values()))
 return rows

def main():
 out=pathlib.Path("build/reports/sphinx-own-surface-matrix");out.mkdir(parents=True,exist_ok=True)
 matrix={name:{card:load(name).get(card,0) for card in UNSUPPORTED} for name in DECKS}
 present={card:[name for name in DECKS if matrix[name][card]>0] for card in UNSUPPORTED}
 result={
  "schema":"sphinx-stage-e-own-surface-matrix-v1",
  "decks":DECKS,
  "unsupported_identities":UNSUPPORTED,
  "matrix":matrix,
  "present_in":present,
  "result":"STATIC_OWN_SURFACE_MATRIX_RECONCILED",
  "behavioral_cases":0,
  "official_counters":{"seeds":0,"games":0,"outcomes":0},
 }
 (out/"result.json").write_text(json.dumps(result,indent=2)+"\n")
 print(json.dumps(result,indent=2))
if __name__=="__main__": main()
