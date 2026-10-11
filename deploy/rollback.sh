#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

[[ $# -le 1 ]] || die 'Usage: sudo bash rollback.sh [RELEASE_ID]'
require_root_and_tools
require_installed
old_release=$(resolve_release_link current)
if [[ $# -eq 1 ]]; then
  validate_release_id "$1"
  target="$PULSE_RELEASES/$1"
else
  target=$(resolve_release_link previous)
fi
[[ -f "$target/app.jar" && ! -L "$target" ]] || die 'Requested release is missing or symlinked.'
[[ "$target" != "$old_release" ]] || die 'Requested release is already current.'
backup_runtime
activate_with_fallback "$target" "$old_release"
