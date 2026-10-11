#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

[[ $# -ge 2 && $# -le 3 ]] || die 'Usage: sudo bash update.sh RELEASE_ID APP_JAR [FRONTEND_DIST]'
require_root_and_tools
require_installed
old_release=$(resolve_release_link current)
new_release=$(stage_release "$1" "$2" "${3:-}")
backup_runtime
activate_with_fallback "$new_release" "$old_release"
