import os
import hashlib
import re

pack_dir = 'pack'
index_path = os.path.join(pack_dir, 'index.toml')
pack_path = os.path.join(pack_dir, 'pack.toml')

files_entries = []

# Scan for all files in pack/
for root, dirs, files in os.walk(pack_dir):
    for f in sorted(files):
        if f in ['pack.toml', 'index.toml', '.packwizignore', 'RESOLVE_REPORT.md']:
            continue
        rel = os.path.relpath(os.path.join(root, f), pack_dir).replace('\\', '/')
        is_meta = rel.endswith('.pw.toml')
        full_path = os.path.join(root, f)
        with open(full_path, 'rb') as fp:
            sha256 = hashlib.sha256(fp.read()).hexdigest()
        files_entries.append((rel, sha256, is_meta))

files_entries.sort(key=lambda x: x[0])

# Generate index.toml
lines = ['hash-format = "sha256"', '']
for rel, sha256, is_meta in files_entries:
    lines.append('[[files]]')
    lines.append(f'file = "{rel}"')
    lines.append(f'hash = "{sha256}"')
    if is_meta:
        lines.append('metafile = true')
    lines.append('')

index_content = '\n'.join(lines)
with open(index_path, 'wb') as fp:
    fp.write(index_content.encode('utf-8'))

with open(index_path, 'rb') as fp:
    index_hash = hashlib.sha256(fp.read()).hexdigest()

# Update pack.toml
with open(pack_path, 'r', encoding='utf-8') as fp:
    pack_content = fp.read()

pack_content = re.sub(r'hash = "[0-9a-f]+"', f'hash = "{index_hash}"', pack_content)
with open(pack_path, 'wb') as fp:
    fp.write(pack_content.encode('utf-8'))

print(f'index.toml and pack.toml refreshed. Total files indexed: {len(files_entries)}, index hash: {index_hash}')
