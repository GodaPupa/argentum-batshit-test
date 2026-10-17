"""Verify local frozen package; no compiler, network or runtime invocation."""
import argparse
import ast
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def verify():
    expected={}
    for line in (ROOT/'SHA256SUMS').read_text().splitlines():
        h,n=line.split('  ',1)
        assert n not in expected and '..' not in Path(n).parts and not Path(n).is_absolute()
        expected[n]=h
    actual={p.relative_to(ROOT).as_posix() for p in ROOT.rglob('*') if p.is_file() and p.name!='SHA256SUMS' and '__pycache__' not in p.parts}
    assert actual==set(expected),'inventory drift'
    for n,h in expected.items():assert hashlib.sha256((ROOT/n).read_bytes()).hexdigest()==h,n
    for f in (ROOT/'src').glob('*.py'):ast.parse(f.read_text())
    assert hashlib.sha256((ROOT/'reference/v6-unit-contract.json').read_bytes()).hexdigest()=='6dddfa5f30311a76d19bebc5d7433ce3692e92e3324b0d42e7698d01f319c7d2'
    print(json.dumps({'manifest_entries':len(expected),'syntax':'PASS','runtime_tested':False,'runtime_qualified':False}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true',required=True);p.parse_args();verify()
