#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly PROJECT_ROOT="/root/autodl-tmp/auralink"
readonly STATE_TOOL="${PROJECT_ROOT}/backend/scripts/round81_provider_state.py"
readonly SERVICE_ROOT="/root/auralink_provider_validation_runs/vmm-service"
readonly PID_FILE="${SERVICE_ROOT}/service.pid"
readonly START_FILE="${SERVICE_ROOT}/service.starttime"
readonly LOG_FILE="${SERVICE_ROOT}/service.log"
readonly ATTEMPT_ROOT="${SERVICE_ROOT}/attempts"
starting_pid=''
start_complete=0
attempt_directory=''
attempt_lifecycle=''
attempt_child_log=''

prepare_attempt_evidence() {
  [[ -d "$ATTEMPT_ROOT" || ! -e "$ATTEMPT_ROOT" ]] || fail 'VMM_ATTEMPT_ROOT_INVALID'
  if [[ ! -d "$ATTEMPT_ROOT" ]]; then
    install -d -m 0700 "$ATTEMPT_ROOT"
  fi
  [[ ! -L "$ATTEMPT_ROOT" ]] || fail 'VMM_ATTEMPT_ROOT_INVALID'
  chmod 0700 "$ATTEMPT_ROOT"
  attempt_directory="$(mktemp -d "${ATTEMPT_ROOT}/attempt-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")"
  chmod 0700 "$attempt_directory"
  attempt_lifecycle="${attempt_directory}/lifecycle.log"
  attempt_child_log="${attempt_directory}/child.combined.log"
  : > "$attempt_lifecycle"
  : > "$attempt_child_log"
  chmod 0600 "$attempt_lifecycle" "$attempt_child_log"
  printf 'event=LAUNCH_PREPARED cwd=%s state_tool=%s\n' "$PROJECT_ROOT" "$STATE_TOOL" >> "$attempt_lifecycle"
}

persist_child_log() {
  [[ -n "$attempt_child_log" && -f "$attempt_child_log" && ! -L "$attempt_child_log" ]] || return 0
  if [[ -f "$LOG_FILE" && ! -L "$LOG_FILE" ]]; then
    if cat -- "$LOG_FILE" > "$attempt_child_log"; then
      chmod 0600 "$attempt_child_log"
      printf 'child_log_capture=COMPLETE\n' >> "$attempt_lifecycle"
    else
      printf 'child_log_capture=FAILED\n' >> "$attempt_lifecycle"
    fi
  else
    printf 'child_log_capture=SOURCE_MISSING\n' >> "$attempt_lifecycle"
  fi
}

record_child_termination() {
  local event="$1"
  local cleanup_signal="$2"
  local child_status=0
  local child_signal='NONE'
  [[ -n "$starting_pid" ]] || return 0
  if wait "$starting_pid"; then
    child_status=0
  else
    child_status=$?
  fi
  if (( child_status >= 128 )); then
    child_signal="$((child_status - 128))"
  fi
  persist_child_log
  if [[ -n "$attempt_lifecycle" && -f "$attempt_lifecycle" && ! -L "$attempt_lifecycle" ]]; then
    printf 'event=%s child_pid=%s child_exit_status=%s child_signal=%s cleanup_signal=%s\n' \
      "$event" "$starting_pid" "$child_status" "$child_signal" "$cleanup_signal" >> "$attempt_lifecycle"
  fi
  starting_pid=''
}

clear_current_state() {
  rm -f -- "$PID_FILE" "$START_FILE" "$LOG_FILE"
}

cleanup_incomplete_start() {
  local cleanup_signal='NONE'
  local had_incomplete_start=0
  local incomplete_pid=''
  if [[ -n "$starting_pid" && $start_complete -eq 0 ]]; then
    had_incomplete_start=1
    incomplete_pid="$starting_pid"
  fi
  if (( had_incomplete_start == 1 )) && kill -0 "$starting_pid" 2>/dev/null; then
    kill -TERM "$starting_pid" 2>/dev/null || true
    cleanup_signal='TERM'
    local attempt
    for attempt in $(seq 1 15); do
      kill -0 "$starting_pid" 2>/dev/null || break
      sleep 1
    done
    if kill -0 "$starting_pid" 2>/dev/null; then
      kill -KILL "$starting_pid" 2>/dev/null || true
      cleanup_signal='KILL'
    fi
  fi
  if (( had_incomplete_start == 1 )); then
    record_child_termination 'CLEANUP' "$cleanup_signal"
    if kill -0 "$incomplete_pid" 2>/dev/null || listener_present; then
      printf 'ROUND81_VMM_LAUNCHER_ERROR=VMM_FAILURE_CLEANUP_INCOMPLETE\n' >&2
    else
      clear_current_state
    fi
  fi
}
trap cleanup_incomplete_start EXIT INT TERM

