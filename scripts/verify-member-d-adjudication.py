"""Read-only HTTP validation of adjudication responses against frozen input + proposed review overlay."""
import argparse,copy,json,pathlib,urllib.request,urllib.parse,warnings
import yaml,jsonschema
warnings.filterwarnings('ignore',category=DeprecationWarning)
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:18080');p.add_argument('--session-id',required=True);a=p.parse_args()
if urllib.parse.urlparse(a.base_url).hostname not in ('127.0.0.1','localhost','::1'):raise SystemExit('Explicit loopback server only')
root=pathlib.Path(__file__).resolve().parents[1]
d=yaml.safe_load((root/'contracts/openapi-ready.yaml').read_text(encoding='utf-8'))
for patch in json.loads((root/'contracts/review/member-d-adjudication-transport.patch.json').read_text(encoding='utf-8')):
    cursor=d;keys=patch['path'].split('/')[1:]
    for key in keys[:-1]:cursor=cursor[key.replace('~1','/').replace('~0','~')]
    cursor[keys[-1]]=copy.deepcopy(patch['value'])
def read(path):
    req=urllib.request.Request(a.base_url+path,headers={'Authorization':'Bearer dev-arbiter-zhao'})
    with urllib.request.urlopen(req,timeout=10) as response:return json.load(response)
def check(op,response):
    jsonschema.Draft202012Validator(d['components']['schemas'][op+'Success'],resolver=jsonschema.RefResolver.from_schema(d),format_checker=jsonschema.FormatChecker()).validate(response)
    print(op+' passed (proposed overlay, shared input unchanged)')
s=read('/api/v1/adjudication-sessions/'+a.session_id);check('J02',s)
check('V03',read('/api/v1/task-documents/'+s['data']['taskDocumentId']+'/annotation-diff'))
# Confirm local persisted entities have complete session/item/decision metadata.
w=read('/api/local/member-d/adjudication/sessions/'+a.session_id)['data']
for field,schema in [('propositionItems','PropositionAdjudicationItem'),('relationItems','RelationAdjudicationItem'),('decisions','AdjudicationDecision')]:
    for resource in w[field]:jsonschema.Draft202012Validator(d['components']['schemas'][schema],resolver=jsonschema.RefResolver.from_schema(d),format_checker=jsonschema.FormatChecker()).validate(resource)
print('Complete persisted items/decisions passed')
