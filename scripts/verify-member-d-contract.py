"""Validate live D responses against frozen schemas plus the authorized REVIEW transport patch.
Read-only unless --mutate-fixture is supplied; mutation is restricted to loopback fixture servers.
"""
import argparse
import copy
import json
import pathlib
import urllib.error
import urllib.parse
import urllib.request
import uuid
import warnings
import yaml
import jsonschema

warnings.filterwarnings('ignore', category=DeprecationWarning)
root = pathlib.Path(__file__).resolve().parents[1]
args_parser = argparse.ArgumentParser()
args_parser.add_argument('--base-url', default='http://127.0.0.1:18080')
args_parser.add_argument('--mutate-fixture', action='store_true')
args = args_parser.parse_args()
url = urllib.parse.urlparse(args.base_url)
if url.hostname not in ('127.0.0.1', 'localhost', '::1') or url.scheme != 'http':
    raise SystemExit('Only an explicit loopback development server is supported')
document = yaml.safe_load((root / 'contracts/openapi-ready.yaml').read_text(encoding='utf-8'))
for patch in json.loads((root / 'contracts/review/member-d-transport.patch.json').read_text(encoding='utf-8')):
    cursor = document
    path = patch['path'].split('/')[1:]
    for key in path[:-1]:
        cursor = cursor[key.replace('~1', '/').replace('~0', '~')]
    cursor[path[-1]] = copy.deepcopy(patch['value'])
validated = []

def request(path, token='dev-annotator-chen', body=None, version=None):
    headers = {'Authorization': 'Bearer ' + token}
    if body is not None:
        headers.update({'Content-Type': 'application/json', 'Idempotency-Key': str(uuid.uuid4()), 'If-Match': str(version)})
    req = urllib.request.Request(args.base_url + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=10) as response:
        return json.load(response)

def validate(operation, response):
    schema = document['components']['schemas'][operation + 'Success']
    jsonschema.Draft202012Validator(schema, resolver=jsonschema.RefResolver.from_schema(document), format_checker=jsonschema.FormatChecker()).validate(response)
    validated.append(operation)

task = 'f102c523-b4ed-4323-81d3-0c564c4141a0'
case = '3cbb140f-0274-4871-8248-194271101a14'
validate('T13', request('/api/v1/tasks/' + task + '/progress'))
validate('V01', request('/api/v1/task-documents/' + case + '/annotation-versions'))
context = request('/api/local/member-d/context')
if args.mutate_fixture:
    if context['data'].get('fixture') is not True:
        raise SystemExit('Refusing mutation outside a fixture context')
    drafts = [d for d in context['data']['drafts'] if d['status'] == 'ACTIVE']
    if not drafts:
        raise SystemExit('No ACTIVE fixture draft; use the UI to withdraw its test version first')
    draft = drafts[0]
    submitted = request('/api/v1/annotation-drafts/' + draft['id'] + '/submit', body={'confirmed': True, 'note': '接口 Schema 验收'}, version=draft['recordVersion'])
    validate('A04', submitted)
    validate('V02', request('/api/v1/annotation-versions/' + submitted['data']['id']))
    validate('A05', request('/api/v1/annotation-versions/' + submitted['data']['id'] + '/withdraw', body={'reason': 'Schema 验收后恢复本地草稿'}, version=submitted['data']['recordVersion']))
else:
    versions = request('/api/v1/task-documents/' + case + '/annotation-versions')['data']['items']
    for version in versions:
        validate('V02', request('/api/v1/annotation-versions/' + version['id']))
print('Schema validation passed: ' + ', '.join(validated) + ' (authorized REVIEW overlay; shared input unchanged)')
