"""Freeze only after original dependent receipt is independently verified and committed."""
from pathlib import Path
import json,hashlib,sys
H=Path(__file__).resolve().parent
def main():
 assert len(sys.argv)==5,'PREPARE.py <verified-receipt-local> <receipt-commit> <receipt-repo-path> <receipt-blob>'
 receipt,commit,path,binding=sys.argv[1:];raw=Path(receipt).read_bytes();d=json.loads(raw)
 assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==binding
 assert d['state']=='ONE_TEST_DEPENDENT_CLIENT_ORIGINAL_VERIFIED_NOT_CARD_ADMISSION' and not d['gate_errors']
 assert d['source']=='ce5145a2f3a361ad202ebc9cb86ef5b88a57dfc3' and d['run']==40977504099
 assert d['gates']['ordinary']['rows']==19074 and d['gates']['ordinary']['skips']==43 and d['gates']['cards']['passed']==389 and d['gates']['client']['passed']==64
 p=json.loads((H/'PROTOCOL.json').read_text());assert p['state']=='ISOLATED_DRAFT_DEPENDENT_RECEIPT_REQUIRED'
 p.update(state='FROZEN_AFTER_VERIFIED_ONE_TEST_DEPENDENT_RECEIPT',base=commit,dependent_receipt=path,dependent_receipt_blob=binding)
 p['source_delta']=[p['record']+'/'+f.name for f in H.iterdir() if f.is_file()]
 (H/'PROTOCOL.json').write_text(json.dumps(p,indent=2)+'\n')
 print(json.dumps({'state':'PREPARED_FOR_SOURCE_REVIEW_NOT_DISPATCHED','base':commit,'files':len(p['source_delta'])}))
if __name__=='__main__':main()
