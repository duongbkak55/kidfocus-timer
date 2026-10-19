#!/usr/bin/env python3
"""One-time local Umami setup on OCI. Prints only the public website ID."""

import json
import os
import secrets
import urllib.error
import urllib.request
from pathlib import Path


BASE = "http://127.0.0.1:3000"
ADMIN_PASSWORD_FILE = Path("/home/ubuntu/kidfocus-umami/.admin-password")


def request(method, route, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    payload = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + route, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        # Never echo response bodies: a provider could include credentials.
        raise RuntimeError(f"{route} returned HTTP {error.code}") from None


def login(password):
    return request("POST", "/api/auth/login", {"username": "admin", "password": password})["token"]


def main():
    if ADMIN_PASSWORD_FILE.exists():
        token = login(ADMIN_PASSWORD_FILE.read_text().strip())
    else:
        token = login("umami")
        password = secrets.token_urlsafe(36)
        request("POST", "/api/me/password", {
            "currentPassword": "umami", "newPassword": password,
        }, token)
        fd = os.open(ADMIN_PASSWORD_FILE, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w") as output:
            output.write(password + "\n")
        token = login(password)
    sites = request("GET", "/api/websites", token=token)
    existing = next((site for site in sites.get("data", []) if site.get("domain") == "kidfocus.web.app"), None)
    site = existing or request("POST", "/api/websites", {
        "name": "KidFocus web", "domain": "kidfocus.web.app",
    }, token)
    if site.get("recorderEnabled") or (site.get("replayConfig") or {}).get("replayEnabled"):
        raise RuntimeError("Recording unexpectedly enabled; disable it before tracking")
    print(site["id"])


if __name__ == "__main__":
    main()
