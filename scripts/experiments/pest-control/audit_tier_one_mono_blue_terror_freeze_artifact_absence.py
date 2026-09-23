#!/usr/bin/env python3
"""Read-only prior-artifact guard for the Mono-Blue Terror production freeze."""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.request

ARTIFACT_NAME = "pest-control-tier-one-mono-blue-terror-smoke-vector-freeze"


def main() -> None:
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GH_TOKEN", "")
    if not repo or not token:
        raise SystemExit("repository/token context missing")

    url = (
        f"https://api.github.com/repos/{repo}"
        f"/actions/artifacts?name={ARTIFACT_NAME}&per_page=100"
    )
    request = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.load(response)
            status = response.status
    except urllib.error.HTTPError as error:
        print(json.dumps({
            "artifact_name": ARTIFACT_NAME,
            "http_status": error.code,
            "result": "HTTP_ERROR",
        }, sort_keys=True))
        raise

    count = payload.get("total_count")
    print(json.dumps({
        "artifact_name": ARTIFACT_NAME,
        "http_status": status,
        "total_count": count,
    }, sort_keys=True))
    if count != 0:
        raise SystemExit(f"prior freeze artifact exists: total_count={count}")


if __name__ == "__main__":
    main()
