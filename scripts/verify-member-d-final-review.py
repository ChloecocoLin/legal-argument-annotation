"""Strict frozen FV schema checks. Read-only by default; mutation only on explicit local fixture."""
import argparse,hashlib,json,pathlib,urllib.request,urllib.error,urllib.parse,uuid,warnings
import yaml,jsonschema
warnings.filterwarnings('ignore',category=DeprecationWarning)
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:18080');p.add_argument('--final-id',required=True);p.add_argument('--mutate-fixture',action='store_true');a=p.parse_args()
if urllib.parse.urlparse(a.base_url).hostname not in ('127.0.0.1','localhost','::1'):raise SystemExit('Explicit loopback development server only')
root=pathlib.Path(__file__).resolve().parents[1];document=yaml.safe_load((root/'contracts/openapi-ready.yaml').read_text(encoding='utf-8'))
def request(path,body=None,version=1,key=None):
 headers={'Authorization':'Bearer dev-arbiter-zhao'}
 if body is not None:headers.update({'Content-Type':'application/json','Idempotency-Key':key or str(uuid.uuid4()),'If-Match':str(version)})
 req=urllib.request.Request(a.base_url+path,headers=headers,data=None if body is None else json.dumps(body).encode())
 try:
  with urllib.request.urlopen(req,timeout=10) as response:return response.status,json.load(response)
 except urllib.error.HTTPError as error:return error.code,json.load(error)
def validate(operation,response):
 jsonschema.Draft202012Validator(document['components']['schemas'][operation+'Success'],resolver=jsonschema.RefResolver.from_schema(document),format_checker=jsonschema.FormatChecker()).validate(response)
 print(operation+' passed (unchanged frozen schema)')
def workspace():
 code,r=request('/api/local/member-d/final-review/versions/'+a.final_id)
 if code!=200:raise SystemExit('Cannot read the explicit local fixture workspace')
 return r['data']
def fingerprint(snapshot):
 data={k:snapshot[k] for k in ['elements','relations','participants','graph','layouts']};data['provenance']=snapshot['version']['provenance']
 return hashlib.sha256(json.dumps(data,sort_keys=True).encode()).hexdigest()
code,r=request('/api/v1/final-annotation-versions/'+a.final_id);assert code==200;validate('FV02',r)
w=workspace();before=fingerprint(w['snapshot'])
if a.mutate_fixture:
 assert w.get('fixture') is True and w['quality']['mode']=='LOCAL_PREVIEW' and w['latest']
 v=w['snapshot']['version'];base='/api/v1/final-annotation-versions/'+a.final_id
 code,created=request('/api/v1/adjudication-sessions/'+v['sessionId']+'/final-versions',{'versionNumber':v['versionNumber']});assert code==200;validate('FV01',created)
 if v['status']=='DRAFT':
  key=str(uuid.uuid4());code,s=request(base+'/review',{'action':'SUBMIT'},v['recordVersion'],key);assert code==200;validate('FV03',s)
  code,retry=request(base+'/review',{'action':'SUBMIT'},v['recordVersion'],key);assert code==200 and retry['data']==s['data']
  w=workspace();v=w['snapshot']['version']
 assert v['status']=='IN_REVIEW'
 code,denied=request(base+'/approve',{'confirmed':True},v['recordVersion']);assert code==409 and denied['error']['code']=='SOURCE_INCOMPLETE'
 jsonschema.Draft202012Validator(document['components']['schemas']['ErrorResponse'],resolver=jsonschema.RefResolver.from_schema(document)).validate(denied)
 print('FV05 live gate passed: local preview approval rejected')
 code,returned=request(base+'/return',{'reason':'Schema 验收：保留本轮退回与来源'},v['recordVersion']);assert code==200;validate('FV04',returned)
 assert fingerprint(workspace()['snapshot'])==before
 print('Immutable snapshot preserved across review/return')
artifact=root/'backend/target/final-review-approved-response.json'
if artifact.exists():
 validate('FV05',json.loads(artifact.read_text(encoding='utf-8')))
 print('FV05 positive response came from a state-machine test with server-side evidence stub, not a live formal validator')
print('Final snapshot ID:',a.final_id)
