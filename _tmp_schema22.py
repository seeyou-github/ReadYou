import json
from pathlib import Path
src=Path('D:/Code/ReadYou/app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/21.json')
data=json.loads(src.read_text(encoding='utf-8'))
# update version
if 'database' in data:
    data['database']['version']=22
# update plugin_rule columns
for table in data.get('database',{}).get('entities',[]):
    if table.get('tableName')=='plugin_rule':
        cols=table['fields']
        existing={c['columnName'] for c in cols}
        def add_col(name, affinity='TEXT', notNull=True, defaultValue="''"):
            cols.append({'fieldPath': name, 'columnName': name, 'affinity': affinity, 'notNull': notNull, 'defaultValue': defaultValue})
        if 'detailExcludeSelector' not in existing:
            add_col('detailExcludeSelector')
        # update createSql
        create=table.get('createSql','')
        if 'detailExcludeSelector' not in create:
            create=create.replace('detailContentSelectors TEXT NOT NULL DEFAULT \'\',', "detailContentSelectors TEXT NOT NULL DEFAULT '', detailExcludeSelector TEXT NOT NULL DEFAULT '',")
        table['createSql']=create
        break
out=Path('D:/Code/ReadYou/app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/22.json')
out.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8')
