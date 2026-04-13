#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-${JACRED_BASE_URL:-https://jacred.nestify.club}}"
MODE="${2:-${JACRED_RECOVERY_MODE:-safe}}"

if [[ "${BASE_URL}" == "--full" || "${BASE_URL}" == "--safe" ]]; then
  MODE="${BASE_URL#--}"
  BASE_URL="${JACRED_BASE_URL:-https://jacred.nestify.club}"
fi

if [[ "${MODE}" == "--full" || "${MODE}" == "--safe" ]]; then
  MODE="${MODE#--}"
fi

if [[ "${MODE}" != "safe" && "${MODE}" != "full" ]]; then
  echo "Usage: $0 [base_url] [safe|full]"
  echo "Examples:"
  echo "  $0"
  echo "  $0 https://jacred.nestify.club safe"
  echo "  $0 https://jacred.nestify.club full"
  exit 1
fi

timestamp() {
  date '+%Y-%m-%d %H:%M:%S'
}

log() {
  echo "[$(timestamp)] $*"
}

call() {
  local path="$1"
  local url="${BASE_URL%/}${path}"

  log "GET ${url}"
  local tmp_body
  tmp_body="$(mktemp)"
  local http_code
  http_code="$(curl -sS -L --max-time 900 -o "${tmp_body}" -w '%{http_code}' "${url}")"
  local body
  body="$(tr '\n' ' ' < "${tmp_body}" | cut -c1-240)"
  rm -f "${tmp_body}"

  if [[ "${http_code}" != "200" ]]; then
    log "FAIL ${http_code} ${path} ${body}"
    return 1
  fi

  log "OK   ${path} ${body}"
}

run_group() {
  local group_name="$1"
  shift
  local failed=0

  log "=== ${group_name} ==="
  for path in "$@"; do
    if ! call "${path}"; then
      failed=1
    fi
    sleep 1
  done

  return "${failed}"
}

SAFE_UPDATES=(
  "/cron/rutor/UpdateTasksParse"
  "/cron/rutracker/UpdateTasksParse"
  "/cron/kinozal/UpdateTasksParse"
  "/cron/torrentby/UpdateTasksParse"
  "/cron/underverse/UpdateTasksParse"
  "/cron/nnmclub/UpdateTasksParse"
  "/cron/bitru/UpdateTasksParse"
  "/cron/selezen/UpdateTasksParse"
  "/cron/toloka/UpdateTasksParse?saveDb=true"
)

MAGNET_BACKFILL=(
  "/cron/rutracker/parseMagnet"
  "/cron/kinozal/parseMagnet"
  "/cron/bitru/parseMagnet"
  "/cron/toloka/parseMagnet"
)

LIGHT_EXTRA=(
  "/cron/anilibria/Parse"
  "/cron/eztv/Parse?page=1"
  "/cron/yts/Parse?page=1"
)

FULL_PARSE_ALL=(
  "/cron/rutor/ParseAllTask"
  "/cron/rutracker/ParseAllTask"
  "/cron/kinozal/ParseAllTask"
  "/cron/torrentby/ParseAllTask"
  "/cron/underverse/ParseAllTask"
  "/cron/nnmclub/ParseAllTask"
  "/cron/bitru/ParseAllTask"
  "/cron/selezen/ParseAllTask"
  "/cron/toloka/ParseAllTask"
)

FULL_DEV_PARSE=(
  "/cron/anidub/DevParse"
  "/cron/anifilm/DevParse"
  "/cron/animelayer/DevParse"
  "/cron/animedia/DevParse?page=1"
  "/cron/hdrezka/DevParse"
  "/cron/lostfilm/DevParse"
  "/cron/hamsterstudio/DevParse"
  "/cron/baibako/DevParse"
)

failed_groups=()

run_group "Core updateTasksParse" "${SAFE_UPDATES[@]}" || failed_groups+=("Core updateTasksParse")
run_group "Magnet backfill" "${MAGNET_BACKFILL[@]}" || failed_groups+=("Magnet backfill")
run_group "Light extra sources" "${LIGHT_EXTRA[@]}" || failed_groups+=("Light extra sources")

if [[ "${MODE}" == "full" ]]; then
  run_group "Full ParseAllTask" "${FULL_PARSE_ALL[@]}" || failed_groups+=("Full ParseAllTask")
  run_group "Full DevParse" "${FULL_DEV_PARSE[@]}" || failed_groups+=("Full DevParse")
  run_group "Second magnet backfill" "${MAGNET_BACKFILL[@]}" || failed_groups+=("Second magnet backfill")
fi

run_group "Persist DB" "/jsondb/Save" || failed_groups+=("Persist DB")

if [[ "${#failed_groups[@]}" -gt 0 ]]; then
  log "Completed with failures in groups: ${failed_groups[*]}"
  exit 2
fi

log "Recovery finished successfully."
