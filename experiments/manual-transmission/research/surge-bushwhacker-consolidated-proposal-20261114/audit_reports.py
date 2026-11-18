"""Independent fail-closed raw-report audit for MT-SURGE-BW-20261114."""
from pathlib import Path
from collections import Counter
import base64,gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
P=Path(__file__).resolve().parent
def read(p):return json.loads(Path(p).read_text())
def require(ok,message):
    if not ok:raise ValueError(message)
def digest(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def xml_rows(directory):
    rows=[]
    files=sorted(Path(directory).rglob('TEST-*.xml'))
    require(bool(files),f'missing XML: {directory}')
    for f in files:
        rel=f.relative_to(directory).as_posix();require('/build/test-results/' in rel,f'unknown module layout {rel}')
        module=rel.split('/build/test-results/',1)[0]
        root=ET.parse(f).getroot()
        require(root.tag=='testsuite',f'wrong XML root {f}')
        cases=list(root.iter('testcase'))
        counts={'tests':len(cases),'failures':sum(c.find('failure') is not None for c in cases),'errors':sum(c.find('error') is not None for c in cases),'skipped':sum(c.find('skipped') is not None for c in cases)}
        for k,n in counts.items():require(int(root.get(k,'-1'))==n,f'XML count mismatch {f}: {k}')
        for row in root.iter('testcase'):
            ident='\t'.join([module,row.get('classname',''),row.get('name','')])
            rows.append({'identity':ident,'skip':row.find('skipped') is not None,'failure':row.find('failure'),
                'error':row.find('error'),'xml':rel})
    return rows
# BEGIN EXACT M01 AUDIT EXCEPTION
def check_m01_rows(directory,expected,skips,negative):
    prefix='mtg-sets\tcom.wingedsheep.mtg.sets.RecklessBushwhackerRegistrationTest\t'
    names=['RG01 OGW116 canonical discovery exactly once','RG02 printed fields match pinned source','RG03 snapshot preserves all existing OGW entries','RG04 serialized script matches exact surge composition','RG05 Manual registry resolves new card without official games']
    identities=[prefix+n for n in names]
    require(negative['witness']==identities[3] and negative['positive_control']==identities[1],'M01 exact controls')
    require(Counter(expected)==Counter({identities[3]:1,identities[1]:1}) and not skips,'M01 selected contract')
    commands=[c for c in read(Path(directory).parent/'COMMANDS.json') if c['name']=='M01-surge-price']
    argv=['scripts/gradle-locked',':mtg-sets:cleanTest',':mtg-sets:test','--tests',prefix.split('\t')[1]+'.'+names[3],'--tests',prefix.split('\t')[1]+'.'+names[1],'--no-build-cache','--no-daemon','--console=plain','--stacktrace','--max-workers=2','-PkotlinCompileParallelism=1']
    require(len(commands)==1 and commands[0]['argv']==argv and commands[0]['returncode']!=0,'M01 exact selected command')
    rows=xml_rows(directory)
    require(Counter(r['identity'] for r in rows)==Counter(identities),'M01 exact five raw identities')
    require(Counter(r['identity'] for r in rows if r['skip'])==Counter([identities[0],identities[2],identities[4]]),'M01 exact framework skips')
    require(not any(r['error'] is not None for r in rows),'M01 error rows')
    failed=[r for r in rows if r['failure'] is not None]
    require(len(failed)==1 and failed[0]['identity']==identities[3] and not failed[0]['skip'],'M01 sole executed RG04 failure')
    positive=next(r for r in rows if r['identity']==identities[1])
    require(not positive['skip'] and positive['failure'] is None,'M01 RG02 positive control')
    failure=failed[0]['failure']
    require(negative['expected_exception']=='org.opentest4j.AssertionFailedError' and failure.get('type')==negative['expected_exception'],'M01 assertion type')
    require(negative['source_file']=='RecklessBushwhackerRegistrationTest.kt' and negative['source_line']==49,'M01 assertion location contract')
    text=failure.get('message','')+'\n'+''.join(failure.itertext())
    require('RecklessBushwhackerRegistrationTest.kt:49' in text,'M01 assertion location')
    expected_value='SelfAlternativeCost(manaCost={1}{R}, additionalCosts=[], condition=PlayerCastSpellsThisTurn(player=You, filter=GameObjectFilter(cardPredicates=[], statePredicates=[], controllerPredicate=null, anyOf=[]), atLeast=1, fromZone=null, fromZoneOtherThan=null))'
    actual_value='SelfAlternativeCost(manaCost={2}{R}, additionalCosts=[], condition=PlayerCastSpellsThisTurn(player=You, filter=GameObjectFilter(cardPredicates=[], statePredicates=[], controllerPredicate=null, anyOf=[]), atLeast=1, fromZone=null, fromZoneOtherThan=null))'
    pairs=re.findall(r'^expected:<(SelfAlternativeCost\(.*\))> but was:<(SelfAlternativeCost\(.*\))>$',text,re.M)
    require(bool(pairs) and all(pair==(expected_value,actual_value) for pair in pairs),'M01 exact directed structured values')
    require(text.count('SelfAlternativeCost(')==2*len(pairs),'M01 unambiguous structured roles')
    return {'rows':5,'executed':2,'skips':3,'failures':1}
# END EXACT M01 AUDIT EXCEPTION
def check_rows(directory,expected,skips=None,negative=None):
    if negative is not None and negative.get('id')=='M01-surge-price':return check_m01_rows(directory,expected,skips,negative)
    rows=xml_rows(directory);actual=Counter(r['identity'] for r in rows)
    require(actual==Counter(expected),f'identity/multiplicity mismatch in {directory}: missing={Counter(expected)-actual}; extra={actual-Counter(expected)}')
    require(Counter(r['identity'] for r in rows if r['skip'])==Counter(skips or {}),f'skip drift in {directory}')
    require(not any(r['error'] is not None for r in rows),f'error rows in {directory}')
    failed=[r for r in rows if r['failure'] is not None]
    if negative is None:require(not failed,f'unexpected failures in {directory}')
    else:
        require(len(failed)==1 and failed[0]['identity']==negative['witness'],f'wrong negative identity in {directory}')
        failure=failed[0]['failure'];require(failure.get('type')==negative['expected_exception'],f'wrong assertion type in {directory}')
        text=(failure.get('message','')+'\n'+''.join(failure.itertext()))
        require(f"{negative['source_file']}:{negative['source_line']}" in text,f'wrong assertion line in {directory}')
        def literal(v):return str(v).lower() if v is None or isinstance(v,bool) else str(v)
        expected='null' if negative['normal_expected'] is None else literal(negative['normal_expected'])
        actual='null' if negative['mutant_expected'] is None else literal(negative['mutant_expected'])
        # Preserve and inspect both labelled values, not just a generic assertion failure.
        require(re.search(r'expected\s*:?\s*<?'+re.escape(expected)+r'>?',text,re.I) is not None,f'wrong expected value {directory}')
        require(re.search(r'(?:but was|actual)\s*:?\s*<?'+re.escape(actual)+r'>?',text,re.I) is not None,f'wrong actual value {directory}')
    return {'rows':len(rows),'skips':sum(r['skip'] for r in rows),'failures':len(failed)}
def client_rows(path):
    report=read(path);require(report.get('success') is True,f'client failed {path}')
    rows=[]
    for suite in report.get('testResults',[]):
        name=suite['name'];marker='/web-client/'
        file='web-client/'+name.split(marker,1)[1] if marker in name else 'web-client/'+name.removeprefix('web-client/')
        for row in suite.get('assertionResults',[]):
            require(row['status']=='passed',f'client row not passed {file}: {row}')
            rows.append((file,row['title']))
    require(bool(rows),f'empty client {path}')
    return Counter(rows)
def audit_all(evidence):
    e=Path(evidence);matrix=read(P/'TEST_MATRIX.json');witness=read(P/'COMPILE_WITNESSES.json')
    normal=Counter({r['identity']:1 for r in matrix['rows'] if r['family']!='client'})
    require(sum(normal.values())==67,'frozen normal count')
    commands=read(e/'COMMANDS.json')
    commands={r['name']:r for r in commands}
    for form in ['normal']+[m['id'] for m in witness['mutants']]:
        require(commands['compile-'+form]['returncode']==0,'compile failed '+form)
    result={'normal':check_rows(e/'normal',normal),'restored':check_rows(e/'restored',normal)}
    for m in witness['mutants']:
        result[m['id']]=check_rows(e/m['id'],{m['witness']:1,m['positive_control']:1},negative=m)
        require(commands[m['id']]['returncode']!=0,'negative masked exit '+m['id'])
        form=e/'source-forms'/m['id']/m['path']
        require(digest(form)==m['mutant_sha256'],'mutant source drift '+m['id'])
        require(digest(e/'source-forms'/'normal'/m['path'])==m['normal_sha256'],'normal source drift '+m['id'])
    baseline=json.loads(gzip.decompress(base64.b64decode((P/'ACCEPTED_INVENTORY.json.gz.b64').read_bytes())))
    cards=Counter(baseline['cards']['identities']);cards.update({r['identity']:1 for r in matrix['rows'] if r['family']=='registry'})
    ordinary=Counter(baseline['ordinary']['identities']);ordinary.update(normal)
    r2=read(P.parent/'reckless-own-prior-spell-diagnostic-proposal-20261106'/'PROTOCOL.json')
    require(len(r2['cases'])==24,'R2 normal inventory changed')
    ordinary.update({'rules-engine\tcom.wingedsheep.engine.scenarios.SelfAlternativePriorSpellEligibilityDiagnosticTest\t'+n:1 for n in r2['cases']})
    require(sum(cards.values())==401 and sum(ordinary.values())==19421,'compatibility inventory count drift')
    result['cards']=check_rows(e/'cards',cards,baseline['cards']['skips'])
    result['ordinary']=check_rows(e/'ordinary',ordinary,baseline['ordinary']['skips'])
    contract=read(P.parent/'final-compatibility-inventory-r1-20261027'/'CLIENT64_CONTRACT.json')
    wanted=Counter((f,n) for f,names in contract['client_cases'].items() for n in names)
    wanted.update((r['path'],r['name']) for r in matrix['rows'] if r['family']=='client')
    focused=client_rows(e/'client-focused.json');require(focused==wanted,'client70 exact multiset mismatch')
    full=client_rows(e/'client-full.json');require(not (wanted-full),'full client omits required rows')
    require(commands['client-typecheck']['returncode']==0,'typecheck failed')
    for name in ['normal','restored','cards','ordinary','client-focused','client-full','metadata-printing']:
        require(commands[name]['returncode']==0,'gate failed '+name)
    # Assay result is not automatically a semantic pass. Its complete report must be classified.
    require((e/'metadata-assay.stdout').is_file(),'missing Assay original')
    classification=read(e/'ASSAY_CLASSIFICATION.json')
    require(classification['all_rows_classified'] is True and classification['unexplained_drift']==[],'unclassified Assay drift')
    restoration=read(e/'RESTORATION.json');require(restoration['exact_normal_bytes'] is True,'source not restored')
    pins=read(e/'CANDIDATE_PINS.json')
    require(restoration['bindings']==pins['bindings'],'final source rebind drift')
    result['client']={'focused':sum(focused.values()),'full':sum(full.values()),'typecheck':True}
    return result
if __name__=='__main__':
    result=audit_all(sys.argv[1]);print(json.dumps(result,indent=2))
