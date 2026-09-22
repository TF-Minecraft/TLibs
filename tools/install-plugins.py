#!/usr/bin/env python3
"""Install direct TFMC plugin dependencies from verified releases, without recursion."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("tlibs_installer", Path(__file__).with_name("install-dependency.py"))
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)
CATALOG = Path(__file__).with_name("plugins.json")
NS = installer.NS


def dependencies(pom, catalog):
    """Select only explicitly declared, managed, provided dependencies."""
    tree = ET.parse(pom).getroot()
    result = []
    for dependency in tree.findall("m:dependencies/m:dependency", NS):
        group = dependency.findtext("m:groupId", namespaces=NS)
        artifact = dependency.findtext("m:artifactId", namespaces=NS)
        key = f"{group}:{artifact}"
        if key not in catalog:
            continue
        if dependency.findtext("m:scope", namespaces=NS) != "provided":
            raise ValueError(f"{key} must have provided scope")
        property_name = catalog[key]["property"]
        if dependency.findtext("m:version", namespaces=NS) != "${" + property_name + "}":
            raise ValueError(f"{key} must use ${{{property_name}}}")
        version = tree.findtext("m:properties/m:" + property_name, namespaces=NS)
        if not version or not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z.+_-]*", version):
            raise ValueError(f"Invalid or missing {property_name}")
        result.append((key, version, catalog[key]))
    return result


def prepare(pom, mode, args, catalog):
    """Resolve and verify each direct input, then select versions in the POM."""
    text = pom.read_text(encoding="utf-8")
    resolved = []
    for key, pinned, definition in dependencies(pom, catalog):
        group, artifact = key.split(":")
        options = argparse.Namespace(jar=None, assets=None, mvn=args.mvn, maven_repo=args.maven_repo)
        private_input = "versions" in definition and (
            "repository" not in definition or (mode == "pinned" and pinned in definition["versions"]))
        if private_input:
            # Source-unavailable inputs stay pinned; known legacy bytes remain usable for rollback.
            if pinned not in definition["versions"]:
                raise ValueError(f"No verified private input for {key}:{pinned}")
            version, entry = pinned, definition["versions"][pinned]
            options.assets = args.assets.resolve() if args.assets else None
        else:
            version, entry = installer.release_artifact(
                definition["repository"], definition["filename"],
                None if mode == "latest" else pinned, definition.get("allow_prerelease", False),
                legacy_filename_patterns=definition.get("legacy_filenames", ()))
        previous_token = os.environ.get("GH_TOKEN")
        try:
            if private_input and not options.assets:
                private_token = os.environ.get("TFMC_PRIVATE_TOKEN")
                if not private_token:
                    raise ValueError(f"{key} requires TFMC_PRIVATE_TOKEN or --assets")
                os.environ["GH_TOKEN"] = private_token
            installer.install(version, entry, options, group, artifact)
        finally:
            if previous_token is None:
                os.environ.pop("GH_TOKEN", None)
            else:
                os.environ["GH_TOKEN"] = previous_token
        property_name = definition["property"]
        pattern = r"(<" + re.escape(property_name) + r">)[^<]*(</" + re.escape(property_name) + r">)"
        text, count = re.subn(pattern, lambda match: match[1] + version + match[2], text)
        if count != 1:
            raise ValueError(f"Expected exactly one {property_name} property")
        resolved.append({"coordinates": f"{key}:{version}", "sha256": entry["sha256"],
                         "source": entry.get("url") or entry.get("repository"),
                         "selection": "private-pinned" if private_input else mode})
    if mode == "latest":
        pom.write_text(text, encoding="utf-8")
    return resolved


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pom", type=Path, default=Path("pom.xml"))
    parser.add_argument("--mode", choices=("pinned", "latest"), default="pinned")
    parser.add_argument("--assets", type=Path, help="Local private ServerAssets checkout")
    parser.add_argument("--mvn", default="mvn")
    parser.add_argument("--maven-repo", type=Path)
    parser.add_argument("--lock", type=Path, help="Write resolved versions and checksums as build metadata")
    args = parser.parse_args()
    try:
        resolved = prepare(args.pom, args.mode, args, json.loads(CATALOG.read_text(encoding="utf-8")))
        if args.lock:
            args.lock.parent.mkdir(parents=True, exist_ok=True)
            args.lock.write_text(json.dumps(resolved, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(resolved, indent=2))
        return 0
    except (ValueError, OSError, ET.ParseError, subprocess.SubprocessError) as error:
        print(f"Plugin dependency installation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
