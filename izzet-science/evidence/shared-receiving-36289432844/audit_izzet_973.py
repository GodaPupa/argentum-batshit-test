import collections
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

path, output_path = sys.argv[1:3]
sha = lambda b: hashlib.sha256(b).hexdigest()
errors = []
detail = []

def require(condition, message):
    if not condition:
        errors.append(message)

with zipfile.ZipFile(path) as z:
    bad = z.testzip()
    require(bad is None, f"ZIP CRC failure: {bad}")
    names = set(z.namelist())
    audit = json.loads(z.read('audit.json'))
    gate = json.loads(z.read('control/receiving-gate.json'))
    before = json.loads(z.read('source-before.json'))
    after = json.loads(z.read('source-after.json'))
    require(audit['status'] == 'PASS_REQUIRES_INDEPENDENT_ARTIFACT_REVIEW', 'collector status')
    require(audit['errors'] == [], 'collector errors')
    require((audit['source_head'],audit['source_tree'],audit['control_head'],audit['run_id'],audit['attempt'],audit['event']) ==
      ('60d2d6f6412d1f4e9238ca0c84755c34cd31f8d5','1a903dc4862d32fdd029e73a916cc9659678db4d',
       '9e9c2cb8070e20b5e81f0065027632c2e54c1ae7','36289432844','1','push'), 'source/control/run binding')
    require(audit['source_before'] == before == after == audit['source_after'], 'before/after source snapshots')
    require(before['head'] == gate['source_head'] == audit['source_head'] and
      before['tree'] == gate['source_tree'] == audit['source_tree'], 'source snapshot gate binding')
    require(before['authority_sha256'] == gate['preserved_authority_sha256'], 'seven authority hashes')
    require(len(before['authority_sha256']) == 7, 'authority count')
    require(set(audit['dependency_files_sha256']) == set(gate['dependency_files']) and
      len(gate['dependency_files']) == 8, 'dependency file identities')
    for fname, expected in audit['control_files_sha256'].items():
        require(sha(z.read('control/'+fname)) == expected, f'control digest {fname}')
    require(len(gate['banks']) == len(audit['stages']) == gate['expected_classes'] == 87, 'bank count')
    require(gate['expected_cases'] == 973, 'declared cases')
    all_ids = set()
    total = 0
    expected_archive = {'audit.json','source-before.json','source-after.json'} | {'control/'+x for x in audit['control_files_sha256']}
    for i,(bank, stage) in enumerate(zip(gate['banks'],audit['stages']),1):
        label = f'{i:02d}-{bank["stage"]}'
        rows = {k:label+'/'+k for k in ('command.json','command.log','exit-status.txt')}
        xml_path = label+'/TEST-'+bank['class']+'.xml'
        expected_archive.update(rows.values()); expected_archive.add(xml_path)
        require(bank['stage'] == stage['stage'] and bank['class'] == stage['class'] and bank['module'] == stage['module'], f'bank/stage identity {i}')
        require(bank['test_source_git_blob'] == before['test_source_git_blobs'][bank['test_source']], f'test source blob {i}')
        require(stage['status'] == 'PASS_PRESERVED_CASE_IDENTITIES' and stage['exit_status'] == 0, f'stage result {i}')
        require(z.read(rows['exit-status.txt']) == b'0\n', f'raw exit status {i}')
        require(json.loads(z.read(rows['command.json'])) == stage, f'raw command sidecar {i}')
        data = z.read(xml_path)
        require(sha(data) == stage['actual']['xml_sha256'], f'raw XML hash {i}')
        root = ET.fromstring(data)
        actual_names = [case.attrib['name'] for case in root.iter('testcase')]
        require(root.attrib['name'] == bank['class'], f'XML class {i}')
        require(len(actual_names) == bank['expected_cases'] == stage['expected_cases'] == stage['actual']['actual_cases'], f'XML case count {i}')
        require(sorted(actual_names) == sorted(bank['case_names']) == sorted(stage['actual']['case_names']), f'case identities {i}')
        require(all(int(root.attrib.get(k,'-1')) == v for k,v in [('tests',len(actual_names)),('failures',0),('errors',0),('skipped',0)]), f'XML result counters {i}')
        require(not list(root.iter('failure')) and not list(root.iter('error')) and not list(root.iter('skipped')), f'XML error/skip elements {i}')
        for n in actual_names:
            require((bank['class'],n) not in all_ids, f'duplicate identity {i} {n}')
            all_ids.add((bank['class'],n))
        log = z.read(rows['command.log']).decode(errors='replace')
        marker = '> Task :'+bank['module'].replace('/',':')+':test'
        task_lines = [line for line in log.splitlines() if line == marker or line.startswith(marker+' ')]
        require(task_lines and all(line == marker for line in task_lines), f'fresh target test task {i}')
        require('BUILD SUCCESSFUL' in log and '0 actionable tasks' not in log, f'build outcome {i}')
        total += len(actual_names)
        detail.append({'stage':bank['stage'],'class':bank['class'],'cases':len(actual_names),'xml_sha256':sha(data),'test_task_lines':len(task_lines)})
    require(total == len(all_ids) == 973, '973 unique actual cases')
    require(names == expected_archive and len(names) == 354, f'archive membership, extra={sorted(names-expected_archive)}, missing={sorted(expected_archive-names)}')
    require(audit['official_games'] == audit['official_seeds'] == audit['new_pilot_cases'] == 0 and not audit['gameplay_authorized'] and not audit['full_runtime_accepted'], 'no gameplay/admission')

review = {
    'schema':'izzet-973-implementer-raw-artifact-audit-v1',
    'role':'implementer audit; independent source/control author review remains separate; independent raw acceptance pending',
    'artifact':{'id':10922204523,'name':'izzet-shared-receiving-36289432844-1','zip_sha256':sha(open(path,'rb').read()),'zip_members':len(names),'crc':'PASS'},
    'run':{'id':36289432844,'attempt':1,'event':'push','control_head':audit['control_head'],'source_head':audit['source_head'],'source_tree':audit['source_tree']},
    'result':{'status':'PASS_RAW_INTEGRITY_PENDING_INDEPENDENT_REVIEW' if not errors else 'FAIL_RAW_AUDIT', 'classes':len(detail),'actual_unique_cases':len(all_ids),'case_total':total,'xml_failures':0 if not errors else None,'xml_errors':0 if not errors else None,'xml_skips':0 if not errors else None,'all_command_exits_zero':not errors,'fresh_target_test_task_markers':len(detail) if not errors else None},
    'checks':['ZIP CRC and exact 354-member manifest','control digests, exact source/tree and before/after source snapshots','seven authority digests, dependency and test source bindings','87 command sidecars and raw exits/logs','each raw JUnit XML class/name/result counter, 973 unique case identities','no official games, seeds, or gameplay admission'],
    'errors':errors,
    'stages':detail,
}
with open(output_path,'w') as f: json.dump(review,f,indent=2,sort_keys=True); f.write('\n')
print(json.dumps({k:v for k,v in review.items() if k != 'stages'},indent=2))
