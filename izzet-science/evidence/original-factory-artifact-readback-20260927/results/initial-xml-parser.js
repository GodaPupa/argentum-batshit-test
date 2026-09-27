
function auditFactoryRaw(inputs, expected, hash) {
  const records = inputs.raw.value.records, manifest = inputs.members.value;
  const fail = m => { throw new Error(m); };
  const ok = (v,m) => { if(!v) fail(m); };
  const stable = v => Array.isArray(v) ? "["+v.map(stable).join(",")+"]" : v && typeof v==="object" ? "{"+Object.keys(v).sort().map(k=>JSON.stringify(k)+":"+stable(v[k])).join(",")+"}" : JSON.stringify(v);
  const same = (a,b,m) => ok(stable(a)===stable(b),m);
  const parsed = path => JSON.parse(records[path].replace(/("(?:started_ns|finished_ns|observed_ns)"\s*:\s*)(\d+)/g,'$1"$2"'));
  const crc32 = bytes => {let c=0xffffffff;for(const b of bytes){c^=b;for(let n=0;n<8;n++)c=(c>>>1)^((c&1)?0xedb88320:0);}return ((c^0xffffffff)>>>0).toString(16).padStart(8,"0");};
  const index = new Map(manifest.map(v=>[v.path,v]));
  ok(index.size===63 && manifest.length===63,"member uniqueness/count");
  ok(Object.keys(records).length===44,"raw count");
  const rawProof=[];
  for(const [path,body] of Object.entries(records)) {
    const m=index.get(path),bytes=hash.utf8Bytes(body),sha=hash.sha256Utf8(body),crc=crc32(bytes);
    ok(m && m.raw_text_relayed===true,"selected member "+path);
    ok(m.bytes===bytes.length && m.sha256===sha && m.crc32===crc,"raw hash/length/crc "+path);
    rawProof.push({path,bytes:bytes.length,sha256:sha,crc32:crc});
  }
  same([...index.values()].filter(v=>v.raw_text_relayed).map(v=>v.path).sort(),Object.keys(records).sort(),"selection completeness");
  const gate=parsed("control/gate.json"), audit=parsed("audit.json"), prereq=parsed("build-semaphore/prerequisite.json");
  same(gate.banks,expected.banks,"immutable bank contract");
  same(gate.sources,expected.sources,"immutable source contract");
  same(gate.preserved_authority_sha256,expected.authority,"immutable authorities");
  same(gate.dependency_sha256,expected.dependencies,"immutable dependencies");
  same(gate.resource_limits,expected.limits,"immutable caps");
  same(gate.build_semaphore,expected.semaphore,"immutable semaphore");
  const controls={};
  for(const path of ["qualify.py","gate.json",".github/workflows/izzet-legacy-factory-qualification.yml"]) {
    const sha=hash.sha256Utf8(records["control/"+path]); same(sha,audit.control_files_sha256[path],"control hash "+path);controls[path]=sha;
  }
  same(controls,{"qualify.py":"ed3c72a1fa5494598f212d0981e37eb0f6cf1c9116bf61938f2fa8dca9ad88e8","gate.json":"4b8a07746888eab107132fb30baed354e2ca7b481760e48556b6dd7f6296a826",".github/workflows/izzet-legacy-factory-qualification.yml":"53165b8e9920c3c6c46c16a5c642106fbe8a5d010a18ebe6661c6fc9b6d6d74c"},"actual controls exact");
  const sourceProof=[];
  const testBlobs=Object.fromEntries(expected.banks.map(b=>[b.test_source,b.test_source_git_blob]));
  for(const path of Object.keys(records).filter(p=>/(^|\/)source-(before|after)(-[^/]*)?\.json$/.test(p))) {
    const v=parsed(path),exp=expected.sources[v.role];
    ok(exp,"unknown source role");
    ok(v.head===exp.head && v.tree===exp.tree && v.production_sha256===exp.production_sha256 && v.status==="" && v.errors.length===0,"source identity/clean "+path);
    same(v.authority_sha256,expected.authority,"authority pins "+path);
    same(v.dependency_sha256,expected.dependencies,"dependency pins "+path);
    same(v.test_source_git_blobs,testBlobs,"test blob pins "+path);
    sourceProof.push({path,role:v.role,head:v.head,tree:v.tree,authority_pins:Object.keys(v.authority_sha256).length,dependency_pins:Object.keys(v.dependency_sha256).length,test_blobs:Object.keys(v.test_source_git_blobs).length,production_sha256:v.production_sha256});
  }
  ok(sourceProof.length===10,"ten source observations");
  for(const role of ["before","fixed"])same(parsed("source-before-"+role+".json"),parsed("source-after-"+role+".json"),"global source stability "+role);
  const entity = s=>s.replace(/&(#x[0-9a-fA-F]+|#\d+|amp|lt|gt|quot|apos);/g,(_,v)=>v[0]==="#"?String.fromCodePoint(v[1]==="x"?parseInt(v.slice(2),16):parseInt(v.slice(1),10)):({amp:"&",lt:"<",gt:">",quot:'"',apos:"'"})[v]);
  const attrs = s=>{const out={};let consumed="";for(const m of s.matchAll(/([A-Za-z_:][A-Za-z0-9_.:-]*)="([^"]*)"/g)){ok(!(m[1] in out),"duplicate XML attribute");out[m[1]]=entity(m[2]);consumed+=m[0];}return out;};
  const parseMap = (xml,marker)=>{const all=[...xml.matchAll(new RegExp("^"+marker+" (\\{[^\\r\\n]+\\})$","gm"))];ok(all.length===1,"single complete map "+marker);const result={};for(const part of all[0][1].slice(1,-1).split(", ")){const pair=part.split("=");ok(pair.length===2 && !(pair[0] in result) && /^(true|false)$/.test(pair[1]),"map syntax");result[pair[0]]=pair[1]==="true";}return result;};
  const bankProof=[],ids=new Set();let pass=0,failures=0;
  for(let n=0;n<expected.banks.length;n++){
    const bank=expected.banks[n],folder=String(n+1).padStart(2,"0")+"-"+bank.stage,xmlPath=folder+"/TEST-"+bank.class+".xml",xml=records[xmlPath],cmd=parsed(folder+"/command.json"),cleanup=parsed(folder+"/command.log.process-cleanup.json");
    ok(typeof xml==="string" && !/<!DOCTYPE|<!ENTITY/i.test(xml),"raw XML");
    const suite=attrs(xml.match(/<testsuite\b([^>]+)>/)[1]);
    ok(suite.name===bank.class && Number(suite.tests)===bank.expected_cases && +suite.errors===0 && +suite.skipped===0,"suite header");
    const cases=[...xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)].map(m=>({attributes:attrs(m[1]),body:m[2]||""}));
    ok(cases.length===bank.expected_cases && new Set(cases.map(c=>c.attributes.name)).size===cases.length,"raw actual count");
    same(cases.map(c=>c.attributes.name).sort(),[...bank.case_names].sort(),"case identities");
    const failRows=[];
    for(const c of cases) {
      ok(c.attributes.classname===bank.class && !/<(?:error|skipped)\b/.test(c.body),"case class/errors/skips");
      const fs=[...c.body.matchAll(/<failure\b([^>]*)>([\s\S]*?)<\/failure>/g)];ok(fs.length<=1,"failure count");
      if(fs.length) failRows.push({name:c.attributes.name,attributes:attrs(fs[0][1]),text:entity(fs[0][2])});
      ids.add(bank.class+"\0"+c.attributes.name);
    }
    same(failRows.map(f=>f.name).sort(),[...bank.expected_failure_names].sort(),"expected failure identities");
    ok(Number(suite.failures)===failRows.length && [...xml.matchAll(/<failure\b/g)].length===failRows.length,"suite failure count");
    if(failRows.length){const f=failRows[0],d=f.attributes.message+"\n"+f.text;ok(f.attributes.type==="org.opentest4j.AssertionFailedError" && d.includes("FROZEN_FACTORY_LEGACY_BINDING") && !d.includes("EXPLICIT_STRATEGY_DIFFERENTIAL") && !d.includes("STATE_UNCHANGED_AFTER_"),"specific preserved failure");}
    const maps={};
    if(bank.requires_observation_maps){
      maps.differential=parseMap(xml,"IZZET_LEGACY_FACTORY_DIFFERENTIAL");
      maps.same_id=parseMap(xml,"IZZET_LEGACY_FACTORY_SAME_ID_CONTROL");
      same(maps.differential,expected.expected_maps[bank.source_role],"complete strategy map");
      same(maps.same_id,expected.expected_maps.same_id,"same ID control map");
    }
    const command=["just","test-class",bank.class.split(".").pop(),"--rerun","--no-build-cache","--no-daemon","-DupdateSnapshots=false","--info","--stacktrace","--max-workers=1","-PkotlinCompileParallelism=1","-Pkotlin.compiler.execution.strategy=in-process","-Dorg.gradle.jvmargs=-Xmx4g"];
    same(cmd.command,command,"exact bank command");
    ok(cmd.source_role===bank.source_role && cmd.class===bank.class && cmd.stage===bank.stage,"command bank binding");
    ok(cmd.status==="PASS_DECLARED_BEHAVIOR_RAW_FAILURES_RETAINED" && cmd.output_complete===true && cmd.technical_limit===null && cmd.discarded_observed_pipe_bytes===0,"command completeness");
    ok(cmd.exit_status===(n===0?1:0) && records[folder+"/exit-status.txt"]===String(cmd.exit_status)+"\n","raw exit");
    ok(cmd.elapsed_seconds>0 && cmd.elapsed_seconds<expected.limits.command_timeout_seconds,"command timeout");
    ok(cmd.stdout_retained_bytes===index.get(folder+"/command.log").bytes && cmd.stdout_retained_bytes<expected.limits.stdout_bytes,"retained stdout");
    ok(cmd.observed_report_bytes_after_exit<expected.limits.test_result_bytes,"report limit");
    same(cmd.process_group_cleanup,cleanup,"separate cleanup exact");
    ok(cleanup.quiescent===true && cleanup.observation_complete===true && cleanup.errors.length===0 && cleanup.before.length===0 && cleanup.after.length===0 && cleanup.signals.length===0,"quiescent complete cleanup");
    for(const name of ["before_observation","latest_observation","after_observation"])ok(cleanup[name].complete===true && cleanup[name].errors.length===0 && cleanup[name].members.length===0,"cleanup observation");
    ok(cmd.owned_semaphore_observations.length>0,"real owned default lock observation");
    for(const s of cmd.owned_semaphore_observations)ok(s.holder_pid>0 && s.owned_process_group===cleanup.group && BigInt(s.observed_ns)>=BigInt(cmd.started_ns) && BigInt(s.observed_ns)<=BigInt(cmd.finished_ns),"lock within bank");
    const stamp=BigInt(Date.parse(suite.timestamp))*1000000n;
    ok(stamp>=BigInt(cmd.started_ns) && stamp<=BigInt(cmd.finished_ns),"XML timestamp inside command");
    ok(stamp+BigInt(Math.ceil(Number(suite.time)*1e9))<=BigInt(cmd.finished_ns)+2000000000n,"suite end inside command");
    const retention=cmd.xml_retention;ok(retention.length===1 && retention[0].complete && retention[0].observed_bytes===index.get(xmlPath).bytes && retention[0].retained_bytes===index.get(xmlPath).bytes && retention[0].sha256===index.get(xmlPath).sha256,"whole XML retained");
    same(parsed(folder+"/source-before.json"),parsed(folder+"/source-after.json"),"bank source stability");
    same(parsed(folder+"/source-before.json"),parsed("source-before-"+bank.source_role+".json"),"bank source same initial");
    const excerpt=inputs.derived.value.log_line_subsets[folder+"/command.log"],selected=excerpt.selected;
    ok(excerpt.kind==="explicit_line_subset_not_full_log" && excerpt.line_count>0,"log subset identity");
    ok(selected.filter(v=>/^Gradle Test Executor \d+ started executing tests\.$/.test(v.text)).length===1,"fresh executor start");
    ok(selected.filter(v=>/^Gradle Test Executor \d+ finished executing tests\.$/.test(v.text)).length===1,"fresh executor finish");
    ok(selected.some(v=>v.text==="> Task :ai:test") && !selected.some(v=>/^> Task :ai:test (FROM-CACHE|UP-TO-DATE|NO-SOURCE|SKIPPED)$/.test(v.text)),"fresh exact ai:test task");
    ok(!selected.some(v=>/running \.\/gradlew unlocked|shlock not found/.test(v.text)),"fallback scan subset absent");
    same(cmd,audit.stages[n],"audit embeds exact command record");
    pass+=cases.length-failRows.length;failures+=failRows.length;
    bankProof.push({stage:bank.stage,role:bank.source_role,class:bank.class,cases:cases.map(c=>c.attributes.name),passed:cases.length-failRows.length,failed:failRows.length,errors:0,skipped:0,failure:failRows.length?{name:failRows[0].name,type:failRows[0].attributes.type,message:failRows[0].attributes.message}:null,maps,xml_sha256:index.get(xmlPath).sha256,xml_timestamp:suite.timestamp,started_ns:cmd.started_ns,finished_ns:cmd.finished_ns,elapsed_seconds:cmd.elapsed_seconds,exit:cmd.exit_status,lock_observations:cmd.owned_semaphore_observations,cleanup_group:cleanup.group,complete:true,freshness_excerpt:excerpt});
  }
  ok(pass===10 && failures===1 && ids.size===9,"aggregate");
  ok(audit.errors.length===0 && audit.stages.length===3 && audit.official_games===0 && audit.official_seeds===0 && audit.gameplay_authorized===false && audit.full_runtime_accepted===false && audit.new_pilot_cases===0,"audit scope");
  ok(audit.control_head===expected.original_control && audit.run_id==="36295267824" && audit.attempt==="1" && audit.event==="push","original run identity");
  ok(audit.activation_event.created===true && audit.activation_event.before==="0".repeat(40) && audit.activation_event.after===expected.original_control,"original creation event");
  ok(prereq.status==="UPSTREAM_SHLOCK_PREREQUISITE_QUALIFIED_BEFORE_JVM" && prereq.errors.length===0 && prereq.commands.length===14,"prerequisite status");
  ok(prereq.default_lock==="/home/runner/.cache/argentum/gradle.lock" && prereq.resolved_command.endsWith("/shlock-prerequisite/bin/shlock") && prereq.binary.path.endsWith("/shlock-prerequisite/extracted/usr/lib/news/bin/shlock"),"actual default lock/binary paths");
  ok(prereq.binary.bytes===14640 && /^[0-9a-f]{64}$/.test(prereq.binary.sha256) && Object.keys(prereq.libraries).length===9,"binary/library hashes recorded");
  same(prereq.utility_checks.map(v=>v.exit_status),[0,1,0],"actual lock semantics");
  ok(new Set(prereq.utility_checks.map(v=>v.lock_content.trim())).size===1 && prereq.utility_checks.every(v=>/^\d+$/.test(v.lock_content.trim()) && v.stdout==="" && v.stderr===""),"utility owner preserved");
  ok(/\nID=ubuntu\n/.test("\n"+records["build-semaphore/os-release.txt"]) && /\nVERSION_ID="24.04"\n/.test("\n"+records["build-semaphore/os-release.txt"]),"actual os release");
  const packageProof=[];
  for(const pkg of prereq.packages){
    const path="build-semaphore/packages/"+pkg.file,m=index.get(path),text=records["build-semaphore/"+pkg.package+"-apt-metadata.txt"],meta=Object.fromEntries(text.split("\n").filter(l=>/^[A-Za-z0-9-]+: /.test(l)).map(l=>[l.slice(0,l.indexOf(": ")),l.slice(l.indexOf(": ")+2)]));
    ok(expected.semaphore.packages.includes(pkg.package) && pkg.version===expected.semaphore.package_version && meta.Architecture==="amd64","package identity");
    ok(m.bytes===pkg.bytes && m.sha256===pkg.sha256 && +meta.Size===pkg.bytes && meta.SHA256===pkg.sha256 && meta.Filename==="pool/universe/i/inn2/"+pkg.file,"package bytes vs metadata/manifest");
    packageProof.push({package:pkg.package,version:pkg.version,bytes:pkg.bytes,sha256:pkg.sha256,manifest_crc32:m.crc32,archive_bytes_independently_decompressed_by_this_reviewer:false});
  }
  ok(packageProof.length===2,"two public packages");
  for(const c of prereq.commands){
    ok(c.exit_status===0 && c.technical_limit===null && c.output_complete && c.discarded_observed_pipe_bytes===0,"prereq command result");
    ok(c.process_group_cleanup.quiescent && c.process_group_cleanup.observation_complete && c.process_group_cleanup.errors.length===0,"prereq cleanup");
    ok(BigInt(c.finished_ns)<BigInt(bankProof[0].started_ns),"prereq before JVM");
    const cap=(c.command[0]==="apt-get")?300:15;ok(c.elapsed_seconds<cap,"prereq bounded");
    ok(!c.command.includes("install") && !c.command.includes("sudo"),"no installation/root command");
  }
  const cleanupCount=Object.keys(records).filter(p=>p.endsWith(".process-cleanup.json")).length;
  ok(cleanupCount===17,"all command cleanup records");
  return {raw_record_hash_crc_proofs:rawProof,controls,source_observations:sourceProof,banks:bankProof,aggregate:{executions:11,distinct_case_identities:9,passed:10,preserved_expected_failures:1,errors:0,skips:0,unattempted_banks:0},prerequisite:{status:prereq.status,commands:14,all_exit_zero:true,all_quiescent:true,completed_before_first_bank:true,packages:packageProof,utility_checks:prereq.utility_checks,binary:prereq.binary,libraries:prereq.libraries,resolved_command:prereq.resolved_command,default_lock:prereq.default_lock},cleanup_record_count:cleanupCount,source_bindings_per_observation:29,full_archive_decompression_by_this_reviewer:false,full_command_logs_read_by_this_reviewer:false};
}
