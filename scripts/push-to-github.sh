#!/usr/bin/env bash
# First-time push. Create an EMPTY repo on GitHub first (no README), named as in project.env.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
set -a; . ./project.env; set +a
URL="https://github.com/$GITHUB_USER/$REPO_NAME.git"
[ -d .git ] || git init -q -b main
git add -A
git commit -q -m "The Interpreter: Android project + Settings tab" || true
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin "$URL"
echo "Pushing to $URL"
git push -u origin main
