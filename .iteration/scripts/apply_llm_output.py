#!/usr/bin/env python3
"""解析 LLM 输出中的 <<<FILE: path>>> ... <<<END>>> 块并写入工作区。"""
import os
import re
import sys

text = open(sys.argv[1], encoding="utf-8").read()
pattern = re.compile(r"<<<FILE:\s*(.+?)\s*>>>\r?\n(.*?)\r?\n?<<<END>>>", re.S)

applied = []
for match in pattern.finditer(text):
    path = match.group(1).strip().lstrip("./")
    content = match.group(2)
    if not path or ".." in path.split("/") or path.startswith("/"):
        continue
    allowed = (
        path.endswith((".kt", ".kts", ".xml", ".json", ".md", ".pro", ".toml", ".properties"))
        or path.startswith("res/")
    )
    if not allowed:
        continue
    directory = os.path.dirname(path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    if not content.endswith("\n"):
        content += "\n"
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(content)
    applied.append(path)

print("applied %d files:" % len(applied))
for path in applied:
    print("  - " + path)
if not applied:
    print("WARNING: no file blocks found in LLM output", file=sys.stderr)
    sys.exit(3)
