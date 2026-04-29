#!/usr/bin/env bash
# Capture a frozen snapshot of the current macOS host stack so post-rewrite
# work has something to compare against.
#
# Output: baselines/<target>-macos-<host>-<YYYY-MM-DD>.json
#
# Modes:
#   --mode=idle        (default) sample CPU/RSS for 60s with no streaming
#   --mode=streaming   sample CPU/RSS for $DURATION seconds (operator must
#                      pair a phone client and start streaming first)
#
# Targets:
#   --target=sunshine  (default) baseline the legacy Sunshine + Node path
#   --target=<name>    label this run (e.g. bclaw-rewrite for re-verification)
#
# Latency and VMAF baselines are intentionally NOT captured here — they
# require the headless Moonlight test client (Milestone 0).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASELINE_DIR="${REPO_DIR}/baselines"

MODE="idle"
TARGET="sunshine"
DURATION=60

usage() {
  cat >&2 <<USAGE
usage: scripts/baseline-macos.sh [--mode=idle|streaming] [--target=<label>] [--duration=<seconds>]

Defaults: --mode=idle --target=sunshine --duration=60 (idle) / 300 (streaming).

Streaming mode requires the operator to manually pair a phone client and
begin a stream before invoking this script. The script does not orchestrate
the client — see verification/README.md.
USAGE
  exit "${1:-1}"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode=*) MODE="${1#--mode=}"; shift ;;
    --target=*) TARGET="${1#--target=}"; shift ;;
    --duration=*) DURATION="${1#--duration=}"; shift ;;
    -h|--help) usage 0 ;;
    *) usage ;;
  esac
done

case "$MODE" in
  idle|streaming) ;;
  *) echo "invalid --mode: $MODE" >&2; exit 1 ;;
esac

[ "$(uname -s)" = "Darwin" ] || { echo "macOS only" >&2; exit 1; }

if [ "$MODE" = "streaming" ] && [ "$DURATION" -lt 60 ]; then
  echo "warning: streaming sample shorter than 60s; results will be noisy" >&2
fi

mkdir -p "$BASELINE_DIR"
HOST="$(scutil --get LocalHostName 2>/dev/null || hostname -s)"
TODAY="$(date +%Y-%m-%d)"
OUT="${BASELINE_DIR}/${TARGET}-${MODE}-macos-${HOST}-${TODAY}.json"

log() { printf '[baseline] %s\n' "$*" >&2; }

# ---------------- collectors ----------------

json_str() {
  python3 -c 'import json,sys; sys.stdout.write(json.dumps(sys.stdin.read()))'
}

collect_system_info() {
  local product_version build_version model arch chip cores ram_bytes
  product_version="$(sw_vers -productVersion)"
  build_version="$(sw_vers -buildVersion)"
  model="$(sysctl -n hw.model 2>/dev/null || printf 'unknown')"
  arch="$(uname -m)"
  chip="$(sysctl -n machdep.cpu.brand_string 2>/dev/null || printf 'unknown')"
  cores="$(sysctl -n hw.ncpu 2>/dev/null || printf 0)"
  ram_bytes="$(sysctl -n hw.memsize 2>/dev/null || printf 0)"
  printf '{"productVersion":%s,"buildVersion":%s,"model":%s,"arch":%s,"chip":%s,"cores":%s,"ramBytes":%s}' \
    "$(printf '%s' "$product_version" | json_str)" \
    "$(printf '%s' "$build_version"   | json_str)" \
    "$(printf '%s' "$model"           | json_str)" \
    "$(printf '%s' "$arch"            | json_str)" \
    "$(printf '%s' "$chip"            | json_str)" \
    "$cores" \
    "$ram_bytes"
}

