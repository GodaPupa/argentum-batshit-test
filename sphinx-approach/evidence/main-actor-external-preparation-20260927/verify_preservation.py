#!/usr/bin/env python3
"""Read-only preservation audit; does not import or execute any archived controls."""
import hashlib, io, json, zipfile
from pathlib import Path
from datetime import datetime, timezone

packet = Path('ferocity-takeover/sphinx-main-binding/publication')
out = Path('izzet-takeover/sphinx-main-packet-review')
out.mkdir(exist_ok=True)
sha = lambda b: hashlib.sha256(b).hexdigest()
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
outer = {p.name: {'bytes': p.stat().st_size, 'sha256': sha(p.read_bytes())} for p in packet.iterdir() if p.is_file()}
assert set(outer) == {'README.md', 'archive-index.json', 'source-review-evidence.zip', 'root-independent-source-review.json'}
expected = {'README.md': '9c2210a155fc651a24dec4f50943d2f9f86a9de75935940a4530127b48cdd12c', 'archive-index.json': 'a3acb5b7b14823859fa72017251b9d2d3192bb848828532a207989e4922a9270', 'source-review-evidence.zip': 'bb767f4d81167037a764188adb5f64b804eee85c07a4e3b57f0cf77bfa38ede4', 'root-independent-source-review.json': 'c064c8d797dcfbfbb59c00119a97913f738a0e8af8794e450bda7d5488930dcf'}
assert {n:r['sha256'] for n,r in outer.items()} == expected
index = json.loads((packet/'archive-index.json').read_text())
z = zipfile.ZipFile(packet/'source-review-evidence.zip')
assert z.testzip() is None
assert len(z.namelist()) == len(set(z.namelist())) == 96
manifest = json.loads(z.read('archive-member-manifest.json'))
assert len(manifest['members']) == 95
assert set(z.namelist()) == {r['path'] for r in manifest['members']} | {'archive-member-manifest.json'}
for row in manifest['members']:
    body = z.read(row['path'])
    assert len(body) == row['bytes'] and sha(body) == row['sha256'], row['path']
assert outer['source-review-evidence.zip']['bytes'] == index['archive']['bytes'] == 753903
assert index['archive']['sha256'] == expected['source-review-evidence.zip']
assert index['archive']['manifest_sha256'] == sha(z.read('archive-member-manifest.json'))
review_bytes = z.read('root-independent-source-review.json')
assert review_bytes == (packet/'root-independent-source-review.json').read_bytes()
assert sha(review_bytes) == index['root_source_review_sha256']
review = json.loads(review_bytes)
for path, row in review['support'].items():
    b = z.read(path)
    assert len(b) == row['bytes'] and sha(b) == row['sha256'], path
for key, path in [('source_byte_proof_sha256','root-source-byte-proof.json'), ('candidate_inventory_sha256','candidate-inventory.json'), ('prospective_preflight_sha256','prospective-activation-preflight.json')]:
    assert sha(z.read(path)) == index[key], key
proof = json.loads(z.read('root-source-byte-proof.json'))
inventory = json.loads(z.read('candidate-inventory.json'))
assert proof['five_control_files'] == review['files'] == inventory['files']
for row in review['files']:
    b = z.read('candidate/'+row['path'])
    assert (len(b),sha(b),blob(b)) == (row['bytes'],row['sha256'],row['git_blob_sha1']),row['path']
sources = proof['source_bodies_rehashed']
assert len(sources) == len({r['path'] for r in sources}) == 60
assert {n for n in z.namelist() if n.startswith('runtime-source/')} == {'runtime-source/'+r['path'] for r in sources}
for row in sources:
    b=z.read('runtime-source/'+row['path'])
    assert (len(b),sha(b),blob(b)) == (row['bytes'],row['sha256'],row['git_blob_sha1']), row['path']
workflows=json.loads(z.read('inherited-workflow-source-bodies.json'))
preflight=json.loads(z.read('prospective-activation-preflight.json'))
assert workflows['canonical'] == index['parent'] == review['canonical']
assert workflows['tree'].get('truncated',False) is False
tree={'.github/'+r['path']:r for r in workflows['tree']['tree'] if r['type']=='blob' and r['path'].startswith('workflows/') and r['path'].endswith(('.yml','.yaml'))}
rows={r['path']:r for r in preflight['all_inherited_workflow_bodies']}
assert len(workflows['files']) == len(tree) == len(rows) == 85
assert {r['path'] for r in workflows['files']} == set(tree) == set(rows)
workflow_checks=[]
for row in workflows['files']:
    path=row['path']; result=row['result']; assert result['encoding']=='utf-8'
    b=result['content'].encode()
    assert blob(b) == result['sha'] == tree[path]['sha'] == rows[path]['git_blob_sha1'],path
    assert len(b)==tree[path]['size'] and sha(b)==rows[path]['sha256'],path
    workflow_checks.append({'path':path,'bytes':len(b),'sha256':sha(b),'git_blob_sha1':blob(b)})
