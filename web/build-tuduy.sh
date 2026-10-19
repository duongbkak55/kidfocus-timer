#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
dist_root="$repo_root/web/dist"
page_out="$dist_root/tu-duy"

if [[ ! -f "$repo_root/web/tu-duy/og.png" ]]; then
  echo "Missing web/tu-duy/og.png; capture the real page before building." >&2
  exit 1
fi

mkdir -p "$dist_root"
cp "$repo_root/web/analytics.js" "$dist_root/analytics.js"
rm -rf "$page_out" "$dist_root/beta"
mkdir -p "$page_out" "$dist_root/beta"
cp "$repo_root/web/tu-duy/index.html" "$page_out/index.html"
cp "$repo_root/web/tu-duy/favicon.svg" "$page_out/favicon.svg"
cp "$repo_root/web/tu-duy/og.png" "$page_out/og.png"
cp "$repo_root/site/privacy.html" "$page_out/privacy.html"
cp "$repo_root/site/terms.html" "$page_out/terms.html"
cp -R "$repo_root/shared/learning/www/tuduy" "$page_out/tuduy"
cp "$repo_root/web/beta/index.html" "$dist_root/beta/index.html"
rm -rf "$dist_root/cai-app" && mkdir -p "$dist_root/cai-app" && cp "$repo_root/web/cai-app/index.html" "$dist_root/cai-app/index.html"

npm --prefix "$repo_root/web" run build:app
npm --prefix "$repo_root/web" run build:learning
if [[ -f "$repo_root/web/app/firebase-config.json" ]]; then
  cp "$repo_root/web/app/firebase-config.json" "$dist_root/app/firebase-config.json"
fi

echo "Built $page_out ($(du -sk "$page_out" | awk '{print $1}') KiB)"
