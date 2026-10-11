#!/usr/bin/env bash
# Shared by install/update/rollback; never source a production environment file.
set -Eeuo pipefail
umask 077

readonly PULSE_BASE=/opt/pulseops
readonly PULSE_RELEASES=/opt/pulseops/releases
readonly PULSE_BACKUPS=/opt/pulseops/backups
readonly PULSE_ENV_DIR=/etc/pulseops
readonly PULSE_ENV_FILE=/etc/pulseops/pulseops.env
readonly PULSE_UNIT=/etc/systemd/system/pulseops.service
readonly PULSE_STATE=/var/lib/pulseops

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
say() { printf '%s\n' "$*"; }

require_root_and_tools() {
  [[ ${EUID} -eq 0 ]] || die 'Run with sudo/root on the intended Linux host.'
  [[ $(uname -s) == Linux ]] || die 'Installation scripts are for Linux hosts only.'
  local tool
  for tool in install realpath readlink sha256sum curl systemctl awk grep date ln mv cp find flock chmod dirname basename ss; do
    command -v "$tool" >/dev/null || die "Required command missing: $tool"
  done
  [[ -x /usr/bin/java ]] || die 'Java must be available at /usr/bin/java.'
  /usr/bin/java -version 2>&1 | grep -Eq 'version "21([.\"]|$)' || die 'Java 21 is required.'
  # Serialize all three scripts without acquiring any lock used by other applications.
  [[ ! -L /run/pulseops-deploy.lock ]] || die 'Refusing a symlinked deployment lock.'
  [[ ! -e /run/pulseops-deploy.lock || -f /run/pulseops-deploy.lock ]] || die 'Invalid deployment lock path.'
  exec 9>>/run/pulseops-deploy.lock
  flock -n 9 || die 'Another PulseOps deployment is running.'
}

