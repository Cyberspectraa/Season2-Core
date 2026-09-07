#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
pat = re.compile(r"^(?:m|f)_\d+_$")


def code_identifiers(text: str):
    """Yield Java identifiers from code only, skipping comments/string/char literals."""
    i = 0
    n = len(text)
    state = "code"
    line = 1
    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if state == "code":
            if ch == "/" and nxt == "/":
                i += 2; state = "line_comment"; continue
            if ch == "/" and nxt == "*":
                i += 2; state = "block_comment"; continue
            if ch == '"':
                i += 1; state = "string"; continue
            if ch == "'":
                i += 1; state = "char"; continue
            if ch.isalpha() or ch in "_$":
                start_line = line
                j = i + 1
                while j < n and (text[j].isalnum() or text[j] in "_$"):
                    j += 1
                yield text[i:j], start_line
                i = j
                continue
            if ch == "\n":
                line += 1
            i += 1
            continue

        if state == "line_comment":
            if ch == "\n":
                line += 1
                state = "code"
            i += 1
            continue

        if state == "block_comment":
            if ch == "\n":
                line += 1
            if ch == "*" and nxt == "/":
                i += 2
                state = "code"
            else:
                i += 1
            continue

        if state in ("string", "char"):
            if ch == "\\" and i + 1 < n:
                if text[i + 1] == "\n":
                    line += 1
                i += 2
                continue
            if ch == "\n":
                line += 1
            if (state == "string" and ch == '"') or (state == "char" and ch == "'"):
                state = "code"
            i += 1
            continue


hits = []
for path in sorted(root.rglob("*.java")):
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    for token, lineno in code_identifiers(text):
        if pat.match(token):
            source_line = lines[lineno - 1].strip() if 0 < lineno <= len(lines) else ""
            hits.append((path, lineno, token, source_line))

if not hits:
    print("No legacy SRG identifiers remain in Java code.")
    raise SystemExit(0)

print(f"Found {len(hits)} legacy SRG identifier occurrences in Java code:")
for path, lineno, token, source_line in hits:
    print(f"{path}:{lineno}: {token} :: {source_line}")
print("\nString/comment SRG fallbacks are intentionally ignored by this audit.")
raise SystemExit(1)
