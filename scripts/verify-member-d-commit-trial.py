"""Read-only live boundaries + positive server-test artifacts; no live approval or commit bypass."""
import argparse,hashlib,json,pathlib,urllib.request,urllib.error,urllib.parse,warnings
import yaml,jsonschema
warnings.filterwarnings('ignore',category=DeprecationWarning)
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:18080');p.add_argument('--final-id',required=True);a=p.parse_args()
if urllib.parse.urlparse(a.base_url).hostname not in ('127.0.0.1','localhost','::1'):raise SystemExit('Loopback development server only')
root=pathlib.Path(__file__).resolve().parents[1];frozen=root/'contracts/openapi-ready.yaml'
assert hashlib.sha256(frozen.read_bytes()).hexdigest()=='813539491a53c0151c44bd426985f74a1df4d5ad35a7df1955d20c9cae13b390'
doc=yaml.safe_load(frozen.read_text(encoding='utf-8'))
def read(token,path):
    req=urllib.request.Request(a.base_url+path,headers={'Authorization':'Bearer '+token})
    try:
        with urllib.request.urlopen(req,timeout=10) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
def error(r):jsonschema.Draft202012Validator(doc['components']['schemas']['ErrorResponse'],resolver=jsonschema.RefResolver.from_schema(doc),format_checker=jsonschema.FormatChecker()).validate(r)
for token in ['dev-admin-wang','dev-creator-li','dev-arbiter-zhao']:
    code,r=read(token,'/api/v1/audit-events?page=1&size=10');assert code==200
    assert set(r)=={'success','data','meta','error','requestId','timestamp'}
    assert r['data']['total']==r['meta']['total'] and len(r['data']['items'])<=10
    schema=json.loads((root/'contracts/review/member-d-audit-trial.schema.json').read_text(encoding='utf-8'))
    for event in r['data']['items']:jsonschema.Draft202012Validator(schema,format_checker=jsonschema.FormatChecker()).validate(event)
    print('Trial L01 authorization/page passed:',token,r['meta']['total'])
for token in ['dev-annotator-chen','dev-expert-qian']:
    code,r=read(token,'/api/v1/audit-events');assert code==403;error(r)
for path in ['/api/v1/provenance/FinalAnnotationVersion/'+a.final_id,'/api/v1/history/FinalAnnotationVersion/'+a.final_id+'?page=1&size=2']:
    code,r=read('dev-arbiter-zhao',path);assert code==200 and r['success']
    if '/history/' in path:assert len(r['data']['items'])<=2 and r['meta']['total']>=len(r['data']['items'])
    else:assert set(r['data'])=={'sourceObjectIds','decisionIds'}
    code,r=read('dev-admin-wang',path);assert code==403;error(r)
artifact=root/'backend/target/member-d-commit-trial-response.json'
event_path=root/'backend/target/member-d-audit-trial-event.json'
assert artifact.exists() and event_path.exists(),'Run CommitAuditTrialWorkflowTest first'
response=json.loads(artifact.read_text(encoding='utf-8'));event=json.loads(event_path.read_text(encoding='utf-8'))
assert set(response['data'])=={'status','committedAt'} and response['data']['status']=='IN_DATABASE'
assert response['requestId']==event['requestId']
jsonschema.Draft202012Validator(schema,format_checker=jsonschema.FormatChecker()).validate(event)
assert event['beforeState']['finalVersion']['status']=='APPROVED' and event['afterState']['finalVersion']['status']=='IN_DATABASE'
print('Positive FV06/audit artifact passed with a server-side certificate test stub; this is not a live formal approval')
print('All live requests were GET; frozen input unchanged')
