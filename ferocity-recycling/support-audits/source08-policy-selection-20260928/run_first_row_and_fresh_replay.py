#!/usr/bin/env python3
from __future__ import annotations
import hashlib,json,os,pathlib,subprocess,sys

ROOT=pathlib.Path(os.environ["EXACT_REPO"]).resolve()
MANIFEST=ROOT/"ferocity-recycling/evidence/admission/D2-first-cell-current/manifest.json"
ALLOC=ROOT/"ferocity-recycling/allocations/D2-first-cell"
INTENT=ALLOC/"ALLOCATION_INTENT.json"
LEDGER=ALLOC/"ledger.json"
MANIFEST_SHA="44316db63408797e45b07cc084acc2d3479e6284601ca65f13847e348ac6662d"
INTENT_SHA="0bf7a24c0fd83159acca1fb3358968af999494374892c82c72bae7796b39d72d"
LEDGER_SHA="356a989ee1522947c66c053d9c596d6d3c185d934553fcde39602e34a7ce5f12"
ALLOCATION="FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/play/1"
NAMESPACE="ferocity-recycling/v0.1/D2"
EXPECTED=[
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/play/1",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/play/2",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/play/3",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/play/4",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/draw/1",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/draw/2",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/draw/3",
"FEROCITY_RECYCLING/v0.1/D2/A3-F4/mono_red_madness_mistertwin_20260924/draw/4",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/play/1",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/play/2",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/play/3",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/play/4",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/draw/1",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/draw/2",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/draw/3",
"FEROCITY_RECYCLING/v0.1/D2/A3-N0/mono_red_madness_mistertwin_20260924/draw/4"]

def sha(p):
    return hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()
def canonical(v):
    return json.dumps(v,sort_keys=True,separators=(",",":"),ensure_ascii=False).encode()
def identity_key():
    return hashlib.sha256(f"{len(NAMESPACE)}:{NAMESPACE}{ALLOCATION}".encode()).hexdigest()

assert ROOT==pathlib.Path("/home/runner/work/argentum-batshit-test/argentum-batshit-test")
assert sha(MANIFEST)==MANIFEST_SHA and sha(INTENT)==INTENT_SHA and sha(LEDGER)==LEDGER_SHA
assert not (ALLOC/"ALLOCATION_FAILURE.json").exists()
intent=json.loads(INTENT.read_text()); ledger=json.loads(LEDGER.read_text()); manifest=json.loads(MANIFEST.read_text())
assert intent["allocations"]==EXPECTED and intent["entropyCalls"]==48 and intent["replacementsAuthorized"]==0
def pid(r):
    a=r["allocation"]; return f"FEROCITY_RECYCLING/v0.1/D2/{a['deckId']}/mono_red_madness_mistertwin_20260924/{a['seat']}/{a['index']}"
assert [pid(r) for r in ledger["rows"]]==EXPECTED and len(ledger["rows"])==16
assert ledger["admissionSha256"]==MANIFEST_SHA and ledger["entropy"]=="java.security.SecureRandom/independent-nextLong/v1"
journal_root=ROOT/manifest["journalDirectory"]; supervisor_root=ROOT/manifest["supervisorDirectory"]
assert not journal_root.exists() and not supervisor_root.exists()
java=pathlib.Path(manifest["source"]["javaExecutable"]); watchdog=ROOT/manifest["watchdog"]["source"]["path"]
assert sha(java)==manifest["source"]["dependencies"]["java-executable"]
assert sha(watchdog)==manifest["watchdog"]["source"]["sha256"]
cp=os.pathsep.join(x["path"] for x in manifest["source"]["classPath"])
journal=journal_root/"journals"/(identity_key()+".jsonl")
run_id="D2-"+hashlib.sha256(ALLOCATION.encode()).hexdigest()[:24]
claim=supervisor_root/run_id/"claim.json"
argv=[str(java),"-Xmx2048m","-cp",cp,"com.wingedsheep.gym.ferocity.FerocityDevelopmentCli",
      "run-one",str(ROOT),str(MANIFEST),MANIFEST_SHA,LEDGER_SHA,ALLOCATION,str(claim)]
bundle=ROOT/manifest["bundle"]["path"]
pins={str(java):sha(java),str(MANIFEST):MANIFEST_SHA,str(LEDGER):LEDGER_SHA,str(bundle):manifest["bundle"]["sha256"]}
spec={"schema_version":1,"run_id":run_id,"argv":argv,"cwd":str(ROOT),"wall_seconds":manifest["watchdog"]["wallSeconds"],
      "term_grace_seconds":manifest["watchdog"]["termGraceSeconds"],"journal_path":str(journal),"pinned_files":pins,
      "supervisor_sha256":manifest["watchdog"]["source"]["sha256"],"inspection_limit_bytes":33554432,
      "file_size_limit_bytes":134217728,"minimum_free_bytes":805306368}
