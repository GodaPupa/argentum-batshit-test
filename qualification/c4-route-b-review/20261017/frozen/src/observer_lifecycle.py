"""Host-independent lifecycle; injected adapters confer no kernel authority."""
from common import require,deployment_barrier
class AttachmentLifecycle:
    def __init__(self,provider,journal,binding,cpus):
        self.provider=provider;self.journal=journal;self.binding=binding
        self.cpus=frozenset(cpus);self.state='NEW';self.links=[]
        require(self.cpus and all(type(c) is int and c>=0 for c in self.cpus),'CPU inventory')
    def prepare_bound_capture(self,scope,binding):
        require(self.state=='NEW' and binding==self.binding,'attachment binding/state')
        self.state='ATTACHING'
        try:
            scope.revalidate();self.provider.validate_installation_and_kernel(binding)
            require(frozenset(self.provider.online_cpus())==self.cpus,'CPU drift before attach')
            self.links=list(self.provider.attach_reviewed_hooks(scope))
            require(self.links and len(set(self.links))==len(self.links),'attachment links')
            self.check_health();self.journal.append('ATTACHED_NOT_QUALIFIED',{'cpus':sorted(self.cpus)})
            self.state='ATTACHED'
        except BaseException:self.state='FAILED';raise
    def check_health(self):
        require(frozenset(self.provider.online_cpus())==self.cpus,'CPU hotplug: coverage invalid')
        loss=self.provider.loss_counters()
        require(set(loss)==self.cpus and all(type(v) is int and v==0 for v in loss.values()),'event loss or missing loss map')
        self.provider.verify_links_and_maps(self.links,self.binding)
    def finish_after_drain(self,drain_check):
        require(self.state=='ATTACHED','observer lifecycle state')
        # Production drain check must use held FD and exact invocation evidence.
        require(drain_check() is True,'drain not verified');self.state='DRAINING'
        try:
            self.check_health();self.provider.flush_durable_events();self.check_health()
            self.journal.append('ATTACH_TO_DRAIN_COMPLETE',{})
            self.provider.detach_once(self.links);self.state='DETACHED'
        except BaseException:self.state='FAILED';raise
        return {'runtime_qualified':False,'kernel_authentication_proven':False}
def construct_authenticated_provider():
    deployment_barrier()
