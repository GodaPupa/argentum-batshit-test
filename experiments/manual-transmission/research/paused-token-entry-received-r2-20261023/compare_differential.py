"""Compare original 51 blocks; write explicitly, then reproduce without writes."""
import argparse
import json
import sys
sys.dont_write_bytecode = True
from common import artifact, compare_differential, frozen_inputs, save_or_compare

parser = argparse.ArgumentParser()
parser.add_argument('--write', action='store_true')
args = parser.parse_args()
config = frozen_inputs()
meta, archive, names, inventory = artifact(config)
report = compare_differential(meta, archive, names)
save_or_compare('DIFFERENTIAL_COMPARISON.json', report, args.write)
print(json.dumps(report, indent=2))
