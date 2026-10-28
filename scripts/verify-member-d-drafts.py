"""Read-only business validation against loopback local/test; login sessions only are mutated."""
import argparse
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
args = parser.parse_args()
address = urllib.parse.urlparse(args.base_url)
if address.hostname not in ('127.0.0.1', 'localhost', '::1') or address.scheme != 'http':
    raise SystemExit('Only loopback local/test servers are supported')
root = pathlib.Path(__file__).resolve().parents[1]
document = yaml.safe_load((root / 'contracts/openapi-ready.yaml').read_text(encoding='utf-8'))

def request(path, token=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(args.base_url + '/api/v1' + path,
        data=None if body is None else json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)

def validate(schema, response):
    jsonschema.Draft202012Validator(document['components']['schemas'][schema],
        resolver=jsonschema.RefResolver.from_schema(document), format_checker=jsonschema.FormatChecker()).validate(response)

sessions = []
drafts = []
try:
    for name in ('annotator.chen', 'annotator.liu'):
        token = request('/auth/login', body={'username': name, 'password': 'demo'})['data']['token']
        sessions.append(token)
        response = request('/task-documents/' + args.case_id + '/my-draft', token)
        validate('A01Success', response)
        draft = response['data']['draft']
        drafts.append(draft['id'])
        elements = request('/annotation-drafts/' + draft['id'] + '/elements', token)
        validate('E01Success', elements)
        assert all(e['ownerType'] == 'DRAFT' and e['ownerId'] == draft['id'] for e in elements['data']['items'])
        assert set(draft['elementIds']) == {e['id'] for e in elements['data']['items']}
    assert drafts[0] != drafts[1], 'Different users share a draft'
    for token, other in zip(sessions, reversed(drafts)):
        try:
            request('/annotation-drafts/' + other + '/elements', token)
            raise AssertionError('Blind scope leak')
        except urllib.error.HTTPError as error:
            assert error.code == 403
    print('A01/E01 frozen schemas, ownership and two-account blind scope passed')
finally:
    for token in sessions:
        request('/auth/logout', token, {})
