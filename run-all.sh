#!/usr/bin/env bash
# Starts all 4 eIM services on a single host.
# Inter-service URLs stay as localhost:* — works because everything runs on this box.
# psmo-service runs with REAL signing (no 'lab' profile) so it produces valid eUICC packages.
# Runs prebuilt jars — build first: mvn clean package -DskipTests
# Usage: ./run-all.sh
set -e
cd "$(dirname "$0")"
mkdir -p logs

# Load eim2 HTTPS + signing config (exported to the child java processes)
if [ -f eim2.env ]; then
    set -a; source eim2.env; set +a
fi

# --- Stop any previously started instances first ---------------------------------------------
# Without this, an old JVM keeps holding its port; the new one fails to bind and dies, leaving the
# STALE jar serving. Killing the recorded PIDs guarantees the freshly built jars actually run.
stop_previous () {
  for pidf in logs/*.pid; do
    [ -f "$pidf" ] || continue
    local pid; pid=$(cat "$pidf")
    if kill -0 "$pid" 2>/dev/null; then
      echo "Stopping previous $(basename "$pidf" .pid) (pid $pid)"
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pidf"
  done
  sleep 3
}

# --- Wait until a service has finished starting (Spring context up, Flyway migrations applied) --
wait_started () {
  local name=$1
  echo "Waiting for $name to finish starting..."
  for _ in $(seq 1 90); do
    if grep -q "Started .*Application" "logs/$name.log" 2>/dev/null; then
      echo "  $name is up."
      return 0
    fi
    # Fail fast if the process died during startup.
    if [ -f "logs/$name.pid" ] && ! kill -0 "$(cat "logs/$name.pid")" 2>/dev/null; then
      echo "!! $name exited during startup — check logs/$name.log"
      return 1
    fi
    sleep 1
  done
  echo "!! $name did not start within 90s — check logs/$name.log"
  return 1
}

start () {
  local name=$1
  local profile=$2          # optional Spring profile
  local jar
  jar=$(ls "$name"/target/*.jar 2>/dev/null | grep -v 'original' | head -n1)
  if [ -z "$jar" ]; then
    echo "!! No jar found for $name — did 'mvn clean package -DskipTests' run?"
    return 1
  fi
  local args=""
  [ -n "$profile" ] && args="--spring.profiles.active=$profile"
  echo "Starting $name  ->  $jar  ${profile:+(profile: $profile)}"
  nohup java -jar "$jar" $args > "logs/$name.log" 2>&1 &
  echo $! > "logs/$name.pid"
}

stop_previous

# Backend services first, gateway last.
start user-service

# inventory-service owns the 'inventory' schema and its Flyway migrations. psmo-service maps some
# inventory tables (device_profiles) and validates them at startup, so inventory MUST finish
# migrating before psmo starts — otherwise psmo fails schema validation on not-yet-created columns.
start inventory-service
wait_started inventory-service

start psmo-service
wait_started psmo-service

start api-gateway
wait_started api-gateway

echo ""
echo "All services started."
echo "  Tail gateway log : tail -f logs/api-gateway.log"
echo "  Health check     : curl http://localhost:8080/actuator/health"
