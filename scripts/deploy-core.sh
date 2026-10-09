#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'
umask 077

SOURCE=/root/autodl-tmp/auralink-core-api-current
DEPLOY_ROOT=/root/autodl-tmp/auralink-core-deploy
HANDOFF=/root/autodl-tmp/artlive-r25-native-pg16/handoff-production-20260923T062101Z
PRODUCTION_PREVIEW=/root/autodl-tmp/artlive/backend-preview/r25-isolated-preview-7694f4069c83c06c
PREVIEW_BASE=/root/autodl-tmp/artlive/backend-preview

PYTHON=/root/miniconda3/bin/python3
MAVEN=/usr/bin/mvn
LAUNCHER="$HANDOFF/operations/r25-production-java-launch.py"
APP_ENV="$HANDOFF/runtime/config/app.env"
SOURCE_ENV="$HANDOFF/runtime/config/imported-source.env"
APP_CONFIG="$HANDOFF/runtime/config/application.yml"

PRODUCTION_RELEASE="$HANDOFF/release"
PRODUCTION_JAR="$PRODUCTION_RELEASE/artifacts/backend.jar"
PRODUCTION_PREVIEW_JAR="$PRODUCTION_PREVIEW/release/backend.jar"
PRODUCTION_PID_FILE="$HANDOFF/runtime/app-pids/java.pid"
PRODUCTION_LOG="$HANDOFF/runtime/app-logs/java.log"

PRODUCTION_PORT=19080
CANDIDATE_PORT=19180
MINIMUM_FREE_KB=4194304

READ_KEY=/root/.ssh/auralink_core_api_readonly_ed25519
KNOWN_HOSTS=/root/.ssh/auralink_core_api_known_hosts
GIT_SSH_COMMAND="ssh -i $READ_KEY -o IdentitiesOnly=yes -o UserKnownHostsFile=$KNOWN_HOSTS -o StrictHostKeyChecking=yes"
export GIT_SSH_COMMAND

MODE="${1:-}"
REF="${2:-}"

SHA=''
SHORT_SHA=''
RELEASE_ROOT=''
PREVIEW_ROOT=''
CANDIDATE_HANDOFF=''
STAGED_JAR=''
CANDIDATE_PID=''
OLD_PID=''
NEW_PID=''
BACKUP_DIR=''
PRODUCTION_STOPPED=0
PRODUCTION_REPLACED=0
DEPLOY_SUCCEEDED=0

fail() {
  printf 'DEPLOY_CORE=FAIL reason=%s\n' "$1" >&2
  exit "${2:-1}"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "command_missing_$1"
}

validate_managed_path() {
  local path="$1"
  case "$path" in
    "$DEPLOY_ROOT"/*) ;;
    *) fail "managed_path_outside_deploy_root" ;;
  esac
}

listener_pid() {
  local port="$1"
  ss -lntp | sed -nE "s/.*:${port} .*pid=([0-9]+).*/\\1/p" | head -1
}

process_ticks() {
  local pid="$1"
  awk '{print $22}' "/proc/$pid/stat"
}

healthy() {
  local port="$1"
  curl -fsS --max-time 10 "http://127.0.0.1:${port}/health" >/dev/null
}

paintings_healthy() {
  local port="$1"
  curl -fsS --max-time 15 \
    "http://127.0.0.1:${port}/api/v1/paintings?page=0&size=1" >/dev/null
}

verify_java_identity() {
  local pid="$1"
  local port="$2"
  local preview_root="$3"
  local cmdline cwd uid sid

  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  [[ -r "/proc/$pid/cmdline" ]] || return 1

  cmdline="$(tr '\0' ' ' < "/proc/$pid/cmdline")"
  cwd="$(readlink -f "/proc/$pid/cwd")"
  uid="$(stat -c '%u' "/proc/$pid")"
  sid="$(ps -o sid= -p "$pid" | tr -d ' ')"

  [[ "$uid" == "0" ]]
  [[ "$cwd" == "/root" ]]
  [[ "$sid" == "$pid" ]]
  [[ "$cmdline" == *'/usr/lib/jvm/java-17-openjdk-amd64/bin/java'* ]]
  [[ "$cmdline" == *"$preview_root/release/backend.jar"* ]]
  [[ "$cmdline" == *"--server.port=$port"* ]]
}

