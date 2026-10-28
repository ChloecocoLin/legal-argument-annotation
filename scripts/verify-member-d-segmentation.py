"""Read business data from a loopback local/test server; only creates/revokes login sessions.
S02 is checked with the explicit pending-review nullable reviewedAt overlay.
"""
import argparse
import json
import pathlib
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
for patch in json.loads((root / 'contracts/review/member-d-segmentation-read.patch.json').read_text(encoding='utf-8')):
    cursor = document
    parts = patch['path'].split('/')[1:]
    for part in parts[:-1]:
        cursor = cursor[part]
    cursor[parts[-1]] = patch['value']

def request(path, token=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(args.base_url + '/api/v1' + path,
                                 data=None if body is None else json.dumps(body).encode(), headers=headers)
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)

sessions = []
validated = []
try:
    for name in ('annotator.chen', 'annotator.liu', 'arbiter.zhao'):
        token = request('/auth/login', body={'username': name, 'password': 'demo'})['data']['token']
        sessions.append(token)
        response = request('/task-documents/' + args.case_id + '/segmentation-submissions', token)
        jsonschema.Draft202012Validator(document['components']['schemas']['S02Success'],
            resolver=jsonschema.RefResolver.from_schema(document), format_checker=jsonschema.FormatChecker()).validate(response)
        projection = request('/integration/cases/' + args.case_id + '/segmentation', token)['data']
        if name != 'arbiter.zhao':
            assignment = projection['assignment']['id']
            assert all(row['assignmentId'] == assignment for row in projection['submissions']), 'Blind scope leak'
        validated.append(name + ':S02')
        if name == 'arbiter.zhao' and projection['checkpoints']:
            stats = request('/task-documents/' + args.case_id + '/segmentation-agreement', token)
            jsonschema.Draft202012Validator(json.loads((root / 'contracts/review/member-d-segmentation-statistics.schema.json').read_text(encoding='utf-8')),
                resolver=jsonschema.RefResolver.from_schema(document), format_checker=jsonschema.FormatChecker()).validate(stats)
            validated.append('S05:pending-model')
    print('Schema and blind-scope validation passed: ' + ', '.join(validated)
          + ' (S02 pending-review overlay; frozen input unchanged)')
finally:
    for token in sessions:
        request('/auth/logout', token, {})
