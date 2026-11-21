#!/usr/bin/env python3
"""Static integrity checks for the NEXORA GitHub Pages website."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse, unquote
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE_HOST = "nexoraofficialph.github.io"
BASE_PREFIX = "/nexora-official-website"
errors = []
pages = {}

class PageParser(HTMLParser):
    def __init__(self, path):
        super().__init__(convert_charrefs=True)
        self.path = path
        self.ids = set()
        self.links = []
        self.images = []
        self.scripts = []
        self.resources = []
        self.inline_script = None
        self.inline_type = ""

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            ident = attrs["id"]
            if ident in self.ids:
                errors.append(f"{self.path}: duplicate id #{ident}")
            self.ids.add(ident)
        if tag in ("a", "link") and attrs.get("href"):
            self.links.append((attrs["href"], attrs.get("target"), attrs.get("rel", "")))
        if tag in ("script", "img", "source") and attrs.get("src"):
            self.resources.append(attrs["src"])
            if tag == "script":
                self.scripts.append(attrs["src"])
        if tag == "img":
            if "alt" not in attrs:
                errors.append(f"{self.path}: image missing alt attribute: {attrs.get('src', '(no src)')}")
        if tag == "script" and not attrs.get("src"):
            self.inline_script = []
            self.inline_type = attrs.get("type", "").lower()

    def handle_data(self, data):
        if self.inline_script is not None:
            self.inline_script.append(data)

    def handle_endtag(self, tag):
        if tag == "script" and self.inline_script is not None:
            code = "".join(self.inline_script).strip()
            if code and self.inline_type not in ("application/ld+json", "application/json", "importmap"):
                result = subprocess.run(["node", "--check"], input=code, text=True, capture_output=True)
                if result.returncode:
                    errors.append(f"{self.path}: inline JavaScript syntax error: {result.stderr.strip()}")
            self.inline_script = None
            self.inline_type = ""

def route_to_file(raw, current):
    parsed = urlparse(raw)
    if parsed.scheme or parsed.netloc:
        if parsed.netloc != BASE_HOST:
            return None, None
        path = unquote(parsed.path)
        if path == BASE_PREFIX or path == BASE_PREFIX + "/":
            path = "/"
        elif path.startswith(BASE_PREFIX + "/"):
            path = path[len(BASE_PREFIX):]
        else:
            return None, None
    else:
        path = unquote(parsed.path)
        if not path:
            target = current
        elif path.startswith("/"):
            target = ROOT / path.lstrip("/")
        else:
            target = current.parent / path
    if parsed.scheme or parsed.netloc:
        target = ROOT / path.lstrip("/")
    if target.is_dir():
        target = target / "index.html"
    resolved = target.resolve()
    try:
        resolved.relative_to(ROOT.resolve())
    except ValueError:
        errors.append(f"{current.relative_to(ROOT)}: local path escapes the website root: {raw}")
        return None, None
    return resolved, parsed.fragment

html_files = sorted(ROOT.rglob("*.html"))
for path in html_files:
    rel = path.relative_to(ROOT)
    parser = PageParser(rel.as_posix())
    try:
        parser.feed(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"{rel}: HTML parse failure: {exc}")
    pages[path.resolve()] = parser

for path, parser in pages.items():
    for href, target, rel in parser.links:
        if target == "_blank" and not {"noopener", "noreferrer"}.issubset(set(rel.lower().split())):
            errors.append(f"{parser.path}: target=_blank link must include rel=noopener noreferrer: {href}")
        if href.startswith(("mailto:", "tel:", "javascript:", "data:")):
            if href.startswith("javascript:"):
                errors.append(f"{parser.path}: javascript: URL is not allowed")
            continue
        target_file, fragment = route_to_file(href, path)
        if target_file is None:
            continue
        if not target_file.is_file():
            errors.append(f"{parser.path}: broken local link/resource: {href}")
            continue
        if fragment and target_file.suffix.lower() == ".html":
            target_parser = pages.get(target_file.resolve())
            if target_parser and fragment not in target_parser.ids:
                errors.append(f"{parser.path}: missing fragment #{fragment} in {target_file.relative_to(ROOT)}")

for html_path, parser in pages.items():
    for src in parser.resources:
        if src.startswith(("https://", "http://", "//", "data:")):
            continue
        target = (html_path.parent / urlparse(src).path).resolve()
        try:
            target.relative_to(ROOT.resolve())
        except ValueError:
            errors.append(f"{parser.path}: local resource escapes the website root: {src}")
            continue
        if not target.is_file():
            errors.append(f"{parser.path}: missing local resource: {src}")

css = ROOT / "styles.css"
if css.exists():
    for raw_value in re.findall(r"url[(]([^)]*)[)]", css.read_text(encoding="utf-8")):
        value = raw_value.strip().strip("'\"")
        if value.startswith(("data:", "http://", "https://", "#")):
            continue
        target = (css.parent / value.split("?", 1)[0]).resolve()
        try:
            target.relative_to(ROOT.resolve())
        except ValueError:
            errors.append(f"styles.css: referenced asset escapes the website root: {value}")
            continue
        if not target.is_file():
            errors.append(f"styles.css: missing referenced asset: {value}")

required_social = [
    "https://t.me/nexorastudioteam",
    "https://www.facebook.com/profile.php?id=61596591209056",
    "https://www.facebook.com/profile.php?id=61596319277504",
]
for name in ("index.html", "drama/index.html", "promotion/index.html"):
    path = ROOT / name
    if path.exists():
        content = path.read_text(encoding="utf-8")
        for url in required_social:
            if url not in content:
                errors.append(f"{name}: missing official social link {url}")

apk_url = "https://github.com/nexoraofficialph/nexora-ai-drama/releases/latest/download/NEXORA-AI-DRAMA.apk"
for name in ("index.html", "drama/index.html", "promotion/index.html"):
    path = ROOT / name
    if path.exists() and apk_url not in path.read_text(encoding="utf-8"):
        errors.append(f"{name}: missing canonical latest APK link")

ads = ROOT / "app-ads.txt"
if not ads.is_file() or "google.com, pub-4497851143738030, DIRECT, f08c47fec0942fa0" not in ads.read_text(encoding="utf-8"):
    errors.append("app-ads.txt: expected NEXORA AdMob publisher record is missing")

sitemap = ROOT / "sitemap.xml"
try:
    ET.parse(sitemap)
except Exception as exc:
    errors.append(f"sitemap.xml: invalid XML: {exc}")

if errors:
    print("NEXORA website validation FAILED:")
    for item in errors:
        print(f"- {item}")
    sys.exit(1)

print(f"NEXORA website validation passed: {len(pages)} HTML pages, local links/assets, image alt attributes, safe external-link targets, inline JavaScript, social links, APK links, app-ads.txt and sitemap.")