prepared=json.loads(z.read('prepared-control-checkpoint.json'))
assert prepared['commit']==index['control_commit']==preflight['control_commit']=='0377a9639061b75bf04f01e466dee247963066b8'
assert prepared['tree']==index['control_tree']==preflight['control_tree']=='062e5624c74795d38bd937c299d3750b764a729f'
assert prepared['parent']==index['parent']==preflight['control_parent']
for row in prepared['files']:
    assert row['remote_sha']==row['git_blob_sha1']==blob(z.read('candidate/'+row['path']))
initial=zipfile.ZipFile(io.BytesIO(z.read('initial-candidate-ec99.zip')))
assert initial.testzip() is None
assert sha(z.read('initial-candidate-ec99.zip'))==proof['original_candidate_archive_sha256']
checks={'schema':'sphinx-main-packet-independent-preservation-checks-v1','checked_utc':datetime.now(timezone.utc).isoformat(),'outer_files':outer,'archive_crc':'PASS','unique_members':96,'manifested_members':95,'manifest_self_hash':sha(z.read('archive-member-manifest.json')),'manifest_rows':manifest['members'],'runtime_source_bodies':sources,'workflow_bodies':workflow_checks,'candidate_files':review['files'],'root_review_support_files':review['support'],'initial_archive_members':len(initial.namelist()),'initial_archive_crc':'PASS','scope':'Read-only archived byte/hash/CRC and retained source-proof linkage; no control import, synthetic guard rerun, JVM, fixture, gameplay, or event.'}
(out/'independent-checks.json').write_text(json.dumps(checks,indent=2)+'\n')
receipt={'schema':'sphinx-main-packet-independent-publication-review-v1','reviewer':'/root/izzet, non-author of the five Sphinx controls and this packet','reviewed_utc':checks['checked_utc'],'disposition':'PASS_EXACT_FOUR_FILE_PREPARATION_PRESERVATION_ONLY','files':outer,'checks':['All96 ZIP members are unique and CRC-valid; all95 payload member byte counts and SHA256 match the retained manifest. The remaining member is the manifest itself, whose hash matches the outer index.','Root source review c064 is byte-identical inside and outside the ZIP; its nine supporting file hashes, five exact candidate SHA256/Git blob identities, inventory and source proof are retained unchanged.','All60 exact runtime-source bodies reproduce their recorded byte counts, SHA256 and Git blob IDs. This verifies preservation and internal proof linkage; it does not reperform source semantics or ref admission.','All85 complete inherited workflow bodies reproduce their recorded Git-tree sizes/blob IDs and prospective preflight SHA256 values. The retained workflow universe equals all85 YAML entries in the archived complete GitHub subtree.','Prepared commit/tree/parent and five candidate remote blob records reconcile across checkpoint, index, source review and prospective preflight. Historical initial candidate ZIP and prior correction/failure records are retained; nested ZIP CRC passes.','README and index describe prospective preparation,50-within136 overlap, zero actual actor-bank/gameplay cases, and separate final event disposition; no present branch-absence or source-acceptance claim is added.'],'root_source_review_sha256':expected['root-independent-source-review.json'],'independent_checks_sha256':sha((out/'independent-checks.json').read_bytes()),'audit_script_sha256':sha(Path(__file__).read_bytes()),'limitations':['No archived control or54-guard program was executed, and no source test/JVM/actor bank/gameplay was run.','This is bounded preservation review, not a new semantic source review, current Git ref/trigger recheck, GitHub approval, runtime acceptance or event permission. Root owns final activation and receiving review.','The95-member manifest excludes only itself;96 is the actual ZIP member count. Source payloads are selected original files, not a complete runtime distribution.']}
(out/'independent-preservation-review.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps({'disposition':receipt['disposition'],'review_sha256':sha((out/'independent-preservation-review.json').read_bytes()),'review_bytes':(out/'independent-preservation-review.json').stat().st_size,'checks_sha256':receipt['independent_checks_sha256'],'script_sha256':receipt['audit_script_sha256'],'source_count':60,'workflow_count':85,'members':96},indent=2))