validate_release_id() {
  [[ $1 =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || die 'Release ID must be 1-64 letters/digits/._- and start with a letter or digit.'
}

assert_plain_directory() {
  local path=$1
  [[ ! -L "$path" ]] || die "Refusing symlinked directory: $path"
  [[ ! -e "$path" || -d "$path" ]] || die "Expected a directory: $path"
  [[ $(realpath -m -- "$path") == "$path" ]] || die "Unexpected resolved directory: $path"
}

validate_layout_paths() {
  local path
  for path in "$PULSE_BASE" "$PULSE_RELEASES" "$PULSE_BACKUPS" "$PULSE_ENV_DIR" "$PULSE_STATE"; do
    assert_plain_directory "$path"
  done
  [[ ! -L "$PULSE_ENV_FILE" && ! -L "$PULSE_UNIT" ]] || die 'Refusing a symlinked environment or service file.'
}

env_value() {
  # This is a constrained KEY=value format, deliberately not shell evaluation.
  awk -v wanted="$2" 'index($0,wanted "=")==1 {print substr($0,length(wanted)+2); exit}' "$1"
}

validate_environment() {
  local file=$1 key value address
  [[ -f "$file" && ! -L "$file" ]] || die 'Environment must be an existing regular, non-symlinked file.'
  ! grep -q $'\r' "$file" || die 'Environment file must use LF line endings.'
  for key in DB_URL DB_USERNAME DB_PASSWORD PULSE_ADMIN_USERNAME PULSE_ADMIN_PASSWORD SERVER_ADDRESS SERVER_PORT PULSE_SECURE_COOKIE; do
    [[ $(grep -c "^${key}=" "$file") -eq 1 ]] || die "Environment must contain exactly one $key assignment."
    value=$(env_value "$file" "$key")
    [[ -n "$value" ]] || die "$key must not be empty."
    [[ "$value" != *"'"* && "$value" != *'"'* && "$value" != *' '* && "$value" != *$'\t'* && "$value" != *'\'* ]] || die "$key must use a single unquoted value without whitespace/backslashes."
  done
  [[ $(env_value "$file" DB_URL) == jdbc:mysql://* ]] || die 'Production install requires an explicit MySQL JDBC URL.'
  value=$(env_value "$file" PULSE_ADMIN_PASSWORD)
  [[ ${#value} -ge 12 ]] || die 'Administrator password must contain at least 12 characters.'
  [[ $(env_value "$file" SERVER_PORT) == 18080 ]] || die 'This host template uses dedicated port 18080.'
  [[ $(env_value "$file" PULSE_SECURE_COOKIE) == true ]] || die 'Production Secure Cookie must be true.'
  address=$(env_value "$file" SERVER_ADDRESS)
  [[ "$address" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ && "$address" != 0.0.0.0 ]] || die 'Use a specific host IPv4 address, never wildcard 0.0.0.0, for systemd.'
  awk -F '[.]' '{for(i=1;i<=4;i++) if($i>255 || (length($i)>1 && substr($i,1,1)=="0")) exit 1}' <<< "$address" || die 'Invalid IPv4 address.'
}

resolve_release_link() {
  local name=$1 resolved
  [[ "$name" == current || "$name" == previous ]] || die 'Unexpected release link.'
  [[ -L "$PULSE_BASE/$name" ]] || die "Missing managed link: $name"
  resolved=$(readlink -f -- "$PULSE_BASE/$name")
  [[ "$resolved" == "$PULSE_RELEASES/"* && -f "$resolved/app.jar" ]] || die "Invalid $name release target."
  [[ $(dirname -- "$resolved") == "$PULSE_RELEASES" ]] || die 'Release must be directly inside the managed releases directory.'
  printf '%s\n' "$resolved"
}

require_installed() {
  validate_layout_paths
  [[ -f "$PULSE_UNIT" && -f "$PULSE_ENV_FILE" && -d "$PULSE_RELEASES" && -d "$PULSE_BACKUPS" ]] || die 'Managed PulseOps installation not found.'
  grep -q '^# PulseOps managed systemd service$' "$PULSE_UNIT" || die 'Existing unit is not the managed PulseOps template.'
  validate_environment "$PULSE_ENV_FILE"
  resolve_release_link current >/dev/null
}

validate_artifact_sources() {
  local jar_source=$1 web_source=${2:-}
  [[ -f "$jar_source" && -s "$jar_source" ]] || die 'JAR source is missing or empty.'
  if [[ -n "$web_source" ]]; then
    [[ -f "$web_source/index.html" && ! -L "$web_source" ]] || die 'Frontend source must be a build directory containing index.html.'
    [[ -z $(find "$web_source" -type l -print -quit) ]] || die 'Frontend build must not contain symbolic links.'
  fi
}

stage_release() {
  local release_id=$1 jar_source=$2 web_source=${3:-} destination
  validate_release_id "$release_id"
  validate_artifact_sources "$jar_source" "$web_source"
  destination="$PULSE_RELEASES/$release_id"
  [[ ! -e "$destination" && ! -L "$destination" ]] || die 'Release already exists; use a new immutable release ID.'
  install -d -m 0755 "$destination"
  install -m 0444 -- "$jar_source" "$destination/app.jar"
  if [[ -n "$web_source" ]]; then
    install -d -m 0755 "$destination/frontend"
    cp -a -- "$web_source/." "$destination/frontend/"
    find "$destination/frontend" -type d -exec chmod 0755 {} +
    find "$destination/frontend" -type f -exec chmod 0444 {} +
  fi
  (cd "$destination" && sha256sum app.jar > SHA256SUMS)
  chmod 0444 "$destination/SHA256SUMS"
  printf '%s\n' "$destination"
}

switch_link() {
  local name=$1 target=$2 temporary
  [[ "$name" == current || "$name" == previous ]] || die 'Unexpected release link.'
  [[ $(dirname -- "$target") == "$PULSE_RELEASES" && -f "$target/app.jar" && ! -L "$target" ]] || die 'Refusing release outside managed directory.'
  [[ ! -e "$PULSE_BASE/$name" || -L "$PULSE_BASE/$name" ]] || die 'Refusing to replace a non-symlink current/previous path.'
  temporary="$PULSE_BASE/.${name}.$$.tmp"
  [[ ! -e "$temporary" && ! -L "$temporary" ]] || die 'Temporary release link already exists.'
  ln -s -- "$target" "$temporary"
  mv -Tf -- "$temporary" "$PULSE_BASE/$name"
}

backup_runtime() {
  local destination="$PULSE_BACKUPS/$(date -u +%Y%m%dT%H%M%SZ)-$$"
  [[ ! -e "$destination" ]] || die 'Backup directory already exists.'
  install -d -m 0700 "$destination"
  install -m 0600 "$PULSE_ENV_FILE" "$destination/pulseops.env"
  install -m 0600 "$PULSE_UNIT" "$destination/pulseops.service"
  readlink -- "$PULSE_BASE/current" > "$destination/current.txt"
  say "Configuration backup saved under $destination (root-only)."
}

wait_for_health() {
  local address attempt body
  address=$(env_value "$PULSE_ENV_FILE" SERVER_ADDRESS)
  for attempt in {1..40}; do
    if systemctl is-active --quiet pulseops.service \
        && body=$(curl --noproxy '*' --fail --silent --max-time 3 "http://${address}:18080/actuator/health") \
        && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$body"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

activate_with_fallback() {
  local target=$1 old=$2
  [[ -f "$target/SHA256SUMS" && -f "$old/SHA256SUMS" ]] || die 'Release checksum file is missing.'
  (cd "$target" && sha256sum --check --status SHA256SUMS) || die 'Target release checksum verification failed.'
  (cd "$old" && sha256sum --check --status SHA256SUMS) || die 'Current release checksum verification failed.'
  switch_link current "$target"
  if systemctl restart pulseops.service && wait_for_health; then
    switch_link previous "$old"
    say "Healthy release: $(basename -- "$target")"
    return 0
  fi
  say 'New release failed health check; restoring the previous application release.'
  switch_link current "$old"
  if systemctl restart pulseops.service && wait_for_health; then
    die 'Update failed; prior application release is running. Database migrations were not reversed.'
  fi
  die 'Update and application fallback both failed. Inspect journalctl -u pulseops; database/configuration may need intervention.'
}
