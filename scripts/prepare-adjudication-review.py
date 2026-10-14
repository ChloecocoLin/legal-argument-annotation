import json, yaml
from pathlib import Path
root=Path('.')
d=yaml.safe_load((root/'contracts/openapi-ready.yaml').read_text(encoding='utf-8'))
patch=[]
for name in ['J01Success','J02Success']:
    patch.append({'op':'replace','path':f'/components/schemas/{name}/properties/data','value':{'$ref':'#/components/schemas/AdjudicationSession'}})
patch.append({'op':'replace','path':'/components/schemas/J06Success/properties/data/properties/items/items','value':{'$ref':'#/components/schemas/RelationAdjudicationItem'}})
(root/'contracts/review/member-d-adjudication-transport.patch.json').write_text(json.dumps(patch,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
