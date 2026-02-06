import requests
from bs4 import BeautifulSoup
url='https://www.zaobao.com/news/china/story20260205-8312221'
html=requests.get(url, timeout=30).text
soup=BeautifulSoup(html,'html.parser')
article=soup.select_one('article.article-body')
imgs=article.select('img') if article else []
print('imgs', len(imgs))
for img in imgs[:5]:
    print(img.get('src'))