write_pid_record() {
  local pid="$1"
  local ticks temporary
  ticks="$(process_ticks "$pid")"
  temporary="${PRODUCTION_PID_FILE}.new.$$"
  printf 'pid=%s\nstart_ticks=%s\n' "$pid" "$ticks" > "$temporary"
  chmod 600 "$temporary"
  mv -f -- "$temporary" "$PRODUCTION_PID_FILE"
}

start_java() {
  local mode="$1"
  local release_root="$2"
  local preview_root="$3"
  local port="$4"
  local log="$5"

  touch "$log"
  chmod 600 "$log"

  "$PYTHON" - \
    "$PYTHON" "$LAUNCHER" "$mode" \
    "$APP_ENV" "$SOURCE_ENV" "$APP_CONFIG" \
    "$release_root" "$preview_root" "$port" "$log" <<'PY'
import subprocess
import sys

*command, log_path = sys.argv[1:]
with open(log_path, "ab", buffering=0) as output:
    process = subprocess.Popen(
        command,
        stdin=subprocess.DEVNULL,
        stdout=output,
        stderr=subprocess.STDOUT,
        start_new_session=True,
        close_fds=True,
        cwd="/root",
    )
print(process.pid)
PY
}

wait_ready() {
  local pid="$1"
  local port="$2"
  local preview_root="$3"
  local attempt owner

  for attempt in $(seq 1 90); do
    kill -0 "$pid" 2>/dev/null || return 1
    if healthy "$port" && paintings_healthy "$port"; then
      owner="$(listener_pid "$port")"
      [[ "$owner" == "$pid" ]] || return 1
      verify_java_identity "$pid" "$port" "$preview_root" || return 1
      return 0
    fi
    sleep 1
  done
  return 1
}

stop_owned_java() {
  local pid="$1"
  local port="$2"
  local preview_root="$3"
  local attempt

  verify_java_identity "$pid" "$port" "$preview_root" || return 1

  kill "$pid"
  for attempt in $(seq 1 300); do
    kill -0 "$pid" 2>/dev/null || break
    sleep .1
  done

  kill -0 "$pid" 2>/dev/null && return 1
  [[ -z "$(listener_pid "$port")" ]] || return 1
}

same_hash() {
  [[ "$(sha256sum "$1" | awk '{print $1}')" == \
     "$(sha256sum "$2" | awk '{print $1}')" ]]
}

link_or_copy() {
  local source="$1"
  local target="$2"
  if ! ln -- "$source" "$target" 2>/dev/null; then
    cp --reflink=auto -- "$source" "$target"
  fi
  chmod 600 "$target"
}

atomic_from_source() {
  local source="$1"
  local target="$2"
  local temporary="${target}.new.$$"
  link_or_copy "$source" "$temporary"
  mv -f -- "$temporary" "$target"
}

