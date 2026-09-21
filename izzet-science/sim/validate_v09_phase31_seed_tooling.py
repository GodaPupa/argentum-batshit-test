#!/usr/bin/env python3
"""Seed-free static qualification of Phase-31 generator/freeze tooling."""
from pathlib import Path
import ast, hashlib, json, tempfile
import phase31_seed_vector as m

ROOT=Path(__file__).resolve().parents[1]
REG=ROOT/"seed-registry-v1.json"

def main():
    data=json.loads(REG.read_text())
    vals=tuple(int(v) for v in data["signed_i64_values"])
    assert len(vals)==len(set(vals))
    assert all(m.MIN_I64 <= v <= m.MAX_I64 and v != 0 for v in vals)

    # Valid synthetic vector, no generation invoked.
    synthetic=tuple(900000000000000000+i for i in range(1,13))
    m.validate_vector(synthetic,set(vals))
    assert m.assignment_vector().count("play")==6
    assert m.assignment_vector().count("draw")==6

    # Fail closed on zero, duplicates, overlap, width.
    cases=[
        (tuple([0]+list(synthetic[1:])), "zero"),
        (tuple([synthetic[1]]+list(synthetic[1:])), "duplicate"),
        (tuple([vals[0]]+list(synthetic[1:])), "overlap"),
        (tuple([m.MAX_I64+1]+list(synthetic[1:])), "width"),
    ]
    for candidate,_ in cases:
        try:
            m.validate_vector(candidate,set(vals))
            raise AssertionError("invalid vector accepted")
        except ValueError:
            pass

    # Freeze synthetic vector and prove public manifest contains no raw seeds.
    reg_digest=hashlib.sha256(REG.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory() as td:
        q=Path(td)/"private"/"vector.json"
        p=Path(td)/"public"/"manifest.json"
        m.freeze(synthetic,set(vals),q,p,reg_digest,"phase29-test","runner-test")
        private=q.read_text()
        public=p.read_text()
        assert all(str(v) in private for v in synthetic)
        assert all(str(v) not in public for v in synthetic)
        manifest=json.loads(public)
        assert manifest["count"]==12
        assert manifest["izzet_play"]==6 and manifest["izzet_draw"]==6
        assert manifest["experimental_seeds_consumed"]==0
        assert manifest["games_initialized"]==0
        assert manifest["outcome_exposure"]==0

    # Static AST boundary: exactly one secrets.randbits call in generator function;
    # no random/os.urandom deterministic fallback.
    src=Path(m.__file__).read_text()
    tree=ast.parse(src)
    calls=[n for n in ast.walk(tree) if isinstance(n,ast.Call)]
    randbits=sum(
        isinstance(n.func,ast.Attribute) and isinstance(n.func.value,ast.Name)
        and n.func.value.id=="secrets" and n.func.attr=="randbits" for n in calls
    )
    assert randbits==1
    assert "random." not in src and "os.urandom" not in src
    assert "while " not in src, "no reroll loop may exist"

    print("V09_PHASE31_SEED_TOOLING_STATIC_VALIDATION_PASS")

if __name__=="__main__":
    main()
