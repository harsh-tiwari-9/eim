#!/usr/bin/env bash
# Starts all 4 eIM services on a single host.
# Inter-service URLs stay as localhost:* — works because everything runs on this box.
# Usage: ./run-all.sh
set -e
cd "$(dirname "$0")"
mkdir -p logs

start () {
  local name=$1
  local jar
  jar=$(ls "$name"/target/*.jar 2>/dev/null | grep -v 'original' | head -n1)
  if [ -z "$jar" ]; then
    echo "!! No jar found for $name — did 'mvn clean package -DskipTests' run?"
    return 1
  fi
  echo "Starting $name  ->  $jar"
  nohup java -jar "$jar" > "logs/$name.log" 2>&1 &
  echo $! > "logs/$name.pid"
}

# Backend services first, gateway last
start user-service
start inventory-service
start psmo-service
sleep 5
start api-gateway

echo ""
echo "All services launching."
echo "  Tail gateway log : tail -f logs/api-gateway.log"
echo "  Health check     : curl http://localhost:8080/actuator/health"
