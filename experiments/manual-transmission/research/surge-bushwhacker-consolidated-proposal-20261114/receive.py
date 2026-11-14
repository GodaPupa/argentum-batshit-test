"""Independent original-artifact receiver. Download transport must be claimed once before entry."""
from pathlib import Path,PurePosixPath
import hashlib,json,sys,zipfile
from audit_reports import audit_all,require,digest
P=Path(__file__).resolve().parent
def read(p):return json.loads(Path(p).read_text())
def write(p,x):Path(p).write_text(json.dumps(x,indent=2)+'\n')
def envelope_check(actual,expected):
    for key in ('repository','branch','epoch','candidate_index','event','source','source_tree','validation','validation_tree','workflow_blob','workflow_sha256','protocol_blob','candidate_pins_blob','run_id','job_id','artifact_id','artifact_name','artifact_digest','artifact_size'):
        require(actual[key]==expected[key],'wrong '+key)
    require(actual['attempt']==actual['run_number']==1,'attempt/run number cap')
    require(actual['status']=='completed' and actual['job_status']=='completed','nonterminal original')
    require(actual['already_received'] is False,'already received artifact')
    require(actual['candidate_index'] in ('01','02'),'candidate cap')
    require(actual['repository']=='GodaPupa/argentum-batshit-test','repository')
    require(actual['branch']=='manual-transmission/quarantine-recovery-20261106','branch')
    require(actual['epoch']=='manual-quarantine-20261106-01','epoch')
    require(actual['protocol_blob']=='a2c26c3b31834a7182bbe0b3a6ceb83922443cd4','protocol')
    require(actual['fresh_reconciliation'] is True and actual['sole_writer'] is True and actual['automation_disabled'] is True,'live receiving gate')

def unpack(archive,destination,metadata):
    require(archive.stat().st_size==metadata['artifact_size'],'archive byte length')
    require('sha256:'+digest(archive)==metadata['artifact_digest'],'wrong artifact digest')
    with zipfile.ZipFile(archive) as z:
        infos=z.infolist();names=[i.filename for i in infos]
        require(len(names)==len(set(names)),'duplicate archive name')
        for i in infos:
            p=PurePosixPath(i.filename)
            require(not p.is_absolute() and '..' not in p.parts and '\\' not in i.filename,'unsafe member')
            require((i.external_attr>>16)&0o170000!=0o120000,'symlink member')
        require(z.testzip() is None,'archive CRC failure')
        z.extractall(destination)
    manifest=read(destination/'MANIFEST.json')
    files={p.relative_to(destination).as_posix():digest(p) for p in destination.rglob('*') if p.is_file() and p!=destination/'MANIFEST.json'}
    require(files==manifest,'outer manifest mismatch')
    return files

def independent_audit(extracted,metadata):
    event=read(extracted/'RECOVERY_EVENT.json');q=event['qualification'];grant=event['grant']
    require(event['event']==metadata['event'] and event['source']==metadata['source'] and event['validation']==metadata['validation'],'event envelope')
    require(str(event['run'])==str(metadata['run_id']) and event['attempt']==1,'original run')
    for k in ('source_tree','validation_tree','workflow_blob','workflow_sha256','candidate_pins_blob','candidate_index'):
        require(q[k]==metadata[k],'original pin '+k)
    require(grant['explicit_user_grant'] is True and grant['protocol_blob']==metadata['protocol_blob'],'grant pin')
    evidence=extracted/'qualification';manifest=read(evidence/'MANIFEST.json')
    actual={p.relative_to(evidence).as_posix():digest(p) for p in evidence.rglob('*') if p.is_file() and p!=evidence/'MANIFEST.json'}
    require(actual==manifest,'qualification manifest')
    pins=read(evidence/'CANDIDATE_PINS.json')
    require(pins==read(P/'CANDIDATE_PINS.json'),'stale candidate')
    for path,pin in pins['bindings'].items():
        f=evidence/'frozen-source'/path
        require(f.stat().st_size==pin['bytes'] and digest(f)==pin['sha256'],'frozen source '+path)
    result=audit_all(evidence)
    require(metadata['conclusion']=='success' and metadata['job_conclusion']=='success','failed original cannot admit')
    return result

def receive(archive,metadata,expected,destination):
    envelope_check(metadata,expected);require(not destination.exists(),'already received candidate')
    destination.mkdir(parents=True)
    # Retain the original even when audit fails. Never download or invoke this receiver again.
    original=destination/'original.zip';original.write_bytes(archive.read_bytes())
    receipt=dict(schema='manual-transmission/surge-original-receipt/v1',metadata=metadata,original_received_once=True,admitted=False)
    try:
        files=unpack(original,destination/'extracted',metadata);receipt['members']=files
        receipt['audit']=independent_audit(destination/'extracted',metadata);receipt['state']='RAW_AUDIT_PASS_READBACK_PENDING'
    except Exception as exc:
        receipt['state']='FAILED_OR_INCOMPLETE_ORIGINAL_PRESERVED';receipt['blocker']=str(exc)
    # Parts are the original bytes, not a repacked zip. A remote readback must independently reassemble them.
    b=original.read_bytes();index=[]
    import base64
    for n,start in enumerate(range(0,len(b),450000)):
        part=b[start:start+450000];name=f'original.part{n:04d}.b64'
        (destination/name).write_bytes(base64.b64encode(part)+b'\n')
        index.append(dict(file=name,bytes=len(part),sha256=hashlib.sha256(part).hexdigest()))
    write(destination/'ARCHIVE_PARTS.json',dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest(),parts=index))
    write(destination/'RECEIPT.json',receipt)
    return receipt
if __name__=='__main__':
    archive,meta,expected,out=map(Path,sys.argv[1:]);result=receive(archive,read(meta),read(expected),out)
    print(json.dumps(result,indent=2));sys.exit(0 if result['state']=='RAW_AUDIT_PASS_READBACK_PENDING' else 1)
