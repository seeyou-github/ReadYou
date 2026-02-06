import json, pathlib
src=pathlib.Path('D:/Code/ReadYou/app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/20.json')
data=json.loads(src.read_text(encoding='utf-8'))
data['version']=21
# update plugin_rule columns
for table in data.get('database',{}).get('entities',[]):
    if table.get('tableName')=='plugin_rule':
        cols=table['fields']
        existing={c['columnName'] for c in cols}
        def add_col(name, affinity='TEXT', notNull=True, defaultValue="''"):
            cols.append({
                'fieldPath': name,
                'columnName': name,
                'affinity': affinity,
                'notNull': notNull,
                'defaultValue': defaultValue,
            })
        if 'icon' not in existing:
            add_col('icon')
        if 'listHtmlCache' not in existing:
            add_col('listHtmlCache')
        if 'detailContentSelectors' not in existing:
            add_col('detailContentSelectors')
        break
# update createSql
for table in data.get('database',{}).get('entities',[]):
    if table.get('tableName')=='plugin_rule':
        create=table.get('createSql','')
        if 'icon TEXT NOT NULL DEFAULT' not in create:
            create=create.replace('subscribeUrl TEXT NOT NULL', "subscribeUrl TEXT NOT NULL, icon TEXT NOT NULL DEFAULT '', listHtmlCache TEXT NOT NULL DEFAULT ''")
        if 'detailContentSelectors' not in create:
            create=create.replace('detailContentSelector TEXT NOT NULL,', "detailContentSelector TEXT NOT NULL, detailContentSelectors TEXT NOT NULL DEFAULT '',")
        table['createSql']=create
        break
out=pathlib.Path('D:/Code/ReadYou/app/schemas/me.ash.reader.infrastructure.db.AndroidDatabase/21.json')
out.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8')