collect_dependencies() {
  # What's actually installed today that the host stack depends on.
  local sunshine_path sunshine_ver node_path node_ver brew_path helper_path
  sunshine_path="$(command -v sunshine 2>/dev/null || true)"
  if [ -z "$sunshine_path" ] && [ -d /Applications/Sunshine.app ]; then
    sunshine_path="/Applications/Sunshine.app"
  fi
  sunshine_ver=""
  if [ -d /Applications/Sunshine.app ]; then
    sunshine_ver="$(defaults read /Applications/Sunshine.app/Contents/Info CFBundleShortVersionString 2>/dev/null || true)"
  fi
  if [ -z "$sunshine_ver" ] && [ -n "$sunshine_path" ] && command -v brew >/dev/null 2>&1; then
    sunshine_ver="$(brew list --versions sunshine 2>/dev/null | awk '{print $2}' || true)"
  fi
  node_path="$(command -v node 2>/dev/null || true)"
  node_ver="$([ -n "$node_path" ] && "$node_path" --version 2>/dev/null || true)"
  brew_path="$(command -v brew 2>/dev/null || true)"
  helper_path="${REPO_DIR}/host-agent/.macos-input-helper/macos-input-helper"
  [ -x "$helper_path" ] || helper_path=""

  printf '{"sunshinePath":%s,"sunshineVersion":%s,"nodePath":%s,"nodeVersion":%s,"brewPath":%s,"inputHelper":%s}' \
    "$(printf '%s' "$sunshine_path"  | json_str)" \
    "$(printf '%s' "$sunshine_ver"   | json_str)" \
    "$(printf '%s' "$node_path"      | json_str)" \
    "$(printf '%s' "$node_ver"       | json_str)" \
    "$(printf '%s' "$brew_path"      | json_str)" \
    "$(printf '%s' "$helper_path"    | json_str)"
}

collect_processes() {
  # Snapshot of every process whose name relates to the host stack.
  python3 - <<'PY'
import json, os, subprocess

def classify(cmd):
    # Returns a bucket name if cmd belongs to the host stack, else None.
    # cmd is the full ps "command" field (argv[0] + ' ' + argv[1] + ...).
    parts = cmd.split()
    if not parts:
        return None
    argv0 = parts[0]
    base = os.path.basename(argv0)
    if base in ("Sunshine", "sunshine"):
        return "sunshine"
    if base == "bclaw-host" or base.endswith("bclaw-host"):
        return "bclaw-host"
    if base in ("macos-input-helper", "macos_input_helper"):
        return "macos-input-helper"
    if base == "node" and len(parts) >= 2:
        # host-agent is launched by start.sh as `node server.js` from the
        # host-agent/ directory. Match exactly that argv shape, plus the
        # optional absolute-path variant (`node /path/to/host-agent/server.js`).
        a1 = parts[1]
        if a1 == "server.js" or a1.endswith("/host-agent/server.js"):
            return "host-agent"
    # Sunshine ships a keep-display-awake.sh helper that's worth tracking.
    if "/.config/sunshine/" in cmd and base in ("bash", "zsh", "sh"):
        return "sunshine-helper"
    return None

out = subprocess.check_output(["ps", "-axo", "pid=,rss=,pcpu=,command="], text=True)
rows = []
for line in out.splitlines():
    parts = line.strip().split(None, 3)
    if len(parts) < 4:
        continue
    pid, rss, cpu, cmd = parts
    bucket = classify(cmd)
    if bucket is None:
        continue
    rows.append({"pid": int(pid), "rssKb": int(rss), "cpuPct": float(cpu), "bucket": bucket, "command": cmd[:200]})
print(json.dumps(rows))
PY
}

