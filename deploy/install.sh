#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

[[ $# -ge 3 && $# -le 4 ]] || die 'Usage: sudo bash install.sh RELEASE_ID APP_JAR ENV_FILE [FRONTEND_DIST]'
require_root_and_tools
validate_layout_paths
validate_release_id "$1"
validate_environment "$3"
validate_artifact_sources "$2" "${4:-}"
command -v useradd >/dev/null || die 'Required command missing: useradd'
command -v getent >/dev/null || die 'Required command missing: getent'
for path in "$PULSE_BASE" "$PULSE_ENV_DIR" "$PULSE_UNIT" "$PULSE_STATE"; do
  [[ ! -e "$path" && ! -L "$path" ]] || die "Existing path detected; install refuses to overwrite it: $path"
done
[[ -f "$SCRIPT_DIR/pulseops.service" ]] || die 'Missing adjacent pulseops.service template.'
[[ $(systemctl show pulseops.service --property=LoadState --value) == not-found ]] || die 'A PulseOps service unit already exists; inspect it before installing.'
[[ -z $(ss -H -lnt 'sport = :18080') ]] || die 'Port 18080 is already in use; install will not interfere with its owner.'
getent passwd pulseops >/dev/null && die 'User pulseops already exists; inspect it before installing.'
getent group pulseops >/dev/null && die 'Group pulseops already exists; inspect it before installing.'

install -d -m 0755 "$PULSE_BASE" "$PULSE_RELEASES"
install -d -m 0700 "$PULSE_BACKUPS" "$PULSE_ENV_DIR"
useradd --system --user-group --home-dir "$PULSE_STATE" --shell /sbin/nologin pulseops
install -d -m 0750 -o pulseops -g pulseops "$PULSE_STATE"
install -m 0600 -- "$3" "$PULSE_ENV_FILE"
install -m 0644 "$SCRIPT_DIR/pulseops.service" "$PULSE_UNIT"
release=$(stage_release "$1" "$2" "${4:-}")
switch_link current "$release"
backup_runtime
systemctl daemon-reload
if systemctl start pulseops.service && wait_for_health; then
  systemctl enable pulseops.service
  say "Installed healthy release: $1. Proxy/DNS/firewall changes remain separate."
else
  systemctl stop pulseops.service || true
  die 'Initial health check failed; application stopped. Files were preserved for diagnosis. Inspect journalctl -u pulseops.'
fi
