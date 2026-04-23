#!/usr/bin/env bash
# Builds and deploys the MkDocs site (including Javadoc) to the `docs-site/`
# folder on the `gh-pages` branch. Leaves the benchmark data (`dev/bench/`)
# untouched.
#
# Usage:
#   ./development/scripts/deploy-docs/deploy-docs.sh
#   ./development/scripts/deploy-docs/deploy-docs.sh "custom commit message"

set -euo pipefail

# Run from repo root regardless of where the script was invoked from.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$REPO_ROOT"

COMMIT_MSG="${1:-docs: update docs site}"
WORKTREE_DIR="gh-pages-deploy"

cleanup() {
  # Always resolve the worktree path relative to REPO_ROOT, not $PWD —
  # later steps cd into the worktree, which would otherwise break
  # `git worktree remove` on exit.
  if [ -d "$REPO_ROOT/$WORKTREE_DIR" ]; then
    echo "==> Cleaning up worktree"
    (cd "$REPO_ROOT" && git worktree remove --force "$WORKTREE_DIR") || true
    git -C "$REPO_ROOT" worktree prune || true
  fi
}
trap cleanup EXIT

echo "==> Generating Javadoc into docs/apidocs/"
mvn javadoc:javadoc -q

echo "==> Building MkDocs site into site/"
python3 -m mkdocs build --strict

echo "==> Checking out gh-pages in a temporary worktree"
git fetch origin gh-pages
git worktree add "$WORKTREE_DIR" gh-pages

echo "==> Replacing docs-site/ with the freshly built site"
rm -rf "$WORKTREE_DIR/docs-site"
cp -R site/ "$WORKTREE_DIR/docs-site/"

echo "==> Committing and pushing to gh-pages"
cd "$WORKTREE_DIR"
git add docs-site

if git diff --cached --quiet; then
  echo "==> No changes to publish."
else
  git commit -m "$COMMIT_MSG"
  git push origin gh-pages
  echo "==> Deployed. Site will be live at:"
  echo "    https://numaproj-contrib.github.io/apache-pulsar-java/docs-site/"
fi
