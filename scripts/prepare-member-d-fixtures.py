"""Derive an isolated pre-submission scenario. Never edit the team's shared Mock files."""
import json
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[1]
source = pathlib.Path(sys.argv[1])
mapping = {
    'base/users.json': 'user', 'base/roles.json': 'role', 'base/tasks.json': 'task',
    'base/task-documents.json': 'taskDocument', 'base/task-assignments.json': 'assignment',
    'base/task-members.json': 'member', 'base/documents.json': 'document',
    'base/document-text-versions.json': 'text', 'base/annotation-scopes.json': 'scope',
    'base/guide-versions.json': 'guideVersion', 'base/label-definitions.json': 'label',
    'base/relation-definitions.json': 'relationDefinition',
    'core/segmentation-checkpoints.json': 'checkpoint',
    'core/annotation-drafts.json': 'draft', 'core/elements.json': 'element',
    'core/relations.json': 'relation', 'core/relation-participants.json': 'participant',
    'core/graphs.json': 'graph', 'core/graph-layouts.json': 'layout',
}
records = []
for file, kind in mapping.items():
    for value in json.loads((source / file).read_text(encoding='utf-8')):
        if kind == 'draft':
            value.update(status='ACTIVE', phase='GRAPH')
        if kind in ('element', 'relation', 'graph') and value['ownerType'] != 'DRAFT':
            continue
        records.append({'kind': kind, 'value': value})
relation_ids = {r['value']['id'] for r in records if r['kind'] == 'relation'}
graph_ids = {r['value']['id'] for r in records if r['kind'] == 'graph'}
records = [r for r in records if r['kind'] != 'participant' or r['value']['relationId'] in relation_ids]
records = [r for r in records if r['kind'] != 'layout' or r['value']['graphId'] in graph_ids]
output = root / 'backend/src/main/resources/fixtures/member-d-ready-submission.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'scenario': 'ready-submission', 'contractStatus': 'LOCAL_TEST_FIXTURE',
    'limitations': ['Confirmed segmentation is preloaded; S04 is not implemented.',
        'A/B/C task, draft and graph data use a local dependency adapter.',
        'Drafts are isolated ACTIVE pre-submission projections; no adjudication or final records are loaded.'],
    'records': records}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Prepared {len(records)} local records')
