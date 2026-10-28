"""Read-only U08 frozen-schema and local export boundary verification."""
import argparse, hashlib, json, pathlib, urllib.error, urllib.parse, urllib.request, warnings
import yaml, jsonschema
warnings.filterwarnings('ignore', category=DeprecationWarning)
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:18080');a=p.parse_args()
if urllib.parse.urlparse(a.base_url).hostname not in ('127.0.0.1','localhost','::1'):raise SystemExit('Loopback development server only')
root=pathlib.Path(__file__).resolve().parents[1];path=root/'contracts/openapi-ready.yaml'
assert hashlib.sha256(path.read_bytes()).hexdigest()=='813539491a53c0151c44bd426985f74a1df4d5ad35a7df1955d20c9cae13b390'
doc=yaml.safe_load(path.read_text(encoding='utf-8'))
def request(token,path):
    headers={'Authorization':'Bearer '+token} if token else {}
    try:
        with urllib.request.urlopen(urllib.request.Request(a.base_url+path,headers=headers),timeout=10) as r:return r.status,json.load(r)
    except urllib.error.HTTPError as e:return e.code,json.load(e)
for token in ['dev-admin-wang','dev-creator-li']:
    code,r=request(token,'/api/v1/dashboard/summary');assert code==200
    jsonschema.Draft202012Validator(doc['components']['schemas']['U08Success'],resolver=jsonschema.RefResolver.from_schema(doc),format_checker=jsonschema.FormatChecker()).validate(r)
    assert all(isinstance(v,int) and v>=0 for v in r['data'].values())
    print('U08 frozen schema passed:',token,r['data'])
    code,preview=request(token,'/api/local/member-d/export-preview');assert code==200
    assert preview['data']['exportAvailable'] is False
    assert all(x['status']=='IN_DATABASE' and len(x['preview'])<=5 for x in preview['data']['items'])
    print('Read-only local export preview passed:',len(preview['data']['items']),'eligible versions')
for token,expected in [(None,401),('dev-annotator-chen',403),('dev-arbiter-zhao',403)]:
    code,r=request(token,'/api/v1/dashboard/summary');assert code==expected
    jsonschema.Draft202012Validator(doc['components']['schemas']['ErrorResponse'],resolver=jsonschema.RefResolver.from_schema(doc),format_checker=jsonschema.FormatChecker()).validate(r)
    code,_=request(token,'/api/local/member-d/export-preview');assert code==expected
assert request('dev-admin-wang','/api/v1/dashboard/summary?scope=all')[0]==400
print('Authentication, role boundaries and undefined scope checks passed; no mutation requested')
