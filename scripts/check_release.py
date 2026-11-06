#!/usr/bin/env python3
"""Prevent mismatches between Android versionName and the Release tag."""
import pathlib, re, sys
text = pathlib.Path('app/build.gradle').read_text()
name = re.search(r"versionName\s+['\"]([^'\"]+)", text)
code = re.search(r'versionCode\s+(\d+)', text)
if not name or not code:
    sys.exit('Cannot read versionName/versionCode from app/build.gradle')
tag = sys.argv[1]
if tag != 'v' + name.group(1):
    sys.exit(f'Tag {tag!r} does not match versionName {name.group(1)!r}')
print('Signing Android', name.group(1), '(versionCode', code.group(1) + ')')
