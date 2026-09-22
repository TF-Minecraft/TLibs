#!/usr/bin/env python3
"""Install an exact TLibs binary in Maven's cache, never in a plugin checkout."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.error
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


def release_artifact(repository, filename_pattern, version=None, allow_prerelease=False,
                     legacy_filename_patterns=()):
    """Resolve one published release and the exact checksum of its plugin JAR."""
    headers = {"Accept": "application/vnd.github+json"}
    if os.environ.get("GH_TOKEN"):
        headers["Authorization"] = "Bearer " + os.environ["GH_TOKEN"]
    endpoint = ("tags/v" + version if version else
                "?per_page=100" if allow_prerelease else "latest")
    url = f"https://api.github.com/repos/{repository}/releases"
    url += endpoint if endpoint.startswith("?") else "/" + endpoint
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=60) as response:
        release = json.load(response)
    if isinstance(release, list):
        release = next((r for r in release if not r.get("draft")), {})
    pattern = r"v?([0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?)"
    match = re.fullmatch(pattern, release.get("tag_name", ""))
    if release.get("draft") or not match or (not allow_prerelease and
            (release.get("prerelease") or "-" in match.group(1))):
        raise ValueError(f"{repository} must have a published {'versioned' if allow_prerelease else 'stable numeric'} release")
    resolved = match.group(1)
    if version and version != resolved:
        raise ValueError(f"Release version mismatch: expected {version}, got {resolved}")
    filenames = [pattern.format(version=resolved)
                 for pattern in (filename_pattern, *legacy_filename_patterns)]
    assets = {asset["name"]: asset for asset in release.get("assets", [])}
    filename = next((name for name in filenames if name in assets), None)
    if filename is None:
        raise ValueError(f"{repository} release {resolved} has no {filenames[0]}")
    asset = assets[filename]
    digest = asset.get("digest") or ""
    if re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        checksum = digest.split(":", 1)[1]
    else:
        checksum_asset = assets.get(filename + ".sha256") or assets.get("SHA256SUMS")
        if not checksum_asset:
            raise ValueError(f"{repository} release has no SHA-256 digest or checksum asset")
        with urllib.request.urlopen(checksum_asset["browser_download_url"], timeout=60) as response:
            lines = response.read().decode("utf-8").splitlines()
        matches = [fields[0] for line in lines if len(fields := line.split()) == 2
                   and re.fullmatch(r"[0-9a-f]{64}", fields[0])
                   and fields[1].lstrip("*") == filename]
        if len(matches) != 1:
            raise ValueError("Invalid TLibs release checksum file")
        checksum = matches[0]
    return resolved, {"url": asset["browser_download_url"], "sha256": checksum}


def latest_release():
    """Preserve the standalone TLibs installer's stable-release selection."""
    return release_artifact("TF-Minecraft/TLibs", "tlibs-{version}.jar",
                            legacy_filename_patterns=("TLibs-{version}.jar",))


def resolved_pom(path, version):
    """Prepare a CI-only version update without reformatting the consumer POM."""
    tree = ET.parse(path).getroot()
    dependency = next((d for d in tree.findall("m:dependencies/m:dependency", NS)
                       if d.findtext("m:groupId", namespaces=NS) == "me.plugins"
                       and d.findtext("m:artifactId", namespaces=NS) == "tlibs"), None)
    if dependency is None or dependency.findtext("m:version", namespaces=NS) != "${tlibs.version}":
        raise ValueError("Latest mode requires me.plugins:tlibs with version ${tlibs.version}")
    updated, count = re.subn(r"(<tlibs.version>)[^<]*(</tlibs.version>)",
                             lambda match: match[1] + version + match[2],
                             path.read_text(encoding="utf-8"))
    if count != 1:
        raise ValueError("Latest mode requires exactly one tlibs.version property")
    return updated


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
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                with destination.open("wb") as output:
                    shutil.copyfileobj(response, output)
        except urllib.error.HTTPError as error:
            if error.code not in (401, 403, 404) or "fallback" not in entry:
                raise
            # Alternate access to the same exact binary, never an API fallback.
            # The caller checks the original full checksum after either source.
            download(entry["fallback"], destination)
    else:
        subprocess.run(["gh", "release", "download", entry["tag"], "--repo",
                        entry["repository"], "--pattern", entry["filename"],
                        "--output", str(destination)], check=True, timeout=120)


def install(version, entry, args, group="me.plugins", artifact="tlibs"):
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
            f'  <groupId>{group}</groupId><artifactId>{artifact}</artifactId>\n'
            f'  <version>{version}</version><packaging>jar</packaging>\n'
            '  <description>Checksum-verified plugin; provided by the server.</description>\n'
            '</project>\n', encoding="utf-8")
        command = [args.mvn, "-B", "--no-transfer-progress",
                   "org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file",
                   f"-Dfile={jar}", f"-DpomFile={pom}"]
        if args.maven_repo:
            command.append(f"-Dmaven.repo.local={args.maven_repo.resolve()}")
        subprocess.run(command, cwd=work, check=True, timeout=300)
    print(f"Installed {group}:{artifact}:{version} (SHA-256 {entry['sha256']})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--pom", type=Path, help="Read the consumer's pinned dependency version")
    selection.add_argument("--version", help="Install a version from artifacts.json")
    parser.add_argument("--latest", action="store_true",
                        help="Resolve the latest stable release and update --pom after installation")
    parser.add_argument("--github-output", type=Path, help="Append resolved version and checksum action outputs")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--jar", type=Path, help="Use a local JAR; the checksum must still match")
    source.add_argument("--assets", type=Path, help="Use a local server-assets checkout")
    parser.add_argument("--mvn", default="mvn", help="Maven executable")
    parser.add_argument("--maven-repo", type=Path, help="Override Maven's local repository")
    args = parser.parse_args()
    try:
        updated_pom = None
        if args.latest:
            if not args.pom or args.jar or args.assets:
                raise ValueError("--latest requires --pom and downloads its release artifact")
            version, entry = latest_release()
            updated_pom = resolved_pom(args.pom, version)
            print(f"Resolved latest stable TLibs: {version} (SHA-256 {entry['sha256']})")
        else:
            version = args.version or version_from_pom(args.pom)
            catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
            if version not in catalog:
                raise ValueError(f"Unknown TLibs version {version!r}; supported: {', '.join(catalog)}")
            entry = catalog[version]
        # Resolve sources before changing the subprocess working directory.
        if args.jar:
            args.jar = args.jar.resolve()
        if args.assets:
            args.assets = args.assets.resolve()
        install(version, entry, args)
        if updated_pom is not None:
            args.pom.write_text(updated_pom, encoding="utf-8")
        if args.github_output:
            with args.github_output.open("a", encoding="utf-8") as output:
                output.write(f"version={version}\nsha256={entry['sha256']}\n")
    except (ValueError, OSError, ET.ParseError, subprocess.SubprocessError) as error:
        print(f"TLibs installation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
