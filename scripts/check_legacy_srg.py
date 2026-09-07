\
#!/usr/bin/env python3
from pathlib import Path
import re, sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'src/main/java')
pat = re.compile(r'\b(?:m|f)_\d+_\b')
hits = []
for p in sorted(root.rglob('*.java')):
    for lineno, line in enumerate(p.read_text(encoding='utf-8', errors='replace').splitlines(), 1):
        names = pat.findall(line)
        if names:
            hits.append((p, lineno, names, line.strip()))

if not hits:
    print('No legacy SRG identifiers found.')
    raise SystemExit(0)

print(f'Found {len(hits)} source lines containing legacy SRG identifiers:')
for p, lineno, names, line in hits:
    print(f'{p}:{lineno}: {", ".join(names)} :: {line}')
print('\nThese must be converted to official mapped names before the migration is considered complete.')
raise SystemExit(1)
