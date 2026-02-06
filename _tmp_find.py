from pathlib import Path
import re
p=Path('D:/Code/ReadYou/app/src/main/res/values/strings.xml')
text=p.read_text(encoding='utf-8')
for m in re.finditer(r'<string name="add"', text):
    start=max(0, m.start()-40); end=min(len(text), m.start()+80)
    print(text[start:end].replace('\n',' '))