# Sample CPU% and RSS for matching processes every 1s for $DURATION seconds.
sample_load() {
  local seconds="$1"
  python3 - "$seconds" <<'PY'
import json, os, subprocess, sys, time

duration = int(sys.argv[1])

def classify(cmd):
    parts = cmd.split()
    if not parts:
        return None
    argv0 = parts[0]
    base = os.path.basename(argv0)
    if base in ("Sunshine", "sunshine"):
        return "sunshine"
    if base == "bclaw-host" or base.endswith("bclaw-host"):
        return "bclaw-host"
    if base in ("macos-input-helper", "macos_input_helper"):
        return "macos-input-helper"
    if base == "node" and len(parts) >= 2:
        a1 = parts[1]
        if a1 == "server.js" or a1.endswith("/host-agent/server.js"):
            return "host-agent"
    if "/.config/sunshine/" in cmd and base in ("bash", "zsh", "sh"):
        return "sunshine-helper"
    return None

samples = []
end = time.time() + duration
while time.time() < end:
    out = subprocess.check_output(["ps", "-axo", "pid=,rss=,pcpu=,command="], text=True)
    snap = []
    for line in out.splitlines():
        parts = line.strip().split(None, 3)
        if len(parts) < 4:
            continue
        pid, rss, cpu, cmd = parts
        b = classify(cmd)
        if b is None:
            continue
        snap.append({"pid": int(pid), "rssKb": int(rss), "cpuPct": float(cpu), "bucket": b})
    samples.append({"t": round(time.time(), 3), "procs": snap})
    time.sleep(1.0)

agg = {}
for snap in samples:
    by_bucket = {}
    for proc in snap["procs"]:
        b = proc["bucket"]
        by_bucket.setdefault(b, {"rssKb": 0, "cpuPct": 0.0})
        by_bucket[b]["rssKb"] += proc["rssKb"]
        by_bucket[b]["cpuPct"] += proc["cpuPct"]
    for b, v in by_bucket.items():
        agg.setdefault(b, {"cpuPct": [], "rssKb": []})
        agg[b]["cpuPct"].append(v["cpuPct"])
        agg[b]["rssKb"].append(v["rssKb"])

def pct(arr, p):
    if not arr:
        return None
    s = sorted(arr)
    k = max(0, min(len(s) - 1, int(round((p / 100.0) * (len(s) - 1)))))
    return s[k]

summary = {}
for b, v in agg.items():
    summary[b] = {
        "cpuPctP50": pct(v["cpuPct"], 50),
        "cpuPctP95": pct(v["cpuPct"], 95),
        "rssKbP50": pct(v["rssKb"], 50),
        "rssKbP95": pct(v["rssKb"], 95),
        "samples": len(v["cpuPct"]),
    }

print(json.dumps({"durationS": duration, "sampleCount": len(samples), "perProcess": summary}))
PY
}

collect_tcc() {
  # User-level TCC entries that might cover host components.
  # Requires Full Disk Access on the running terminal; degrades gracefully.
  local db="${HOME}/Library/Application Support/com.apple.TCC/TCC.db"
  if [ ! -r "$db" ]; then
    printf '{"readable":false,"reason":"TCC.db not readable; grant Full Disk Access to this terminal","entries":[]}'
    return
  fi
  python3 - "$db" <<'PY'
import json, sqlite3, sys
db = sys.argv[1]
con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
cur = con.cursor()
try:
    cur.execute(
        "SELECT service, client, client_type, auth_value FROM access "
        "WHERE service IN ('kTCCServiceScreenCapture','kTCCServiceAccessibility') "
        "ORDER BY service, client"
    )
    rows = [
        {"service": s, "client": c, "clientType": t, "authValue": a}
        for s, c, t, a in cur.fetchall()
    ]
finally:
    con.close()
print(json.dumps({"readable": True, "entries": rows}))
PY
}

collect_install_footprint() {
  # Static parse of install-macos-host-agent: what does the current path
  # actually drag in? Used to verify criterion 2.2 after the rewrite.
  local installer="${REPO_DIR}/scripts/install-macos-host-agent"
  python3 - "$installer" <<'PY'
import json, re, sys
path = sys.argv[1]
with open(path) as f:
    text = f.read()
patterns = {
    "brewInstallCalls": r"\bbrew\s+install\b",
    "brewTapCalls": r"\bbrew\s+tap\b",
    "npmInstallCalls": r"\bnpm\s+(?:install|ci)\b",
    "homebrewBootstrap": r"Homebrew/install/HEAD/install\.sh",
    "sunshineFormula": r"sunshine[a-z\-]*",
}
out = {k: len(re.findall(p, text)) for k, p in patterns.items()}
print(json.dumps(out))
PY
}

