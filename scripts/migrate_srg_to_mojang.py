#!/usr/bin/env python3
"""One-time Season2 Core source migrator for Minecraft 1.20.1.

Builds an SRG -> Mojang-name table by composing Forge MCPConfig's joined.tsrg
with Mojang's official client/server ProGuard mappings, then rewrites SRG
identifiers in Java *code only*. String literals and comments are deliberately
left untouched so reflection fallbacks such as "m_6846_" remain available.

This script is intended for the ForgeGradle migration branch/CI, not runtime.
It does not redistribute Mojang or MCP mapping files; they are downloaded into
a temporary cache and used to derive the rename table locally.
"""
from __future__ import annotations

import collections
import io
import json
import os
from pathlib import Path
import re
import sys
import urllib.request
import zipfile

MC_VERSION = "1.20.1"
MCP_ARTIFACT = "1.20.1-20230612.114412"
MCP_URL = (
    "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/"
    f"{MCP_ARTIFACT}/mcp_config-{MCP_ARTIFACT}.zip"
)
MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
USER_AGENT = {"User-Agent": "Season2-Core-ForgeGradle-Migration/1.0"}
SRG_RE = re.compile(r"^(?:m|f)_\d+_$")

PRIM = {
    "int": "I", "long": "J", "short": "S", "byte": "B", "char": "C",
    "boolean": "Z", "float": "F", "double": "D", "void": "V",
}
CLS_RE = re.compile(r"^(\S+) -> (\S+):$")
MEM_RE = re.compile(r"^\s+(?:\d+:\d+:)?(\S+) (\w+|<init>|<clinit>)(\(.*\))? -> (\S+)$")


def _open(url: str):
    return urllib.request.urlopen(urllib.request.Request(url, headers=USER_AGENT), timeout=60)


def fetch(url: str, dest: Path) -> Path:
    if dest.is_file() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    print(f"Downloading {url}")
    with _open(url) as response, dest.open("wb") as out:
        out.write(response.read())
    return dest


def mcp_tsrg(cache: Path) -> Path:
    archive = fetch(MCP_URL, cache / f"mcp_config-{MCP_ARTIFACT}.zip")
    out = cache / "joined.tsrg"
    if not out.exists():
        with zipfile.ZipFile(archive) as zf:
            out.write_bytes(zf.read("config/joined.tsrg"))
    return out


def mojang_mapping_paths(cache: Path) -> list[Path]:
    with _open(MANIFEST_URL) as response:
        manifest = json.load(response)
    version_url = next(v["url"] for v in manifest["versions"] if v["id"] == MC_VERSION)
    with _open(version_url) as response:
        metadata = json.load(response)

    paths: list[Path] = []
    for side in ("client_mappings", "server_mappings"):
        entry = metadata["downloads"].get(side)
        if not entry:
            continue
        paths.append(fetch(entry["url"], cache / f"{side}-{MC_VERSION}.txt"))
    return paths


def load_proguard(paths: list[Path]):
    obf_to_moj_class: dict[str, str] = {}
    raw_fields: dict[str, list[tuple[str, str]]] = collections.defaultdict(list)
    raw_methods: dict[str, list[tuple[str, str, str, str]]] = collections.defaultdict(list)

    for path in paths:
        current = None
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            match = CLS_RE.match(line)
            if match:
                current = match.group(2).replace(".", "/")
                obf_to_moj_class[current] = match.group(1).replace(".", "/")
                continue
            match = MEM_RE.match(line)
            if match and current is not None:
                member_type, moj_name, args, obf_name = match.groups()
                if args is None:
                    raw_fields[current].append((moj_name, obf_name))
                else:
                    raw_methods[current].append((moj_name, member_type, args, obf_name))
    return obf_to_moj_class, raw_fields, raw_methods


def type_to_desc(type_name: str, moj_to_obf_class: dict[str, str]) -> str:
    prefix = ""
    while type_name.endswith("[]"):
        prefix += "["
        type_name = type_name[:-2]
    if type_name in PRIM:
        return prefix + PRIM[type_name]
    internal = type_name.replace(".", "/")
    obf = moj_to_obf_class.get(internal, internal)
    return prefix + "L" + obf + ";"


def args_to_desc(args: str, moj_to_obf_class: dict[str, str]) -> str:
    inner = args[1:-1]
    if not inner:
        return "()"
    return "(" + "".join(type_to_desc(arg.strip(), moj_to_obf_class) for arg in inner.split(",")) + ")"


