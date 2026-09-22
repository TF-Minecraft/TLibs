#!/usr/bin/env python3
"""Install an exact TLibs binary in Maven's cache, never in a plugin checkout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET

CATALOG = Path(__file__).with_name("artifacts.json")
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def version_from_pom(path):
    root = ET.parse(path).getroot()
    dependency = next((d for d in root.findall("m:dependencies/m:dependency", NS)
                       if d.findtext("m:groupId", namespaces=NS) == "me.plugins"
                       and d.findtext("m:artifactId", namespaces=NS) == "tlibs"), None)
    if dependency is None:
        raise ValueError(f"No me.plugins:tlibs dependency in {path}")
    version = dependency.findtext("m:version", namespaces=NS)
    if version == "${tlibs.version}":
        version = root.findtext("m:properties/m:tlibs.version", namespaces=NS)
    if not version or "${" in version:
        raise ValueError(f"Expected a pinned tlibs.version in {path}")
    return version


def verify(path, expected):
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != expected:
        raise ValueError(f"TLibs checksum mismatch: expected {expected}, got {actual}")


def download(entry, destination):
    if "url" in entry:
        with urllib.request.urlopen(entry["url"], timeout=60) as response:
            with destination.open("wb") as output:
                shutil.copyfileobj(response, output)
    elif "ref" in entry:
        endpoint = (f"repos/{entry['repository']}/contents/{entry['asset_path']}"
                    f"?ref={entry['ref']}")
        token = os.environ.get("GH_TOKEN") or subprocess.check_output(
            ["gh", "auth", "token"], text=True, timeout=30).strip()
        request = urllib.request.Request("https://api.github.com/" + endpoint, headers={
            "Accept": "application/vnd.github.raw+json",
            "Authorization": "Bearer " + token,
        })
        with urllib.request.urlopen(request, timeout=60) as response:
            with destination.open("wb") as output:
                shutil.copyfileobj(response, output)
    else:
        subprocess.run(["gh", "release", "download", entry["tag"], "--repo",
                        entry["repository"], "--pattern", entry["filename"],
                        "--output", str(destination)], check=True, timeout=120)


def install(version, entry, args):
    # Running outside a consumer's project also works before its other private
    # dependencies have been prepared. Always supply a clean POM; the binary's
    # embedded POM may contain obsolete absolute systemPath dependencies.
    with tempfile.TemporaryDirectory(prefix="tlibs-maven-") as directory:
        work = Path(directory)
        jar = work / "tlibs.jar"
        if args.jar:
            shutil.copyfile(args.jar, jar)
        elif args.assets:
            if "asset_path" not in entry:
                raise ValueError("This version is not in server-assets; use --jar or authenticated download")
            shutil.copyfile(args.assets / entry["asset_path"], jar)
        else:
            download(entry, jar)
        verify(jar, entry["sha256"])
        pom = work / "pom.xml"
        pom.write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
            '  <modelVersion>4.0.0</modelVersion>\n'
            '  <groupId>me.plugins</groupId><artifactId>tlibs</artifactId>\n'
            f'  <version>{version}</version><packaging>jar</packaging>\n'
            '  <description>Checksum-verified TLibs plugin; provided by the server.</description>\n'
            '</project>\n', encoding="utf-8")
        command = [args.mvn, "-B", "--no-transfer-progress",
                   "org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file",
                   f"-Dfile={jar}", f"-DpomFile={pom}"]
        if args.maven_repo:
            command.append(f"-Dmaven.repo.local={args.maven_repo.resolve()}")
        subprocess.run(command, cwd=work, check=True, timeout=300)
    print(f"Installed me.plugins:tlibs:{version} (SHA-256 {entry['sha256']})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--pom", type=Path, help="Read the consumer's pinned dependency version")
    selection.add_argument("--version", help="Install a version from artifacts.json")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--jar", type=Path, help="Use a local JAR; the checksum must still match")
    source.add_argument("--assets", type=Path, help="Use a local server-assets checkout")
    parser.add_argument("--mvn", default="mvn", help="Maven executable")
    parser.add_argument("--maven-repo", type=Path, help="Override Maven's local repository")
    args = parser.parse_args()
    try:
        version = args.version or version_from_pom(args.pom)
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        if version not in catalog:
            raise ValueError(f"Unknown TLibs version {version!r}; supported: {', '.join(catalog)}")
        # Resolve sources before changing the subprocess working directory.
        if args.jar:
            args.jar = args.jar.resolve()
        if args.assets:
            args.assets = args.assets.resolve()
        install(version, catalog[version], args)
    except (ValueError, OSError, ET.ParseError, subprocess.SubprocessError) as error:
        print(f"TLibs installation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
