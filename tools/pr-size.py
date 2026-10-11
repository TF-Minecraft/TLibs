#!/usr/bin/env python3
"""Label a pull request by size."""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

# (name, smallest effective line count, colour, description)
SIZES = [
    ("size:XS", 0, "0e8a16", "0-9 effective changed lines (tests excluded in mixed PRs)."),
    ("size:S", 10, "5ebd3e", "10-29 effective changed lines (tests excluded in mixed PRs)."),
    ("size:M", 30, "fbca04", "30-99 effective changed lines (tests excluded in mixed PRs)."),
    ("size:L", 100, "fe7d37", "100-499 effective changed lines (tests excluded in mixed PRs)."),
    ("size:XL", 500, "d93f0b", "500-999 effective changed lines (tests excluded in mixed PRs)."),
    ("size:XXL", 1000, "b60205", "1,000+ effective changed lines (tests excluded in mixed PRs)."),
]
TEST_EXCLUDES = [
    ":(glob,exclude)**/src/test/**",
    ":(glob,exclude)**/test/**",
    ":(glob,exclude)**/tests/**",
    ":(glob,exclude)**/__tests__/**",
    ":(glob,exclude)**/*.test.*",
    ":(glob,exclude)**/*.spec.*",
]


def sum_numstat(text):
    """Add insertions and deletions from `git diff --numstat`; binary files count as zero."""
    total = 0
    for line in text.splitlines():
        if not line:
            continue
        added, deleted = line.split("\t")[:2]
        total += (0 if added == "-" else int(added)) + (0 if deleted == "-" else int(deleted))
    return total


def effective_lines(total, non_test):
    """Tests count only when the pull request changes nothing else."""
    return non_test if non_test else total


def size_label(lines):
    return [name for name, minimum, _, _ in SIZES if lines >= minimum][-1]


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def measure(base_sha, head_sha):
    for sha in (base_sha, head_sha):
        if subprocess.run(["git", "cat-file", "-e", f"{sha}^{{commit}}"], capture_output=True).returncode:
            git("fetch", "--no-tags", "origin", sha)
    diff = ["diff", "--numstat", "--ignore-all-space", "--ignore-blank-lines", f"{base_sha}...{head_sha}"]
    total = sum_numstat(git(*diff))
    non_test = sum_numstat(git(*diff, "--", ".", *TEST_EXCLUDES))
    return total, non_test


class GitHub:
    def __init__(self, repository, token):
        self.base = f"https://api.github.com/repos/{repository}"
        self.token = token

    def request(self, method, path, body=None):
        request = urllib.request.Request(
            self.base + path, method=method,
            data=None if body is None else json.dumps(body).encode(),
            headers={"Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
                     "X-GitHub-Api-Version": "2022-11-28"})
        with urllib.request.urlopen(request) as response:
            payload = response.read()
            return json.loads(payload) if payload else None

    def paged(self, path):
        page, items = 1, []
        while True:
            batch = self.request("GET", f"{path}?per_page=100&page={page}")
            items += batch
            if len(batch) < 100:
                return items
            page += 1


def sync_label(api, number, label, current):
    name, _, colour, description = next(size for size in SIZES if size[0] == label)
    quoted = urllib.parse.quote(name, safe="")
    try:
        existing = api.request("GET", f"/labels/{quoted}")
        if existing["color"] != colour or (existing.get("description") or "") != description:
            api.request("PATCH", f"/labels/{quoted}", {"color": colour, "description": description})
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        api.request("POST", "/labels", {"name": name, "color": colour, "description": description})
    for stale in current:
        if stale != name and stale in {size[0] for size in SIZES}:
            api.request("DELETE", f"/issues/{number}/labels/{urllib.parse.quote(stale, safe='')}")
    if name not in current:
        api.request("POST", f"/issues/{number}/labels", {"labels": [name]})


def main():
    with open(os.environ["GITHUB_EVENT_PATH"], encoding="utf-8") as handle:
        pull = json.load(handle)["pull_request"]
    api = GitHub(os.environ["GITHUB_REPOSITORY"], os.environ["GH_TOKEN"])
    number, head_sha = pull["number"], pull["head"]["sha"]

    total, non_test = measure(pull["base"]["sha"], head_sha)
    lines = effective_lines(total, non_test)
    label = size_label(lines)
    print(f"PR #{number}: {non_test} non-test lines, {total - non_test} test lines -> {label}")

    current = [item["name"] for item in api.paged(f"/issues/{number}/labels")]
    try:
        sync_label(api, number, label, current)
    except urllib.error.HTTPError as error:
        if error.code != 403:
            raise
        print("::warning::No permission to label this pull request; the size is in the job summary.")

    text = f"**{label}**: {lines} effective changed lines."
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as handle:
            handle.write(text + "\n")
    print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
