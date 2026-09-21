#!/usr/bin/env python3
"""Audit a Phase-8 paired Capsize-policy JSON summary."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from paired_capsize_contract import validate_summary


def reject_duplicates(pairs):
    out={}
    for key,value in pairs:
        if key in out:
            raise ValueError(f"duplicate JSON key: {key}")
        out[key]=value
    return out


def reject_constant(value):
    raise ValueError(f"non-finite JSON constant: {value}")


def loads_strict(payload):
    return json.loads(payload,object_pairs_hook=reject_duplicates,
                      parse_constant=reject_constant)


def load_strict(path):
    return loads_strict(path.read_text(encoding="utf-8"))


def main() -> None:
    parser=argparse.ArgumentParser()
    parser.add_argument("summary",type=Path)
    parser.add_argument("--expected-source",required=True)
    parser.add_argument("--expected-seed",required=True,type=lambda x:int(x,0))
    parser.add_argument("--expected-samples",required=True,type=int)
    args=parser.parse_args()
    summary=load_strict(args.summary)
    validate_summary(summary,args.expected_source,args.expected_seed,args.expected_samples)
    print("audit=pass")
    print(f"schema={summary['schema']}")
    print(f"source_sha={summary['source_sha']}")
    print(f"master_seed={summary['master_seed']}")
    print(f"samples={summary['samples']}")
    print(f"through={summary['through']}")


if __name__=="__main__":
    main()
