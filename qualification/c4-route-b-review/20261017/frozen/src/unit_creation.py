"""Fixed transient-unit request and one-attempt transaction candidate.

No integration with accepted sources. Live sender cannot run in this package.
Caller-injected controls in tests are synthetic and never operational authority.
"""
import asyncio
import hashlib
from common import require,deployment_barrier
from manager import UNIT,ROOT,MANAGER

CONTRACT_SHA='6dddfa5f30311a76d19bebc5d7433ce3692e92e3324b0d42e7698d01f319c7d2'

def fixed_properties(contract_bytes,supervisor_bytes,expected_supervisor_sha):
    require(hashlib.sha256(contract_bytes).hexdigest()==CONTRACT_SHA,'unit contract drift')
    require(hashlib.sha256(supervisor_bytes).hexdigest()==expected_supervisor_sha,'supervisor byte drift')
    require(len(expected_supervisor_sha)==64,'supervisor hash shape')
    return [
        ('Type','s','exec'),('User','s','root'),('Group','s','root'),
        ('ExecStart','a(sasb)',[['/opt/argentum-c4-v6/supervisor',['/opt/argentum-c4-v6/supervisor'],False]]),
        ('Delegate','b',True),('DelegateControllers','as',['memory']),('DelegateSubgroup','s','supervisor'),
        ('Restart','s','no'),('KillMode','s','control-group'),('SendSIGKILL','b',True),
        ('RuntimeMaxUSec','t',60000000),('TimeoutStopUSec','t',5000000),
        ('ExecStartPre','a(sasb)',[]),('ExecStartPost','a(sasb)',[]),('ExecReload','a(sasb)',[]),
        ('ExecStop','a(sasb)',[]),('ExecStopPost','a(sasb)',[])]

class FixedUnitSender:
    """Single mutating method; no arbitrary name, command, flags or property API."""
    def __init__(self,session,contract_bytes,supervisor_bytes,supervisor_sha):
        deployment_barrier()
        self.session=session;self.properties=fixed_properties(contract_bytes,supervisor_bytes,supervisor_sha);self.used=False
    async def create_once(self):
        require(not self.used,'unit creation already attempted')
        await self.session.revalidate()
        from dbus_next import Message,MessageType,Variant
        # Caller must already have proved the unit absent and external controller
        # armed. Integration is intentionally absent until reviewed deployment.
        body=[UNIT,'fail',[[name,Variant(sig,value)] for name,sig,value in self.properties],[]]
        self.used=True
        reply=await asyncio.wait_for(self.session.wire.bus.call(Message(destination=self.session.owner,path=ROOT,interface=MANAGER+'.Manager',member='StartTransientUnit',signature='ssa(sv)a(sa(sv))',body=body)),2)
        require(reply is not None and reply.message_type==MessageType.METHOD_RETURN and reply.sender==self.session.owner,'unit outcome uncertain/denied; do not retry')
        require(len(reply.body)==1 and reply.body[0].startswith(ROOT+'/job/'),'unexpected job identity')
        return reply.body[0]

class CreationTransaction:
    def __init__(self,controls,sender,journal):
        self.controls=controls;self.sender=sender;self.journal=journal;self.used=False
    async def execute_once(self):
        require(not self.used,'transaction consumed')
        self.used=True
        # These methods are implementation interfaces, not dictionary PASS flags.
        # Production controls binding must be reviewed; test doubles confer nothing.
        await self.controls.check_live_identity_and_authority()
        await self.controls.require_absent_unit()
        await self.controls.require_external_controller_ready()
        baseline=await self.controls.read_upstream_state()
        self.journal.append('UNIT_CREATE_INTENT',{'unit':UNIT})
        try:
            job=await self.sender.create_once()
            self.journal.append('UNIT_CREATE_RETURNED',{'job':job})
            require(await self.controls.read_upstream_state()==baseline,'unauthorized ancestor effect')
            await self.controls.verify_effective_properties_and_hold_scope()
            self.journal.append('SCOPE_READY_NOT_WORKER_RELEASE',{})
            return {'status':'SCOPE_CANDIDATE_READY','runtime_qualified':False,'worker_released':False}
        except BaseException:
            # No automatic StartTransientUnit retry, StopUnit or PID-based repair.
            # Reviewed external controller owns any cleanup, including uncertain create.
            try:self.journal.append('UNIT_TRANSACTION_INCOMPLETE',{})
            except Exception:pass
            raise
