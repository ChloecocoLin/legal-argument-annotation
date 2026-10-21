"""Loopback automation acceptance. --create makes an explicitly named test task and candidates.
All authentication tokens remain in memory. --verify only reads business data.
"""
import argparse,copy,json,pathlib,time,urllib.request,urllib.error,urllib.parse,uuid,warnings
import yaml,jsonschema
warnings.filterwarnings('ignore',category=DeprecationWarning)
p=argparse.ArgumentParser();p.add_argument('--base-url',default='http://127.0.0.1:18080');p.add_argument('--create',action='store_true');p.add_argument('--verify',type=pathlib.Path);args=p.parse_args()
assert urllib.parse.urlparse(args.base_url).hostname in ('127.0.0.1','localhost','::1')
assert args.create != bool(args.verify)
root=pathlib.Path(__file__).resolve().parents[1];contract=yaml.safe_load((root/'contracts/openapi-ready.yaml').read_text(encoding='utf-8'));validated=[]
def request(path,token=None,body=None,key=None,version=None,method=None,operation=None,expected=200):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 if key:h['Idempotency-Key']=key
 if version is not None:h['If-Match']=str(version)
 req=urllib.request.Request(args.base_url+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method)
 try:
  with urllib.request.urlopen(req,timeout=20) as response:
   envelope=json.load(response);assert response.status==expected
 except urllib.error.HTTPError as e:
  envelope=json.load(e);assert e.code==expected,(e.code,envelope.get('error'));return envelope['error']
 if operation:
  jsonschema.Draft202012Validator(contract['components']['schemas'][operation+'Success'],resolver=jsonschema.RefResolver.from_schema(contract),format_checker=jsonschema.FormatChecker()).validate(envelope);validated.append(operation)
 return envelope['data']
def login(name):return request('/api/v1/auth/login',body={'username':name,'password':'demo'})['token']
tokens={n:login(n) for n in ['creator.li','annotator.chen','annotator.liu','arbiter.zhao','admin.wang']};owner=tokens['creator.li']
def command(path,body,version=None,operation=None):return request(path,owner,body,str(uuid.uuid4()),version,operation=operation)
if args.create:
 options=request('/api/v1/integration/task-options',owner);source=options['documents'][0]
 body={'name':'第七批联验 · 自动任务测试适配器','guideVersionId':options['guides'][0]['id'],'taskType':'FORMAL_RESEARCH','defaultRequiredAnnotators':2,'autoPreannotationEnabled':True,'expertConfirmationRequired':False,'adjudicationReasonRequired':True}
 task_id=command('/api/v1/tasks',body)['id']
 case_id=command('/api/v1/tasks/'+task_id+'/documents',{k:source[k] for k in ['documentId','documentTextVersionId','annotationScopeId']}|{'requiredAnnotators':2})['id']
 for user,role in [('annotator.chen','ANNOTATOR'),('annotator.liu','ANNOTATOR'),('arbiter.zhao','ARBITER')]:
  uid=request('/api/v1/auth/me',tokens[user])['id'];command('/api/v1/tasks/'+task_id+'/members',{'userId':uid,'memberRole':role})
 setup=request('/api/v1/integration/tasks/'+task_id+'/setup',owner)
 members=setup['members'] if 'members' in setup else setup['taskMembers']
 assignments={'annotatorMemberIds':[m['id'] for m in members if m['memberRole']=='ANNOTATOR'],'arbiterMemberId':next(m['id'] for m in members if m['memberRole']=='ARBITER')}
 request('/api/v1/task-documents/'+case_id+'/assignments',owner,assignments,str(uuid.uuid4()),1,method='PUT')
 command('/api/v1/tasks/'+task_id+'/transitions',{'action':'PUBLISH'},1)
 setup=request('/api/v1/integration/tasks/'+task_id+'/setup',owner)
 command('/api/v1/tasks/'+task_id+'/transitions',{'action':'START'},setup['task']['recordVersion'])
 model={'modelId':'member-d-test-adapter','modelVersion':'1.0'};key=str(uuid.uuid4())
 request('/api/v1/task-documents/'+case_id+'/automation-tasks',owner,model,key,operation='M02')
 created=request('/api/local/member-d/task-documents/'+case_id+'/automation-tasks',owner,model,key)
 automation_id=created['id']
 request('/api/v1/automation-tasks/'+automation_id,owner,operation='M03')
 command('/api/v1/automation-tasks/'+automation_id+'/transitions',{'action':'QUEUE'},created['recordVersion'],operation='M04')
 for i in range(20):
  state=request('/api/v1/automation-tasks/'+automation_id,owner,operation='M03')
  if state['status'] not in ('QUEUED','RUNNING'):break
  time.sleep(1)
 assert state['status']=='COMPLETED',state
 request('/api/v1/automation-tasks?taskId='+task_id,owner,operation='M01')
 for user in ['admin.wang','annotator.chen','arbiter.zhao']:request('/api/v1/automation-tasks',tokens[user],expected=403)
 second=request('/api/local/member-d/task-documents/'+case_id+'/automation-tasks',owner,model,str(uuid.uuid4()))
 command('/api/v1/automation-tasks/'+second['id']+'/transitions',{'action':'CANCEL'},1,operation='M04')
 cancel_id=second['id']
