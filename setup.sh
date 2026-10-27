#!/usr/bin/env bash
# MovieHub setup & launcher.
#   ./setup.sh            install missing prerequisites, install dependencies, start everything
#   ./setup.sh check      only report what is installed / missing
#   ./setup.sh install    install prerequisites and dependencies, do not start
#   ./setup.sh start      start catalog, gateway and frontend (Ctrl-C stops all)
#   ./setup.sh stop       stop anything started by this script
#   ./setup.sh test       run backend and frontend tests
# Options: -y / --yes  install missing tools without asking
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$ROOT/.run"
REQUIRED_JAVA=21
MIN_NODE_MAJOR=22      # Vite 8 needs Node 20.19+ or 22.12+; 22 LTS keeps it simple
ASSUME_YES=0

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
info() { printf '  %s\n' "$*"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
die()  { printf '\033[31mError:\033[0m %s\n' "$*" >&2; exit 1; }

confirm() {
  [ "$ASSUME_YES" -eq 1 ] && return 0
  [ -t 0 ] || die "Cannot ask for confirmation in a non-interactive shell. Re-run with --yes."
  read -r -p "  $1 [y/N] " answer
  case "$answer" in y|Y|yes|YES) return 0 ;; *) return 1 ;; esac
}

OS="$(uname -s)"
SUDO=""
[ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1 && SUDO="sudo"

pkg_manager() {
  if [ "$OS" = "Darwin" ]; then echo brew
  elif command -v apt-get >/dev/null 2>&1; then echo apt
  elif command -v dnf >/dev/null 2>&1; then echo dnf
  else echo none; fi
}

# ---------- Java 21 ----------
java_major() { "$1" -version 2>&1 | sed -n '1s/.*"\([0-9]*\)[."].*/\1/p'; }

find_java_home() {
  if [ "$OS" = "Darwin" ] && /usr/libexec/java_home -v "$REQUIRED_JAVA" >/dev/null 2>&1; then
    /usr/libexec/java_home -v "$REQUIRED_JAVA"; return 0
  fi
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(java_major "$JAVA_HOME/bin/java")" = "$REQUIRED_JAVA" ]; then
    echo "$JAVA_HOME"; return 0
  fi
  local candidate
  for candidate in /usr/lib/jvm/*21* /usr/lib/jvm/*-21-*; do
    [ -x "$candidate/bin/java" ] && { echo "$candidate"; return 0; }
  done
  if command -v java >/dev/null 2>&1 && [ "$(java_major "$(command -v java)")" = "$REQUIRED_JAVA" ]; then
    dirname "$(dirname "$(readlink -f "$(command -v java)" 2>/dev/null || command -v java)")"; return 0
  fi
  return 1
}

# ---------- Node ----------
node_ok() {
  command -v node >/dev/null 2>&1 && command -v npm >/dev/null 2>&1 &&
    [ "$(node -p 'process.versions.node.split(".")[0]')" -ge "$MIN_NODE_MAJOR" ]
}

# ---------- Homebrew ----------
ensure_brew() {
  command -v brew >/dev/null 2>&1 && return 0
  for b in /opt/homebrew/bin/brew /usr/local/bin/brew; do
    [ -x "$b" ] && { eval "$("$b" shellenv)"; return 0; }
  done
  warn "Homebrew is not installed."
  confirm "Install Homebrew from https://brew.sh (official installer)?" || die "Homebrew is required to install tools on macOS."
  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
  for b in /opt/homebrew/bin/brew /usr/local/bin/brew; do
    [ -x "$b" ] && eval "$("$b" shellenv)"
  done
  command -v brew >/dev/null 2>&1 || die "Homebrew installation did not complete."
}

install_java() {
  case "$(pkg_manager)" in
    brew) ensure_brew; brew install --cask "temurin@$REQUIRED_JAVA" ;;
    apt)  $SUDO apt-get update && $SUDO apt-get install -y "openjdk-$REQUIRED_JAVA-jdk" ;;
    dnf)  $SUDO dnf install -y "java-$REQUIRED_JAVA-openjdk-devel" ;;
    *)    die "Unsupported system. Install JDK $REQUIRED_JAVA manually (https://adoptium.net) and re-run." ;;
  esac
}

install_node() {
  case "$(pkg_manager)" in
    brew) ensure_brew; brew install "node@$MIN_NODE_MAJOR" && brew link --overwrite --force "node@$MIN_NODE_MAJOR" ;;
    apt)  curl -fsSL "https://deb.nodesource.com/setup_${MIN_NODE_MAJOR}.x" | $SUDO -E bash - && $SUDO apt-get install -y nodejs ;;
    dnf)  curl -fsSL "https://rpm.nodesource.com/setup_${MIN_NODE_MAJOR}.x" | $SUDO bash - && $SUDO dnf install -y nodejs ;;
    *)    die "Unsupported system. Install Node.js $MIN_NODE_MAJOR+ manually (https://nodejs.org) and re-run." ;;
  esac
}

# ---------- Commands ----------
cmd_check() {
  bold "Checking prerequisites"
  local missing=0
  if JAVA_HOME_FOUND="$(find_java_home)"; then ok "JDK $REQUIRED_JAVA ($JAVA_HOME_FOUND)"; else warn "JDK $REQUIRED_JAVA missing"; missing=1; fi
  if node_ok; then ok "Node $(node -v), npm $(npm -v)"; else warn "Node.js $MIN_NODE_MAJOR+ (with npm) missing or too old"; missing=1; fi
  command -v curl >/dev/null 2>&1 && ok "curl" || { warn "curl missing"; missing=1; }
  return $missing
}

cmd_install() {
  bold "Installing prerequisites"
  if ! find_java_home >/dev/null; then
    warn "JDK $REQUIRED_JAVA not found."
    confirm "Install JDK $REQUIRED_JAVA now?" || die "JDK $REQUIRED_JAVA is required."
    install_java
    find_java_home >/dev/null || die "JDK $REQUIRED_JAVA still not found after installation."
  fi
  ok "JDK $REQUIRED_JAVA"
  if ! node_ok; then
    warn "Node.js $MIN_NODE_MAJOR+ not found."
    confirm "Install Node.js $MIN_NODE_MAJOR now?" || die "Node.js $MIN_NODE_MAJOR+ is required."
    install_node
    hash -r
    node_ok || die "Node.js $MIN_NODE_MAJOR+ still not available. Open a new terminal and re-run."
  fi
  ok "Node $(node -v)"
  bold "Installing project dependencies"
  (cd "$ROOT/frontend" && npm ci --no-audit --no-fund)
  JAVA_HOME="$(find_java_home)" "$ROOT/gradlew" --console=plain -q -p "$ROOT" help >/dev/null
  ok "Gradle wrapper and backend dependencies ready"
}

port_open() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

wait_for() { # name, command-that-succeeds-when-ready, timeout seconds
  local name="$1" probe="$2" timeout="$3" waited=0
  until eval "$probe"; do
    sleep 1; waited=$((waited + 1))
    [ "$waited" -ge "$timeout" ] && die "$name did not become ready in ${timeout}s. See $RUN_DIR/*.log"
  done
  ok "$name ready"
}

kill_tree() {
  local pid="$1" child
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}

cmd_stop() {
  [ -d "$RUN_DIR" ] || { info "Nothing to stop."; return 0; }
  local file
  for file in "$RUN_DIR"/*.pid; do
    [ -e "$file" ] || continue
    kill_tree "$(cat "$file")"
    rm -f "$file"
  done
  ok "Stopped"
}

cmd_start() {
  cmd_check >/dev/null || die "Prerequisites missing. Run ./setup.sh first."
  [ -d "$ROOT/frontend/node_modules" ] || die "Dependencies not installed. Run ./setup.sh first."
  local p
  for p in 9090 8080 5173; do
    port_open "$p" && die "Port $p is already in use. Stop the other process (or run ./setup.sh stop)."
  done
  export JAVA_HOME; JAVA_HOME="$(find_java_home)"
  mkdir -p "$RUN_DIR"
  trap 'echo; bold "Shutting down"; cmd_stop; exit 0' INT TERM

  bold "Starting MovieHub"
  (cd "$ROOT" && exec ./gradlew :catalog-service:bootRun --console=plain >"$RUN_DIR/catalog.log" 2>&1) &
  echo $! >"$RUN_DIR/catalog.pid"
  wait_for "Catalog service (gRPC :9090)" 'port_open 9090' 180

  (cd "$ROOT" && exec ./gradlew :gateway-service:bootRun --console=plain >"$RUN_DIR/gateway.log" 2>&1) &
  echo $! >"$RUN_DIR/gateway.pid"
  wait_for "Gateway (GraphQL :8080)" 'curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1' 180

  (cd "$ROOT/frontend" && exec npm run dev >"$RUN_DIR/frontend.log" 2>&1) &
  echo $! >"$RUN_DIR/frontend.pid"
  wait_for "Frontend (:5173)" 'port_open 5173' 60

  echo
  bold "MovieHub is running:  http://localhost:5173"
  info "GraphiQL:  http://localhost:8080/graphiql"
  info "Logs:      $RUN_DIR/*.log"
  info "Press Ctrl-C to stop everything."
  wait
}

cmd_test() {
  cmd_check >/dev/null || die "Prerequisites missing. Run ./setup.sh first."
  export JAVA_HOME; JAVA_HOME="$(find_java_home)"
  bold "Backend tests";  (cd "$ROOT" && ./gradlew test --console=plain)
  bold "Frontend tests"; (cd "$ROOT/frontend" && npm run check && npm test)
}

# ---------- Entry ----------
COMMAND=""
for arg in "$@"; do
  case "$arg" in
    -y|--yes) ASSUME_YES=1 ;;
    -h|--help|help) sed -n '2,10p' "${BASH_SOURCE[0]}"; exit 0 ;;
    check|install|start|stop|test|setup) COMMAND="$arg" ;;
    *) die "Unknown argument: $arg (try --help)" ;;
  esac
done

case "${COMMAND:-setup}" in
  check)   cmd_check ;;
  install) cmd_install ;;
  start)   cmd_start ;;
  stop)    cmd_stop ;;
  test)    cmd_test ;;
  setup)   cmd_install && cmd_start ;;
esac

