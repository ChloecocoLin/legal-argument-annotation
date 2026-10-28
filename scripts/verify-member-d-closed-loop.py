"""Read-only local business validation for the integrated annotation and review workflows."""
import argparse
import copy
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request
import warnings
import yaml
import jsonschema

warnings.filterwarnings('ignore', category=DeprecationWarning)
parser = argparse.ArgumentParser()
parser.add_argument('--base-url', default='http://127.0.0.1:18080')
parser.add_argument('--case-id', required=True)
parser.add_argument('--session-id', required=True)
parser.add_argument('--final-id')
parser.add_argument('--reannotation-task-id')
args = parser.parse_args()
address = urllib.parse.urlparse(args.base_url)
if address.hostname not in ('127.0.0.1', 'localhost', '::1') or address.scheme != 'http':
    raise SystemExit('Only loopback local/test servers are supported')
root = pathlib.Path(__file__).resolve().parents[1]
document = yaml.safe_load((root / 'contracts/openapi-ready.yaml').read_text(encoding='utf-8'))
for patch in json.loads((root / 'contracts/review/member-d-transport.patch.json').read_text(encoding='utf-8')):
    cursor = document
    parts = patch['path'].split('/')[1:]
    for part in parts[:-1]:
        cursor = cursor[part.replace('~1', '/').replace('~0', '~')]
    cursor[parts[-1]] = copy.deepcopy(patch['value'])
validated = []