fail() {
  printf 'ROUND81_VMM_LAUNCHER_ERROR=%s\n' "$1" >&2
  exit 2
}

verify_root() {
  [[ "$(pwd -P)" == "$PROJECT_ROOT" && ! -L "$PROJECT_ROOT" ]] || fail 'SERVER_LOCAL_ROOT_REQUIRED'
  local fs_type
  fs_type="$(findmnt -n -o FSTYPE -T "$PROJECT_ROOT" 2>/dev/null || true)"
  [[ -n "$fs_type" && "${fs_type,,}" != *fuse* && "${fs_type,,}" != *sshfs* ]] \
    || fail 'SSHFS_EXECUTION_REFUSED'
  [[ "${AURALINK_ROUND81_EXPECTED_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]] \
    || fail 'EXPECTED_COMMIT_REQUIRED'
  [[ "$(git -C "$PROJECT_ROOT" rev-parse HEAD)" == "$AURALINK_ROUND81_EXPECTED_COMMIT" ]] \
    || fail 'REVIEWED_COMMIT_MISMATCH'
  [[ -z "$(git -C "$PROJECT_ROOT" status --porcelain=v1 --untracked-files=normal)" ]] \
    || fail 'WORKTREE_NOT_CLEAN'
}

listener_present() {
  ss -ltnH 2>/dev/null | awk '$4 ~ /:5001$/ { found=1 } END { exit !found }'
}

owned_pid() {
  [[ -f "$PID_FILE" && -f "$START_FILE" && ! -L "$PID_FILE" && ! -L "$START_FILE" ]] || return 1
  local pid expected_start actual_start
  pid="$(<"$PID_FILE")"
  expected_start="$(<"$START_FILE")"
  [[ "$pid" =~ ^[1-9][0-9]*$ && -r "/proc/${pid}/stat" ]] || return 1
  actual_start="$(awk '{print $22}' "/proc/${pid}/stat")"
  [[ "$actual_start" == "$expected_start" ]] || return 1
  local command_line
  command_line="$(tr '\0' ' ' < "/proc/${pid}/cmdline")"
  [[ "$command_line" == *'/micromamba/envs/auralink-ai/bin/python'* \
    && "$command_line" == *'/VMM/app.py'* ]] || return 1
  printf '%s' "$pid"
}

prepare_private_state() {
  if [[ ! -d /root/auralink_provider_validation_runs ]]; then
    install -d -m 0700 /root/auralink_provider_validation_runs
  fi
  [[ ! -L /root/auralink_provider_validation_runs ]] || fail 'PRIVATE_RUN_ROOT_INVALID'
  chmod 0700 /root/auralink_provider_validation_runs
  if [[ ! -d "$SERVICE_ROOT" ]]; then
    install -d -m 0700 "$SERVICE_ROOT"
  fi
  [[ ! -L "$SERVICE_ROOT" ]] || fail 'VMM_SERVICE_STATE_INVALID'
  chmod 0700 "$SERVICE_ROOT"
  [[ -d "$ATTEMPT_ROOT" || ! -e "$ATTEMPT_ROOT" ]] || fail 'VMM_ATTEMPT_ROOT_INVALID'
  if [[ ! -d "$ATTEMPT_ROOT" ]]; then
    install -d -m 0700 "$ATTEMPT_ROOT"
  fi
  [[ ! -L "$ATTEMPT_ROOT" ]] || fail 'VMM_ATTEMPT_ROOT_INVALID'
  chmod 0700 "$ATTEMPT_ROOT"
}

preflight() {
  verify_root
  local result
  result="$("$STATE_TOOL" vmm-static-preflight --project-root "$PROJECT_ROOT")"
  printf '%s\n' "$result"
  [[ "$result" == *'"state":"VMM_STATIC_PREFLIGHT_READY"'* ]] \
    || fail 'VMM_STATIC_PREFLIGHT_BLOCKED'
  listener_present && fail 'VMM_PORT_OCCUPIED'
  printf 'VMM_STATIC_PREFLIGHT_READY\n'
}

start_service() {
  preflight
  [[ "${AURALINK_ROUND81_VMM_ACTION:-}" == 'START_OWNED_ROUND81_VMM' ]] \
    || fail 'VMM_START_CONFIRMATION_REQUIRED'
  prepare_private_state
  owned_pid >/dev/null && fail 'VMM_SERVICE_ALREADY_OWNED'
  prepare_attempt_evidence
  : > "$LOG_FILE"
  chmod 0600 "$LOG_FILE"
  nohup "$STATE_TOOL" exec-vmm --project-root "$PROJECT_ROOT" >>"$LOG_FILE" 2>&1 &
  local pid=$!
  local child_starttime=''
  starting_pid="$pid"
  printf '%s\n' "$pid" > "$PID_FILE"
  if child_starttime="$(awk '{print $22}' "/proc/${pid}/stat" 2>/dev/null)" && [[ "$child_starttime" =~ ^[1-9][0-9]*$ ]]; then
    printf '%s\n' "$child_starttime" > "$START_FILE"
  else
    printf 'event=CHILD_METADATA_UNAVAILABLE child_pid=%s\n' "$pid" >> "$attempt_lifecycle"
    record_child_termination 'CHILD_METADATA_UNAVAILABLE' 'NONE'
    clear_current_state
    fail 'VMM_SERVICE_START_FAILED'
  fi
  chmod 0600 "$PID_FILE" "$START_FILE"
  printf 'event=CHILD_STARTED child_pid=%s child_starttime=%s\n' "$pid" "$child_starttime" >> "$attempt_lifecycle"
  local attempt health
  for attempt in $(seq 1 450); do
    if ! kill -0 "$pid" 2>/dev/null; then
      record_child_termination 'LIVENESS_LOST' 'NONE'
      clear_current_state
      fail 'VMM_SERVICE_START_FAILED'
    fi
    if listener_present; then
      health="$(curl --noproxy '*' --silent --show-error --max-time 3 http://127.0.0.1:5001/health 2>/dev/null || true)"
      if [[ "$health" == *'"status":"ok"'* && "$health" == *'"model_ready":true'* ]]; then
        printf 'event=SERVICE_READY child_pid=%s\n' "$pid" >> "$attempt_lifecycle"
        printf 'VMM_OWNED_SERVICE_READY\n'
        start_complete=1
        return
      fi
    fi
    sleep 2
  done
  fail 'VMM_MODEL_INITIALIZATION_TIMEOUT'
}

stop_service() {
  verify_root
  [[ "${AURALINK_ROUND81_VMM_ACTION:-}" == 'STOP_OWNED_ROUND81_VMM' ]] \
    || fail 'VMM_STOP_CONFIRMATION_REQUIRED'
  local pid
  pid="$(owned_pid)" || fail 'VMM_OWNED_PROCESS_NOT_FOUND'
  kill -TERM "$pid"
  local attempt
  for attempt in $(seq 1 30); do
    if ! kill -0 "$pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -KILL "$pid"
  fi
  rm -f -- "$PID_FILE" "$START_FILE"
  rm -f -- "$LOG_FILE"
  for attempt in $(seq 1 10); do
    listener_present || break
    sleep 1
  done
  listener_present && fail 'VMM_PORT_NOT_RELEASED'
  printf 'VMM_OWNED_SERVICE_STOPPED\n'
}

status_service() {
  verify_root
  if owned_pid >/dev/null && listener_present; then
    printf 'VMM_OWNED_SERVICE_RUNNING\n'
    return
  fi
  printf 'VMM_OWNED_SERVICE_STOPPED\n'
}

case "${1:-}" in
  preflight) preflight ;;
  start) start_service ;;
  stop) stop_service ;;
  status) status_service ;;
  *) printf 'Usage: start-vmm-service.sh {preflight|start|stop|status}\n' >&2; exit 64 ;;
esac