else:
 baseline=json.loads(args.verify.read_text(encoding='utf-8'));task_id=baseline['taskId'];case_id=baseline['caseId'];automation_id=baseline['automationTaskId'];cancel_id=baseline['cancelledTaskId']
 request('/api/v1/automation-tasks?taskId='+task_id,owner,operation='M01')
 request('/api/v1/automation-tasks/'+automation_id,owner,operation='M03')
detail=request('/api/v1/integration/automation-tasks/'+automation_id+'/workspace',owner)
assert detail['task']['executionMode']=='TEST_ADAPTER' and detail['task']['status']=='COMPLETED'
assert len(detail['suggestions'])==1 and detail['suggestions'][0]['confidence']==0
assert detail['suggestions'][0]['status']=='PENDING_HUMAN_REVIEW' and detail['suggestions'][0]['targetDraftId'] is None
assert [e['outcome'] for e in detail['events']]==['DRAFT','QUEUED','RUNNING','COMPLETED']
assert request('/api/v1/integration/automation-tasks/'+automation_id+'/workspace',owner)==detail
setup=request('/api/v1/integration/tasks/'+task_id+'/setup',owner)
review=[]
for user in ['annotator.chen','annotator.liu']:
 assignment=request('/api/v1/integration/cases/'+case_id+'/segmentation',tokens[user])['assignment']
 review.append(request('/api/local/member-d/assignments/'+assignment['id']+'/model-suggestions',tokens[user]))
assert review[0]==review[1] and not review[0]['reviewContractReady']
assert detail['suggestions'][0]['id'] in {item['id'] for item in review[0]['items']}
assert all(item['status']=='PENDING_HUMAN_REVIEW' and item['targetDraftId'] is None for item in review[0]['items'])
progress=request('/api/v1/tasks/'+task_id+'/progress',owner)
assert all(c['submittedCount']==0 and c['requiredAnnotators']==2 for c in progress['items'])
assert request('/api/v1/automation-tasks/'+cancel_id,owner,operation='M03')['status']=='CANCELLED'
result={'taskId':task_id,'caseId':case_id,'automationTaskId':automation_id,'cancelledTaskId':cancel_id,'detail':detail,'progress':progress,'sharedCandidateReadOnly':True,'frozenSchemas':sorted(set(validated))}
if args.verify:
 for k in ['detail','progress','sharedCandidateReadOnly']:assert result[k]==baseline[k]
result['sameAfterRestart']=bool(args.verify)
(root/'backend/target'/('batch7-restart-verified.json' if args.verify else 'batch7-live-acceptance.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:result[k] for k in ['taskId','caseId','automationTaskId','cancelledTaskId','sharedCandidateReadOnly','frozenSchemas','sameAfterRestart']}))
