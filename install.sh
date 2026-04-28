#!/usr/bin/env bash
# Bootstrap bclaw from a one-line curl install.
#
# Public install entry:
#   curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash
#
# The bootstrapper downloads or updates a local copy of the repository, then
# delegates host setup to scripts/install-macos-host-agent or
# scripts/install-linux-host-agent.
set -euo pipefail

DEFAULT_REPO_URL="https://github.com/boomyao/bclaw.git"
DEFAULT_REF="main"
DEFAULT_INSTALL_DIR="${HOME}/.bclaw/bclaw"
DEFAULT_APK_URL="https://github.com/boomyao/bclaw/releases/latest/download/bclaw-android-debug.apk"

REPO_URL="${BCLAW_REPO_URL:-${DEFAULT_REPO_URL}}"
REF="${BCLAW_REF:-${DEFAULT_REF}}"
INSTALL_DIR="${BCLAW_INSTALL_DIR:-${DEFAULT_INSTALL_DIR}}"
APK_URL="${BCLAW_APK_URL:-${DEFAULT_APK_URL}}"
INSTALLER_ARGS=()

usage() {
  cat >&2 <<USAGE
usage: install.sh [options] [-- installer options]

Options:
  --dir <path>       Install/update repository copy here (default: ~/.bclaw/bclaw)
  --repo <url>       Git repository URL (default: ${DEFAULT_REPO_URL})
  --ref <ref>        Git branch/tag/commit to install (default: ${DEFAULT_REF})
  --apk-url <url>    APK URL printed after service install
  -h, --help         Show this help

Examples:
  curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash
  curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash -s -- --ref v0.0.1
  curl -fsSL https://raw.githubusercontent.com/boomyao/bclaw/main/install.sh | bash -s -- -- --port 9000

Environment overrides:
  BCLAW_INSTALL_DIR  Same as --dir
  BCLAW_REPO_URL     Same as --repo
  BCLAW_REF          Same as --ref
  BCLAW_APK_URL      Same as --apk-url
USAGE
  exit "${1:-1}"
}

log() {
  printf '[bclaw-bootstrap] %s\n' "$*" >&2
}

warn() {
  printf '[bclaw-bootstrap] warning: %s\n' "$*" >&2
}

fail() {
  printf '[bclaw-bootstrap] error: %s\n' "$*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --dir)
      [ "$#" -ge 2 ] || usage
      INSTALL_DIR="$2"
      shift 2
      ;;
    --repo)
      [ "$#" -ge 2 ] || usage
      REPO_URL="$2"
      shift 2
      ;;
    --ref)
      [ "$#" -ge 2 ] || usage
      REF="$2"
      shift 2
      ;;
    --apk-url)
      [ "$#" -ge 2 ] || usage
      APK_URL="$2"
      shift 2
      ;;
    --)
      shift
      INSTALLER_ARGS=("$@")
      break
      ;;
    -h|--help)
      usage 0
      ;;
    *)
      INSTALLER_ARGS+=("$1")
      shift
      ;;
  esac
done

script_dir_if_local() {
  local source_path
  source_path="${BASH_SOURCE[0]:-}"
  [ -n "$source_path" ] || return 1
  [ -f "$source_path" ] || return 1
  (cd "$(dirname "$source_path")" >/dev/null 2>&1 && pwd)
}

local_repo_dir() {
  local script_dir
  script_dir="$(script_dir_if_local 2>/dev/null || true)"
  if [ -n "$script_dir" ] && [ -f "${script_dir}/host-agent/package.json" ]; then
    printf '%s\n' "$script_dir"
    return 0
  fi
  return 1
}

ensure_repo_with_git() {
  mkdir -p "$(dirname "$INSTALL_DIR")"
  if [ -d "${INSTALL_DIR}/.git" ]; then
    log "Updating repository: ${INSTALL_DIR}"
    git -C "$INSTALL_DIR" fetch --tags --prune origin
  elif [ -e "$INSTALL_DIR" ]; then
    fail "${INSTALL_DIR} exists but is not a git checkout; set BCLAW_INSTALL_DIR or remove it"
  else
    log "Cloning repository: ${REPO_URL} -> ${INSTALL_DIR}"
    git clone "$REPO_URL" "$INSTALL_DIR"
  fi

  git -C "$INSTALL_DIR" checkout "$REF"
  if git -C "$INSTALL_DIR" symbolic-ref -q HEAD >/dev/null 2>&1; then
    git -C "$INSTALL_DIR" pull --ff-only origin "$REF" || true
  fi
}

repo_archive_url() {
  case "$REPO_URL" in
    https://github.com/*/*.git)
      local path
      path="${REPO_URL#https://github.com/}"
      path="${path%.git}"
      printf 'https://github.com/%s/archive/%s.tar.gz\n' "$path" "$REF"
      ;;
    https://github.com/*/*)
      local path
      path="${REPO_URL#https://github.com/}"
      printf 'https://github.com/%s/archive/%s.tar.gz\n' "$path" "$REF"
      ;;
    *)
      return 1
      ;;
  esac
}

ensure_repo_with_archive() {
  local url tmp_dir archive
  command -v curl >/dev/null 2>&1 || fail "curl is required"
  command -v tar >/dev/null 2>&1 || fail "tar is required"
  url="$(repo_archive_url)" || fail "git is missing and ${REPO_URL} is not a supported GitHub URL"
  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/bclaw.XXXXXX")"
  archive="${tmp_dir}/bclaw.tar.gz"

  log "Downloading repository archive: ${url}"
  curl -fL "$url" -o "$archive"
  mkdir -p "$(dirname "$INSTALL_DIR")"
  rm -rf "${tmp_dir}/extract"
  mkdir -p "${tmp_dir}/extract"
  tar -xzf "$archive" -C "${tmp_dir}/extract" --strip-components=1
  rm -rf "$INSTALL_DIR"
  mv "${tmp_dir}/extract" "$INSTALL_DIR"
  rm -rf "$tmp_dir"
}

ensure_repo() {
  if local_repo_dir >/dev/null 2>&1; then
    local_repo_dir
    return 0
  fi

  if command -v git >/dev/null 2>&1; then
    ensure_repo_with_git
  else
    ensure_repo_with_archive
  fi

  [ -f "${INSTALL_DIR}/host-agent/package.json" ] || fail "downloaded repository is missing host-agent/package.json"
  printf '%s\n' "$INSTALL_DIR"
}

run_platform_installer() {
  local repo_dir os installer
  repo_dir="$1"
  os="$(uname -s)"
  case "$os" in
    Darwin)
      installer="${repo_dir}/scripts/install-macos-host-agent"
      ;;
    Linux)
      installer="${repo_dir}/scripts/install-linux-host-agent"
      ;;
    *)
      fail "unsupported OS: ${os}; bclaw currently supports macOS and Linux host agents"
      ;;
  esac

  [ -x "$installer" ] || fail "installer is not executable: ${installer}"
  log "Running host-agent installer: ${installer}"
  "$installer" "${INSTALLER_ARGS[@]}"
}

main() {
  local repo_dir
  repo_dir="$(ensure_repo)"
  run_platform_installer "$repo_dir"

  cat >&2 <<DONE

bclaw is installed.

Android APK:
  ${APK_URL}

Pair this host:
  ${repo_dir}/scripts/bclaw-handoff --qr

If QR rendering is unavailable, run the same command without --qr and paste the printed bclaw2:// URL into the Android app.

DONE
}

main "$@"
