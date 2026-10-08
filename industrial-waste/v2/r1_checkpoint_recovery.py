"""Source-pinned RECOVER coordination; no uncertain-intent resume or admission.

Extends the accepted single-authority journal grammar without changing its bytes.
The accepted holder fails closed on these new records. Deployment still must
provide independent nonrollback storage, exclusive routing and key custody.
"""
import hashlib
import os

from r1_anchor_witness import AnchorWitness
from r1_checkpoint_holder import CheckpointHolder, checkpoint, identity
from r1_receiver_evidence import Rejected, decode


class RecoveryHolder(CheckpointHolder):
    def _apply(self, kind, data):
        if kind == 'RECOVERY_INTENT' and self._seq > 0:
            if set(data) != {'witness', 'instance', 'revision', 'path', 'operation'}:
                raise Rejected('recovery intent fields')
            state = self._cas(data['witness'], data['instance'], data['revision'])
            if data['operation'] != 'RECOVER':
                raise Rejected('recovery operation')
            self._path(data['path'])
            state['pending'] = dict(data)
            state['revision'] += 1
        elif kind == 'RECOVERY_PUBLISH' and self._seq > 0:
            if set(data) != {'witness', 'checkpoint', 'record'} or data['witness'] not in self._states:
                raise Rejected('recovery publication fields')
            wid = data['witness']; state = self._states[wid]
            if state['pending'] is None or state['pending'].get('operation') != 'RECOVER':
                raise Rejected('no pending recovery')
            self._verify_recovery(wid, state['checkpoint'].encode('ascii'),
                                  data['checkpoint'].encode('ascii'), data['record'].encode('ascii'))
            state['checkpoint'] = data['checkpoint']
            state['pending'] = None
            state['revision'] += 1
        else:
            # A RETAIN PUBLISH must never clear a recovery intent, including during
            # journal replay. Parent records otherwise retain their exact semantics.
            if kind == 'PUBLISH' and isinstance(data, dict):
                state = self._states.get(data.get('witness'))
                if state and state['pending'] and state['pending'].get('operation') == 'RECOVER':
                    raise Rejected('wrong publication for recovery intent')
            super()._apply(kind, data)

    @staticmethod
    def _path(path):
        if (type(path) is not str or not os.path.isabs(path) or path.startswith('//') or
                os.path.normpath(path) != path or not os.path.basename(path)):
            raise Rejected('canonical witness journal path required')
        return path

    def _verify_recovery(self, wid, before, after, raw_record):
        old = checkpoint(self._registry[wid], wid, before)
        new = checkpoint(self._registry[wid], wid, after)
        row = decode(raw_record)
        if (set(row) != {'seq', 'previous', 'kind', 'data'} or type(row['seq']) is not int or
                row['seq'] != old['rows'] or row['previous'] != old['tail'] or
                row['kind'] != 'RECOVER' or set(row['data']) != {'abandoned'} or
                type(row['data']['abandoned']) is not list):
            raise Rejected('not exact next RECOVER record')
        abandoned = row['data']['abandoned']
        for launch in abandoned:
            identity(launch)
        if abandoned != sorted(set(abandoned)):
            raise Rejected('recovery abandonment set')
        if (new['rows'] != old['rows'] + 1 or
                new['tail'] != hashlib.sha256(raw_record).hexdigest()):
            raise Rejected('recovery checkpoint/record binding')
        # The exact abandonment set is established by the accepted AnchorWitness
        # constructor replay, not by trusting this record grammar alone.

    def recover(self, witness, instance, revision, path, supervisor_key):
        """Return a reopened witness only AFTER its checkpoint is published.

        Must be called before any caller independently reopens the witness. There
        is deliberately no import of an already advanced checkpoint. A failed
        open (even contention/path/key failure) after INTENT leaves a permanent
        recovery fence. No pending RETAIN or prior recovery can be resumed here.
        """
        with self._lock:
            self._live()
            state = self._cas(witness, instance, revision)
            path = self._path(path)
            if (type(supervisor_key) is not bytes or len(supervisor_key) != 32 or
                    supervisor_key == self._key or supervisor_key in self._registry.values()):
                raise Rejected('distinct provisioned supervisor key required')
            before = state['checkpoint'].encode('ascii')
            self._append('RECOVERY_INTENT', {'witness': witness, 'instance': instance,
                         'revision': revision, 'path': path, 'operation': 'RECOVER'})
            local = None
            try:
                local = AnchorWitness(path, self._registry[witness], supervisor_key, witness, before)
                after = local.checkpoint()
                # Read the exact fd held/locked by the constructor, never reopen a
                # pathname to obtain publication evidence. Bound the read by the
                # accepted witness size and reject changed storage.
                local._live()
                raw = os.pread(local._fd, local._size + 1, 0)
                if len(raw) != local._size or not raw.endswith(b'\n'):
                    raise Rejected('changed recovery journal')
                record = raw[:-1].rsplit(b'\n', 1)[-1]
                self._verify_recovery(witness, before, after, record)
                self._append('RECOVERY_PUBLISH', {'witness': witness,
                             'checkpoint': after.decode('ascii'), 'record': record.decode('ascii')})
                return {'witness': local, 'holder_revision': self._states[witness]['revision'],
                        'admission_authority': False}
            except BaseException:
                if local is not None:
                    local.close()
                raise
