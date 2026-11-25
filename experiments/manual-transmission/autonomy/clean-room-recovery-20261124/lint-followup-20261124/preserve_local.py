"""Preserve and independently audit the single terminal clean-room LOCAL attempt.

Reviewed operational preserver. No qualification, build, network publication,
CI receiving, relaunch, repair, or source mutation is performed.

Run only after runtime/LOCAL_TERMINAL.json exists and the original Manual flock
is free. The script acquires that same lock before reading source/evidence.
Example:
  python preserve_local.py --carrier-root CARRIER --source-root SOURCE \
      --runtime RUNTIME --host-root HOST --output NEW_OUTPUT_DIRECTORY

OUTPUT must not exist or be inside SOURCE or RUNTIME. The complete qualifier
output and runtime directory are copied byte-for-byte. Failures remain evidence.
The first resulting original.zip is a local terminal-evidence collection, NOT a
GitHub original artifact. Original-byte base64 parts use 450000-byte chunks.
Host restoration uses the frozen launcher's terminal equality attestation plus
a post-terminal property hash/key-schema check. No independent prelaunch hash
was recorded, and this script does not invent one or preserve proxy values.
"""
from pathlib import Path, PurePosixPath
import argparse, base64, datetime, fcntl, hashlib, json, os, shutil, stat, subprocess, sys, traceback, types, zipfile

sys.dont_write_bytecode = True
SOURCE='82ec335573572edfd2a2e4d734d2f0d81801139f'
SOURCE_TREE='923ec79e1c867eedf35b408153bf60988fbd26b6'
LAUNCH='9953c3e78c6f386f4284352f39ccf5d81554e10b'
CHARTER='86450c1ecb227804dbd45acf5ba28689ce0c61bc'
CHARTER_SHA='a9a2f3b775c6231d1a520679492aba4d2bf3bdea0f1b0abe3fc47be39ef0087e'
PINS_SHA='b0e8da912f98b75a35c44db4a3e9e3334d941af3891e0bb28281490ab01e7bbd'
AUDIT_SHA='aed497bac5eafd80fc141557768c7697472324d5da087cf1fe3fb66741075e81'
RECORD='experiments/manual-transmission/research/surge-bushwhacker-consolidated-proposal-20261114'
NS='experiments/manual-transmission/autonomy/clean-room-recovery-20261124/lint-followup-20261124'
EPOCH='manual-lint-followup-20261124-01'
PASS='ALL_LOCAL_GATES_PASS_ORIGINAL_RECEIVING_REQUIRED'
FAIL='FAILED_OR_INCOMPLETE_LOCAL_ORIGINAL_PRESERVED'
SOURCE_BASIS='2059afe031fb33dbe43a4741a3ca25ed9a2fabd9'
CHARTER_BLOB='418fb7f5a0ebeed92582f8285e76305c39250efa'
if any(str(value).startswith('PENDING_') for value in (SOURCE, SOURCE_TREE, LAUNCH, CHARTER, CHARTER_BLOB, CHARTER_SHA, PINS_SHA, AUDIT_SHA,)):
    raise RuntimeError('Unfinalized lint-followup draft: fill all pinned identities before use')



def need(ok, message):
    if not ok: raise ValueError(message)


def sha(b): return hashlib.sha256(b).hexdigest()


