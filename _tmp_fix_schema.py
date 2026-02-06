import json
from pathlib import Path
p=Path('D:/Code/ReadYou/app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/21.json')
data=json.loads(p.read_text(encoding='utf-8'))
# remove top-level version if exists
if 'version' in data:
    data.pop('version')
# update database.version
if 'database' in data and isinstance(data['database'], dict):
    data['database']['version']=21
p.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8')
