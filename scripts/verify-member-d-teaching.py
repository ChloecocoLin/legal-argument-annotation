"""Local teaching acceptance; tokens stay in memory. --verify reads saved business records."""
import argparse,json,pathlib,urllib.request,urllib.error,uuid,datetime
p=argparse.ArgumentParser();p.add_argument('--verify',action='store_true');args=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];file=root/'backend/target/batch8-live-acceptance.json'
base='http://127.0.0.1:18080';prefix='/api/local/member-d/teaching'
def req(path,token=None,body=None,version=None,method=None,expected=200):
 h={'Content-Type':'application/json'}
 if token:h['Authorization']='Bearer '+token
 if body is not None:h['Idempotency-Key']=str(uuid.uuid4())
 if version is not None:h['If-Match']=str(version)
 try:
  with urllib.request.urlopen(urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=h,method=method),timeout=30) as r:return json.load(r)['data']
 except urllib.error.HTTPError as e:
  error=json.load(e);assert e.code==expected,(e.code,error);return error

def login(name):return req('/api/v1/auth/login',body={'username':name,'password':'demo'})['token']
c,t,s,a=[login(n) for n in ['creator.li','teacher.sun','student.zhou','admin.wang']]
def read(path,who=s):return req(prefix+path,who)
def cmd(path,body,who=t,version=None,method=None):return req(prefix+path,who,body,version,method)
def date(delta):return (datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(seconds=delta)).strftime('%Y-%m-%dT%H:%M:%SZ')
if not args.verify:
 teacher=req('/api/v1/auth/me',t)['id'];student=req('/api/v1/auth/me',s)['id']
 existing=read('/workspace',c)['sourceGrants'];grant=next((g for g in existing if g.get('sourceFinalVersionId')=='552259f0-374a-472f-9927-2697c7ad9371' and g.get('teacherUserId')==teacher and g.get('status')=='ACTIVE'),None)
 if grant is None:grant=cmd('/source-grants',{'finalVersionId':'552259f0-374a-472f-9927-2697c7ad9371','teacherUserId':teacher},c)
 course=cmd('/courses',{'name':'第八批教学闭环验收','className':'教学联验班','studentUserIds':[student]});cmd('/courses/'+course['id']+'/activate',{'confirmed':True},version=1)
 std=cmd('/standards',{'sourceGrantId':grant['id'],'name':'教学标准 F1'});cmd('/standards/'+std['id']+'/publish',{'reason':'核查独立教学标准与原文'},version=1)
 def assignment(name,mode,open_delta):
  n=cmd('/assignments',{'name':name,'courseId':course['id'],'standardId':std['id'],'exerciseMode':mode,'exerciseType':'GRAPH_COMPARE','dueAt':date(172800)})
  cmd('/assignments/'+n['id']+'/publish',{'maxAttempts':2,'answerOpensAt':date(open_delta)},version=1);return n['id']
 held_id=assignment('答案尚未开放 · 关系练习','RELATION_GUIDED',86400);open_id=assignment('答案已开放 · 关系练习','RELATION_GUIDED',-60);full_id=assignment('完整案例 · 评分待确认','FULL_CASE',-60)
 def complete(aid,mode,code='S'):
  ctx=read('/assignments/'+aid+'/context');assert 'standardId' not in json.dumps(ctx) and ctx['draft'] is None
  n=cmd('/assignments/'+aid+'/attempts',{'reason':'联验主动开始作答'},s)
  material=ctx['material'];elements=[];relations=[]
  if mode=='FULL_CASE':
   assert 'givenElements' not in material
   elements=[{'clientKey':str(uuid.uuid4()),'startOffset':material['offsetBase'],'endOffset':material['offsetBase']+len(material['scopeText'].encode('utf-16-le'))//2,'labelCode':'GM-L'}]
  else:
   given=material['givenElements'];assert len(given)==2
   relations=[{'ref':str(uuid.uuid4()),'code':code,'participants':[{'role':role,'targetKind':'ELEMENT','targetId':e['id'],'targetRef':None} for role,e in zip(['SOURCE','TARGET'],given)]}]
  saved=cmd('/answers/'+n['id'],{'elements':elements,'relations':relations},s,1,'PATCH');done=cmd('/answers/'+n['id']+'/submit',{'confirmed':True},s,saved['recordVersion']);return done['resultId']
 held=complete(held_id,'RELATION_GUIDED');opened=complete(open_id,'RELATION_GUIDED');wrong=complete(open_id,'RELATION_GUIDED','A');full=complete(full_id,'FULL_CASE')
 cmd('/results/'+held+'/feedback',{'comment':'答案开放后核查支持方向','itemFeedback':[]});cmd('/results/'+opened+'/feedback',{'comment':'本次支持关系方向正确','itemFeedback':[]})
 req(prefix+'/assignments/'+open_id+'/attempts',s,{'reason':'超过次数'},expected=409)
 revision=cmd('/standards/'+std['id']+'/revisions',{'sourceGrantId':grant['id'],'reason':'验证新标准不覆盖旧作业'});cmd('/standards/'+revision['id']+'/publish',{'reason':'确认新版本'},version=1)
 ids={'courseId':course['id'],'standardId':std['id'],'revisionId':revision['id'],'assignmentIds':[held_id,open_id,full_id],'resultIds':[held,opened,wrong,full]}
else:ids=json.loads(file.read_text(encoding='utf-8'))['ids']
results=[read('/results/'+i) for i in ids['resultIds']];teacher_results=[read('/results/'+i,t) for i in ids['resultIds']]
held,opened,wrong,full=results
assert held['scoreAvailability']=='HELD' and not any(k in held for k in ['grading','standardSnapshot','teacherFeedback','standardDigest'])
assert opened['scoringRuleVersion']==opened['grading']['algorithmVersion']=='C-TeachingScorer-ab6f03d+D-snapshot-v2'
assert opened['grading']['relationSpecialistScore']==100 and wrong['grading']['relationSpecialistScore']<100
assert full['grading']['scoreStatus']=='PENDING_BOUNDARY_AND_ELEMENT_RULES'
assert all(r['totalScore'] is None for r in results)
assert opened['standardVersionId']==ids['standardId'] and len(opened['teacherFeedback'])==1
req(prefix+'/results/'+ids['resultIds'][0],a,expected=403);req(prefix+'/standards/'+ids['standardId'],s,expected=403)
standards=[read('/standards/'+i,t) for i in [ids['standardId'],ids['revisionId']]]
out={'ids':ids,'results':results,'teacherResults':teacher_results,'standards':standards}
if args.verify:
 assert out==json.loads(file.read_text(encoding='utf-8'));print('Teaching persistence and authorization verified')
else:file.write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(ids))