resolve_ref() {
  git -C "$SOURCE" fetch --no-tags origin "$REF"
  SHA="$(git -C "$SOURCE" rev-parse --verify 'FETCH_HEAD^{commit}')"
  [[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || fail "invalid_commit"
  SHORT_SHA="${SHA:0:12}"
  CANDIDATE_HANDOFF="$DEPLOY_ROOT/handoff-production-$SHORT_SHA"
  RELEASE_ROOT="$CANDIDATE_HANDOFF/release"
  PREVIEW_ROOT="$PREVIEW_BASE/r25-isolated-preview-$(
    printf '%s' "$CANDIDATE_HANDOFF" | sha256sum | awk '{print substr($1, 1, 16)}'
  )"
  STAGED_JAR="$RELEASE_ROOT/artifacts/backend.jar"
}

preflight() {
  [[ "$MODE" == "dry-run" || "$MODE" == "stage" || "$MODE" == "deploy" ]] ||
    fail "usage: deploy-core.sh dry-run|stage|deploy COMMIT" 64
  [[ -n "$REF" ]] || fail "commit_required" 64

  for command in git curl ss sha256sum awk sed ps stat readlink flock mv ln cp jar; do
    require_command "$command"
  done

  [[ -d "$SOURCE/.git" ]] || fail "source_repository_missing"
  [[ -x "$PYTHON" ]] || fail "python_missing"
  [[ -x "$MAVEN" ]] || fail "maven_missing"
  [[ -f "$LAUNCHER" ]] || fail "launcher_missing"
  [[ -f "$READ_KEY" ]] || fail "readonly_key_missing"
  [[ -f "$KNOWN_HOSTS" ]] || fail "known_hosts_missing"

  for path in "$APP_ENV" "$SOURCE_ENV" "$APP_CONFIG" \
    "$PRODUCTION_JAR" "$PRODUCTION_PREVIEW_JAR" "$PRODUCTION_PID_FILE"; do
    [[ -f "$path" ]] || fail "required_file_missing"
  done

  install -d -m 700 \
    "$DEPLOY_ROOT" "$DEPLOY_ROOT/bin" "$DEPLOY_ROOT/build" \
    "$DEPLOY_ROOT/releases" \
    "$DEPLOY_ROOT/logs" "$DEPLOY_ROOT/backups" "$DEPLOY_ROOT/runtime"

  exec 9>"$DEPLOY_ROOT/runtime/deploy.lock"
  flock -n 9 || fail "deployment_busy"

  local free_kb production_pid recorded_pid recorded_ticks actual_ticks
  free_kb="$(df -Pk "$DEPLOY_ROOT" | awk 'NR == 2 {print $4}')"
  [[ "$free_kb" =~ ^[0-9]+$ ]] || fail "disk_check_failed"
  (( free_kb >= MINIMUM_FREE_KB )) || fail "less_than_4gb_free"

  healthy "$PRODUCTION_PORT" || fail "production_unhealthy"
  paintings_healthy "$PRODUCTION_PORT" || fail "production_paintings_unhealthy"
  [[ -z "$(listener_pid "$CANDIDATE_PORT")" ]] || fail "candidate_port_in_use"

  production_pid="$(listener_pid "$PRODUCTION_PORT")"
  verify_java_identity "$production_pid" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW" ||
    fail "production_identity_invalid"

  recorded_pid="$(sed -n 's/^pid=//p' "$PRODUCTION_PID_FILE")"
  recorded_ticks="$(sed -n 's/^start_ticks=//p' "$PRODUCTION_PID_FILE")"
  actual_ticks="$(process_ticks "$production_pid")"
  [[ "$recorded_pid" == "$production_pid" && "$recorded_ticks" == "$actual_ticks" ]] ||
    fail "production_pid_record_mismatch"

  same_hash "$PRODUCTION_JAR" "$PRODUCTION_PREVIEW_JAR" ||
    fail "production_jar_hash_mismatch"

  resolve_ref
}

build_release() {
  if [[ -f "$RELEASE_ROOT/.commit-sha" && -f "$STAGED_JAR" && \
        "$(cat "$RELEASE_ROOT/.commit-sha")" == "$SHA" ]]; then
    return 0
  fi

  [[ ! -e "$CANDIDATE_HANDOFF" ]] || fail "incomplete_candidate_handoff_exists"
  [[ ! -e "$PREVIEW_ROOT" ]] || fail "incomplete_preview_exists"

  local build_root="$DEPLOY_ROOT/build/$SHORT_SHA"
  local worktree="$build_root/source"
  validate_managed_path "$build_root"
  [[ ! -e "$build_root" ]] || fail "build_root_exists"
  install -d -m 700 "$build_root"

  git -C "$SOURCE" worktree add --detach "$worktree" "$SHA"
  (
    cd "$worktree"
    export PATH="/root/miniconda3/bin:/usr/bin:/bin"
    "$MAVEN" -B -ntp -Dmaven.test.skip=true clean package
  ) > "$DEPLOY_ROOT/logs/build-$SHORT_SHA.log" 2>&1

  local built_jar="$worktree/target/auralink-backend-0.0.1-SNAPSHOT.jar"
  [[ -f "$built_jar" ]] || fail "built_jar_missing"
  jar tf "$built_jar" | grep -qE 'BOOT-INF/lib/postgresql-[^/]+\.jar$' ||
    fail "postgresql_driver_missing"

  install -d -m 700 \
    "$CANDIDATE_HANDOFF/release/artifacts" \
    "$CANDIDATE_HANDOFF/runtime/java-home" \
    "$PREVIEW_ROOT/release" "$PREVIEW_ROOT/work"
  chmod 700 "$PREVIEW_ROOT"
  install -m 600 "$built_jar" "$STAGED_JAR"
  link_or_copy "$STAGED_JAR" "$PREVIEW_ROOT/release/backend.jar"
  same_hash "$STAGED_JAR" "$PREVIEW_ROOT/release/backend.jar" ||
    fail "staged_jar_hash_mismatch"
  printf '%s\n' "$SHA" > "$RELEASE_ROOT/.commit-sha"
  chmod 600 "$RELEASE_ROOT/.commit-sha"

  git -C "$SOURCE" worktree remove --force "$worktree"
  rmdir "$build_root" 2>/dev/null || true
}

verify_candidate() {
  local log="$DEPLOY_ROOT/logs/candidate-$SHORT_SHA.log"
  : > "$log"
  CANDIDATE_PID="$(start_java readonly "$RELEASE_ROOT" "$PREVIEW_ROOT" \
    "$CANDIDATE_PORT" "$log")"
  [[ "$CANDIDATE_PID" =~ ^[0-9]+$ ]] || fail "candidate_pid_invalid"

  if ! wait_ready "$CANDIDATE_PID" "$CANDIDATE_PORT" "$PREVIEW_ROOT"; then
    if kill -0 "$CANDIDATE_PID" 2>/dev/null &&
       verify_java_identity "$CANDIDATE_PID" "$CANDIDATE_PORT" "$PREVIEW_ROOT"; then
      stop_owned_java "$CANDIDATE_PID" "$CANDIDATE_PORT" "$PREVIEW_ROOT"
    fi
    tail -120 "$log" >&2 || true
    fail "candidate_unhealthy"
  fi

  healthy "$PRODUCTION_PORT" || fail "production_became_unhealthy"
  stop_owned_java "$CANDIDATE_PID" "$CANDIDATE_PORT" "$PREVIEW_ROOT" ||
    fail "candidate_stop_failed"
  CANDIDATE_PID=''
}

backup_production() {
  BACKUP_DIR="$DEPLOY_ROOT/backups/$(date -u +%Y%m%dT%H%M%SZ)-$SHORT_SHA"
  [[ ! -e "$BACKUP_DIR" ]] || fail "backup_dir_exists"
  install -d -m 700 "$BACKUP_DIR"
  link_or_copy "$PRODUCTION_JAR" "$BACKUP_DIR/release-backend.jar"
  link_or_copy "$PRODUCTION_PREVIEW_JAR" "$BACKUP_DIR/preview-backend.jar"
  sha256sum "$BACKUP_DIR/release-backend.jar" > "$BACKUP_DIR/SHA256SUMS"
  chmod 600 "$BACKUP_DIR/SHA256SUMS"
}

install_staged_production() {
  atomic_from_source "$STAGED_JAR" "$PRODUCTION_JAR"
  atomic_from_source "$PREVIEW_ROOT/release/backend.jar" "$PRODUCTION_PREVIEW_JAR"
  same_hash "$PRODUCTION_JAR" "$PRODUCTION_PREVIEW_JAR" ||
    fail "installed_jar_hash_mismatch"
  PRODUCTION_REPLACED=1
}

start_production_from_current_artifacts() {
  local log="$PRODUCTION_LOG"
  NEW_PID="$(start_java normal "$PRODUCTION_RELEASE" "$PRODUCTION_PREVIEW" \
    "$PRODUCTION_PORT" "$log")"
  [[ "$NEW_PID" =~ ^[0-9]+$ ]] || return 1
  wait_ready "$NEW_PID" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW" || return 1
  write_pid_record "$NEW_PID"
}

restore_old_production() {
  local rollback_pid=''
  set +e

  if [[ -n "$NEW_PID" ]] && kill -0 "$NEW_PID" 2>/dev/null &&
     verify_java_identity "$NEW_PID" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW"; then
    stop_owned_java "$NEW_PID" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW"
  fi

  if (( PRODUCTION_REPLACED == 1 )) && [[ -n "$BACKUP_DIR" ]]; then
    atomic_from_source "$BACKUP_DIR/release-backend.jar" "$PRODUCTION_JAR"
    atomic_from_source "$BACKUP_DIR/preview-backend.jar" "$PRODUCTION_PREVIEW_JAR"
  fi

  rollback_pid="$(start_java normal "$PRODUCTION_RELEASE" "$PRODUCTION_PREVIEW" \
    "$PRODUCTION_PORT" "$PRODUCTION_LOG")"
  if [[ "$rollback_pid" =~ ^[0-9]+$ ]] &&
     wait_ready "$rollback_pid" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW"; then
    write_pid_record "$rollback_pid"
    printf 'DEPLOY_CORE=ROLLBACK_OK pid=%s\n' "$rollback_pid" >&2
    set -e
    return 0
  fi

  printf 'DEPLOY_CORE=ROLLBACK_FAILED\n' >&2
  set -e
  return 1
}

activate_release() {
  OLD_PID="$(listener_pid "$PRODUCTION_PORT")"
  verify_java_identity "$OLD_PID" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW" ||
    fail "old_production_identity_invalid"

  backup_production
  stop_owned_java "$OLD_PID" "$PRODUCTION_PORT" "$PRODUCTION_PREVIEW" ||
    fail "old_production_stop_failed"
  PRODUCTION_STOPPED=1

  if ! install_staged_production || ! start_production_from_current_artifacts; then
    restore_old_production || fail "deployment_and_rollback_failed"
    fail "deployment_failed_and_rolled_back"
  fi

  healthy "$PRODUCTION_PORT" || {
    restore_old_production || fail "postcheck_and_rollback_failed"
    fail "postcheck_failed_and_rolled_back"
  }
  paintings_healthy "$PRODUCTION_PORT" || {
    restore_old_production || fail "paintings_postcheck_and_rollback_failed"
    fail "paintings_postcheck_failed_and_rolled_back"
  }

  printf '%s\n' "$SHA" > "$DEPLOY_ROOT/runtime/current-commit"
  chmod 600 "$DEPLOY_ROOT/runtime/current-commit"
  DEPLOY_SUCCEEDED=1
}

cleanup() {
  local rc=$?
  if [[ -n "$CANDIDATE_PID" ]] && kill -0 "$CANDIDATE_PID" 2>/dev/null; then
    if verify_java_identity "$CANDIDATE_PID" "$CANDIDATE_PORT" "$PREVIEW_ROOT"; then
      stop_owned_java "$CANDIDATE_PID" "$CANDIDATE_PORT" "$PREVIEW_ROOT" || true
    fi
  fi
  if (( rc != 0 )) && (( PRODUCTION_STOPPED == 1 )) && (( DEPLOY_SUCCEEDED == 0 )); then
    if [[ -z "$(listener_pid "$PRODUCTION_PORT")" ]]; then
      restore_old_production || true
    fi
  fi
  exit "$rc"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

preflight

case "$MODE" in
  dry-run)
    printf 'DEPLOY_CORE=DRY_RUN_OK\n'
    printf 'sha=%s\n' "$SHA"
    printf 'production_port=%s\n' "$PRODUCTION_PORT"
    printf 'candidate_port=%s\n' "$CANDIDATE_PORT"
    ;;
  stage)
    build_release
    verify_candidate
    printf 'DEPLOY_CORE=STAGE_OK\n'
    printf 'sha=%s\n' "$SHA"
    printf 'release=%s\n' "$RELEASE_ROOT"
    ;;
  deploy)
    build_release
    verify_candidate
    activate_release
    printf 'DEPLOY_CORE=DEPLOY_OK\n'
    printf 'sha=%s\n' "$SHA"
    printf 'pid=%s\n' "$NEW_PID"
    printf 'backup=%s\n' "$BACKUP_DIR"
    ;;
esac
