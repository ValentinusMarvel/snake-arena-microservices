#!/usr/bin/env bash
set -e
echo "====================================================================="
echo " Starting Snake Arena Microservices via Docker Compose"
echo "====================================================================="
docker compose up --build -d

echo ""
echo "[STATUS] Containers are deploying in background."
echo "[INFO] Player Service:  http://localhost:8081/swagger-ui.html"
echo "[INFO] Arena Service:   http://localhost:8082"
echo "[INFO] Health Endpoint: http://localhost:8081/actuator/health"
echo "====================================================================="
