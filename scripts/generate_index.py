#!/usr/bin/env python3
"""
generate_index.py  –  Scans a folder of built APKs and emits an
index.min.json that Aniyomi understands as a custom extension repository.

Usage (called automatically by the GitHub Actions workflow):
    python3 scripts/generate_index.py <output_dir>

The index is printed to stdout so the workflow can redirect it:
    python3 scripts/generate_index.py output/ > output/index.min.json
"""

import json
import re
import struct
import sys
import zipfile
from pathlib import Path


# ── APK / Android manifest helpers ────────────────────────────────────────

def _decode_axml(data: bytes) -> dict:
    """
    Ultra-minimal binary AndroidManifest.xml reader.
    Extracts only the <meta-data> and <manifest> attributes we care about.
    Returns a flat dict of {name: value}.
    """
    # We only need the string pool and a handful of known attribute names.
    # Full AXML parsing is complex; instead we grep for UTF-16LE strings.
    result = {}
    # Extract UTF-16LE strings from the binary chunk
    strings = re.findall(b'(?:[\x20-\x7e]\x00){4,}', data)
    decoded = [s.decode('utf-16-le', errors='ignore').strip() for s in strings]
    return decoded


def read_apk_metadata(apk_path: Path) -> dict:
    """
    Open an APK (it's a ZIP) and pull metadata from AndroidManifest.xml
    and the APK filename itself (which common.gradle names predictably).
    """
    meta = {
        "apk": apk_path.name,
        "name": "",
        "pkg": "",
        "lang": "",
        "code": 1,
        "version": "1.1",
        "nsfw": 0,
    }

    # ── Parse filename: aniyomi-en.mysite-v1.1.apk ────────────────────────
    m = re.match(r'aniyomi-([a-z]+\.[^-]+)-v([\d.]+)\.apk', apk_path.name)
    if m:
        meta["pkg"] = f"eu.kanade.tachiyomi.extension.{m.group(1)}"
        meta["lang"] = m.group(1).split(".")[0]
        meta["version"] = m.group(2)
        try:
            meta["code"] = int(m.group(2).split(".")[-1])
        except ValueError:
            pass
        # Derive a display name from the slug (e.g. "mysite" → "MySite")
        slug = m.group(1).split(".", 1)[-1]
        meta["name"] = slug.title().replace("_", " ")

    # ── Try to get a more accurate name from the manifest ─────────────────
    try:
        with zipfile.ZipFile(apk_path) as z:
            raw = z.read("AndroidManifest.xml")
            strings = _decode_axml(raw)
            # Look for the appName string (set to "Aniyomi: <ExtName>")
            for s in strings:
                if s.startswith("Aniyomi: "):
                    meta["name"] = s.removeprefix("Aniyomi: ")
                    break
                # Also capture NSFW flag if present
                if s in ("1", "0") and "nsfw" not in meta:
                    meta["nsfw"] = int(s)
    except Exception:
        pass  # Non-critical — filename fallback is fine

    return meta


# ── Main ──────────────────────────────────────────────────────────────────

def main():
    if len(sys.argv) < 2:
        print("Usage: generate_index.py <output_dir>", file=sys.stderr)
        sys.exit(1)

    output_dir = Path(sys.argv[1])
    apks = sorted(output_dir.glob("*.apk"))

    if not apks:
        print("[]")  # Valid empty repo
        return

    index = []
    for apk in apks:
        meta = read_apk_metadata(apk)
        index.append({
            "name":    meta["name"],
            "pkg":     meta["pkg"],
            "apk":     meta["apk"],
            "lang":    meta["lang"],
            "code":    meta["code"],
            "version": meta["version"],
            "nsfw":    meta["nsfw"],
            # icon: omit — Aniyomi will use a default icon
            # source: [] — per-source detail, optional for custom repos
        })

    print(json.dumps(index, indent=None, separators=(',', ':')))


if __name__ == "__main__":
    main()