# ---------------- main ----------------

log "writing $OUT"
log "mode=$MODE target=$TARGET duration=${DURATION}s"

if [ "$MODE" = "streaming" ]; then
  log "make sure a phone client is paired and currently streaming a reference workload"
  log "sampling for ${DURATION}s..."
fi

system_info="$(collect_system_info)"
deps="$(collect_dependencies)"
processes="$(collect_processes)"
tcc="$(collect_tcc)"
install_footprint="$(collect_install_footprint)"

if [ "$MODE" = "idle" ] && [ "$DURATION" = "60" ]; then
  load_seconds="$DURATION"
elif [ "$MODE" = "streaming" ] && [ "$DURATION" = "60" ]; then
  load_seconds=300
else
  load_seconds="$DURATION"
fi
load="$(sample_load "$load_seconds")"

now_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
git_sha="$(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || printf 'unknown')"
git_branch="$(git -C "$REPO_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || printf 'unknown')"

python3 - "$OUT" "$now_iso" "$TARGET" "$MODE" "$git_sha" "$git_branch" \
  "$system_info" "$deps" "$processes" "$tcc" "$install_footprint" "$load" <<'PY'
import json, sys
out_path, ts, target, mode, sha, branch, sysinfo, deps, procs, tcc, footprint, load = sys.argv[1:]
record = {
    "schemaVersion": 1,
    "capturedAtUTC": ts,
    "target": target,
    "mode": mode,
    "git": {"branch": branch, "sha": sha},
    "system": json.loads(sysinfo),
    "dependencies": json.loads(deps),
    "processes": json.loads(procs),
    "tccPermissions": json.loads(tcc),
    "installFootprint": json.loads(footprint),
    "load": json.loads(load),
    "deferred": {
        "latency": "blocked on Milestone 0 (headless Moonlight test client)",
        "vmaf": "blocked on Milestone 0",
    },
}
with open(out_path, "w") as f:
    json.dump(record, f, indent=2)
    f.write("\n")
print(out_path)
PY

log "done. summary:"
python3 - "$OUT" <<'PY'
import json, sys
with open(sys.argv[1]) as f:
    r = json.load(f)
print(f"  target:           {r['target']} ({r['mode']})")
print(f"  system:           macOS {r['system']['productVersion']} on {r['system']['model']} ({r['system']['arch']})")
sun = r['dependencies'].get('sunshinePath') or '<missing>'
print(f"  sunshine:         {sun}")
node = r['dependencies'].get('nodeVersion') or '<missing>'
print(f"  node:             {node}")
print(f"  install pulls in: brew={r['installFootprint']['brewInstallCalls']} tap={r['installFootprint']['brewTapCalls']} npm={r['installFootprint']['npmInstallCalls']}")
tcc = r['tccPermissions']
if tcc.get('readable'):
    print(f"  tcc entries:      {len(tcc['entries'])} (services: ScreenCapture+Accessibility)")
    for e in tcc['entries']:
        print(f"                    [{e['service'].replace('kTCCService','')}] {e['client']} (auth={e['authValue']})")
else:
    print(f"  tcc entries:      <unreadable> ({tcc.get('reason','')})")
print(f"  load samples:     {r['load']['sampleCount']} over {r['load']['durationS']}s")
for proc, stats in r['load'].get('perProcess', {}).items():
    print(f"    {proc:20s}  cpu p50={stats['cpuPctP50']:.1f}% p95={stats['cpuPctP95']:.1f}%  rss p50={stats['rssKbP50']/1024:.0f}MB p95={stats['rssKbP95']/1024:.0f}MB")
PY
