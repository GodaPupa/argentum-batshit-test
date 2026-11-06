"""Synthetic receiver controls. They provide no evidence about gameplay or R2 behavior."""
import copy, hashlib, json
from pathlib import Path
from receive import verify_envelope, verify_api

here = Path(__file__).parent
e = json.loads((here / "EXPECTED.json").read_text())
api = json.loads((here / "API_IMMUTABLE.json").read_text())
sha = lambda b: hashlib.sha256(b).hexdigest()
b = b"synthetic archive"
e.update(artifact=999, artifact_bytes=len(b), artifact_sha256=sha(b))
a = dict(id=999, name=e["artifact_name"], expired=False, size_in_bytes=len(b),
         digest="sha256:"+sha(b), workflow_run=dict(id=e["run"], head_sha=e["event"]))
api.update(run=dict(id=e["run"], head_sha=e["event"], run_attempt=1, run_number=1,
                    status="completed", repository={"full_name":e["repository"]},
                    head_branch=e["branch"], path=e["workflow"], event="push"),
           jobs=dict(total_count=1, jobs=[dict(id=e["job"],run_id=e["run"],run_attempt=1,
                                               head_sha=e["event"], status="completed")]),
           artifacts=dict(total_count=1, artifacts=[a]), artifact=a)
verify_api(api,e,b)
envelope = {k:e[k] for k in ("epoch","branch","event","event_tree","first_parent","first_tree",
                              "validation","validation_tree","source","workflow","protocol_blob","run")}
envelope.update(source_tree=e["tree"],event_workflow_blob=e["workflow_blob"],
                validation_workflow_blob=e["workflow_blob"],run_number=1,attempt=1)
files={"MANIFEST.json":b"{}", "RECOVERY_EVENT.json":json.dumps(envelope).encode(),
       "qualification/X":b"data","qualification/MANIFEST.json":json.dumps({"X":sha(b"data")}).encode()}
assert verify_envelope(files,e)["X"] == b"data"
rejected=[]
def reject(label, fn):
    try:
        fn()
    except (AssertionError, KeyError, ValueError):
        rejected.append(label)
    else:
        raise AssertionError("accepted corrupt fixture "+label)
for field in envelope:
    f=dict(files)
    f["RECOVERY_EVENT.json"]=json.dumps(dict(envelope, **{field:"invalid"})).encode()
    reject("envelope "+field, lambda:verify_envelope(f,e))
for path in ["qualification/X","qualification/extra","unbound-extra"]:
    f=dict(files, **{path:b"invalid"})
    reject("member "+path,lambda:verify_envelope(f,e))
for group,field,value in [
        ("run","head_sha",e["validation"]),("run","head_branch","manual-transmission/autonomous-execution"),
        ("validation_commit","parents",[{"sha":e["first_parent"]}]),
        ("event_commit","parents",[{"sha":e["validation"]},{"sha":e["first_parent"]}]),
        ("event_first_compare","files",[]),("run","run_attempt",2),
        ("artifacts","total_count",2)]:
    altered=copy.deepcopy(api)
    altered[group][field]=value
    reject(group+" "+field,lambda:verify_api(altered,e,b))
print(json.dumps({"positive_controls":2,"rejected":rejected,"behavioral_evidence":False},indent=2))
