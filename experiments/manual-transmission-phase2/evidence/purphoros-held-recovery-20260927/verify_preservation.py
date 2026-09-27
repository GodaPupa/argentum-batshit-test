"""Read existing held draft recovery bytes only. Does not import or run controls."""
from pathlib import Path
import hashlib,json,zipfile

HERE=Path(__file__).resolve().parent
SOURCE=Path('sphinx-audit/manual-purphoros-diagnostic-review')
def ident(b):return {'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()}
p=SOURCE/'status-recovery-package.zip';b=p.read_bytes();assert ident(b)=={'bytes':153978,'sha256':'9307b2ea8a86c5e112ac9706ed87104bd4ab7b8b17d6245aa073b5f04b061461'}
z=zipfile.ZipFile(p);assert z.testzip()is None and len(z.infolist())==len(set(z.namelist()))==41
m=json.loads(z.read('manifest.json'));assert len(m['members'])==40
assert set(z.namelist())=={r['member']for r in m['members']}|{'manifest.json'}
rows=[]
for row in m['members']:
 raw=z.read(row['member']);expected={k:row[k]for k in ['bytes','sha256']};assert ident(raw)==expected,row['member']
 local=Path(row['local_original']);s1=local.stat();observed=local.read_bytes();s2=local.stat()
 assert raw==observed,row['member']
 assert(s1.st_ino,s1.st_size,s1.st_mtime_ns)==(s2.st_ino,s2.st_size,s2.st_mtime_ns)
 rows.append({**row,'crc32':f'{z.getinfo(row["member"]).CRC:08x}','original_bytes_equal':True,'original_inode_mtime_size_unchanged':True})
status=json.loads(z.read('independent/STATUS_RECOVERY.json'));assert status['status']=='HELD_NO_DIAGNOSTIC_EVENT'
assert status['current_draft_disposition']['ready_for_execution']is False and status['current_draft_disposition']['source_review']is None
assert status['live_preflight_result']['ref_http']==404 and status['live_preflight_result']['actions_branch_total_count']==0
old=z.read('author-snapshot/control/preserved-b23-review-draft/run.py');cur=z.read('author-snapshot/control/run.py')
assert ident(old)['sha256']=='b23b6d0256eeccffb999c7eb2b192e55a84b795dcdf12a0a25193dcb18624d66'
assert ident(cur)['sha256']=='68de84f2b82ecdfb0af96bdff25670962fcda32923f3ad54f7eb1e93b505f5b2'
manifest=json.loads(z.read('author-snapshot/control/control-manifest.json'));assert manifest['ready']is False and manifest['event_created']is False
assert next(v for v in manifest['files']if v['path']=='run.py')['sha256']==ident(old)['sha256']
assert 'Originalb23, not current68de' in status['current_draft_disposition']['manifest_and_author_guards_still_bind']
findings=json.loads(z.read('independent/independent-b23-control-findings.json'));assert findings['verdict']=='HOLD_CONCRETE_CONTROL_DEFECTS_AND_SHARED_PREREQUISITE'
assert {x['id']for x in findings['blocking_findings']}=={'MPC01','MPC02','MPC03'}
assert ident(z.read('independent/independent-b23-exited-wrapper-check.json'))['sha256']=='c81b3347d84be1ec414df24e9d0e1510b6cecb0a57e9501414c9f52c815ed0f2'
pins=json.loads(z.read('independent/independent-selector-immutable-pins.json'));tag=json.loads(pins['tag']['content']);assert tag['tag']=='6.2.1'and tag['object']['sha']=='9ae8a1d5bcac861c03022b5fe2f2b01ec5b731aa'
receipt=json.loads((SOURCE/'status-recovery-package-receipt.json').read_text());assert receipt['sha256']==ident(b)['sha256']and receipt['members']==41
assert receipt['manifest_sha256']==ident(z.read('manifest.json'))['sha256']and receipt['status_checkpoint_sha256']==ident(z.read('independent/STATUS_RECOVERY.json'))['sha256']
out={'schema':'independent-manual-held-recovery-preservation-checks/v1','original_archive':{'path':str(p),**ident(b)},'members':rows,'manifest_sha256':ident(z.read('manifest.json'))['sha256'],'status_sha256':ident(z.read('independent/STATUS_RECOVERY.json'))['sha256'],'held_draft_sha256':ident(cur)['sha256'],'original_draft_sha256':ident(old)['sha256'],'stale_manifest_distinction_verified':True,'explicit_no_event_and_unreviewed_flags_verified':True,'pinned_kotest_selector_proof_retained':{'version':tag['tag'],'commit':tag['object']['sha']},'reproducer_executed':False,'control_imported':False,'scope':'Preservation and factual-held-status check only; no source/control/test/selector acceptance or event permission.'}
(HERE/'independent-checks.json').write_text(json.dumps(out,indent=2)+'\n')
print(json.dumps({'members':41,'original_files_verified':40,'sha256':ident((HERE/'independent-checks.json').read_bytes())['sha256']}))