out=ROOT/"build/reports/ferocity-d2-first-row-runner-replay"; out.mkdir(parents=True,exist_ok=False)
spec_path=out/"watchdog-spec.json"; spec_path.write_bytes(canonical(spec))
python=manifest["watchdog"]["pythonExecutable"]
proc=subprocess.run([python,str(watchdog),"--spec",str(spec_path),"--output-root",str(supervisor_root)],
                    cwd=ROOT,text=True,capture_output=True)
(out/"watchdog-stdout.json").write_text(proc.stdout); (out/"watchdog-stderr.log").write_text(proc.stderr)
(out/"watchdog-exit-code.txt").write_text(str(proc.returncode)+"\n")
if proc.returncode!=0: raise SystemExit(proc.returncode)
summary=json.loads(proc.stdout)
assert summary["classification"]=="EXIT_ZERO_REQUIRES_ENGINE_REPLAY" and summary["input_pins_unchanged"] is True
assert journal.is_file()
before=sha(journal)

bridge=out/"FerocityFreshReplayBridge.java"
bridge.write_text(r'''import java.lang.reflect.*; import java.nio.file.*;
public final class FerocityFreshReplayBridge {
  static Method method(Class<?> c,String prefix,int n){
    for(Method m:c.getMethods()) if(m.getName().startsWith(prefix)&&m.getParameterCount()==n){m.setAccessible(true);return m;}
    throw new IllegalStateException("missing method "+prefix+"/"+n);
  }
  public static void main(String[] a)throws Exception{
    Path repo=Path.of(a[0]), manifest=Path.of(a[1]); String msha=a[2], lsha=a[3], allocation=a[4];
    Class<?> vc=Class.forName("com.wingedsheep.gym.ferocity.VerifiedFerocityDevelopment");
    Object companion=vc.getField("Companion").get(null);
    Object admitted=method(companion.getClass(),"verify",3).invoke(companion,repo,manifest,msha);
    Object registry=method(vc,"getRegistry",0).invoke(admitted);
    Object pins=method(vc,"pins",1).invoke(admitted,lsha);
    Object inline=method(vc,"getInlineTokens",0).invoke(admitted);
    Class<?> rc=Class.forName("com.wingedsheep.gym.ferocity.FerocityTrialReplay");
    Constructor<?> ctor=null; for(Constructor<?> c:rc.getConstructors()) if(c.getParameterCount()==3){ctor=c;break;}
    if(ctor==null) throw new IllegalStateException("missing replay constructor");
    Object replay=ctor.newInstance(registry,pins,inline);
    Object root=method(vc,"getJournalRoot",0).invoke(admitted);
    Object report=method(rc,"verify",3).invoke(replay,root,"ferocity-recycling/v0.1/D2",allocation);
    Object status=method(report.getClass(),"getStatus",0).invoke(report);
    Object games=method(report.getClass(),"getNewGameplayGames",0).invoke(report);
    if(!games.equals(0)) throw new IllegalStateException("replay initialized gameplay");
    System.out.println("FRESH_REPLAY_VERIFIED status="+status+" newGameplayGames=0");
  }
}''')
javac=java.parent/"javac"
subprocess.run([str(javac),"-cp",cp,str(bridge)],cwd=out,check=True)
replay_cp=str(out)+os.pathsep+cp
rp=subprocess.run([str(java),"-cp",replay_cp,"FerocityFreshReplayBridge",str(ROOT),str(MANIFEST),MANIFEST_SHA,LEDGER_SHA,ALLOCATION],
                  cwd=ROOT,text=True,capture_output=True)
(out/"fresh-replay-stdout.txt").write_text(rp.stdout); (out/"fresh-replay-stderr.log").write_text(rp.stderr)
(out/"fresh-replay-exit-code.txt").write_text(str(rp.returncode)+"\n")
if rp.returncode!=0: raise SystemExit(rp.returncode)
assert sha(journal)==before
receipt={"schema":"ferocity-d2-first-row-runner-replay-result-v1","authority":"ORIGINAL_FOR_INDEPENDENT_AUDIT_ONLY",
         "allocation_id":ALLOCATION,"accepted_ledger_sha256":LEDGER_SHA,"journal_sha256":before,
         "watchdog_classification":summary["classification"],"fresh_replay":"PASS_READ_ONLY_ZERO_NEW_GAMES",
         "official_delta":{"claims":1,"games_initialized_at_most":1,"allocations":0,"seed_streams":0,"replacements":0},
         "outcomes_interpreted":0}
(out/"result.json").write_bytes(canonical(receipt))
print(json.dumps({"status":"RECORDED_AND_FRESH_REPLAY_VERIFIED","allocation_id":ALLOCATION,"outcomes_interpreted":0},sort_keys=True))
