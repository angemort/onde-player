#!/usr/bin/env python3
"""Write update.json on main only after a signed GitHub Release succeeded."""
import base64, json, os, pathlib, re, urllib.error, urllib.request
source = pathlib.Path('app/build.gradle').read_text()
version_code = int(re.search(r'versionCode\s+(\d+)', source).group(1))
version_name = re.search(r"versionName\s+['\"]([^'\"]+)", source).group(1)
repo, tag = os.environ['GH_REPO'], os.environ['RELEASE_TAG']
url = f'https://github.com/{repo}/releases/download/{tag}/OndePlayer-{version_name}.apk'
payload = {"versionCode": version_code, "versionName": version_name,
           "notes": f'Onde Player {version_name} : voir la publication GitHub pour les nouveautés.',
           "apkUrl": url}
api = f'https://api.github.com/repos/{repo}/contents/update.json'
token = os.environ['GH_TOKEN']
headers = {'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json',
           'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'OndePlayer-release'}
req = urllib.request.Request(api+'?ref=main', headers=headers)
try:
    with urllib.request.urlopen(req, timeout=15) as response:
        existing_sha = json.load(response)['sha']
except urllib.error.HTTPError as exc:
    if exc.code != 404: raise
    existing_sha = None
body = {'message': f'Publish update manifest for {tag}', 'branch': 'main',
        'content': base64.b64encode((json.dumps(payload, ensure_ascii=False, indent=2)+'\n').encode()).decode()}
if existing_sha: body['sha'] = existing_sha
req = urllib.request.Request(api, data=json.dumps(body).encode(), headers=headers, method='PUT')
with urllib.request.urlopen(req, timeout=20) as response:
    print('Published update.json to main:', response.status)
