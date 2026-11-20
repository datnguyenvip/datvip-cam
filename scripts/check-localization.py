"""Fail when a system label lacks an English translation or changes placeholders."""
from pathlib import Path
import json
import re

root = Path(__file__).resolve().parents[1]
errors = []
for folder, catalog_path, pattern in [
    ("ios/Sources", "ios/Sources/ui-en.json", r'AppLanguage\.text\("((?:[^"\\]|\\.)*)"'),
    ("android/app/src/main/java", "android/app/src/main/assets/ui-en.json", r'Language\.text\([^,]+,\s*"((?:[^"\\]|\\.)*)"'),
]:
    catalog = json.loads((root / catalog_path).read_text(encoding="utf-8"))
    for file in (root / folder).rglob("*"):
        if file.suffix not in (".swift", ".kt"): continue
        for raw in re.findall(pattern, file.read_text(encoding="utf-8")):
            source = json.loads('"' + raw + '"')
            if source not in catalog:
                errors.append(f"{file.relative_to(root)}: missing {source!r}")
            elif sorted(re.findall(r"\{\d+\}", source)) != sorted(re.findall(r"\{\d+\}", catalog[source])):
                errors.append(f"{file.relative_to(root)}: changed placeholders in {source!r}")
if errors:
    raise SystemExit("\n".join(errors))
print("All referenced system labels have translations with matching placeholders.")
