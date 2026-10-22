"""Extract original differential findings and structural deltas for review; never fold them."""
import json
import re
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parent
with zipfile.ZipFile(root/'manual-assay-search-qualified-39787740747.zip') as z:
    report = z.read('differential.log').decode()
headers = list(re.finditer(r'^  (.+)  \[([A-Z0-9]+)\]$', report, re.M))
decoder = json.JSONDecoder()

def diff(left, right, path='$'):
    if type(left) is not type(right):
        return [{'path':path,'from_text':left,'on_card':right}]
    if isinstance(left, dict):
        result = []
        for key in sorted(left.keys() | right.keys()):
            if key not in left or key not in right:
                result.append({'path':path+'.'+key,'from_text':left.get(key,'<ABSENT>'),'on_card':right.get(key,'<ABSENT>')})
            else:
                result += diff(left[key],right[key],path+'.'+key)
        return result
    if isinstance(left,list):
        if len(left) != len(right):
            return [{'path':path,'from_text':left,'on_card':right}]
        return [item for index,(a,b) in enumerate(zip(left,right)) for item in diff(a,b,path+f'[{index}]')]
    return [] if left == right else [{'path':path,'from_text':left,'on_card':right}]

findings = []
for index, match in enumerate(headers):
    block = report[match.end():headers[index+1].start() if index+1<len(headers) else len(report)]
    item = {'card':match.group(1),'set':match.group(2),'original_block':block}
    if 'script from text:' in block and 'script on the card:' in block:
        left = decoder.raw_decode(block.split('script from text:',1)[1].lstrip())[0]
        right = decoder.raw_decode(block.split('script on the card:',1)[1].lstrip())[0]
        item['raw_structural_differences'] = diff(left,right)
    findings.append(item)
assert len(findings) == 51
(root/'differential-findings.json').write_text(json.dumps(findings,indent=2)+'\n')
for item in findings:
    print(json.dumps({k:v for k,v in item.items() if k!='original_block'}))
