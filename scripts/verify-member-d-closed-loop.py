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
    print('Frozen schemas and blind scopes passed: ' + ', '.join(validated) + ' (V02 authorized review overlay only)')
finally:
    for token in tokens.values():
        request('/auth/logout', token, {})