def build_mapping(cache: Path) -> dict[str, str]:
    tsrg = mcp_tsrg(cache)
    obf_to_moj_class, raw_fields, raw_methods = load_proguard(mojang_mapping_paths(cache))
    moj_to_obf_class = {moj: obf for obf, moj in obf_to_moj_class.items()}

    field_lookup: dict[str, dict[str, str]] = collections.defaultdict(dict)
    method_lookup: dict[str, dict[tuple[str, str], str]] = collections.defaultdict(dict)

    for obf_class, entries in raw_fields.items():
        for moj_name, obf_name in entries:
            field_lookup[obf_class][obf_name] = moj_name
    for obf_class, entries in raw_methods.items():
        for moj_name, _return_type, args, obf_name in entries:
            method_lookup[obf_class][(obf_name, args_to_desc(args, moj_to_obf_class))] = moj_name

    lines = tsrg.read_text(encoding="utf-8", errors="replace").splitlines()
    tsrg2 = bool(lines and lines[0].startswith("tsrg2"))
    current_class = None
    candidate_names: dict[str, set[str]] = collections.defaultdict(set)

    for line in lines:
        if not line or line.startswith("tsrg2"):
            continue
        if not line.startswith("\t"):
            current_class = line.split(" ")[0]
            continue
        if line.startswith("\t\t") or current_class is None:
            continue

        parts = line.strip().split(" ")
        if tsrg2:
            if len(parts) == 3:  # obf srg id
                obf_name, srg_name = parts[0], parts[1]
                moj = field_lookup.get(current_class, {}).get(obf_name)
                if moj and srg_name.startswith("f_"):
                    candidate_names[srg_name].add(moj)
            elif len(parts) == 4:  # obf desc srg id
                obf_name, desc, srg_name = parts[0], parts[1], parts[2]
                arg_desc = desc[: desc.rfind(")") + 1]
                moj = method_lookup.get(current_class, {}).get((obf_name, arg_desc))
                if moj and srg_name.startswith("m_") and not moj.startswith(("lambda$", "access$")):
                    candidate_names[srg_name].add(moj)
        else:
            if len(parts) == 2:
                obf_name, srg_name = parts
                moj = field_lookup.get(current_class, {}).get(obf_name)
                if moj and srg_name.startswith(("f_", "field_")):
                    candidate_names[srg_name].add(moj)
            elif len(parts) == 3:
                obf_name, desc, srg_name = parts
                arg_desc = desc[: desc.rfind(")") + 1]
                moj = method_lookup.get(current_class, {}).get((obf_name, arg_desc))
                if moj and srg_name.startswith(("m_", "func_")) and not moj.startswith(("lambda$", "access$")):
                    candidate_names[srg_name].add(moj)

    conflicts = {name: values for name, values in candidate_names.items() if len(values) != 1}
    if conflicts:
        print(f"Skipping {len(conflicts)} ambiguous SRG identifiers.")
    mapping = {name: next(iter(values)) for name, values in candidate_names.items() if len(values) == 1}
    print(f"Built {len(mapping)} unambiguous SRG -> Mojang mappings for {MC_VERSION}.")
    return mapping


def rewrite_java(text: str, mapping: dict[str, str]):
    """Rewrite identifiers in Java code, leaving comments/strings/chars untouched."""
    out: list[str] = []
    replaced: collections.Counter[str] = collections.Counter()
    unknown: collections.Counter[str] = collections.Counter()
    i = 0
    n = len(text)
    state = "code"

    while i < n:
        ch = text[i]
        nxt = text[i + 1] if i + 1 < n else ""

        if state == "code":
            if ch == "/" and nxt == "/":
                out.extend((ch, nxt)); i += 2; state = "line_comment"; continue
            if ch == "/" and nxt == "*":
                out.extend((ch, nxt)); i += 2; state = "block_comment"; continue
            if ch == '"':
                out.append(ch); i += 1; state = "string"; continue
            if ch == "'":
                out.append(ch); i += 1; state = "char"; continue
            if ch.isalpha() or ch in "_$":
                j = i + 1
                while j < n and (text[j].isalnum() or text[j] in "_$"):
                    j += 1
                token = text[i:j]
                if SRG_RE.match(token):
                    if token in mapping:
                        out.append(mapping[token])
                        replaced[token] += 1
                    else:
                        out.append(token)
                        unknown[token] += 1
                else:
                    out.append(token)
                i = j
                continue
            out.append(ch); i += 1; continue

        if state == "line_comment":
            out.append(ch); i += 1
            if ch == "\n":
                state = "code"
            continue

        if state == "block_comment":
            if ch == "*" and nxt == "/":
                out.extend((ch, nxt)); i += 2; state = "code"
            else:
                out.append(ch); i += 1
            continue

        if state in ("string", "char"):
            out.append(ch); i += 1
            if ch == "\\" and i < n:
                out.append(text[i]); i += 1
                continue
            if (state == "string" and ch == '"') or (state == "char" and ch == "'"):
                state = "code"
            continue

    return "".join(out), replaced, unknown


def main() -> int:
    source_root = Path(sys.argv[1] if len(sys.argv) > 1 else "src/main/java")
    cache = Path(os.environ.get("SEASON2_MAPPING_CACHE", "build/migration-mappings"))
    report_path = Path("build/srg-migration-report.json")
    mapping = build_mapping(cache)

    all_replaced: collections.Counter[str] = collections.Counter()
    all_unknown: collections.Counter[str] = collections.Counter()
    changed_files = 0

    for path in sorted(source_root.rglob("*.java")):
        original = path.read_text(encoding="utf-8", errors="replace")
        migrated, replaced, unknown = rewrite_java(original, mapping)
        all_replaced.update(replaced)
        all_unknown.update(unknown)
        if migrated != original:
            path.write_text(migrated, encoding="utf-8")
            changed_files += 1

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps({
        "minecraft_version": MC_VERSION,
        "mcp_artifact": MCP_ARTIFACT,
        "mapping_entries": len(mapping),
        "changed_files": changed_files,
        "replacement_occurrences": sum(all_replaced.values()),
        "replaced_identifiers": dict(sorted(all_replaced.items())),
        "unmapped_code_identifiers": dict(sorted(all_unknown.items())),
    }, indent=2) + "\n", encoding="utf-8")

    print(f"Changed {changed_files} Java files; replaced {sum(all_replaced.values())} SRG identifier occurrences.")
    if all_unknown:
        print(f"Unmapped SRG identifiers still present in Java code: {len(all_unknown)}")
        for name, count in sorted(all_unknown.items()):
            print(f"  {name}: {count}")
    else:
        print("No unmapped SRG identifiers remain in Java code.")
    print(f"Report: {report_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
