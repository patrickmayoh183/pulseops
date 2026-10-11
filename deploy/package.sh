#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

[[ $# -ge 2 && $# -le 3 ]] || die 'Usage: bash deploy/package.sh RELEASE_ID APP_JAR [FRONTEND_DIST]'
validate_release_id "$1"
validate_artifact_sources "$2" "${3:-}"
repo_root=$(cd -- "$SCRIPT_DIR/.." && pwd)
output_dir="$repo_root/artifacts"
[[ ! -L "$output_dir" ]] || die 'Refusing symlinked artifacts directory.'
mkdir -p -- "$output_dir"
archive="$output_dir/pulseops-$1.tar.gz"
[[ ! -e "$archive" ]] || die 'Archive already exists; use a new release ID.'
stage=$(mktemp -d "$output_dir/package-$1.XXXXXX")
cleanup() {
  # Only remove the verified temporary tree created above; never a release or repository directory.
  if [[ "$stage" == "$output_dir/package-$1."* && -d "$stage" && ! -L "$stage" ]]; then
    rm -rf -- "$stage"
  fi
}
trap 'cleanup "$1"' EXIT
mkdir -p "$stage/deploy"
cp -- "$repo_root/README.md" "$repo_root/API-CONTRACT.md" "$stage/"
cp -R -- "$repo_root/docs" "$stage/docs"
cp -- "$SCRIPT_DIR/"*.sh "$SCRIPT_DIR/pulseops.service" "$SCRIPT_DIR/pulseops.env.example" \
  "$SCRIPT_DIR/Caddyfile.fragment.example" "$SCRIPT_DIR/README.md" "$stage/deploy/"
install -m 0444 -- "$2" "$stage/app.jar"
printf '%s\n' "$1" > "$stage/RELEASE_ID"
if [[ -n ${3:-} ]]; then
  mkdir "$stage/frontend"
  cp -a -- "$3/." "$stage/frontend/"
fi
(cd "$stage" && sha256sum app.jar > SHA256SUMS)
tar -C "$stage" -czf "$archive" .
say "Created $archive (no production environment file included)."
