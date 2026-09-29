#!/usr/bin/env bash
set -e
echo "====================================================================="
echo " Stopping Snake Arena Microservices Containers"
echo "====================================================================="
docker compose down

echo ""
echo "[STATUS] All containers and networks stopped."
echo "====================================================================="
