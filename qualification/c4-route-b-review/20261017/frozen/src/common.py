"""New candidate code; does not replace frozen runtime entry points."""
import hashlib
import json
import os
import re
import time
from dataclasses import dataclass

class Blocked(RuntimeError):
    pass

def require(ok, reason):
    if not ok:
        raise Blocked(reason)

# A new reviewed deployment build is required. No environment or JSON override.
def deployment_barrier():
    raise Blocked('LOCAL_CANDIDATE_ONLY: independent review, target bindings and deployment authority absent')

@dataclass(frozen=True)
class Binding:
    boot: str
    invocation: str
    run: str
    source: str
    image: str
    unit: str = 'argentum-c4-v6-probe.service'

    def __post_init__(self):
        require(bool(re.fullmatch(r'[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}',self.boot)), 'boot binding')
        require(bool(re.fullmatch('[0-9a-f]{32}', self.invocation)), 'invocation binding')
        require(bool(re.fullmatch('[0-9a-f]{32}', self.run)), 'run nonce')
        require(all(bool(re.fullmatch('[0-9a-f]{64}',x)) for x in (self.source,self.image)), 'source/image hash')
        require(self.unit=='argentum-c4-v6-probe.service','unit allowlist')

class Journal:
    """Exclusive local file, hash chain and fsync before returning each receipt.

    Hash chaining detects modification only against an independently retained tip.
    It is NOT a signature or authenticated kernel provenance.
    """
    def __init__(self, path, binding):
        self.fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_CLOEXEC|os.O_NOFOLLOW,0o600)
        self.binding=binding.__dict__;self.seq=0;self.tip='0'*64;self.failed=False
        parent=os.open(os.path.dirname(os.path.abspath(path)),os.O_RDONLY|os.O_DIRECTORY|os.O_CLOEXEC)
        try:os.fsync(parent)
        finally:os.close(parent)
    def append(self, kind, data):
        require(not self.failed,'journal previously failed')
        row=dict(seq=self.seq,previous=self.tip,mono_ns=time.monotonic_ns(),binding=self.binding,kind=kind,data=data)
        raw=json.dumps(row,sort_keys=True,separators=(',',':'),allow_nan=False).encode()
        tip=hashlib.sha256(raw).hexdigest()
        encoded=json.dumps({'record':row,'sha256':tip},sort_keys=True,separators=(',',':')).encode()+b'\n'
        try:
            view=memoryview(encoded)
            while view:
                n=os.write(self.fd,view);require(n>0,'short journal write');view=view[n:]
            os.fsync(self.fd)
        except BaseException:
            self.failed=True
            raise
        self.seq+=1;self.tip=tip
        return tip
    def close(self):
        if self.fd>=0:os.close(self.fd);self.fd=-1