def request(path, token=None, body=None, operation=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(args.base_url + '/api/v1' + path,
        data=None if body is None else json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=15) as response:
        result = json.load(response)
    if operation:
        jsonschema.Draft202012Validator(document['components']['schemas'][operation + 'Success'],
            resolver=jsonschema.RefResolver.from_schema(document), format_checker=jsonschema.FormatChecker()).validate(result)
        validated.append(operation)
    return result['data']

tokens = {}
try:
    for name in ('annotator.chen', 'annotator.liu', 'arbiter.zhao', 'expert.qian'):
        tokens[name] = request('/auth/login', body={'username': name, 'password': 'demo'})['token']
    drafts = []
    for name in ('annotator.chen', 'annotator.liu'):
        token = tokens[name]
        view = request('/task-documents/' + args.case_id + '/my-draft', token, operation='A01')
        draft = view['draft']
        drafts.append(draft['id'])
        request('/annotation-drafts/' + draft['id'] + '/elements', token, operation='E01')
        request('/annotation-drafts/' + draft['id'] + '/relations', token, operation='R01')
        request('/annotation-owners/DRAFT/' + draft['id'] + '/graph', token, operation='GR01')
        versions = request('/task-documents/' + args.case_id + '/annotation-versions', token, operation='V01')
        for version in versions['items']:
            request('/annotation-versions/' + version['id'], token, operation='V02')
            request('/annotation-owners/ANNOTATION_VERSION/' + version['id'] + '/graph', token, operation='GR01')
    assert drafts[0] != drafts[1]
    for token, other in zip((tokens['annotator.chen'], tokens['annotator.liu']), reversed(drafts)):
        try:
            request('/annotation-drafts/' + other + '/relations', token)
            raise AssertionError('Other annotator relation read succeeded')
        except urllib.error.HTTPError as error:
            assert error.code == 403
    token = tokens['arbiter.zhao']
    query = request('/adjudication-sessions/' + args.session_id + '/proposition-items', token, operation='J03')
    for item in query['items']:
        request('/adjudication-sessions/' + args.session_id + '/custom-context?itemKind=PROPOSITION&itemId=' + item['item']['id'], token, operation='J09')
    relation_query = request('/adjudication-sessions/' + args.session_id + '/relation-items', token, operation='J06')
    for row in relation_query['items']:
        item = row.get('item', row)
        request('/adjudication-sessions/' + args.session_id + '/custom-context?itemKind=RELATION&itemId=' + item['id'], token, operation='J09')
    expert = tokens['expert.qian']
    summaries = request('/expert-reviews?size=100', expert, operation='X01')['items']
    relevant = [row for row in summaries if row['review']['sessionId'] == args.session_id]
    assert relevant, 'No actual expert review in this session'
    for row in relevant:
        request('/expert-reviews/' + row['review']['id'], expert, operation='X02')
        try:
            request('/expert-reviews/' + row['review']['id'], tokens['annotator.chen'])
            raise AssertionError('Unassigned expert read succeeded')
        except urllib.error.HTTPError as error:
            assert error.code == 403
    if args.final_id:
        final = request('/final-annotation-versions/' + args.final_id, token, operation='FV02')
        view = request('/integration/final-annotation-versions/' + args.final_id + '/workspace', token)
        snapshot = view['snapshot']
        assert final['id'] == snapshot['version']['id'] == args.final_id
        assert snapshot['version']['status'] == 'IN_DATABASE'
        assert view['quality']['canSubmit'] is False and view['quality']['canApprove'] is False
        assert len(view['graphView']['nodes']) == sum(bool(e['isProposition']) for e in snapshot['elements']) + len(snapshot['relations'])
        for name in ('annotator.chen', 'annotator.liu', 'expert.qian'):
            try:
                request('/integration/final-annotation-versions/' + args.final_id + '/workspace', tokens[name])
                raise AssertionError('Unassigned final workspace read succeeded')
            except urllib.error.HTTPError as error:
                assert error.code == 403
        creator = request('/auth/login', body={'username': 'creator.li', 'password': 'demo'})['token']
        # Local trial routes intentionally live outside /api/v1.
        def local_read(path, credential):
            req = urllib.request.Request(args.base_url + path, headers={'Authorization': 'Bearer ' + credential})
            with urllib.request.urlopen(req, timeout=15) as response:
                return json.load(response)['data']
        detail = local_read('/api/local/member-d/results/' + args.final_id, creator)
        assert sum(row['count'] for row in detail['labelDistribution']) == len(snapshot['elements'])
        assert sum(row['count'] for row in detail['relationDistribution']) == len(snapshot['relations'])
        assert detail['agreement']['reproducible'] is True
        events = request('/audit-events?objectId=' + args.final_id, creator)['items']
        assert len(events) == 1 and events[0]['operationCode'] == 'TRIAL_FV06'
        assert events[0]['validationMode'] == 'D_BRANCH_TRIAL'
        subject = copy.deepcopy(snapshot)
        subject.pop('decisionDigest', None)
        for field in ('status','recordVersion','updatedAt','updatedById','approvedAt','approvedById','committedAt','correctionSessionId'):
            subject['version'].pop(field, None)
        import hashlib
        digest = hashlib.sha256(json.dumps(subject, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
        assert events[0]['contentDigest'] == digest
        if args.reannotation_task_id:
            created = request('/tasks/' + args.reannotation_task_id, creator)
            assert created['status'] == 'DRAFT'
            source = request('/tasks/' + detail['sourceTaskId'], creator)
            assert source['status'] == 'COMPLETED'
            setup = request('/integration/tasks/' + args.reannotation_task_id + '/setup', creator)
            assert setup['task']['sourceTaskId'] == detail['sourceTaskId']
            assert setup['task']['sourceFinalVersionId'] == args.final_id
            assert len(setup['taskDocuments']) == 1
            case = setup['taskDocuments'][0]
            assert case['id'] != args.case_id
            assert case['documentTextVersionId'] == snapshot['version']['documentTextVersionId']
            assert case['annotationScopeId'] == snapshot['version']['annotationScopeId']
        print('Committed result, permanent trial audit, frozen content digest and independent reannotation scope passed.')
    print('Frozen schemas and blind scopes passed: ' + ', '.join(validated) + ' (V02 authorized review overlay only)')
finally:
    for token in tokens.values():
        request('/auth/logout', token, {})
