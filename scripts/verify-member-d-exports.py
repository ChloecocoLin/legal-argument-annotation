"""Loopback-only export acceptance. --create explicitly writes trial exports/tickets.
Credentials and download tokens stay in memory and are never written to output files.
"""
import argparse, hashlib, io, json, pathlib, time, urllib.request, urllib.error, urllib.parse, uuid, zipfile
import xml.etree.ElementTree as ET
parser=argparse.ArgumentParser()
parser.add_argument('--base-url',default='http://127.0.0.1:18080')
parser.add_argument('--final-id',required=True)
parser.add_argument('--create',action='store_true')
parser.add_argument('--verify',type=pathlib.Path)
args=parser.parse_args()
assert urllib.parse.urlparse(args.base_url).hostname in ('127.0.0.1','localhost','::1')
assert args.create != bool(args.verify), 'Choose --create or --verify BASELINE (tickets are issued in both modes)'
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'backend/target'
def request(path,token=None,body=None,key=None,binary=False,expected=200):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    if body is not None:headers['Idempotency-Key']=key or str(uuid.uuid4())
    req=urllib.request.Request(args.base_url+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=20) as r:
            assert r.status==expected
            return r.read() if binary else json.load(r)['data']
    except urllib.error.HTTPError as e:
        payload=json.load(e)
        assert e.code==expected,(e.code,payload.get('error'))
        return payload['error']
def login(name):return request('/api/v1/auth/login',body={'username':name,'password':'demo'})['token']
owner=login('creator.li');arbiter=login('arbiter.zhao')
workspace='/api/v1/integration/final-annotation-versions/'+args.final_id+'/workspace'
before=request(workspace,arbiter)['snapshot']
assert before['version']['status']=='IN_DATABASE'
records=[]
if args.create:
    for fmt in ('JSON','XLSX','PNG','SVG'):
        body={'format':fmt,'finalVersionIds':[args.final_id],'include':[]}
        path='/api/local/member-d/graph-exports' if fmt in ('PNG','SVG') else '/api/v1/exports'
        key=str(uuid.uuid4());r=request(path,owner,body,key)
        assert request(path,owner,body,key)==r
        records.append(r['id'])
    request('/api/v1/exports',owner,{'format':'PNG','finalVersionIds':[args.final_id],'include':[]},expected=400)
    request('/api/local/member-d/graph-exports',owner,{'format':'SVG','finalVersionIds':[args.final_id],'include':[],'svg':'<svg/>'},expected=400)
    request('/api/v1/exports/'+records[0],login('annotator.chen'),expected=403)
else:
    baseline=json.loads(args.verify.read_text(encoding='utf-8'))
    assert baseline['finalId']==args.final_id
    assert before==baseline['frozenSnapshot']
    records=[r['id'] for r in baseline['exports']]
results=[]
for record_id in records:
    path='/api/v1/exports/'+record_id
    for attempt in range(15):
        r=request(path,owner)
        if r['status'] not in ('QUEUED','RUNNING'):break
        time.sleep(1)
    assert r['status']=='COMPLETED',(record_id,r['status'],r.get('failureMessage'))
    tk=request(path+'/download-tickets',owner,{'purpose':'第六批归档与重启完整性核查'})
    events='/api/local/member-d/export-records/'+record_id+'/events'
    audit_before=request(events,owner)
    url='/api/v1/file-assets/'+tk['fileId']+'/content?'+urllib.parse.urlencode({'downloadToken':tk['downloadToken']})
    content=request(url,owner,binary=True)
    assert request(url,owner,binary=True)==content
    assert request(events,owner)==audit_before
    assert len(content)==r['file']['sizeBytes']
    assert hashlib.sha256(content).hexdigest()==r['file']['sha256']
    fmt=r['format']
    if fmt=='JSON':
        data=json.loads(content)
        assert len(data['segmentationSubmissions'])==2
        for table in ('elements','relations','participants'):assert data[table]==before[table]
        assert data['customReferences']
        assert data['validationMode']=='D_BRANCH_TRIAL'
    elif fmt=='XLSX':
        with zipfile.ZipFile(io.BytesIO(content)) as z:
            for name in z.namelist():ET.fromstring(z.read(name))
            assert b'CustomReferences' in z.read('xl/workbook.xml')
    elif fmt=='SVG':assert ET.fromstring(content).tag=='{http://www.w3.org/2000/svg}svg'
    elif fmt=='PNG':assert content.startswith(b'\x89PNG\r\n\x1a\n')
    if fmt in ('PNG','SVG'):
        meta=r['generatedMetadata'];assert meta['validationMode']=='D_BRANCH_TRIAL'
        manifest=meta['componentManifest'];node_ids=[n for c in manifest for n in c['nodeIds']]
        expected={n['id'] for n in before['elements'] if n['isProposition']}|{n['id'] for n in before['relations']}
        assert set(node_ids)==expected and len(node_ids)==len(expected)
        assert all(c['finalVersionId']==args.final_id and c['fileEntryName']==r['file']['fileName'] for c in manifest)
    (out/('batch6-live.'+fmt.lower())).write_bytes(content)
    results.append(r)
assert request(workspace,arbiter)['snapshot']==before
result={'finalId':args.final_id,'frozenSnapshot':before,'exports':results,'repeatedContentGetReadOnly':True,'sameAfterRestart':bool(args.verify)}
if args.verify:assert results==baseline['exports']
(out/('batch6-restart-verified.json' if args.verify else 'batch6-live-acceptance.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'formats':[r['format'] for r in results],'recordIds':records,'frozenUnchanged':True,'sameAfterRestart':bool(args.verify)}))
