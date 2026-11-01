"""Unexecuted follow-on helper: same installed Assay binary/corpus, isolated original/candidate goldens.
Call only after original targeted receiving, verified additive snapshot and full compilation.
No tracked file is rewritten. Raw outputs always require original receiving/classification.
"""
from pathlib import Path
import hashlib,json,subprocess
sha=lambda b:hashlib.sha256(b).hexdigest()
def observe_pair(root, output, candidate_sha256, original):
 root=Path(root).resolve();output=Path(output).resolve()
 assert len(original)==67843 and sha(original)=='b021d21c371237f2f080f751b9ccc256e5de91f4bb559a0c5fb4c50ae537b4c3'
 candidate=(root/'mtg-sets/src/test/resources/snapshots/cards/MH3.json').read_bytes()
 assert sha(candidate)==candidate_sha256
 binary=root/'oracle-assay/build/install/oracle-assay/bin/oracle-assay'
 assert binary.is_file()
 corpus=Path.home()/'.cache/scryfall/_bulk-oracle-cards.jsonl.gz'
 assert corpus.is_file()
 corpus_hash=sha(corpus.read_bytes())
 distribution={str(p.relative_to(binary.parent.parent)):sha(p.read_bytes()) for p in sorted(binary.parent.parent.rglob('*')) if p.is_file()}
 output.mkdir(parents=True,exist_ok=False)
 (output/'oracle-cards.jsonl.gz').write_bytes(corpus.read_bytes())
 recipe=output/'assay-pair.just'
 recipe.write_text('observe:\n    "'+str(binary)+'" differential --set MH3 --top 10000\n')
 for name,data,count in [('original',original,24),('candidate',candidate,25)]:
  folder=output/name;folder.mkdir()
  (folder/'settings.gradle.kts').write_bytes((root/'settings.gradle.kts').read_bytes())
  snapshot=folder/'mtg-sets/src/test/resources/snapshots/cards/MH3.json'
  snapshot.parent.mkdir(parents=True);snapshot.write_bytes(data)
  assert sum(l.startswith(b'// ') for l in data.splitlines())==count
  with (folder/'stdout.log').open('wb') as stdout,(folder/'stderr.log').open('wb') as stderr:
   rc=subprocess.run(['just','--working-directory',str(folder),'--justfile',str(recipe),'observe'],stdout=stdout,stderr=stderr).returncode
  (folder/'exit.txt').write_text(str(rc)+'\n')
  assert rc==0,name
  assert snapshot.read_bytes()==data
  assert sha(corpus.read_bytes())==corpus_hash
 assert (root/'mtg-sets/src/test/resources/snapshots/cards/MH3.json').read_bytes()==candidate
 assert distribution=={str(p.relative_to(binary.parent.parent)):sha(p.read_bytes()) for p in sorted(binary.parent.parent.rglob('*')) if p.is_file()}
 (output/'BINDINGS.json').write_text(json.dumps({'original_snapshot':sha(original),'candidate_snapshot':candidate_sha256,'corpus':corpus_hash,'distribution':distribution,'classification_required':True,'admitted':False},indent=2)+'\n')