def digest(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()


def write(path, value):
    Path(path).parent.mkdir(parents=True,exist_ok=True)
    Path(path).write_text(json.dumps(value,indent=2)+'\n')


def read(path): return json.loads(Path(path).read_text())


def files(root):
    return sorted(p for p in root.rglob('*') if p.is_file())


def snapshot(source, target, errors):
    """Copy every regular file; record, never silently skip, an abnormal member."""
    if not source.is_dir():
        errors.append({'gate':'copy','path':str(source),'error':'missing original directory'})
        return
    for path in sorted(source.rglob('*')):
        rel=path.relative_to(source)
        try:
            mode=path.lstat().st_mode
            if stat.S_ISDIR(mode): continue
            need(stat.S_ISREG(mode),'non-regular original member; not followed')
            out=target/rel;out.parent.mkdir(parents=True,exist_ok=True)
            before=path.stat()
            shutil.copyfile(path,out)
            after=path.stat()
            need((before.st_size,before.st_mtime_ns)==(after.st_size,after.st_mtime_ns),
                 'original changed during copy')
            need(digest(path)==digest(out),'copied original bytes differ')
        except Exception as exc:
            errors.append({'gate':'copy','path':str(rel),'error':repr(exc)})


def archive_check(archive, expected):
    with zipfile.ZipFile(archive) as z:
        names=z.namelist()
        need(len(names)==len(set(names)),'duplicate archive member')
        for info in z.infolist():
            path=PurePosixPath(info.filename)
            need(not path.is_absolute() and '..' not in path.parts and '\\' not in info.filename,'unsafe archive member')
            need((info.external_attr>>16)&0o170000!=0o120000,'symlink archive member')
        need(set(names)==set(expected)|{'MANIFEST.json'},'outer archive member set')
        need(z.testzip() is None,'archive CRC failure')
        need(json.loads(z.read('MANIFEST.json'))==expected,'archived outer manifest differs')
        for name,wanted in expected.items(): need(sha(z.read(name))==wanted,'archive member hash '+name)
    return {'crc':'PASS','member_manifest':'PASS','members':len(expected)+1,
            'bytes':archive.stat().st_size,'sha256':digest(archive)}


def split_original(archive, destination):
    parts=[]
    with archive.open('rb') as f:
        for n,chunk in enumerate(iter(lambda:f.read(450000),b'')):
            name=f'original.part{n:04d}.b64'
            (destination/name).write_bytes(base64.b64encode(chunk)+b'\n')
            parts.append({'file':name,'bytes':len(chunk),'sha256':sha(chunk)})
    index={'bytes':archive.stat().st_size,'sha256':digest(archive),'parts':parts}
    write(destination/'ARCHIVE_PARTS.json',index)
    h=hashlib.sha256();total=0
    for item in parts:
        raw=base64.b64decode(b''.join((destination/item['file']).read_bytes().split()),validate=True)
        need(len(raw)==item['bytes'] and sha(raw)==item['sha256'],'original part readback '+item['file'])
        total+=len(raw);h.update(raw)
    need(total==index['bytes'] and h.hexdigest()==index['sha256'],'original parts exact archive readback')
    return {'state':'PASS','parts':len(parts),'bytes':total,'sha256':h.hexdigest()}


def main():
    a=argparse.ArgumentParser(description=__doc__)
    for name in ('carrier-root','source-root','runtime','host-root','output'):
        a.add_argument('--'+name,required=True,type=Path)
    args=a.parse_args()
    carrier=args.carrier_root.resolve();source=args.source_root.resolve()
    runtime=args.runtime.resolve();host=args.host_root.resolve();out=args.output.resolve()
    terminal_path=runtime/'LOCAL_TERMINAL.json'
    # These checks deliberately precede every access to the source tree.
    need(terminal_path.is_file(),'LOCAL_TERMINAL absent: do not inspect a live qualification')
    terminal_bytes=terminal_path.read_bytes();terminal=json.loads(terminal_bytes)
    need(terminal['source']==SOURCE and terminal['launch_commit']==LAUNCH,'terminal identity')
    need(terminal['state'] in ('QUALIFICATION_FINISHED','LAUNCH_OR_VERIFICATION_FAILED'),'terminal state')
    lock_path=Path.home()/'.cache/argentum/manual-transmission.lock'
    need(lock_path.is_file(),'original Manual lock missing; cannot establish local terminal boundary')
    lock=lock_path.open('r')
    try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError: raise RuntimeError('Manual launcher still holds its lock; preserve only after it exits')
    need(terminal_path.read_bytes()==terminal_bytes,'terminal changed while acquiring lock')
    need(not out.exists(),'output already exists; never overwrite or silently repeat preservation')
    need(not out.is_relative_to(source) and not out.is_relative_to(runtime),
         'output must be outside the original source and runtime')
    out.mkdir(parents=True)
    stage=out/'extracted';stage.mkdir()
    errors=[];checks={};audit_result=None;source_report={};host_report={}
    def gate(name, action):
        try:
            result=action();checks[name]={'state':'PASS'}
            return result
        except Exception as exc:
            checks[name]={'state':'FAIL','error':str(exc)}
            errors.append({'gate':name,'error':repr(exc),'traceback':traceback.format_exc()})
            return None
    def git(*argv):return subprocess.check_output(['git',*argv],cwd=carrier).decode().strip()
    def raw(ref,path):return subprocess.check_output(['git','show',ref+':'+path],cwd=carrier)
    # Copy originals before trying any acceptance audit, including failed output.
    snapshot(source/'manual-surge-bushwhacker-evidence',stage/'qualification',errors)
    snapshot(runtime,stage/'runtime',errors)
    evidence=stage/'qualification'
    authority=stage/'authority';authority.mkdir()
    preserver_bytes=Path(__file__).read_bytes()
    (authority/'preserve_local.py').write_bytes(preserver_bytes)
    authority_map={
        'CHARTER.json':(CHARTER,NS+'/CHARTER.json'),
        'LAUNCH_CURRENT.json':(LAUNCH,NS+'/CURRENT.json'),
        'SOURCE_FREEZE.json':(LAUNCH,NS+'/SOURCE_FREEZE.json'),
        'run_local_qualification.py':(LAUNCH,NS+'/run_local_qualification.py'),
        'CANDIDATE_PINS.json':(SOURCE,RECORD+'/CANDIDATE_PINS.json'),
        'audit_reports.py':(SOURCE,RECORD+'/audit_reports.py'),
        'qualify.py':(SOURCE,RECORD+'/qualify.py'),
    }
    for name,(ref,path) in authority_map.items():
        gate('immutable authority '+name,lambda n=name,r=ref,p=path:(authority/n).write_bytes(raw(r,p)))
    def authority_check():
        need(git('rev-parse',SOURCE+'^{tree}')==SOURCE_TREE,'exact source tree')
        need(git('show','-s','--format=%P',SOURCE)==SOURCE_BASIS,'lint followup source sole parent')
        need(git('rev-parse',CHARTER+':'+NS+'/CHARTER.json')==CHARTER_BLOB,'exact charter blob')
        need(digest(authority/'CHARTER.json')==CHARTER_SHA,'charter hash')
        need(digest(authority/'CANDIDATE_PINS.json')==PINS_SHA,'source pin metadata hash')
        need(digest(authority/'audit_reports.py')==AUDIT_SHA,'frozen amended auditor hash')
        pins=read(authority/'CANDIDATE_PINS.json');need(len(pins['bindings'])==378,'binding count')
        current=read(authority/'LAUNCH_CURRENT.json')
        need(current['recovery_id']==EPOCH and current['source_freeze']['source']==SOURCE,'launch source and epoch')
        need(current['source_freeze']['source_basis']==SOURCE_BASIS,'launch source basis')
        need(current['owner']==current['lease']['owner']=='manual-lint-foreground-20261124','launch owner')
        need(current['attempts']['local_reserved']==current['attempts']['local_started']==1,'single durable reservation')
        need(current['attempts']['local_completed']==current['attempts']['ci_activated']==current['attempts']['original_artifact_downloads']==0,'launch counters')
        need(read(authority/'SOURCE_FREEZE.json')['qualification']==current['source_freeze'],'immutable source freeze')
        return pins
    pins=gate('immutable source and launch authority',authority_check)
    def terminal_check():
        t=read(stage/'runtime/LOCAL_TERMINAL.json');launch=read(stage/'runtime/LOCAL_LAUNCH.json')
        event=read(stage/'runtime/RECOVERY_EVENT.json');current=read(authority/'LAUNCH_CURRENT.json')
        need('lint_followup' in event and 'clean_room' not in event,'exclusive new followup authority envelope')
        need(t==terminal,'copied terminal equals observed original')
        need(t['state']=='QUALIFICATION_FINISHED' and t['returncode']==0,'qualifier terminal return zero')
        need(launch['source']==SOURCE and launch['launch_commit']==LAUNCH,'runtime launch identity')
        need(launch['recovery_id']==EPOCH and launch['attempt_id']==EPOCH+'/local-01','runtime single attempt identity')
        need(launch['qualification_invocations']==t['qualification_invocations']==1,'exactly one original qualifier invocation')
        need(t['source_bindings_verified']==378 and t['all_bindings_match'] is True and t['failed_bindings']==[],'terminal source restoration')
        need(t['tracked_index_clean'] is True and t['host_properties_restored'] is True,'terminal clean source and restored host')
        need(t['phase2_games']==t['phase2_seeds']==t['ci_activations']==t['original_artifact_downloads']==0 and t['admitted'] is False,'terminal exclusions')
        need(event['source']==SOURCE and event['first_parent']==LAUNCH and event['qualification']==current['source_freeze'],'local event source freeze')
        need(event['local_execution'] is True and event['event'] is None and event['validation'] is None and event['run'] is None and event['attempt']==1,'local event is not CI')
        need(event['lint_followup']==dict(recovery_id=EPOCH,branch='manual-transmission/clean-room-recovery-20261124',charter_commit=CHARTER,
             charter_blob=CHARTER_BLOB,lane='local',attempt=1,attempt_id=EPOCH+'/local-01'),'exact local event identity')
        need(read(evidence/'RECOVERY_EVENT.json')==event,'qualifier event agrees with launcher original')
        return t
    gate('original terminal and invocation',terminal_check)
    def manifest_check():
        manifest=read(evidence/'MANIFEST.json')
        actual={p.relative_to(evidence).as_posix():digest(p) for p in files(evidence) if p!=evidence/'MANIFEST.json'}
        need(actual==manifest,'original qualifier manifest exact names and hashes')
        return {'files':len(actual),'manifest_sha256':digest(evidence/'MANIFEST.json')}
    manifest_result=gate('original inner manifest',manifest_check)
    def binding_check():
        nonlocal source_report
        need(pins is not None,'immutable pins unavailable')
        failed=[];verified=0
        for path,pin in pins['bindings'].items():
            try:
                live=(source/path).read_bytes();frozen=(evidence/'frozen-source'/path).read_bytes()
                need(len(live)==pin['bytes'] and sha(live)==pin['sha256'],'terminal source binding')
                need(len(frozen)==pin['bytes'] and sha(frozen)==pin['sha256'],'copied original frozen-source binding')
                verified+=1
            except Exception as exc:failed.append({'path':path,'error':str(exc)})
        head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source).decode().strip()
        status=subprocess.check_output(['git','status','--porcelain=v1','--untracked-files=no'],cwd=source).decode()
        source_report={'source':SOURCE,'head':head,'source_bindings_verified':verified,'expected_bindings':378,
                       'failed_bindings':failed,'tracked_index_clean':not bool(status.strip()),'tracked_status':status}
        need(verified==378 and not failed and head==SOURCE and not status.strip(),'fresh terminal source/frozen-source binding or tracked-index failure')
        need(read(evidence/'CANDIDATE_PINS.json')==pins,'original qualifier candidate pins differ')
        restoration=read(evidence/'RESTORATION.json')
        need(restoration['exact_normal_bytes'] is True and restoration['bindings']==pins['bindings'],'original restoration evidence')
        return source_report
    gate('terminal source and copied original bindings',binding_check)
    def host_check():
        nonlocal host_report
        current=read(authority/'LAUNCH_CURRENT.json')
        helper=(host/'environment.py').read_bytes();properties=(host/'gradle-user-home/gradle.properties').read_bytes()
        lines=[line for line in properties.decode().splitlines() if line and not line.startswith('#')]
        pairs=[line.split('=',1) for line in lines]
        need(all(len(pair)==2 for pair in pairs),'restored host property syntax')
        values=dict(pairs)
        base={'org.gradle.java.installations.paths','org.gradle.java.installations.auto-detect','org.gradle.java.installations.auto-download',
              'systemProp.javax.net.ssl.trustStore','systemProp.http.nonProxyHosts'}
        proxy={f'systemProp.{scheme}.proxy{field}' for scheme in ('http','https') for field in ('Host','Port')}
        schema_ok=len(pairs)==len(values) and base<=set(values)<=base|proxy
        schema_ok=schema_ok and values.get('org.gradle.java.installations.auto-detect')=='false' and values.get('org.gradle.java.installations.auto-download')=='false'
        schema_ok=schema_ok and values.get('org.gradle.java.installations.paths')==str(host/'jdk/jdk-21.0.13+9')
        schema_ok=schema_ok and values.get('systemProp.javax.net.ssl.trustStore')==str(host/'java-platform-cacerts')
        schema_ok=schema_ok and values.get('systemProp.http.nonProxyHosts')=='localhost|127.*|[::1]'
        for scheme in ('http','https'):
            h=f'systemProp.{scheme}.proxyHost';port=f'systemProp.{scheme}.proxyPort'
            schema_ok=schema_ok and ((h in values)==(port in values))
            if h in values:
                schema_ok=schema_ok and bool(values[h]) and '@' not in values[h] and not any(c.isspace() for c in values[h])
                schema_ok=schema_ok and values[port].isdigit() and 1<=int(values[port])<=65535
        host_report={'launcher_byte_equality_attested':terminal.get('host_properties_restored') is True,
                     'post_terminal_properties_sha256':sha(properties),'post_terminal_properties_bytes':len(properties),
                     'property_keys':sorted(values),'restored_key_schema':schema_ok,
                     'environment_helper_sha256':sha(helper),'independent_prelaunch_hash_available':False,
                     'scope':'Launcher terminal byte-equality attestation plus post-terminal hash and restored key schema; proxy values and full environment are not retained.'}
        need(sha(helper)==current['host_ready']['environment_helper_sha256'],'exact frozen host helper')
        need(host_report['launcher_byte_equality_attested'] and schema_ok,'host restoration attestation/schema')
        return host_report
    gate('host restoration evidence',host_check)
    def independent_audit():
        nonlocal audit_result
        need(pins is not None and manifest_result is not None,'pinned source and original inner manifest required')
        need(checks['terminal source and copied original bindings']['state']=='PASS','full frozen-source and terminal binding verification required')
        code=(authority/'audit_reports.py').read_bytes();need(sha(code)==AUDIT_SHA,'independent auditor hash')
        module=types.ModuleType('_independent_manual_local_auditor')
        module.__file__=str(evidence/'frozen-source'/RECORD/'audit_reports.py')
        exec(compile(code,module.__file__,'exec'),module.__dict__)
        commands=read(evidence/'COMMANDS.json')
        need(len(commands)==len({row['name'] for row in commands}),'duplicate qualification command names')
        result=read(evidence/'RESULT.json')
        need(result['state']==PASS and result['admitted'] is False,'original complete local result')
        compile_first=read(evidence/'COMPILE_FIRST.json')
        need(compile_first['forms']==15 and compile_first['all_compiled'] is True,'original compile-first gate')
        audit_result=module.audit_all(evidence)
        return audit_result
    gate('independent frozen raw-report audit',independent_audit)
    # All additional diagnostics are separate from unchanged original output trees.
    write(stage/'review/SOURCE_REBINDING.json',source_report)
    write(stage/'review/HOST_RESTORATION.json',host_report)
    write(stage/'review/INDEPENDENT_AUDIT.json',{'state':'PASS' if audit_result is not None else 'FAIL_OR_NOT_EXECUTED',
          'auditor_sha256':AUDIT_SHA,'result':audit_result,'original_inner_manifest':manifest_result})
    write(stage/'review/PRESERVATION_CHECKS.json',{'checks':checks,'errors':errors,'copied_originals_unchanged':not any(e['gate']=='copy' for e in errors),
          'qualification_invocations_started_by_this_script':0,'ci_downloads':0,'admitted':False})
    manifest={p.relative_to(stage).as_posix():digest(p) for p in files(stage)}
    write(stage/'MANIFEST.json',manifest)
    archive=out/'original.zip'
    def make_archive():
        with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9,allowZip64=True) as z:
            for path in files(stage):z.write(path,path.relative_to(stage).as_posix())
        return archive_check(archive,manifest)
    archive_result=gate('first local archive CRC and member readback',make_archive)
    parts_result=gate('original archive part readback',lambda:split_original(archive,out)) if archive.is_file() else None
    passed=not errors and audit_result is not None and archive_result is not None and parts_result is not None
    receipt={'schema':'manual-transmission/clean-room-local-receipt/v1','state':PASS if passed else FAIL,
             'preserver_sha256':sha(preserver_bytes),
             'source':SOURCE,'source_tree':SOURCE_TREE,'source_basis':SOURCE_BASIS,'launch_commit':LAUNCH,'recovery_id':EPOCH,
             'attempt_id':EPOCH+'/local-01','recorded_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
             'all_frozen_local_gates_pass':passed,'source_bindings_verified':source_report.get('source_bindings_verified',0),
             'archive_origin':'FIRST_TERMINAL_LOCAL_EVIDENCE_COLLECTION_NOT_CI_ARTIFACT',
             'archive_name':'original.zip','archive_bytes':archive.stat().st_size if archive.exists() else None,
             'archive_sha256':digest(archive) if archive.exists() else None,'archive_validation':archive_result,'parts_readback':parts_result,
             'qualifier_returncode':terminal.get('returncode'),'original_qualification_invocations':terminal.get('qualification_invocations'),
             'host_properties_restored':host_report.get('launcher_byte_equality_attested',False) and host_report.get('restored_key_schema',False),
             'host_restoration_evidence_basis':host_report,'checks':checks,'errors':errors,
             'raw_audit':audit_result,'admitted':False,'phase2_games':0,'phase2_seeds':0,'ci_activations':0,'original_artifact_downloads':0,
             'new_qualification_invocations':0,'requires_immutable_and_live_publication_readback':True}
    write(out/'LOCAL_RECEIPT.json',receipt)
    print(json.dumps({k:receipt[k] for k in ('state','source','source_bindings_verified','archive_bytes','archive_sha256','new_qualification_invocations')}))
    return 0 if passed else 1


if __name__=='__main__':
    sys.exit(main())

