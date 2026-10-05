#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$project_root/site"
cp "$project_root/docs/privacy-policy.html" "$project_root/site/privacy.html"
cp "$project_root/docs/terms.html" "$project_root/site/terms.html"
