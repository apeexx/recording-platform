#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
APP_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
ENV_FILE="$APP_ROOT/.env"
APP_NAME="recording-platform-backend"
PM2_CONFIG="$SCRIPT_DIR/pm2-production.config.cjs"
MAVEN_SETTINGS="$SCRIPT_DIR/maven-settings-production.xml"
WEB_DIR="$APP_ROOT/apps/web"
BACKEND_DIR="$APP_ROOT/backend"
MINIPROGRAM_DIR="$APP_ROOT/apps/miniprogram"
WEB_INDEX="$APP_ROOT/apps/web/dist/index.html"
BACKEND_JAR="$BACKEND_DIR/target/recording-platform-backend-0.0.1-SNAPSHOT.jar"
NPM_REGISTRY="${NPM_REGISTRY:-https://registry.npmmirror.com/}"
MAVEN_MIRROR="${MVNW_REPOURL:-https://maven.aliyun.com/repository/public}"
HEALTH_URL="http://127.0.0.1:8080/api/health/ready"

log() {
  printf '[deploy] %s\n' "$*"
}

die() {
  printf '[deploy] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"
}

require_env_value() {
  local key="$1"
  grep -qE "^${key}=.+" "$ENV_FILE" || die "Missing required .env key: $key"
}

check_preflight() {
  local repository_root
  local branch
  local worktree_status
  local env_mode
  local java_version

  repository_root="$(git rev-parse --show-toplevel)"
  [[ "$repository_root" == "$APP_ROOT" ]] || die "Unexpected Git repository root"

  branch="$(git branch --show-current)"
  [[ "$branch" == "main" ]] || die "Production deployment requires branch main"

  worktree_status="$(git status --porcelain --untracked-files=normal)"
  [[ -z "$worktree_status" ]] || die "Git worktree is not clean"

  [[ -f "$ENV_FILE" && ! -L "$ENV_FILE" ]] || die "Root .env must be a regular file"
  env_mode="$(stat -c '%a' "$ENV_FILE")"
  (( (8#$env_mode & 077) == 0 )) || die "Root .env permissions must not allow group or other access"

  for key in \
    MONGODB_URI \
    RECORDING_STORAGE_DIR \
    AVATAR_STORAGE_DIR \
    VOICE_GENERATION_STORAGE_DIR \
    WECHAT_APP_ID \
    WECHAT_APP_SECRET \
    RECORDING_INTEGRATION_API_KEY_SHA256
  do
    require_env_value "$key"
  done

  grep -qx 'WEB_SESSION_COOKIE_SECURE=true' "$ENV_FILE" \
    || die "WEB_SESSION_COOKIE_SECURE must be true"
  grep -qx 'RECORDING_PATH_MIGRATION_ENABLED=false' "$ENV_FILE" \
    || die "RECORDING_PATH_MIGRATION_ENABLED must be false"
  grep -qx 'SERVER_ADDRESS=127.0.0.1' "$ENV_FILE" \
    || die "SERVER_ADDRESS must be 127.0.0.1"
  grep -qx 'SERVER_PORT=8080' "$ENV_FILE" \
    || die "SERVER_PORT must be 8080"
  grep -qE '^RECORDING_INTEGRATION_API_KEY_SHA256=[0-9A-Fa-f]{64}$' "$ENV_FILE" \
    || die "RECORDING_INTEGRATION_API_KEY_SHA256 must be 64 hexadecimal characters"

  java_version="$(java -version 2>&1 | head -n 1)"
  [[ "$java_version" =~ version\ \"17\. ]] || die "Java 17 is required"

  [[ -d /var/log/recording-platform && -w /var/log/recording-platform ]] \
    || die "/var/log/recording-platform must exist and be writable"
  [[ -x "$BACKEND_DIR/mvnw" ]] || die "Backend Maven Wrapper is not executable"
  [[ -f "$PM2_CONFIG" ]] || die "PM2 production configuration is missing"
  [[ -f "$MAVEN_SETTINGS" ]] || die "Maven production settings are missing"
}

activate_node() {
  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  [[ -s "$NVM_DIR/nvm.sh" ]] || die "NVM is not installed"
  # shellcheck disable=SC1090
  . "$NVM_DIR/nvm.sh"
  nvm use 22
  [[ "$(node -p "process.versions.node.split('.')[0]")" == "22" ]] \
    || die "Node.js 22 is required"
}

validate_arguments() {
  if (( $# > 1 )); then
    die "Usage: ./scripts/deploy-production.sh"
  fi
  if (( $# == 1 )) && [[ "$1" != "--after-pull" ]]; then
    die "Usage: ./scripts/deploy-production.sh"
  fi
}

build_and_test() {
  log "Testing and building Web"
  cd "$WEB_DIR"
  npm ci --registry="$NPM_REGISTRY" --no-audit --no-fund
  npm test -- --run
  npm run build
  [[ -s "$WEB_INDEX" ]] || die "Web build is missing"

  log "Testing and packaging backend"
  cd "$BACKEND_DIR"
  MVNW_REPOURL="$MAVEN_MIRROR" \
    ./mvnw -s "$MAVEN_SETTINGS" --batch-mode clean package
  [[ -s "$BACKEND_JAR" ]] || die "Backend JAR is missing"

  log "Testing mini-program"
  cd "$MINIPROGRAM_DIR"
  npm ci --registry="$NPM_REGISTRY" --no-audit --no-fund
  npm test
}

start_backend() {
  log "Starting or restarting the PM2 backend"
  if pm2 describe "$APP_NAME" >/dev/null 2>&1; then
    pm2 restart "$PM2_CONFIG" --only "$APP_NAME" --update-env
  else
    pm2 start "$PM2_CONFIG" --only "$APP_NAME"
  fi
}

health_is_up() {
  local health_response="$1"
  HEALTH_RESPONSE="$health_response" node -e '
    const value = JSON.parse(process.env.HEALTH_RESPONSE)
    if (
      value.overall !== "UP" ||
      value.mongo !== "UP" ||
      value.storage !== "UP"
    ) {
      process.exit(1)
    }
  '
}

wait_for_readiness() {
  local attempt
  local health_response

  for attempt in {1..12}; do
    health_response="$(curl --fail --silent --show-error "$HEALTH_URL" 2>/dev/null || true)"
    if [[ -n "$health_response" ]] && health_is_up "$health_response"; then
      pm2 save
      log "Deployment completed successfully"
      return 0
    fi
    log "Waiting for backend readiness (${attempt}/12)"
    sleep 5
  done

  pm2 logs "$APP_NAME" --lines 80 --nostream || true
  die "Backend readiness did not become fully UP within 60 seconds"
}

main() {
  validate_arguments "$@"
  cd "$APP_ROOT"

  require_command git
  require_command java
  require_command curl
  require_command stat
  activate_node
  require_command npm
  require_command pm2

  check_preflight

  if [[ "${1:-}" != "--after-pull" ]]; then
    log "Fetching and fast-forwarding origin/main"
    git fetch origin main
    git pull --ff-only origin main
    exec "$SCRIPT_DIR/deploy-production.sh" --after-pull
  fi

  build_and_test
  start_backend
  wait_for_readiness
}

main "$@"
